-- 晶圆代工 360 事实数据，覆盖 2025-01 至 2026-06。
-- 采用确定性公式生成，重复执行前请清空 f360_ 表或重建测试 Schema。
SET DEFINE OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

INSERT INTO f360_sales_order
  (order_id,order_no,customer_id,order_date,requested_delivery_date,currency_code,order_status,incoterm,priority_code,sales_owner)
SELECT 100000+n,
       'SO-'||TO_CHAR(100000+n),
       1+MOD(n-1,8),
       ADD_MONTHS(DATE '2025-01-01',FLOOR((n-1)/4))+MOD(n*3,20),
       ADD_MONTHS(DATE '2025-01-01',FLOOR((n-1)/4))+MOD(n*3,20)+70,
       'USD',
       CASE WHEN MOD(n,17)=0 THEN 'CANCELLED' WHEN MOD(n,13)=0 THEN 'ON_HOLD' ELSE 'COMPLETED' END,
       CASE WHEN MOD(n,3)=0 THEN 'FOB' ELSE 'DAP' END,
       CASE WHEN MOD(n,10)=0 THEN 'HOT' WHEN MOD(n,4)=0 THEN 'HIGH' ELSE 'NORMAL' END,
       CASE MOD(n-1,4) WHEN 0 THEN '周宁' WHEN 1 THEN '陈晓' WHEN 2 THEN '林海' ELSE '孙悦' END
FROM (SELECT LEVEL n FROM dual CONNECT BY LEVEL <= 72);

INSERT INTO f360_order_line
  (order_line_id,order_id,line_no,product_id,ordered_wafers,unit_price_usd,discount_amount_usd,promised_date,line_status)
WITH line_source AS (
  SELECT o.order_id,o.customer_id,o.requested_delivery_date,o.order_status,l.line_no,
         CASE o.customer_id
           WHEN 1 THEN CASE l.line_no WHEN 1 THEN 1001 ELSE CASE MOD(o.order_id,16) WHEN 1 THEN 1002 ELSE 1010 END END
           WHEN 2 THEN 1003 WHEN 3 THEN CASE l.line_no WHEN 1 THEN 1004 ELSE 1011 END
           WHEN 4 THEN CASE l.line_no WHEN 1 THEN 1005 ELSE 1012 END
           WHEN 5 THEN 1006 WHEN 6 THEN 1009 WHEN 7 THEN 1007 ELSE 1008
         END product_id
  FROM f360_sales_order o
  CROSS JOIN (SELECT 1 line_no FROM dual UNION ALL SELECT 2 FROM dual) l
)
SELECT order_id*10+line_no,order_id,line_no,product_id,
       180+MOD(order_id*line_no,720),
       CASE product_id WHEN 1001 THEN 2450 WHEN 1002 THEN 1680 WHEN 1003 THEN 3920 WHEN 1004 THEN 11200
         WHEN 1005 THEN 980 WHEN 1006 THEN 1460 WHEN 1007 THEN 4380 WHEN 1008 THEN 3150
         WHEN 1009 THEN 1150 WHEN 1010 THEN 1320 WHEN 1011 THEN 3850 ELSE 1280 END,
       CASE WHEN MOD(order_id+line_no,9)=0 THEN 15000 ELSE MOD(order_id,5)*2500 END,
       requested_delivery_date+CASE line_no WHEN 1 THEN 0 ELSE 14 END,
       CASE WHEN order_status='CANCELLED' THEN 'CANCELLED' WHEN order_status='ON_HOLD' THEN 'ON_HOLD' ELSE 'CONFIRMED' END
FROM line_source;

INSERT INTO f360_wafer_lot
  (lot_id,lot_no,order_line_id,product_id,fab_id,route_id,start_date,planned_wafers,actual_start_wafers,lot_status,hot_lot_flag)
SELECT 200000+ROW_NUMBER() OVER (ORDER BY ol.order_line_id),
       'LOT-'||TO_CHAR(200000+ROW_NUMBER() OVER (ORDER BY ol.order_line_id)),
       ol.order_line_id,ol.product_id,p.primary_fab_id,p.route_id,
       o.order_date+5+MOD(ol.order_line_id,12),
       ol.ordered_wafers,
       ol.ordered_wafers-CASE WHEN MOD(ol.order_line_id,11)=0 THEN 12 ELSE 0 END,
       CASE WHEN MOD(ol.order_line_id,19)=0 THEN 'ON_HOLD' ELSE 'COMPLETED' END,
       CASE WHEN o.priority_code='HOT' THEN 1 ELSE 0 END
