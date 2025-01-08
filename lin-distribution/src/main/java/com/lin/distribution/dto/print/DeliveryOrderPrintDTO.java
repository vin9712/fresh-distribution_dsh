package com.lin.distribution.dto.print;

import com.lin.distribution.domain.SaleOrderDetail;
import com.lin.distribution.vo.SaleOrderDetailVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryOrderPrintDTO implements Serializable {
    private String deliveryName;
    private List<SaleOrderDetailVo> table;


}
