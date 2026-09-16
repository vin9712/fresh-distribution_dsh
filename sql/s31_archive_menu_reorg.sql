-- =============================================================================
-- s31：单据管理目录重构 + 退货单模块退役
--
-- 业务定稿（2026-09-16）：
--   1) 「单据管理」(menu_id=5) 目录保留原名，集中存放视图性单据（销售订单/送货单据/采购管理）；
--   2) 目录内保留三个入口：
--        - 2030 销售订单（操作型，保留）
--        - 2116 客户日总表 → 更名「送货单据」（页面/组件/权限不变，即客户日报表视图）
--        - 2075 采购管理（由顶级移入本目录；前端路由 /purchase → /order/purchase）
--   3) 2036 旧「送货单据」页更名「送货单据(历史)」并保持隐藏（visible=1），
--      仅保留路由/组件供历史单证 URL 直达（D-055），避免与 2116 同名歧义；
--   4) 2106 退货单及其按钮/字典/表全部退役（无使用场景）。
--      注意：配送后退货「标记」change_type=3 仍保留（DeliveryChangeService），
--            退役的只是独立的退货单台账模块。
--   5) 2081 验收单取消隐藏：台账页（查看/打印；历史送货单维度可补建/维护）。
--
-- 幂等：UPDATE 绝对值赋值 / DELETE 条件删除 / DROP IF EXISTS，可重复执行。
-- ⚠ 执行前请备份（sql/db_bak/），参考 before_s31_*.sql.gz。
-- =============================================================================

SET NAMES utf8mb4;

-- ---------- 1. 目录名保持「单据管理」（仅调整子菜单） ----------
UPDATE `sys_menu`
SET `menu_name`   = '单据管理',
    `remark`      = '单据管理目录：销售订单 / 送货单据 / 采购管理（视图性单据集中查看与打印）',
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `menu_id` = 5;

-- 2. 2116 客户日总表 → 送货单据；按业务链路排序
--    ⚠ init_all 中 2116 为自增 id 插入（按 component 去重），新装库时 menu_id ≠ 2116，
--      故必须带 component 兜底，否则重命名/排序落空（2026-09-16 审查修复）
UPDATE `sys_menu`
SET `menu_name`   = '送货单据',
    `order_num`   = 2,
    `remark`      = '送货单据（客户日报表视图：矩阵/配货/点单三口径，打印与验收入口）',
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `menu_id` = 2116
   OR `component` = 'order/batch/view'; -- 兜底：init_all 中该菜单为自增 id 插入，新装库 menu_id≠2116

UPDATE `sys_menu` SET `order_num` = 1, `update_by` = 'system', `update_time` = NOW() WHERE `menu_id` = 2030; -- 销售订单
UPDATE `sys_menu` SET `order_num` = 3, `update_by` = 'system', `update_time` = NOW() WHERE `menu_id` = 2075; -- 采购管理（移入后）
UPDATE `sys_menu` SET `order_num` = 4, `update_by` = 'system', `update_time` = NOW() WHERE `menu_id` = 2081; -- 验收单（隐藏）
UPDATE `sys_menu` SET `order_num` = 5, `update_by` = 'system', `update_time` = NOW() WHERE `menu_id` = 2036; -- 送货单据(历史)（隐藏）

-- ---------- 3. 2036 旧送货单据页改名，避免与 2116 同名 ----------
UPDATE `sys_menu`
SET `menu_name`   = '送货单据(历史)',
    `visible`     = '1',
    `remark`      = CONCAT(IFNULL(`remark`, ''), ' [更名历史，仅路由/组件保留，URL 直达历史单证]'),
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `menu_id` = 2036;

-- ---------- 4. 采购管理由顶级移入「单据管理」目录 ----------
UPDATE `sys_menu`
SET `parent_id`   = 5,
    `order_num`   = 3,
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `menu_id` = 2075;
-- 兜底：按 component 定位（menu_id 变动时）
UPDATE `sys_menu`
SET `parent_id`   = 5,
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `component` = 'purchase/index' AND `menu_type` = 'C' AND `parent_id` <> 5;

-- ---------- 5. 退货单菜单 + 按钮退役（先清角色绑定，再删菜单） ----------
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (2106, 2107, 2108, 2109, 2110, 2111, 2112);

DELETE FROM `sys_menu`
WHERE `menu_id` IN (2106, 2107, 2108, 2109, 2110, 2111, 2112);

-- 兜底：按权限串/组件删除（menu_id 变动时）
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `menu_id` FROM `sys_menu` WHERE `perms` LIKE 'return:%' OR `component` = 'order/return/index');

DELETE FROM `sys_menu`
WHERE `perms` LIKE 'return:%' OR `component` = 'order/return/index';

-- ---------- 6. 退货质检结果字典退役 ----------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'return_quality_result';

-- ---------- 7. 退货单表退役（执行前已确认 0 行；DROP 前请确认备份） ----------
DROP TABLE IF EXISTS `t_return_item`;
DROP TABLE IF EXISTS `t_return_order`;

-- ---------- 8. 验收单台账上线（取消隐藏；订单维度只读 + 打印，历史送货单维度保留维护入口） ----------
--   订单维度写入口统一在订单明细页验收模式；历史维度（s29 设计）仍可补建/录入/提交/撤销/删除。
UPDATE `sys_menu`
SET `visible`     = '0',
    `status`      = '0',
    `order_num`   = 4,
    `remark`      = '验收单台账：查看/打印；订单验收在订单明细页「去验收」，历史送货单维度可补建/录入/提交/撤销',
    `update_by`   = 'system',
    `update_time` = NOW()
WHERE `menu_id` = 2081
   OR `component` = 'order/acceptance/index';

-- ⚠ 缓存提醒：字典直删 DB 后，后端 Redis 仍可能残留旧缓存键 —— 执行后请清理：
--   redis-cli DEL sys_dict:return_quality_result
--   （或重启后端并触发字典缓存刷新，否则 /system/dict/data/type/return_quality_result 仍会返回旧值）

-- =============================================================================
-- 自检：
-- SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status
--   FROM sys_menu WHERE parent_id IN (0,5) AND menu_type IN ('M','C') ORDER BY parent_id, order_num;
-- SELECT COUNT(*) AS return_menus FROM sys_menu WHERE perms LIKE 'return:%' OR component='order/return/index';
-- SELECT COUNT(*) AS return_dict FROM sys_dict_type WHERE dict_type='return_quality_result';
-- SHOW TABLES LIKE 't_return%';
-- =============================================================================
