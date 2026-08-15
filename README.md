# Fresh Distribution 生鲜配送管理系统

基于 RuoYi v3.8.8 框架开发的生鲜配送管理系统，采用 Spring Boot 3 + Vue 3 前后端分离架构。

## 技术栈

### 后端
| 技术 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.4.1 |
| MyBatis Plus | 3.5.9 |
| Druid | 1.2.23 |
| Redis / Redisson | 3.39.0 |
| JWT (JJWT) | 0.12.5 |
| Apache POI | 5.2.5 |
| Springdoc OpenAPI | 2.6.0 |
| MySQL Connector | 8.2.0 |

### 前端
| 技术 | 版本 |
|------|------|
| Vue | 3.5.12 |
| Vite | 5.4.10 |
| Element Plus | 2.8.1 |
| Pinia | 2.2.2 |
| Vue Router | 4.4.3 |
| Axios | 1.7.7 |
| ECharts | 5.5.1 |

## 模块结构

### 后端 (Java)
```
fresh-distribution/
├── lin-common          # 通用工具、异常、注解、Redis缓存、基础实体
├── lin-admin           # 后台管理控制器（系统/监控/工具）
├── lin-distribution    # 核心业务模块（客户/商品/报价/订单/送货单/打印）
├── lin-quartz          # Quartz 定时任务
├── lin-generator       # 代码生成器
├── lin-entry           # Spring Boot 启动入口
├── sql/                # 数据库初始化脚本
└── pom.xml             # 父 POM
```

### 前端 (Vue 3)
```
ruoyi-ui/
├── src/
│   ├── api/            # API 接口定义
│   ├── assets/         # 静态资源
│   ├── components/     # 公共组件
│   ├── directive/      # 自定义指令
│   ├── layout/         # 布局组件
│   ├── plugins/        # 插件
│   ├── router/         # 路由配置
│   ├── store/          # Pinia 状态管理
│   ├── utils/          # 工具函数
│   └── views/          # 页面视图
├── public/             # 公共资源
├── index.html          # Vite 入口 HTML
├── vite.config.js      # Vite 配置
└── package.json
```

## 核心业务功能

### 1. 客户管理
- 客户基本信息维护
- 客户部门管理（一对多）

### 2. 商品管理
- **商品分类** — 多级分类体系
- **商品 SPU** — 标准产品单元（名称、助记码、分类）
- **商品 SKU** — 库存量单位（按客户绑定价格、规格）

### 3. 报价管理
- 报价单创建（有效期控制）
- 报价明细（商品 SKU × 客户）
- 报价发布/失效管理
- 每日定时同步报价状态

### 4. 销售订单
- 订单创建（自动编号 XD + 日期 + 流水号）
- 订单编辑（仅限制单状态）
- 订单状态流转：**制单 → 审核 → 送货 → 验收 → 完成**
- 审核时自动创建送货单

### 5. 送货单
- 按客户 + 配送日期自动聚合
- 送货单详情管理
- 打印状态跟踪

### 6. 打印模板
- hiPrint 打印集成

## 快速开始

### 后端启动

1. 初始化数据库：
```bash
mysql -u root -p < sql/ry_20240629.sql
mysql -u root -p < sql/init_fresh_distribution.sql
```

2. 修改配置 `lin-entry/src/main/resources/application-local.yml` 中的数据库连接信息

3. 启动：
```bash
cd lin-entry
mvn spring-boot:run
```

或打包运行：
```bash
mvn clean package -DskipTests
java -jar lin-entry/target/lin-entry.jar
```

### 前端启动

```bash
cd ruoyi-ui
npm install
npm run dev
```

访问：http://localhost:80

## API 文档

启动后端后访问：http://localhost:8090/swagger-ui.html

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |

## 项目配置说明

### 环境变量

| 变量名 | 开发环境 | 生产环境 |
|--------|---------|---------|
| VUE_APP_BASE_API | /dev-api | /prod-api |
| VUE_APP_TITLE | 若依管理系统 | 若依管理系统 |

### 后端端口
- 默认端口：8090（开发）/ 8080（生产）
- 配置文件：`application-local.yml` / `application.yml`

## 依赖升级记录

| 日期 | 内容 |
|------|------|
| 2025-01 | Spring Boot 3.3.0 → 3.4.1 |
| 2025-01 | Apache POI 4.1.2 → 5.2.5 |
| 2025-01 | JJWT 0.9.1 → 0.12.5 (API变更) |
| 2025-01 | MyBatis Plus 3.5.7 → 3.5.9 |
| 2025-01 | Springdoc 2.5.0 → 2.6.0 |
| 2025-01 | Redisson 3.38.1 → 3.39.0 |
| 2025-01 | 移除 Springfox（不兼容 Spring Boot 3） |
| 2025-01 | JAXB javax → jakarta |
| 2025-01 | 前端 Vue 2 → Vue 3 + Vite + Element Plus |

## 已知问题 / 待办

- [ ] 各 Vue SFC 文件需手动适配 Vue 3 语法（v-model 语法、filters 移除等）
- [ ] 前端 Element Plus 组件 API 差异需逐页面检查
- [ ] `RuoYiConfig` 静态字段模式可考虑重构为实例字段
- [ ] 定时任务 `syncUpdateQuoteStatus` 考虑加分布式锁防止重复执行

## License

MIT