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
-- ============================================================
-- [08] 基础信息模块重构表结构（SKU去客户化/客户商品池/默认模板，幂等ALTER）  | 源: r1_basicinfo_redesign.sql
-- ============================================================

-- ============================================================
-- r1 基础信息模块重构（deepseek_redesign.md）
-- 核心变化：SKU 去客户化（t_product_sku 移除 customer_id），
--           新增 customers_sku（客户商品池）、customer_group（客户分组）、
--           delivery_sku_override（配送点覆盖）、default_sku_template（批量赋值模板）
-- 前置条件：项目未上线，允许重建 t_product_sku（旧数据已确认量级极小，不迁移）
-- 说明：旧 t_product_sku 数据（2 条）+ delivery_point_price（1 条）不再迁移，
--       重新录入标准 SKU；delivery_point_price 表在服务层切换后废弃。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 重建 t_product_sku：移除 customer_id，SKU 成为标准商品单元
--    code 全局唯一：S + 8 位数字（S00000001）
--    唯一约束：(category_id, name, spec_name, unit)
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_product_sku`;
CREATE TABLE `t_product_sku` (
    `id`              bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `spu_id`          bigint(20)              DEFAULT NULL COMMENT '所属SPU（可空）',
    `category_id`     bigint(20)     NOT NULL COMMENT '分类ID',
    `code`            varchar(32)    NOT NULL COMMENT '全局唯一编码（S+8位数字）',
    `name`            varchar(200)   NOT NULL COMMENT '商品名称',
    `mnemonic_code`   varchar(128)            DEFAULT NULL COMMENT '助记码（拼音首字母）',
    `spec_name`       varchar(200)            DEFAULT NULL COMMENT '规格描述（如“大果”“5斤/箱”）',
    `unit`            varchar(20)    NOT NULL COMMENT '固定单位（箱/斤）',
    `is_weighted`     tinyint(1)     NOT NULL DEFAULT '0' COMMENT '是否称重商品（1=称重，0=非称重）',
    `base_unit`       varchar(20)             DEFAULT NULL COMMENT '基础单位（可选，跨SKU汇总用）',
    `conversion_rate` decimal(10,4)           DEFAULT NULL COMMENT '与基础单位的换算率',
    `sale_price`      decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '参考售价（仅展示，非交易价格）',
    `saleable`        tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否上架',
    `valid`           tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否有效',
    `is_deleted`      tinyint(1)     NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`       varchar(64)              DEFAULT '' COMMENT '创建者',
    `create_time`     timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       varchar(64)              DEFAULT '' COMMENT '更新者',
    `update_time`     datetime                DEFAULT NULL COMMENT '更新时间',
    `remark`          varchar(500)            DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_category_name_spec_unit` (`category_id`, `name`, `spec_name`, `unit`),
    KEY `idx_spu_id` (`spu_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_saleable` (`saleable`),
    KEY `idx_valid` (`valid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准商品SKU表（客户无关）';

-- ------------------------------------------------------------
-- 2. 新增 customers_sku：客户商品池（客户对标准SKU的个性化）
--    唯一约束：(customer_id, sku_id)；customer_code 全局唯一
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customers_sku` (
    `id`                bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `customer_id`       bigint(20)     NOT NULL COMMENT '客户ID',
    `sku_id`            bigint(20)     NOT NULL COMMENT '标准SKU ID',
    `alias`             varchar(200)            DEFAULT NULL COMMENT '客户自定义商品别名',
    `customer_code`     varchar(64)    NOT NULL COMMENT '客户商品编码（C{客户ID}+6位自增）',
    `unit`              varchar(20)             DEFAULT NULL COMMENT '客户下单单位（默认同SKU单位）',
    `min_order_qty`     decimal(10,2)  NOT NULL DEFAULT '1.00' COMMENT '最小起订量',
    `order_step`        decimal(10,2)  NOT NULL DEFAULT '1.00' COMMENT '下单步长',
    `is_follow_default` tinyint(1)     NOT NULL DEFAULT '1' COMMENT '是否跟随默认模板（1=是，0=已个性化）',
    `source_template_id` bigint(20)             DEFAULT NULL COMMENT '来源模板ID',
    `status`            tinyint(1)     NOT NULL DEFAULT '1' COMMENT '状态（1可用 0停用）',
    `created_at`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime                DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_sku` (`customer_id`, `sku_id`),
    UNIQUE KEY `uk_customer_code` (`customer_code`),
    KEY `idx_sku_id` (`sku_id`),
    KEY `idx_customer_status` (`customer_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户商品表（客户对SKU的个性化与商品池）';

-- ------------------------------------------------------------
-- 3. 新增 customer_group：客户分组（默认模板按分组适配）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `customer_group` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(50)  NOT NULL COMMENT '分组名称（如“批发”“食堂”）',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户分组表';

-- t_customer 增加 group_id（可空，现有客户无分组）；幂等处理（MySQL 5.7 无 ADD COLUMN IF NOT EXISTS）
DROP PROCEDURE IF EXISTS `r1_add_customer_group_id`;
DELIMITER $$
CREATE PROCEDURE `r1_add_customer_group_id`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer' AND COLUMN_NAME = 'group_id') THEN
        ALTER TABLE `t_customer` ADD COLUMN `group_id` bigint(20) DEFAULT NULL COMMENT '客户分组ID（关联 customer_group.id）' AFTER `alias`;
        ALTER TABLE `t_customer` ADD KEY `idx_group_id` (`group_id`);
    END IF;
END$$
DELIMITER ;
CALL `r1_add_customer_group_id`();
DROP PROCEDURE `r1_add_customer_group_id`;

-- ------------------------------------------------------------
-- 4. 新增 delivery_sku_override：配送点级覆盖（价格/别名/可见性）
--    整合原 delivery_point_price；唯一 (delivery_point_id, sku_id)，
--    生效/失效日期管理有效期，取价取最新生效
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `delivery_sku_override` (
    `id`               bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `delivery_point_id` bigint(20)   NOT NULL COMMENT '配送点ID（关联 t_customer_dept.id）',
    `sku_id`           bigint(20)    NOT NULL COMMENT '标准SKU ID',
    `is_available`     tinyint(1)    NOT NULL DEFAULT '1' COMMENT '是否可用（1=可见，0=隐藏）',
    `price_override`   decimal(10,2)          DEFAULT NULL COMMENT '价格覆盖（空则继承客户级价格）',
    `alias_override`   varchar(200)           DEFAULT NULL COMMENT '别名覆盖（可空）',
    `effective_date`   date                   DEFAULT NULL COMMENT '生效日期（可空）',
    `expire_date`      date                   DEFAULT NULL COMMENT '失效日期（可空）',
    `created_at`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       datetime               DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delivery_sku` (`delivery_point_id`, `sku_id`),
    KEY `idx_sku_id` (`sku_id`),
    KEY `idx_available` (`is_available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送点商品覆盖表（价格/别名/可见性）';

-- ------------------------------------------------------------
-- 5. 新增 default_sku_template + default_sku_template_item：批量赋值默认SKU模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `default_sku_template` (
    `id`                bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`              varchar(100) NOT NULL COMMENT '模板名称（如“食堂常用商品”）',
    `customer_group_id` bigint(20)  DEFAULT NULL COMMENT '适用客户分组（可空=全部）',
    `status`            tinyint(1)  NOT NULL DEFAULT '1' COMMENT '状态（1启用 0停用）',
    `created_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        datetime             DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_customer_group` (`customer_group_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认SKU模板（批量赋值用，不含价格）';

CREATE TABLE IF NOT EXISTS `default_sku_template_item` (
    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_id` bigint(20) NOT NULL COMMENT '模板ID',
    `sku_id`      bigint(20) NOT NULL COMMENT '标准SKU ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_sku` (`template_id`, `sku_id`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认SKU模板明细';

-- ------------------------------------------------------------
-- 6. temp_product 增强：支持客户专用临时商品 + 转正标记（幂等）
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS `r1_add_temp_product_cols`;
DELIMITER $$
CREATE PROCEDURE `r1_add_temp_product_cols`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'temp_product' AND COLUMN_NAME = 'customer_id') THEN
        ALTER TABLE `temp_product`
            ADD COLUMN `customer_id` bigint(20) DEFAULT NULL COMMENT '关联客户ID（可空=全局临时商品）' AFTER `id`,
            ADD COLUMN `converted_sku_id` bigint(20) DEFAULT NULL COMMENT '转正后标准SKU ID（可空=未转正）' AFTER `customer_id`,
            ADD KEY `idx_customer_id` (`customer_id`),
            ADD KEY `idx_converted_sku` (`converted_sku_id`);
    END IF;
END$$
DELIMITER ;
CALL `r1_add_temp_product_cols`();
DROP PROCEDURE `r1_add_temp_product_cols`;

-- ============================================================
-- [09] 基础信息重构菜单（客户商品/默认SKU模板，DELETE+INSERT 幂等）  | 源: r1_frontend_menu.sql
-- ============================================================

-- ============================================================
-- R1 阶段4 前端配套：菜单/权限/字典
-- 1) 菜单 2090 起（现有最大值 2089）
-- 2) 配送点报价(2064) 改名为"配送点覆盖"，权限 price:point:* → price:delivery-override:*
-- 3) 补齐 t_sku_unit / biz_yes_no 字典数据（字典类型已存在但无数据）
-- 幂等：可重复执行（先删后插）
-- ============================================================

