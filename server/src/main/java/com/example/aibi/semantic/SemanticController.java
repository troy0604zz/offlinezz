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
    public List<Map<String, Object>> metrics(@RequestParam String domain) { return service.metrics(domain); }

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody @Valid MetricRequest request) { return service.createMetric(request); }

    @PutMapping("/metrics/{id}")
    public Map<String,Object> update(@PathVariable long id,@RequestBody @Valid MetricRequest request){ return service.updateMetric(id,request); }

    @DeleteMapping("/metrics/{id}")
    public Map<String,Object> delete(@PathVariable long id,@RequestParam String domain){ return service.deleteMetric(id,domain); }

    @PostMapping("/metrics/{id}/publish")
    public Map<String, Object> publish(@PathVariable long id,@RequestParam String domain) { return service.publish(id,domain); }

    @GetMapping("/relations")
    public List<Map<String, Object>> relations(@RequestParam String domain) { return service.relations(domain); }

    @PostMapping("/relations")
    public Map<String,Object> relation(@RequestBody @Valid com.example.aibi.training.TrainingRequests.Relation request) {
        return service.createRelation(request);
    }

    @PutMapping("/relations/{id}")
    public Map<String,Object> updateRelation(@PathVariable long id,@RequestBody @Valid com.example.aibi.training.TrainingRequests.Relation request){ return service.updateRelation(id,request); }

    @DeleteMapping("/relations/{id}")
    public Map<String,Object> deleteRelation(@PathVariable long id,@RequestParam String domain){ return service.deleteRelation(id,domain); }
}
