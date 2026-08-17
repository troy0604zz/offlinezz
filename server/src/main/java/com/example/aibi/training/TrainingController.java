package com.example.aibi.training;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/training")
public class TrainingController {
    private final TrainingService service;
    private final TrainingEvaluationService evaluation;

    public TrainingController(TrainingService service,TrainingEvaluationService evaluation) { this.service=service; this.evaluation=evaluation; }

    @GetMapping("/dashboard") public Map<String,Object> dashboard(){ return service.dashboard(); }
    @GetMapping("/schemas") public List<Map<String,Object>> schemas(){ return service.schemas(); }
    @PostMapping("/schemas") public Map<String,Object> schema(@RequestBody @Valid TrainingRequests.SchemaAsset r){ return service.createSchema(r); }
    @GetMapping("/synonyms") public List<Map<String,Object>> synonyms(){ return service.synonyms(); }
    @PostMapping("/synonyms") public Map<String,Object> synonym(@RequestBody @Valid TrainingRequests.Synonym r){ return service.createSynonym(r); }
    @GetMapping("/sql-examples") public List<Map<String,Object>> examples(){ return service.examples(); }
    @PostMapping("/sql-examples") public Map<String,Object> example(@RequestBody @Valid TrainingRequests.SqlExample r){ return service.createExample(r); }
    @PostMapping("/sql-examples/{id}/publish") public Map<String,Object> publish(@PathVariable long id){ return service.publishExample(id); }
    @GetMapping("/golden-questions") public List<Map<String,Object>> golden(){ return service.goldenQuestions(); }
    @PostMapping("/golden-questions") public Map<String,Object> golden(@RequestBody @Valid TrainingRequests.GoldenQuestion r){ return service.createGolden(r); }
    @PostMapping("/golden-questions/{id}/run") public Map<String,Object> run(@PathVariable long id){ return evaluation.run(id); }
    @GetMapping("/feedback") public List<Map<String,Object>> feedback(){ return service.feedback(); }
    @PostMapping("/feedback/{id}/promote") public Map<String,Object> promote(@PathVariable long id){ return service.promoteFeedback(id); }
}