FROM f360_order_line ol
JOIN f360_sales_order o ON o.order_id=ol.order_id
JOIN f360_product p ON p.product_id=ol.product_id
WHERE o.order_status<>'CANCELLED';

INSERT INTO f360_wafer_output
  (output_id,lot_id,completion_date,completed_wafers,good_wafers,scrapped_wafers,rework_wafers,cycle_time_days,hold_hours)
SELECT 300000+ROW_NUMBER() OVER (ORDER BY l.lot_id),l.lot_id,
       l.start_date+r.standard_cycle_days+MOD(l.lot_id,9)-3,
       l.actual_start_wafers-CASE WHEN MOD(l.lot_id,23)=0 THEN 8 ELSE 0 END,
       ROUND((l.actual_start_wafers-CASE WHEN MOD(l.lot_id,23)=0 THEN 8 ELSE 0 END)*(0.94-MOD(l.lot_id,7)/200),0),
       ROUND((l.actual_start_wafers-CASE WHEN MOD(l.lot_id,23)=0 THEN 8 ELSE 0 END)*(0.02+MOD(l.lot_id,4)/200),0),
       CASE WHEN MOD(l.lot_id,10)=0 THEN 6 ELSE 0 END,
       r.standard_cycle_days+MOD(l.lot_id,9)-3,
       CASE WHEN MOD(l.lot_id,9)=0 THEN 72 WHEN MOD(l.lot_id,5)=0 THEN 24 ELSE 4 END
FROM f360_wafer_lot l
JOIN f360_process_route r ON r.route_id=l.route_id;

INSERT INTO f360_yield_result
  (yield_id,lot_id,test_stage,test_date,tested_die,good_die,electrical_yield_pct,defect_density,bin1_pct,parametric_pass_pct)
WITH stages AS (
  SELECT 'WAT' test_stage,1 stage_no FROM dual
  UNION ALL SELECT 'CP',2 FROM dual
), base AS (
  SELECT l.lot_id,p.gross_die_per_wafer,o.good_wafers,o.completion_date,s.test_stage,s.stage_no,
         91.5-MOD(l.lot_id,8)*0.7-CASE s.stage_no WHEN 1 THEN 0 ELSE 1.2 END yield_pct
  FROM f360_wafer_lot l
  JOIN f360_wafer_output o ON o.lot_id=l.lot_id
  JOIN f360_product p ON p.product_id=l.product_id
  CROSS JOIN stages s
)
SELECT 400000+ROW_NUMBER() OVER (ORDER BY lot_id,stage_no),lot_id,test_stage,completion_date+stage_no,
       good_wafers*gross_die_per_wafer,
       ROUND(good_wafers*gross_die_per_wafer*yield_pct/100,0),
       yield_pct,ROUND(0.08+MOD(lot_id,11)*0.013,6),yield_pct-2.1,yield_pct+3.4
FROM base;

INSERT INTO f360_shipment
  (shipment_id,shipment_no,order_line_id,ship_date,actual_delivery_date,shipped_wafers,accepted_wafers,unit_price_usd,freight_usd,shipment_status)
SELECT 500000+ROW_NUMBER() OVER (ORDER BY ol.order_line_id),
       'SHP-'||TO_CHAR(500000+ROW_NUMBER() OVER (ORDER BY ol.order_line_id)),
       ol.order_line_id,
       wo.completion_date+5,
       wo.completion_date+CASE WHEN MOD(ol.order_line_id,7)=0 THEN 18 ELSE 11 END,
       wo.good_wafers,
       wo.good_wafers-CASE WHEN MOD(ol.order_line_id,29)=0 THEN 5 ELSE 0 END,
       ol.unit_price_usd,
       800+MOD(ol.order_line_id,9)*120,
       CASE WHEN MOD(ol.order_line_id,31)=0 THEN 'CUSTOMER_HOLD' ELSE 'DELIVERED' END
