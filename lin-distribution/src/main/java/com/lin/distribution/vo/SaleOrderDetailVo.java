package com.lin.distribution.vo;

import com.lin.common.utils.bean.BeanUtils;
import com.lin.distribution.domain.SaleOrder;
import com.lin.distribution.domain.SaleOrderDetail;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class SaleOrderDetailVo extends SaleOrderDetail {
    private Integer index;

    public static List<SaleOrderDetailVo> from(List<SaleOrderDetail> details) {
        List<SaleOrderDetailVo> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(details)) {
            return result;
        }
        int size = details.size();
        for (int i = 0; i < size; i++) {
            SaleOrderDetail detail = details.get(i);
            SaleOrderDetailVo vo = new SaleOrderDetailVo();
            BeanUtils.copyProperties(detail, vo);
            vo.setIndex(i + 1);
            result.add(vo);
        }
        return result;
    }
}
