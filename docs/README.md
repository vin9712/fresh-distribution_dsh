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
