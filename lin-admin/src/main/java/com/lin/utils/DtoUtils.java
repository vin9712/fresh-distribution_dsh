package com.lin.utils;

import com.lin.common.utils.bean.BeanUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DtoUtils {

    public List<OrderDetail> modifyAndAddOrderDetails(Long orderId, List<OrderDetail> existingDetails, List<OrderDetail> requestList) {
        // 步骤 A: 通过现有订单详情创建 map
        Map<Long, OrderDetail> existingDetailsMap = existingDetails.stream()
                .collect(Collectors.toMap(
                        OrderDetail::getId,
                        Function.identity(),
                        (existing, replacement) -> existing));

        // 步骤 B: 修改指定订单详情
        List<OrderDetail> updatedDetails = new ArrayList<>();
        for (OrderDetail detail : requestList) {
            if (detail.getId() == null) {
                updatedDetails.add(detail);
                continue;
            }

            OrderDetail existingDetail = existingDetailsMap.getOrDefault(detail.getId(), null);
            if (existingDetail != null) {
                BeanUtils.copyProperties(detail, existingDetail);
                updatedDetails.add(existingDetail);
            } else {
                updatedDetails.add(detail);
            }
        }

        // 步骤 C: 保存所有订单详情
        return updatedDetails;
    }

}
