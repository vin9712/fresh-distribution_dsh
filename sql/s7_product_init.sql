-- ============================================================
-- s7 商品初始化（生成产物，勿手改）：报价demo.csv → 商品库/商品信息/报价
-- 生成器：.dsh-e2e/gen-product-init.js（幂等，可重复执行）
-- 生成时间：2026/8/22 12:58:55  数据行：88
-- 分类映射：叶菜类50/根茎类51/瓜类→瓜果类52/菌菇类54/佐料类56；新建 葱蒜类110009、半成品类110010
-- SKU 编码：S00000003~S00000090（跳过测试数据 S00000001/2）
-- 报价单：BJ2026082200001（客户 10 丽宫，2026-08-22 ~ 2026-09-21，已发布）
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO t_product_category (name, parent_id, code, level, sort, is_deleted, create_by, create_time, remark)
SELECT '葱蒜类', 1, '110009', 2, 9, 0, 'admin', NOW(), '报价demo初始化'
WHERE NOT EXISTS (SELECT 1 FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0);

INSERT INTO t_product_category (name, parent_id, code, level, sort, is_deleted, create_by, create_time, remark)
SELECT '半成品类', 1, '110010', 2, 10, 0, 'admin', NOW(), '报价demo初始化'
WHERE NOT EXISTS (SELECT 1 FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0);

