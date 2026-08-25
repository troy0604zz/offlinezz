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

    public TrainingController(TrainingService service, TrainingEvaluationService evaluation) {
        this.service = service;
        this.evaluation = evaluation;
    }

    @GetMapping("/dashboard") public Map<String,Object> dashboard(@RequestParam(defaultValue="sales") String domain){ return service.dashboard(domain); }
    @GetMapping("/schemas") public List<Map<String,Object>> schemas(@RequestParam String domain){ return service.schemas(domain); }
    @PostMapping("/schemas") public Map<String,Object> schema(@RequestBody @Valid TrainingRequests.SchemaAsset r){ return service.createSchema(r); }
    @PutMapping("/schemas/{id}") public Map<String,Object> updateSchema(@PathVariable long id,@RequestBody @Valid TrainingRequests.SchemaAsset r){ return service.updateSchema(id,r); }
    @DeleteMapping("/schemas/{id}") public Map<String,Object> deleteSchema(@PathVariable long id,@RequestParam String domain){ return service.deleteSchema(id,domain); }
    @GetMapping("/synonyms") public List<Map<String,Object>> synonyms(@RequestParam String domain){ return service.synonyms(domain); }
    @PostMapping("/synonyms") public Map<String,Object> synonym(@RequestBody @Valid TrainingRequests.Synonym r){ return service.createSynonym(r); }
    @PutMapping("/synonyms/{id}") public Map<String,Object> updateSynonym(@PathVariable long id,@RequestBody @Valid TrainingRequests.Synonym r){ return service.updateSynonym(id,r); }
    @DeleteMapping("/synonyms/{id}") public Map<String,Object> deleteSynonym(@PathVariable long id,@RequestParam String domain){ return service.deleteSynonym(id,domain); }
    @GetMapping("/sql-examples") public List<Map<String,Object>> examples(@RequestParam String domain){ return service.examples(domain); }
    @PostMapping("/sql-examples") public Map<String,Object> example(@RequestBody @Valid TrainingRequests.SqlExample r){ return service.createExample(r); }
    @PutMapping("/sql-examples/{id}") public Map<String,Object> updateExample(@PathVariable long id,@RequestBody @Valid TrainingRequests.SqlExample r){ return service.updateExample(id,r); }
    @DeleteMapping("/sql-examples/{id}") public Map<String,Object> deleteExample(@PathVariable long id,@RequestParam String domain){ return service.deleteExample(id,domain); }
    @PostMapping("/sql-examples/{id}/publish") public Map<String,Object> publish(@PathVariable long id,@RequestParam String domain){ return service.publishExample(id,domain); }
    @GetMapping("/golden-questions") public List<Map<String,Object>> golden(@RequestParam String domain){ return service.goldenQuestions(domain); }
    @PostMapping("/golden-questions") public Map<String,Object> golden(@RequestBody @Valid TrainingRequests.GoldenQuestion r){ return service.createGolden(r); }
    @PutMapping("/golden-questions/{id}") public Map<String,Object> updateGolden(@PathVariable long id,@RequestBody @Valid TrainingRequests.GoldenQuestion r){ return service.updateGolden(id,r); }
    @DeleteMapping("/golden-questions/{id}") public Map<String,Object> deleteGolden(@PathVariable long id,@RequestParam String domain){ return service.deleteGolden(id,domain); }
    @PostMapping("/golden-questions/{id}/run") public Map<String,Object> run(@PathVariable long id,@RequestParam String domain){ return evaluation.run(id,domain); }
    @GetMapping("/feedback") public List<Map<String,Object>> feedback(@RequestParam String domain){ return service.feedback(domain); }
    @PostMapping("/feedback/{id}/promote") public Map<String,Object> promote(@PathVariable long id,@RequestParam String domain){ return service.promoteFeedback(id,domain); }
}
