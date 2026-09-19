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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.domain.Customer;
import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.CustomerSkuMapping;
import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.domain.DeliveryPrintLog;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.mapper.DeliveryPrintLogMapper;
import com.lin.distribution.util.PrintBizKeys;
import com.lin.distribution.util.ShiftCodes;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import com.lin.distribution.vo.DeliveryPointViewVO;
import com.lin.distribution.vo.PrintManifestVO;
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
 * <p>两类视图都不落物理明细（设计 §3.1）：D-055 主口径由 {@code t_sale_order_detail} 实时聚合，
 * 无订单数据的历史日期回退 {@code t_delivery_order_detail} + {@code t_delivery_source_item} 台账口径。</p>
 *
 * <ul>
 *   <li><b>客户日总表</b>：标准品名+规格+单位 聚合、各点小计、不显价不拆价（D-027/28）；</li>
 *   <li><b>矩阵总表</b>：行=菜品（订单明细五元组合并行，沿用 D-024 不同价必拆行）、
 *       列=启用配送点（含空列）、格=(行×点) 应送量透视，
 *       打印前跑恒等式 {@code 行应送 == Σ格} 自检（D-047）。</li>
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
    private final DeliveryPrintLogMapper deliveryPrintLogMapper;

    // ==================== 打印分界登记（D-055） ====================

    @Override
    public java.util.Date markPrinted(Long customerId, String deliveryDate, Long customerDeptId, Long templateId) {
        if (customerId == null || StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("打印登记须指定客户与配送日期");
        }
        java.util.Date now = new java.util.Date();
        String operator = currentUsername();
        deliveryPrintLogMapper.insertPrintLog(DeliveryPrintLog.builder()
                .customerId(customerId)
                .deliveryDate(LocalDate.parse(deliveryDate))
                .customerDeptId(customerDeptId)
                .printTime(now)
                .printBy(operator)
                .templateId(templateId)
                .createBy(operator)
                .createTime(now)
                .build());
        return now;
    }

    @Override
    public boolean isPrinted(Long customerId, String deliveryDate, Long customerDeptId) {
        if (customerId == null || StringUtils.isBlank(deliveryDate)) {
            return false;
        }
        return deliveryPrintLogMapper.countPrinted(customerId, LocalDate.parse(deliveryDate), customerDeptId) > 0;
    }

    /**
     * 当日打印清单（PT-2）：扁平行按客户聚合 → 总单主体 + 各点条目；
     * printed 逐项查打印分界（单日客户/点数量级小，不做 IN 批查）。
     * num 聚合 = 应送合计（与点单 tab 口径一致，纯退货行 num=0 不影响）。
     */
    @Override
    public List<PrintManifestVO> selectPrintManifest(String deliveryDate) {
        if (StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("配送日期不能为空");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(deliveryDate.trim().substring(0, 10));
        } catch (Exception e) {
            throw new ServiceException("配送日期格式非法，应为 yyyy-MM-dd");
        }
        List<PrintManifestVO.Row> rows = saleOrderDetailMapper.selectPrintManifestRows(date);
        Map<Long, PrintManifestVO> byCustomer = new LinkedHashMap<>();
        Map<String, PrintManifestVO.PointEntry> pointByKey = new LinkedHashMap<>();
        for (PrintManifestVO.Row row : rows) {
            if (row.getCustomerId() == null) {
                continue;
            }
            PrintManifestVO vo = byCustomer.computeIfAbsent(row.getCustomerId(), k -> PrintManifestVO.builder()
                    .customerId(k)
                    .customerName(StringUtils.defaultIfBlank(row.getCustomerName(), "客户" + k))
                    .matrixBizKey(PrintBizKeys.matrix(k, deliveryDate))
                    .matrixPrinted(Boolean.FALSE)
                    .points(new ArrayList<>())
                    .build());
            String key = String.valueOf(row.getDeptId() == null ? "_" : row.getDeptId());
            PrintManifestVO.PointEntry point = pointByKey.get(vo.getCustomerId() + "|" + key);
            if (point == null) {
                point = PrintManifestVO.PointEntry.builder()
                        .deptId(row.getDeptId())
                        .deptName(StringUtils.defaultIfBlank(row.getDeptName(), "未分配"))
                        .bizKey(PrintBizKeys.point(row.getCustomerId(), row.getDeptId(), deliveryDate))
                        .printed(Boolean.FALSE)
                        .totalNum(BigDecimal.ZERO)
                        .build();
                pointByKey.put(vo.getCustomerId() + "|" + key, point);
                vo.getPoints().add(point);
            }
            point.setTotalNum(point.getTotalNum().add(row.getNum() == null ? BigDecimal.ZERO : row.getNum()));
        }
        // 打印分界批量判定：总单按客户维度（deptId=null），点单按 客户+点
        for (PrintManifestVO vo : byCustomer.values()) {
            vo.setMatrixPrinted(isPrinted(vo.getCustomerId(), deliveryDate, null));
            for (PrintManifestVO.PointEntry point : vo.getPoints()) {
                point.setPrinted(isPrinted(vo.getCustomerId(), deliveryDate, point.getDeptId()));
            }
        }
        return new ArrayList<>(byCustomer.values());
    }

    /**
     * 当日全部客户总览（D-071 卡片视角）：D-055 送货单视图化后以已确认订单（status&gt;=1）为口径，
     * 按客户聚合单数/点数/数量/金额与各状态数。
     */
    @Override
    public List<com.lin.distribution.vo.DeliveryOrderOverviewVO> selectOrderOverviewByDate(String deliveryDate) {
        if (StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("配送日期不能为空");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(deliveryDate.trim().substring(0, 10));
        } catch (Exception e) {
            throw new ServiceException("配送日期格式非法，应为 yyyy-MM-dd");
        }
        return saleOrderDetailMapper.selectOrderOverviewByDate(date);
    }

    /** 打印登记可能无安全上下文（内部调用），回落 system */
    private String currentUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    // ==================== 客户日总表（D-027/28 + D-055 视图化） ====================

    @Override
    public List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate) {
        // 主口径（D-055）：订单明细聚合；无订单数据时回退 D-055 前的送货单台账口径（历史日期只读）
        List<DeliveryBatchViewVO.Row> rows = deliveryBatchMapper.selectBatchViewRowsFromOrder(customerId, deliveryDate);
        if (rows.isEmpty()) {
            rows = deliveryBatchMapper.selectBatchViewRowsBySource(customerId, deliveryDate);
        }
        if (rows.isEmpty()) {
            // 历史单回退：无 source_item 台账时按送货明细行聚合
            rows = deliveryBatchMapper.selectBatchViewRowsByDetail(customerId, deliveryDate);
        }

        // 行键 = 标准品名 + 规格 + 单位（P0-A 修正：原只到品名会把同品名不同规格/单位错并成一行）；
        // 不显价也不因价拆行（D-028），故价格不进键
        // s35：启用班次的客户，点小计按「配送点×班次」展开（与总单矩阵列同口径）
        Customer customer = customerMapper.selectCustomerById(customerId);
        boolean shiftEnabled = customer != null && BooleanUtils.isTrue(customer.getShiftEnabled());
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

            String shift = normalizeShift(shiftEnabled, row.getShiftCode());
            String deptKey = ShiftCodes.cellKey(row.getDeptId(), shift);
            DeliveryBatchViewVO.DeptRow dept = vo.getDepts().stream()
                    .filter(d -> ShiftCodes.cellKey(d.getDeptId(), d.getShiftCode()).equals(deptKey))
                    .findFirst()
                    .orElseGet(() -> {
                        DeliveryBatchViewVO.DeptRow d = new DeliveryBatchViewVO.DeptRow();
                        d.setDeptId(row.getDeptId());
                        d.setShiftCode(shift);
                        d.setDeptName(ShiftCodes.columnName(row.getDeptName(), shift));
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
        // 展示口径与矩阵/配货一致：已确认及以后（status>=1）都算应送，仅排除草稿与已删单
        return saleOrderDetailMapper.selectValidByCustomerPointDateForView(customerId, customerDeptId,
                LocalDate.parse(deliveryDate));
    }

    @Override
    public List<DeliveryPointViewVO> selectPointViewAll(Long customerId, String deliveryDate) {
        if (customerId == null || StringUtils.isBlank(deliveryDate)) {
            throw new ServiceException("客户与配送日期不能为空");
        }
        List<SaleOrderDetail> rows = saleOrderDetailMapper.selectValidByCustomerDateForView(customerId,
                LocalDate.parse(deliveryDate));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 按（配送点）保序分组：SQL 已按 cd.code, cd.id 排序（与矩阵列序一致），组内行保持 sort 升序；
        // 「按当天实际情况」——当天没有单的点不出 tab
        LinkedHashMap<Long, DeliveryPointViewVO> byDept = new LinkedHashMap<>();
        for (SaleOrderDetail d : rows) {
            Long deptId = d.getCustomerDeptId();
            if (deptId == null) {
                continue;
            }
            DeliveryPointViewVO group = byDept.computeIfAbsent(deptId, k -> DeliveryPointViewVO.builder()
                    .deptId(deptId)
                    .deptName(d.getCustomerDeptName())
                    .rowCount(0)
                    .totalNum(BigDecimal.ZERO)
                    .totalActual(BigDecimal.ZERO)
                    .rows(new ArrayList<>())
                    .build());
            group.getRows().add(d);
            group.setRowCount(group.getRows().size());
            group.setTotalNum(group.getTotalNum().add(d.getNum() == null ? BigDecimal.ZERO : d.getNum()));
            group.setTotalActual(group.getTotalActual()
                    .add(d.getActualNum() == null ? BigDecimal.ZERO : d.getActualNum()));
            if (StringUtils.isBlank(group.getDeptName()) && StringUtils.isNotBlank(d.getCustomerDeptName())) {
                group.setDeptName(d.getCustomerDeptName());
            }
        }
        // 点名兑底回查（防御历史数据点被删）
        for (DeliveryPointViewVO group : byDept.values()) {
            if (StringUtils.isBlank(group.getDeptName())) {
                CustomerDept dept = customerDeptMapper.selectCustomerDeptById(group.getDeptId());
                group.setDeptName(dept == null ? "配送点" + group.getDeptId() : dept.getName());
            }
        }
        return new ArrayList<>(byDept.values());
    }

    // ==================== 矩阵总表（D-044~D-053 + D-055 视图化） ====================

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
        // 班次（s35）：仅启用班次的客户参与列/格展开，其余客户全链路行为与引入前一致
        Customer customer = customerMapper.selectCustomerById(customerId);
        boolean shiftEnabled = customer != null && BooleanUtils.isTrue(customer.getShiftEnabled());

        DeliveryBatch batch = deliveryBatchMapper.selectByCustomerAndDate(customerId, deliveryDate);
        if (batch != null) {
            vo.setBatchId(batch.getId());
            vo.setScopeType(batch.getScopeType());
        }

        List<DeliveryMatrixVO.DetailRow> details = deliveryBatchMapper.selectMatrixDetailsFromOrder(customerId, deliveryDate);
        List<DeliveryMatrixVO.CellRow> cellRows = deliveryBatchMapper.selectMatrixCellsFromOrder(customerId, deliveryDate);
        boolean legacy = false;
        if (details.isEmpty()) {
            // 历史回退：D-055 前生成的送货单（订单明细口径无数据）按送货明细行 + source_item 台账展示，只读
            details = deliveryBatchMapper.selectMatrixDetails(customerId, deliveryDate);
            cellRows = deliveryBatchMapper.selectMatrixCells(customerId, deliveryDate);
            legacy = !details.isEmpty();
        }
        if (details.isEmpty()) {
            return vo;
        }
        vo.setHistoryFallback(legacy);

        // 布局：批次快照优先；无快照或快照无列（历史批次/未走过生成）按主数据实时推导，只读不落库（D-045）
        DeliveryMatrixLayout rawSnapshot = batch == null ? null : DeliveryMatrixLayout.fromJson(batch.getLayoutJson());
        boolean usable = rawSnapshot != null && !rawSnapshot.getColumns().isEmpty();
        DeliveryMatrixLayout snapshot = usable ? rawSnapshot : null;
        vo.setLayoutDerived(!usable);
        DeliveryMatrixLayout layout = composeLayout(snapshot, customerId, details, cellRows, !usable, shiftEnabled);
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
                    .shiftCode(c.getShiftCode())
                    .adHoc(c.isAdHoc())
                    .hasData(Boolean.FALSE)
                    .blockNo(colsPerPage > 0 ? i / colsPerPage + 1 : 1)
                    .build());
        }
        vo.setColumns(columns);
        vo.setColBlocks(colsPerPage > 0 ? Math.max(1, (int) Math.ceil(columns.size() / (double) colsPerPage)) : 1);

        // 格：行身份键 → (列键 → 数量)。列键 = deptId 或 deptId#班次（s35）；主口径按五元组对位，历史口径按送货明细行ID对位
        Map<String, Map<String, BigDecimal>> cellIndex = new LinkedHashMap<>();
        for (DeliveryMatrixVO.CellRow cell : cellRows) {
            if (cell.getDeptId() == null) {
                continue;
            }
            String key = legacy ? legacyRowKey(cell.getDetailId())
                    : matrixRowKey(cell.getSkuId(), cell.getProductName(), cell.getSpec(), cell.getUnit(), cell.getPrice());
            if (key == null) {
                continue;
            }
            cellIndex.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .merge(ShiftCodes.cellKey(cell.getDeptId(), normalizeShift(shiftEnabled, cell.getShiftCode())),
                            nvl(cell.getQuantity()), BigDecimal::add);
        }
        // 历史单完全无点级分配台账 = 旧旧数据，点列打 —（D-051）
        vo.setHistoryFallback(legacy && cellIndex.isEmpty());

        Map<Long, String> aliasBySku = customerAliasBySku(customerId);

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (DeliveryMatrixVO.DetailRow detail : details) {
            Map<String, BigDecimal> rowCells = cellIndex.getOrDefault(
                    legacy ? legacyRowKey(detail.getDetailId())
                            : matrixRowKey(detail.getSkuId(), detail.getProductName(), detail.getSpec(),
                            detail.getUnit(), detail.getPrice()),
                    Collections.emptyMap());
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
                BigDecimal qty = rowCells.get(ShiftCodes.cellKey(column.getDeptId(), column.getShiftCode()));
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
        // 展示优化（D-055）：当日无单的列排到最后（有数据列保持原始顺序在前）
        List<DeliveryMatrixVO.ColumnVO> sortedColumns = new ArrayList<>();
        List<DeliveryMatrixVO.ColumnVO> emptyColumns = new ArrayList<>();
        for (DeliveryMatrixVO.ColumnVO c : columns) {
            (Boolean.TRUE.equals(c.getHasData()) ? sortedColumns : emptyColumns).add(c);
        }
        // 重排后重算 blockNo（列分页按新顺序）
        int cp = colsPerPage > 0 ? colsPerPage : Math.max(sortedColumns.size(), 1);
        for (int i = 0; i < sortedColumns.size(); i++) {
            sortedColumns.get(i).setBlockNo(i / cp + 1);
        }
        sortedColumns.addAll(emptyColumns);
        for (int i = sortedColumns.size() - emptyColumns.size(); i < sortedColumns.size(); i++) {
            sortedColumns.get(i).setBlockNo(i / cp + 1);
        }
        vo.setColumns(sortedColumns);
        vo.setTotalQuantity(grandTotal);
        return vo;
    }

    // ==================== 布局快照（D-045/D-053） ====================
    // D-055 视图化后「生成成功后定格布局」的写入路径（refreshLayout）已随生成服务一并退役：
    // 矩阵完全按 订单明细 + 启用点 实时推导；历史批次已有的 layout_json 快照仍优先采用。

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
                                               boolean includeValidDepts,
                                               boolean shiftEnabled) {
        DeliveryMatrixLayout src = (base == null ? DeliveryMatrixLayout.newInstance() : base).normalize();

        // ---------- 列：快照列 → 启用点（×班次）补列 → adHoc 临时补列 ----------
        // 列身份键 = deptId 或 deptId#班次（s35）：同一配送点的白/夜班是两个独立列
        List<Column> columns = new ArrayList<>();
        Set<String> deptSeen = new LinkedHashSet<>();
        for (Column c : src.getColumns()) {
            if (c.getDeptId() == null) {
                continue;
            }
            // s35：快照列班次与格值同一口径归一化（启用→空班次归白班，停用→清空）。
            // 否则旧快照列（无班次键）与归一化后的格值键错位：老列恒空、量全部落进 adHoc 补列
            String shift = shiftEnabled ? ShiftCodes.orDefault(c.getShiftCode()) : "";
            if (!deptSeen.add(ShiftCodes.cellKey(c.getDeptId(), shift))) {
                continue;
            }
            columns.add(Column.builder().deptId(c.getDeptId()).code(c.getCode()).name(c.getName())
                    .shiftCode(shift)
                    .adHoc(c.isAdHoc()).build());
        }
        if (includeValidDepts) {
            for (Column c : nullSafe(deliveryBatchMapper.selectMatrixColumns(customerId))) {
                if (c.getDeptId() == null) {
                    continue;
                }
                // 启用班次的客户按该点声明的班次展开；未声明班次（或客户未启用）保持单列
                List<String> shifts = shiftEnabled ? ShiftCodes.parse(c.getShiftCodes()) : new ArrayList<>();
                if (shifts.isEmpty()) {
                    shifts.add("");
                }
                for (String shift : shifts) {
                    if (deptSeen.add(ShiftCodes.cellKey(c.getDeptId(), shift))) {
                        columns.add(Column.builder().deptId(c.getDeptId()).code(c.getCode())
                                .name(ShiftCodes.columnName(c.getName(), shift))
                                .shiftCode(shift)
                                .adHoc(Boolean.FALSE).build());
                    }
                }
            }
        }
        // 停用点/未声明班次当日有单 → 临时补列并告警，绝不静默丢量（D-053）
        for (DeliveryMatrixVO.CellRow cell : nullSafe(cells)) {
            if (cell.getDeptId() == null) {
                continue;
            }
            String shift = normalizeShift(shiftEnabled, cell.getShiftCode());
            if (!deptSeen.add(ShiftCodes.cellKey(cell.getDeptId(), shift))) {
                continue;
            }
            CustomerDept dept = customerDeptMapper.selectCustomerDeptById(cell.getDeptId());
            columns.add(Column.builder()
                    .deptId(cell.getDeptId())
                    .code(dept == null ? null : dept.getCode())
                    .name(dept == null ? "已停用配送点" : ShiftCodes.columnName(dept.getName(), shift))
                    .shiftCode(shift)
                    .adHoc(Boolean.TRUE)
                    .build());
            log.warn("矩阵总表临时补列：客户={} 配送点={} 班次={}（不在布局快照内，多为已停用点或未声明班次但当日有单）",
                    customerId, cell.getDeptId(), shift);
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

    /**
     * 矩阵行身份键（D-055 主口径）：五元组 = 档分组键（sku/品名快照 + 规格 + 单位）+ 单价。
     * 行查询与格查询回传同一组字段，故两侧算出的键一致（不同价必拆行 D-024）。
     */
    private static String matrixRowKey(Long skuId, String productName, String spec, String unit, BigDecimal price) {
        return DeliveryMatrixLayout.groupKey(skuId, productName, spec, unit)
                + "|" + DeliveryMatrixLayout.priceKey(price);
    }

    /** 历史口径行身份键（D-055 前送货明细行，行身份即 detail.id，D-047 不重新聚合） */
    private static String legacyRowKey(Long detailId) {
        return detailId == null ? null : "d:" + detailId;
    }

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

    /**
     * 班次归一化：启用班次的客户把空班次（历史单）归白班；未启用的客户一律置空（不参与列/格键）。
     */
    private String normalizeShift(boolean shiftEnabled, String shiftCode) {
        return shiftEnabled ? ShiftCodes.orDefault(shiftCode) : "";
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