-- 商品库 SPU（品名去重：86 个）
INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '大白菜', 'DBC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '椰菜', 'YC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '奶白菜', 'NBC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '菜芯', 'CX', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '小白菜', 'XBC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '小唐菜', 'XTC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '生菜', 'SC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '油唛菜', 'YMC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '苋菜', 'XC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '芥菜', 'JC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '大豆芽', 'DDY', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '绿豆芽', 'LDY', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '菠菜', 'BC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '西生菜', 'XSC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '旱地水菜', 'HDSC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '西洋菜', 'XYC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '白菜芯', 'BCX', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '娃娃菜', 'WWC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (50, '芥兰', 'JL', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '凉瓜', 'LG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '青瓜', 'QG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '青皮冬瓜', 'QPDG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '南瓜', 'NG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '胜瓜', 'SG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '云南小瓜', 'YNXG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '节瓜仔', 'JGZ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '佛手瓜', 'FSG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '老黄瓜', 'LHG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (52, '杜阮凉瓜', 'DRLG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '冬菇', 'DG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '茶树菇', 'CSG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '鸡爪菇', 'JZG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '金针菇', 'JZG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '平菇', 'PG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '杏鲍菇', 'XBG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '海鲜菇', 'HXG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (54, '口蘑', 'KM', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '马蹄肉', 'MTR', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '新鲜玉米粒', 'XXYML', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '菠萝肉', 'BLR', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '蕃薯', 'FS', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '玉米', 'YM', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '小番薯', 'XFS', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), '魔芋豆腐', 'MYDF', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '豆角', 'DJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '蕃茄', 'FQ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '西兰花', 'XLH', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '菜花', 'CH', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '蒜芯', 'SX', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '脆肉莲藕', 'CRLO', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '元椒', 'YJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '西芹', 'XQ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '香芹', 'XQ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '唛头', 'MT', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '红萝卜', 'HLB', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '茄瓜', 'QG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '白萝卜', 'BLB', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '新鲜土豆', 'XXTD', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '淮山薯', 'HSS', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '粉芋头', 'FYT', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '芋仔', 'YZ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '粉葛', 'FG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '沙葛', 'SG', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '青尖椒', 'QJJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '红尖椒', 'HJJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '指天椒', 'ZTJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '细长红尖椒', 'XCHJJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '荷兰豆', 'HLD', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '粉肉莲藕', 'FRLO', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '细长青辣椒', 'XCQLJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '铁棍山药', 'TGSY', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '散花', 'SH', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (51, '螺丝椒', 'LSJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '红洋葱', 'HYC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '白洋葱', 'BYC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '大葱', 'DC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '大蒜', 'DS', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '韭黄', 'JH', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES ((SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), '韭菜', 'JC', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '芫茜', 'YQ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '葱肉', 'CR', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '姜肉', 'JR', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '蒜子肉', 'SZR', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '子姜', 'ZJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '沙姜', 'SJ', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

INSERT IGNORE INTO t_product_spu (category_id, name, mnemonic_code, saleable, sort, valid, is_deleted, create_by, create_time, remark)
VALUES (56, '紫苏', 'ZS', 1, 0, 1, 0, 'admin', NOW(), '报价demo初始化');

-- 商品信息 SKU（88 条）
INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000003', '大白菜', 'DBC', NULL, '斤', 1, '斤', 1.0000, 1.20, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '大白菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000004', '椰菜', 'YC', NULL, '斤', 1, '斤', 1.0000, 1.05, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '椰菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000005', '奶白菜', 'NBC', NULL, '斤', 1, '斤', 1.0000, 2.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '奶白菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000006', '菜芯', 'CX', NULL, '斤', 1, '斤', 1.0000, 2.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '菜芯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000007', '小白菜', 'XBC', NULL, '斤', 1, '斤', 1.0000, 1.80, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '小白菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000008', '小唐菜', 'XTC', NULL, '斤', 1, '斤', 1.0000, 1.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '小唐菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000009', '生菜', 'SC', NULL, '斤', 1, '斤', 1.0000, 2.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '生菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000010', '油唛菜', 'YMC', NULL, '斤', 1, '斤', 1.0000, 2.30, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '油唛菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000011', '苋菜', 'XC', NULL, '斤', 1, '斤', 1.0000, 2.45, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '苋菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000012', '芥菜', 'JC', NULL, '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '芥菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000013', '大豆芽', 'DDY', NULL, '斤', 1, '斤', 1.0000, 0.98, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '大豆芽' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000014', '绿豆芽', 'LDY', NULL, '斤', 1, '斤', 1.0000, 0.98, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '绿豆芽' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000015', '菠菜', 'BC', NULL, '斤', 1, '斤', 1.0000, 5.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '菠菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000016', '西生菜', 'XSC', NULL, '斤', 1, '斤', 1.0000, 3.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '西生菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000017', '旱地水菜', 'HDSC', NULL, '斤', 1, '斤', 1.0000, 1.90, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '旱地水菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000018', '西洋菜', 'XYC', NULL, '斤', 1, '斤', 1.0000, 5.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '西洋菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000019', '白菜芯', 'BCX', NULL, '斤', 1, '斤', 1.0000, 2.60, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '白菜芯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000020', '娃娃菜', 'WWC', NULL, '袋', 0, '袋', 1.0000, 2.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '娃娃菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 50, 'S00000021', '芥兰', 'JL', NULL, '斤', 1, '斤', 1.0000, 2.20, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 50 AND sp.name = '芥兰' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000022', '凉瓜', 'LG', NULL, '斤', 1, '斤', 1.0000, 1.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '凉瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000023', '青瓜', 'QG', NULL, '斤', 1, '斤', 1.0000, 1.28, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '青瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000024', '青皮冬瓜', 'QPDG', NULL, '斤', 1, '斤', 1.0000, 0.85, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '青皮冬瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000025', '南瓜', 'NG', NULL, '斤', 1, '斤', 1.0000, 0.85, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '南瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000026', '胜瓜', 'SG', NULL, '斤', 1, '斤', 1.0000, 1.70, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '胜瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000027', '云南小瓜', 'YNXG', NULL, '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '云南小瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000028', '节瓜仔', 'JGZ', NULL, '斤', 1, '斤', 1.0000, 1.15, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '节瓜仔' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000029', '佛手瓜', 'FSG', NULL, '斤', 1, '斤', 1.0000, 1.18, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '佛手瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000030', '老黄瓜', 'LHG', NULL, '斤', 1, '斤', 1.0000, 1.70, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '老黄瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 52, 'S00000031', '杜阮凉瓜', 'DRLG', NULL, '斤', 1, '斤', 1.0000, 2.58, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 52 AND sp.name = '杜阮凉瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000032', '冬菇', 'DG', NULL, '斤', 1, '斤', 1.0000, 4.65, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '冬菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000033', '茶树菇', 'CSG', NULL, '斤', 1, '斤', 1.0000, 5.90, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '茶树菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000034', '鸡爪菇', 'JZG', NULL, '斤', 1, '斤', 1.0000, 6.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '鸡爪菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000035', '金针菇', 'JZG', NULL, '斤', 1, '斤', 1.0000, 3.20, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '金针菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000036', '平菇', 'PG', NULL, '斤', 1, '斤', 1.0000, 5.30, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '平菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000037', '杏鲍菇', 'XBG', NULL, '斤', 1, '斤', 1.0000, 3.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '杏鲍菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000038', '海鲜菇', 'HXG', NULL, '斤', 1, '斤', 1.0000, 5.30, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '海鲜菇' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 54, 'S00000039', '口蘑', 'KM', NULL, '斤', 1, '斤', 1.0000, 11.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 54 AND sp.name = '口蘑' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000040', '马蹄肉', 'MTR', NULL, '斤', 1, '斤', 1.0000, 7.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '马蹄肉' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000041', '新鲜玉米粒', 'XXYML', NULL, '斤', 1, '斤', 1.0000, 3.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '新鲜玉米粒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000042', '菠萝肉', 'BLR', NULL, '斤', 1, '斤', 1.0000, 5.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '菠萝肉' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000043', '蕃薯', 'FS', NULL, '斤', 1, '斤', 1.0000, 1.98, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '蕃薯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000044', '玉米', 'YM', NULL, '斤', 1, '斤', 1.0000, 1.78, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '玉米' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000045', '小番薯', 'XFS', NULL, '斤', 1, '斤', 1.0000, 2.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '小番薯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0), 'S00000046', '魔芋豆腐', 'MYDF', NULL, '斤', 1, '斤', 1.0000, 1.20, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '半成品类' AND is_deleted = 0) AND sp.name = '魔芋豆腐' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000047', '豆角', 'DJ', NULL, '斤', 1, '斤', 1.0000, 2.68, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '豆角' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000048', '蕃茄', 'FQ', NULL, '斤', 1, '斤', 1.0000, 1.56, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '蕃茄' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000049', '西兰花', 'XLH', NULL, '斤', 1, '斤', 1.0000, 2.68, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '西兰花' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000050', '菜花', 'CH', NULL, '斤', 1, '斤', 1.0000, 1.70, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '菜花' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000051', '蒜芯', 'SX', NULL, '斤', 1, '斤', 1.0000, 2.85, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '蒜芯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000052', '脆肉莲藕', 'CRLO', NULL, '斤', 1, '斤', 1.0000, 3.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '脆肉莲藕' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000053', '元椒', 'YJ', NULL, '斤', 1, '斤', 1.0000, 2.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '元椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000054', '西芹', 'XQ', NULL, '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '西芹' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000055', '香芹', 'XQ', NULL, '斤', 1, '斤', 1.0000, 2.90, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '香芹' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000056', '唛头', 'MT', NULL, '斤', 1, '斤', 1.0000, 1.60, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '唛头' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000057', '红萝卜', 'HLB', NULL, '斤', 1, '斤', 1.0000, 1.23, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '红萝卜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000058', '茄瓜', 'QG', NULL, '斤', 1, '斤', 1.0000, 1.10, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '茄瓜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000059', '白萝卜', 'BLB', NULL, '斤', 1, '斤', 1.0000, 0.90, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '白萝卜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000060', '新鲜土豆', 'XXTD', '大', '斤', 1, '斤', 1.0000, 1.60, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '新鲜土豆' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000061', '新鲜土豆', 'XXTD', '100g以上', '斤', 1, '斤', 1.0000, 1.40, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '新鲜土豆' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000062', '淮山薯', 'HSS', NULL, '斤', 1, '斤', 1.0000, 2.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '淮山薯' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000063', '粉芋头', 'FYT', NULL, '斤', 1, '斤', 1.0000, 2.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '粉芋头' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000064', '芋仔', 'YZ', NULL, '斤', 1, '斤', 1.0000, 3.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '芋仔' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000065', '粉葛', 'FG', NULL, '斤', 1, '斤', 1.0000, 2.88, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '粉葛' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000066', '沙葛', 'SG', NULL, '斤', 1, '斤', 1.0000, 1.38, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '沙葛' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000067', '青尖椒', 'QJJ', '大', '斤', 1, '斤', 1.0000, 1.80, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '青尖椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000068', '青尖椒', 'QJJ', '中', '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '青尖椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000069', '红尖椒', 'HJJ', NULL, '斤', 1, '斤', 1.0000, 3.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '红尖椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000070', '指天椒', 'ZTJ', NULL, '斤', 1, '斤', 1.0000, 4.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '指天椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000071', '细长红尖椒', 'XCHJJ', NULL, '斤', 1, '斤', 1.0000, 4.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '细长红尖椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000072', '荷兰豆', 'HLD', NULL, '斤', 1, '斤', 1.0000, 7.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '荷兰豆' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000073', '粉肉莲藕', 'FRLO', NULL, '斤', 1, '斤', 1.0000, 3.20, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '粉肉莲藕' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000074', '细长青辣椒', 'XCQLJ', NULL, '斤', 1, '斤', 1.0000, 2.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '细长青辣椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000075', '铁棍山药', 'TGSY', '蔬菜', '斤', 1, '斤', 1.0000, 5.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '铁棍山药' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000076', '散花', 'SH', NULL, '斤', 1, '斤', 1.0000, 1.70, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '散花' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 51, 'S00000077', '螺丝椒', 'LSJ', NULL, '斤', 1, '斤', 1.0000, 3.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 51 AND sp.name = '螺丝椒' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000078', '红洋葱', 'HYC', NULL, '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '红洋葱' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000079', '白洋葱', 'BYC', NULL, '斤', 1, '斤', 1.0000, 1.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '白洋葱' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000080', '大葱', 'DC', NULL, '斤', 1, '斤', 1.0000, 2.58, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '大葱' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000081', '大蒜', 'DS', NULL, '斤', 1, '斤', 1.0000, 3.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '大蒜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000082', '韭黄', 'JH', NULL, '斤', 1, '斤', 1.0000, 8.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '韭黄' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0), 'S00000083', '韭菜', 'JC', NULL, '斤', 1, '斤', 1.0000, 2.30, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = (SELECT id FROM t_product_category WHERE name = '葱蒜类' AND is_deleted = 0) AND sp.name = '韭菜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000084', '芫茜', 'YQ', NULL, '斤', 1, '斤', 1.0000, 10.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '芫茜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000085', '葱肉', 'CR', NULL, '斤', 1, '斤', 1.0000, 4.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '葱肉' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000086', '姜肉', 'JR', NULL, '斤', 1, '斤', 1.0000, 5.30, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '姜肉' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000087', '蒜子肉', 'SZR', NULL, '斤', 1, '斤', 1.0000, 2.90, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '蒜子肉' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000088', '子姜', 'ZJ', NULL, '斤', 1, '斤', 1.0000, 4.50, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '子姜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000089', '沙姜', 'SJ', NULL, '斤', 1, '斤', 1.0000, 11.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '沙姜' AND sp.is_deleted = 0;

