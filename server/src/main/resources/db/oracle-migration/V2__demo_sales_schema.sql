CREATE TABLE region (
    region_id NUMBER(19) PRIMARY KEY,
    region_code VARCHAR2(20 CHAR) NOT NULL UNIQUE,
    region_name VARCHAR2(100 CHAR) NOT NULL
);

CREATE TABLE customer (
    customer_id NUMBER(19) PRIMARY KEY,
    customer_name VARCHAR2(200 CHAR) NOT NULL,
    region_id NUMBER(19) NOT NULL,
    industry VARCHAR2(100 CHAR),
    customer_level VARCHAR2(20 CHAR),
    created_date DATE NOT NULL,
    CONSTRAINT fk_customer_region FOREIGN KEY (region_id) REFERENCES region(region_id)
);

CREATE TABLE product (
    product_id NUMBER(19) PRIMARY KEY,
    product_name VARCHAR2(200 CHAR) NOT NULL,
    category VARCHAR2(100 CHAR) NOT NULL,
    unit_cost NUMBER(18,2) NOT NULL
);

CREATE TABLE sales_order (
    order_id NUMBER(19) PRIMARY KEY,
    customer_id NUMBER(19) NOT NULL,
    order_date DATE NOT NULL,
    order_status VARCHAR2(30 CHAR) NOT NULL,
    salesperson VARCHAR2(100 CHAR),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE sales_order_item (
    order_item_id NUMBER(19) PRIMARY KEY,
    order_id NUMBER(19) NOT NULL,
    product_id NUMBER(19) NOT NULL,
    quantity NUMBER(10) NOT NULL,
    unit_price NUMBER(18,2) NOT NULL,
    discount_amount NUMBER(18,2) DEFAULT 0 NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES sales_order(order_id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE TABLE refund (
    refund_id NUMBER(19) PRIMARY KEY,
    order_id NUMBER(19) NOT NULL,
    refund_date DATE NOT NULL,
    refund_amount NUMBER(18,2) NOT NULL,
    refund_status VARCHAR2(30 CHAR) NOT NULL,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES sales_order(order_id)
);

INSERT ALL
  INTO region VALUES (1, 'EAST', '华东')
  INTO region VALUES (2, 'NORTH', '华北')
  INTO region VALUES (3, 'SOUTH', '华南')
SELECT 1 FROM dual;

INSERT ALL
  INTO customer VALUES (101, '上海未来汽车有限公司', 1, '新能源汽车', 'A', DATE '2024-01-10')
  INTO customer VALUES (102, '江苏智造科技有限公司', 1, '工业制造', 'A', DATE '2024-03-15')
  INTO customer VALUES (103, '浙江云商集团', 1, '互联网', 'B', DATE '2025-02-08')
  INTO customer VALUES (104, '北京北辰能源有限公司', 2, '能源', 'A', DATE '2023-06-01')
  INTO customer VALUES (105, '广州南方零售集团', 3, '零售', 'B', DATE '2025-04-12')
SELECT 1 FROM dual;

INSERT ALL
  INTO product VALUES (201, '企业数据平台标准版', '软件', 120000)
  INTO product VALUES (202, '企业数据平台专业版', '软件', 260000)
  INTO product VALUES (203, '实施咨询服务', '服务', 80000)
  INTO product VALUES (204, '年度运维服务', '服务', 50000)
SELECT 1 FROM dual;

INSERT ALL
  INTO sales_order VALUES (1001, 101, DATE '2025-01-18', 'COMPLETED', '张伟')
  INTO sales_order VALUES (1002, 102, DATE '2025-03-06', 'COMPLETED', '李娜')
  INTO sales_order VALUES (1003, 103, DATE '2025-07-21', 'COMPLETED', '张伟')
  INTO sales_order VALUES (1004, 104, DATE '2025-09-11', 'COMPLETED', '王强')
  INTO sales_order VALUES (1005, 105, DATE '2025-11-19', 'CANCELLED', '李娜')
  INTO sales_order VALUES (1006, 101, DATE '2026-01-16', 'COMPLETED', '张伟')
  INTO sales_order VALUES (1007, 102, DATE '2026-02-22', 'COMPLETED', '李娜')
  INTO sales_order VALUES (1008, 103, DATE '2026-03-12', 'COMPLETED', '张伟')
  INTO sales_order VALUES (1009, 104, DATE '2026-04-08', 'COMPLETED', '王强')
  INTO sales_order VALUES (1010, 105, DATE '2026-05-20', 'COMPLETED', '李娜')
SELECT 1 FROM dual;

INSERT ALL
  INTO sales_order_item VALUES (1,1001,201,2,200000,10000)
  INTO sales_order_item VALUES (2,1001,203,1,100000,0)
  INTO sales_order_item VALUES (3,1002,202,1,400000,20000)
  INTO sales_order_item VALUES (4,1002,204,1,80000,0)
  INTO sales_order_item VALUES (5,1003,201,1,210000,0)
  INTO sales_order_item VALUES (6,1003,204,1,70000,0)
  INTO sales_order_item VALUES (7,1004,202,2,390000,30000)
  INTO sales_order_item VALUES (8,1005,201,1,200000,0)
  INTO sales_order_item VALUES (9,1006,202,2,420000,20000)
  INTO sales_order_item VALUES (10,1006,203,1,120000,0)
  INTO sales_order_item VALUES (11,1007,201,3,220000,30000)
  INTO sales_order_item VALUES (12,1007,204,2,75000,0)
  INTO sales_order_item VALUES (13,1008,202,1,430000,10000)
  INTO sales_order_item VALUES (14,1008,203,1,110000,0)
  INTO sales_order_item VALUES (15,1009,202,2,410000,40000)
  INTO sales_order_item VALUES (16,1010,201,2,215000,10000)
SELECT 1 FROM dual;

INSERT ALL
  INTO refund VALUES (1,1002,DATE '2025-03-20',30000,'COMPLETED')
  INTO refund VALUES (2,1006,DATE '2026-02-03',50000,'COMPLETED')
  INTO refund VALUES (3,1008,DATE '2026-03-28',20000,'COMPLETED')
SELECT 1 FROM dual;

-- Oracle evaluates an identity expression once for all branches of an INSERT ALL.
-- Use separate statements for identity-backed tables so every row gets a new id.
INSERT INTO semantic_metric(code,name,description,expression_sql,base_table,status,version)
VALUES ('gross_sales','销售额','已完成订单的订单明细金额减去折扣','SUM(i.quantity * i.unit_price - i.discount_amount)','sales_order_item','PUBLISHED',1);
INSERT INTO semantic_metric(code,name,description,expression_sql,base_table,status,version)
VALUES ('net_sales','净销售额','销售额减去已完成退款；退款必须先按订单预聚合','SUM(i.quantity * i.unit_price - i.discount_amount) - COALESCE(SUM(r.refund_amount),0)','sales_order_item','PUBLISHED',1);
INSERT INTO semantic_metric(code,name,description,expression_sql,base_table,status,version)
VALUES ('customer_count','成交客户数','指定期间内至少有一笔已完成订单的去重客户数','COUNT(DISTINCT o.customer_id)','sales_order','PUBLISHED',1);

INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled)
VALUES ('region','customer','INNER','region.region_id = customer.region_id','ONE_TO_MANY',1);
INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled)
VALUES ('customer','sales_order','INNER','customer.customer_id = sales_order.customer_id','ONE_TO_MANY',1);
INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled)
VALUES ('sales_order','sales_order_item','INNER','sales_order.order_id = sales_order_item.order_id','ONE_TO_MANY',1);
INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled)
VALUES ('sales_order_item','product','INNER','sales_order_item.product_id = product.product_id','MANY_TO_ONE',1);
INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled)
VALUES ('sales_order','refund','LEFT','sales_order.order_id = refund.order_id','ONE_TO_MANY',1);

CREATE INDEX idx_order_date ON sales_order(order_date);
CREATE INDEX idx_order_customer ON sales_order(customer_id);
CREATE INDEX idx_customer_region ON customer(region_id);
