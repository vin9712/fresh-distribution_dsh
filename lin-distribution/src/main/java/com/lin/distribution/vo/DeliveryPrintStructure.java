package com.lin.distribution.vo;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 送货单打印结构（W0-2.2，蓝图「拆单标识/针式分页」）
 *
 * <p>描述一张送货单明细的打印顺序与分页桶：
 * {@code order} 为按打印顺序排列的明细ID全排列；{@code pages} 为按分页规则
 * （针式固定每页 10 条；A4 按页高自动分页）推导出的「第 N/M 张」桶。
 * 序列化为 {@code t_delivery_print_config.structure_json} 与版本快照。</p>
 *
 * @author dsh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPrintStructure implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 打印顺序明细ID全排列 */
    private List<Long> order;

    /** 分页桶（含第 N/M 张标识） */
    private List<Page> pages;

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public static DeliveryPrintStructure fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, DeliveryPrintStructure.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 分页桶
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 页码（从 1 起） */
        private Integer pageNo;

        /** 总页数 */
        private Integer totalPages;

        /** 本页明细ID */
        private List<Long> detailIds;

        /** 页签「第 N/M 张」（即使仅一页也显示 第 1/1 张） */
        private String pageLabel;
    }
}
