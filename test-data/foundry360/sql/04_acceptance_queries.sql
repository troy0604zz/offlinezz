-- 装载后验收。预期总计 2359 行业务数据。
SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

SELECT 'f360_geo' table_name,COUNT(*) row_count FROM f360_geo UNION ALL
SELECT 'f360_customer',COUNT(*) FROM f360_customer UNION ALL
SELECT 'f360_application',COUNT(*) FROM f360_application UNION ALL
SELECT 'f360_technology_node',COUNT(*) FROM f360_technology_node UNION ALL
SELECT 'f360_fab',COUNT(*) FROM f360_fab UNION ALL
SELECT 'f360_process_route',COUNT(*) FROM f360_process_route UNION ALL
SELECT 'f360_product',COUNT(*) FROM f360_product UNION ALL
SELECT 'f360_sales_order',COUNT(*) FROM f360_sales_order UNION ALL
SELECT 'f360_order_line',COUNT(*) FROM f360_order_line UNION ALL
SELECT 'f360_wafer_lot',COUNT(*) FROM f360_wafer_lot UNION ALL
SELECT 'f360_wafer_output',COUNT(*) FROM f360_wafer_output UNION ALL
SELECT 'f360_yield_result',COUNT(*) FROM f360_yield_result UNION ALL
SELECT 'f360_shipment',COUNT(*) FROM f360_shipment UNION ALL
SELECT 'f360_customer_forecast',COUNT(*) FROM f360_customer_forecast UNION ALL
SELECT 'f360_capacity_plan',COUNT(*) FROM f360_capacity_plan UNION ALL
SELECT 'f360_price_agreement',COUNT(*) FROM f360_price_agreement UNION ALL
SELECT 'f360_product_cost',COUNT(*) FROM f360_product_cost UNION ALL
SELECT 'f360_inventory_snapshot',COUNT(*) FROM f360_inventory_snapshot UNION ALL
SELECT 'f360_quality_incident',COUNT(*) FROM f360_quality_incident UNION ALL
SELECT 'f360_customer_interaction',COUNT(*) FROM f360_customer_interaction UNION ALL
SELECT 'f360_design_win',COUNT(*) FROM f360_design_win UNION ALL
SELECT 'f360_npi_milestone',COUNT(*) FROM f360_npi_milestone UNION ALL
SELECT 'f360_equipment_downtime',COUNT(*) FROM f360_equipment_downtime UNION ALL
SELECT 'f360_customer_score_snapshot',COUNT(*) FROM f360_customer_score_snapshot UNION ALL
SELECT 'f360_application_market',COUNT(*) FROM f360_application_market
ORDER BY 1;

-- 预期为 0：订单行与订单客户和产品客户不一致。
SELECT COUNT(*) inconsistent_customer_rows
FROM f360_order_line ol
JOIN f360_sales_order so ON so.order_id=ol.order_id
JOIN f360_product p ON p.product_id=ol.product_id
WHERE so.customer_id<>p.customer_id;

-- 预期每个批次恰好两个测试阶段。
SELECT MIN(stage_count) min_stage_count,MAX(stage_count) max_stage_count
FROM (SELECT lot_id,COUNT(*) stage_count FROM f360_yield_result GROUP BY lot_id);

-- 预期无负数、无良品数大于完成数。
SELECT COUNT(*) invalid_output_rows
FROM f360_wafer_output
WHERE completed_wafers<0 OR good_wafers<0 OR scrapped_wafers<0 OR good_wafers>completed_wafers;

-- 预期无范围异常。
SELECT COUNT(*) invalid_yield_rows
FROM f360_yield_result
WHERE electrical_yield_pct<0 OR electrical_yield_pct>100 OR good_die>tested_die;

-- 收入汇总冒烟测试。
SELECT EXTRACT(YEAR FROM s.ship_date) year_no,
       ROUND(SUM(s.accepted_wafers*s.unit_price_usd),2) wafer_revenue,
       SUM(s.accepted_wafers) accepted_wafers
FROM f360_shipment s
WHERE s.shipment_status='DELIVERED'
GROUP BY EXTRACT(YEAR FROM s.ship_date)
ORDER BY year_no;

-- CP 良率冒烟测试。
SELECT p.product_code,
       ROUND(100*SUM(yr.good_die)/NULLIF(SUM(yr.tested_die),0),2) cp_yield_pct
FROM f360_yield_result yr
JOIN f360_wafer_lot wl ON wl.lot_id=yr.lot_id
JOIN f360_product p ON p.product_id=wl.product_id
WHERE yr.test_stage='CP'
GROUP BY p.product_code
ORDER BY cp_yield_pct;

-- 最新库存快照冒烟测试，不能跨月份累计。
SELECT i.snapshot_date,SUM(i.wafer_quantity) inventory_wafers
FROM f360_inventory_snapshot i
WHERE i.snapshot_date=(SELECT MAX(snapshot_date) FROM f360_inventory_snapshot)
GROUP BY i.snapshot_date;
