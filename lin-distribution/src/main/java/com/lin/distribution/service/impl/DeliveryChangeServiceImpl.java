package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.common.utils.SecurityUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.AcceptanceService;
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
    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceItemMapper acceptanceItemMapper;
    private final AcceptanceService acceptanceService;

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

    /**
     * 配送后变更回退（OA 定稿 2026-09-09）：加单/退货/换货均可回退。
     * 退货/换货原应收数量取 change_original_num 快照（markReturned 时留痕，s28）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaleOrderDetail revokeChange(Long orderId, Long detailId) {
        requireConfirmedOrder(orderId);
        SaleOrderDetail d = saleOrderDetailMapper.selectSaleOrderDetailById(detailId);
        if (d == null || !orderId.equals(d.getOrderId())) {
            throw new ServiceException("变更明细不存在或不属于该订单");
        }
        if (d.getChangeType() == null) {
            throw new ServiceException("该明细行无配送后变更标记，无需回退");
        }
        // 验收守卫：订单维度验收单已提交则先撤销；草稿自动同步（删行/恢复后 syncMissingItems 对齐）
        Acceptance acc = acceptanceMapper.selectBySaleOrder(orderId);
        if (acc != null && AcceptanceStatus.SUBMITTED.getCode().equals(acc.getStatus())) {
            throw new ServiceException("该订单已验收（" + acc.getCode() + "），请先撤销验收再回退变更");
        }
        Integer type = d.getChangeType();
        List<Long> affectedDetailIds = new ArrayList<>();
        if (type == 1) {
            // 加单回退：删除加单行
            affectedDetailIds.add(d.getId());
            saleOrderDetailMapper.deleteSaleOrderDetailById(d.getId());
        } else if (type == 2 || type == 3) {
            // 换货组整组回退；独立退货行仅恢复本行
            List<SaleOrderDetail> members = new ArrayList<>();
            members.add(d);
            if (d.getChangeGroup() != null) {
                saleOrderDetailMapper.selectValidByOrderIdForView(orderId).stream()
                        .filter(x -> d.getChangeGroup().equals(x.getChangeGroup())
                                && x.getChangeType() != null && !d.getId().equals(x.getId()))
                        .forEach(members::add);
            }
            for (SaleOrderDetail m : members) {
                if (m.getChangeType() != null && m.getChangeType() == 3) {
                    restoreReturnedRow(m);
                } else if (m.getChangeType() != null && m.getChangeType() == 2) {
                    affectedDetailIds.add(m.getId());
                    saleOrderDetailMapper.deleteSaleOrderDetailById(m.getId());
                }
            }
        } else {
            throw new ServiceException("该变更类型不支持回退");
        }
        // 验收草稿同步：删掉被回退行的验收行，恢复行由 syncMissingItems 重新生成，总额重算
        if (acc != null && !affectedDetailIds.isEmpty()) {
            acceptanceItemMapper.deleteBySaleOrderDetailIds(acc.getId(), affectedDetailIds);
            acceptanceService.syncMissingItems(acc.getId());
        } else if (acc != null) {
            acceptanceService.syncMissingItems(acc.getId());
        }
        log.info("[delivery change] 订单 {} 回退变更明细 {}（type={}）", orderId, detailId, type);
        return d;
    }

    /** 恢复被标记退货的行：change_type 还原为 0，数量取快照（无快照的历史行用 应收金额/单价 推算） */
    private void restoreReturnedRow(SaleOrderDetail returned) {
        BigDecimal original = returned.getChangeOriginalNum();
        if (original == null && returned.getProductPrice() != null
                && returned.getProductPrice().compareTo(BigDecimal.ZERO) > 0
                && returned.getExpectAmount() != null) {
            original = returned.getExpectAmount().divide(returned.getProductPrice(), 2, java.math.RoundingMode.HALF_UP);
        }
        if (original == null) {
            throw new ServiceException("明细【" + returned.getProductName() + "】的原数量已不可追溯，请核对手工处理");
        }
        SaleOrderDetail update = new SaleOrderDetail();
        update.setId(returned.getId());
        update.setChangeType(0);
        update.setNum(original);
        update.setActualNum(original);
        update.setUpdateBy(resolveOperator());
        update.setUpdateTime(DateUtils.getNowDate());
        saleOrderDetailMapper.updateSaleOrderDetail(update);
        returned.setChangeType(0);
        returned.setNum(original);
        returned.setActualNum(original);
    }

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
        // 原应收数量快照（s28）：num/actual_num 归零前留痕，供回退恢复
        update.setChangeOriginalNum(target.getNum());
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
