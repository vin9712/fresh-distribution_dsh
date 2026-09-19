-- =============================================================================
-- s40：（可选，默认不执行）t_print_template 死字段收敛
--
-- ⚠️ 本脚本为**破坏性 DDL**，默认全部注释。仅在确认后按步骤执行，且需同步改代码。
--    P5 评估结论：收益有限（仅去掉恒值列），风险与代码耦合高，故缓行。
--
-- 现状：
--   · data          JSON NOT NULL  —— 恒 '{}'，mapper 插入时硬编码；查询不 select（纯写死值）
--   · render_engine varchar(32) NOT NULL DEFAULT 'jimureport' —— 恒 'jimureport'（无多引擎）
--   · type          tinyint(1) NOT NULL DEFAULT 0 —— 0送货单/1汇总表；运行期未使用
--   · is_default    char(1) —— **不是死字段**：selectBindTemplate 依赖 bind_type=3 AND is_default='1'，保留
--
-- 执行前提（缺一不可）：
--   1) 代码：PrintTemplateMapper.xml 删除 insert 中的 `data, '{}'`；
--            删除 selectPrintTemplateVo / resultMap 中的 render_engine、type；
--            PrintTemplate 实体移除 data/renderEngine/type（或保留但不再读写）；
--            PrintTemplateServiceImpl.exportTemplate/importTemplates 移除 renderEngine/type 字段。
--   2) 前端：打印模板表单移除「类型」单选（RuoYi-Vue3/src/views/print/template/index.vue）。
--   3) 备份：mysqldump t_print_template（sql/db_bak/）。
--   4) 全量构建 + 重启后验证：新增/修改/发布/导出/导入各一次。
--
-- 步骤（确认后逐条取消注释执行）：
-- =============================================================================

SET NAMES utf8mb4;

-- 步骤 1：给 data 一个默认值（MySQL 8.0.13+ 支持 JSON 表达式默认），使 mapper 不再需要硬编码
-- ALTER TABLE `t_print_template`
--   MODIFY COLUMN `data` JSON NOT NULL DEFAULT (JSON_OBJECT())
--   COMMENT '打印测试数据（恒空对象，保留兼容）';

-- 步骤 2：确认代码已不再读写 render_engine / type 后，删除列
-- ALTER TABLE `t_print_template` DROP COLUMN `render_engine`;
-- ALTER TABLE `t_print_template` DROP COLUMN `type`;

-- 步骤 3：确认 data 已无任何读取后，删除列
-- ALTER TABLE `t_print_template` DROP COLUMN `data`;

-- 自检：
-- SELECT column_name, column_type, is_nullable, column_default
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name = 't_print_template'
--  ORDER BY ordinal_position;
