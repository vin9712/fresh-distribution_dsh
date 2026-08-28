package com.lin.distribution.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 验收单撤销请求（S14/T5，DESIGN.md §5.4：原因必填 + 完整审计快照）
 *
 * @author dsh
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AcceptanceRevokeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 撤销原因（必填，落 t_acceptance.revoke_reason 与撤回审计） */
    @NotBlank(message = "撤销原因不能为空")
    private String reason;
}
