# Product 360 业务说明

Product 360 以 `f360_product.product_id` 为产品唯一标识，整合客户归属、终端应用、工艺节点、制程路线、主生产厂、订单、投片、良率、出货、成本、库存、质量和 NPI 状态。

## 产品生命周期

- `NPI`：仍在工程验证或客户认证阶段，不能用量产产品的稳定良率目标评价。
- `RAMP_UP`：已开始商业出货但产量与良率仍在爬坡。
- `MASS_PRODUCTION`：正式量产，适用于收入、毛利、交付、稳定良率等常规经营指标。
- `EOL`：生命周期结束。若查询当前有效产品，应排除 EOL。

`qualification_status='ENGINEERING'` 表示尚未完成正式认证，`QUALIFIED` 表示具备量产资格。生命周期与认证状态不是同一个概念，不能混用。

## Product 360 推荐分析路径

1. 商务：product → order_line → sales_order / shipment。
2. 制造：product → wafer_lot → wafer_output / yield_result。
3. 成本：product → product_cost，必须同时匹配成本月份。
4. 质量：product → quality_incident，必要时再关联 lot。
5. 市场：product → application → application_market。
6. 项目：product → npi_milestone / design_win。

## 常见陷阱

- 一个订单行可能对应多个批次或多次出货。跨订单、批次、出货查询时必须先分别聚合，再回到订单行粒度关联。
- `f360_yield_result` 每个 lot 有 WAT、CP 两个测试阶段。查询 CP 良率必须过滤 `test_stage='CP'`，否则会把不同阶段重复计算。
- 产品成本是月度快照；收入与成本计算毛利时应按 `product_id + 月份` 关联。
- 库存是快照数据，期间库存要取指定日期或期间最后一张快照，不能把多个月份库存直接相加。
- 价格协议是参考价格；确认收入使用实际出货表中的 `unit_price_usd`。

