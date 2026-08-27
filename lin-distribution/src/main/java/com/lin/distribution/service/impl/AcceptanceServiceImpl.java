package com.lin.distribution.service.impl;

import com.lin.common.exception.ServiceException;
import com.lin.common.utils.DateUtils;
import com.lin.distribution.constant.AcceptanceStatus;
import com.lin.distribution.constant.DeliveryOrderStatus;
import com.lin.distribution.constant.SaleOrderStatus;
import com.lin.distribution.domain.Acceptance;
import com.lin.distribution.domain.AcceptanceItem;
import com.lin.distribution.domain.DeliveryOrder;
import com.lin.distribution.domain.DeliveryOrderDetail;
import com.lin.distribution.dto.AcceptanceUpdateDTO;
import com.lin.distribution.mapper.AcceptanceItemMapper;
import com.lin.distribution.mapper.AcceptanceMapper;
import com.lin.distribution.mapper.DeliveryOrderDetailMapper;
import com.lin.distribution.mapper.DeliveryOrderMapper;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.service.AcceptanceService;
import com.lin.distribution.service.BizCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 验收单Service业务层处理（DESIGN.md §9）
 * 实收金额=actual_quantity×unit_price（后端重算）；损耗=实收−送货（可为负，负值必填原因）
 *
 * @author dsh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptanceServiceImpl implements AcceptanceService {
    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceItemMapper acceptanceItemMapper;
    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderDetailMapper deliveryOrderDetailMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final BizCodeService bizCodeService;

    @Override
    public Acceptance selectAcceptanceById(Long id) {
        return acceptanceMapper.selectAcceptanceById(id);
    }

    @Override
    public List<Acceptance> selectAcceptanceList(Acceptance acceptance) {
        return acceptanceMapper.selectAcceptanceList(acceptance);
    }

    @Override
    public List<AcceptanceItem> selectItemListByAcceptanceId(Long acceptanceId) {
        return acceptanceItemMapper.selectListByAcceptanceId(acceptanceId);
    }

    @Override
    @Transactional
    public Acceptance createByDeliveryOrder(Long deliveryOrderId) {
        if (deliveryOrderId == null) {
            throw new ServiceException("送货单ID不能为空");
        }
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(deliveryOrderId);
        if (deliveryOrder == null) {
            throw new ServiceException("送货单不存在");
        }
        if (!Objects.equals(deliveryOrder.getStatus(), DeliveryOrderStatus.DELIVERED.getCode())) {
            throw new ServiceException("仅已送达的送货单可生成验收单");
        }
        // 一单一验守卫
        if (acceptanceMapper.countByDeliveryOrderId(deliveryOrderId) > 0) {
            throw new ServiceException("该送货单已生成验收单，一单一验");
        }

        List<DeliveryOrderDetail> details = deliveryOrderDetailMapper.selectListByDeliveryId(deliveryOrderId);
        if (CollectionUtils.isEmpty(details)) {
            throw new ServiceException("送货单明细为空，无法生成验收单");
        }

        List<AcceptanceItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;
        for (DeliveryOrderDetail detail : details) {
            BigDecimal price = detail.getPrice() == null ? BigDecimal.ZERO : detail.getPrice();
            BigDecimal delivered = detail.getNum() == null ? BigDecimal.ZERO : detail.getNum();
            AcceptanceItem item = new AcceptanceItem();
            item.setDeliveryItemId(detail.getId());
            item.setSkuId(detail.getSkuId());
            item.setProductName(detail.getProductName());
            item.setProductSpec(detail.getProductSpec());
            item.setProductUnit(detail.getProductUnit());
            item.setDeliveredQuantity(delivered);
            item.setActualQuantity(delivered); // 默认实收=送货，可超送在录入时改
            item.setUnitPrice(price);
            item.setDifferenceQuantity(BigDecimal.ZERO);
            item.setActualAmount(scale(price.multiply(delivered)));
            item.setSort(sort++);
            items.add(item);
            total = total.add(item.getActualAmount());
        }

        Acceptance acceptance = new Acceptance();
        acceptance.setCode(bizCodeService.nextDailyCode("acceptance", "YS", 3));
        acceptance.setDeliveryOrderId(deliveryOrderId);
        acceptance.setCustomerId(deliveryOrder.getCustomerId());
        acceptance.setDeliveryPointId(deliveryOrder.getDeliveryPointId());
        acceptance.setAcceptDate(deliveryOrder.getDeliveryDate());
        acceptance.setTotalAmount(total);
        acceptance.setStatus(AcceptanceStatus.DRAFT.getCode());
        acceptance.setCreateTime(DateUtils.getNowDate());
        acceptanceMapper.insertAcceptance(acceptance);

        for (AcceptanceItem item : items) {
            item.setAcceptanceId(acceptance.getId());
        }
        acceptanceItemMapper.insertAcceptanceItemBatch(items);
        return acceptance;
    }

    @Override
    @Transactional
    public Acceptance updateDraft(AcceptanceUpdateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new ServiceException("验收单ID不能为空");
        }
        Acceptance acceptance = getExistAcceptance(dto.getId());
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可修改");
        }
        if (CollectionUtils.isEmpty(dto.getItems())) {
            throw new ServiceException("验收明细不能为空");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (AcceptanceUpdateDTO.Item dtoItem : dto.getItems()) {
            if (dtoItem.getId() == null) {
                throw new ServiceException("验收明细行ID不能为空");
            }
            AcceptanceItem item = acceptanceItemMapper.selectAcceptanceItemById(dtoItem.getId());
            if (item == null || !Objects.equals(item.getAcceptanceId(), acceptance.getId())) {
                throw new ServiceException("验收明细不存在");
            }
            BigDecimal actual = dtoItem.getActualQuantity();
            if (actual == null) {
                throw new ServiceException("实收数量不能为空");
            }
            if (actual.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("实收数量不能为负");
            }
            BigDecimal price = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal delivered = item.getDeliveredQuantity() == null ? BigDecimal.ZERO : item.getDeliveredQuantity();
            BigDecimal loss = scale(actual.subtract(delivered));
            String lossReason = dtoItem.getLossReason();
            if (loss.compareTo(BigDecimal.ZERO) < 0 && StringUtils.isBlank(lossReason)) {
                throw new ServiceException("损耗为负时必须填写原因：" + item.getProductName());
            }

            AcceptanceItem update = new AcceptanceItem();
            update.setId(item.getId());
            update.setActualQuantity(actual);
            update.setDifferenceQuantity(loss);
            // 负损耗保留原因；非负损耗清空原因避免脏数据
            update.setLossReason(loss.compareTo(BigDecimal.ZERO) < 0 ? lossReason : null);
            update.setActualAmount(scale(price.multiply(actual)));
            acceptanceItemMapper.updateAcceptanceItem(update);
            total = total.add(update.getActualAmount());
        }

        Acceptance update = new Acceptance();
        update.setId(acceptance.getId());
        update.setAcceptDate(dto.getAcceptDate());
        update.setRemark(dto.getRemark());
        update.setTotalAmount(total);
        update.setUpdateTime(DateUtils.getNowDate());
        acceptanceMapper.updateAcceptance(update);

        acceptance.setTotalAmount(total);
        acceptance.setAcceptDate(dto.getAcceptDate() == null ? acceptance.getAcceptDate() : dto.getAcceptDate());
        return acceptance;
    }

    @Override
    @Transactional
    public Acceptance submit(Long id) {
        Acceptance acceptance = getExistAcceptance(id);
        if (!Objects.equals(acceptance.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
            throw new ServiceException("仅草稿状态可提交");
        }
        Acceptance update = new Acceptance();
        update.setId(id);
        update.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        update.setUpdateTime(DateUtils.getNowDate());
        acceptanceMapper.updateAcceptance(update);

        // 同组已配送订单 → ACCEPTED（DESIGN.md §7.1）
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectDeliveryOrderById(acceptance.getDeliveryOrderId());
        if (deliveryOrder != null) {
            saleOrderMapper.updateStatusByDeliveryGroup(
                    deliveryOrder.getCustomerId(),
                    deliveryOrder.getDeliveryPointId(),
                    deliveryOrder.getDeliveryDate(),
                    SaleOrderStatus.DELIVERED.getCode(),
                    SaleOrderStatus.ACCEPTED.getCode());
        }
        acceptance.setStatus(AcceptanceStatus.SUBMITTED.getCode());
        return acceptance;
    }

    @Override
    @Transactional
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            Acceptance exist = acceptanceMapper.selectAcceptanceById(id);
            if (exist == null) {
                continue;
            }
            if (!Objects.equals(exist.getStatus(), AcceptanceStatus.DRAFT.getCode())) {
                throw new ServiceException("仅草稿状态可删除：" + exist.getCode());
            }
            acceptanceItemMapper.deleteAcceptanceItemByAcceptanceId(id);
        }
        return acceptanceMapper.deleteAcceptanceByIds(ids);
    }

    private Acceptance getExistAcceptance(Long id) {
        if (id == null) {
            throw new ServiceException("验收单ID不能为空");
        }
        Acceptance exist = acceptanceMapper.selectAcceptanceById(id);
        if (exist == null) {
            throw new ServiceException("验收单不存在");
        }
        return exist;
    }

    private BigDecimal scale(BigDecimal value) {
        return NumberUtils.toScaledBigDecimal(value, 2, RoundingMode.HALF_UP);
    }
}
