-- =============================================================================
-- s32：销售订单「订单来源 / 订单类型」默认值（后台下单 / 正常订单）
--
-- 背景（2026-09-16）：订单来源、订单类型业务上无可选项（无线上单、无其它类型），
--   前端已移除录入/筛选/列表展示，后端建单不再设置 source/type；
--   SaleOrderMapper.xml 的 insert 为动态列（<if test="source != null">），
--   值为 null 时整列省略 → 由本脚本的 DB DEFAULT 1 兜底。
--   ⚠ 此 DEFAULT 是必需兜底而非冗余：去掉后所有后端建单会报
--   `Field 'source' doesn't have a default value`。
--
-- 幂等：MODIFY COLUMN 绝对值，可重复执行。
-- 回滚参考：MODIFY 回 `... NOT NULL COMMENT '订单来源：1后台下单,2线上下单'`（去掉 DEFAULT）。
-- =============================================================================

SET NAMES utf8mb4;

ALTER TABLE `t_sale_order`
    MODIFY COLUMN `source` tinyint(3) unsigned NOT NULL DEFAULT 1 COMMENT '订单来源：1后台下单,2线上下单（默认1后台下单）',
    MODIFY COLUMN `type`   tinyint(3) unsigned NOT NULL DEFAULT 1 COMMENT '订单类型：1正常订单,2加单（默认1正常订单）';

-- 自检：
-- SELECT COLUMN_NAME, COLUMN_DEFAULT, IS_NULLABLE, COLUMN_COMMENT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_sale_order' AND COLUMN_NAME IN ('source','type');
