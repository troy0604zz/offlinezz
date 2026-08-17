package com.example.aibi.config;

import com.example.aibi.knowledge.KnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/model-runtime")
public class ModelRuntimeController {
    private final ModelRuntimeService runtime;
    private final KnowledgeService knowledge;

    public ModelRuntimeController(ModelRuntimeService runtime, KnowledgeService knowledge) {
        this.runtime = runtime;
        this.knowledge = knowledge;
    }

    @GetMapping
    public ModelRuntimeService.RuntimeSnapshot get() {
        return runtime.snapshot();
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody ModelRuntimeService.UpdateRequest request) {
        ModelRuntimeService.UpdateResult result = runtime.update(request);
        boolean reindexed = result.embeddingChanged() && request.reindexKnowledge();
        if (reindexed) knowledge.restorePublishedIndex();
        return Map.of("runtime", result.runtime(), "embeddingChanged", result.embeddingChanged(),
                "knowledgeReindexed", reindexed);
    }
}
