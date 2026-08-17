CREATE TABLE IF NOT EXISTS region (
    region_id BIGINT PRIMARY KEY,
    region_code VARCHAR(20) NOT NULL UNIQUE,
    region_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    region_id BIGINT NOT NULL,
    industry VARCHAR(100),
    customer_level VARCHAR(20),
    created_date DATE NOT NULL,
    CONSTRAINT fk_customer_region FOREIGN KEY (region_id) REFERENCES region(region_id)
);

CREATE TABLE IF NOT EXISTS product (
    product_id BIGINT PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    unit_cost DECIMAL(18,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS sales_order (
    order_id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    order_status VARCHAR(30) NOT NULL,
    salesperson VARCHAR(100),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE IF NOT EXISTS sales_order_item (
    order_item_id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES sales_order(order_id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE TABLE IF NOT EXISTS refund (
    refund_id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    refund_date DATE NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL,
    refund_status VARCHAR(30) NOT NULL,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES sales_order(order_id)
);

INSERT INTO region(region_id, region_code, region_name) VALUES
(1, 'EAST', '华东'), (2, 'NORTH', '华北'), (3, 'SOUTH', '华南');

INSERT INTO customer(customer_id, customer_name, region_id, industry, customer_level, created_date) VALUES
(101, '上海未来汽车有限公司', 1, '新能源汽车', 'A', DATE '2024-01-10'),
(102, '江苏智造科技有限公司', 1, '工业制造', 'A', DATE '2024-03-15'),
(103, '浙江云商集团', 1, '互联网', 'B', DATE '2025-02-08'),
(104, '北京北辰能源有限公司', 2, '能源', 'A', DATE '2023-06-01'),
(105, '广州南方零售集团', 3, '零售', 'B', DATE '2025-04-12');

INSERT INTO product(product_id, product_name, category, unit_cost) VALUES
(201, '企业数据平台标准版', '软件', 120000.00),
(202, '企业数据平台专业版', '软件', 260000.00),
(203, '实施咨询服务', '服务', 80000.00),
(204, '年度运维服务', '服务', 50000.00);

INSERT INTO sales_order(order_id, customer_id, order_date, order_status, salesperson) VALUES
(1001, 101, DATE '2025-01-18', 'COMPLETED', '张伟'),
(1002, 102, DATE '2025-03-06', 'COMPLETED', '李娜'),
(1003, 103, DATE '2025-07-21', 'COMPLETED', '张伟'),
(1004, 104, DATE '2025-09-11', 'COMPLETED', '王强'),
(1005, 105, DATE '2025-11-19', 'CANCELLED', '李娜'),
(1006, 101, DATE '2026-01-16', 'COMPLETED', '张伟'),
(1007, 102, DATE '2026-02-22', 'COMPLETED', '李娜'),
(1008, 103, DATE '2026-03-12', 'COMPLETED', '张伟'),
(1009, 104, DATE '2026-04-08', 'COMPLETED', '王强'),
(1010, 105, DATE '2026-05-20', 'COMPLETED', '李娜');

INSERT INTO sales_order_item(order_item_id, order_id, product_id, quantity, unit_price, discount_amount) VALUES
(1,1001,201,2,200000,10000),(2,1001,203,1,100000,0),
(3,1002,202,1,400000,20000),(4,1002,204,1,80000,0),
(5,1003,201,1,210000,0),(6,1003,204,1,70000,0),
(7,1004,202,2,390000,30000),(8,1005,201,1,200000,0),
(9,1006,202,2,420000,20000),(10,1006,203,1,120000,0),
(11,1007,201,3,220000,30000),(12,1007,204,2,75000,0),
(13,1008,202,1,430000,10000),(14,1008,203,1,110000,0),
(15,1009,202,2,410000,40000),(16,1010,201,2,215000,10000);

INSERT INTO refund(refund_id, order_id, refund_date, refund_amount, refund_status) VALUES
(1,1002,DATE '2025-03-20',30000,'COMPLETED'),
(2,1006,DATE '2026-02-03',50000,'COMPLETED'),
(3,1008,DATE '2026-03-28',20000,'COMPLETED');

INSERT INTO semantic_metric(code,name,description,expression_sql,base_table,status,version) VALUES
('gross_sales','销售额','已完成订单的订单明细金额减去折扣','SUM(i.quantity * i.unit_price - i.discount_amount)','sales_order_item','PUBLISHED',1),
('net_sales','净销售额','销售额减去已完成退款；退款必须先按订单预聚合','SUM(i.quantity * i.unit_price - i.discount_amount) - COALESCE(SUM(r.refund_amount),0)','sales_order_item','PUBLISHED',1),
('customer_count','成交客户数','指定期间内至少有一笔已完成订单的去重客户数','COUNT(DISTINCT o.customer_id)','sales_order','PUBLISHED',1);

INSERT INTO semantic_relation(left_table,right_table,join_type,join_condition,cardinality,enabled) VALUES
('region','customer','INNER','region.region_id = customer.region_id','ONE_TO_MANY',TRUE),
('customer','sales_order','INNER','customer.customer_id = sales_order.customer_id','ONE_TO_MANY',TRUE),
('sales_order','sales_order_item','INNER','sales_order.order_id = sales_order_item.order_id','ONE_TO_MANY',TRUE),
('sales_order_item','product','INNER','sales_order_item.product_id = product.product_id','MANY_TO_ONE',TRUE),
('sales_order','refund','LEFT','sales_order.order_id = refund.order_id','ONE_TO_MANY',TRUE);

