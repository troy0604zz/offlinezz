package com.example.aibi.report;

import com.example.aibi.common.DatabaseRows;
import com.example.aibi.query.AskRequest;
import com.example.aibi.query.QueryAnswer;
import com.example.aibi.query.QueryOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {
    private final QueryOrchestrator orchestrator;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public ReportService(QueryOrchestrator orchestrator, JdbcClient jdbc, ObjectMapper mapper) {
        this.orchestrator = orchestrator;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> generate(ReportRequest request) {
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO report_job(id,title,request_text,status) VALUES(?,?,?,'GENERATING')")
                .params(id, request.title(), request.request()).update();
        try {
            String domain = request.knowledgeDomain() == null ? "sales" : request.knowledgeDomain();
            QueryAnswer trend = orchestrator.ask(new AskRequest("查询2026年华东区域每月净销售额", domain));
            QueryAnswer ranking = orchestrator.ask(new AskRequest("客户净销售额排名 Top10", domain));
            Map<String, Object> report = Map.of(
                    "id", id,
                    "title", request.title(),
                    "executiveSummary", "本报告由受控查询工作流生成。所有指标均保留 SQL、口径、数据表和执行结果证据。",
                    "sections", List.of(
                            Map.of("title", "月度趋势", "query", trend),
                            Map.of("title", "客户排名", "query", ranking)),
                    "recommendations", List.of("核对月度异常波动对应的客户和产品", "业务发布前由指标 Owner 复核口径"));
            jdbc.sql("UPDATE report_job SET status='READY',content_json=? WHERE id=?")
                    .params(mapper.writeValueAsString(report), id).update();
            return report;
        } catch (Exception ex) {
            jdbc.sql("UPDATE report_job SET status='FAILED' WHERE id=?").param(id).update();
            throw new IllegalStateException("报告生成失败：" + ex.getMessage(), ex);
        }
    }

    public List<Map<String, Object>> list() {
        return DatabaseRows.normalize(jdbc.sql("SELECT id,title,request_text,status,created_at FROM report_job ORDER BY created_at DESC")
                .query().listOfRows());
    }
}
