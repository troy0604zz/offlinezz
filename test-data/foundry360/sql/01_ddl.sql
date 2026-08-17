-- 晶圆代工 360 综合测试模型
-- Oracle Database 19c / SQL*Plus compatible
-- 所有对象使用 f360_ 前缀，不影响原有销售演示表。
SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

CREATE TABLE f360_geo (
  geo_id NUMBER(19) PRIMARY KEY,
  geo_code VARCHAR2(30 CHAR) UNIQUE NOT NULL,
  geo_name VARCHAR2(100 CHAR) NOT NULL,
  region_group VARCHAR2(50 CHAR) NOT NULL,
  country_code VARCHAR2(10 CHAR) NOT NULL,
  currency_code VARCHAR2(10 CHAR) NOT NULL
);

CREATE TABLE f360_customer (
  customer_id NUMBER(19) PRIMARY KEY,
  customer_code VARCHAR2(30 CHAR) UNIQUE NOT NULL,
  customer_name VARCHAR2(200 CHAR) NOT NULL,
  customer_group VARCHAR2(100 CHAR),
  geo_id NUMBER(19) NOT NULL,
  market_segment VARCHAR2(80 CHAR) NOT NULL,
  customer_tier VARCHAR2(20 CHAR) NOT NULL,
  credit_rating VARCHAR2(20 CHAR),
  account_manager VARCHAR2(100 CHAR),
  customer_status VARCHAR2(30 CHAR) NOT NULL,
  onboard_date DATE NOT NULL,
  CONSTRAINT fk_f360_customer_geo FOREIGN KEY (geo_id) REFERENCES f360_geo(geo_id)
);

CREATE TABLE f360_application (
  application_id NUMBER(19) PRIMARY KEY,
  application_code VARCHAR2(30 CHAR) UNIQUE NOT NULL,
  application_name VARCHAR2(150 CHAR) NOT NULL,
  market_segment VARCHAR2(80 CHAR) NOT NULL,
  end_product VARCHAR2(120 CHAR) NOT NULL,
  functional_domain VARCHAR2(100 CHAR),
  safety_grade VARCHAR2(30 CHAR),
  lifecycle_stage VARCHAR2(30 CHAR) NOT NULL
);

CREATE TABLE f360_technology_node (
  node_id NUMBER(19) PRIMARY KEY,
  node_code VARCHAR2(30 CHAR) UNIQUE NOT NULL,
  node_name VARCHAR2(100 CHAR) NOT NULL,
  process_family VARCHAR2(80 CHAR) NOT NULL,
  geometry_nm NUMBER(8,2),
  wafer_size_mm NUMBER(6) NOT NULL,
  specialty_flag NUMBER(1) DEFAULT 0 NOT NULL,
  CONSTRAINT ck_f360_node_specialty CHECK (specialty_flag IN (0,1))
);

CREATE TABLE f360_fab (
  fab_id NUMBER(19) PRIMARY KEY,
  fab_code VARCHAR2(30 CHAR) UNIQUE NOT NULL,
  fab_name VARCHAR2(120 CHAR) NOT NULL,
  geo_id NUMBER(19) NOT NULL,
  wafer_size_mm NUMBER(6) NOT NULL,
  monthly_capacity_wafers NUMBER(12) NOT NULL,
  fab_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT fk_f360_fab_geo FOREIGN KEY (geo_id) REFERENCES f360_geo(geo_id)
);

CREATE TABLE f360_process_route (
  route_id NUMBER(19) PRIMARY KEY,
  route_code VARCHAR2(40 CHAR) UNIQUE NOT NULL,
  route_name VARCHAR2(150 CHAR) NOT NULL,
  node_id NUMBER(19) NOT NULL,
  standard_cycle_days NUMBER(8,2) NOT NULL,
  mask_layer_count NUMBER(6) NOT NULL,
  active_flag NUMBER(1) DEFAULT 1 NOT NULL,
  CONSTRAINT fk_f360_route_node FOREIGN KEY (node_id) REFERENCES f360_technology_node(node_id),
  CONSTRAINT ck_f360_route_active CHECK (active_flag IN (0,1))
);

