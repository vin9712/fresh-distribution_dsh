package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryMatrixLayout.Column;
import com.lin.distribution.vo.DeliveryMatrixLayout.Tier;
import com.lin.distribution.vo.DeliveryMatrixLayout.TierGroup;
import com.lin.distribution.vo.DeliveryMatrixVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 配送批次查询服务实现（S14 §6.1 / §八，D-027/D-028；矩阵总表 D-044~D-053）
 *
 * <p>两类视图都不落物理明细（设计 §3.1）：新模型由 {@code t_delivery_source_item} 台账实时聚合，
 * 历史单（无台账）回退送货明细行聚合。</p>
 *
 * <ul>
 *   <li><b>客户日总表</b>：标准品名+规格+单位 聚合、各点小计、不显价不拆价（D-027/28）；</li>
 *   <li><b>矩阵总表</b>：行=送货明细行（沿用 D-024 五元组，不同价必拆行）、
 *       列=批次布局快照的配送点（含空列）、格=(明细行×点) 分配量透视，
 *       打印前跑恒等式 {@code detail.num == Σ格} 自检（D-047）。</li>
 * </ul>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryBatchServiceImpl implements DeliveryBatchService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DeliveryBatchMapper deliveryBatchMapper;
    private final CustomerDeptMapper customerDeptMapper;
    private final CustomerMapper customerMapper;
    private final CustomerSkuMappingMapper customerSkuMappingMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;

    // ==================== 客户日总表（D-027/28） ====================

    @Override
    public List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate) {
        List<DeliveryBatchViewVO.Row> rows = deliveryBatchMapper.selectBatchViewRowsBySource(customerId, deliveryDate);
        if (rows.isEmpty()) {
            // 历史单回退：无 source_item 台账时按送货明细行聚合
            rows = deliveryBatchMapper.selectBatchViewRowsByDetail(customerId, deliveryDate);
        }

        // 行键 = 标准品名 + 规格 + 单位（P0-A 修正：原只到品名会把同品名不同规格/单位错并成一行）；
        // 不显价也不因价拆行（D-028），故价格不进键
        Map<String, DeliveryBatchViewVO> byProduct = new LinkedHashMap<>();
        for (DeliveryBatchViewVO.Row row : rows) {
            String key = (row.getSkuId() == null ? "name:" + row.getProductName() : "sku:" + row.getSkuId())
                    + "|" + StringUtils.trimToEmpty(row.getSpec())
                    + "|" + StringUtils.trimToEmpty(row.getUnit());
            DeliveryBatchViewVO vo = byProduct.computeIfAbsent(key, k -> {
                DeliveryBatchViewVO v = new DeliveryBatchViewVO();
                v.setSkuId(row.getSkuId());
                v.setProductName(row.getProductName());
                v.setSpec(row.getSpec());
                v.setUnit(row.getUnit());
                v.setTotalQuantity(BigDecimal.ZERO);
                return v;
            });
            BigDecimal qty = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
            vo.setTotalQuantity(vo.getTotalQuantity().add(qty));

            String deptKey = String.valueOf(row.getDeptId());
            DeliveryBatchViewVO.DeptRow dept = vo.getDepts().stream()
                    .filter(d -> String.valueOf(d.getDeptId()).equals(deptKey))
                    .findFirst()
                    .orElseGet(() -> {
                        DeliveryBatchViewVO.DeptRow d = new DeliveryBatchViewVO.DeptRow();
                        d.setDeptId(row.getDeptId());
                        d.setDeptName(row.getDeptName());
                        d.setQuantity(BigDecimal.ZERO);
                        vo.getDepts().add(d);
                        return d;
                    });
            dept.setQuantity(dept.getQuantity().add(qty));
        }
        return new ArrayList<>(byProduct.values());
    }

    // ==================== 点单视图（D-055：客户+日期+点，订单明细行含标记） ====================

    @Override
    public List<SaleOrderDetail> selectPointView(Long customerId, Long customerDeptId, String deliveryDate) {
        if (customerId == null || customerDeptId == null || StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("客户/配送点/配送日期不能为空");
        }
        return saleOrderDetailMapper.selectValidByCustomerPointDate(customerId, customerDeptId,
                LocalDate.parse(deliveryDate));
    }

    // ==================== 矩阵总表（D-044~D-053） ====================

    @Override
    public DeliveryMatrixVO selectMatrix(Long customerId, String deliveryDate) {
        if (customerId == null || StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("客户与配送日期不能为空");
        }
        DeliveryMatrixVO vo = new DeliveryMatrixVO();
        vo.setCustomerId(customerId);
        vo.setDeliveryDate(deliveryDate);
        vo.setPrintForm(DeliveryMatrixLayout.FORM_MATRIX);
        vo.setColsPerPage(DeliveryMatrixLayout.DEFAULT_COLS_PER_PAGE);
        vo.setColBlocks(1);
        fillCustomer(vo, customerId);

        DeliveryBatch batch = deliveryBatchMapper.selectByCustomerAndDate(customerId, deliveryDate);
        if (batch != null) {
            vo.setBatchId(batch.getId());
            vo.setScopeType(batch.getScopeType());
        }

        List<DeliveryMatrixVO.DetailRow> details = deliveryBatchMapper.selectMatrixDetails(customerId, deliveryDate);
        List<DeliveryMatrixVO.CellRow> cellRows = deliveryBatchMapper.selectMatrixCells(customerId, deliveryDate);
        if (details.isEmpty()) {
            return vo;
        }

        // 布局：批次快照优先；无快照或快照无列（历史批次/未走过生成）按主数据实时推导，只读不落库（D-045）
        DeliveryMatrixLayout rawSnapshot = batch == null ? null : DeliveryMatrixLayout.fromJson(batch.getLayoutJson());
        boolean usable = rawSnapshot != null && !rawSnapshot.getColumns().isEmpty();
        DeliveryMatrixLayout snapshot = usable ? rawSnapshot : null;
        vo.setLayoutDerived(!usable);
        DeliveryMatrixLayout layout = composeLayout(snapshot, customerId, details, cellRows, !usable);
        vo.setPrintForm(layout.getPrintForm());
        vo.setColsPerPage(layout.getColsPerPage());
        // 已存快照时回显批次落库版本（读取路径的 adHoc 补列不刷版本，留给下次生成定格）
        vo.setLayoutVersion(usable ? snapshot.getLayoutVersion() : layout.getLayoutVersion());

        // 列（含空列与 adHoc 临时补列），blockNo 为横向列分页粒度
        List<DeliveryMatrixVO.ColumnVO> columns = new ArrayList<>();
        int colsPerPage = layout.getColsPerPage() == null || layout.getColsPerPage() <= 0
                ? layout.getColumns().size() : layout.getColsPerPage();
        for (int i = 0; i < layout.getColumns().size(); i++) {
            Column c = layout.getColumns().get(i);
            columns.add(DeliveryMatrixVO.ColumnVO.builder()
                    .deptId(c.getDeptId())
                    .code(c.getCode())
                    .name(c.getName())
                    .adHoc(c.isAdHoc())
                    .hasData(Boolean.FALSE)
                    .blockNo(colsPerPage > 0 ? i / colsPerPage + 1 : 1)
                    .build());
        }
        vo.setColumns(columns);
        vo.setColBlocks(colsPerPage > 0 ? Math.max(1, (int) Math.ceil(columns.size() / (double) colsPerPage)) : 1);

        // 格：detailId → (deptId → 数量)
        Map<Long, Map<Long, BigDecimal>> cellIndex = new LinkedHashMap<>();
        for (DeliveryMatrixVO.CellRow cell : cellRows) {
            if (cell.getDetailId() == null || cell.getDeptId() == null) {
                continue;
            }
            cellIndex.computeIfAbsent(cell.getDetailId(), k -> new LinkedHashMap<>())
                    .merge(cell.getDeptId(), nvl(cell.getQuantity()), BigDecimal::add);
        }
        // 全部明细都无台账 = 历史单，点列打 —（D-051）
        vo.setHistoryFallback(cellIndex.isEmpty());

        Map<Long, String> aliasBySku = customerAliasBySku(customerId);

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (DeliveryMatrixVO.DetailRow detail : details) {
            Map<Long, BigDecimal> rowCells = cellIndex.getOrDefault(detail.getDetailId(), Collections.emptyMap());
            String groupKey = DeliveryMatrixLayout.groupKey(detail.getSkuId(), detail.getProductName(),
                    detail.getSpec(), detail.getUnit());
            TierGroup group = layout.tierGroup(groupKey);
            int tierCount = group == null ? 1 : group.safeTiers().size();
            Integer rank = tierRankOf(group, detail.getPrice());

            BigDecimal rowTotal = rowCells.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean rowHistory = rowCells.isEmpty();
            if (rowHistory) {
                // 历史单无点级分配台账：合计取明细行数量，各列留空
                rowTotal = nvl(detail.getNum());
            }
            boolean identityOk = rowHistory || nvl(detail.getNum()).compareTo(rowTotal) == 0;

            List<BigDecimal> columnValues = new ArrayList<>(columns.size());
            for (DeliveryMatrixVO.ColumnVO column : columns) {
                BigDecimal qty = rowCells.get(column.getDeptId());
                columnValues.add(qty);
                if (qty != null && qty.compareTo(BigDecimal.ZERO) != 0) {
                    column.setHasData(Boolean.TRUE);
                }
            }

            String stdName = StringUtils.defaultIfBlank(detail.getStdProductName(), detail.getProductName());
            DeliveryMatrixVO.RowVO row = DeliveryMatrixVO.RowVO.builder()
                    .detailId(detail.getDetailId())
                    .deliveryId(detail.getDeliveryId())
                    .deliveryCode(detail.getDeliveryCode())
                    .docKind(detail.getDocKind())
                    .skuId(detail.getSkuId())
                    .productName(stdName)
                    .customerAlias(detail.getSkuId() == null ? null : aliasBySku.get(detail.getSkuId()))
                    .spec(detail.getSpec())
                    .unit(detail.getUnit())
                    .price(detail.getPrice())
                    .num(detail.getNum())
                    .groupKey(groupKey)
                    .tierRank(rank)
                    .tierCount(tierCount)
                    // 品名保持干净，档位标注进备注列（D-046/D-053 修订：纸面不打价，同名多行以备注列区分）
                    .displayProductName(DeliveryMatrixLayout.displayProductName(stdName))
                    .remark(DeliveryMatrixLayout.tierRemark(rank, tierCount))
                    .cells(new LinkedHashMap<>(rowCells))
                    .columnValues(columnValues)
                    .totalQuantity(rowTotal)
                    .identityOk(identityOk)
                    .build();
            vo.getRows().add(row);
            grandTotal = grandTotal.add(rowTotal);
            if (!identityOk) {
                vo.setIdentityOk(Boolean.FALSE);
                vo.getMismatches().add(DeliveryMatrixVO.MismatchVO.builder()
                        .detailId(detail.getDetailId())
                        .deliveryCode(detail.getDeliveryCode())
                        .productName(stdName)
                        .num(detail.getNum())
                        .cellSum(rowTotal)
                        .build());
            }
        }
        vo.setTotalQuantity(grandTotal);
        return vo;
    }

    // ==================== 布局快照（D-045/D-053） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryMatrixLayout refreshLayout(DeliveryBatch batch, String operator) {
        if (batch == null || batch.getId() == null) {
            return null;
        }
        String deliveryDate = batch.getDeliveryDate() == null ? null : batch.getDeliveryDate().toString();
        List<DeliveryMatrixVO.DetailRow> details = deliveryBatchMapper
                .selectMatrixDetails(batch.getCustomerId(), deliveryDate);
        List<DeliveryMatrixVO.CellRow> cells = deliveryBatchMapper
                .selectMatrixCells(batch.getCustomerId(), deliveryDate);

        DeliveryMatrixLayout snapshot = DeliveryMatrixLayout.fromJson(batch.getLayoutJson());
        // 写入路径：启用点全部并进列（新点追加末尾），档号 append-only
        DeliveryMatrixLayout target = composeLayout(snapshot, batch.getCustomerId(), details, cells, true);
        if (snapshot != null && Objects.equals(snapshot.getLayoutVersion(), target.getLayoutVersion())) {
            // 内容无变化：不刷版本、不落库（幂等生成不产生布局噪声）
            return target;
        }
        DeliveryBatch update = DeliveryBatch.builder().id(batch.getId()).layoutJson(target.toJson()).build();
        update.setUpdateBy(operator);
        deliveryBatchMapper.updateDeliveryBatch(update);
        batch.setLayoutJson(target.toJson());
        return target;
    }

    /**
     * 组装目标布局：列与价档均 append-only（已有列顺序、点名快照、档号一律保留）。
     *
     * @param base             既有快照（null=新建）
     * @param includeValidDepts 是否把客户启用点并进列（写入路径 true；读取路径仅在有快照时 false，
     *                          保证"只用快照、不回落实时主数据"，D-045 规则 3）
     * @return 目标布局（内容有变则 layoutVersion+1 并刷新 snapshotAt）
     */
    private DeliveryMatrixLayout composeLayout(DeliveryMatrixLayout base, Long customerId,
                                               List<DeliveryMatrixVO.DetailRow> details,
                                               List<DeliveryMatrixVO.CellRow> cells,
                                               boolean includeValidDepts) {
        DeliveryMatrixLayout src = (base == null ? DeliveryMatrixLayout.newInstance() : base).normalize();

        // ---------- 列：快照列 → 启用点补列 → adHoc 临时补列 ----------
        List<Column> columns = new ArrayList<>();
        Set<Long> deptSeen = new LinkedHashSet<>();
        for (Column c : src.getColumns()) {
            if (c.getDeptId() != null && deptSeen.add(c.getDeptId())) {
                columns.add(Column.builder().deptId(c.getDeptId()).code(c.getCode()).name(c.getName())
                        .adHoc(c.isAdHoc()).build());
            }
        }
        if (includeValidDepts) {
            for (Column c : nullSafe(deliveryBatchMapper.selectMatrixColumns(customerId))) {
                if (c.getDeptId() != null && deptSeen.add(c.getDeptId())) {
                    columns.add(Column.builder().deptId(c.getDeptId()).code(c.getCode()).name(c.getName())
                            .adHoc(Boolean.FALSE).build());
                }
            }
        }
        // 停用点当日有单 → 临时补列并告警，绝不静默丢量（D-053）
        for (Long deptId : distinctCellDeptIds(cells)) {
            if (deptSeen.add(deptId)) {
                CustomerDept dept = customerDeptMapper.selectCustomerDeptById(deptId);
                columns.add(Column.builder()
                        .deptId(deptId)
                        .code(dept == null ? null : dept.getCode())
                        .name(dept == null ? "已停用配送点" : dept.getName())
                        .adHoc(Boolean.TRUE)
                        .build());
                log.warn("矩阵总表临时补列：客户={} 配送点={}（不在布局快照内，多为已停用但当日有单）", customerId, deptId);
            }
        }

        // ---------- 价档：快照档 → 新价按升序追加档号（已有档号绝不重排） ----------
        Map<String, TierGroup> groups = new LinkedHashMap<>();
        for (TierGroup g : src.getPriceTiers()) {
            if (g == null || g.getGroupKey() == null) {
                continue;
            }
            TierGroup copy = TierGroup.builder()
                    .groupKey(g.getGroupKey())
                    .productName(g.getProductName())
                    .spec(g.getSpec())
                    .unit(g.getUnit())
                    .tiers(g.safeTiers().stream()
                            .map(t -> Tier.builder().rank(t.getRank()).price(t.getPrice()).priceKey(t.getPriceKey()).build())
                            .collect(Collectors.toList()))
                    .build();
            groups.put(g.getGroupKey(), copy);
        }
        for (DeliveryMatrixVO.DetailRow detail : nullSafe(details)) {
            String key = DeliveryMatrixLayout.groupKey(detail.getSkuId(), detail.getProductName(),
                    detail.getSpec(), detail.getUnit());
            TierGroup group = groups.computeIfAbsent(key, k -> TierGroup.builder()
                    .groupKey(k)
                    .productName(StringUtils.defaultIfBlank(detail.getStdProductName(), detail.getProductName()))
                    .spec(detail.getSpec())
                    .unit(detail.getUnit())
                    .tiers(new ArrayList<>())
                    .build());
            String priceKey = DeliveryMatrixLayout.priceKey(detail.getPrice());
            boolean exists = group.safeTiers().stream()
                    .anyMatch(t -> priceKey.equals(t.getPriceKey())
                            || (t.getPrice() != null && nvl(detail.getPrice()).compareTo(t.getPrice()) == 0));
            if (!exists) {
                int rank = group.safeTiers().stream()
                        .map(Tier::getRank).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 1;
                group.safeTiers().add(Tier.builder().rank(rank).price(detail.getPrice()).priceKey(priceKey).build());
                group.getTiers().sort((a, b) -> Integer.compare(nvlRank(a), nvlRank(b)));
            }
        }

        // ---------- 变化判定：列或档任一不同则版本 +1 ----------
        DeliveryMatrixLayout target = DeliveryMatrixLayout.builder()
                .printForm(src.getPrintForm())
                .colsPerPage(src.getColsPerPage())
                .rowsPerPage(src.getRowsPerPage())
                .layoutVersion(src.getLayoutVersion())
                .snapshotAt(src.getSnapshotAt())
                .columns(columns)
                .priceTiers(new ArrayList<>(groups.values()))
                .build();
        if (base == null) {
            // 首次建立快照：版本从 1 起（无“从无列→有列”的伪变化）
            target.setLayoutVersion(1);
            target.setSnapshotAt(LocalDateTime.now().format(ISO));
        } else if (!signature(target).equals(signature(src))) {
            target.setLayoutVersion(src.getLayoutVersion() + 1);
            target.setSnapshotAt(LocalDateTime.now().format(ISO));
        }
        return target;
    }

    /** 布局内容签名（不含版本号与时间戳，用于"是否真的变了"判定） */
    private String signature(DeliveryMatrixLayout layout) {
        String cols = layout.getColumns().stream()
                .map(c -> c.getDeptId() + ":" + StringUtils.trimToEmpty(c.getName()) + ":" + c.isAdHoc())
                .collect(Collectors.joining(","));
        String tiers = layout.getPriceTiers().stream()
                .map(g -> g.getGroupKey() + "=" + g.safeTiers().stream()
                        .map(t -> t.getRank() + "@" + DeliveryMatrixLayout.priceKey(t.getPrice()))
                        .collect(Collectors.joining("/")))
                .collect(Collectors.joining(","));
        return cols + "||" + tiers + "||" + layout.getPrintForm() + "/" + layout.getColsPerPage() + "/" + layout.getRowsPerPage();
    }

    // ==================== 辅助 ====================

    private void fillCustomer(DeliveryMatrixVO vo, Long customerId) {
        Customer customer = customerMapper.selectCustomerById(customerId);
        if (customer != null) {
            vo.setCustomerName(StringUtils.defaultIfBlank(customer.getAlias(), customer.getName()));
        }
    }

    /** 客户 SKU 映射叫法（打印品名客户叫法优先，DESIGN 不变量 8） */
    private Map<Long, String> customerAliasBySku(Long customerId) {
        CustomerSkuMapping query = new CustomerSkuMapping();
        query.setCustomerId(customerId);
        return nullSafe(customerSkuMappingMapper.selectCustomerSkuMappingList(query)).stream()
                .filter(m -> m.getSkuId() != null && StringUtils.isNotBlank(m.getCustomerAlias()))
                .collect(Collectors.toMap(CustomerSkuMapping::getSkuId, CustomerSkuMapping::getCustomerAlias, (a, b) -> a));
    }

    /** 本行档号：按单价匹配档表（去尾零比较），匹配不到按单档处理（纸面不打档标） */
    private Integer tierRankOf(TierGroup group, BigDecimal price) {
        if (group == null || group.safeTiers().isEmpty()) {
            return null;
        }
        String priceKey = DeliveryMatrixLayout.priceKey(price);
        return group.safeTiers().stream()
                .filter(t -> priceKey.equals(t.getPriceKey())
                        || (t.getPrice() != null && nvl(price).compareTo(t.getPrice()) == 0))
                .map(Tier::getRank)
                .findFirst()
                .orElse(null);
    }

    private List<Long> distinctCellDeptIds(List<DeliveryMatrixVO.CellRow> cells) {
        return nullSafe(cells).stream()
                .map(DeliveryMatrixVO.CellRow::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private int nvlRank(Tier tier) {
        return tier.getRank() == null ? 0 : tier.getRank();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
