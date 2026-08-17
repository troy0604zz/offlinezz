# 晶圆代工 Foundry 360 综合测试数据包

该数据包全部为确定性生成的虚构数据，不包含真实客户、产品或人员信息。它用于测试 Product 360、Customer 360、Application 360、复杂 NL2SQL、多事实表防放大、RAG 检索、Semantic Layer、Few-shot、黄金评测和智能报告。

为了兼容当前数据问答页面，训练资产默认发布到 `sales` 数据域；所有数据库对象使用 `f360_` 前缀，与原销售演示数据隔离。

## 资产规模

- 25 张 Oracle 19c 业务表。
- 6 个区域、8 个客户、10 个应用、6 个工艺节点、4 个晶圆厂、6 条制程路线、12 个产品。
- 2025-01 至 2026-06 的订单、投片、产出、良率、出货、产能、成本和库存数据，共 2359 行业务数据。
- 30 多个业务指标、39 条表关系、60 个同义词映射。
- 24 条复杂标准 SQL，并自动生成同问题黄金评测。
- 40 条综合测试问题与 23 组复杂报告/追问场景。
- 9 份可上传到知识库的业务文档。

## 文件结构

```text
foundry360/
├── sql/
│   ├── 01_ddl.sql              建表、约束、索引、注释
│   ├── 02_seed_dimensions.sql  维表和主数据
│   ├── 03_seed_facts.sql       事实数据
│   └── 04_acceptance_queries.sql 数据量与口径验收
├── knowledge/                  RAG 知识文档
├── semantic/
│   ├── metrics.yaml            指标口径
│   ├── relations.yaml          Join 路径与基数
│   └── synonyms.csv            中文、英文、缩写和口语映射
├── sample_sql/few_shot.jsonl   Oracle 标准 SQL
├── golden_questions.jsonl      自动、歧义、安全与放大测试清单
├── report_prompts.md           复杂 360 报告题
└── 05_import_training_assets.ps1 训练中心幂等导入脚本
```

## 装载顺序

在 AI BI 应用使用的 Oracle Schema 中，以 UTF-8 会话依次执行：

1. `sql/01_ddl.sql`
2. `sql/02_seed_dimensions.sql`
3. `sql/03_seed_facts.sql`
4. `sql/04_acceptance_queries.sql`

然后启动应用并执行训练资产导入：

```powershell
.\test-data\foundry360\05_import_training_assets.ps1
```

脚本会提示输入 AI 管理员密码，并跳过已经存在的同名资产。它会完成：按表登记 25 个 Schema、指标发布、关系发布、同义词发布、标准 SQL 发布、标准 SQL 对应黄金问题创建，以及 9 份知识文档上传和向量化。DDL 按表拆分是为了避免把整份大型 DDL 作为一次超长 Embedding 请求。

如果从其他机器访问应用：

```powershell
.\test-data\foundry360\05_import_training_assets.ps1 -ApiBase 'http://服务器地址:8080/api/v1'
```

## 推荐验收顺序

1. 先使用 `few_shot.jsonl` 中完全相同的问题验证标准 SQL 命中和 Oracle 执行。
2. 再改写问法，验证同义词和相似 SQL 检索。
3. 再使用 `golden_questions.jsonl` 中 JOIN、快照、歧义和安全类问题。
4. 最后使用 `report_prompts.md` 测试多章节报告。

注意：当前智能报告实现仍是 MVP，报告题可以用于暴露多查询规划、跨事实聚合、追问上下文和图表选择方面的后续改进点。