CREATE TABLE f360_product (
  product_id NUMBER(19) PRIMARY KEY,
  product_code VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  product_name VARCHAR2(200 CHAR) NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  application_id NUMBER(19) NOT NULL,
  node_id NUMBER(19) NOT NULL,
  route_id NUMBER(19) NOT NULL,
  primary_fab_id NUMBER(19) NOT NULL,
  product_type VARCHAR2(60 CHAR) NOT NULL,
  wafer_size_mm NUMBER(6) NOT NULL,
  die_size_mm2 NUMBER(12,4) NOT NULL,
  gross_die_per_wafer NUMBER(10) NOT NULL,
  qualification_status VARCHAR2(30 CHAR) NOT NULL,
  lifecycle_stage VARCHAR2(30 CHAR) NOT NULL,
  launch_date DATE,
  eol_date DATE,
  CONSTRAINT fk_f360_product_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id),
  CONSTRAINT fk_f360_product_app FOREIGN KEY (application_id) REFERENCES f360_application(application_id),
  CONSTRAINT fk_f360_product_node FOREIGN KEY (node_id) REFERENCES f360_technology_node(node_id),
  CONSTRAINT fk_f360_product_route FOREIGN KEY (route_id) REFERENCES f360_process_route(route_id),
  CONSTRAINT fk_f360_product_fab FOREIGN KEY (primary_fab_id) REFERENCES f360_fab(fab_id)
);

CREATE TABLE f360_sales_order (
  order_id NUMBER(19) PRIMARY KEY,
  order_no VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  order_date DATE NOT NULL,
  requested_delivery_date DATE NOT NULL,
  currency_code VARCHAR2(10 CHAR) NOT NULL,
  order_status VARCHAR2(30 CHAR) NOT NULL,
  incoterm VARCHAR2(20 CHAR),
  priority_code VARCHAR2(20 CHAR),
  sales_owner VARCHAR2(100 CHAR),
  CONSTRAINT fk_f360_order_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id)
);

CREATE TABLE f360_order_line (
  order_line_id NUMBER(19) PRIMARY KEY,
  order_id NUMBER(19) NOT NULL,
  line_no NUMBER(8) NOT NULL,
  product_id NUMBER(19) NOT NULL,
  ordered_wafers NUMBER(12) NOT NULL,
  unit_price_usd NUMBER(18,4) NOT NULL,
  discount_amount_usd NUMBER(18,2) DEFAULT 0 NOT NULL,
  promised_date DATE NOT NULL,
  line_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT uq_f360_order_line UNIQUE (order_id,line_no),
  CONSTRAINT fk_f360_line_order FOREIGN KEY (order_id) REFERENCES f360_sales_order(order_id),
  CONSTRAINT fk_f360_line_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_wafer_lot (
  lot_id NUMBER(19) PRIMARY KEY,
  lot_no VARCHAR2(60 CHAR) UNIQUE NOT NULL,
  order_line_id NUMBER(19),
  product_id NUMBER(19) NOT NULL,
  fab_id NUMBER(19) NOT NULL,
  route_id NUMBER(19) NOT NULL,
  start_date DATE NOT NULL,
  planned_wafers NUMBER(10) NOT NULL,
  actual_start_wafers NUMBER(10) NOT NULL,
  lot_status VARCHAR2(30 CHAR) NOT NULL,
  hot_lot_flag NUMBER(1) DEFAULT 0 NOT NULL,
  CONSTRAINT fk_f360_lot_line FOREIGN KEY (order_line_id) REFERENCES f360_order_line(order_line_id),
  CONSTRAINT fk_f360_lot_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id),
  CONSTRAINT fk_f360_lot_fab FOREIGN KEY (fab_id) REFERENCES f360_fab(fab_id),
  CONSTRAINT fk_f360_lot_route FOREIGN KEY (route_id) REFERENCES f360_process_route(route_id),
  CONSTRAINT ck_f360_lot_hot CHECK (hot_lot_flag IN (0,1))
);

CREATE TABLE f360_wafer_output (
  output_id NUMBER(19) PRIMARY KEY,
  lot_id NUMBER(19) UNIQUE NOT NULL,
  completion_date DATE NOT NULL,
  completed_wafers NUMBER(10) NOT NULL,
  good_wafers NUMBER(10) NOT NULL,
  scrapped_wafers NUMBER(10) NOT NULL,
  rework_wafers NUMBER(10) DEFAULT 0 NOT NULL,
  cycle_time_days NUMBER(10,2) NOT NULL,
  hold_hours NUMBER(12,2) DEFAULT 0 NOT NULL,
  CONSTRAINT fk_f360_output_lot FOREIGN KEY (lot_id) REFERENCES f360_wafer_lot(lot_id)
);

