INSERT INTO sql_example(domain,question,sql_text,explanation,status)
VALUES (
  'sales',
  '客户净销售额排名 Top10',
  'WITH refund_by_order AS (SELECT order_id,SUM(refund_amount) refund_amount FROM refund WHERE refund_status=''COMPLETED'' GROUP BY order_id), order_amount AS (SELECT o.order_id,o.customer_id,SUM(i.quantity*i.unit_price-i.discount_amount) gross_sales,COALESCE(r.refund_amount,0) refund_amount FROM sales_order o JOIN sales_order_item i ON i.order_id=o.order_id LEFT JOIN refund_by_order r ON r.order_id=o.order_id WHERE o.order_status=''COMPLETED'' GROUP BY o.order_id,o.customer_id,r.refund_amount) SELECT c.customer_name,SUM(oa.gross_sales-oa.refund_amount) net_sales FROM order_amount oa JOIN customer c ON c.customer_id=oa.customer_id GROUP BY c.customer_name ORDER BY net_sales DESC FETCH FIRST 10 ROWS ONLY',
  '退款按订单预聚合，再按客户汇总净销售额并取前十。',
  'PUBLISHED'
);
