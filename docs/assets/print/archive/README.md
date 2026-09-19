# 归档：静态样板源文件（P1 已退役）

这些 `*-source.json` 是各客户原始纸面样张（Excel 公式 + 静态数量/颜色），
曾用于 `JimuSampleImporter` 导入为「静态样板报表」做版式对照。

**P1（打印模块重构）已退役静态样板导入**：
- 版式改由 `PrintTemplateGenerator` 从**当前数据契约 + 客户实际结构**动态生成；
- 数据由 `/print/**` 经数据契约实时取，不再掺入静态数量；
- 保留本目录仅作历史纸面版式参考，**不再参与任何导入/打印链路**。

动态总单设计（`docs/assets/print/hotels/*-matrix-sheet.json`）与导入记录仍保留在上级目录。
