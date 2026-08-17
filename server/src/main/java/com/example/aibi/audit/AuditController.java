package com.example.aibi.audit;

import com.example.aibi.common.DatabaseRows;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AuditController {
    private final JdbcClient jdbc;

    public AuditController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list() {
        return DatabaseRows.normalize(jdbc.sql("SELECT id,trace_id,event_type,actor,resource_type,resource_id,detail,created_at FROM audit_event ORDER BY id DESC")
                .query().listOfRows());
    }
}
