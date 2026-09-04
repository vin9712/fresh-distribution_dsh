package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.lin.distribution.domain.CustomerDept;
import com.lin.distribution.domain.DeliveryBatch;
import com.lin.distribution.domain.DeliveryPrintLog;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.CustomerSkuMappingMapper;
import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.mapper.DeliveryPrintLogMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.vo.DeliveryBatchViewVO;
import com.lin.distribution.vo.DeliveryMatrixLayout;
import com.lin.distribution.vo.DeliveryMatrixLayout.Column;
import com.lin.distribution.vo.DeliveryMatrixLayout.Tier;
import com.lin.distribution.vo.DeliveryMatrixLayout.TierGroup;
import com.lin.distribution.vo.DeliveryMatrixVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送批次视图测试：客户日总表（D-027/28）+ 矩阵总表（D-044~D-053）+ D-055 视图化取数口径。
 *
 * <p>矩阵覆盖：主口径（订单明细）优先、无订单数据回退历史送货单口径、空列保留、
 * 恒等式自检与不一致、同名不同价拆行打档标、档号 append-only 不重排、停用点临时补列（adHoc）、
 * 布局快照优先不回落实时主数据、无快照实时推导；另含点单/配货口径同源校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryBatchServiceImplTest {

    private static final Long CUSTOMER = 100L;
    private static final String DATE = "2026-08-28";
    private static final Long POINT_1 = 201L;
    private static final Long POINT_2 = 202L;
    private static final Long POINT_3 = 203L;

    @Mock
    private DeliveryBatchMapper deliveryBatchMapper;
    @Mock
    private CustomerDeptMapper customerDeptMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerSkuMappingMapper customerSkuMappingMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private DeliveryPrintLogMapper deliveryPrintLogMapper;

    @InjectMocks
    private DeliveryBatchServiceImpl service;

    // ==================== 造数工具 ====================

    private DeliveryBatchViewVO.Row row(Long skuId, String name, Long deptId, String deptName, String qty) {
        return row(skuId, name, null, null, deptId, deptName, qty);
    }

    private DeliveryBatchViewVO.Row row(Long skuId, String name, String spec, String unit,
                                        Long deptId, String deptName, String qty) {
        DeliveryBatchViewVO.Row row = new DeliveryBatchViewVO.Row();
        row.setSkuId(skuId);
        row.setProductName(name);
        row.setSpec(spec);
        row.setUnit(unit);
        row.setDeptId(deptId);
        row.setDeptName(deptName);
        row.setQuantity(new BigDecimal(qty));
        return row;
    }

    private DeliveryMatrixVO.DetailRow detail(Long detailId, Long deliveryId, String code, Long skuId,
                                              String name, String spec, String unit, String num, String price) {
        DeliveryMatrixVO.DetailRow d = new DeliveryMatrixVO.DetailRow();
        d.setDetailId(detailId);
        d.setDeliveryId(deliveryId);
        d.setDeliveryCode(code);
        d.setDocKind(0);
        d.setSkuId(skuId);
        d.setProductName(name);
        d.setStdProductName(name);
        d.setSpec(spec);
        d.setUnit(unit);
        d.setNum(new BigDecimal(num));
        d.setPrice(new BigDecimal(price));
        return d;
    }

    private DeliveryMatrixVO.CellRow cell(Long detailId, Long deptId, String qty) {
        DeliveryMatrixVO.CellRow c = new DeliveryMatrixVO.CellRow();
        c.setDetailId(detailId);
        c.setDeptId(deptId);
        c.setQuantity(new BigDecimal(qty));
        return c;
    }

    private Column col(Long deptId, String code, String name) {
        return Column.builder().deptId(deptId).code(code).name(name).adHoc(false).build();
    }

    /** 已存布局快照：列 + 可选既有价档（rank 已定） */
    private String layoutJson(List<Column> columns, List<TierGroup> tiers, int version) {
        return DeliveryMatrixLayout.builder()
                .printForm(DeliveryMatrixLayout.FORM_MATRIX)
                .colsPerPage(DeliveryMatrixLayout.DEFAULT_COLS_PER_PAGE)
                .rowsPerPage(0)
                .layoutVersion(version)
                .snapshotAt("2026-08-28T06:00:00")
                .columns(new ArrayList<>(columns))
                .priceTiers(tiers == null ? new ArrayList<>() : new ArrayList<>(tiers))
                .build().toJson();
    }

    private DeliveryBatch batchWith(String layoutJson) {
        DeliveryBatch batch = DeliveryBatch.builder()
                .id(700L)
                .customerId(CUSTOMER)
                .deliveryDate(LocalDate.parse(DATE))
                .scopeType("CUSTOMER_DATE")
                .mergeSameItem(true)
                .layoutJson(layoutJson)
                .build();
        return batch;
    }

    private void stubBatch(DeliveryBatch batch) {
        when(deliveryBatchMapper.selectByCustomerAndDate(CUSTOMER, DATE)).thenReturn(batch);
    }

    /** 主口径下 {@link #stubCells} 需要参照的行集 */
    private List<DeliveryMatrixVO.DetailRow> stubbedDetails = List.of();

    private void stubDetails(List<DeliveryMatrixVO.DetailRow> details) {
        this.stubbedDetails = details;
        when(deliveryBatchMapper.selectMatrixDetailsFromOrder(CUSTOMER, DATE)).thenReturn(details);
    }

    /**
     * 主口径格（D-055）：测试内用 detailId 引用 {@link #stubDetails} 的行，
     * 此处回填同一组五元组字段——真实 SQL 里行/格两侧回传同一组字段，服务层按五元组对位。
     */
    private void stubCells(List<DeliveryMatrixVO.CellRow> cells) {
        for (DeliveryMatrixVO.CellRow c : cells) {
            stubbedDetails.stream()
                    .filter(d -> Objects.equals(d.getDetailId(), c.getDetailId()))
                    .findFirst()
                    .ifPresent(d -> {
                        c.setSkuId(d.getSkuId());
                        c.setProductName(d.getProductName());
                        c.setSpec(d.getSpec());
                        c.setUnit(d.getUnit());
                        c.setPrice(d.getPrice());
                    });
        }
        when(deliveryBatchMapper.selectMatrixCellsFromOrder(CUSTOMER, DATE)).thenReturn(cells);
    }

    /** 历史只读口径（D-055 前的送货单）：订单明细无数据 → 回退送货明细行 + source_item 台账（行身份=detailId） */
    private void stubLegacy(List<DeliveryMatrixVO.DetailRow> details, List<DeliveryMatrixVO.CellRow> cells) {
        when(deliveryBatchMapper.selectMatrixDetailsFromOrder(CUSTOMER, DATE)).thenReturn(List.of());
        when(deliveryBatchMapper.selectMatrixCellsFromOrder(CUSTOMER, DATE)).thenReturn(List.of());
        when(deliveryBatchMapper.selectMatrixDetails(CUSTOMER, DATE)).thenReturn(details);
        when(deliveryBatchMapper.selectMatrixCells(CUSTOMER, DATE)).thenReturn(cells);
    }

    // ==================== 客户日总表（D-027/28） ====================

    /** 同品跨点/跨价多行聚合成一个品名行，总量=各点合计，无价格字段拆行 */
    @Test
    void 按标准品名聚合各点小计() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(CUSTOMER, DATE)).thenReturn(List.of(
                row(11L, "白菜", 201L, "A点", "5"),
                row(11L, "白菜", 202L, "B点", "3"),
                // 同一 sku 不同价（source_item 两行）不拆品名行
                row(11L, "白菜", 201L, "A点", "2"),
                row(12L, "土豆", 201L, "A点", "4")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(CUSTOMER, DATE);

        assertEquals(2, result.size());
        DeliveryBatchViewVO cabbage = result.get(0);
        assertEquals(11L, cabbage.getSkuId());
        assertEquals("白菜", cabbage.getProductName());
        assertEquals(0, new BigDecimal("10").compareTo(cabbage.getTotalQuantity()));
        assertEquals(2, cabbage.getDepts().size());
        assertEquals(0, new BigDecimal("7").compareTo(cabbage.getDepts().get(0).getQuantity()));
        assertEquals("A点", cabbage.getDepts().get(0).getDeptName());
        assertEquals(0, new BigDecimal("3").compareTo(cabbage.getDepts().get(1).getQuantity()));
        assertEquals("B点", cabbage.getDepts().get(1).getDeptName());
        assertEquals(0, new BigDecimal("4").compareTo(result.get(1).getTotalQuantity()));
    }

    /** P0-A 修正：同 SKU 不同规格/单位不得错并成一行（原行键只到 sku_id） */
    @Test
    void 同sku不同规格单位不合并() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(CUSTOMER, DATE)).thenReturn(List.of(
                row(11L, "白菜", "500g/份", "份", 201L, "A点", "5"),
                row(11L, "白菜", "1kg/袋", "袋", 201L, "A点", "3")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(CUSTOMER, DATE);

        assertEquals(2, result.size());
        assertEquals("500g/份", result.get(0).getSpec());
        assertEquals("份", result.get(0).getUnit());
        assertEquals("1kg/袋", result.get(1).getSpec());
    }

    /** 历史单回退：source_item 空时走送货明细行聚合；临时商品（无 sku）按品名归并 */
    @Test
    void 无台账历史单回退明细行聚合() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(CUSTOMER, DATE)).thenReturn(List.of());
        when(deliveryBatchMapper.selectBatchViewRowsByDetail(CUSTOMER, DATE)).thenReturn(List.of(
                row(null, "土鸡蛋", 201L, "A点", "10"),
                row(null, "土鸡蛋", 202L, "B点", "6")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(CUSTOMER, DATE);

        assertEquals(1, result.size());
        assertNull(result.get(0).getSkuId());
        assertEquals("土鸡蛋", result.get(0).getProductName());
        assertEquals(0, new BigDecimal("16").compareTo(result.get(0).getTotalQuantity()));
        assertEquals(2, result.get(0).getDepts().size());
    }

    /** 空数据：两路均为空返回空列表 */
    @Test
    void 无数据显示空列表() {
        when(deliveryBatchMapper.selectBatchViewRowsBySource(CUSTOMER, DATE)).thenReturn(List.of());
        when(deliveryBatchMapper.selectBatchViewRowsByDetail(CUSTOMER, DATE)).thenReturn(List.of());

        assertTrue(service.selectBatchView(CUSTOMER, DATE).isEmpty());
    }

    // ==================== 矩阵总表：列（D-045/D-053） ====================

    /** 当日无订单的启用点仍保留空列（hasData=false），格值按 (明细行,点) 透视，恒等式通过 */
    @Test
    void 空列保留且恒等式通过() {
        stubBatch(batchWith(layoutJson(Arrays.asList(
                col(POINT_1, "D01", "人民路店"),
                col(POINT_2, "D02", "公园路店"),
                col(POINT_3, "D03", "新区店")), null, 1)));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "土豆", "500g", "份", "8", "3.20")));
        stubCells(List.of(cell(901L, POINT_1, "5"), cell(901L, POINT_2, "3")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(3, vo.getColumns().size());
        assertEquals("人民路店", vo.getColumns().get(0).getName());
        assertFalse(vo.getColumns().get(2).getHasData(), "当日无单的第3点应保留为空列");
        assertEquals(1, vo.getRows().size());
        DeliveryMatrixVO.RowVO rowVO = vo.getRows().get(0);
        assertEquals(0, new BigDecimal("8").compareTo(rowVO.getTotalQuantity()));
        assertEquals(3, rowVO.getColumnValues().size());
        assertNull(rowVO.getColumnValues().get(2), "空格应为 null");
        assertTrue(vo.getIdentityOk());
        assertTrue(vo.getMismatches().isEmpty());
        assertEquals(0, new BigDecimal("8").compareTo(vo.getTotalQuantity()));
        assertFalse(vo.getLayoutDerived(), "有快照不应标记为实时推导");
    }

    /** 无快照（历史批次）：按主数据启用点实时推导列，标记 layoutDerived，不落库 */
    @Test
    void 无快照按主数据实时推导列() {
        stubBatch(batchWith(null));
        when(deliveryBatchMapper.selectMatrixColumns(CUSTOMER)).thenReturn(Arrays.asList(
                col(POINT_1, "D01", "人民路店"), col(POINT_2, "D02", "公园路店")));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "土豆", "500g", "份", "5", "3.20")));
        stubCells(List.of(cell(901L, POINT_1, "5")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertTrue(vo.getLayoutDerived());
        assertEquals(2, vo.getColumns().size());
        verify(deliveryBatchMapper, never()).updateDeliveryBatch(any(DeliveryBatch.class));
    }

    /** 布局快照优先：点改名后仍用快照名，且不并实时启用点（D-045 规则 3） */
    @Test
    void 快照列优先不回落实时主数据() {
        stubBatch(batchWith(layoutJson(Arrays.asList(
                col(POINT_1, "D01", "旧点名"), col(POINT_3, "D03", "新区店")), null, 4)));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "土豆", "500g", "份", "5", "3.20")));
        stubCells(List.of(cell(901L, POINT_1, "5")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(2, vo.getColumns().size());
        assertEquals("旧点名", vo.getColumns().get(0).getName(), "点名取快照，不回落实时主数据");
        assertEquals(POINT_3, vo.getColumns().get(1).getDeptId(), "快照列顺序保留");
        verify(deliveryBatchMapper, never()).selectMatrixColumns(anyLong());
    }

    /** 停用点当日有单 → 临时补列（adHoc=true），绝不静默丢量（D-053） */
    @Test
    void 停用点当日有单临时补列() {
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")), null, 2)));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "土豆", "500g", "份", "8", "3.20")));
        stubCells(List.of(cell(901L, POINT_1, "5"), cell(901L, POINT_2, "3")));
        CustomerDept stopped = new CustomerDept();
        stopped.setId(POINT_2);
        stopped.setCode("D02");
        stopped.setName("公园路店");
        stopped.setValid(0);
        when(customerDeptMapper.selectCustomerDeptById(POINT_2)).thenReturn(stopped);

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(2, vo.getColumns().size());
        assertFalse(vo.getColumns().get(0).getAdHoc());
        assertTrue(vo.getColumns().get(1).getAdHoc(), "快照外有单点应临时补列并标记 adHoc");
        assertEquals("公园路店", vo.getColumns().get(1).getName());
        assertTrue(vo.getIdentityOk());
    }

    /** 列块划分：>colsPerPage 走横向列分页（D-049 兜底） */
    @Test
    void 列块划分与页码() {
        List<Column> columns = new ArrayList<>();
        List<DeliveryMatrixVO.CellRow> cells = new ArrayList<>();
        for (long i = 1; i <= 7; i++) {
            columns.add(col(200L + i, "D0" + i, "点" + i));
            cells.add(cell(901L, 200L + i, "1"));
        }
        stubBatch(batchWith(layoutJson(columns, null, 1)));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "土豆", "500g", "份", "7", "3.20")));
        stubCells(cells);

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(7, vo.getColumns().size());
        assertEquals(1, vo.getColumns().get(0).getBlockNo());
        assertEquals(1, vo.getColumns().get(5).getBlockNo());
        assertEquals(2, vo.getColumns().get(6).getBlockNo(), "第7列落第二列块（每页6列）");
        assertEquals(2, vo.getColBlocks());
    }

    // ==================== 矩阵总表：行与档标（D-046/D-047） ====================

    /** 同名同规格同单位不同价 → 拆两行并打 (档①)/(档②)；单档品名不打标 */
    @Test
    void 不同价拆行并打档标() {
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")), null, 1)));
        stubDetails(Arrays.asList(
                detail(901L, 801L, "HS001", 11L, "白菜", "", "斤", "5", "2.00"),
                detail(902L, 801L, "HS001", 11L, "白菜", "", "斤", "1", "2.50"),
                detail(903L, 801L, "SKU2", 12L, "土豆", "", "斤", "4", "3.50")));
        stubCells(List.of(cell(901L, POINT_1, "5"), cell(902L, POINT_1, "1"), cell(903L, POINT_1, "4")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(3, vo.getRows().size());
        // D-046/D-053 修订：品名保持干净，档位标进备注列
        assertEquals("白菜", vo.getRows().get(0).getDisplayProductName(), "品名不拼档标");
        assertEquals("白菜", vo.getRows().get(1).getDisplayProductName());
        assertEquals("土豆", vo.getRows().get(2).getDisplayProductName(), "单档品名不打档标");
        assertEquals("档①", vo.getRows().get(0).getRemark(), "档位标在备注列");
        assertEquals("档②", vo.getRows().get(1).getRemark());
        assertEquals("", vo.getRows().get(2).getRemark(), "单档备注列为空");
        assertEquals(2, vo.getRows().get(0).getTierCount());
        assertTrue(vo.getIdentityOk());
        // 纸面不打价：单价仅内部字段，格值与合计为数量
        assertEquals(0, new BigDecimal("2.00").compareTo(vo.getRows().get(0).getPrice()));
    }

    /** 档号 append-only：新出现的更低价追加为后一档，已有档号绝不重排（D-053） */
    @Test
    void 档号追加不重排() {
        TierGroup existing = TierGroup.builder()
                .groupKey(DeliveryMatrixLayout.groupKey(11L, "白菜", "", "斤"))
                .productName("白菜").spec("").unit("斤")
                .tiers(new ArrayList<>(List.of(Tier.builder().rank(1).price(new BigDecimal("3.20"))
                        .priceKey("3.2").build())))
                .build();
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")),
                List.of(existing), 3)));
        stubDetails(Arrays.asList(
                detail(901L, 801L, "HS001", 11L, "白菜", "", "斤", "5", "3.20"),
                // 补单带来更低价：应追加为档②，而不是把 3.20 重排成档②
                detail(902L, 802L, "HS002", 11L, "白菜", "", "斤", "2", "2.80")));
        stubCells(List.of(cell(901L, POINT_1, "5"), cell(902L, POINT_1, "2")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        // D-046/D-053 修订：档位标在备注列，品名干净；新价追加为下一档时不重排已有档号
        assertEquals("白菜", vo.getRows().get(0).getDisplayProductName(), "原档号不变");
        assertEquals("白菜", vo.getRows().get(1).getDisplayProductName(), "新价追加为下一档");
        assertEquals("档①", vo.getRows().get(0).getRemark(), "原档位标在备注列");
        assertEquals("档②", vo.getRows().get(1).getRemark(), "新档位追加");
        assertEquals(2, vo.getRows().get(0).getTierCount());
    }

    /** 恒等式不一致（明细数量 ≠ 各列格值合计）→ 标记并列出明细（D-047） */
    @Test
    void 恒等式不一致应标记() {
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")), null, 1)));
        stubDetails(List.of(detail(901L, 801L, "HS001", 11L, "白菜", "", "斤", "5", "2.00")));
        stubCells(List.of(cell(901L, POINT_1, "4")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertFalse(vo.getIdentityOk());
        assertEquals(1, vo.getMismatches().size());
        assertEquals(0, new BigDecimal("5").compareTo(vo.getMismatches().get(0).getNum()));
        assertEquals(0, new BigDecimal("4").compareTo(vo.getMismatches().get(0).getCellSum()));
        assertFalse(vo.getRows().get(0).getIdentityOk());
    }

    /** 历史只读口径（D-055 前送货单）无台账：点列全空、合计取明细数量、不误报恒等式不一致（D-051） */
    @Test
    void 历史单无台账回退() {
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")), null, 1)));
        stubLegacy(List.of(detail(901L, 801L, "HS001", 11L, "白菜", "", "斤", "5", "2.00")), List.of());

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertTrue(vo.getHistoryFallback());
        assertTrue(vo.getIdentityOk(), "无台账不应判为恒等式不一致");
        assertEquals(0, new BigDecimal("5").compareTo(vo.getRows().get(0).getTotalQuantity()));
        assertNull(vo.getRows().get(0).getColumnValues().get(0));
    }

    /** 参数缺失应报错 */
    @Test
    void 矩阵参数校验() {
        assertTrue(Arrays.asList(
                expectError(() -> service.selectMatrix(null, DATE)),
                expectError(() -> service.selectMatrix(CUSTOMER, "  "))).stream().allMatch(b -> b));
    }

    // ==================== D-055 视图化取数口径 ====================

    /** 主口径优先：订单明细有数据时不得回退历史送货单口径 */
    @Test
    void 订单明细口径优先于历史送货单() {
        stubBatch(batchWith(null));
        when(deliveryBatchMapper.selectMatrixColumns(CUSTOMER)).thenReturn(List.of(col(POINT_1, "D01", "人民路店")));
        stubDetails(List.of(detail(901L, null, null, 11L, "白菜", "", "斤", "5", "2.00")));
        stubCells(List.of(cell(901L, POINT_1, "5")));
        // 旧口径同时有数据（D-055 前遗留单）：应被完全忽略，不得参与取数
        lenient().when(deliveryBatchMapper.selectMatrixDetails(CUSTOMER, DATE)).thenReturn(List.of(
                detail(777L, 888L, "HS-OLD", 99L, "旧单菜", "", "斤", "9", "9.00")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(1, vo.getRows().size());
        assertEquals("白菜", vo.getRows().get(0).getProductName());
        assertNull(vo.getRows().get(0).getDeliveryId(), "主口径行不属任何送货单");
        assertFalse(vo.getHistoryFallback(), "走主口径不应标历史回退");
        verify(deliveryBatchMapper, never()).selectMatrixCells(CUSTOMER, DATE);
    }

    /** 无订单明细时回退历史口径，格按送货明细行ID对位（旧日期仍可展示） */
    @Test
    void 无订单明细回退历史送货单口径() {
        stubBatch(batchWith(layoutJson(Arrays.asList(col(POINT_1, "D01", "人民路店")), null, 1)));
        stubLegacy(Arrays.asList(
                detail(901L, 801L, "HS001", 11L, "白菜", "", "斤", "5", "2.00"),
                detail(902L, 801L, "HS001", 12L, "土豆", "", "斤", "3", "3.50")),
                Arrays.asList(cell(901L, POINT_1, "5"), cell(902L, POINT_1, "3")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(2, vo.getRows().size());
        assertEquals(0, new BigDecimal("5").compareTo(vo.getRows().get(0).getTotalQuantity()));
        assertEquals(801L, vo.getRows().get(0).getDeliveryId(), "历史口径保留送货单归属");
        assertFalse(vo.getHistoryFallback(), "历史口径但有台账 → 不打 —");
        assertTrue(vo.getIdentityOk());
    }

    /** 同 SKU 不同价在订单明细口径下仍拆两行并分档（D-024/D-046） */
    @Test
    void 订单明细口径同名不同价拆行() {
        stubBatch(batchWith(null));
        when(deliveryBatchMapper.selectMatrixColumns(CUSTOMER)).thenReturn(Arrays.asList(
                col(POINT_1, "D01", "人民路店"), col(POINT_2, "D02", "公园路店")));
        stubDetails(Arrays.asList(
                detail(901L, null, null, 11L, "白菜", "", "斤", "8", "2.00"),
                detail(902L, null, null, 11L, "白菜", "", "斤", "3", "2.50")));
        stubCells(Arrays.asList(
                cell(901L, POINT_1, "5"), cell(901L, POINT_2, "3"),
                cell(902L, POINT_1, "3")));

        DeliveryMatrixVO vo = service.selectMatrix(CUSTOMER, DATE);

        assertEquals(2, vo.getRows().size());
        assertEquals("档①", vo.getRows().get(0).getRemark());
        assertEquals("档②", vo.getRows().get(1).getRemark());
        assertEquals(0, new BigDecimal("8").compareTo(vo.getRows().get(0).getTotalQuantity()));
        assertTrue(vo.getIdentityOk(), "行应送=各点合计");
        assertTrue(vo.getLayoutDerived(), "无快照批次按启用点实时推导");
    }

    /** 点单视图走只读口径（status&gt;=1），不得误用验收创建口径（status=1） */
    @Test
    void 点单视图走只读口径查询() {
        SaleOrderDetail d = new SaleOrderDetail();
        d.setId(901L);
        when(saleOrderDetailMapper.selectValidByCustomerPointDateForView(CUSTOMER, POINT_1, LocalDate.parse(DATE)))
                .thenReturn(List.of(d));

        assertEquals(1, service.selectPointView(CUSTOMER, POINT_1, DATE).size());

        verify(saleOrderDetailMapper, never())
                .selectValidByCustomerPointDate(anyLong(), anyLong(), any());
    }

    /** 配货口径优先订单明细，不再读旧送货单台账 */
    @Test
    void 配货口径优先订单明细() {
        when(deliveryBatchMapper.selectBatchViewRowsFromOrder(CUSTOMER, DATE)).thenReturn(List.of(
                row(11L, "白菜", 201L, "A点", "7"),
                row(11L, "白菜", 202L, "B点", "3")));

        List<DeliveryBatchViewVO> result = service.selectBatchView(CUSTOMER, DATE);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("10").compareTo(result.get(0).getTotalQuantity()));
        verify(deliveryBatchMapper, never()).selectBatchViewRowsBySource(anyLong(), any());
    }

    /** 打印分界登记（D-055）：总单不拆点（deptId=null），点单带点 */
    @Test
    void 打印登记写入打印日志() {
        service.markPrinted(CUSTOMER, DATE, POINT_1, 12L);

        ArgumentCaptor<DeliveryPrintLog> captor = ArgumentCaptor.forClass(DeliveryPrintLog.class);
        verify(deliveryPrintLogMapper).insertPrintLog(captor.capture());
        DeliveryPrintLog log = captor.getValue();
        assertEquals(CUSTOMER, log.getCustomerId());
        assertEquals(LocalDate.parse(DATE), log.getDeliveryDate());
        assertEquals(POINT_1, log.getCustomerDeptId());
        assertEquals(12L, log.getTemplateId());
        assertTrue(log.getPrintTime() != null && log.getCreateTime() != null);
        assertEquals("system", log.getPrintBy(), "无安全上下文回落 system");
    }

    /** 打印分界判定：无客户/日期直接 false，不得发查询 */
    @Test
    void 打印状态查询参数兼底() {
        assertFalse(service.isPrinted(null, DATE, POINT_1));
        assertFalse(service.isPrinted(CUSTOMER, "  ", POINT_1));
        verify(deliveryPrintLogMapper, never()).countPrinted(any(), any(), any());

        when(deliveryPrintLogMapper.countPrinted(CUSTOMER, LocalDate.parse(DATE), POINT_1)).thenReturn(1);
        assertTrue(service.isPrinted(CUSTOMER, DATE, POINT_1));

        // 总单维度：deptId=null 同样可判
        when(deliveryPrintLogMapper.countPrinted(CUSTOMER, LocalDate.parse(DATE), null)).thenReturn(0);
        assertFalse(service.isPrinted(CUSTOMER, DATE, null));
    }

    /** 打印登记参数缺失应报错 */
    @Test
    void 打印登记参数校验() {
        assertTrue(expectError(() -> service.markPrinted(null, DATE, null, null)));
        assertTrue(expectError(() -> service.markPrinted(CUSTOMER, "", null, null)));
    }

    private boolean expectError(Runnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (com.lin.common.exception.ServiceException e) {
            return true;
        }
    }

    // ==================== 工具 ====================
}
