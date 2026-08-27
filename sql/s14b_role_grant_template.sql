-- ============================================================
-- s14b 非 admin 角色授权模板（默认不自动执行，人工决策后使用）
-- ============================================================
-- 背景：s14 新增 10 个菜单/按钮（退货单 C+6F、送货作废 F、送货补生成 F、验收撤销 F）。
--       RuoYi 的 admin 角色走超管放行，无需授权；其余角色须在
--       sys_role_menu 建立绑定后才能看到菜单 / 通过 @PreAuthorize 校验。
--
-- 现状（2026-08-28 核对）：库中仅 admin / common 两角色，
--       common 仅绑框架系统管理菜单，无业务菜单——本模板暂无执行对象。
--       未来创建业务角色（如"文员""仓管"）后，按下面二选一授权。
--
-- 用法二选一：
--   A. UI 勾选：系统管理-角色管理 → 目标角色 → 菜单权限，
--      按本文末尾「勾选清单」勾选后保存。
--   B. SQL 执行：改下面的 @role_key 为目标角色的"权限字符"，然后整段执行。
--      幂等：可重复执行（WHERE NOT EXISTS 防重），不会产生重复绑定。
--      生效：RuoYi 登录态缓存权限，被授权角色需重新登录。
-- ============================================================

-- ↓↓↓ 改成目标角色的权限字符（sys_role.role_key），例如 'clerk' / 'warehouse' ↓↓↓
SET @role_key := 'CHANGE_ME';

SET @rid := (SELECT role_id FROM sys_role WHERE role_key = @role_key AND del_flag = '0' LIMIT 1);

-- ---------- 1. 送货单：作废 / 按客户补生成 按钮 ----------
-- 推荐授予对象：日常操作送货单的角色（已绑 order/delivery/index 菜单者）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @rid, m.menu_id
FROM sys_menu m
WHERE m.perms IN ('order:delivery:void', 'order:delivery:generateCustomer')
  AND @rid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @rid AND rm.menu_id = m.menu_id);

-- ---------- 2. 验收单：撤销 按钮 ----------
-- 推荐授予对象：负责验收纠错的角色（撤销会回写订单为已配送，属敏感操作，按需授予）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @rid, m.menu_id
FROM sys_menu m
WHERE m.perms = 'acceptance:revoke'
  AND @rid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @rid AND rm.menu_id = m.menu_id);

-- ---------- 3. 退货单：C 菜单 + 6 个按钮 + 所在目录 ----------
-- 推荐授予对象：与验收同岗的角色（退货单以验收单为单价来源）
-- 3.1 父目录（订单管理 M 目录，保证菜单树可达；未绑则补绑）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @rid, m.parent_id
FROM sys_menu m
WHERE m.component = 'order/return/index'
  AND @rid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @rid AND rm.menu_id = m.parent_id);

-- 3.2 退货单 C 菜单 + 全部 F 按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @rid, m.menu_id
FROM sys_menu m
WHERE (m.component = 'order/return/index'
    OR m.parent_id = (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE component = 'order/return/index') t))
  AND @rid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @rid AND rm.menu_id = m.menu_id);

-- ============================================================
-- UI 勾选清单（用法 A 对照，共 10 项）
-- ============================================================
-- 单据管理 > 送货单据：
--   [x] 送货单作废            (order:delivery:void)
--   [x] 送货单补生成          (order:delivery:generateCustomer)
-- 订单管理 > 验收单：
--   [x] 验收单撤销            (acceptance:revoke)
-- 订单管理 > 退货单（整棵勾）：
--   [x] 退货单                (return:list)
--   [x] 退货单查询/新增/修改/删除/提交/质检
--       (return:query / return:add / return:edit / return:remove / return:submit / return:inspect)
-- ============================================================
