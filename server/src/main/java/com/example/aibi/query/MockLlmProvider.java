package com.example.aibi.query;

import com.example.aibi.knowledge.KnowledgeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import com.example.aibi.training.SqlExampleMatch;
import com.example.aibi.training.TrainingService;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmProvider implements LlmProvider {
    private final TrainingService training;

    public MockLlmProvider(TrainingService training) { this.training=training; }

    @Override
    public GeneratedQuery generateSql(String knowledgeDomain, String question, List<KnowledgeChunk> context,
                                      List<Map<String, Object>> metrics, List<Map<String, Object>> relations) {
        List<SqlExampleMatch> examples=training.relevantExamples(knowledgeDomain,question,1);
        if(!examples.isEmpty() && examples.get(0).score()>=0.72) {
            SqlExampleMatch match=examples.get(0);
            return new GeneratedQuery(match.sql(),"命中管理员发布的标准 SQL 案例："+match.question(),List.of("案例相似度="+String.format("%.2f",match.score())),0.99);
        }
        String normalized = question.replaceAll("\\s+", "");
        if (normalized.contains("华东") && normalized.contains("2026") &&
                (normalized.contains("按月") || normalized.contains("每月") || normalized.contains("月度"))) {
            return new GeneratedQuery(monthlyEast2026(), "按订单月份汇总华东区域已完成订单，订单级退款先预聚合，避免订单明细与退款多对多导致金额放大。",
                    List.of("销售额只统计 COMPLETED 订单", "净销售额=明细金额-折扣-已完成退款"), 0.98);
        }
        if (normalized.toLowerCase().contains("top") || normalized.contains("前十") || normalized.contains("排名")) {
            return new GeneratedQuery(topCustomers(), "按净销售额降序返回客户排名。",
                    List.of("统计所有样例年份", "取消订单不计入"), 0.95);
        }
        if (normalized.contains("客户数") || normalized.contains("多少客户")) {
            return new GeneratedQuery("SELECT COUNT(DISTINCT customer_id) AS customer_count FROM sales_order WHERE order_status='COMPLETED'",
                    "统计至少有一笔已完成订单的去重客户数。", List.of(), 0.96);
        }
        return new GeneratedQuery(salesByRegion(), "按区域汇总净销售额。",
                List.of("未明确时间范围，因此返回样例库全部期间"), 0.86);
    }

    @Override
    public String providerName() { return "deterministic-mock"; }

    private String monthlyEast2026() {
        return """
                WITH refund_by_order AS (
                    SELECT order_id, SUM(refund_amount) AS refund_amount
                    FROM refund WHERE refund_status='COMPLETED' GROUP BY order_id
                ), order_amount AS (
                    SELECT o.order_id, o.order_date, c.region_id,
                           SUM(i.quantity*i.unit_price-i.discount_amount) AS gross_sales,
                           COALESCE(r.refund_amount,0) AS refund_amount
                    FROM sales_order o
                    JOIN customer c ON c.customer_id=o.customer_id
                    JOIN sales_order_item i ON i.order_id=o.order_id
                    LEFT JOIN refund_by_order r ON r.order_id=o.order_id
                    WHERE o.order_status='COMPLETED'
                    GROUP BY o.order_id,o.order_date,c.region_id,r.refund_amount
                )
                SELECT EXTRACT(MONTH FROM oa.order_date) AS month_no,
                       SUM(oa.gross_sales-oa.refund_amount) AS net_sales
                FROM order_amount oa JOIN region rg ON rg.region_id=oa.region_id
                WHERE rg.region_name='华东' AND EXTRACT(YEAR FROM oa.order_date)=2026
                GROUP BY EXTRACT(MONTH FROM oa.order_date)
                ORDER BY month_no
                """;
    }

    private String topCustomers() {
        return """
                WITH refund_by_order AS (
                    SELECT order_id, SUM(refund_amount) refund_amount FROM refund
                    WHERE refund_status='COMPLETED' GROUP BY order_id
                ), order_amount AS (
                    SELECT o.order_id,o.customer_id,
                           SUM(i.quantity*i.unit_price-i.discount_amount) gross_sales,
                           COALESCE(r.refund_amount,0) refund_amount
                    FROM sales_order o JOIN sales_order_item i ON i.order_id=o.order_id
                    LEFT JOIN refund_by_order r ON r.order_id=o.order_id
                    WHERE o.order_status='COMPLETED'
                    GROUP BY o.order_id,o.customer_id,r.refund_amount
                )
                SELECT c.customer_name,SUM(oa.gross_sales-oa.refund_amount) net_sales
                FROM order_amount oa JOIN customer c ON c.customer_id=oa.customer_id
                GROUP BY c.customer_name ORDER BY net_sales DESC FETCH FIRST 10 ROWS ONLY
                """;
    }

    private String salesByRegion() {
        return """
                WITH refund_by_order AS (
                    SELECT order_id, SUM(refund_amount) refund_amount FROM refund
                    WHERE refund_status='COMPLETED' GROUP BY order_id
                ), order_amount AS (
                    SELECT o.order_id,c.region_id,
                           SUM(i.quantity*i.unit_price-i.discount_amount) gross_sales,
                           COALESCE(r.refund_amount,0) refund_amount
                    FROM sales_order o JOIN customer c ON c.customer_id=o.customer_id
                    JOIN sales_order_item i ON i.order_id=o.order_id
                    LEFT JOIN refund_by_order r ON r.order_id=o.order_id
                    WHERE o.order_status='COMPLETED'
                    GROUP BY o.order_id,c.region_id,r.refund_amount
                )
                SELECT rg.region_name,SUM(oa.gross_sales-oa.refund_amount) net_sales
                FROM order_amount oa JOIN region rg ON rg.region_id=oa.region_id
                GROUP BY rg.region_name ORDER BY net_sales DESC
                """;
    }
}
