package com.lin.distribution.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 销售订单简报（草稿箱陈旧判定用）
 *
 * <p>只暴露编号/主键/状态/最后更新时间，供前端与本地草稿的基线（载入时的 updateTime）比对，
 * 判断「该编号的订单是否已在服务端被保存/推进」→ 是则本地草稿为旧数据，应清理。</p>
 *
 * <p>updateTime 用与 {@code /order/sale/{id}} 一致的字符串格式（yyyy-MM-dd HH:mm:ss），
 * 前端直接字符串相等比对，避开时区/时钟偏移。</p>
 *
 * @author dsh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleOrderBriefVO {

    private Long id;

    private String code;

    private Integer status;

    /** 最后更新时间（yyyy-MM-dd HH:mm:ss，与订单详情接口同口径） */
    private String updateTime;
}
