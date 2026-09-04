-- =============================================================================
-- s23：D-055 退役对象清理（打印包 / 打印拆分配置 / 打印资源登记 / 补生成按钮）
--
-- 本脚本处理「代码已删、库对象残留」的收尾。**A 段可直接执行（幂等、不动数据）**；
-- B 段是删表动作，默认注释保护——需先全库备份（sql/db_bak/）并确认无历史依赖后再取消注释执行。
-- =============================================================================

SET NAMES utf8mb4;

-- ============================================================
-- A 段：停用已退役的按钮权限（幂等，可重复执行）
-- ============================================================

-- 2114「送货单补生成」：统一生成服务已在 D-055 删除，order:delivery:generateCustomer 无调用方
UPDATE `sys_menu`
SET `status`      = '1',   -- 1=停用（保留行与权限串，便于回溯）
    `update_by`   = 'system',
    `update_time` = NOW(),
    `remark`      = CONCAT(IFNULL(`remark`, ''), ' [D-055 生成服务退役，按钮停用]')
WHERE `menu_id` = 2114
  AND `status` <> '1';

-- ============================================================
-- B 段：删除退役表（危险动作，默认注释；执行前务必备份）
--   t_print_package / t_print_task / t_print_task_bak_20260902 —— P2/D-050 打印包（代码已删）
--   t_delivery_print_config / t_delivery_print_config_version —— W0-2.2 打印拆分配置（前端 0 调用）
--   t_print_asset —— W0-6 打印资源登记（前端 0 调用）
--   保留：t_print_preview_log（W0-4.4 预览留痕，仍在用）、t_delivery_print_log（D-055 打印分界，在用）
-- ============================================================
-- DROP TABLE IF EXISTS `t_print_task`;
-- DROP TABLE IF EXISTS `t_print_task_bak_20260902`;
-- DROP TABLE IF EXISTS `t_print_package`;
-- DROP TABLE IF EXISTS `t_delivery_print_config_version`;
-- DROP TABLE IF EXISTS `t_delivery_print_config`;
-- DROP TABLE IF EXISTS `t_print_asset`;

-- 自检：
-- SELECT menu_id, menu_name, status FROM sys_menu WHERE menu_id = 2114;
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = DATABASE()
--    AND table_name IN ('t_print_package','t_print_task','t_delivery_print_config','t_print_asset');
