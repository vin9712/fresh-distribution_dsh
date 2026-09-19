package com.lin.distribution.dto;

import lombok.Data;

/**
 * 打印模板骨架生成请求（P3 动态生成，PR-D4）
 *
 * <p>生成器读取**当前真实结构**（客户名/配送点×班次）产出 JimuReport 设计 JSON，
 * 替代「导入别人给的静态 JSON 样板」。</p>
 *
 * @author dsh
 */
@Data
public class PrintTemplateGenerateDTO {

    /** 打印形态：MATRIX=总单跨点矩阵 / FLAT=点单平铺（缺省 FLAT） */
    private String printForm;

    /** 总单行形态：LONG=全交叉长表（动态列，推荐）/ WIDE=槽位宽表（套打）（缺省 LONG） */
    private String rowsType;

    /** 客户ID（用于推导标题与槽位列；宽表必填） */
    private Long customerId;

    /** 配送日期 yyyy-MM-dd（宽表按当日实际布局推导槽位；缺省取今天） */
    private String deliveryDate;

    /** 标题覆盖（缺省：总单用 ${hm.printTitle} 动态、点单用「送货单」） */
    private String title;

    /** 纸张：A4（缺省） */
    private String paper;

    /** 版式方向：portrait（缺省）/ landscape */
    private String layout;
}
