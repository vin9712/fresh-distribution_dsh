package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.dto.SaleOrderCreateDTO;
import com.lin.distribution.dto.SaleOrderUpdateStatusDTO;
import com.lin.distribution.dto.WithdrawCascadeResultVO;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.CustomerDeptMapper;
import com.lin.distribution.mapper.CustomerMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.BizCodeService;
import com.lin.distribution.service.DeliveryOrderService;
import com.lin.distribution.service.OrderWithdrawCascadeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单编辑护栏与撤回精确化测试（S14/T2，DESIGN.md §5.5 / §七 操作可行性矩阵）
 *
 * <p>G6：DELIVERED/ACCEPTED/SETTLED 拒改；CONFIRMED 已进有效送货单拒改；
 * DRAFT / CONFIRMED 未分配可改。<br>
 * G4：撤回仅看 EXISTS source_item 有效分配，同客户同日他单有送货单不影响。<br>
 * D-055/OA（P0）：已存在验收单（草稿/已提交）或配送后变更标记（加单/换货/退货）时，
 * 整单重写（物理删重插）会 dangling 验收行/静默丢弃标记，故一并拒改。</p>
 */
@ExtendWith(MockitoExtension.class)
class SaleOrderServiceImplTest {

    @Mock
    private SaleOrderMapper saleOrderMapper;
    @Mock
    private SaleOrderDetailMapper saleOrderDetailMapper;
    @Mock
    private AcceptanceMapper acceptanceMapper;
    @Mock
    private DeliveryOrderService deliveryOrderService;
    @Mock
    private OrderWithdrawCascadeService orderWithdrawCascadeService;
    @Mock
    private BizCodeService bizCodeService;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private CustomerDeptMapper customerDeptMapper;

    @InjectMocks
    private SaleOrderServiceImpl saleOrderService;