CREATE TABLE f360_yield_result (
  yield_id NUMBER(19) PRIMARY KEY,
  lot_id NUMBER(19) NOT NULL,
  test_stage VARCHAR2(30 CHAR) NOT NULL,
  test_date DATE NOT NULL,
  tested_die NUMBER(14) NOT NULL,
  good_die NUMBER(14) NOT NULL,
  electrical_yield_pct NUMBER(8,4) NOT NULL,
  defect_density NUMBER(10,6),
  bin1_pct NUMBER(8,4),
  parametric_pass_pct NUMBER(8,4),
  CONSTRAINT uq_f360_yield_stage UNIQUE (lot_id,test_stage),
  CONSTRAINT fk_f360_yield_lot FOREIGN KEY (lot_id) REFERENCES f360_wafer_lot(lot_id)
);

CREATE TABLE f360_shipment (
  shipment_id NUMBER(19) PRIMARY KEY,
  shipment_no VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  order_line_id NUMBER(19) NOT NULL,
  ship_date DATE NOT NULL,
  actual_delivery_date DATE,
  shipped_wafers NUMBER(12) NOT NULL,
  accepted_wafers NUMBER(12) NOT NULL,
  unit_price_usd NUMBER(18,4) NOT NULL,
  freight_usd NUMBER(18,2) DEFAULT 0 NOT NULL,
  shipment_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT fk_f360_ship_line FOREIGN KEY (order_line_id) REFERENCES f360_order_line(order_line_id)
);

CREATE TABLE f360_customer_forecast (
  forecast_id NUMBER(19) PRIMARY KEY,
  forecast_version VARCHAR2(30 CHAR) NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  product_id NUMBER(19) NOT NULL,
  forecast_month DATE NOT NULL,
  submitted_date DATE NOT NULL,
  forecast_wafers NUMBER(12) NOT NULL,
  confidence_pct NUMBER(8,2) NOT NULL,
  forecast_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT uq_f360_forecast UNIQUE (forecast_version,customer_id,product_id,forecast_month),
  CONSTRAINT fk_f360_fcst_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id),
  CONSTRAINT fk_f360_fcst_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_capacity_plan (
  capacity_id NUMBER(19) PRIMARY KEY,
  fab_id NUMBER(19) NOT NULL,
  node_id NUMBER(19) NOT NULL,
  capacity_month DATE NOT NULL,
  available_wafers NUMBER(12) NOT NULL,
  committed_wafers NUMBER(12) NOT NULL,
  actual_start_wafers NUMBER(12) NOT NULL,
  maintenance_loss_wafers NUMBER(12) DEFAULT 0 NOT NULL,
  CONSTRAINT uq_f360_capacity UNIQUE (fab_id,node_id,capacity_month),
  CONSTRAINT fk_f360_capacity_fab FOREIGN KEY (fab_id) REFERENCES f360_fab(fab_id),
  CONSTRAINT fk_f360_capacity_node FOREIGN KEY (node_id) REFERENCES f360_technology_node(node_id)
);

