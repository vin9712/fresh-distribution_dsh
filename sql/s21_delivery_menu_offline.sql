-- =============================================================================
-- s21：D-055 送货单据菜单下线（补齐入库版）
--
-- 背景：送货单改为订单的视图后，唯一入口 = 客户日总表页 /order/batch（矩阵/配货/点单三口径）。
--       「送货单据」页（/order/delivery）菜单下线，但**路由与组件保留**（历史单证只读查询用）。
--
-- 动作：sys_menu 2036「送货单据」visible 0→1（隐藏），不改 status、不删行，
--       权限 order:delivery:* 全部保留（页面仍可按 URL 直达、按钮权限仍生效）。
-- 幂等：UPDATE 天然幂等。执行前请备份（sql/db_bak/）。
-- =============================================================================

SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `visible`     = '1',
    `update_by`   = 'system',
    `update_time` = NOW(),
    `remark`      = CONCAT(IFNULL(`remark`, ''), ' [D-055 菜单下线：送货单=订单视图，入口改客户日总表 /order/batch]')
WHERE `menu_id` = 2036
  AND `visible` <> '1';

-- 自检：
-- SELECT menu_id, menu_name, path, visible, status FROM sys_menu WHERE menu_id IN (2036, 2116);
