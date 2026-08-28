-- =====================================================================
-- W0-2.5 已确认采购单直接调整：新增调整审计日志表 purchase_modify_log
-- 用途：已确认采购单被直接修改数量/成本时，记录操作日志 + 前后金额与明细快照（审计/报表重算依据）。
-- 幂等：CREATE TABLE IF NOT EXISTS；存量库直接执行；init_all.sql [建表节] 已同步本表。
-- 回滚参考：DROP TABLE IF EXISTS `purchase_modify_log`;
-- =====================================================================

CREATE TABLE IF NOT EXISTS `purchase_modify_log` (
  `id`            bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_id`   bigint(20)    NOT NULL COMMENT '采购单ID',
  `purchase_code` varchar(32)   NOT NULL COMMENT '采购单号（PCyyyyMMddNNN）',
  `before_amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '调整前采购总额',
  `after_amount`  decimal(12,2) NOT NULL DEFAULT 0 COMMENT '调整后采购总额',
  `before_items`  text          COMMENT '调整前明细快照(JSON)',
  `after_items`   text          COMMENT '调整后明细快照(JSON)',
  `operator`      varchar(64)   DEFAULT '' COMMENT '操作人',
  `operate_time`  datetime      DEFAULT NULL COMMENT '操作时间',
  `remark`        varchar(500)  DEFAULT NULL COMMENT '调整说明',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_id` (`purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已确认采购单调整审计日志(W0-2.5)';
