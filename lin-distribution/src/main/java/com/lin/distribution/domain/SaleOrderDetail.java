package com.lin.distribution.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 销售订单详情对象 t_sale_order_detail
 *
 * @author lin
 * @date 2024-11-23
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class SaleOrderDetail extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单ID
     */
    @Excel(name = "订单ID")
    private Long orderId;

    /**
     * 客户ID
     */
    @Excel(name = "客户ID")
    private Long customerId;

    /**
     * 配送点ID
     */
    @Excel(name = "配送点ID")
    private Long customerDeptId;

    /**
     * 商品ID（临时添加，可为空）
     */
    @Excel(name = "商品ID", readConverterExp = "临=时添加，可为空")
    private Long skuId;

    /**
     * 订单编号
     */
    @Excel(name = "订单编号")
    private String orderCode;

    /**
     * 商品名称
     */
    @Excel(name = "商品名称")
    private String productName;

    /**
     * 商品单位（可为空）
     */
    @Excel(name = "商品单位", readConverterExp = "可=为空")
    private String productUnit;

    /**
     * 商品单价
     */
    @Excel(name = "商品单价")
    private BigDecimal productPrice;

    /**
     * 商品规格
     */
    @Excel(name = "商品规格")
    private String productSpec;

    /**
     * 计划数量
     */
    @Excel(name = "计划数量")
    private BigDecimal num;

    /**
     * 计划总金额
     */
    @Excel(name = "计划总金额")
    private BigDecimal expectAmount;

    /**
     * 报价来源（S1-1.3 手工定价留痕）：quote=客户报价，manual=手工定价，temp=临时商品默认价；空=历史数据/未标注
     */
    private String priceSource;

    /**
     * 原建议价（S1-1.3 手工定价留痕：取价引擎/报价快照价，手工定价时用于比对审计；无报价为空）
     */
    private BigDecimal refPrice;

    /**
     * 手工定价原因（S1-1.3：报价来源为 manual 时必填，订单确认时写入操作日志）
     */
    private String priceReason;

    /**
     * 损耗原因（字典 biz_loss_reason，实收 < 下单数时必填）
     */
    private String lossReason;

    /**
     * 验收商品单价
     */
    @Excel(name = "验收商品单价")
    private BigDecimal actualPrice;

    /**
     * 验收数量
     */
    @Excel(name = "验收数量")
    private BigDecimal actualNum;

    /**
     * 验收总金额
     */
    @Excel(name = "验收总金额")
    private BigDecimal actualAmount;

    /**
     * 订单详情排序
     */
    private Integer sort;

    /**
     * 变更标记（D-055）：0正常(含配送前更新) / 1加单(配送后补充) / 2换货 / 3退货
     */
    private Integer changeType;

    /**
     * 换货组号（被换行与换货行同组关联，非换货为 NULL）
     */
    private Long changeGroup;

    /**
     * 变更说明（如：换货 原土豆→大白菜）
     */
    private String changeRemark;

    /** 配送后变更回退用：标记退货/换货前的原应收数量快照（s28，markReturned 时写入） */
    private BigDecimal changeOriginalNum;

    /**
     * 逻辑删除
     */
    private Boolean isDeleted;

    /**
     * 版本号
     */
    @Version
    private Integer version;

    /**
     * 查询字段：常用商品统计-下单次数（frequent 接口）
     */
    @TableField(exist = false)
    private Long orderCount;

    /**
     * 查询字段：品类名称（生成单据预览按品类分组用，临时商品归"临时商品"）
     */
    @TableField(exist = false)
    private String categoryName;

    /**
     * 查询字段：配送点名称（D-055 收尾：点单全点视图按点分组，tab 标签用）
     */
    @TableField(exist = false)
    private String customerDeptName;

    /**
     * 查询字段：常用商品统计-最近下单时间（frequent 接口）
     */
    @TableField(exist = false)
    private String lastOrderTime;

}
