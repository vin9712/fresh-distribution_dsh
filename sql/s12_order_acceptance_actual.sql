-- ============================================================
-- S12：订单页实收与验收 + 录单页后端草稿
-- 依据：
--   docs/01-design/订单页实收与验收交互设计.md §4.5 开发清单 1/2
--   docs/01-design/录单页交互细化设计.md   §四 开发清单 1
-- 内容：
--   1) t_sale_order_detail 增加 loss_reason 列（实收<下单数必填，字典 biz_loss_reason）
--   2) 新建录单草稿表 t_sale_order_draft（localStorage + 后端双写，后端为主）
--   3) 损耗原因字典 biz_loss_reason 初始化（挤压/破损/拒收/质量不达标/少送）
--   4) 差异提醒阈值参数 order.accept.diff.threshold（默认 20%，仅提示非阻断）
-- 均为幂等写法，可重复执行
-- ============================================================

-- ---------- 1. 订单明细增加损耗原因列 ----------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'loss_reason');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `t_sale_order_detail` ADD COLUMN `loss_reason` varchar(64) DEFAULT NULL COMMENT ''损耗原因（biz_loss_reason，实收<下单数必填）'' AFTER `expect_amount`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 实收三列改为可空：撤销验收(3→2)需清空 actual_*（部分存量库为 NOT NULL DEFAULT 0）
SET @col_nullable = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'actual_price' AND IS_NULLABLE = 'NO');
SET @ddl = IF(@col_nullable > 0,
  'ALTER TABLE `t_sale_order_detail` MODIFY COLUMN `actual_price` decimal(10,2) DEFAULT NULL COMMENT ''验收商品单价''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = NULL;

SET @col_nullable = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'actual_num' AND IS_NULLABLE = 'NO');
SET @ddl = IF(@col_nullable > 0,
  'ALTER TABLE `t_sale_order_detail` MODIFY COLUMN `actual_num` decimal(10,2) DEFAULT NULL COMMENT ''验收数量''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = NULL;

SET @col_nullable = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_sale_order_detail'
    AND COLUMN_NAME = 'actual_amount' AND IS_NULLABLE = 'NO');
SET @ddl = IF(@col_nullable > 0,
  'ALTER TABLE `t_sale_order_detail` MODIFY COLUMN `actual_amount` decimal(10,2) DEFAULT NULL COMMENT ''验收总金额''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = NULL;

-- ---------- 2. 录单草稿表（后端为主、本地兜底断网场景） ----------
CREATE TABLE IF NOT EXISTS `t_sale_order_draft` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `draft_key`   varchar(64)  NOT NULL COMMENT '草稿键：new:{customerDeptId} / order:{orderId}',
  `user_id`     bigint       NOT NULL DEFAULT 0 COMMENT '用户ID',
  `payload`     mediumtext   NOT NULL COMMENT '草稿载荷 JSON（表头+明细+savedAt）',
  `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime     DEFAULT NULL COMMENT '更新时间（恢复时新旧比较依据）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_key` (`draft_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单录入草稿表';

-- ---------- 3. 损耗原因字典 biz_loss_reason ----------
INSERT INTO `sys_dict_type`
(`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '损耗原因', 'biz_loss_reason', '0', 'admin', sysdate(), '订单验收实收差异（损耗）原因'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'biz_loss_reason');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 1, '挤压', 'squeeze', 'biz_loss_reason', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_loss_reason' AND `dict_value` = 'squeeze');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2, '破损', 'broken', 'biz_loss_reason', '', 'danger', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_loss_reason' AND `dict_value` = 'broken');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 3, '拒收', 'rejected', 'biz_loss_reason', '', 'info', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_loss_reason' AND `dict_value` = 'rejected');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4, '质量不达标', 'quality', 'biz_loss_reason', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_loss_reason' AND `dict_value` = 'quality');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 5, '少送', 'shortage', 'biz_loss_reason', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'biz_loss_reason' AND `dict_value` = 'shortage');

-- ---------- 4. 差异提醒阈值参数（±20% 仅提示非阻断） ----------
INSERT INTO `sys_config`
(`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '订单验收差异提醒阈值(%)', 'order.accept.diff.threshold', '20', 'Y', 'admin', sysdate(), '实收与下单数相差超过该百分比时提示，仅提示不阻断'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'order.accept.diff.threshold');

-- ---------- 5. 验收单菜单下线（§4.5-6：先停用，观察一个调价周期后再删代码） ----------
-- 验收已改为在销售订单上直接完成，独立「验收单」页停用（visible='1' 隐藏 + status='1' 停用）
UPDATE `sys_menu`
SET `visible` = '1', `status` = '1', `remark` = '已下线：验收改在销售订单页完成（见 s12）'
WHERE `menu_id` BETWEEN 2081 AND 2086;