CREATE TABLE f360_price_agreement (
  agreement_id NUMBER(19) PRIMARY KEY,
  agreement_no VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  product_id NUMBER(19) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE NOT NULL,
  agreed_unit_price_usd NUMBER(18,4) NOT NULL,
  minimum_volume_wafers NUMBER(12),
  rebate_pct NUMBER(8,4) DEFAULT 0 NOT NULL,
  agreement_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT fk_f360_price_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id),
  CONSTRAINT fk_f360_price_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_product_cost (
  cost_id NUMBER(19) PRIMARY KEY,
  product_id NUMBER(19) NOT NULL,
  cost_month DATE NOT NULL,
  material_cost_usd NUMBER(18,4) NOT NULL,
  process_cost_usd NUMBER(18,4) NOT NULL,
  overhead_cost_usd NUMBER(18,4) NOT NULL,
  probe_cost_usd NUMBER(18,4) NOT NULL,
  scrap_cost_usd NUMBER(18,4) DEFAULT 0 NOT NULL,
  CONSTRAINT uq_f360_product_cost UNIQUE (product_id,cost_month),
  CONSTRAINT fk_f360_cost_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_inventory_snapshot (
  snapshot_id NUMBER(19) PRIMARY KEY,
  snapshot_date DATE NOT NULL,
  product_id NUMBER(19) NOT NULL,
  fab_id NUMBER(19) NOT NULL,
  inventory_stage VARCHAR2(30 CHAR) NOT NULL,
  wafer_quantity NUMBER(12) NOT NULL,
  aging_days NUMBER(10) NOT NULL,
  reserved_wafers NUMBER(12) DEFAULT 0 NOT NULL,
  CONSTRAINT uq_f360_inventory UNIQUE (snapshot_date,product_id,fab_id,inventory_stage),
  CONSTRAINT fk_f360_inventory_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id),
  CONSTRAINT fk_f360_inventory_fab FOREIGN KEY (fab_id) REFERENCES f360_fab(fab_id)
);

CREATE TABLE f360_quality_incident (
  incident_id NUMBER(19) PRIMARY KEY,
  incident_no VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  product_id NUMBER(19) NOT NULL,
  lot_id NUMBER(19),
  opened_date DATE NOT NULL,
  closed_date DATE,
  incident_category VARCHAR2(60 CHAR) NOT NULL,
  severity VARCHAR2(20 CHAR) NOT NULL,
  affected_wafers NUMBER(12) DEFAULT 0 NOT NULL,
  root_cause_group VARCHAR2(80 CHAR),
  incident_status VARCHAR2(30 CHAR) NOT NULL,
  CONSTRAINT fk_f360_qi_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id),
  CONSTRAINT fk_f360_qi_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id),
  CONSTRAINT fk_f360_qi_lot FOREIGN KEY (lot_id) REFERENCES f360_wafer_lot(lot_id)
);

CREATE TABLE f360_customer_interaction (
  interaction_id NUMBER(19) PRIMARY KEY,
  customer_id NUMBER(19) NOT NULL,
  interaction_date DATE NOT NULL,
  interaction_type VARCHAR2(40 CHAR) NOT NULL,
  contact_role VARCHAR2(60 CHAR),
  topic VARCHAR2(150 CHAR),
  sentiment_score NUMBER(5,2),
  action_due_date DATE,
  action_status VARCHAR2(30 CHAR),
  owner_name VARCHAR2(100 CHAR),
  CONSTRAINT fk_f360_interaction_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id)
);

CREATE TABLE f360_design_win (
  design_win_id NUMBER(19) PRIMARY KEY,
  opportunity_no VARCHAR2(50 CHAR) UNIQUE NOT NULL,
  customer_id NUMBER(19) NOT NULL,
  application_id NUMBER(19) NOT NULL,
  product_id NUMBER(19),
  opportunity_stage VARCHAR2(30 CHAR) NOT NULL,
  probability_pct NUMBER(8,2) NOT NULL,
  estimated_annual_wafers NUMBER(14) NOT NULL,
  target_sop_date DATE NOT NULL,
  competitor_name VARCHAR2(100 CHAR),
  win_loss_reason VARCHAR2(200 CHAR),
  last_updated_date DATE NOT NULL,
  CONSTRAINT fk_f360_dw_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id),
  CONSTRAINT fk_f360_dw_app FOREIGN KEY (application_id) REFERENCES f360_application(application_id),
  CONSTRAINT fk_f360_dw_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_npi_milestone (
  milestone_id NUMBER(19) PRIMARY KEY,
  product_id NUMBER(19) NOT NULL,
  milestone_type VARCHAR2(40 CHAR) NOT NULL,
  planned_date DATE NOT NULL,
  actual_date DATE,
  milestone_status VARCHAR2(30 CHAR) NOT NULL,
  owner_department VARCHAR2(80 CHAR),
  risk_level VARCHAR2(20 CHAR),
  CONSTRAINT uq_f360_npi UNIQUE (product_id,milestone_type),
  CONSTRAINT fk_f360_npi_product FOREIGN KEY (product_id) REFERENCES f360_product(product_id)
);

