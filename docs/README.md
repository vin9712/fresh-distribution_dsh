# 文档导航

本项目全部文档按类别归档如下，各目录内的交叉引用已使用相对路径。

## 目录结构

```
docs/
├── 01-design/             # 设计与开发规范（权威文档链）
├── 02-redesign-r1/        # 基础信息模块重构（R1）设计与进度
├── 03-ui-optimization/    # 交互与 UI 优化
├── 04-manuals/            # 操作手册
└── assets/                # 截图、示例数据
```

## 01-design — 设计与开发规范

阅读顺序即优先级顺序：

| 文档 | 说明 |
|---|---|
| [DESIGN.md](01-design/DESIGN.md) | **唯一权威设计**，禁止 Agent 修改，变更设计须先经用户确认 |
| [../销售订单与送货单关系设计.md](../销售订单与送货单关系设计.md) | 订单→送货→验收业务关系基线（As-Is / To-Be，决策 D-001~D-037）|
| [订单-送货-验收链路详细设计.md](01-design/订单-送货-验收链路详细设计.md) | 链路详细设计：库表 / 服务 / 接口 / 页面改造与实施切片（依据上述基线） |
| [送货单客户维度设计.md](01-design/送货单客户维度设计.md) | **送货单客户维度专项设计**（D-038~D-042）：四层模型、生成三步式预览、客户管理页体现、group-preview 契约 |
| [送货单矩阵总表与批次视图设计.md](01-design/送货单矩阵总表与批次视图设计.md) | **矩阵总表与打印形态专项设计**（D-043~D-052）：批次两级视图、A 类矩阵总表（行=菜品×单位×单价 / 列=配送点快照含空列 / 格=分配量透视 + 恒等式校验）、打印形态与模板绑定加维、B/C 类针式打印包 |
| [客户日报表打印优化设计.md](01-design/客户日报表打印优化设计.md) | **打印模块优化设计**（PT-1~4）：bizKey 模板解析服务化（替代前端硬编码）、当日打印清单+批量打印队列、打印回执时机修正（开窗不登记，真实打印才登记）、票据批量签发 |
| [验收模块订单明细视角重构设计.md](01-design/验收模块订单明细视角重构设计.md) | **验收模块重构设计**（AC-1~7）：验收维度升到客户日（一客户日一验，应送行=订单明细跨点平铺）、明细视图订单明细化、去验收定位改订单视角、历史送货单路径退役为只读维护 |
| [订单-送货-验收链路开发进度.md](01-design/订单-送货-验收链路开发进度.md) | **进度看板**：决策浓缩 / 库表变更清单 / T1~T8 勾选清单 / 进度日志 |
| [DEVELOPMENT.md](01-design/DEVELOPMENT.md) | 开发文档：领域模型、表结构、接口契约、页面规划、实施阶段 |
| [agent-development-brief.md](01-design/agent-development-brief.md) | Agent 任务切片规则、验收协议与报告格式 |
| [DEV-ENV-NOTES.md](01-design/DEV-ENV-NOTES.md) | 开发环境已验证规则（Node/npm/git、编码陷阱、E2E 工具） |
| [architecture_redesign_generate_by_claude_code.md](01-design/architecture_redesign_generate_by_claude_code.md) | 架构约束补充（历史草稿，与 DESIGN.md 不一致处以 DESIGN.md 为准） |

## 02-redesign-r1 — 基础信息模块重构（R1）

| 文档 | 说明 |
|---|---|
| [deepseek_redesign.md](02-redesign-r1/deepseek_redesign.md) | 重构设计（最终目标），2026-08-21 已全部落地 |
| [r1_redesign_progress.md](02-redesign-r1/r1_redesign_progress.md) | 执行进度与全链路验证记录（含"下一步"章节） |
| [deepseek_ui_redesign.md](02-redesign-r1/deepseek_ui_redesign.md) | 前端交互设计指南 |
| [基础信息模块梳理.md](02-redesign-r1/基础信息模块梳理.md) | 重构前基线参考（旧结构梳理） |

配套 SQL：[sql/r1_basicinfo_redesign.sql](../sql/r1_basicinfo_redesign.sql)、[sql/r1_frontend_menu.sql](../sql/r1_frontend_menu.sql)

> 📌 **菜单迁移**：v1.2 对基础信息菜单做了更名（商品信息→商品规格）与排序调整，
> 迁移到其他项目时执行可移植脚本 [sql/s9_menu_product_ia.sql](../sql/s9_menu_product_ia.sql)
> （按 parent_id=4 + component 路径定位，不依赖 menu_id）。

## 03-ui-optimization — 交互与 UI 优化

| 文档 | 说明 |
|---|---|
| [ui-interaction-spec.md](03-ui-optimization/ui-interaction-spec.md) | 模块 UI 交互规格（基于现有代码现状，按菜单树逐模块描述） |
| [生鲜配送 ERP 交互优化方案.md](03-ui-optimization/生鲜配送%20ERP%20交互优化方案.md) | 交互优化方案（P0~P4 优先级） |
| [交互优化开发计划.md](03-ui-optimization/交互优化开发计划.md) | 分阶段开发计划 |

## 04-manuals — 手册

| 文档 | 说明 |
|---|---|
| [操作手册.md](04-manuals/操作手册.md) | 面向管理员/业务员/文员/仓配人员的系统操作手册（配截图） |

## assets — 资源

- [`截图/`](assets/)：操作手册插图
- [`报价demo.csv`](assets/报价demo.csv)：商品价格 demo 数据（`.dsh-e2e/gen-product-init.js` 使用）

## 相关目录

- [deploy/DEPLOY.md](../deploy/DEPLOY.md)：部署手册（随部署脚本放在 [deploy/](../deploy/)）
