-- Oracle Database 19c / SQL*Plus compatible demo business schema.
CREATE TABLE region (
  region_id NUMBER(19) PRIMARY KEY,
  region_code VARCHAR2(20 CHAR) UNIQUE NOT NULL,
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

CREATE INDEX idx_order_date ON sales_order(order_date);
CREATE INDEX idx_order_customer ON sales_order(customer_id);
CREATE INDEX idx_customer_region ON customer(region_id);