CREATE TABLE f360_equipment_downtime (
  downtime_id NUMBER(19) PRIMARY KEY,
  fab_id NUMBER(19) NOT NULL,
  node_id NUMBER(19) NOT NULL,
  equipment_group VARCHAR2(80 CHAR) NOT NULL,
  downtime_start TIMESTAMP NOT NULL,
  downtime_end TIMESTAMP NOT NULL,
  downtime_hours NUMBER(12,2) NOT NULL,
  downtime_type VARCHAR2(40 CHAR) NOT NULL,
  lost_wafers NUMBER(12) DEFAULT 0 NOT NULL,
  CONSTRAINT fk_f360_down_fab FOREIGN KEY (fab_id) REFERENCES f360_fab(fab_id),
  CONSTRAINT fk_f360_down_node FOREIGN KEY (node_id) REFERENCES f360_technology_node(node_id)
);

CREATE TABLE f360_customer_score_snapshot (
  score_id NUMBER(19) PRIMARY KEY,
  customer_id NUMBER(19) NOT NULL,
  snapshot_date DATE NOT NULL,
  revenue_score NUMBER(8,2) NOT NULL,
  growth_score NUMBER(8,2) NOT NULL,
  payment_score NUMBER(8,2) NOT NULL,
  quality_score NUMBER(8,2) NOT NULL,
  engagement_score NUMBER(8,2) NOT NULL,
  overall_score NUMBER(8,2) NOT NULL,
  risk_level VARCHAR2(20 CHAR) NOT NULL,
  CONSTRAINT uq_f360_customer_score UNIQUE (customer_id,snapshot_date),
  CONSTRAINT fk_f360_score_customer FOREIGN KEY (customer_id) REFERENCES f360_customer(customer_id)
);

CREATE TABLE f360_application_market (
  market_id NUMBER(19) PRIMARY KEY,
  application_id NUMBER(19) NOT NULL,
  market_quarter DATE NOT NULL,
  global_tam_usd NUMBER(20,2) NOT NULL,
  served_sam_usd NUMBER(20,2) NOT NULL,
  fab_revenue_usd NUMBER(20,2) NOT NULL,
  market_growth_pct NUMBER(8,4),
  CONSTRAINT uq_f360_app_market UNIQUE (application_id,market_quarter),
  CONSTRAINT fk_f360_market_app FOREIGN KEY (application_id) REFERENCES f360_application(application_id)
);

CREATE INDEX idx_f360_order_date ON f360_sales_order(order_date);
CREATE INDEX idx_f360_order_customer ON f360_sales_order(customer_id);
CREATE INDEX idx_f360_line_product ON f360_order_line(product_id);
CREATE INDEX idx_f360_lot_product_date ON f360_wafer_lot(product_id,start_date);
CREATE INDEX idx_f360_ship_date ON f360_shipment(ship_date);
CREATE INDEX idx_f360_fcst_month ON f360_customer_forecast(forecast_month);
CREATE INDEX idx_f360_capacity_month ON f360_capacity_plan(capacity_month);
CREATE INDEX idx_f360_cost_month ON f360_product_cost(cost_month);
CREATE INDEX idx_f360_inventory_date ON f360_inventory_snapshot(snapshot_date);
CREATE INDEX idx_f360_quality_opened ON f360_quality_incident(opened_date);
CREATE INDEX idx_f360_interaction_date ON f360_customer_interaction(interaction_date);
CREATE INDEX idx_f360_dw_sop ON f360_design_win(target_sop_date);

COMMENT ON TABLE f360_product IS 'Product 360 主数据：客户、应用、工艺节点、制程路线及主生产厂映射';
COMMENT ON TABLE f360_customer IS 'Customer 360 主数据：区域、市场、分级、信用和客户经理';
COMMENT ON TABLE f360_application IS 'Application 360 主数据：终端应用、市场板块、安全等级和生命周期';
COMMENT ON COLUMN f360_yield_result.electrical_yield_pct IS '电性良率百分比，取值 0-100，不是 0-1 小数';
COMMENT ON COLUMN f360_capacity_plan.available_wafers IS '已扣除计划维护损失后的可用月产能';
COMMENT ON COLUMN f360_shipment.accepted_wafers IS '客户验收数量，收入以其乘以成交单价计算';
