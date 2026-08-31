package com.lin.distribution.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按配送日期生成送货单前的「待生成清单」预览（送货单据页 生成 → 预览 → 确认 三步式）。
 *
 * <p>口径与 {@code DeliveryGenerationServiceImpl#doGenerateForCustomer} 完全同源（同一套
 * 遗漏订单判定 + 三态分支 + 组单策略快照 + 明细合并），仅做只读推演：不建批次、不落库、不加行锁。
 * 预览与真实生成之间若有新订单确认，实际生成结果以生成瞬间为准（幂等补齐，不会漏单）。</p>
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryGeneratePreviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 配送日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    /** 待处理客户数（有遗漏订单的客户） */
    private Integer customerCount;

    /** 待并入销售订单张数（按订单去重） */
    private Integer sourceOrderCount;

    /** 预计生成送货单张数 */
    private Integer expectedDeliveryCount;

    /** 预计送货明细行数（按各客户 merge_same_item 合并后口径） */
    private Integer expectedDetailCount;

    /** 待并入订单金额合计 */
    private BigDecimal totalAmount;

    /** 其中「作废重建」客户数（D-022，未打印原单会被作废） */
    private Integer rebuildCount;

    /** 其中「补充单」客户数（D-023，原单已打印/已送达，另出 doc_kind=1） */
    private Integer supplementCount;

    /** 当日未确认草稿订单张数（不会进入本次生成，仅提示） */
    private Integer draftOrderCount;

    /** 当日草稿订单号（最多展示若干张，超出留空由前端按 count 提示） */
    private List<String> draftOrderCodes = new ArrayList<>();

    /** 逐客户预览明细 */
    private List<CustomerPreview> customers = new ArrayList<>();

    /**
     * 单个客户的生成推演结果
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CustomerPreview implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long customerId;
        private String customerName;

        /** 组单策略（批次快照优先，无批次取客户当前配置） */
        private String scopeType;
        /** 组单策略中文：跨点总单 / 每点一单 */
        private String scopeDesc;
        /** 策略来源：BATCH_SNAPSHOT 当日批次快照（已锁定）/ CUSTOMER_CONFIG 客户当前配置（尚未建批次） */
        private String scopeSource;
        /** 策略来源说明（D-041：改客户配置不影响已建批次的当日） */
        private String scopeSourceDesc;
        /** 相同商品是否合并成行（D-024 不同价必拆行） */
        private Boolean mergeSameItem;

        /** 处理分支：CREATE 正常生成 / REBUILD 作废重建 / SUPPLEMENT 补充单 */
        private String action;
        private String actionDesc;
        /** 分支说明（人话，供确认前提示影响面） */
        private String actionTip;

        /** 预计生成送货单张数 */
        private Integer expectedDeliveryCount;
        /** 预计送货明细行数（合并后） */
        private Integer expectedDetailCount;
        /** 待并入订单金额合计 */
        private BigDecimal totalAmount;

        /** 待并入的销售订单 */
        private List<OrderPreview> orders = new ArrayList<>();
        /** 该客户当日既有有效送货单（作废重建/补充单分支非空） */
        private List<ExistingPreview> existingOrders = new ArrayList<>();
    }

    /**
     * 待并入的销售订单
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderPreview implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String code;
        private String customerName;
        private String customerDeptName;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate deliveryDate;
        private BigDecimal amount;
        /** 有效明细行数（订单行原始行数，未合并） */
        private Integer itemCount;
        /** 该单是否已在既有有效送货单中（作废重建分支下原单已含的订单为 true） */
        private Boolean rebuildCovered;
    }

    /**
     * 既有有效送货单（影响面提示）
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExistingPreview implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String code;
        private Integer status;
        private String statusDesc;
        private Integer printCount;
        private Integer docKind;
        private String deliveryPointName;
    }
}