FROM f360_order_line ol
JOIN f360_sales_order so ON so.order_id=ol.order_id
JOIN f360_wafer_lot wl ON wl.order_line_id=ol.order_line_id
JOIN f360_wafer_output wo ON wo.lot_id=wl.lot_id
WHERE so.order_status='COMPLETED';

INSERT INTO f360_customer_forecast
  (forecast_id,forecast_version,customer_id,product_id,forecast_month,submitted_date,forecast_wafers,confidence_pct,forecast_status)
SELECT 600000+ROW_NUMBER() OVER (ORDER BY p.product_id,m.month_no),
       'PLAN_2026',p.customer_id,p.product_id,ADD_MONTHS(DATE '2026-01-01',m.month_no-1),
       ADD_MONTHS(DATE '2025-12-01',m.month_no-1)+20,
       420+MOD(p.product_id*7+m.month_no*31,980),
       65+MOD(p.product_id+m.month_no,31),
       CASE WHEN m.month_no<=6 THEN 'FROZEN' ELSE 'OPEN' END
FROM f360_product p
CROSS JOIN (SELECT LEVEL month_no FROM dual CONNECT BY LEVEL<=12) m;

INSERT INTO f360_capacity_plan
  (capacity_id,fab_id,node_id,capacity_month,available_wafers,committed_wafers,actual_start_wafers,maintenance_loss_wafers)
WITH fab_node AS (
  SELECT 301 fab_id,201 node_id,9500 base_capacity FROM dual UNION ALL
  SELECT 301,203,10500 FROM dual UNION ALL SELECT 301,204,8500 FROM dual UNION ALL
  SELECT 302,202,12000 FROM dual UNION ALL SELECT 302,204,7500 FROM dual UNION ALL
  SELECT 302,205,10500 FROM dual UNION ALL SELECT 303,206,29000 FROM dual UNION ALL
  SELECT 304,201,1200 FROM dual UNION ALL SELECT 304,202,900 FROM dual UNION ALL
  SELECT 304,203,600 FROM dual UNION ALL SELECT 304,205,800 FROM dual
), months AS (SELECT LEVEL month_no FROM dual CONNECT BY LEVEL<=18)
SELECT 700000+ROW_NUMBER() OVER (ORDER BY f.fab_id,f.node_id,m.month_no),f.fab_id,f.node_id,
       ADD_MONTHS(DATE '2025-01-01',m.month_no-1),
       f.base_capacity-MOD(m.month_no*f.node_id,700),
       ROUND((f.base_capacity-MOD(m.month_no*f.node_id,700))*(0.72+MOD(m.month_no+f.node_id,20)/100),0),
       ROUND((f.base_capacity-MOD(m.month_no*f.node_id,700))*(0.68+MOD(m.month_no*3+f.node_id,24)/100),0),
       120+MOD(m.month_no*f.fab_id,480)
FROM fab_node f CROSS JOIN months m;

INSERT INTO f360_price_agreement
  (agreement_id,agreement_no,customer_id,product_id,effective_from,effective_to,agreed_unit_price_usd,minimum_volume_wafers,rebate_pct,agreement_status)
SELECT 800000+ROW_NUMBER() OVER (ORDER BY p.product_id,y.yr),
       'PA-'||p.product_code||'-'||y.yr,p.customer_id,p.product_id,
       TO_DATE(y.yr||'-01-01','YYYY-MM-DD'),TO_DATE(y.yr||'-12-31','YYYY-MM-DD'),
       CASE p.product_id WHEN 1001 THEN 2450 WHEN 1002 THEN 1680 WHEN 1003 THEN 3920 WHEN 1004 THEN 11200
         WHEN 1005 THEN 980 WHEN 1006 THEN 1460 WHEN 1007 THEN 4380 WHEN 1008 THEN 3150
         WHEN 1009 THEN 1150 WHEN 1010 THEN 1320 WHEN 1011 THEN 3850 ELSE 1280 END * CASE y.yr WHEN 2025 THEN 1.04 ELSE 1 END,
       3600+MOD(p.product_id,7)*600,CASE WHEN p.customer_id IN (1,3) THEN 2.5 ELSE 1 END,'ACTIVE'
FROM f360_product p CROSS JOIN (SELECT 2025 yr FROM dual UNION ALL SELECT 2026 FROM dual) y;

