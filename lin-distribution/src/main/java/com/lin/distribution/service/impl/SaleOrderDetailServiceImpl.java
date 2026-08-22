package com.lin.distribution.service.impl;

import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.lin.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.lin.distribution.mapper.SaleOrderDetailMapper;
import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.service.SaleOrderDetailService;

/**
 * 销售订单详情Service业务层处理
 *
 * @author lin
 * @date 2024-11-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderDetailServiceImpl implements SaleOrderDetailService {
    private final SaleOrderDetailMapper saleOrderDetailMapper;

    /**
     * 查询销售订单详情
     *
     * @param id 销售订单详情主键
     * @return 销售订单详情
     */
    @Override
    public SaleOrderDetail selectSaleOrderDetailById(Long id) {
        return saleOrderDetailMapper.selectSaleOrderDetailById(id);
    }

    /**
     * 查询销售订单详情列表
     *
     * @param saleOrderDetail 销售订单详情
     * @return 销售订单详情
     */
    @Override
    public List<SaleOrderDetail> selectSaleOrderDetailList(SaleOrderDetail saleOrderDetail) {
        return saleOrderDetailMapper.selectSaleOrderDetailList(saleOrderDetail);
    }

    /**
     * 新增销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    @Override
    public int insertSaleOrderDetail(SaleOrderDetail saleOrderDetail) {
        saleOrderDetail.setCreateTime(DateUtils.getNowDate());
        return saleOrderDetailMapper.insertSaleOrderDetail(saleOrderDetail);
    }

    /**
     * 修改销售订单详情
     *
     * @param saleOrderDetail 销售订单详情
     * @return 结果
     */
    @Override
    public int updateSaleOrderDetail(SaleOrderDetail saleOrderDetail) {
        saleOrderDetail.setUpdateTime(DateUtils.getNowDate());
        return saleOrderDetailMapper.updateSaleOrderDetail(saleOrderDetail);
    }

    /**
     * 批量删除销售订单详情
     *
     * @param ids 需要删除的销售订单详情主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderDetailByIds(Long[] ids) {
        return saleOrderDetailMapper.deleteSaleOrderDetailByIds(ids);
    }

    /**
     * 删除销售订单详情信息
     *
     * @param id 销售订单详情主键
     * @return 结果
     */
    @Override
    public int deleteSaleOrderDetailById(Long id) {
        return saleOrderDetailMapper.deleteSaleOrderDetailById(id);
    }

    /**
     * 常用商品统计：近 N 天下单频率最高的 SKU（录单页"常用"面板）
     */
    @Override
    public List<SaleOrderDetail> selectFrequentSkuList(Long customerId, Integer days, Integer limit) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        int d = (days == null || days <= 0) ? 30 : Math.min(days, 90);
        int lim = (limit == null || limit <= 0) ? 20 : Math.min(limit, 50);
        LocalDateTime startTime = LocalDate.now().minusDays(d).atStartOfDay();
        return saleOrderDetailMapper.selectFrequentSkuList(customerId, startTime, lim);
    }
}
