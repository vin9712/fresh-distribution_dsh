package com.lin.distribution.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lin.distribution.mapper.DeliveryBatchMapper;
import com.lin.distribution.service.DeliveryBatchService;
import com.lin.distribution.vo.DeliveryBatchViewVO;

import lombok.RequiredArgsConstructor;

/**
 * 配送批次查询服务实现（S14 §6.1 / §八，D-027/D-028）
 *
 * <p>内部总表不落物理明细（设计 §3.1）：新模型由 source_item 台账实时聚合；
 * 历史单（无台账）回退送货明细行聚合。两路 SQL 均按 标准品名×配送点 返回扁平行，
 * 服务层聚合成「品名行 + 各点小计」视图，不含任何价格金额。</p>
 *
 * @author dsh
 */
@Service
@RequiredArgsConstructor
public class DeliveryBatchServiceImpl implements DeliveryBatchService {

    private final DeliveryBatchMapper deliveryBatchMapper;

    @Override
    public List<DeliveryBatchViewVO> selectBatchView(Long customerId, String deliveryDate) {
        List<DeliveryBatchViewVO.Row> rows = deliveryBatchMapper.selectBatchViewRowsBySource(customerId, deliveryDate);
        if (rows.isEmpty()) {
            // 历史单回退：无 source_item 台账时按送货明细行聚合
            rows = deliveryBatchMapper.selectBatchViewRowsByDetail(customerId, deliveryDate);
        }

        // 品名行聚合（LinkedHashMap 保持品名排序），各点小计按出现顺序归并（同点多行防御性合量）
        Map<String, DeliveryBatchViewVO> byProduct = new LinkedHashMap<>();
        for (DeliveryBatchViewVO.Row row : rows) {
            String key = row.getSkuId() == null ? row.getProductName() : "sku:" + row.getSkuId();
            DeliveryBatchViewVO vo = byProduct.computeIfAbsent(key, k -> {
                DeliveryBatchViewVO v = new DeliveryBatchViewVO();
                v.setSkuId(row.getSkuId());
                v.setProductName(row.getProductName());
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
}