INSERT IGNORE INTO t_product_sku (spu_id, category_id, code, name, mnemonic_code, spec_name, unit, is_weighted, base_unit, conversion_rate, sale_price, saleable, valid, is_deleted, create_by, create_time, remark)
SELECT sp.id, 56, 'S00000090', '紫苏', 'ZS', NULL, '斤', 1, '斤', 1.0000, 4.00, 1, 1, 0, 'admin', NOW(), '报价demo初始化'
FROM t_product_spu sp WHERE sp.category_id = 56 AND sp.name = '紫苏' AND sp.is_deleted = 0;

-- 报价单头（BJ2026082200001，客户 10，已发布）
INSERT INTO t_product_sku_quote (customer_id, code, effective_start_date, effective_end_date, status, valid, is_deleted, version, create_by, create_time, remark)
SELECT 10, 'BJ2026082200001', '2026-08-22 00:00:00', '2026-09-21 23:59:59', 1, 1, 0, 0, 'admin', NOW(), '报价demo初始化'
WHERE NOT EXISTS (SELECT 1 FROM t_product_sku_quote WHERE code = 'BJ2026082200001');

-- 报价明细（88 条）
INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.20, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000003'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.05, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000004'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000005'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000006'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.80, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000007'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000008'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000009'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.30, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000010'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.45, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000011'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000012'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 0.98, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000013'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 0.98, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000014'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000015'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000016'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.90, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000017'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000018'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.60, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000019'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000020'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.20, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000021'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000022'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.28, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000023'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 0.85, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000024'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 0.85, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000025'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.70, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000026'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000027'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.15, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000028'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.18, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000029'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.70, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000030'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.58, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000031'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.65, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000032'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.90, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000033'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 6.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000034'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.20, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000035'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.30, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000036'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000037'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.30, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000038'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 11.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000039'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 7.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000040'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000041'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000042'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.98, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000043'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.78, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000044'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000045'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.20, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000046'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.68, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000047'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.56, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000048'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.68, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000049'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.70, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000050'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.85, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000051'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000052'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000053'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000054'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.90, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000055'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.60, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000056'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.23, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000057'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.10, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000058'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 0.90, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000059'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, '大', 1.60, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000060'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, '100g以上', 1.40, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000061'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000062'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000063'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000064'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.88, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000065'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.38, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000066'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, '大', 1.80, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000067'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, '中', 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000068'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000069'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000070'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000071'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 7.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000072'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.20, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000073'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000074'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, '蔬菜', 5.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000075'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.70, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000076'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000077'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000078'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 1.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000079'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.58, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000080'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 3.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000081'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 8.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000082'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.30, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000083'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 10.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000084'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000085'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 5.30, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000086'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 2.90, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000087'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.50, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000088'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 11.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000089'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

