package com.lin.distribution.service.impl;

import java.util.List;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.SaleOrderMapper;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.service.SaleOrderService;

/**
 * 销售订单Service业务层处理
 *
 * @author lin
 * @date 2024-11-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements SaleOrderService {
    private final SaleOrderMapper saleOrderMapper;

    /**
     * 查询销售订单
     *
     * @param id 销售订单主键
     * @return 销售订单
     */
    @Override
    public SaleOrder selectSaleOrderById(Long id) {
        return saleOrderMapper.selectSaleOrderById(id);
    }

    /**
     * 查询销售订单列表
     *
     * @param saleOrder 销售订单
     * @return 销售订单
     */
    @Override
    public List<SaleOrder> selectSaleOrderList(SaleOrder saleOrder) {
        return saleOrderMapper.selectSaleOrderList(saleOrder);
    }

    /**
     * 新增销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    @Override
    public int insertSaleOrder(SaleOrder saleOrder) {
        saleOrder.setCreateTime(DateUtils.getNowDate());
        return saleOrderMapper.insertSaleOrder(saleOrder);
    }

    /**
     * 修改销售订单
     *
     * @param saleOrder 销售订单
     * @return 结果
     */
    @Override
    public int updateSaleOrder(SaleOrder saleOrder) {
        saleOrder.setUpdateTime(DateUtils.getNowDate());
        return saleOrderMapper.updateSaleOrder(saleOrder);
    }

    /**
     * 批量删除销售订单
     *
     * @param ids 需要删除的销售订单主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderByIds(Long[] ids) {
        return saleOrderMapper.deleteSaleOrderByIds(ids);
    }

    /**
     * 删除销售订单信息
     *
     * @param id 销售订单主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderById(Long id) {
        return saleOrderMapper.deleteSaleOrderById(id);
    }
}