    private static final Long ORDER_ID = 100L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 28);

    private SaleOrder order(Integer status) {
        SaleOrder order = new SaleOrder();
        order.setId(ORDER_ID);
        order.setCode("XD202608280001");
        order.setStatus(status);
        order.setDeliveryDate(DATE);
        order.setAmount(new BigDecimal("10.00"));
        // 快照标识字段与 updateRequest() 保持一致（客户/配送点/单号不可变）
        order.setCustomerId(1000L);
        order.setCustomerDeptId(1001L);
        return order;
    }

    private SaleOrderDetail detail() {
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setSkuId(11L);
        detail.setProductName("白菜");
        detail.setProductPrice(new BigDecimal("2.00"));
        detail.setNum(new BigDecimal("5"));
        return detail;
    }

    private SaleOrderCreateDTO updateRequest() {
        return SaleOrderCreateDTO.builder()
                .orderId(ORDER_ID)
                .customerId(1000L)
                .customerDeptId(1001L)
                .orderCode("XD202608280001")
                .deliveryDate(DATE)
                .orderDetails(Collections.singletonList(detail()))
                .build();
    }

    // ================= s35：草稿判重按班次 =================

    /** 启用班次：按「配送点+日期+班次」判重（不同班次不算重复，否则白班草稿会拦住夜班录单） */
    @Test
    void 启用班次时判重带上班次() {
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE, "NIGHT", "DAY")).thenReturn(Collections.emptyList());

        assertNull(saleOrderService.findExistingDraftOrder(1001L, DATE, "NIGHT"));

        verify(saleOrderMapper).selectExistingDraftOrder(1001L, DATE, "NIGHT", "DAY");
    }

    /** 启用班次 + 未传班次：按业务口径归白班后判重（历史无班次单同一口径） */
    @Test
    void 启用班次时空班次归白班判重() {
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE, "DAY", "DAY")).thenReturn(Collections.emptyList());

        assertNull(saleOrderService.findExistingDraftOrder(1001L, DATE, null));

        verify(saleOrderMapper).selectExistingDraftOrder(1001L, DATE, "DAY", "DAY");
    }

    /** 未启用班次：不按班次过滤，判重口径与引入前一致（回归） */
    @Test
    void 未启用班次时判重不带班次() {
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(false));
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE, null, "DAY")).thenReturn(Collections.emptyList());

        assertNull(saleOrderService.findExistingDraftOrder(1001L, DATE, "NIGHT"));

        verify(saleOrderMapper).selectExistingDraftOrder(1001L, DATE, null, "DAY");
    }

    // ================= G6：整单改写护栏 =================

    @Test
    void 草稿状态可修改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));

        saleOrderService.updateSaleOrderWithDetails(updateRequest());

        verify(saleOrderMapper).updateSaleOrder(any(SaleOrder.class));
        verify(saleOrderDetailMapper).deleteSaleOrderDetailByOrderId(ORDER_ID);
    }

    // ================= s35：班次（客户开关 + 配送点班次） =================

    private com.lin.distribution.domain.Customer customer(boolean shiftEnabled) {
        com.lin.distribution.domain.Customer customer = new com.lin.distribution.domain.Customer();
        customer.setId(1000L);
        customer.setShiftEnabled(shiftEnabled);
        return customer;
    }

    private com.lin.distribution.domain.CustomerDept dept(String shiftCodes) {
        com.lin.distribution.domain.CustomerDept dept = new com.lin.distribution.domain.CustomerDept();
        dept.setId(1001L);
        dept.setCustomerId(1000L);
        dept.setName("华铃");
        dept.setShiftCodes(shiftCodes);
        return dept;
    }

    @Test
    void 未启用班次的客户忽略请求班次() {
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(false));

        SaleOrder order = saleOrderService.createSaleOrder(SaleOrderCreateDTO.builder()
                .customerId(1000L).customerDeptId(1001L).orderCode("XD001").deliveryDate(DATE)
                .shiftCode("NIGHT")
                .orderDetails(Collections.singletonList(detail()))
                .build());

        assertEquals("", order.getShiftCode(), "未启用班次的客户不落班次，行为与引入前一致");
        verify(customerDeptMapper, never()).selectCustomerDeptById(anyLong());
    }

    @Test
    void 启用班次时单班次配送点可省略并自动带出() {
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY"));

        SaleOrder order = saleOrderService.createSaleOrder(SaleOrderCreateDTO.builder()
                .customerId(1000L).customerDeptId(1001L).orderCode("XD002").deliveryDate(DATE)
                .orderDetails(Collections.singletonList(detail()))
                .build());

        assertEquals("DAY", order.getShiftCode());
    }

    @Test
    void 启用班次时多班次配送点必须选班次() {
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));

        SaleOrderCreateDTO request = SaleOrderCreateDTO.builder()
                .customerId(1000L).customerDeptId(1001L).orderCode("XD003").deliveryDate(DATE)
                .orderDetails(Collections.singletonList(detail()))
                .build();

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.createSaleOrder(request));
        assertTrue(ex.getMessage().contains("请选择班次"), ex.getMessage());
    }

    @Test
    void 启用班次时班次必须在该配送点声明范围内() {
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY"));

        SaleOrderCreateDTO request = SaleOrderCreateDTO.builder()
                .customerId(1000L).customerDeptId(1001L).orderCode("XD004").deliveryDate(DATE)
                .shiftCode("NIGHT")
                .orderDetails(Collections.singletonList(detail()))
                .build();

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.createSaleOrder(request));
        assertTrue(ex.getMessage().contains("班次不在配送点"), ex.getMessage());
        verify(saleOrderMapper, never()).insertSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 启用班次但配送点未维护班次时拒绝下单() {
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept(""));

        SaleOrderCreateDTO request = SaleOrderCreateDTO.builder()
                .customerId(1000L).customerDeptId(1001L).orderCode("XD005").deliveryDate(DATE)
                .orderDetails(Collections.singletonList(detail()))
                .build();

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.createSaleOrder(request));
        assertTrue(ex.getMessage().contains("未配置班次"), ex.getMessage());
    }

    @Test
    void 编辑时班次不可变更() {
        SaleOrder existing = order(SaleOrderStatus.DRAFT.getCode());
        existing.setShiftCode("DAY");
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(existing);
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));
        SaleOrderCreateDTO request = updateRequest();
        request.setShiftCode("NIGHT");

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderWithDetails(request));
        assertTrue(ex.getMessage().contains("订单班次不可修改"), ex.getMessage());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 历史无班次单编辑时归白班不报错() {
        SaleOrder existing = order(SaleOrderStatus.DRAFT.getCode());
        existing.setShiftCode("");
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(existing);
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("DAY,NIGHT"));
        SaleOrderCreateDTO request = updateRequest();
        request.setShiftCode("DAY");

        saleOrderService.updateSaleOrderWithDetails(request);

        ArgumentCaptor<SaleOrder> captor = ArgumentCaptor.forClass(SaleOrder.class);
        verify(saleOrderMapper).updateSaleOrder(captor.capture());
        assertEquals("DAY", captor.getValue().getShiftCode(), "历史无班次单编辑时补齐为白班");
    }

    @Test
    void 历史无班次单在只配夜班的配送点仍可编辑补齐夜班() {
        // 回归：旧实现把空班次归白班后比对，配送点只声明 NIGHT 时历史单会被「班次不可修改」死锁
        SaleOrder existing = order(SaleOrderStatus.DRAFT.getCode());
        existing.setShiftCode("");
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(existing);
        when(customerMapper.selectCustomerById(1000L)).thenReturn(customer(true));
        when(customerDeptMapper.selectCustomerDeptById(1001L)).thenReturn(dept("NIGHT"));
        SaleOrderCreateDTO request = updateRequest();
        request.setShiftCode("NIGHT");

        saleOrderService.updateSaleOrderWithDetails(request);

        ArgumentCaptor<SaleOrder> captor = ArgumentCaptor.forClass(SaleOrder.class);
        verify(saleOrderMapper).updateSaleOrder(captor.capture());
        assertEquals("NIGHT", captor.getValue().getShiftCode(), "历史空班次允许落库补齐为该点声明的班次");
    }

    // ================= 删除护栏与逻辑删除（2026-09-14） =================

    @Test
    void 草稿删除为逻辑删除并级联明细() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));

        saleOrderService.deleteSaleOrderByIds(new Long[]{ORDER_ID});

        ArgumentCaptor<Long[]> idsCaptor = ArgumentCaptor.forClass(Long[].class);
        verify(saleOrderDetailMapper).deleteSaleOrderDetailByOrderIds(idsCaptor.capture());
        assertEquals(ORDER_ID, idsCaptor.getValue()[0], "明细同步逻辑删除（避免孤儿行）");
        verify(saleOrderMapper).deleteSaleOrderByIds(idsCaptor.capture());
        assertEquals(ORDER_ID, idsCaptor.getValue()[0], "主单逻辑删除");
    }

    @Test
    void 已确认及之后订单不可删除() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.CONFIRMED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.deleteSaleOrderByIds(new Long[]{ORDER_ID}));
        assertTrue(ex.getMessage().contains("仅草稿状态可删除"), ex.getMessage());
        verify(saleOrderMapper, never()).deleteSaleOrderByIds(any());
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderIds(any());
    }

    @Test
    void 已确认订单拒改_需先撤回为草稿() {
        // 2026-09-14 业务定稿：已确认不再允许直接改单，必须先撤回（撤回后 status=0 即可改）
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.CONFIRMED.getCode()));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("撤回"), "提示先撤回为草稿：" + ex.getMessage());
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderId(anyLong());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 已进有效送货单的已确认订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.CONFIRMED.getCode()));
        when(saleOrderDetailMapper.existsValidAllocation(ORDER_ID)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("历史送货单"), "D-055 后该护栏仅对已进入历史送货单的订单生效");
        // 拒改时不得触发物理删重插（切断关联/清零镜像的风险路径）
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderId(anyLong());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 已生成验收单的草稿订单拒改() {
        // P0：验收草稿行挂在 sale_order_detail_id 上，整单重写会让验收行 dangling 并在下次同步时重复补行。
        // 场景=已确认单带验收草稿 → 撤回为草稿后仍不得改单，需先撤销/删除验收单
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));
        Acceptance acc = new Acceptance();
        acc.setId(9L);
        acc.setCode("YS20260911001");
        acc.setSaleOrderId(ORDER_ID);
        when(acceptanceMapper.selectBySaleOrder(ORDER_ID)).thenReturn(acc);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("验收单"), "提示应先撤销/删除验收单");
        assertTrue(ex.getMessage().contains("YS20260911001"), "提示带已有验收单号");
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderId(anyLong());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 存在配送后变更标记的草稿订单拒改() {
        // P0：change_type≠0 是配送后现场事实，物理删重插会静默丢弃（退货行变回正常行=重复计费）
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));
        when(acceptanceMapper.selectBySaleOrder(ORDER_ID)).thenReturn(null);
        when(saleOrderDetailMapper.existsChangeMark(ORDER_ID)).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("配送后变更"), "提示走变更入口/先回退");
        verify(saleOrderDetailMapper, never()).deleteSaleOrderDetailByOrderId(anyLong());
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 已配送订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DELIVERED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("配送后变更（加单/退货标记）或新增销售订单"));
        verify(saleOrderDetailMapper, never()).existsValidAllocation(anyLong());
    }

    @Test
    void 已验收订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.ACCEPTED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("配送后变更（加单/退货标记）或新增销售订单"));
    }

    @Test
    void 已结算订单拒改() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.SETTLED.getCode()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        assertTrue(ex.getMessage().contains("已结算"));
    }

    // ================= W0-1：订单快照不可变护栏（蓝图「基础资料快照」） =================

    @Test
    void 编辑时变更客户应拒绝() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));
        SaleOrderCreateDTO request = updateRequest();
        request.setCustomerId(9999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(request));
        assertTrue(ex.getMessage().contains("客户不可修改"));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 编辑时变更配送点应拒绝() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));
        SaleOrderCreateDTO request = updateRequest();
        request.setCustomerDeptId(9999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(request));
        assertTrue(ex.getMessage().contains("配送点不可修改"));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 编辑时变更订单编号应拒绝() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));
        SaleOrderCreateDTO request = updateRequest();
        request.setOrderCode("XD202608289999");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> saleOrderService.updateSaleOrderWithDetails(request));
        assertTrue(ex.getMessage().contains("订单编号不可修改"));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    @Test
    void 编辑时订单行快照价按请求写入且不重新取价() {
        // 快照不可变的另一面：编辑不触发取价/回写，明细行价格即请求中的下单快照
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(order(SaleOrderStatus.DRAFT.getCode()));

        ArgumentCaptor<SaleOrderDetail> captor = ArgumentCaptor.forClass(SaleOrderDetail.class);
        saleOrderService.updateSaleOrderWithDetails(updateRequest());

        verify(saleOrderDetailMapper).insertSaleOrderDetail(captor.capture());
        assertEquals(0, new BigDecimal("2.00").compareTo(captor.getValue().getProductPrice()));
        assertEquals(1000L, captor.getValue().getCustomerId());
        assertEquals(1001L, captor.getValue().getCustomerDeptId());
    }

    @Test
    void 修改不存在的订单应报错() {
        when(saleOrderMapper.selectSaleOrderById(ORDER_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderWithDetails(updateRequest()));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
    }

    // ================= G4：撤回级联（W0-2.1） =================

    @Test
    void 撤回被已打印送货单占用的订单应拒绝() {
        SaleOrder occupied = order(SaleOrderStatus.CONFIRMED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_ID)))
                .thenReturn(Collections.singletonList(occupied));
        doThrow(new ServiceException("订单已进入已打印/已送达送货单【HS202608280001】，请先作废送货单后再撤回："
                + occupied.getCode())).when(orderWithdrawCascadeService).validateOrderWithdrawable(any(SaleOrder.class));

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(Collections.singletonList(ORDER_ID));
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderStatus(request));
        assertTrue(ex.getMessage().contains("已打印/已送达"));
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
        // 拒绝时不得执行级联扣除
        verify(orderWithdrawCascadeService, never()).cascadeOnOrderWithdraw(any(SaleOrder.class));
    }

    @Test
    void 撤回未进单的同组订单不受他单已打印送货单影响() {
        // G4 核心场景：同客户同日 B 单已进入已打印送货单，A 单未进单 → 校验拒绝，报错指向 B
        SaleOrder a = order(SaleOrderStatus.CONFIRMED.getCode());
        SaleOrder b = new SaleOrder();
        b.setId(101L);
        b.setCode("XD202608280002");
        b.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        b.setDeliveryDate(DATE);

        List<Long> ids = Arrays.asList(ORDER_ID, 101L);
        when(saleOrderMapper.selectSaleOrderByIdIn(ids)).thenReturn(Arrays.asList(a, b));
        // A 校验通过；B 被已打印送货单占用 → 校验拒绝（报错指向 B）
        doAnswer(invocation -> {
            SaleOrder o = invocation.getArgument(0);
            if (Long.valueOf(101L).equals(o.getId())) {
                throw new ServiceException("订单已进入已打印/已送达送货单【HS202608280002】，请先作废送货单后再撤回：XD202608280002");
            }
            return null;
        }).when(orderWithdrawCascadeService).validateOrderWithdrawable(any(SaleOrder.class));

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(ids);
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        ServiceException ex = assertThrows(ServiceException.class, () -> saleOrderService.updateSaleOrderStatus(request));
        assertTrue(ex.getMessage().contains("XD202608280002"));
        // 校验未全部通过，任何订单均不更新、不级联
        verify(saleOrderMapper, never()).updateSaleOrder(any(SaleOrder.class));
        verify(orderWithdrawCascadeService, never()).cascadeOnOrderWithdraw(any(SaleOrder.class));
    }

    @Test
    void 撤回待打印送货单占用的订单应级联扣除后放行() {
        // W0-2.1 核心：CONFIRMED + 待打印送货单占用 → 级联扣除/作废后可撤回
        SaleOrder a = order(SaleOrderStatus.CONFIRMED.getCode());
        when(saleOrderMapper.selectSaleOrderByIdIn(Collections.singletonList(ORDER_ID)))
                .thenReturn(Collections.singletonList(a));
        when(orderWithdrawCascadeService.cascadeOnOrderWithdraw(any(SaleOrder.class)))
                .thenReturn(WithdrawCascadeResultVO.builder()
                        .voidedDeliveryCodes(Arrays.asList("HS202608280001"))
                        .build());

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(Collections.singletonList(ORDER_ID));
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        saleOrderService.updateSaleOrderStatus(request);

        verify(orderWithdrawCascadeService).validateOrderWithdrawable(any(SaleOrder.class));
        verify(orderWithdrawCascadeService).cascadeOnOrderWithdraw(any(SaleOrder.class));
        ArgumentCaptor<SaleOrder> captor = ArgumentCaptor.forClass(SaleOrder.class);
        verify(saleOrderMapper).updateSaleOrder(captor.capture());
        assertEquals(SaleOrderStatus.DRAFT.getCode(), captor.getValue().getStatus());
    }

    @Test
    void 撤回全部未进单订单应放行() {
        SaleOrder a = order(SaleOrderStatus.CONFIRMED.getCode());
        SaleOrder b = new SaleOrder();
        b.setId(101L);
        b.setCode("XD202608280002");
        b.setStatus(SaleOrderStatus.CONFIRMED.getCode());
        b.setDeliveryDate(DATE);

        List<Long> ids = Arrays.asList(ORDER_ID, 101L);
        when(saleOrderMapper.selectSaleOrderByIdIn(ids)).thenReturn(Arrays.asList(a, b));
        when(orderWithdrawCascadeService.cascadeOnOrderWithdraw(any(SaleOrder.class)))
                .thenReturn(WithdrawCascadeResultVO.builder().build());

        SaleOrderUpdateStatusDTO request = new SaleOrderUpdateStatusDTO();
        request.setOrderIds(ids);
        request.setStatus(SaleOrderStatus.DRAFT.getCode());

        saleOrderService.updateSaleOrderStatus(request);

        verify(orderWithdrawCascadeService, times(2)).validateOrderWithdrawable(any(SaleOrder.class));
        ArgumentCaptor<SaleOrder> captor = ArgumentCaptor.forClass(SaleOrder.class);
        verify(saleOrderMapper, times(2)).updateSaleOrder(captor.capture());
        assertEquals(SaleOrderStatus.DRAFT.getCode(), captor.getAllValues().get(0).getStatus());
        assertEquals(SaleOrderStatus.DRAFT.getCode(), captor.getAllValues().get(1).getStatus());
    }

    // ================= 新增订单防重复：同配送点+同日期草稿检测 =================

    @Test
    void 同配送点同日期有草稿订单则返回最新一条() {
        SaleOrder latest = order(SaleOrderStatus.DRAFT.getCode());
        latest.setId(200L);
        latest.setCode("XD202608280200");
        // 客户未启用班次（customerDept 查不到）→ 不按班次过滤，口径与引入前一致
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE, null, "DAY"))
                .thenReturn(Arrays.asList(latest, order(SaleOrderStatus.DRAFT.getCode())));

        SaleOrder result = saleOrderService.findExistingDraftOrder(1001L, DATE, null);

        assertEquals(200L, result.getId());
        assertEquals("XD202608280200", result.getCode());
        verify(saleOrderMapper).selectExistingDraftOrder(1001L, DATE, null, "DAY");
    }

    @Test
    void 同配送点同日期无草稿订单则返回null() {
        when(saleOrderMapper.selectExistingDraftOrder(1001L, DATE, null, "DAY"))
                .thenReturn(Collections.emptyList());

        SaleOrder result = saleOrderService.findExistingDraftOrder(1001L, DATE, null);

        assertEquals(null, result);
    }

    @Test
    void 防重复检测参数缺一不查返回null() {
        SaleOrder result1 = saleOrderService.findExistingDraftOrder(null, DATE, null);
        SaleOrder result2 = saleOrderService.findExistingDraftOrder(1001L, null, null);

        assertEquals(null, result1);
        assertEquals(null, result2);
        verify(saleOrderMapper, never()).selectExistingDraftOrder(anyLong(), any(), any(), any());
    }

    /**
     * D-064 客户视角分组分页：服务层为纯直出，筛选条件必须原样传给 mapper（口径与明细视角一致），
     * 且不额外补字段（班次是订单级属性，只在子行展示 D-065）。
     */
    @Test
    void 客户视角分组分页筛选条件原样下传() {
        SaleOrder query = new SaleOrder();
        query.setDeliveryDate(DATE);
        query.setStatus(SaleOrderStatus.DELIVERED.getCode());
        query.setCustomerDeptIds(Arrays.asList(1001L, 1002L));
        com.lin.distribution.vo.SaleCustomerPageVO row = new com.lin.distribution.vo.SaleCustomerPageVO();
        row.setCustomerId(7L);
        row.setOrderCount(3);
        row.setPointCount(2);
        when(saleOrderMapper.selectCustomerPage(query)).thenReturn(Collections.singletonList(row));

        List<com.lin.distribution.vo.SaleCustomerPageVO> result = saleOrderService.selectCustomerPage(query);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getCustomerId());
        assertEquals(3, result.get(0).getOrderCount());
        // 入参未被服务层改写（否则分组口径会与明细视角漂移）
        assertEquals(DATE, query.getDeliveryDate());
        assertEquals(Arrays.asList(1001L, 1002L), query.getCustomerDeptIds());
        verify(saleOrderMapper).selectCustomerPage(query);
    }
}
