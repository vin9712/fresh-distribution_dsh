package com.lin.distribution.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 按勾选订单生成送货单请求（销售订单列表页抽屉，配送日期可调整）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryByOrdersDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 销售订单ID集合（必填；confirmDrafts=true 时允许包含草稿单） */
    @NotEmpty(message = "请选择要生成送货单的订单")
    private List<Long> orderIds;

    /** 配送日期（可空，默认取订单配送日期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /**
     * 勾选里若含草稿（status=0），是否在同一事务内先批量确认再出单。
     * <p>录单页「选订单·生成送货单」抽屉专用（D-021 主路径配套）：统一生成服务只捞
     * 已确认订单，草稿不先确认则必然空跑。失败整体回滚，不留「已确认但没出单」的半截状态。
     * 默认 false，保持订单列表页旧入口「只能勾已确认单」的既有语义。
     */
    private Boolean confirmDrafts;
}
