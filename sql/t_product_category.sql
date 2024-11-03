/*
 Navicat Premium Data Transfer

 Source Server         : 本机
 Source Server Type    : MySQL
 Source Server Version : 50728
 Source Host           : localhost:3306
 Source Schema         : fresh-distribution

 Target Server Type    : MySQL
 Target Server Version : 50728
 File Encoding         : 65001

 Date: 03/11/2024 23:57:43
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_product_category
-- ----------------------------
DROP TABLE IF EXISTS `t_product_category`;
CREATE TABLE `t_product_category`  (
  `id` bigint(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(200) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint(10) UNSIGNED NULL DEFAULT NULL COMMENT '上级分类ID',
  `code` char(10) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '分类编号',
  `level` tinyint(2) NOT NULL COMMENT '分类级别(1级最大)',
  `sort` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '分类排序',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_sort`(`sort`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 114 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '商品分类表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_product_category
-- ----------------------------
INSERT INTO `t_product_category` VALUES (1, '蔬菜类', 0, '110000', 1, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (2, '肉蛋类', 0, '120000', 1, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (3, '冻品类', 0, '130000', 1, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (4, '干货类', 0, '140000', 1, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (5, '粮油类', 0, '150000', 1, 5, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (6, '调料类', 0, '160000', 1, 6, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (7, '熟食', 0, '170000', 1, 7, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (8, '水果类', 0, '180000', 1, 8, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (9, '易耗品', 0, '190000', 1, 9, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (10, '其他', 0, '200000', 1, 10, 0, '', '2024-11-03 10:07:54', '', '2024-11-03 16:18:06', '11');
INSERT INTO `t_product_category` VALUES (50, '叶菜类', 1, '110001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (51, '根茎类', 1, '110002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (52, '瓜果类', 1, '110003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (53, '豆类', 1, '110004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (54, '菌菇类', 1, '110005', 2, 5, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (55, '豆制品', 1, '110006', 2, 6, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (56, '佐料类', 1, '110007', 2, 7, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (57, '咸菜类', 1, '110008', 2, 8, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (60, '猪肉类', 2, '120001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (61, '牛肉类', 2, '120002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (62, '三鸟类', 2, '120003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (63, '水产类', 2, '120004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (64, '蛋类', 2, '120005', 2, 5, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (70, '鸡副类', 3, '130001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (71, '鸭副类', 3, '130002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (72, '猪类', 3, '130003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (73, '牛类', 3, '130004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (74, '冰鲜水产类', 3, '130005', 2, 5, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (75, '饺子类', 3, '130006', 2, 6, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (76, '其他类', 3, '130007', 2, 7, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (80, '副食类', 4, '140001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (81, '香料类', 4, '140002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (82, '五谷类', 4, '140003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (83, '杂项类', 4, '140004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (90, '米类', 5, '150001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (91, '面类', 5, '150002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (92, '油类', 5, '150003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (93, '淀粉类', 5, '150004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (100, '一次性用品', 9, '190001', 2, 1, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (101, '清洁工具', 9, '190002', 2, 2, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (102, '洗涤用品', 9, '190003', 2, 3, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (103, '一次性餐具', 9, '190004', 2, 4, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (104, '生产用品', 9, '190005', 2, 5, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (105, '其他用品', 9, '190006', 2, 6, 0, '', '2024-11-03 10:07:54', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (106, '3', 10, '200001', 2, 3, 0, '', '2024-11-03 16:18:15', '', '2024-11-03 16:19:19', '113');
INSERT INTO `t_product_category` VALUES (107, '33', 10, '200002', 2, 2, 0, '', '2024-11-03 16:19:40', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (108, 'w', 0, '210000', 1, 11, 0, '', '2024-11-03 21:47:56', '', '2024-11-03 22:04:57', 'd');
INSERT INTO `t_product_category` VALUES (109, 'w1', 108, '210001', 2, 1, 0, '', '2024-11-03 22:05:02', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (110, 'ww1', 109, '210002', 3, 1, 0, '', '2024-11-03 22:05:08', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (112, 'ww2', 109, '210003', 3, 2, 0, '', '2024-11-03 22:53:33', '', NULL, NULL);
INSERT INTO `t_product_category` VALUES (113, 'ww3', 109, '210004', 3, 3, 0, '', '2024-11-03 22:53:47', '', NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
