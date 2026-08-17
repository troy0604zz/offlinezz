package com.example.aibi.config;

import com.example.aibi.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRuntimeServiceTest {
    @Test
    void keepsOllamaAsDefaultAndNeverExposesApiKey() {
        ModelRuntimeService service = new ModelRuntimeService(properties("secret-value"));

        assertThat(service.snapshot().activeChatProvider()).isEqualTo("ollama");
        assertThat(service.snapshot().activeEmbeddingProvider()).isEqualTo("ollama");
        assertThat(service.snapshot().qwenApiConfigured()).isTrue();
        assertThat(service.snapshot().toString()).doesNotContain("secret-value");
    }

    @Test
    void rejectsQwenSwitchWithoutServerSideKey() {
        ModelRuntimeService service = new ModelRuntimeService(properties(""));
        ModelRuntimeService.UpdateRequest request = new ModelRuntimeService.UpdateRequest(
                "qwen-api", "ollama", "qwen3.8-max", "qwen3.7-text-embedding", true);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DASHSCOPE_API_KEY");
    }

    private AiBiProperties properties(String key) {
        return new AiBiProperties(new AiBiProperties.Ai("real", "ollama", "ollama",
                new AiBiProperties.Ollama("http://localhost:11434", "qwen-local", "bge-m3", 8192, 120),
                new AiBiProperties.QwenApi("https://dashscope.aliyuncs.com/compatible-mode/v1", key,
                        "qwen3.8-max", "qwen3.7-text-embedding", 1024, 120),
                new AiBiProperties.Qdrant("http://localhost:6333", "", "aibi", 1024)),
                new AiBiProperties.Query(200, 1000, 15), new AiBiProperties.Storage("./data"));
    }
}
