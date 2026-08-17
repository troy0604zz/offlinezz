package com.example.aibi.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class QueryController {
    private final QueryOrchestrator orchestrator;
    private final QueryExportService exports;

    public QueryController(QueryOrchestrator orchestrator, QueryExportService exports) {
        this.orchestrator = orchestrator;
        this.exports = exports;
    }

    @PostMapping("/questions")
    public QueryAnswer ask(@RequestBody @Valid AskRequest request) { return orchestrator.ask(request); }

    @GetMapping("/query-runs")
    public List<Map<String, Object>> history() { return orchestrator.history(); }

    @PostMapping("/query-runs/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable String id, @RequestBody @Valid FeedbackRequest request) {
        return orchestrator.feedback(id, request.rating(), request.comment(), request.correctedSql());
    }

    @GetMapping("/query-runs/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id, @RequestParam String format) {
        QueryExportService.ExportFile file = exports.export(id, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    public record FeedbackRequest(@Min(1) @Max(5) int rating, String comment, String correctedSql) {}
}
