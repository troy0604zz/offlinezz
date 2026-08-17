package com.example.aibi.semantic;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/semantic")
public class SemanticController {
    private final SemanticService service;

    public SemanticController(SemanticService service) { this.service = service; }

    @GetMapping("/metrics")
    public List<Map<String, Object>> metrics() { return service.metrics(); }

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody @Valid MetricRequest request) { return service.createMetric(request); }

    @PostMapping("/metrics/{id}/publish")
    public Map<String, Object> publish(@PathVariable long id) { return service.publish(id); }

    @GetMapping("/relations")
    public List<Map<String, Object>> relations() { return service.relations(); }

    @PostMapping("/relations")
    public Map<String,Object> relation(@RequestBody @Valid com.example.aibi.training.TrainingRequests.Relation request) {
        return service.createRelation(request);
    }
}