INSERT INTO t_product_sku_quote_detail (customer_id, quote_id, sku_id, product_name, product_unit, product_spec, price, valid, is_deleted, version, create_by, create_time)
SELECT 10, qz.id, s.id, s.name, s.unit, NULL, 4.00, 1, 0, 0, 'admin', NOW()
FROM t_product_sku_quote qz JOIN t_product_sku s ON s.code = 'S00000090'
WHERE qz.code = 'BJ2026082200001'
AND NOT EXISTS (SELECT 1 FROM t_product_sku_quote_detail d WHERE d.quote_id = qz.id AND d.sku_id = s.id);

-- 客户商品池（丽宫 10 × 88）
INSERT IGNORE INTO customers_sku (customer_id, sku_id, alias, customer_code, unit, min_order_qty, order_step, is_follow_default, source_template_id, status, created_at, updated_at)
VALUES (10, (SELECT id FROM t_product_sku WHERE code = 'S00000003'), NULL, 'C10000003', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000004'), NULL, 'C10000004', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000005'), NULL, 'C10000005', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000006'), NULL, 'C10000006', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000007'), NULL, 'C10000007', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000008'), NULL, 'C10000008', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000009'), NULL, 'C10000009', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000010'), NULL, 'C10000010', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000011'), NULL, 'C10000011', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000012'), NULL, 'C10000012', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000013'), NULL, 'C10000013', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000014'), NULL, 'C10000014', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000015'), NULL, 'C10000015', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000016'), NULL, 'C10000016', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000017'), NULL, 'C10000017', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000018'), NULL, 'C10000018', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000019'), NULL, 'C10000019', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000020'), NULL, 'C10000020', '袋', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000021'), NULL, 'C10000021', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000022'), NULL, 'C10000022', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000023'), NULL, 'C10000023', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000024'), NULL, 'C10000024', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000025'), NULL, 'C10000025', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000026'), NULL, 'C10000026', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000027'), NULL, 'C10000027', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000028'), NULL, 'C10000028', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000029'), NULL, 'C10000029', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000030'), NULL, 'C10000030', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000031'), NULL, 'C10000031', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000032'), NULL, 'C10000032', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000033'), NULL, 'C10000033', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000034'), NULL, 'C10000034', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000035'), NULL, 'C10000035', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000036'), NULL, 'C10000036', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000037'), NULL, 'C10000037', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000038'), NULL, 'C10000038', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000039'), NULL, 'C10000039', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000040'), NULL, 'C10000040', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000041'), NULL, 'C10000041', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000042'), NULL, 'C10000042', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000043'), NULL, 'C10000043', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000044'), NULL, 'C10000044', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000045'), NULL, 'C10000045', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000046'), NULL, 'C10000046', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000047'), NULL, 'C10000047', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000048'), NULL, 'C10000048', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000049'), NULL, 'C10000049', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000050'), NULL, 'C10000050', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000051'), NULL, 'C10000051', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000052'), NULL, 'C10000052', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000053'), NULL, 'C10000053', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000054'), NULL, 'C10000054', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000055'), NULL, 'C10000055', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000056'), NULL, 'C10000056', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000057'), NULL, 'C10000057', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000058'), NULL, 'C10000058', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000059'), NULL, 'C10000059', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000060'), NULL, 'C10000060', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000061'), NULL, 'C10000061', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000062'), NULL, 'C10000062', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000063'), NULL, 'C10000063', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000064'), NULL, 'C10000064', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000065'), NULL, 'C10000065', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000066'), NULL, 'C10000066', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000067'), NULL, 'C10000067', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000068'), NULL, 'C10000068', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000069'), NULL, 'C10000069', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000070'), NULL, 'C10000070', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000071'), NULL, 'C10000071', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000072'), NULL, 'C10000072', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000073'), NULL, 'C10000073', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000074'), NULL, 'C10000074', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000075'), NULL, 'C10000075', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000076'), NULL, 'C10000076', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000077'), NULL, 'C10000077', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000078'), NULL, 'C10000078', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000079'), NULL, 'C10000079', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000080'), NULL, 'C10000080', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000081'), NULL, 'C10000081', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000082'), NULL, 'C10000082', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000083'), NULL, 'C10000083', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000084'), NULL, 'C10000084', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000085'), NULL, 'C10000085', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000086'), NULL, 'C10000086', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000087'), NULL, 'C10000087', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000088'), NULL, 'C10000088', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000089'), NULL, 'C10000089', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW()),
(10, (SELECT id FROM t_product_sku WHERE code = 'S00000090'), NULL, 'C10000090', '斤', 1.00, 1.00, 0, NULL, 1, NOW(), NOW());

-- 编号序列衔接
INSERT INTO biz_code_seq (biz_key, seq) VALUES ('sku_code', 90)
ON DUPLICATE KEY UPDATE seq = GREATEST(seq, 90);
INSERT INTO biz_code_seq (biz_key, seq) VALUES ('skuQuote:20260822', 1)
ON DUPLICATE KEY UPDATE seq = GREATEST(seq, 1);
INSERT INTO biz_code_seq (biz_key, seq) VALUES ('customer_sku_code:10', 90)
ON DUPLICATE KEY UPDATE seq = GREATEST(seq, 90);
