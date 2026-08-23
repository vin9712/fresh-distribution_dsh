-- ============================================================
-- fresh-distribution_dsh 数据库一体化初始化脚本
-- 文件: init_all.sql
-- 说明: 由 sql/ 目录下各分脚本按依赖顺序合并而成，一次执行完成库结构 + 基础数据初始化
-- ============================================================
--
-- 【用途】一次性初始化业务库（RuoYi 框架 + Quartz + 业务表 + 菜单/字典 + 增量迁移）。
--
-- 【执行方式】
--   1) 先建库并连接（库名自定，下例用 fresh-distribution-dsh）：
--        CREATE DATABASE IF NOT EXISTS `fresh-distribution-dsh` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
--        USE `fresh-distribution-dsh`;
--   2) 导入本脚本：
--        mysql -uroot -p fresh-distribution-dsh < sql/init_all.sql
--      或在 Navicat 中选中目标库后整体运行。
--
-- 【包含的源文件（按执行顺序）】
--   01 ry_20240629.sql                 RuoYi 框架基线（sys_*、菜单 1~1060、字典、角色-菜单绑定）
--   02 quartz.sql                      Quartz 调度表 QRTZ_*
--   03 init_fresh_distribution.sql     业务表 DDL（t_product_*、t_customer*、t_sale_order*、t_delivery*、t_print_*）
--   04 t_product_category.sql          商品分类数据（DROP+CREATE+INSERT 种子）
--   05 new_added_sql.sql               业务菜单 2000~2047 + 业务字典 dict_type 100~107
--   06 s0_1_biz_code_seq_and_dicts.sql 通用业务序列表 + 业务字典数据 + 采购单状态字典(108)
--   07 s0_2_table_baseline.sql          增量表基线（temp_product/alias/mapping/price/adjustment/purchase/acceptance + 字段改造）
--   08 s1_2_alias_mapping_menu.sql     菜单/权限：别名与映射
--   09 s1_3_import_menu.sql            菜单/权限：导入按钮
--   10 s2_2_price_menu.sql             菜单/权限：报价模板、配送点报价
--   11 s2_2_price_template_customer.sql 报价模板-客户绑定表
--   12 s3_1_order_status_menu.sql      菜单/权限：订单撤回、月结
--   13 s3_2_workbench_menu.sql         菜单/权限：工作台
--   14 s3_4_adjustment_menu.sql        菜单/权限：订单调整
--   15 s4_purchase_menu.sql            菜单/权限：采购管理
--   16 s5_1_delivery_alter.sql        送货单表结构适配（配送点维度、商品合并）
--   17 s5_1_delivery_menu.sql         菜单/权限：送货单打印、送达
--   18 s5_2_acceptance_menu.sql        菜单/权限：验收 + 验收状态字典
--   19 s6_1_report_menu.sql            菜单/权限：报表中心
--
-- 【已跳过（不纳入本脚本）】
--   - s0_3_jimureport_init.sql         JimuReport 报表引擎初始化（依赖按需单独导入）
--   - s6_2_print_seed.sql              JimuReport 送货单打印模板种子（依赖 jimu_* 表，按需单独导入）
--   - db_bak/*.nb3                     数据库备份文件
--
-- 【注意事项】
--   1) new_added_sql.sql 中原写死的 `fresh-distribution-dsh` 库名限定已统一去除，
--      全部按当前连接库执行；库名不同时无需改动即可在任意目标库运行。
--   2) 业务菜单(menu_id 2000+)在各源脚本中仅 INSERT sys_menu，未做 sys_role_menu 角色绑定；
--      初始化后请到「系统管理-菜单」给对应角色分配这些菜单，或自行补 role_menu 关系。
--   3) 多数菜单/字典 INSERT 为非幂等（首次初始化执行一次即可；重复执行会触发主键冲突）；
--      s0_1/s0_2/s6_2 内含 INSERT IGNORE / IF NOT EXISTS 的部分可重复执行，但整体不保证幂等。
--   4) 本脚本含 DROP TABLE / ALTER，会修改库结构；执行前请确认目标库为空或数据可覆盖。
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- [01] RuoYi 框架基线（部门/用户/角色/菜单/字典/角色-菜单绑定）  | 源: ry_20240629.sql
-- ============================================================

-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '帐号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), 'admin', sysdate(), '', null, '测试员');


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '97', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '98', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '99', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '基础信息', '0', '1', 'basicInfo', null, '', '', 1, 0, 'M', '0', '0', '', 'star',    'admin', sysdate(), '', null, '基础设施目录');
insert into sys_menu values('5', '单据管理', '0', '2', 'order', null, '', '', 1, 0, 'M', '0', '0', '', 'build',    'admin', sysdate(), '', null, '单据管理目录');
insert into sys_menu values('6', '打印管理', '0', '3', 'print', null, '', '', 1, 0, 'M', '0', '0', '', 'table',    'admin', sysdate(), '', null, '打印管理目录');

-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',            'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',         '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',           'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',    'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',      'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',         '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 19、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';
-- ============================================================
-- [02] Quartz 调度表  | 源: quartz.sql
-- ============================================================

DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;
-- ============================================================
-- [03] 业务表 DDL（t_product_* / t_customer* / t_sale_order* / t_delivery* / t_print_*）  | 源: init_fresh_distribution.sql
-- ============================================================

-- 商品分类表
CREATE TABLE `t_product_category`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '分类名称',
    `parent_id`   bigint(10) unsigned DEFAULT NULL COMMENT '上级分类ID',
    `code`        char(10)              DEFAULT NULL COMMENT '分类编号',
    `level`       tinyint(2) NOT NULL COMMENT '分类级别(1级最大)',
    `sort`        int(10) unsigned NOT NULL DEFAULT '0' COMMENT '分类排序',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_name` (`name`),
    KEY           `idx_parent_id` (`parent_id`) USING BTREE,
    KEY           `idx_sort` (`sort`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品分类表';

-- 商品spu表
CREATE TABLE `t_product_spu`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id`   bigint(10) unsigned NOT NULL COMMENT '分类ID',
    `name`          varchar(200) NOT NULL COMMENT '商品名称',
    `description`   varchar(200)          DEFAULT NULL COMMENT '商品描述',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `images`        json                  DEFAULT NULL COMMENT '商品图片',
    `saleable`      tinyint(1) NOT NULL COMMENT '是否上架',
    `sort`          int(10) unsigned NOT NULL DEFAULT '0' COMMENT '商品排序',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `idx_category_id_name` (`category_id`, `name`),
    KEY             `idx_mnemonic_code` (`mnemonic_code`) USING BTREE,
    KEY             `idx_remark` (`remark`) USING BTREE,
    KEY             `idx_saleable` (`saleable`) USING BTREE,
    KEY             `idx_valid` (`valid`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品spu表';

-- 客户表
CREATE TABLE `t_customer`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '客户名称',
    `alias`       varchar(200)          DEFAULT NULL COMMENT '客户别名',
    `type`        varchar(10)  NOT NULL COMMENT '客户类型',
    `tel`         char(11)              DEFAULT NULL COMMENT '手机号',
    `address`     varchar(200)          DEFAULT NULL COMMENT '客户地址',
    `valid`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_name` (`name`) USING BTREE,
    KEY           `idx_alias` (`alias`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='客户表';

-- 客户部门表
CREATE TABLE `t_customer_dept`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `parent_id`     bigint(10) unsigned NOT NULL COMMENT '上级部门ID',
    `code`          varchar(200) NOT NULL COMMENT '部门编号',
    `name`          varchar(200) NOT NULL COMMENT '部门名称',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `address`       varchar(200)          DEFAULT NULL COMMENT '客户配送地址',
    `location`      GEOMETRY              DEFAULT NULL COMMENT '位置坐标',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_code` (`code`) USING BTREE,
    KEY             `idx_name` (`name`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='客户部门表';

-- 供应商表
CREATE TABLE `t_supplier`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(200) NOT NULL COMMENT '供应商名称',
    `alias`       varchar(200) NOT NULL COMMENT '供应商别名',
    `tel`         char(11)              DEFAULT NULL COMMENT '手机号',
    `valid`       tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `unq_name` (`name`) USING BTREE,
    KEY           `idx_alias` (`alias`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='供应商表';

-- 供应商明细表
CREATE TABLE `t_supplier_detail`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_id` bigint(10) unsigned NOT NULL COMMENT '供应商ID',
    `spu_id`      bigint(10) unsigned NOT NULL COMMENT '产品ID',
    `sku_id`      bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)        DEFAULT '' COMMENT '创建者',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)        DEFAULT '' COMMENT '更新者',
    `update_time` datetime           DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)       DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY           `idx_spu_id` (`spu_id`) USING BTREE,
    KEY           `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='供应商明细表';

-- 商品sku表
CREATE TABLE `t_product_sku`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `category_id`   bigint(10) unsigned NOT NULL COMMENT '分类ID',
    `spu_id`        bigint(10) DEFAULT NULL COMMENT '产品ID(未匹配可为空)',
    `code`          varchar(200) NOT NULL COMMENT '商品编号',
    `name`          varchar(200) NOT NULL COMMENT '商品名称',
    `mnemonic_code` varchar(128) NOT NULL COMMENT '助记码',
    `unit`          varchar(20)  NOT NULL COMMENT '商品单位',
    `spec`          varchar(200)          DEFAULT NULL COMMENT '商品规格',
    `images`        json                  DEFAULT NULL COMMENT '商品图片',
    `properties`    json                  DEFAULT NULL COMMENT '商品参数',
    `sale_price`    decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品售价',
    `visit_count`   bigint(10) unsigned DEFAULT NULL COMMENT '下单次数',
    `saleable`      tinyint(1) NOT NULL COMMENT '是否上架',
    `valid`         tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '商品备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY             `idx_spu_id` (`spu_id`) USING BTREE,
    KEY             `idx_customer_id_category_id` (`customer_id`, `category_id`) USING BTREE,
    UNIQUE KEY `idx_customer_id_name_unit` (`customer_id`, `name`, `unit`) USING BTREE,
    KEY             `idx_saleable` (`saleable`) USING BTREE,
    KEY             `idx_valid` (`valid`) USING BTREE,
    KEY             `idx_code` (`code`) USING BTREE,
    KEY             `idx_remark` (`remark`) USING BTREE,
    FULLTEXT KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品sku表';

-- 商品报价表
CREATE TABLE `t_product_sku_quote`
(
    `id`                   bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`          bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `code`                 varchar(200) NOT NULL COMMENT '商品报价编号',
    `effective_start_date` datetime     NOT NULL COMMENT '报价生效时间',
    `effective_end_date`   datetime     NOT NULL COMMENT '报价结束时间',
    `status`               int(4) NOT NULL DEFAULT '0' COMMENT '报价状态: 0-新增, 1-发布, 2-撤销, 3-失效',
    `valid`                tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`           tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`              int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`            varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`            varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`          datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`               varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY                    `idx_customer_id` (`customer_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价表';

-- 商品报价明细表
CREATE TABLE `t_product_sku_quote_detail`
(
    `id`           bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`  bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `quote_id`     bigint(10) unsigned NOT NULL COMMENT '商品报价ID',
    `sku_id`       bigint(10) unsigned NOT NULL COMMENT '商品ID',
    `product_name` varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit` varchar(20)  NOT NULL COMMENT '商品单位',
    `product_spec` varchar(200)          DEFAULT NULL COMMENT '商品参数',
    `price`        decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品报价',
    `valid`        tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`   tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`      int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`    varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`  datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY            `idx_customer_id` (`customer_id`) USING BTREE,
    KEY            `idx_quote_id` (`quote_id`) USING BTREE,
    KEY            `idx_sku_id` (`sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='商品报价明细表';

-- 销售订单表
CREATE TABLE `t_sale_order`
(
    `id`               bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`      bigint(10) unsigned DEFAULT NULL COMMENT '客户ID',
    `customer_dept_id` bigint(10) unsigned DEFAULT NULL COMMENT '客户部门ID',
    `code`             varchar(200) NOT NULL COMMENT '订单编号',
    `source`           tinyint(3) unsigned NOT NULL COMMENT '订单来源：1后台下单,2线上下单',
    `type`             tinyint(3) unsigned NOT NULL COMMENT '订单类型：1正常订单,2加单',
    `amount`           decimal(10, 2) unsigned NOT NULL COMMENT '总金额',
    `status`           tinyint(3) unsigned NOT NULL COMMENT '状态：0制单,1审核,2送货,3验收,4完成',
    `delivery_date`    date         NOT NULL COMMENT '预计配送日期',
    `is_print`         tinyint(1) NOT NULL DEFAULT '0' COMMENT '打印状态',
    `is_deleted`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`          int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`        varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`      datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_code` (`code`) USING BTREE,
    KEY                `idx_code` (`code`) USING BTREE,
    KEY                `idx_customer_id` (`customer_id`) USING BTREE,
    KEY                `idx_customer_dept_id` (`customer_dept_id`) USING BTREE,
    KEY                `idx_status` (`status`) USING BTREE,
    KEY                `idx_delivery_date` (`delivery_date`) USING BTREE,
    KEY                `idx_create_time` (`create_time`) USING BTREE,
    KEY                `idx_type` (`type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='销售订单表';

-- 销售订单详情表
CREATE TABLE `t_sale_order_detail`
(
    `id`               bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`         bigint(10) unsigned NOT NULL COMMENT '订单ID',
    `customer_id`      bigint(10) unsigned NOT NULL COMMENT '客户ID',
    `customer_dept_id` bigint(10) unsigned NOT NULL COMMENT '客户部门ID',
    `sku_id`           bigint(10) unsigned DEFAULT NULL COMMENT '商品ID（临时添加，可为空）',
    `order_code`       varchar(200) NOT NULL COMMENT '订单编号',
    `product_name`     varchar(200) NOT NULL COMMENT '商品名称',
    `product_unit`     varchar(20)           DEFAULT NULL COMMENT '商品单位（可为空）',
    `product_price`    decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '商品单价',
    `product_spec`     varchar(200)          DEFAULT NULL COMMENT '商品规格',
    `num`              decimal(10, 2) unsigned NOT NULL COMMENT '计划数量',
    `expect_amount`    decimal(10, 2) unsigned DEFAULT '0' COMMENT '计划总金额',
    `actual_price`     decimal(10, 2) unsigned NOT NULL DEFAULT '0' COMMENT '验收商品单价',
    `actual_num`       decimal(10, 2) unsigned DEFAULT '0' COMMENT '验收数量',
    `actual_amount`    decimal(10, 2) unsigned DEFAULT '0' COMMENT '验收总金额',
    `sort`             int(10) unsigned NOT NULL DEFAULT '0' COMMENT '订单详情排序',
    `is_deleted`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`          int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`        varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`      datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY (`order_id`, `sku_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='销售订单详情表';

-- 送货单表
CREATE TABLE `t_delivery_order`
(
    `id`            bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`   bigint(10) unsigned DEFAULT NULL COMMENT '客户ID',
    `code`          varchar(200) NOT NULL COMMENT '送货单编号',
    `status`        tinyint(3) unsigned NOT NULL COMMENT '送货单状态：0待打印,1送货,2完成',
    `delivery_date` date         NOT NULL COMMENT '配送日期',
    `is_deleted`    tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`       int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`   datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_code` (`code`) USING BTREE,
    KEY             `idx_code` (`code`) USING BTREE,
    KEY             `idx_customer_id` (`customer_id`) USING BTREE,
    KEY             `idx_status` (`status`) USING BTREE,
    KEY             `idx_delivery_date` (`delivery_date`) USING BTREE,
    KEY             `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='送货单表';

-- 送货单详情表
CREATE TABLE `t_delivery_order_detail`
(
    `id`               bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `delivery_id`      bigint(10) unsigned DEFAULT NULL COMMENT '送货单ID',
    `order_id`         bigint(10) unsigned DEFAULT NULL COMMENT '订单ID',
    `customer_id`      bigint(10) unsigned DEFAULT NULL COMMENT '客户ID',
    `customer_dept_id` bigint(10) unsigned DEFAULT NULL COMMENT '客户部门ID',
    `order_code`       varchar(200) NOT NULL COMMENT '订单编号',
    `is_print`         tinyint(1) NOT NULL DEFAULT '0' COMMENT '打印状态',
    `is_deleted`       tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`          int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`        varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`      datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_order_id` (`order_id`) USING BTREE,
    KEY                `idx_delivery_id` (`delivery_id`) USING BTREE,
    KEY                `idx_order_id` (`order_id`) USING BTREE,
    KEY                `idx_order_code` (`order_code`) USING BTREE,
    KEY                `idx_customer_id` (`customer_id`) USING BTREE,
    KEY                `idx_customer_dept_id` (`customer_dept_id`) USING BTREE,
    KEY                `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='送货单详情表';

-- 打印模板表
CREATE TABLE `t_print_template`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id` bigint(10) unsigned NOT NULL DEFAULT '0' COMMENT '客户ID（默认为0，表示通用模板）',
    `code`        varchar(200) NOT NULL COMMENT '打印模板编号',
    `name`        varchar(200) NOT NULL COMMENT '打印模板名称',
    `content`     JSON         NOT NULL COMMENT '打印模板内容',
    `data`        JSON         NOT NULL COMMENT '打印测试数据',
    `type`        tinyint(1) NOT NULL DEFAULT '0' COMMENT '打印模板类型，0-送货单，1-汇总表',
    `is_deleted`  tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `version`     int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `unq_code` (`code`) USING BTREE,
    KEY           `idx_name` (`name`) USING BTREE,
    KEY           `idx_customer_id` (`customer_id`) USING BTREE
)ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='打印模板表';

-- 打印任务表
CREATE TABLE `t_print_task`
(
    `id`          bigint(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id` bigint(10) unsigned NOT NULL COMMENT '模板ID',
    `order_id`    bigint(10) unsigned NOT NULL COMMENT '订单ID',
    `request_id`  varchar(64) NOT NULL COMMENT '请求ID',
    `template_content`     JSON         NOT NULL COMMENT '打印模板内容',
    `template_data`        JSON         NOT NULL COMMENT '打印数据',
    `status`      int(4) NOT NULL DEFAULT '0' COMMENT '任务状态: 0-新增, 1-完成, 2-取消, 3-失败',
    `version`     int(10) unsigned NOT NULL DEFAULT '0' COMMENT '版本号',
    `create_by`   varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    KEY           `idx_template_id` (`template_id`) USING BTREE,
    KEY           `idx_order_id` (`order_id`) USING BTREE,
    KEY           `idx_request_id` (`request_id`) USING BTREE
)ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='打印任务表';
-- ============================================================
-- [04] 商品分类数据（DROP+CREATE+INSERT 种子）  | 源: t_product_category.sql
-- ============================================================

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

-- ============================================================
-- [05] 业务菜单 2000~2047 + 业务字典 dict_type 100~107（库名限定已去除）  | 源: new_added_sql.sql
-- ============================================================

-- add sys_menu item
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2000, '商品分类', 4, 1, 'category', 'product/category/index', NULL, '', 1, 0, 'C', '0', '0', 'product:category:list', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '商品分类菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2001, '商品分类查询', 2000, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:category:query', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2002, '商品分类新增', 2000, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:category:add', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2003, '商品分类修改', 2000, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:category:edit', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2004, '商品分类删除', 2000, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:category:remove', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2005, '商品分类导出', 2000, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:category:export', '#', 'admin', '2024-11-07 20:37:58', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2006, '商品库', 4, 1, 'spu', 'product/spu/index', NULL, '', 1, 0, 'C', '0', '0', 'product:spu:list', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '商品库菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2007, '商品库查询', 2006, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:query', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2008, '商品库新增', 2006, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:add', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2009, '商品库修改', 2006, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:edit', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2010, '商品库删除', 2006, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:remove', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2011, '商品库导出', 2006, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:export', '#', 'admin', '2024-11-07 21:14:23', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2012, '客户信息', 4, 1, 'customer', 'partner/customer/index', NULL, '', 1, 0, 'C', '0', '0', 'partner:customer:list', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '客户菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2013, '客户查询', 2012, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customer:query', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2014, '客户新增', 2012, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customer:add', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2015, '客户修改', 2012, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customer:edit', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2016, '客户删除', 2012, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customer:remove', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2017, '客户导出', 2012, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customer:export', '#', 'admin', '2024-11-08 22:59:08', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2018, '商品信息', 4, 1, 'sku', 'product/sku/index', NULL, '', 1, 0, 'C', '0', '0', 'product:sku:list', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '商品信息菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2019, '商品信息查询', 2018, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:sku:query', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2020, '商品信息新增', 2018, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:sku:add', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2021, '商品信息修改', 2018, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:sku:edit', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2022, '商品信息删除', 2018, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:sku:remove', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2023, '商品信息导出', 2018, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:sku:export', '#', 'admin', '2024-11-11 06:23:24', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2024, '商品报价', 4, 1, 'quote', 'product/quote/index', NULL, '', 1, 0, 'C', '0', '0', 'product:quote:list', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '商品报价菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2025, '商品报价查询', 2024, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:query', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2026, '商品报价新增', 2024, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:add', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2027, '商品报价修改', 2024, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:edit', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2028, '商品报价删除', 2024, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:remove', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2029, '商品报价导出', 2024, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:export', '#', 'admin', '2024-11-14 08:49:40', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2030, '销售订单', 5, 1, 'sale', 'order/sale/index', NULL, '', 1, 0, 'C', '0', '0', 'order:sale:list', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '销售订单菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2031, '销售订单查询', 2030, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:query', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2032, '销售订单新增', 2030, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:add', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2033, '销售订单修改', 2030, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:edit', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2034, '销售订单删除', 2030, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:remove', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2035, '销售订单导出', 2030, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:export', '#', 'admin', '2024-11-23 23:10:27', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2036, '送货单据', 5, 1, 'delivery', 'order/delivery/index', NULL, '', 1, 0, 'C', '0', '0', 'order:delivery:list', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '送货单据菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2037, '送货单据查询', 2036, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:query', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2038, '送货单据新增', 2036, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:add', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2039, '送货单据修改', 2036, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:edit', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2040, '送货单据删除', 2036, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:remove', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2041, '送货单据导出', 2036, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:export', '#', 'admin', '2024-12-11 09:59:06', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2042, '打印模板', 6, 1, 'template', 'print/template/index', NULL, '', 1, 0, 'C', '0', '0', 'print:template:list', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '打印模板菜单');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2043, '打印模板查询', 2042, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'print:template:query', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2044, '打印模板新增', 2042, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'print:template:add', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2045, '打印模板修改', 2042, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'print:template:edit', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2046, '打印模板删除', 2042, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'print:template:remove', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2047, '打印模板导出', 2042, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'print:template:export', '#', 'admin', '2024-12-18 23:03:33', '', NULL, '');

-- add dict type & data
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (100,'业务是否','biz_yes_no','0','admin','2024-11-08 07:06:37','',null,null);
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (101,'客户类型','t_customer_type','0','admin','2024-11-11 02:45:59','',null,null);
INSERT INTO `sys_dict_type` (`dict_id`,`dict_name`,`dict_type`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`) VALUES (102,'商品单位','t_sku_unit','0','admin','2024-11-11 08:12:35','',null,null);
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (103, '报价状态', 't_sku_quote_status', '0', 'admin', '2024-11-23 11:00:10', '', NULL, NULL);
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (104, '销售订单状态', 't_sale_order_status', '0', 'admin', '2024-11-23 22:33:32', '', NULL, NULL);
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (105, '销售订单类型', 't_sale_order_type', '0', 'admin', '2024-11-23 23:01:18', '', NULL, NULL);
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (106, '销售订单来源', 't_sale_order_source', '0', 'admin', '2024-11-23 23:03:10', '', NULL, NULL);
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (107, '销售送货单状态', 't_delivery_order_status', '0', 'admin', '2024-11-23 23:03:10', '', NULL, NULL);
-- ============================================================
-- [06] 通用业务序列表 + 业务字典数据 + 采购单状态字典(108)  | 源: s0_1_biz_code_seq_and_dicts.sql
-- ============================================================

-- ============================================================
-- S0-1 权限字典与单号服务
-- 1) 通用业务序列表 biz_code_seq（DB 序列替代 Redis，Redis 降为非硬依赖）
-- 2) 补齐业务状态字典数据（原库仅有 dict_type 无 dict_data，页面此前显示裸数字）
-- 3) 新增采购单状态字典
-- ============================================================

-- 1. 通用业务序列表
CREATE TABLE IF NOT EXISTS `biz_code_seq` (
  `biz_key`     varchar(64) NOT NULL COMMENT '序列键（bizType:yyyyMMdd 或 bizType:ownerId）',
  `seq`         bigint(20)  NOT NULL DEFAULT 0 COMMENT '当前序列值',
  `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务序列表';

-- 2. 销售订单状态字典数据（dict_type=104，枚举码 0-4 保持不变，仅补数据与文案）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(30, 0, '草稿',   '0', 't_sale_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '订单状态-草稿(DRAFT)'),
(31, 1, '已确认', '1', 't_sale_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已确认(CONFIRMED)'),
(32, 2, '已配送', '2', 't_sale_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已配送(DELIVERED)'),
(33, 3, '已验收', '3', 't_sale_order_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已验收(ACCEPTED)'),
(34, 4, '已结算', '4', 't_sale_order_status', '', 'danger',  'N', '0', 'admin', sysdate(), '', NULL, '订单状态-已结算(SETTLED)');

-- 3. 送货单状态字典数据（dict_type=107，枚举码 0-2 不变）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(35, 0, '待打印', '0', 't_delivery_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-待打印'),
(36, 1, '已打印', '1', 't_delivery_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-已打印'),
(37, 2, '已送达', '2', 't_delivery_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '送货单状态-已送达');

-- 4. 报价状态字典数据（dict_type=103，沿用现状 NEW/PUBLISHED/INVALID）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(38, 0, '新增', '0', 't_sku_quote_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '报价状态-新增'),
(39, 1, '发布', '1', 't_sku_quote_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '报价状态-发布'),
(40, 2, '失效', '2', 't_sku_quote_status', '', 'danger',  'N', '0', 'admin', sysdate(), '', NULL, '报价状态-失效');

-- 5. 销售订单类型/来源字典数据（dict_type=105/106）
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(41, 0, '正常订单', '1', 't_sale_order_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单类型-正常订单'),
(42, 1, '加单',     '2', 't_sale_order_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, '订单类型-加单'),
(43, 0, '后台下单', '1', 't_sale_order_source', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '订单来源-后台下单'),
(44, 1, '线上下单', '2', 't_sale_order_source', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '订单来源-线上下单');

-- 6. 采购单状态字典（新增 dict_type=108）
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
VALUES (108, '采购单状态', 't_purchase_order_status', '0', 'admin', sysdate(), '', NULL, '采购单状态');

INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(45, 0, '草稿',   '0', 't_purchase_order_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-草稿'),
(46, 1, '已确认', '1', 't_purchase_order_status', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-已确认'),
(47, 2, '已入库', '2', 't_purchase_order_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '采购单状态-已入库');

-- ============================================================
-- [07] 增量表基线（新表 + 复用表字段改造）  | 源: s0_2_table_baseline.sql
-- ============================================================

-- ============================================================
-- S0-2 表结构基线：新建表 + 复用表改造字段
-- 依赖：无（可从空库执行，或对现有库执行增量）
-- 设计依据：DESIGN.md §6、DEVELOPMENT.md §2
-- ============================================================

-- ------------------------------------------------------------
-- 1. 基础数据新增：临时商品、全局别名、客户 SKU 映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `temp_product` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`          varchar(200) NOT NULL COMMENT '临时商品名称',
  `spec`          varchar(200) DEFAULT NULL COMMENT '规格',
  `unit`          varchar(50)  DEFAULT NULL COMMENT '单位',
  `default_price` decimal(10,2) DEFAULT NULL COMMENT '默认单价',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时商品表';

CREATE TABLE IF NOT EXISTS `product_alias` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `alias_type` tinyint(3)   NOT NULL DEFAULT 1 COMMENT '别名类型：1名称 2拼音 3英文缩写',
  `alias`      varchar(200) NOT NULL COMMENT '别名内容',
  `sku_id`     bigint(20)   NOT NULL COMMENT '关联SKU',
  `create_by`  varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
  `update_by`  varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alias` (`alias`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品全局别名表';

CREATE TABLE IF NOT EXISTS `customer_sku_mapping` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`    bigint(20)   NOT NULL COMMENT '客户ID',
  `customer_alias` varchar(200) NOT NULL COMMENT '客户侧叫法/编码',
  `sku_id`         bigint(20)   NOT NULL COMMENT '我方SKU',
  `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_alias` (`customer_id`, `customer_alias`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户SKU映射表（客户叫法->我方SKU）';

-- ------------------------------------------------------------
-- 2. 报价新增：报价模板、模板SKU价、配送点报价
--    客户报价复用 t_product_sku_quote（有效期字段已存在，无需加列）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `price_template` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`           varchar(200) NOT NULL COMMENT '模板名称',
  `status`         char(1)      NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `effective_date` date         DEFAULT NULL COMMENT '生效日期',
  `expire_date`    date         DEFAULT NULL COMMENT '失效日期',
  `is_default`     char(1)      NOT NULL DEFAULT '0' COMMENT '是否全局默认（0否 1是）',
  `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
  `remark`         varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板表';

CREATE TABLE IF NOT EXISTS `price_template_sku` (
  `id`             bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id`    bigint(20)    NOT NULL COMMENT '模板ID',
  `sku_id`         bigint(20)    NOT NULL COMMENT 'SKU',
  `unit_price`     decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价',
  `effective_date` date          DEFAULT NULL COMMENT '生效日期',
  `expire_date`    date          DEFAULT NULL COMMENT '失效日期',
  `create_by`      varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`      varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`    datetime      DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板SKU价格表';

CREATE TABLE IF NOT EXISTS `delivery_point_price` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `delivery_point_id` bigint(20)    NOT NULL COMMENT '配送点ID（t_customer_dept.id）',
  `sku_id`            bigint(20)    NOT NULL COMMENT 'SKU',
  `unit_price`        decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价',
  `effective_date`    date          DEFAULT NULL COMMENT '生效日期',
  `expire_date`       date          DEFAULT NULL COMMENT '失效日期',
  `create_by`         varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`         varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`            varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_point_id` (`delivery_point_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送点报价表';

-- ------------------------------------------------------------
-- 3. 订单：调整记录表 + 复用表改造字段（t_sale_order 加 adjust_flag）
-- ------------------------------------------------------------
ALTER TABLE `t_sale_order`
  ADD COLUMN `adjust_flag` char(1) NOT NULL DEFAULT '0' COMMENT '是否发生配送后调整（0否 1是）' AFTER `status`;

CREATE TABLE IF NOT EXISTS `order_adjustment` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`      bigint(20)   NOT NULL COMMENT '原订单ID',
  `order_item_id` bigint(20)   DEFAULT NULL COMMENT '原订单行ID（可空）',
  `type`          tinyint(3)   NOT NULL COMMENT '类型：1加单 2退单 3换货',
  `reason`        varchar(500) DEFAULT NULL COMMENT '原因',
  `adjust_date`   date         NOT NULL COMMENT '调整日期（归属D天）',
  `detail_json`   text         COMMENT '明细说明JSON（换货含加/退两行）',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`     varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单加退换调整表';

-- ------------------------------------------------------------
-- 4. 采购：采购单主表与明细
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id`               bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`             varchar(32)   NOT NULL COMMENT '采购单号（PCyyyyMMddNNN）',
  `order_date`       date          NOT NULL COMMENT '采购归属日期（=订单配送日期）',
  `source_type`      tinyint(3)    NOT NULL COMMENT '来源类型：1自动生成 2手工创建',
  `source_order_ids` varchar(2000) DEFAULT NULL COMMENT '来源订单ID列表（JSON）',
  `supplier_id`      bigint(20)    DEFAULT NULL COMMENT '供应商ID（可空，确认时后补）',
  `supplier_name`    varchar(200)  DEFAULT NULL COMMENT '供应商名称（直填）',
  `total_amount`     decimal(12,2) NOT NULL DEFAULT 0 COMMENT '采购总额',
  `status`           tinyint(3)    NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已确认 2已入库',
  `create_by`        varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`      datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`        varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`      datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`           varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_order_date` (`order_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单主表';

CREATE TABLE IF NOT EXISTS `purchase_item` (
  `id`           bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_id`  bigint(20)    NOT NULL COMMENT '采购单ID',
  `sku_id`       bigint(20)    DEFAULT NULL COMMENT 'SKU（临时商品可空）',
  `product_name` varchar(200)  NOT NULL COMMENT '商品名称快照',
  `product_spec` varchar(200)  DEFAULT NULL COMMENT '规格快照',
  `product_unit` varchar(50)   DEFAULT NULL COMMENT '单位快照',
  `quantity`     decimal(10,2) NOT NULL DEFAULT 0 COMMENT '数量',
  `unit_price`   decimal(10,2) NOT NULL DEFAULT 0 COMMENT '采购单价（成本）',
  `subtotal`     decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计',
  `sort`         int(10)       NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_id` (`purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细表';

-- ------------------------------------------------------------
-- 5. 验收：验收单主表与明细（一单一验）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `acceptance` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`              varchar(32)   NOT NULL COMMENT '验收单号（YSyyyyMMddNNN）',
  `delivery_order_id` bigint(20)    NOT NULL COMMENT '送货单ID（唯一，一单一验）',
  `customer_id`       bigint(20)    NOT NULL COMMENT '客户ID',
  `delivery_point_id` bigint(20)    DEFAULT NULL COMMENT '配送点ID',
  `accept_date`       date          NOT NULL COMMENT '验收日期',
  `total_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '验收总额（结算依据）',
  `status`            tinyint(3)    NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已提交',
  `create_by`         varchar(64)   DEFAULT '' COMMENT '创建者',
  `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
  `update_by`         varchar(64)   DEFAULT '' COMMENT '更新者',
  `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
  `remark`            varchar(500)  DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_delivery_order_id` (`delivery_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收单主表';

CREATE TABLE IF NOT EXISTS `acceptance_item` (
  `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
  `acceptance_id`      bigint(20)    NOT NULL COMMENT '验收单ID',
  `delivery_item_id`   bigint(20)    DEFAULT NULL COMMENT '送货单明细ID',
  `sku_id`             bigint(20)    DEFAULT NULL COMMENT 'SKU',
  `product_name`       varchar(200)  NOT NULL COMMENT '商品名称快照',
  `product_spec`       varchar(200)  DEFAULT NULL COMMENT '规格快照',
  `product_unit`       varchar(50)   DEFAULT NULL COMMENT '单位快照',
  `delivered_quantity` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '送货数量（基线=调整后订单行数量）',
  `actual_quantity`    decimal(10,2) NOT NULL DEFAULT 0 COMMENT '实收数量（可超送）',
  `unit_price`         decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价快照',
  `loss_quantity`      decimal(10,2) NOT NULL DEFAULT 0 COMMENT '损耗数量（实收-送货，可为负）',
  `loss_reason`        varchar(500)  DEFAULT NULL COMMENT '负损耗原因（必填）',
  `actual_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '实收金额（实收×单价）',
  `sort`               int(10)       NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_acceptance_id` (`acceptance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收单明细表';

-- ------------------------------------------------------------
-- 6. 复用表改造：送货单加打印次数；打印模板加引擎与绑定字段
-- ------------------------------------------------------------
ALTER TABLE `t_delivery_order`
  ADD COLUMN `print_count` int(10) NOT NULL DEFAULT 0 COMMENT '打印次数' AFTER `status`;

ALTER TABLE `t_print_template`
  ADD COLUMN `render_engine` varchar(32)  NOT NULL DEFAULT 'jimureport' COMMENT '渲染引擎（jimureport/hiprint）' AFTER `id`,
  ADD COLUMN `bind_type`     tinyint(3)    NOT NULL DEFAULT 3 COMMENT '绑定类型：1客户+配送点组合 2客户 3全局默认' AFTER `render_engine`,
  ADD COLUMN `delivery_point_id` bigint(20) DEFAULT NULL COMMENT '绑定配送点ID（可空）' AFTER `customer_id`,
  ADD COLUMN `copies`        int(10)       NOT NULL DEFAULT 1 COMMENT '联数（打印份数）' AFTER `delivery_point_id`,
  ADD COLUMN `is_default`    char(1)       NOT NULL DEFAULT '0' COMMENT '是否全局默认模板（0否 1是）' AFTER `copies`;

-- ============================================================
-- [08] 菜单/权限：别名与映射  | 源: s1_2_alias_mapping_menu.sql
-- ============================================================

-- ============================================================
-- S1-2 菜单/权限：别名与映射（全局别名、客户SKU映射、临时商品）
-- 父菜单挂载在"基础信息"(menu_id=4) 下；menu_id 从 2050 起，避开现有最大值 2047
-- ============================================================

-- 父菜单（C）：别名与映射
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2050, '别名与映射', 4, 2, 'aliasMapping', 'product/aliasMapping/index', NULL, '', 1, 0, 'C', '0', '0', 'product:aliasMapping:list', '#', 'admin', sysdate(), '', NULL, '别名与映射菜单');

-- 按钮（F）：全局别名
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2051, '别名新增', 2050, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2052, '别名修改', 2050, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2053, '别名删除', 2050, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮（F）：客户SKU映射
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2054, '映射新增', 2050, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2055, '映射修改', 2050, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2056, '映射删除', 2050, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮（F）：临时商品
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2057, '临时商品新增', 2050, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2058, '临时商品修改', 2050, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2059, '临时商品删除', 2050, 9, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2060, '临时商品转正', 2050, 10, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:convert', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [09] 菜单/权限：导入按钮  | 源: s1_3_import_menu.sql
-- ============================================================

-- ============================================================
-- S1-3 导入初始化：商品库导入、客户报价导入的按钮权限
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2061, '商品库导入', 2006, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:import', '#', 'admin', sysdate(), '', NULL, ''),
(2062, '报价导入', 2024, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:import', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [10] 菜单/权限：报价模板、配送点报价  | 源: s2_2_price_menu.sql
-- ============================================================

-- ============================================================
-- S2-2 菜单/权限：报价模板、配送点报价
-- 父菜单挂载在"基础信息"(menu_id=4) 下；menu_id 从 2063 起
-- ============================================================

-- 父菜单（C）：报价模板
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2063, '报价模板', 4, 3, 'priceTemplate', 'price/template/index', NULL, '', 1, 0, 'C', '0', '0', 'price:template:list', '#', 'admin', sysdate(), '', NULL, '报价模板菜单');

-- 按钮（F）：报价模板
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2065, '模板新增', 2063, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2066, '模板修改', 2063, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2067, '模板删除', 2063, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 父菜单（C）：配送点覆盖
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2064, '配送点覆盖', 4, 4, 'pointPrice', 'price/pointPrice/index', NULL, '', 1, 0, 'C', '0', '0', 'price:delivery-override:list', '#', 'admin', sysdate(), '', NULL, '配送点覆盖菜单（替代原配送点报价）');

-- 按钮（F）：配送点覆盖
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2068, '配送点覆盖新增', 2064, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2069, '配送点覆盖修改', 2064, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2070, '配送点覆盖删除', 2064, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:remove', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [11] 报价模板-客户绑定表  | 源: s2_2_price_template_customer.sql
-- ============================================================

-- ============================================================
-- S2-2 报价模板-客户绑定表（一个客户最多绑定一个模板）
-- ============================================================
CREATE TABLE IF NOT EXISTS `price_template_customer` (
  `id`          bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint(20)  NOT NULL COMMENT '报价模板ID',
  `customer_id` bigint(20)  NOT NULL COMMENT '客户ID',
  `create_by`   varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_id` (`customer_id`),
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价模板-客户绑定表';

-- ============================================================
-- [12] 菜单/权限：订单撤回、月结  | 源: s3_1_order_status_menu.sql
-- ============================================================

-- ============================================================
-- S3-1 订单五状态：撤回、月结按钮权限
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2071, '订单撤回', 2030, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:recall', '#', 'admin', sysdate(), '', NULL, ''),
(2072, '订单月结', 2030, 7, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:settle', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [13] 菜单/权限：工作台  | 源: s3_2_workbench_menu.sql
-- ============================================================

-- ============================================================
-- S3-2 工作台菜单（一级菜单，置顶）
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2073, '工作台', 0, 1, 'workbench', 'workbench/index', NULL, '', 1, 0, 'C', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, '文员工作台');

-- ============================================================
-- [14] 菜单/权限：订单调整  | 源: s3_4_adjustment_menu.sql
-- ============================================================

-- ============================================================
-- S3-4 菜单/权限：订单调整（配送后加退换）
-- F 按钮挂在"销售订单"(menu_id=2030) 下
-- ============================================================

INSERT INTO `fresh-distribution-dsh`.`sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2074, '订单调整', 2030, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:adjust', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [15] 菜单/权限：采购管理  | 源: s4_purchase_menu.sql
-- ============================================================

-- ============================================================
-- S4 采购切片：菜单/按钮权限
-- 一级菜单 2075 采购管理；按钮 2076-2078（新增/修改/删除）。
-- 说明：生成采购单复用"新增"权限（purchase:add），不单独建按钮，
--       故 2079 预留未使用。
-- ============================================================

-- 一级菜单（C）：采购管理
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2075, '采购管理', 0, 3, 'purchase', 'purchase/index', NULL, '', 1, 0, 'C', '0', '0', 'purchase:list', 'shopping', 'admin', sysdate(), '', NULL, '采购管理菜单');

-- 按钮（F）：采购单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2076, '采购单新增', 2075, 1, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:add', '#', 'admin', sysdate(), '', NULL, ''),
(2077, '采购单修改', 2075, 2, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2078, '采购单删除', 2075, 3, '', '', NULL, '', 1, 0, 'F', '0', '0', 'purchase:remove', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [16] 送货单表结构适配（配送点维度、商品合并）  | 源: s5_1_delivery_alter.sql
-- ============================================================

-- ============================================================
-- S5-1 送货生成：表结构适配（按商品合并 + 配送点维度）
-- ============================================================

-- 送货单增加配送点维度
ALTER TABLE `t_delivery_order`
  ADD COLUMN `delivery_point_id` bigint(20) DEFAULT NULL COMMENT '配送点ID（t_customer_dept.id）' AFTER `customer_id`;

-- 送货明细：移除"一单一订单"唯一约束，增加商品合并行字段
ALTER TABLE `t_delivery_order_detail`
  DROP INDEX `unq_order_id`,
  ADD COLUMN `sku_id` bigint(20) DEFAULT NULL COMMENT 'SKU（临时商品可空）' AFTER `order_code`,
  ADD COLUMN `product_name` varchar(200) DEFAULT NULL COMMENT '商品名称快照' AFTER `sku_id`,
  ADD COLUMN `product_unit` varchar(50) DEFAULT NULL COMMENT '单位快照' AFTER `product_name`,
  ADD COLUMN `product_spec` varchar(200) DEFAULT NULL COMMENT '规格快照' AFTER `product_unit`,
  ADD COLUMN `num` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '送货数量' AFTER `product_spec`,
  ADD COLUMN `price` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '单价快照' AFTER `num`,
  ADD COLUMN `amount` decimal(12,2) NOT NULL DEFAULT 0 COMMENT '小计（num*price）' AFTER `price`;

-- ============================================================
-- [17] 菜单/权限：送货单打印、送达  | 源: s5_1_delivery_menu.sql
-- ============================================================

-- ============================================================
-- S5-1 菜单/权限：送货单打印、送达按钮
-- F 按钮挂在"送货单据"(menu_id=2036) 下
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2079, '送货单打印', 2036, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:print', '#', 'admin', sysdate(), '', NULL, '送货单打印（print_count+1）'),
(2080, '送货单送达', 2036, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:deliver', '#', 'admin', sysdate(), '', NULL, '送货单送达（订单→DELIVERED）');

-- ============================================================
-- [18] 菜单/权限：验收 + 验收状态字典  | 源: s5_2_acceptance_menu.sql
-- ============================================================

-- ============================================================
-- S5-2 验收：菜单/按钮权限 + 验收状态字典
-- C 菜单挂在"单据管理"(menu_id=5) 下；按钮 2082-2086
-- ============================================================

-- 验收单状态字典（0草稿 1已提交）
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
VALUES (109, '验收单状态', 't_acceptance_status', '0', 'admin', sysdate(), '', NULL, '验收单状态');

INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(48, 0, '草稿',   '0', 't_acceptance_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-草稿'),
(49, 1, '已提交', '1', 't_acceptance_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-已提交');

-- 菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2081, '验收单', 5, 3, 'acceptance', 'order/acceptance/index', NULL, '', 1, 0, 'C', '0', '0', 'acceptance:list', 'post', 'admin', sysdate(), '', NULL, '验收单菜单');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2082, '验收单查询', 2081, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:query', '#', 'admin', sysdate(), '', NULL, ''),
(2083, '验收单新增', 2081, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:add', '#', 'admin', sysdate(), '', NULL, ''),
(2084, '验收单修改', 2081, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2085, '验收单删除', 2081, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2086, '验收单提交', 2081, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:submit', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [19] 菜单/权限：报表中心  | 源: s6_1_report_menu.sql
-- ============================================================

-- ============================================================
-- S6-1 报表：菜单/按钮权限（报表中心）
-- C 菜单 2087 顶级；按钮 2088 查询 / 2089 导出
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2087, '报表中心', 0, 4, 'report', 'report/index', NULL, '', 1, 0, 'C', '0', '0', 'report:list', 'chart', 'admin', sysdate(), '', NULL, '报表中心菜单');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2088, '报表查询', 2087, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'report:query', '#', 'admin', sysdate(), '', NULL, ''),
(2089, '报表导出', 2087, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'report:export', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- 初始化结束
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;
