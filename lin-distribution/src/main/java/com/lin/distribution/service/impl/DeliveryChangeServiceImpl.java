package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.DeliveryChangeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配送后订单变更服务实现（D-055）
 *
 * <p>业务口径：配送前（未打印）改动=正常更新订单（无标记）；配送后（已打印）改动=原订单数据不变，
 * 以标记（change_type 1加单/2换货/3退货）附加在订单明细行上，验收/日总表/点单视图按标记展示。</p>
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryChangeServiceImpl implements DeliveryChangeService {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderDetailMapper saleOrderDetailMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleOrderDetail addSupplement(Long orderId, Long skuId, String productName, String spec, String unit,
                                         BigDecimal num, BigDecimal price, BigDecimal actualNum, String remark) {
        SaleOrder order = requireConfirmedOrder(orderId);
        if (num == null || num.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("加单应收数量必须大于 0");
        }
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setOrderId(orderId);
        detail.setCustomerId(order.getCustomerId());
        detail.setCustomerDeptId(order.getCustomerDeptId());
        detail.setOrderCode(order.getCode());
        detail.setSkuId(skuId);
        detail.setProductName(productName);
        detail.setProductUnit(unit);
        detail.setProductSpec(spec);
        detail.setProductPrice(price == null ? BigDecimal.ZERO : price);
        detail.setNum(num);
        detail.setExpectAmount(price == null ? BigDecimal.ZERO : price.multiply(num));
        detail.setActualNum(actualNum == null ? num : actualNum);
        detail.setSort(nextSort(orderId));
        detail.setChangeType(1);
        detail.setChangeRemark(remark == null ? "配送后加单" : remark);
        detail.setIsDeleted(Boolean.FALSE);
        detail.setCreateBy(resolveOperator());
        detail.setCreateTime(DateUtils.getNowDate());
        saleOrderDetailMapper.insertSaleOrderDetail(detail);
        log.info("[delivery change] 订单 {} 加单 {}（{} {}），应收 {} 实收 {}", orderId,
                productName, unit, num, detail.getActualNum());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SaleOrderDetail> exchange(Long orderId, Long targetDetailId, Long skuId, String productName,
                                          String spec, String unit, BigDecimal num, BigDecimal actualNum, String remark) {
        SaleOrder order = requireConfirmedOrder(orderId);
        // 被换行标记退货（change_type=3，应送/实收归 0——应收为0口径）
        SaleOrderDetail returned = markReturned(orderId, targetDetailId, remark == null ? "换货：原商品退货" : remark,
                "换货被换行");
        // 换货新增行（change_type=2），同 change_group
        Long group = System.currentTimeMillis();
        SaleOrderDetail detail = new SaleOrderDetail();
        detail.setOrderId(orderId);
        detail.setCustomerId(order.getCustomerId());
        detail.setCustomerDeptId(order.getCustomerDeptId());
        detail.setOrderCode(order.getCode());
        detail.setSkuId(skuId);
        detail.setProductName(productName);
        detail.setProductUnit(unit);
        detail.setProductSpec(spec);
        detail.setProductPrice(num == null ? BigDecimal.ZERO : BigDecimal.ZERO); // 换入单价由前端传？——简化：0，展示用
        detail.setNum(num == null ? BigDecimal.ZERO : num);
        detail.setExpectAmount(detail.getProductPrice().multiply(detail.getNum()));
        detail.setActualNum(actualNum == null ? detail.getNum() : actualNum);
        detail.setSort(returned.getSort() == null ? nextSort(orderId) : returned.getSort());
        detail.setChangeType(2);
        detail.setChangeGroup(group);
        detail.setChangeRemark(remark == null ? "换货：" + productName : remark);
        detail.setIsDeleted(Boolean.FALSE);
        detail.setCreateBy(resolveOperator());
        detail.setCreateTime(DateUtils.getNowDate());
        saleOrderDetailMapper.insertSaleOrderDetail(detail);
        // 同组关联
        SaleOrderDetail updateReturned = new SaleOrderDetail();
        updateReturned.setId(returned.getId());
        updateReturned.setChangeGroup(group);
        saleOrderDetailMapper.updateSaleOrderDetail(updateReturned);
        returned.setChangeGroup(group);
        log.info("[delivery change] 订单 {} 换货：原行 {} → {}（组 {}）", orderId, targetDetailId, productName, group);
        return List.of(detail, returned);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleOrderDetail returnLine(Long orderId, Long targetDetailId, String remark) {
        requireConfirmedOrder(orderId);
        return markReturned(orderId, targetDetailId, remark, "退货");
    }

    // ==================== 辅助 ====================

    private SaleOrder requireConfirmedOrder(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("订单ID不能为空");
        }
        SaleOrder order = saleOrderMapper.selectSaleOrderById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        if (!SaleOrderStatus.CONFIRMED.getCode().equals(order.getStatus())) {
            throw new ServiceException("仅已确认订单可做配送后变更（草稿请直接编辑）");
        }
        return order;
    }

    /** 原明细行标记退货（change_type=3 应送/实收归0），返回被标记行 */
    private SaleOrderDetail markReturned(Long orderId, Long targetDetailId, String remark, String label) {
        SaleOrderDetail target = saleOrderDetailMapper.selectSaleOrderDetailById(targetDetailId);
        if (target == null || !orderId.equals(target.getOrderId())) {
            throw new ServiceException("被变更明细行不存在或不属于该订单");
        }
        if (target.getChangeType() != null && target.getChangeType() == 3) {
            throw new ServiceException("该明细行已是退货/被换行，不可重复变更");
        }
        SaleOrderDetail update = new SaleOrderDetail();
        update.setId(targetDetailId);
        update.setChangeType(3);
        update.setChangeGroup(null);
        update.setChangeRemark(remark == null ? label : remark);
        update.setNum(BigDecimal.ZERO);
        update.setActualNum(BigDecimal.ZERO);
        update.setUpdateBy(resolveOperator());
        update.setUpdateTime(DateUtils.getNowDate());
        saleOrderDetailMapper.updateSaleOrderDetail(update);
        target.setChangeType(3);
        target.setChangeRemark(update.getChangeRemark());
        target.setNum(BigDecimal.ZERO);
        target.setActualNum(BigDecimal.ZERO);
        return target;
    }

    /** 该订单当前明细排序号最大值+1 */
    private int nextSort(Long orderId) {
        com.lin.distribution.domain.SaleOrderDetail q = new com.lin.distribution.domain.SaleOrderDetail();
        q.setOrderId(orderId);
        List<SaleOrderDetail> list = saleOrderDetailMapper.selectSaleOrderDetailList(q);
        return list.stream().mapToInt(d -> d.getSort() == null ? 0 : d.getSort()).max().orElse(0) + 1;
    }

    private String resolveOperator() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
