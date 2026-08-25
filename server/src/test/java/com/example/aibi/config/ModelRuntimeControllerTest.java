package com.example.aibi.config;

import com.example.aibi.common.BusinessException;
import com.example.aibi.knowledge.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelRuntimeControllerTest {
    @Mock ModelRuntimeService runtime;
    @Mock KnowledgeService knowledge;
    ModelRuntimeController controller;

    @BeforeEach void setUp(){controller=new ModelRuntimeController(runtime,knowledge);}

    @Test
    void embeddingChangeAlwaysReindexesEvenWhenLegacyFlagIsFalse(){
        ModelRuntimeService.UpdateRequest request=new ModelRuntimeService.UpdateRequest(
                "ollama","qwen-api","qwen-max","qwen-embedding",false);
        ModelRuntimeService.RuntimeSelection previous=new ModelRuntimeService.RuntimeSelection(
                "ollama","ollama","qwen-max","qwen-embedding");
        when(runtime.update(request)).thenReturn(new ModelRuntimeService.UpdateResult(snapshot(),true,previous));

        Map<String,Object> result=controller.update(request);

        verify(knowledge).rebuildAllDomains();
        assertThat(result.get("knowledgeReindexed")).isEqualTo(true);
    }

    @Test
    void failedReindexRestoresPreviousRuntimeSelection(){
        ModelRuntimeService.UpdateRequest request=new ModelRuntimeService.UpdateRequest(
                "ollama","qwen-api","qwen-max","qwen-embedding",true);
        ModelRuntimeService.RuntimeSelection previous=new ModelRuntimeService.RuntimeSelection(
                "ollama","ollama","qwen-max","qwen-embedding");
        when(runtime.update(request)).thenReturn(new ModelRuntimeService.UpdateResult(snapshot(),true,previous));
        doThrow(new IllegalStateException("Qdrant unavailable")).when(knowledge).rebuildAllDomains();

        assertThatThrownBy(()->controller.update(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("切换已撤销");
        verify(runtime).restore(previous);
    }

    private ModelRuntimeService.RuntimeSnapshot snapshot(){
        return new ModelRuntimeService.RuntimeSnapshot("real","ollama","qwen-api","qwen-local","qwen-embedding",
                "qwen-local","bge-m3","qwen-max","qwen-embedding",true,"https://example.invalid",1024);
    }
}
