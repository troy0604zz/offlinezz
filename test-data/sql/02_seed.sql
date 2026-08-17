-- Oracle Database 19c demo data. All company names are fictional.
INSERT ALL
  INTO region VALUES (1,'EAST','华东')
  INTO region VALUES (2,'NORTH','华北')
  INTO region VALUES (3,'SOUTH','华南')
SELECT 1 FROM dual;

INSERT ALL
  INTO customer VALUES (101,'上海未来汽车有限公司',1,'新能源汽车','A',DATE '2024-01-10')
  INTO customer VALUES (102,'江苏智造科技有限公司',1,'工业制造','A',DATE '2024-03-15')
  INTO customer VALUES (103,'浙江云商集团',1,'互联网','B',DATE '2025-02-08')
  INTO customer VALUES (104,'北京北辰能源有限公司',2,'能源','A',DATE '2023-06-01')
  INTO customer VALUES (105,'广州南方零售集团',3,'零售','B',DATE '2025-04-12')
SELECT 1 FROM dual;

INSERT ALL
  INTO product VALUES (201,'企业数据平台标准版','软件',120000)
  INTO product VALUES (202,'企业数据平台专业版','软件',260000)
  INTO product VALUES (203,'实施咨询服务','服务',80000)
  INTO product VALUES (204,'年度运维服务','服务',50000)
SELECT 1 FROM dual;

INSERT ALL
  INTO sales_order VALUES (1001,101,DATE '2025-01-18','COMPLETED','张伟')
  INTO sales_order VALUES (1002,102,DATE '2025-03-06','COMPLETED','李娜')
  INTO sales_order VALUES (1003,103,DATE '2025-07-21','COMPLETED','张伟')
  INTO sales_order VALUES (1004,104,DATE '2025-09-11','COMPLETED','王强')
  INTO sales_order VALUES (1005,105,DATE '2025-11-19','CANCELLED','李娜')
  INTO sales_order VALUES (1006,101,DATE '2026-01-16','COMPLETED','张伟')
  INTO sales_order VALUES (1007,102,DATE '2026-02-22','COMPLETED','李娜')
  INTO sales_order VALUES (1008,103,DATE '2026-03-12','COMPLETED','张伟')
  INTO sales_order VALUES (1009,104,DATE '2026-04-08','COMPLETED','王强')
  INTO sales_order VALUES (1010,105,DATE '2026-05-20','COMPLETED','李娜')
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

COMMIT;
