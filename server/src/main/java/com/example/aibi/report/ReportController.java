package com.example.aibi.report;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    private final ReportExportService exports;

    public ReportController(ReportService service, ReportExportService exports) {
        this.service = service;
        this.exports = exports;
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody @Valid ReportRequest request) { return service.generate(request); }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue="sales") String domain) { return service.list(domain); }

    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable String id){return service.detail(id);}

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id, @RequestParam(defaultValue = "pdf") String format) {
        ReportExportService.ExportFile file = exports.export(id, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable String id){return service.delete(id);}
}
