package com.lin.distribution.domain;

import com.lin.common.annotation.Excel;
import com.lin.common.core.domain.BaseEntity;
import lombok.*;

/**
 * 打印资源登记对象 t_print_asset（W0-6 模板导入导出与资源治理）
 * <p>Logo/底图等本机文件目录资源，按模板版本固化；历史引用资源不可物理删除。
 *
 * @author dsh
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class PrintAsset extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 资源键（UUID，对外暴露） */
    @Excel(name = "资源键")
    private String assetKey;

    /** 存储文件名（UUID 命名，保留原扩展名） */
    private String fileName;

    /** 原始文件名（展示用） */
    @Excel(name = "原始文件名")
    private String originName;

    /** MIME 类型（仅 image/png|jpeg|jpg） */
    @Excel(name = "MIME类型")
    private String contentType;

    /** 文件字节数（上限 5MB） */
    @Excel(name = "字节数")
    private Long sizeBytes;

    /** 相对 profile 的存储路径，如 print/assets/xxx.png */
    private String storagePath;

    /** 对外访问 URL（/profile/print/assets/xxx.png） */
    @Excel(name = "访问URL")
    private String url;

    /** 冗余引用计数快照（删除前以扫描为准） */
    private Integer refCount;

    /** 软删（被引用资源禁止物理删除） */
    private Boolean isDeleted;
}