-- ---------- 1. 配送点报价 → 配送点覆盖（改名 + 换权限） ----------
UPDATE `sys_menu` SET menu_name = '配送点覆盖', perms = 'price:delivery-override:list', remark = '配送点覆盖菜单（替代原配送点报价）' WHERE menu_id = 2064;
UPDATE `sys_menu` SET menu_name = '配送点覆盖新增', perms = 'price:delivery-override:add' WHERE menu_id = 2068;
UPDATE `sys_menu` SET menu_name = '配送点覆盖修改', perms = 'price:delivery-override:edit' WHERE menu_id = 2069;
UPDATE `sys_menu` SET menu_name = '配送点覆盖删除', perms = 'price:delivery-override:remove' WHERE menu_id = 2070;

-- ---------- 2. 客户商品菜单（挂"基础信息" parent=4） ----------
DELETE FROM `sys_menu` WHERE menu_id BETWEEN 2090 AND 2094;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2090, '客户商品', 4, 5, 'customerSku', 'product/customerSku/index', NULL, '', 1, 0, 'C', '0', '0', 'product:customer-sku:list', '#', 'admin', sysdate(), '', NULL, '客户商品池与个性化');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2091, '客户商品新增', 2090, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2092, '客户商品修改', 2090, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2093, '客户商品删除', 2090, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2094, '客户商品批量赋值', 2090, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:customer-sku:assign', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 3. 默认SKU模板菜单 ----------
DELETE FROM `sys_menu` WHERE menu_id BETWEEN 2095 AND 2099;
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2095, '默认SKU模板', 4, 6, 'defaultSkuTemplate', 'product/defaultSkuTemplate/index', NULL, '', 1, 0, 'C', '0', '0', 'product:default-sku-template:list', '#', 'admin', sysdate(), '', NULL, '批量赋值默认SKU模板');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2096, '模板查询', 2095, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:query', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2097, '模板新增', 2095, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2098, '模板修改', 2095, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2099, '模板删除', 2095, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:default-sku-template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 4. 字典数据补齐（幂等：先删后插） ----------
-- biz_yes_no（业务是否）：0=否 1=是
DELETE FROM `sys_dict_data` WHERE dict_type = 'biz_yes_no';
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES (52, 1, '是', '1', 'biz_yes_no', '', 'primary', 'N', '0', 'admin', sysdate(), '');
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES (53, 2, '否', '0', 'biz_yes_no', '', 'danger', 'N', '0', 'admin', sysdate(), '');
-- t_sku_unit（商品单位）
DELETE FROM `sys_dict_data` WHERE dict_type = 't_sku_unit';
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES
(54, 1, '斤', '斤', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(55, 2, '公斤', '公斤', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(56, 3, '箱', '箱', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(57, 4, '袋', '袋', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(58, 5, '份', '份', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(59, 6, '个', '个', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(60, 7, '包', '包', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(61, 8, '瓶', '瓶', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(62, 9, '件', '件', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
(63, 10, '捆', '捆', 't_sku_unit', '', '', 'N', '0', 'admin', sysdate(), '');

-- [10] 菜单/权限：别名与映射  | 源: s1_2_alias_mapping_menu.sql
-- ============================================================

-- ============================================================
-- S1-2 菜单/权限：别名与映射（全局别名、客户SKU映射、临时商品）
-- 父菜单挂载在"基础信息"(menu_id=4) 下；menu_id 从 2050 起，避开现有最大值 2047
-- ============================================================

-- 父菜单（C）：别名与映射
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2050, '别名与映射', 4, 2, 'aliasMapping', 'product/aliasMapping/index', NULL, '', 1, 0, 'C', '0', '0', 'product:aliasMapping:list', '#', 'admin', sysdate(), '', NULL, '别名与映射菜单');

-- 按钮（F）：全局别名
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2051, '别名新增', 2050, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2052, '别名修改', 2050, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2053, '别名删除', 2050, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:alias:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮（F）：客户SKU映射
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2054, '映射新增', 2050, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2055, '映射修改', 2050, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2056, '映射删除', 2050, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:mapping:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮（F）：临时商品
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2057, '临时商品新增', 2050, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2058, '临时商品修改', 2050, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2059, '临时商品删除', 2050, 9, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:remove', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2060, '临时商品转正', 2050, 10, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'product:temp:convert', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [11] 菜单/权限：导入按钮  | 源: s1_3_import_menu.sql
-- ============================================================

-- ============================================================
-- S1-3 导入初始化：商品库导入、客户报价导入的按钮权限
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2061, '商品库导入', 2006, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:spu:import', '#', 'admin', sysdate(), '', NULL, ''),
(2062, '报价导入', 2024, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'product:quote:import', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [12] 菜单/权限：报价模板、配送点报价  | 源: s2_2_price_menu.sql
-- ============================================================

-- ============================================================
-- S2-2 菜单/权限：报价模板、配送点报价
-- 父菜单挂载在"基础信息"(menu_id=4) 下；menu_id 从 2063 起
-- ============================================================

-- 父菜单（C）：报价模板
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2063, '报价模板', 4, 3, 'priceTemplate', 'price/template/index', NULL, '', 1, 0, 'C', '0', '0', 'price:template:list', '#', 'admin', sysdate(), '', NULL, '报价模板菜单');

-- 按钮（F）：报价模板
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2065, '模板新增', 2063, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2066, '模板修改', 2063, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2067, '模板删除', 2063, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:template:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 父菜单（C）：配送点覆盖
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2064, '配送点覆盖', 4, 4, 'pointPrice', 'price/pointPrice/index', NULL, '', 1, 0, 'C', '0', '0', 'price:delivery-override:list', '#', 'admin', sysdate(), '', NULL, '配送点覆盖菜单（替代原配送点报价）');

-- 按钮（F）：配送点覆盖
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2068, '配送点覆盖新增', 2064, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:add', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2069, '配送点覆盖修改', 2064, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2070, '配送点覆盖删除', 2064, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'price:delivery-override:remove', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [13] 报价模板-客户绑定表  | 源: s2_2_price_template_customer.sql
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
-- [14] 菜单/权限：订单撤回、月结  | 源: s3_1_order_status_menu.sql
-- ============================================================

-- ============================================================
-- S3-1 订单五状态：撤回、月结按钮权限
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2071, '订单撤回', 2030, 6, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:recall', '#', 'admin', sysdate(), '', NULL, ''),
(2072, '订单月结', 2030, 7, '', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:settle', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- ============================================================
-- [15] 商品初始化（报价demo → 商品库/商品/报价，幂等）  | 源: s7_product_init.sql
-- ============================================================

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

-- ============================================================
-- [16] 采购单采购员字段 + 销售订单抽屉按钮菜单  | 源: s8_purchase_delivery_drawer.sql
-- ============================================================

-- ============================================================
-- Phase 2：销售订单列表页生成采购单/送货单抽屉
-- 1) purchase_order 增加采购员字段（幂等：information_schema 探测后 ALTER）
-- 说明：MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，用 PREPARE 兼容幂等重跑
-- ============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'purchase_order'
      AND COLUMN_NAME = 'purchaser'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_order ADD COLUMN purchaser varchar(64) DEFAULT NULL COMMENT ''采购员'' AFTER supplier_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- [17] 基础信息菜单信息架构优化（可移植版）  | 源: s9_menu_product_ia.sql
-- ============================================================

-- ============================================================
-- s9_menu_product_ia.sql — 基础信息菜单信息架构优化（可移植版）
-- ============================================================
-- 用途：消除「商品库 / 商品信息 / 客户商品」命名歧义，
--       按"客户 → 品类 → 商品 → 规格 → 客户适配"的数据流向排列菜单。
--
-- ★ 可移植性说明：
--   本脚本【不依赖 menu_id】，全部通过 parent_id=4（基础信息目录）
--   + component 组件路径 定位行，可直接在其他基于 RuoYi 表结构的
--   项目上执行（前提：基础信息目录 parent_id=4、组件路径一致）。
--
-- 变更内容：
--   1. 「商品信息」（product/sku/index）更名「商品规格」
--   2. 基础信息(parent_id=4)下菜单统一重排 order_num：
--        1 客户信息          （往来单位放最前）
--        2 商品分类
--        3 商品库            （商品本体：叫什么/归属哪个分类）
--        4 商品规格          （可售单元：规格/单位/售价）
--        5 客户商品          （客户可购范围 + 别名/起订量）
--        6 别名与映射        （客户叫法 ↔ 我方SKU 映射）
--        7 默认SKU模板
--        8 报价模板
--        9 配送点覆盖
--       10 商品报价
--
-- 执行前建议备份 sys_menu；执行后需重新登录刷新菜单缓存。
-- ============================================================

-- 1. 更名（按组件路径定位，避免依赖 menu_id）
UPDATE `sys_menu`
SET `menu_name` = '商品规格',
    `remark`    = '商品可售规格/单位（SKU）'
WHERE `parent_id` = 4
  AND `component` = 'product/sku/index'
  AND `menu_type` = 'C';

-- 2. 统一排序（按组件路径定位）
UPDATE `sys_menu` SET `order_num` = 1  WHERE `parent_id` = 4 AND `component` = 'partner/customer/index'      AND `menu_type` = 'C'; -- 客户信息
UPDATE `sys_menu` SET `order_num` = 2  WHERE `parent_id` = 4 AND `component` = 'product/category/index'      AND `menu_type` = 'C'; -- 商品分类
UPDATE `sys_menu` SET `order_num` = 3  WHERE `parent_id` = 4 AND `component` = 'product/spu/index'           AND `menu_type` = 'C'; -- 商品库
UPDATE `sys_menu` SET `order_num` = 4  WHERE `parent_id` = 4 AND `component` = 'product/sku/index'           AND `menu_type` = 'C'; -- 商品规格
UPDATE `sys_menu` SET `order_num` = 5  WHERE `parent_id` = 4 AND `component` = 'product/customerSku/index'   AND `menu_type` = 'C'; -- 客户商品
UPDATE `sys_menu` SET `order_num` = 6  WHERE `parent_id` = 4 AND `component` = 'product/aliasMapping/index'  AND `menu_type` = 'C'; -- 别名与映射
UPDATE `sys_menu` SET `order_num` = 7  WHERE `parent_id` = 4 AND `component` = 'product/defaultSkuTemplate/index' AND `menu_type` = 'C'; -- 默认SKU模板
UPDATE `sys_menu` SET `order_num` = 8  WHERE `parent_id` = 4 AND `component` = 'price/template/index'        AND `menu_type` = 'C'; -- 报价模板
UPDATE `sys_menu` SET `order_num` = 9  WHERE `parent_id` = 4 AND `component` = 'price/pointPrice/index'      AND `menu_type` = 'C'; -- 配送点覆盖
UPDATE `sys_menu` SET `order_num` = 10 WHERE `parent_id` = 4 AND `component` = 'product/quote/index'         AND `menu_type` = 'C'; -- 商品报价

-- 3. 验证（执行后检查目标顺序）
-- SELECT menu_id, menu_name, order_num, component
-- FROM sys_menu WHERE parent_id = 4 AND menu_type = 'C'
-- ORDER BY order_num;

-- ============================================================
-- [18] Phase5 权限/字典一致性修复  | 源: s9_phase5_perms_dict.sql
-- ============================================================

-- ============================================================
-- S9 Phase5：权限/字典一致性修复（方案 §3.6）
-- 1) 配送点页权限 partner:customerDept:*（补 sys_menu 权限项，供角色分配）
-- 2) 客户类型字典 t_customer_type 补充字典数据（此前只有 dict_type 无 dict_data）
-- 3) 清理 price:point:* 旧权限残留（已被 price:delivery-override:* 取代）
-- menu_id 从 2100 起（当前最大 2099）；均为幂等写法，可重复执行
-- ============================================================

-- ---------- 1. 配送点页权限（挂载到"客户信息" menu_id=2012 下） ----------
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2100, '配送点查询', 2012, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:query', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2101, '配送点新增', 2012, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:add', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2102, '配送点修改', 2012, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:edit', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2103, '配送点删除', 2012, 8, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2104, '配送点导出', 2012, 9, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:export', '#', 'admin', sysdate(), '', NULL, '');

-- partner:customerDept:list（列表接口权限，供角色分配；配送点页为隐藏路由，无独立 C 菜单）
-- 说明：配送点页入口走 /basicInfo/customer-dept（partner:customer:list），此处补 list 便于角色细粒度授权
INSERT IGNORE INTO `sys_menu`
(`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES (2105, '配送点列表', 2012, 10, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'partner:customerDept:list', '#', 'admin', sysdate(), '', NULL, '');

-- ---------- 2. 客户类型字典 t_customer_type 补充数据 ----------
-- 若已存在该 dict_type+dict_value 则跳过
INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 1, '餐馆', '1', 't_customer_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '1');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 2, '食堂', '2', 't_customer_type', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '2');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 3, '商超', '3', 't_customer_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '3');

INSERT INTO `sys_dict_data`
(`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 4, '商户', '4', 't_customer_type', '', 'info', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_customer_type' AND `dict_value` = '4');

-- ---------- 3. 清理 price:point:* 旧权限残留（已被 price:delivery-override:* 取代） ----------
DELETE FROM `sys_menu` WHERE `perms` LIKE 'price:point:%';

-- ============================================================
-- [19] 客户类型字典补充工厂/酒店  | 源: s10_customer_type_add_factory_hotel.sql
-- ============================================================

-- s10_customer_type_add_factory_hotel.sql
-- 客户类型字典 t_customer_type 补充「工厂」「酒店」（2026 业务确认）
-- 背景：实际业务中工厂客户通常约 3 个配送点，酒店客户通常约 5 个配送点（可能含员工食堂，
--       菜品与价格独立），此为经验值而非硬规则，仅用于归类与统计。
-- 幂等：已存在同 dict_value 时跳过。执行后需重新登录刷新字典缓存。

INSERT INTO sys_dict_data
(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 5, '工厂', '5', 't_customer_type', '', 'danger', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 't_customer_type' AND dict_value = '5');

INSERT INTO sys_dict_data
(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 6, '酒店', '6', 't_customer_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', NULL, ''
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 't_customer_type' AND dict_value = '6');

-- ============================================================
-- [20] 客户商品池配送点限定（白名单模型）  | 源: s11_customer_sku_dept_scoping.sql
-- ============================================================

-- s11_customer_sku_dept_scoping.sql
-- 客户商品池增加配送点限定（白名单模型），替代并废弃 delivery_sku_override
--
-- 背景（2026-02 业务确认）：
--   员工食堂等场景是「不同 SKU 归属不同配送点」，不是同一 SKU 不同价。
--   价格差异通过同 SPU 下拆分不同 SKU 解决，报价引擎保持客户级不动。
-- 模型：
--   customers_sku.dept_id 为空     → 客户通用商品（所有配送点可见）
--   customers_sku.dept_id = 配送点 → 仅该配送点可见
--   下单选品范围 = 通用池 ∪ 本点专属池；不支持"全局可见但某点排除"
-- 废弃：
--   delivery_sku_override 表从未投入使用，直接删除；
--   「配送点覆盖」菜单下线。

-- 1) customers_sku 增加 dept_id（幂等：存在即跳过）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers_sku' AND COLUMN_NAME = 'dept_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `customers_sku` ADD COLUMN `dept_id` BIGINT NULL COMMENT ''限定配送点ID(t_customer_dept.id)，空=客户通用'' AFTER `sku_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 索引（幂等）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers_sku' AND INDEX_NAME = 'idx_customer_dept');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `customers_sku` ADD INDEX `idx_customer_dept` (`customer_id`, `dept_id`)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 删除未使用的配送点覆盖表
DROP TABLE IF EXISTS `delivery_sku_override`;

-- 4) 下线「配送点覆盖」菜单及按钮权限
DELETE FROM sys_menu WHERE component = 'price/pointPrice/index';
DELETE FROM sys_menu WHERE perms LIKE 'price:delivery-override:%';

-- ============================================================
-- [21] 订单页实收与验收 + 录单页后端草稿  | 源: s12_order_acceptance_actual.sql
-- ============================================================

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

-- [22] 菜单/权限：工作台  | 源: s3_2_workbench_menu.sql
-- ============================================================

-- ============================================================
-- S3-2 工作台菜单（一级菜单，置顶）
-- ============================================================
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2073, '工作台', 0, 1, 'workbench', 'workbench/index', NULL, '', 1, 0, 'C', '0', '0', '', 'dashboard', 'admin', sysdate(), '', NULL, '文员工作台');

-- ============================================================
-- [23] 菜单/权限：订单调整  | 源: s3_4_adjustment_menu.sql
-- ============================================================

-- ============================================================
-- S3-4 菜单/权限：订单调整（配送后加退换）
-- F 按钮挂在"销售订单"(menu_id=2030) 下
-- ============================================================

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2074, '订单调整', 2030, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:sale:adjust', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [24] 菜单/权限：采购管理  | 源: s4_purchase_menu.sql
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
-- [25] 送货单表结构适配（配送点维度、商品合并）  | 源: s5_1_delivery_alter.sql
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
-- [26] 菜单/权限：送货单打印、送达  | 源: s5_1_delivery_menu.sql
-- ============================================================

-- ============================================================
-- S5-1 菜单/权限：送货单打印、送达按钮
-- F 按钮挂在"送货单据"(menu_id=2036) 下
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2079, '送货单打印', 2036, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:print', '#', 'admin', sysdate(), '', NULL, '送货单打印（print_count+1）'),
(2080, '送货单送达', 2036, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:deliver', '#', 'admin', sysdate(), '', NULL, '送货单送达（订单→DELIVERED）');

-- ============================================================
-- [27] 菜单/权限：验收 + 验收状态字典  | 源: s5_2_acceptance_menu.sql
-- ============================================================

-- ============================================================
-- S5-2 验收：菜单/按钮权限 + 验收状态字典
-- C 菜单挂在"单据管理"(menu_id=5) 下；按钮 2082-2086
-- ============================================================

-- 验收单状态字典（0草稿 1已提交）
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
VALUES ('验收单状态', 't_acceptance_status', '0', 'admin', sysdate(), '', NULL, '验收单状态');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark) VALUES
(0, '草稿',   '0', 't_acceptance_status', '', 'info',    'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-草稿'),
(1, '已提交', '1', 't_acceptance_status', '', 'success', 'N', '0', 'admin', sysdate(), '', NULL, '验收单状态-已提交');

-- 菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2081, '验收单', 5, 3, 'acceptance', 'order/acceptance/index', NULL, '', 1, 0, 'C', '1', '1', 'acceptance:list', 'post', 'admin', sysdate(), '', NULL, '已下线：验收改在销售订单页完成（见 s12）');

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark) VALUES
(2082, '验收单查询', 2081, 1, '#', '', NULL, '', 1, 0, 'F', '1', '1', 'acceptance:query', '#', 'admin', sysdate(), '', NULL, ''),
(2083, '验收单新增', 2081, 2, '#', '', NULL, '', 1, 0, 'F', '1', '1', 'acceptance:add', '#', 'admin', sysdate(), '', NULL, ''),
(2084, '验收单修改', 2081, 3, '#', '', NULL, '', 1, 0, 'F', '1', '1', 'acceptance:edit', '#', 'admin', sysdate(), '', NULL, ''),
(2085, '验收单删除', 2081, 4, '#', '', NULL, '', 1, 0, 'F', '1', '1', 'acceptance:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2086, '验收单提交', 2081, 5, '#', '', NULL, '', 1, 0, 'F', '1', '1', 'acceptance:submit', '#', 'admin', sysdate(), '', NULL, '');

-- ============================================================
-- [28] 菜单/权限：报表中心  | 源: s6_1_report_menu.sql
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
-- [29] S14 订单-送货-验收链路重构（三层模型地基）  | 源: s14_delivery_acceptance_redesign.sql
-- 注意: 历史增量（r1/s7~s12）已全部合并入本文件 [08]~[21] 节；全量重建已在测试库验证 0 错误（2026-08-28）
-- ============================================================

-- ============================================================
-- S14：订单-送货-验收链路重构（三层模型地基）
-- 依据: docs/01-design/订单-送货-验收链路详细设计.md v1.2 §三（2026-08-28 定稿）
--
-- 内容:
--   1. 新表: t_delivery_batch / t_delivery_source_item / t_acceptance_revoke_log
--           t_return_order + t_return_item / t_job_run_log
--   2. 存量表: t_delivery_order 加批次/补单/作废维度; t_delivery_order_detail 去掉 order_id 唯一键(G5);
--             acceptance 加撤回字段; acceptance_item 更名 loss_quantity→difference_quantity + 加点级归属/差异原因类型;
--             t_customer 加组单策略两列
--   3. 字典: 4 个新字典 + 送货单状态补"已作废(3)"
--   4. 菜单/权限: 退货单菜单、送货单作废/补生成按钮、验收撤销按钮、验收单菜单恢复可见
--
-- 幂等: 全部可重复执行（CREATE IF NOT EXISTS / information_schema 探测 / WHERE NOT EXISTS）
-- 命名: 设计稿写 t_acceptance / t_acceptance_item，实际库表为 acceptance / acceptance_item
--       （无 t_ 前缀，s0_2_table_baseline.sql 建立），本脚本按实际表名执行。
-- 编号: TH(退货) 序列无需预插——BizCodeServiceImpl.nextSeq 首次使用时自动建行。
-- ============================================================


-- ============================================================
-- 1. 新表
-- ============================================================

-- ---------- 1.1 t_delivery_batch 客户每日配送批次（第一层，客户+日期唯一，策略快照） ----------
CREATE TABLE IF NOT EXISTS `t_delivery_batch` (
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `customer_id`     bigint unsigned NOT NULL COMMENT '客户ID',
    `delivery_date`   date NOT NULL COMMENT '配送日期',
    `scope_type`      varchar(32) NOT NULL DEFAULT 'DELIVERY_POINT_DATE' COMMENT '组单策略快照:CUSTOMER_DATE/DELIVERY_POINT_DATE',
    `merge_same_item` tinyint(1) NOT NULL DEFAULT 1 COMMENT '跨订单/跨点相同商品合并快照',
    `template_id`     bigint unsigned DEFAULT NULL COMMENT '模板绑定快照(打印层)',
    `layout_json`     varchar(500) DEFAULT NULL COMMENT '布局参数快照 column_count/rows_per_column',
    `status`          tinyint NOT NULL DEFAULT 0 COMMENT '0有效 1关闭(当日确认不再生成)',
    `version`         int unsigned NOT NULL DEFAULT 0,
    `is_deleted`      tinyint(1) NOT NULL DEFAULT 0,
    `create_by` varchar(64) DEFAULT '', `create_time` datetime,
    `update_by` varchar(64) DEFAULT '', `update_time` datetime,
    `remark`        varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_customer_date` (`customer_id`, `delivery_date`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户每日配送批次(内部配货边界)';

-- ---------- 1.2 t_delivery_source_item 送货来源分配（P0 核心：订单行→送货行，应送台账） ----------
CREATE TABLE IF NOT EXISTS `t_delivery_source_item` (
    `id`                     bigint unsigned NOT NULL AUTO_INCREMENT,
    `delivery_id`            bigint unsigned NOT NULL COMMENT '送货单ID',
    `delivery_detail_id`     bigint unsigned NOT NULL COMMENT '聚合送货行ID',
    `sale_order_id`          bigint unsigned NOT NULL COMMENT '来源销售订单',
    `sale_order_detail_id`   bigint unsigned NOT NULL COMMENT '来源订单行',
    `customer_dept_id`       bigint unsigned NOT NULL COMMENT '来源配送点(追溯/差异归属)',
    `sku_id`                 bigint unsigned DEFAULT NULL,
    `product_name`           varchar(200) NOT NULL COMMENT '来源行品名快照',
    `allocated_quantity`     decimal(10,2) NOT NULL COMMENT '本订单行分配到该送货行的数量',
    `unit_price`             decimal(10,2) NOT NULL DEFAULT 0 COMMENT '来源行单价快照(成本归属)',
    `is_deleted`             tinyint(1) NOT NULL DEFAULT 0,
    `create_time`            datetime,
    `create_by`              varchar(64) DEFAULT '',
    PRIMARY KEY (`id`),
    -- 近期唯一：一张订单行只能进入一次有效送货（D-005）；作废软删后重建不撞键
    UNIQUE KEY `unq_sale_order_detail` (`sale_order_detail_id`, `is_deleted`),
    KEY `idx_delivery` (`delivery_id`),
    KEY `idx_delivery_detail` (`delivery_detail_id`),
    KEY `idx_sale_order` (`sale_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送货来源明细(订单行→送货行分配)';

-- ---------- 1.3 t_acceptance_revoke_log 验收撤回审计（Q16/D-014） ----------
CREATE TABLE IF NOT EXISTS `t_acceptance_revoke_log` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `acceptance_id` bigint unsigned NOT NULL,
    `snapshot_json` longtext NOT NULL COMMENT '撤回前主表+明细完整JSON',
    `reason`        varchar(200) NOT NULL,
    `revoked_by`    varchar(64) NOT NULL,
    `revoked_time`  datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_acceptance` (`acceptance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验收撤回审计';

-- ---------- 1.4 t_return_order / t_return_item 退货单（D-032/D-034：独立于验收，不撤回历史验收） ----------
CREATE TABLE IF NOT EXISTS `t_return_order` (
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT,
    `code`              varchar(32) NOT NULL COMMENT 'THyyyyMMddNNN',
    `customer_id`       bigint unsigned NOT NULL,
    `customer_dept_id`  bigint unsigned DEFAULT NULL,
    `delivery_id`       bigint unsigned NOT NULL COMMENT '原送货单',
    `acceptance_id`     bigint unsigned NOT NULL COMMENT '原验收单(退货单价来源)',
    `return_date`       date NOT NULL,
    `total_amount`      decimal(12,2) NOT NULL DEFAULT 0 COMMENT '合计退货金额(负项入对账)',
    `status`            tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 1已提交(质检中) 2质检完成 3已完成',
    `inspected_by`      varchar(64) DEFAULT NULL COMMENT '质检处理人',
    `inspected_time`    datetime DEFAULT NULL,
    `settle_scope`      tinyint NOT NULL DEFAULT 0 COMMENT '0结算前当期冲销 1结算后下期冲销(提交时计算快照)',
    `version`           int unsigned NOT NULL DEFAULT 0,
    `is_deleted`        tinyint(1) NOT NULL DEFAULT 0,
    `create_by`         varchar(64) DEFAULT '', `create_time` datetime,
    `update_by`         varchar(64) DEFAULT '', `update_time` datetime,
    `remark`            varchar(500),
    PRIMARY KEY (`id`),
    UNIQUE KEY `unq_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货单(独立于验收,不撤回历史验收)';

CREATE TABLE IF NOT EXISTS `t_return_item` (
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT,
    `return_id`          bigint unsigned NOT NULL,
    `acceptance_item_id` bigint unsigned NOT NULL COMMENT '来源验收明细行',
    `sku_id`             bigint unsigned DEFAULT NULL,
    `product_name`       varchar(200) NOT NULL,
    `product_spec`       varchar(200),
    `product_unit`       varchar(50),
    `return_quantity`    decimal(10,2) NOT NULL COMMENT '退货数量<=实收-已退',
    `unit_price`         decimal(10,2) NOT NULL COMMENT '=原验收单价(不可改)',
    `amount`             decimal(12,2) NOT NULL,
    `quality_result`     tinyint DEFAULT NULL COMMENT '质检:1可再售(入库) 2不可再售(报损)',
    `quality_note`       varchar(255),
    PRIMARY KEY (`id`),
    KEY `idx_return` (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货明细(数量上限=实收-累计已退)';

-- ---------- 1.5 t_job_run_log 定时任务运行记录（Q36/D-037 工作台告警数据源） ----------
CREATE TABLE IF NOT EXISTS `t_job_run_log` (
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `job_name`      varchar(64) NOT NULL COMMENT 'DELIVERY_GENERATE 等',
    `biz_date`      date NOT NULL,
    `status`        tinyint NOT NULL COMMENT '0成功 1失败 2部分失败(遗漏订单)',
    `message`       varchar(500) DEFAULT NULL COMMENT '异常摘要/遗漏提示',
    `warning_count` int NOT NULL DEFAULT 0 COMMENT '遗漏订单数',
    `run_time`      datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_job_date` (`job_name`,`biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务运行记录(工作台告警)';

-- ============================================================
-- 2. 存量表变更（幂等 ALTER：information_schema 探测 + PREPARE）
-- ============================================================

-- ---------- 2.1 t_delivery_order：批次/策略/补单/作废维度 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_delivery_order' AND COLUMN_NAME = 'batch_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_delivery_order`
        ADD COLUMN `batch_id`       bigint unsigned DEFAULT NULL COMMENT ''所属配送批次'' AFTER `delivery_point_id`,
        ADD COLUMN `scope_type`     varchar(32)  DEFAULT ''DELIVERY_POINT_DATE'' COMMENT ''本单组单范围快照(总单时=CUSTOMER_DATE)'' AFTER `batch_id`,
        ADD COLUMN `doc_kind`       tinyint      NOT NULL DEFAULT 0 COMMENT ''0正常单 1补充单(遗漏订单单独成单)'' AFTER `scope_type`,
        ADD COLUMN `predecessor_id` bigint unsigned DEFAULT NULL COMMENT ''作废重建来源单ID'' AFTER `doc_kind`,
        ADD COLUMN `void_reason`    varchar(200) DEFAULT NULL COMMENT ''作废原因'',
        ADD COLUMN `void_by`        varchar(64)  DEFAULT NULL COMMENT ''作废人'',
        ADD COLUMN `void_time`      datetime     DEFAULT NULL COMMENT ''作废时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.2 t_delivery_order_detail：去掉 order_id 唯一键（G5 结构性修复） ----------
-- order_id 列保留仅供历史行查询；s5_1 若已删除则本步自动跳过
SET @idx_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_delivery_order_detail'
      AND INDEX_NAME = 'unq_order_id'
);
SET @ddl := IF(@idx_exists > 0,
    'ALTER TABLE `t_delivery_order_detail` DROP INDEX `unq_order_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.3 acceptance：撤回字段 ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance' AND COLUMN_NAME = 'revoke_reason'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance`
        ADD COLUMN `revoke_reason` varchar(200) DEFAULT NULL COMMENT ''撤回原因'',
        ADD COLUMN `revoked_by`    varchar(64)  DEFAULT NULL COMMENT ''撤回人'',
        ADD COLUMN `revoked_time`  datetime     DEFAULT NULL COMMENT ''撤回时间''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.4 acceptance_item：更名 loss_quantity→difference_quantity + 点级归属/差异原因类型 ----------
-- 更名（仅当旧列存在且新列不存在）
SET @old_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'loss_quantity'
);
SET @new_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'difference_quantity'
);
SET @ddl := IF(@old_exists = 1 AND @new_exists = 0,
    'ALTER TABLE `acceptance_item`
        CHANGE COLUMN `loss_quantity` `difference_quantity` decimal(10,2) DEFAULT NULL COMMENT ''验收差异=实收-送货(正超收/负短收)''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 新列（一次探测一列）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'customer_dept_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance_item`
        ADD COLUMN `customer_dept_id` bigint unsigned DEFAULT NULL COMMENT ''明细所属配送点(A类总单按点展开,B/C类也填)'' AFTER `delivery_item_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acceptance_item' AND COLUMN_NAME = 'reason_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `acceptance_item`
        ADD COLUMN `reason_type` tinyint DEFAULT NULL COMMENT ''差异原因类型:1短收(acceptance_shortfall_reason) 2超收(acceptance_overage_reason)'' AFTER `loss_reason`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2.5 t_customer：组单策略（客户级，D-015 配送点不覆盖） ----------
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_customer' AND COLUMN_NAME = 'doc_scope_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `t_customer`
        ADD COLUMN `doc_scope_type`      varchar(32) NOT NULL DEFAULT ''DELIVERY_POINT_DATE'' COMMENT ''组单策略:CUSTOMER_DATE/DELIVERY_POINT_DATE'',
        ADD COLUMN `doc_merge_same_item` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''相同商品合并成行''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 字典
-- ============================================================

-- ---------- 3.1 delivery_no_print_reason 未打印送达原因（Q18/D-018） ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '送货未打印送达原因', 'delivery_no_print_reason', '0', 'admin', sysdate(), '送货单未打印直接送达时登记（免纸/电子单据/录单补登/设备故障/其他）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'delivery_no_print_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '客户免纸', 'customer_paperless', 'delivery_no_print_reason', '', 'info', 'N', '0', 'admin', sysdate(), '客户无需纸质单据'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'customer_paperless');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '已发电子单据', 'electronic', 'delivery_no_print_reason', '', 'primary', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'electronic');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '录单补登', 'rework', 'delivery_no_print_reason', '', 'warning', 'N', '0', 'admin', sysdate(), '后续回补打印/补录'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'rework');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '设备故障', 'device_fault', 'delivery_no_print_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'device_fault');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '其他', 'other', 'delivery_no_print_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_no_print_reason' AND `dict_value` = 'other');

-- ---------- 3.2 acceptance_shortfall_reason 短收原因 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '验收短收原因', 'acceptance_shortfall_reason', '0', 'admin', sysdate(), '实收<送货（差异原因类型1）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'acceptance_shortfall_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '缺货', 'shortage', 'acceptance_shortfall_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'shortage');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '拒收', 'refuse', 'acceptance_shortfall_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'refuse');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '损耗', 'spoilage', 'acceptance_shortfall_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'spoilage');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '质量问题', 'quality', 'acceptance_shortfall_reason', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'quality');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '错送', 'wrong_send', 'acceptance_shortfall_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'wrong_send');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 6, '其他', 'other', 'acceptance_shortfall_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_shortfall_reason' AND `dict_value` = 'other');

-- ---------- 3.3 acceptance_overage_reason 超收原因 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '验收超收原因', 'acceptance_overage_reason', '0', 'admin', sysdate(), '实收>送货（差异原因类型2）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'acceptance_overage_reason');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '临时加送', 'temp_add', 'acceptance_overage_reason', '', 'primary', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'temp_add');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '计量差异', 'scale_diff', 'acceptance_overage_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'scale_diff');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '录单遗漏', 'order_miss', 'acceptance_overage_reason', '', 'warning', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'order_miss');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '其他', 'other', 'acceptance_overage_reason', '', 'info', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'acceptance_overage_reason' AND `dict_value` = 'other');

-- ---------- 3.4 return_quality_result 退货质检结果 ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '退货质检结果', 'return_quality_result', '0', 'admin', sysdate(), '退货单质检：可再售入库/不可再售报损'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'return_quality_result');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '可再售(入库)', 'reusable', 'return_quality_result', '', 'success', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = 'reusable');
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '不可再售(报损)', 'damaged', 'return_quality_result', '', 'danger', 'N', '0', 'admin', sysdate(), ''
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'return_quality_result' AND `dict_value` = 'damaged');

-- ---------- 3.5 送货单状态字典补"已作废(3)"（dict_type 107 = t_delivery_order_status） ----------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '已作废', '3', 't_delivery_order_status', '', 'danger', 'N', '0', 'admin', sysdate(), '送货单作废后状态，来源分配已释放'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 't_delivery_order_status' AND `dict_value` = '3');

-- ============================================================
-- 4. 菜单/权限（portable：不写死 menu_id，按 component 定位父菜单）
-- 说明：如需给非 admin 角色授权，请在"系统管理-角色管理"中勾选新菜单
-- ============================================================

-- ---------- 4.1 退货单菜单（挂在验收单同级 = 订单管理目录下） ----------
SET @order_parent := (SELECT parent_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单', @order_parent, 4, 'return', 'order/return/index', NULL, '', 1, 0, 'C', '0', '0', 'return:list', 'refund', 'admin', sysdate(), '退货单菜单(独立于验收单,不撤回历史验收)'
WHERE @order_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'order/return/index');

SET @return_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/return/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单查询', @return_menu, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:query', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单新增', @return_menu, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:add', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单修改', @return_menu, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:edit', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单删除', @return_menu, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:remove', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单提交', @return_menu, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:submit', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:submit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '退货单质检', @return_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'return:inspect', '#', 'admin', sysdate(), ''
WHERE @return_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @return_menu AND perms = 'return:inspect');

-- ---------- 4.2 送货单：作废 / 按客户补生成 按钮 ----------
SET @delivery_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/delivery/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '送货单作废', @delivery_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:void', '#', 'admin', sysdate(), '作废未验收送货单并释放来源订单'
WHERE @delivery_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @delivery_menu AND perms = 'order:delivery:void');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '送货单补生成', @delivery_menu, 7, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'order:delivery:generateCustomer', '#', 'admin', sysdate(), '按客户补生成(遗漏订单补充单)'
WHERE @delivery_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @delivery_menu AND perms = 'order:delivery:generateCustomer');

-- ---------- 4.3 验收单：撤销按钮 + 菜单恢复可见（方案A 曾隐藏验收单入口） ----------
SET @acceptance_menu := (SELECT menu_id FROM sys_menu WHERE component = 'order/acceptance/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '验收单撤销', @acceptance_menu, 6, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'acceptance:revoke', '#', 'admin', sysdate(), '撤回已提交验收单(留审计快照,订单回到已配送)'
WHERE @acceptance_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @acceptance_menu AND perms = 'acceptance:revoke');

UPDATE sys_menu SET visible = '0', status = '0'
WHERE component = 'order/acceptance/index' AND (visible <> '0' OR status <> '0');
UPDATE sys_menu SET visible = '0', status = '0'
WHERE menu_type = 'F' AND parent_id = @acceptance_menu AND (visible <> '0' OR status <> '0');

-- ============================================================
-- 5. 存量数据回填（可选，默认注释）
-- ============================================================
-- 设计口径（详细设计 §三 + 附录·迁移与灰度）：t_delivery_source_item 只为近期单据回填，
-- 更早单据标"历史无来源"（验收页来源对照列显示"—"）。
-- 下述 SQL 为「昨日待处理单据近似回填」模板：按 order_id 命中的送货明细行，
-- 以同订单同商品行的 (sale_order_detail_id) 近似对应（合并行无法精确拆分，接受近似）。
-- 执行前请人工核对行数；仅对仍有效的送货单（status<>3 且 is_deleted=0）执行。
--
-- INSERT INTO t_delivery_source_item
--     (delivery_id, delivery_detail_id, sale_order_id, sale_order_detail_id, customer_dept_id,
--      sku_id, product_name, allocated_quantity, unit_price, is_deleted, create_time, create_by)
-- SELECT dod.order_id,
--        dod.id,
--        sod.order_id,
--        sod.id,
--        d.delivery_point_id,
--        dod.sku_id,
--        dod.product_name,
--        dod.quantity,
--        dod.unit_price,
--        0,
--        NOW(),
--        's14-backfill'
-- FROM t_delivery_order_detail dod
-- JOIN t_delivery_order d           ON d.id = dod.order_id AND d.is_deleted = 0
-- JOIN t_sale_order_detail sod      ON sod.order_id = (SELECT so.id FROM t_sale_order so WHERE ...) -- 模板：来源订单行匹配键按实际可追溯性确定
-- WHERE dod.order_id IS NOT NULL
--   AND d.delivery_date >= CURDATE() - INTERVAL 1 DAY;
--
-- ⚠️ 旧明细行的 order_id 唯一键曾被 s5_1 调整为 (order_id, sku_id) 联合唯一，
--    勾选生成的行可精确回填（每行 order_id → sale_order_detail.id 需按 sku+名称匹配）；
--    合并生成(s5_1 改造后)的行无法精确回填来源订单行——该部分按"历史无来源"处理。
--    正式回填方案在 T3 统一生成服务上线前由人工评估，此处仅留模板占位。


-- ============================================================
-- S14 结束
-- ============================================================

-- ============================================================
-- [30] S14/T4 送货单作废原因字典  | 源: s14c_delivery_void_reason.sql
-- ============================================================

-- ---------- 1. 字典类型 delivery_void_reason（送货单作废原因） ----------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT '送货单作废原因', 'delivery_void_reason', '0', 'admin', sysdate(), '送货单手工作废时登记（录单错误/重复生成/客户取消/缺货取消/其他；选其他必须填补充说明）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'delivery_void_reason');

-- ---------- 2. 字典数据 ----------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '录单错误', 'order_error', 'delivery_void_reason', '', 'warning', 'N', '0', 'admin', sysdate(), '订单录错（商品/数量/价格/配送点等）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'order_error');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '重复生成', 'duplicate', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '同一批订单重复出单，作废多余单据'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'duplicate');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, '客户取消', 'customer_cancel', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '客户取消当日订单/配送点'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'customer_cancel');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '缺货取消', 'out_of_stock', 'delivery_void_reason', '', 'danger', 'N', '0', 'admin', sysdate(), '商品缺货整单取消（部分缺货请改单而非作废）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'out_of_stock');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, '其他', 'other', 'delivery_void_reason', '', 'info', 'N', '0', 'admin', sysdate(), '其他原因，必须填写补充说明'
WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'delivery_void_reason' AND `dict_value` = 'other');

-- ============================================================
-- 初始化结束
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;
