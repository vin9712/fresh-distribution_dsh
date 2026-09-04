-- =============================================================================
-- s15：矩阵布局快照列加宽（P0-A/D-045，补齐入库版）
--
-- 背景：t_delivery_batch.layout_json 原为 varchar(500)，实测 3 点 3 档已 653 字符，
--       STRICT_TRANS_TABLES 下超长会在生成事务内报错 → 改 text。
-- 现状：D-055 视图化后「生成成功后定格布局」的写入路径（refreshLayout）已退役，
--       layout_json 仅保留历史批次快照的读取；列布局按启用配送点实时推导。
--       本脚本仍保留，用于兼容历史批次（快照可能已落库）。
-- 幂等：MODIFY 可重复执行。执行前请备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

ALTER TABLE `t_delivery_batch`
    MODIFY COLUMN `layout_json` text DEFAULT NULL
        COMMENT '矩阵布局快照（列/价档 append-only，D-045；D-055 后仅历史批次有值）';

-- 自检：
-- SELECT column_type FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_delivery_batch' AND column_name = 'layout_json';
