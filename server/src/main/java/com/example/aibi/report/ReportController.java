package com.example.aibi.report;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) { this.service = service; }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody @Valid ReportRequest request) { return service.generate(request); }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue="sales") String domain) { return service.list(domain); }

    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable String id){return service.detail(id);}
}