INSERT INTO f360_product_cost
  (cost_id,product_id,cost_month,material_cost_usd,process_cost_usd,overhead_cost_usd,probe_cost_usd,scrap_cost_usd)
SELECT 900000+ROW_NUMBER() OVER (ORDER BY p.product_id,m.month_no),p.product_id,
       ADD_MONTHS(DATE '2025-01-01',m.month_no-1),
       120+MOD(p.product_id*3,180),
       CASE p.node_id WHEN 203 THEN 6800 WHEN 202 THEN 2100 WHEN 201 THEN 1350 WHEN 206 THEN 620 ELSE 820 END-MOD(m.month_no,4)*18,
       180+MOD(p.product_id+m.month_no,90),
       60+MOD(p.gross_die_per_wafer,110),
       25+MOD(p.product_id*m.month_no,75)
FROM f360_product p CROSS JOIN (SELECT LEVEL month_no FROM dual CONNECT BY LEVEL<=18) m;

INSERT INTO f360_inventory_snapshot
  (snapshot_id,snapshot_date,product_id,fab_id,inventory_stage,wafer_quantity,aging_days,reserved_wafers)
WITH stages AS (
  SELECT 'WIP' inventory_stage,1 stage_no FROM dual
  UNION ALL SELECT 'FINISHED_GOODS',2 FROM dual
), months AS (SELECT LEVEL month_no FROM dual CONNECT BY LEVEL<=18)
SELECT 1000000+ROW_NUMBER() OVER (ORDER BY p.product_id,m.month_no,s.stage_no),
       LAST_DAY(ADD_MONTHS(DATE '2025-01-01',m.month_no-1)),p.product_id,p.primary_fab_id,s.inventory_stage,
       110+MOD(p.product_id*13+m.month_no*47+s.stage_no*31,780),
       5+MOD(p.product_id+m.month_no*3+s.stage_no*11,125),
       40+MOD(p.product_id+m.month_no*17,260)
FROM f360_product p CROSS JOIN months m CROSS JOIN stages s;

INSERT INTO f360_quality_incident
  (incident_id,incident_no,customer_id,product_id,lot_id,opened_date,closed_date,incident_category,severity,affected_wafers,root_cause_group,incident_status)
SELECT 1100000+rn,'QI-'||TO_CHAR(1100000+rn),customer_id,product_id,lot_id,
       start_date+25,
       CASE WHEN MOD(rn,5)=0 THEN NULL ELSE start_date+38+MOD(rn,15) END,
       CASE MOD(rn,5) WHEN 0 THEN 'YIELD_LOSS' WHEN 1 THEN 'PARAMETRIC_SHIFT' WHEN 2 THEN 'VISUAL_DEFECT' WHEN 3 THEN 'DELIVERY_DAMAGE' ELSE 'DOCUMENTATION' END,
       CASE WHEN MOD(rn,11)=0 THEN 'CRITICAL' WHEN MOD(rn,4)=0 THEN 'MAJOR' ELSE 'MINOR' END,
       8+MOD(rn*17,95),
       CASE MOD(rn,4) WHEN 0 THEN 'EQUIPMENT' WHEN 1 THEN 'PROCESS' WHEN 2 THEN 'MATERIAL' ELSE 'HANDLING' END,
       CASE WHEN MOD(rn,5)=0 THEN 'OPEN' ELSE 'CLOSED' END
FROM (
  SELECT ROW_NUMBER() OVER (ORDER BY l.lot_id) rn,l.lot_id,l.product_id,l.start_date,p.customer_id
  FROM f360_wafer_lot l JOIN f360_product p ON p.product_id=l.product_id
  WHERE MOD(l.lot_id,6)=0
) WHERE rn<=24;

INSERT INTO f360_customer_interaction
  (interaction_id,customer_id,interaction_date,interaction_type,contact_role,topic,sentiment_score,action_due_date,action_status,owner_name)
