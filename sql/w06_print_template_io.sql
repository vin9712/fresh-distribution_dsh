-- ============================================================
-- w06: 模板导入导出与资源治理（蓝图 §8-S0 第 28~39 条）
--   1. 打印资源登记表 t_print_asset：Logo/底图等本机文件目录资源，按模板版本固化；
--      历史引用资源不可物理删除（删单时仅标记软删，被引用则禁止删除）。
--   2. 草稿导入为未绑定模板需「重走发布门禁」——状态列与绑定字段已在 t_print_template 就绪，本文件不重复。
--   3. 资源引用计数冗余字段 ref_count（冗余快照，删除前以扫描为准；扫描命中则禁止删除）。
-- 幂等: ALTER/CREATE 均 WHERE NOT EXISTS 或 IF NOT EXISTS，可重复执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_print_asset` (
  `id`            bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `asset_key`     varchar(64)   NOT NULL COMMENT '资源键（UUID，对外暴露，content 引用形如 /profile/print/assets/<filename>）',
  `file_name`     varchar(200)  NOT NULL COMMENT '存储文件名（UUID 命名，保留原扩展名）',
  `origin_name`   varchar(200)  DEFAULT NULL COMMENT '原始文件名（展示用）',
  `content_type`  varchar(100)  NOT NULL COMMENT 'MIME 类型（仅 image/png|jpeg|jpg）',
  `size_bytes`    bigint(20)    NOT NULL DEFAULT 0 COMMENT '文件字节数（上限 5MB）',
  `storage_path`  varchar(500)  NOT NULL COMMENT '相对 profile 的存储路径，如 print/assets/xxx.png',
  `url`           varchar(500)  NOT NULL COMMENT '对外访问 URL（/profile/print/assets/xxx.png）',
  `ref_count`     int(10)       NOT NULL DEFAULT 0 COMMENT '冗余引用计数快照（删除前以扫描为准）',
  `is_deleted`    tinyint(1)    NOT NULL DEFAULT 0 COMMENT '软删（被引用资源禁止物理删除）',
  `create_by`     varchar(64)   DEFAULT '' COMMENT '上传人',
  `create_time`   datetime      DEFAULT NULL COMMENT '上传时间',
  `update_by`     varchar(64)   DEFAULT '' COMMENT '更新人',
  `update_time`   datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`        varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_key` (`asset_key`),
  UNIQUE KEY `uk_storage_path` (`storage_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印资源登记表（W0-6）';
