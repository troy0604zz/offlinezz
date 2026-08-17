package com.example.aibi.query;

import com.example.aibi.config.ModelRuntimeService;
import com.example.aibi.knowledge.KnowledgeChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "real")
public class QwenApiLlmProvider implements LlmProvider {
    private final QwenApiClient client;
    private final ModelRuntimeService runtime;
    private final GeneratedQueryParser parser;
    private final SqlPromptFactory prompts;

    public QwenApiLlmProvider(QwenApiClient client, ModelRuntimeService runtime, GeneratedQueryParser parser,
                              SqlPromptFactory prompts) {
        this.client = client;
        this.runtime = runtime;
        this.parser = parser;
        this.prompts = prompts;
    }

    @Override
    public GeneratedQuery generateSql(String knowledgeDomain, String question, List<KnowledgeChunk> context,
                                      List<Map<String, Object>> metrics, List<Map<String, Object>> relations) {
        SqlPromptFactory.SqlPrompt prompt = prompts.create(knowledgeDomain, question, context, metrics, relations);
        if (prompt.directMatch() != null) return prompt.directMatch();
        String content = client.chat(runtime.selection().qwenChatModel(), prompt.system(), prompt.user());
        return parser.parse(content, "千问官方 API");
    }

    @Override
    public String providerName() {
        return "qwen-api:" + runtime.selection().qwenChatModel();
    }
}
