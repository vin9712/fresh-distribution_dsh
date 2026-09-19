package com.lin.distribution.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 客户配送点排序请求（D-074：总单列顺序）
 *
 * <p>按 {@code ids} 的先后顺序把该客户的配送点重排为 sortNo = 1..N。</p>
 *
 * @author dsh
 */
@Data
public class CustomerDeptSortDTO {

    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @NotEmpty(message = "排序列表不能为空")
    private List<Long> ids;
}