SELECT 1200000+n,1+MOD(n-1,8),DATE '2025-01-05'+n*5,
       CASE MOD(n,4) WHEN 0 THEN 'QBR' WHEN 1 THEN 'TECHNICAL_REVIEW' WHEN 2 THEN 'FORECAST_REVIEW' ELSE 'QUALITY_REVIEW' END,
       CASE MOD(n,3) WHEN 0 THEN 'VP_ENGINEERING' WHEN 1 THEN 'PROCUREMENT' ELSE 'QUALITY_MANAGER' END,
       CASE MOD(n,5) WHEN 0 THEN '产能保障' WHEN 1 THEN '良率改善' WHEN 2 THEN '价格协商' WHEN 3 THEN 'NPI 进度' ELSE '交付承诺' END,
       ROUND(-0.6+MOD(n*13,17)/10,2),
       DATE '2025-01-05'+n*5+14,
       CASE WHEN MOD(n,7)=0 THEN 'OVERDUE' WHEN MOD(n,3)=0 THEN 'OPEN' ELSE 'CLOSED' END,
       CASE MOD(n-1,4) WHEN 0 THEN '周宁' WHEN 1 THEN '陈晓' WHEN 2 THEN '林海' ELSE '孙悦' END
FROM (SELECT LEVEL n FROM dual CONNECT BY LEVEL<=96);

INSERT INTO f360_design_win
  (design_win_id,opportunity_no,customer_id,application_id,product_id,opportunity_stage,probability_pct,estimated_annual_wafers,target_sop_date,competitor_name,win_loss_reason,last_updated_date)
SELECT 1300000+n,'DW-'||TO_CHAR(1300000+n),1+MOD(n-1,8),101+MOD(n-1,10),
       CASE WHEN MOD(n,5)=0 THEN NULL ELSE 1001+MOD(n-1,12) END,
       CASE MOD(n,6) WHEN 0 THEN 'WON' WHEN 1 THEN 'QUALIFICATION' WHEN 2 THEN 'SAMPLE' WHEN 3 THEN 'RFQ' WHEN 4 THEN 'PROPOSAL' ELSE 'LOST' END,
       CASE MOD(n,6) WHEN 0 THEN 100 WHEN 1 THEN 75 WHEN 2 THEN 60 WHEN 3 THEN 35 WHEN 4 THEN 50 ELSE 0 END,
       6000+MOD(n*3700,42000),ADD_MONTHS(DATE '2026-01-01',MOD(n,18)),
       CASE MOD(n,4) WHEN 0 THEN 'Foundry Alpha' WHEN 1 THEN 'Foundry Beta' WHEN 2 THEN 'IDM Gamma' ELSE NULL END,
       CASE WHEN MOD(n,6)=0 THEN '车规平台认证通过' WHEN MOD(n,6)=5 THEN '报价竞争力不足' ELSE NULL END,
       DATE '2026-06-30'-MOD(n*7,120)
FROM (SELECT LEVEL n FROM dual CONNECT BY LEVEL<=24);

INSERT INTO f360_npi_milestone
  (milestone_id,product_id,milestone_type,planned_date,actual_date,milestone_status,owner_department,risk_level)
WITH milestones AS (
  SELECT 'TAPE_OUT' milestone_type,1 seq_no FROM dual UNION ALL
  SELECT 'FIRST_SILICON',2 FROM dual UNION ALL SELECT 'QUALIFICATION',3 FROM dual UNION ALL SELECT 'MASS_PRODUCTION',4 FROM dual
)
SELECT 1400000+ROW_NUMBER() OVER (ORDER BY p.product_id,m.seq_no),p.product_id,m.milestone_type,
       ADD_MONTHS(DATE '2024-01-01',MOD(p.product_id,12)+m.seq_no*3),
       CASE WHEN p.lifecycle_stage='NPI' AND m.seq_no>=3 THEN NULL
            ELSE ADD_MONTHS(DATE '2024-01-01',MOD(p.product_id,12)+m.seq_no*3)+CASE WHEN MOD(p.product_id+m.seq_no,5)=0 THEN 28 ELSE 4 END END,
       CASE WHEN p.lifecycle_stage='NPI' AND m.seq_no>=3 THEN 'PLANNED'
            WHEN MOD(p.product_id+m.seq_no,5)=0 THEN 'DELAYED' ELSE 'COMPLETED' END,
       CASE m.seq_no WHEN 1 THEN 'DESIGN_ENABLEMENT' WHEN 2 THEN 'PRODUCT_ENGINEERING' WHEN 3 THEN 'QUALITY' ELSE 'MANUFACTURING' END,
       CASE WHEN p.lifecycle_stage='NPI' AND m.seq_no>=3 THEN 'HIGH' WHEN MOD(p.product_id+m.seq_no,5)=0 THEN 'MEDIUM' ELSE 'LOW' END
