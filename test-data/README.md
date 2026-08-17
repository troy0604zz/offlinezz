# 验证数据集说明

该目录用于验证文档摄取、语义检索、多表 NL2SQL、SQL 安全、图表和报告生成。推荐按以下顺序操作：

1. Mock 模式启动后端，Flyway 会自动建立同结构的 H2 演示库。
2. 将 `knowledge/` 下文档逐个上传到知识中心，数据域选择 `sales`。
3. 使用 `golden_questions.jsonl` 中的问题验证，比较 SQL 涉及表、关键过滤条件与预期结果。
4. 真实 Oracle 19c 验证时，以 UTF-8/AL32UTF8 会话依次执行 `sql/01_ddl.sql`、`sql/02_seed.sql`。

注意：演示数据完全虚构，不含真实客户信息。

## 晶圆代工 Foundry 360 综合数据

`foundry360/` 是更大规模的 Oracle 19c 测试包，包含 Product 360、Customer 360、Application 360、制造、产能、良率、质量、交付、成本、库存、NPI 和 Design Win。它提供 25 张表、2359 行业务数据及训练中心一键导入脚本。详细装载顺序见 `foundry360/README.md`。
