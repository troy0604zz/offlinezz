package com.example.aibi.query;

import com.example.aibi.knowledge.KnowledgeChunk;
import com.example.aibi.training.SqlExampleMatch;
import com.example.aibi.training.TrainingService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SqlPromptFactory {
    private final TrainingService training;

    public SqlPromptFactory(TrainingService training) {
        this.training = training;
    }

    public SqlPrompt create(String knowledgeDomain, String question, List<KnowledgeChunk> context,
                            List<Map<String, Object>> metrics, List<Map<String, Object>> relations) {
        List<SqlExampleMatch> examples = training.relevantExamples(knowledgeDomain, question, context, 3);
        if (!examples.isEmpty() && examples.get(0).score() >= 0.78) {
            SqlExampleMatch match = examples.get(0);
            GeneratedQuery direct = new GeneratedQuery(match.sql(),
                    "命中管理员发布的高相似度标准 SQL：" + match.question(),
                    List.of("标准 SQL 相似度 " + String.format("%.2f", match.score())),
                    Math.min(0.99, match.score()));
            return new SqlPrompt(null, null, direct);
        }
        String system = """
                你是企业数据查询规划器，目标数据库为 Oracle Database 19c。
                只允许生成一个只读 SELECT 或 WITH ... SELECT，禁止 INSERT、UPDATE、DELETE、MERGE、DDL、PL/SQL 和多语句。
                只能使用上下文明示的表、字段、关系和指标口径，不要猜测不存在的对象。
                Oracle 语法要求：字符串拼接使用 ||，空值处理使用 COALESCE/NVL，行数限制使用 FETCH FIRST n ROWS ONLY，禁止 LIMIT。
                日期字面量使用 DATE 'YYYY-MM-DD'；按年月统计可使用 EXTRACT(YEAR/MONTH FROM date_column)。
                退款必须先按 order_id 聚合后再关联，避免一对多 Join 导致金额放大；销售额只统计 order_status='COMPLETED'。
                只输出严格 JSON：{"sql":"...","explanation":"...","assumptions":["..."],"confidence":0.0}。
                """;
        String user = "问题：" + question
                + "\n指标：" + metrics
                + "\n关系：" + relations
                + "\n管理员发布的相似标准 SQL：" + examples.stream().map(x -> Map.of(
                "question", x.question(), "sql", x.sql(), "score", x.score())).toList()
                + "\n检索上下文：" + context.stream().map(KnowledgeChunk::content).toList();
        return new SqlPrompt(system, user, null);
    }

    public record SqlPrompt(String system, String user, GeneratedQuery directMatch) {}
}
