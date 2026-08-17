# Foundry 360 表关系导航

## 三个 360 主路径

```text
Customer 360
f360_geo
  └─ f360_customer
       ├─ f360_sales_order ─ f360_order_line ─ f360_shipment
       ├─ f360_product ─ f360_wafer_lot ─ f360_wafer_output
       │                              └─ f360_yield_result
       ├─ f360_customer_forecast
       ├─ f360_price_agreement
       ├─ f360_quality_incident
       ├─ f360_customer_interaction
       ├─ f360_design_win
       └─ f360_customer_score_snapshot

Product 360
f360_product
  ├─ f360_customer
  ├─ f360_application ─ f360_application_market
  ├─ f360_technology_node ─ f360_capacity_plan
  ├─ f360_process_route
  ├─ f360_fab ─ f360_equipment_downtime
  ├─ f360_order_line ─ f360_shipment
  ├─ f360_wafer_lot ─ f360_wafer_output / f360_yield_result
  ├─ f360_customer_forecast
  ├─ f360_product_cost
  ├─ f360_inventory_snapshot
  ├─ f360_quality_incident
  ├─ f360_design_win
  └─ f360_npi_milestone

Application 360
f360_application
  ├─ f360_product ─ 商务、制造、质量、成本事实
  ├─ f360_design_win
  └─ f360_application_market
```

## 多事实查询纪律

以下事实不能直接全部 Join 后汇总：

- 订单行与出货：先把出货聚合到 `order_line_id`。
- 批次与良率：先过滤测试阶段并聚合到 `lot_id` 或 `product_id`。
- 收入与成本：分别聚合到 `product_id + month`。
- 收入、质量、互动、Design Win：分别聚合到 `customer_id`。
- 收入、市场、Design Win：分别聚合到 `application_id`。
- 库存和客户评分：先选最新快照。

精确机器可读关系见 `semantic/relations.yaml`。

