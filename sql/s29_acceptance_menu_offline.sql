-- =============================================================================
-- s29：验收单菜单下线（验收全面订单视角）
--
-- 背景（2026-09-15 业务定稿）：验收的录入/查看/撤销全部收敛到**订单视角**
--   —— 订单列表「查看验收」→ 订单明细页验收模式；订单状态在验收提交时同步为「已验收」。
--   独立「验收单」台账页（/order/acceptance）不再是主路径，菜单隐藏。
--
-- 动作：sys_menu 2081「验收单」visible→1（隐藏），status→0（**保持启用，仅隐藏**）；
--       权限 acceptance:* 全部保留；页面（component order/acceptance/index）与路由保留，
--       仍可按 URL 直达 —— 历史送货单维度验收单的查看/撤销/补建继续可用
--       （订单列表对 status=2 历史单的「去验收」仍会路由到该页）。
-- 注：不设 status=1（停用）——停用会让 getRouters 不注册路由，历史单入口会直接失效。
-- 幂等：UPDATE 天然幂等。执行前请备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `visible`     = '1',
    `status`      = '0',
    `update_by`   = 'system',
    `update_time` = NOW(),
    `remark`      = CONCAT(IFNULL(`remark`, ''), ' [2026-09-15 菜单下线：验收收敛订单视角，入口=订单列表「查看验收」；页面保留供历史单维护]')
WHERE `menu_id` = 2081
  AND (`visible` <> '1' OR `status` <> '0');

-- 兼容：按 component 兜底(菜单ID 变动时)
UPDATE `sys_menu`
SET `visible`     = '1',
    `status`      = '0',
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `component` = 'order/acceptance/index'
  AND (`visible` <> '1' OR `status` <> '0');

-- 自检：
-- SELECT menu_id, menu_name, path, component, perms, visible, status FROM sys_menu
--  WHERE menu_id = 2081 OR component = 'order/acceptance/index';
