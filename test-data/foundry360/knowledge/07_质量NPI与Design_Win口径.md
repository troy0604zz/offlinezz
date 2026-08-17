# 质量、NPI 与 Design Win 口径

## 质量事件

质量事件主键为 incident_id，一个事件关联客户和产品，可选关联具体 lot。

- 严重度：CRITICAL、MAJOR、MINOR。
- 未关闭事件：`incident_status='OPEN'`。
- 重大未关闭事件：OPEN 且 severity 为 CRITICAL 或 MAJOR。
- 平均关闭天数只统计有 closed_date 的事件。

客户、产品、lot 三条关系同时存在时仍然只能按 incident_id 计数，避免重复。

## NPI

标准里程碑顺序为 TAPE_OUT、FIRST_SILICON、QUALIFICATION、MASS_PRODUCTION。实际日期为空表示尚未完成，不能记为延期天数 0。

准时率仅针对已完成里程碑：actual_date <= planned_date 记为准时。未完成项目应单独列入“未来节点”和“高风险节点”。

## Design Win

- 开放阶段：RFQ、PROPOSAL、SAMPLE、QUALIFICATION。
- 已结束阶段：WON、LOST。
- 加权管线：开放机会的 estimated_annual_wafers × probability_pct。
- 赢单率：WON / (WON + LOST)。开放机会不进入分母。

Design Win 代表未来机会，不是已确认订单或收入。报告中必须区分实际收入、订单、预测和机会管线。

