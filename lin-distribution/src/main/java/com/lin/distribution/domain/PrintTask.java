package com.lin.distribution.domain;

import com.baomidou.mybatisplus.annotation.Version;
import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 打印任务对象 t_print_task
 *
 * @author lin
 * @date 2025-01-07
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class PrintTask extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 模板ID
     */
    @Excel(name = "模板ID")
    private Long templateId;

    /**
     * 订单ID
     */
    @Excel(name = "订单ID")
    private Long orderId;

    /**
     * 请求ID
     */
    @Excel(name = "请求ID")
    private String requestId;

    /**
     * 任务状态: 0-新增, 1-完成, 2-取消, 3-失败
     */
    @Excel(name = "任务状态: 0-新增, 1-完成, 2-取消, 3-失败")
    private Integer status;

    /**
     * 版本号
     */
    @Excel(name = "版本号")
    @Version
    private Integer version;

}
