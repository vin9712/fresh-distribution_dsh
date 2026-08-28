package com.lin.distribution.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.DeliveryPrintMediaType;
import com.lin.distribution.constant.DeliveryPrintRule;
import com.lin.distribution.constant.DeliveryPrintSplitMode;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.domain.DeliveryPrintConfig;
import com.lin.distribution.domain.DeliveryPrintConfigVersion;
import com.lin.distribution.dto.DeliveryPrintConfigDTO;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.DeliveryPrintConfigMapper;
import com.lin.distribution.mapper.DeliveryPrintConfigVersionMapper;
import com.lin.distribution.service.DeliveryPrintConfigService;
import com.lin.distribution.vo.DeliveryPrintConfigVO;
import com.lin.distribution.vo.DeliveryPrintStructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 送货单打印拆分配置服务实现（W0-2.2，蓝图「送货单打印拆分配置」）
 *
 * <p>核心口径：
 * <ul>
 *   <li>只读解析：未保存配置时按批次组单策略推导默认值 + 自动生成结构（明细ID升序），不落库；</li>
 *   <li>保存/恢复仅允许未打印（PENDING）单（蓝图「打印后改单禁改/未打印送货单排序拆分合并每次保存」）；</li>
 *   <li>规则校验：跨点合单仅 A4（针式仅单点）；针式单点固定每页 10 条；detailOrder 必须为该单有效明细全排列；</li>
 *   <li>每次保存/恢复写入一份版本记录（versionNo 自增）实现“配置变更审计”与“恢复自动生成结构”。</li>
 * </ul></p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPrintConfigServiceImpl implements DeliveryPrintConfigService {

    /** 恢复自动生成结构的固定变更说明 */
    public static final String RESTORE_AUTO_NOTE = "恢复自动生成结构";

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final DeliveryPrintConfigMapper configMapper;
    private final DeliveryPrintConfigVersionMapper versionMapper;

    @Override
    public DeliveryPrintConfigVO resolveConfig(Long deliveryOrderId) {
        DeliveryOrder delivery = loadDelivery(deliveryOrderId);
        DeliveryPrintConfig config = configMapper.selectByDeliveryOrderId(deliveryOrderId);
        if (config == null) {
            // 未保存配置：按批次策略推导默认值 + 自动生成结构（只读，不落库）
            DeliveryPrintConfig auto = buildDefaultConfig(delivery);
            int versionNo = versionMapper.selectMaxVersionNo(deliveryOrderId);
            return DeliveryPrintConfigVO.from(auto, delivery.getCode(), delivery.getStatus(), versionNo);
        }
        int versionNo = versionMapper.selectMaxVersionNo(deliveryOrderId);
        return DeliveryPrintConfigVO.from(config, delivery.getCode(), delivery.getStatus(), versionNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintConfigVO saveConfig(Long deliveryOrderId, DeliveryPrintConfigDTO dto) {
        if (dto == null) {
            throw new ServiceException("保存参数不能为空");
        }
        DeliveryOrder delivery = loadDeliveryForEdit(deliveryOrderId);
        List<DeliveryOrderDetail> details = activeDetails(deliveryOrderId);

        // 1. 归一化拆分方式 / 介质 / 分页行数（含规则校验）
        String splitMode = normalizeSplitMode(dto.getSplitMode(), delivery);
        String mediaType = normalizeMediaType(dto.getMediaType(), splitMode);
        Integer rowsPerPage = normalizeRowsPerPage(dto.getRowsPerPage(), mediaType, splitMode);

        // 2. 打印顺序：缺省按当前结构或自动生成顺序，否则校验为该单有效明细全排列
        List<Long> order = resolveOrder(dto.getDetailOrder(), details, deliveryOrderId, splitMode, mediaType, rowsPerPage);

        DeliveryPrintStructure structure = buildStructure(order, rowsPerPage);

        // 3. UPSERT 当前配置（并发安全：按送货单ID锁行 + 唯一键兜底）
        DeliveryPrintConfig config = upsertConfig(delivery, splitMode, mediaType, rowsPerPage, structure, false);

        // 4. 追加版本记录
        appendVersion(deliveryOrderId, config, dto.getChangeNote());
        return toVO(config, delivery);
    }

    @Override
    public List<DeliveryPrintConfigVersion> listVersions(Long deliveryOrderId) {
        loadDelivery(deliveryOrderId);
        return versionMapper.selectByDeliveryOrderId(deliveryOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPrintConfigVO restoreAutoStructure(Long deliveryOrderId) {
        DeliveryOrder delivery = loadDeliveryForEdit(deliveryOrderId);
        // 保留当前介质与分页行数（如未配置则用默认），仅重算自动结构顺序（明细ID升序）
        DeliveryPrintConfig current = configMapper.selectByDeliveryOrderId(deliveryOrderId);
        String splitMode = current != null ? current.getSplitMode()
                : DeliveryPrintSplitMode.fromScopeType(delivery.getScopeType()).getCode();
        String mediaType = current != null ? current.getMediaType() : DeliveryPrintRule.DEFAULT_MEDIA_TYPE;
        Integer rowsPerPage = current != null && current.getRowsPerPage() != null
                ? current.getRowsPerPage()
                : normalizeRowsPerPage(null, mediaType, splitMode);

        List<DeliveryOrderDetail> details = activeDetails(deliveryOrderId);
        List<Long> order = autoOrder(details);
        DeliveryPrintStructure structure = buildStructure(order, rowsPerPage);

        DeliveryPrintConfig config = upsertConfig(delivery, splitMode, mediaType, rowsPerPage, structure, true);
        appendVersion(deliveryOrderId, config, RESTORE_AUTO_NOTE);
        return toVO(config, delivery);
    }

    // ==================== 校验与归一化 ====================

    /**
     * 打印相关配置改动仅允许未打印（PENDING）单（蓝图「打印后改单禁改」）。
     */
    private DeliveryOrder loadDeliveryForEdit(Long deliveryOrderId) {
        DeliveryOrder delivery = loadDelivery(deliveryOrderId);
        if (!DeliveryOrderStatus.PENDING.getCode().equals(delivery.getStatus())) {
            throw new ServiceException("仅待打印的送货单可调整打印拆分配置，当前状态："
                    + DeliveryOrderStatus.fromCode(delivery.getStatus()).getDesc());
        }
        return delivery;
    }

    private DeliveryOrder loadDelivery(Long deliveryOrderId) {
        if (deliveryOrderId == null) {
            throw new ServiceException("送货单ID不能为空");
        }
        DeliveryOrder delivery = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (delivery == null || Boolean.TRUE.equals(delivery.getIsDeleted())) {
            throw new ServiceException("送货单不存在");
        }
        return delivery;
    }

    /**
     * 拆分方式：缺省按批次组单范围推导；非空则校验合法值。
     */
    private String normalizeSplitMode(String splitMode, DeliveryOrder delivery) {
        if (StringUtils.isBlank(splitMode)) {
            return DeliveryPrintSplitMode.fromScopeType(delivery.getScopeType()).getCode();
        }
        if (!DeliveryPrintSplitMode.isValid(splitMode)) {
            throw new ServiceException("拆分方式不合法：" + splitMode);
        }
        return splitMode;
    }

    /**
     * 介质归一化与规则校验：缺省 A4；跨点合单仅 A4（针式仅单点）。
     */
    private String normalizeMediaType(String mediaType, String splitMode) {
        String resolved = StringUtils.isBlank(mediaType) ? DeliveryPrintRule.DEFAULT_MEDIA_TYPE : mediaType;
        if (!DeliveryPrintMediaType.isValid(resolved)) {
            throw new ServiceException("输出介质不合法：" + resolved);
        }
        // 跨点合单仅允许 A4 输出（蓝图「针式合单边界」）；针式仅单点、按点/最大行数拆分均属单点输出
        if (DeliveryPrintSplitMode.CROSS_POINT_MERGE.getCode().equals(splitMode)
                && DeliveryPrintMediaType.isDotMatrix(resolved)) {
            throw new ServiceException("跨配送点合单仅允许 A4 输出，不能使用针式");
        }
        return resolved;
    }

    /**
     * 分页行数归一化与规则校验：缺省 10；针式单点固定每页 10 条；上限防御。
     */
    private Integer normalizeRowsPerPage(Integer rowsPerPage, String mediaType, String splitMode) {
        if (DeliveryPrintMediaType.isDotMatrix(mediaType)) {
            // 针式单点固定每页 10 条
            if (rowsPerPage != null && rowsPerPage != DeliveryPrintRule.DOT_MATRIX_ROWS_PER_PAGE) {
                throw new ServiceException("针式打印固定每页 " + DeliveryPrintRule.DOT_MATRIX_ROWS_PER_PAGE + " 条明细");
            }
            return DeliveryPrintRule.DOT_MATRIX_ROWS_PER_PAGE;
        }
        // A4：按页高自动分页，rowsPerPage 仅为期望值（默认 10，上限防御）
        int resolved = rowsPerPage == null || rowsPerPage <= 0
                ? DeliveryPrintRule.DEFAULT_ROWS_PER_PAGE : rowsPerPage;
        if (resolved > DeliveryPrintRule.MAX_ROWS_PER_PAGE) {
            throw new ServiceException("分页行数不能超过 " + DeliveryPrintRule.MAX_ROWS_PER_PAGE);
        }
        return resolved;
    }

    /**
     * 打印顺序：缺省用当前结构顺序（无配置则自动顺序）；非空校验为该单有效明细全排列。
     */
    private List<Long> resolveOrder(List<Long> detailOrder, List<DeliveryOrderDetail> details,
                                    Long deliveryOrderId, String splitMode, String mediaType, Integer rowsPerPage) {
        if (CollectionUtils.isEmpty(detailOrder)) {
            List<Long> currentOrder = currentStructureOrder(deliveryOrderId);
            return currentOrder.isEmpty() ? autoOrder(details) : currentOrder;
        }
        Set<Long> expectedSet = details.stream().map(DeliveryOrderDetail::getId).collect(Collectors.toSet());
        if (detailOrder.size() != expectedSet.size()
                || new HashSet<>(detailOrder).size() != expectedSet.size()
                || !expectedSet.containsAll(detailOrder)) {
            throw new ServiceException("打印顺序必须包含该送货单全部" + expectedSet.size() + "条有效明细且不重复");
        }
        return new ArrayList<>(detailOrder);
    }

    /**
     * 当前配置的打印顺序（无配置则空列表）。
     */
    private List<Long> currentStructureOrder(Long deliveryOrderId) {
        DeliveryPrintConfig config = configMapper.selectByDeliveryOrderId(deliveryOrderId);
        if (config == null || StringUtils.isBlank(config.getStructureJson())) {
            return List.of();
        }
        DeliveryPrintStructure structure = DeliveryPrintStructure.fromJson(config.getStructureJson());
        return structure == null || CollectionUtils.isEmpty(structure.getOrder())
                ? List.of() : structure.getOrder();
    }

    private List<Long> autoOrder(List<DeliveryOrderDetail> details) {
        return details.stream()
                .sorted(Comparator.comparing(DeliveryOrderDetail::getId))
                .map(DeliveryOrderDetail::getId)
                .collect(Collectors.toList());
    }

    /**
     * 按分页行数切分打印结构（针式固定每页 10 条；A4 按期望值预估，“第 N/M 张”）。
     */
    private DeliveryPrintStructure buildStructure(List<Long> order, Integer rowsPerPage) {
        int rpp = rowsPerPage == null || rowsPerPage <= 0
                ? DeliveryPrintRule.DEFAULT_ROWS_PER_PAGE : rowsPerPage;
        int n = order.size();
        int totalPages = (int) Math.ceil((double) n / rpp);
        if (totalPages < 1) {
            totalPages = 1;
        }
        List<DeliveryPrintStructure.Page> pages = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            int from = i * rpp;
            int to = Math.min(from + rpp, n);
            List<Long> chunk = n == 0 ? List.of() : new ArrayList<>(order.subList(from, to));
            pages.add(DeliveryPrintStructure.Page.builder()
                    .pageNo(i + 1)
                    .totalPages(totalPages)
                    .detailIds(chunk)
                    .pageLabel("第" + (i + 1) + "/" + totalPages + "张")
                    .build());
        }
        return DeliveryPrintStructure.builder()
                .order(new ArrayList<>(order))
                .pages(pages)
                .build();
    }

    /**
     * 按送货单ID锁行 UPSERT 当前配置（并发安全；唯一键 unq_delivery_order 兜底）。
     */
    private DeliveryPrintConfig upsertConfig(DeliveryOrder delivery, String splitMode, String mediaType,
                                             Integer rowsPerPage, DeliveryPrintStructure structure, boolean autoGenerated) {
        DeliveryPrintConfig config = configMapper.selectByDeliveryOrderIdForUpdate(delivery.getId());
        Date now = DateUtils.getNowDate();
        String operator = resolveOperator();
        if (config == null) {
            config = new DeliveryPrintConfig();
            config.setDeliveryOrderId(delivery.getId());
            config.setVersion(0);
            config.setIsDeleted(Boolean.FALSE);
            config.setCreateTime(now);
            config.setCreateBy(operator);
            applyConfigFields(config, splitMode, mediaType, rowsPerPage, structure, autoGenerated);
            configMapper.insertDeliveryPrintConfig(config);
            return config;
        }
        int currentVersion = config.getVersion() == null ? 0 : config.getVersion();
        applyConfigFields(config, splitMode, mediaType, rowsPerPage, structure, autoGenerated);
        config.setUpdateTime(now);
        config.setUpdateBy(operator);
        configMapper.updateDeliveryPrintConfig(config);
        config.setVersion(currentVersion + 1); // 反映库内 version = version + 1
        return config;
    }

    private void applyConfigFields(DeliveryPrintConfig config, String splitMode, String mediaType,
                                   Integer rowsPerPage, DeliveryPrintStructure structure, boolean autoGenerated) {
        config.setSplitMode(splitMode);
        config.setMediaType(mediaType);
        config.setRowsPerPage(rowsPerPage);
        config.setStructureJson(structure.toJson());
        config.setAutoGenerated(autoGenerated);
    }

    /**
     * 追加版本记录：versionNo = 当前最大 + 1，快照当前配置与结构。
     */
    private void appendVersion(Long deliveryOrderId, DeliveryPrintConfig config, String changeNote) {
        int versionNo = versionMapper.selectMaxVersionNo(deliveryOrderId) + 1;
        DeliveryPrintConfigVersion version = DeliveryPrintConfigVersion.builder()
                .deliveryOrderId(deliveryOrderId)
                .versionNo(versionNo)
                .splitMode(config.getSplitMode())
                .mediaType(config.getMediaType())
                .rowsPerPage(config.getRowsPerPage())
                .structureJson(config.getStructureJson())
                .autoGenerated(config.getAutoGenerated())
                .changeNote(changeNote)
                .build();
        version.setCreateBy(resolveOperator());
        version.setCreateTime(DateUtils.getNowDate());
        versionMapper.insertDeliveryPrintConfigVersion(version);
    }

    private DeliveryPrintConfigVO toVO(DeliveryPrintConfig config, DeliveryOrder delivery) {
        int versionNo = versionMapper.selectMaxVersionNo(delivery.getId());
        return DeliveryPrintConfigVO.from(config, delivery.getCode(), delivery.getStatus(), versionNo);
    }

    /**
     * 该送货单有效（未删除）明细，按ID升序稳定排序。
     */
    private List<DeliveryOrderDetail> activeDetails(Long deliveryOrderId) {
        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        if (CollectionUtils.isEmpty(details)) {
            return List.of();
        }
        return details.stream()
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .sorted(Comparator.comparing(DeliveryOrderDetail::getId))
                .collect(Collectors.toList());
    }

    /**
     * 未保存配置时按批次策略推导默认配置（拆分方式 + 介质 + 分页 + 自动结构）。
     */
    private DeliveryPrintConfig buildDefaultConfig(DeliveryOrder delivery) {
        String splitMode = DeliveryPrintSplitMode.fromScopeType(delivery.getScopeType()).getCode();
        String mediaType = DeliveryPrintRule.DEFAULT_MEDIA_TYPE;
        Integer rowsPerPage = normalizeRowsPerPage(null, mediaType, splitMode);
        List<DeliveryOrderDetail> details = activeDetails(delivery.getId());
        DeliveryPrintStructure structure = buildStructure(autoOrder(details), rowsPerPage);
        return DeliveryPrintConfig.builder()
                .deliveryOrderId(delivery.getId())
                .splitMode(splitMode)
                .mediaType(mediaType)
                .rowsPerPage(rowsPerPage)
                .structureJson(structure.toJson())
                .autoGenerated(Boolean.TRUE)
                .build();
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
