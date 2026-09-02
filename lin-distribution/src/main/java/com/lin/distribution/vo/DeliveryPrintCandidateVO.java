package com.lin.distribution.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.lin.distribution.domain.PrintTemplate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送货单候选打印模板（P1/D-048，《送货单矩阵总表与批次视图设计》§六）
 *
 * <p>后端出候选模板，取代前端复刻的「三级绑定过滤」（原先前端 null vs undefined 比较不一致、\n * 规则双写必然漂移）。返回同印刷形态、该客户可用的已发布模板，按绑定层级排序；\n * 若命中「全局默认」则标记 {@code matchGlobalDefault}，打印对话框据此告警。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintCandidateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 打印形态 MATRIX=跨点总单 / FLAT=点单平铺 */
    private String printForm;

    /** 是否命中「全局默认」模板（未配客户级/客户+点模板时才为 true，用于告警） */
    private Boolean matchGlobalDefault;

    /** 候选已发布模板（bind_type 升序） */
    @Builder.Default
    private List<PrintTemplate> templates = new ArrayList<>();
}