FROM f360_product p CROSS JOIN milestones m;

INSERT INTO f360_equipment_downtime
  (downtime_id,fab_id,node_id,equipment_group,downtime_start,downtime_end,downtime_hours,downtime_type,lost_wafers)
SELECT 1500000+n,
       CASE MOD(n,3) WHEN 0 THEN 303 WHEN 1 THEN 301 ELSE 302 END,
       CASE MOD(n,6) WHEN 0 THEN 206 WHEN 1 THEN 201 WHEN 2 THEN 202 WHEN 3 THEN 203 WHEN 4 THEN 204 ELSE 205 END,
       CASE MOD(n,5) WHEN 0 THEN 'LITHO' WHEN 1 THEN 'ETCH' WHEN 2 THEN 'CVD' WHEN 3 THEN 'IMPLANT' ELSE 'CMP' END,
       TIMESTAMP '2025-01-01 08:00:00'+NUMTODSINTERVAL(n*7,'DAY'),
       TIMESTAMP '2025-01-01 08:00:00'+NUMTODSINTERVAL(n*7,'DAY')+NUMTODSINTERVAL(2+MOD(n*5,38),'HOUR'),
       2+MOD(n*5,38),CASE WHEN MOD(n,4)=0 THEN 'UNPLANNED' ELSE 'PLANNED' END,
       CASE WHEN MOD(n,4)=0 THEN 35+MOD(n*19,210) ELSE 5+MOD(n,30) END
FROM (SELECT LEVEL n FROM dual CONNECT BY LEVEL<=72);

INSERT INTO f360_customer_score_snapshot
  (score_id,customer_id,snapshot_date,revenue_score,growth_score,payment_score,quality_score,engagement_score,overall_score,risk_level)
SELECT 1600000+ROW_NUMBER() OVER (ORDER BY c.customer_id,q.q_no),c.customer_id,
       ADD_MONTHS(DATE '2024-03-31',(q.q_no-1)*3),
       58+MOD(c.customer_id*7+q.q_no*3,40),42+MOD(c.customer_id*11+q.q_no*7,55),
       65+MOD(c.customer_id*5+q.q_no,34),55+MOD(c.customer_id*13+q.q_no*2,42),
       50+MOD(c.customer_id*17+q.q_no*5,48),
       ROUND((58+MOD(c.customer_id*7+q.q_no*3,40))*0.30+(42+MOD(c.customer_id*11+q.q_no*7,55))*0.20+
             (65+MOD(c.customer_id*5+q.q_no,34))*0.15+(55+MOD(c.customer_id*13+q.q_no*2,42))*0.20+
             (50+MOD(c.customer_id*17+q.q_no*5,48))*0.15,2),
       CASE WHEN c.customer_id IN (6,8) AND q.q_no>=6 THEN 'HIGH' WHEN MOD(c.customer_id+q.q_no,5)=0 THEN 'MEDIUM' ELSE 'LOW' END
FROM f360_customer c CROSS JOIN (SELECT LEVEL q_no FROM dual CONNECT BY LEVEL<=8) q;

INSERT INTO f360_application_market
  (market_id,application_id,market_quarter,global_tam_usd,served_sam_usd,fab_revenue_usd,market_growth_pct)
SELECT 1700000+ROW_NUMBER() OVER (ORDER BY a.application_id,q.q_no),a.application_id,
       ADD_MONTHS(DATE '2024-01-01',(q.q_no-1)*3),
       800000000+MOD(a.application_id*17000000+q.q_no*53000000,4200000000),
       260000000+MOD(a.application_id*11000000+q.q_no*31000000,1600000000),
       18000000+MOD(a.application_id*700000+q.q_no*2900000,180000000),
       -4+MOD(a.application_id+q.q_no*3,31)
FROM f360_application a CROSS JOIN (SELECT LEVEL q_no FROM dual CONNECT BY LEVEL<=8) q;

COMMIT;
