# Application 360 业务说明

Application 360 用于回答“收入来自哪些终端应用、增长机会在哪里、产品与制程布局是否匹配”等问题。主表为 `f360_application`。

## 关键维度

- `market_segment`：AUTOMOTIVE、MOBILE、HPC、IOT、INDUSTRIAL、NETWORKING、MEDICAL、CONSUMER。
- `end_product`：实际终端产品，例如新能源汽车、数据中心服务器、医学影像设备。
- `functional_domain`：芯片在终端中的功能，例如 ADAS、BMS、AI_ACCELERATOR。
- `safety_grade`：车规、工业或医疗安全等级。空值代表不适用，不代表未知质量等级。
- `lifecycle_stage`：应用市场的导入、成长或成熟阶段。

## 市场口径

- TAM：全球理论总市场，对应 `global_tam_usd`。
- SAM：本代工厂当前技术与商务能力可服务市场，对应 `served_sam_usd`。
- 应用市场份额：`fab_revenue_usd / served_sam_usd`，不是收入除以 TAM。
- 应用增长率使用同一应用的相邻季度或上年同期比较。

## 应用归因

商业收入通过 shipment → order_line → product → application 归因。Design Win 通过 design_win → application 归因。两者不能直接 Join 后同时求和，否则一个应用的收入会被机会数放大；应先各自聚合到 application_id 再关联。

Application 360 报告建议包含：应用收入与增速、市场份额、产品组合、制程组合、量产/NPI 分布、Design Win 管线、客户集中度和质量风险。

