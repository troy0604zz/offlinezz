package com.example.aibi.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class GeneratedQueryParser {
    private final ObjectMapper mapper;

    public GeneratedQueryParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public GeneratedQuery parse(String content, String providerLabel) {
        try {
            String normalized = content == null ? "" : content.trim();
            if (normalized.startsWith("```")) {
                normalized = normalized.replaceFirst("^```(?:json)?\\s*", "")
                        .replaceFirst("\\s*```$", "").trim();
            }
            int first = normalized.indexOf('{');
            int last = normalized.lastIndexOf('}');
            if (first >= 0 && last > first) normalized = normalized.substring(first, last + 1);
            return mapper.readValue(normalized, GeneratedQuery.class);
        } catch (Exception ex) {
            throw new IllegalStateException(providerLabel + " 结构化响应解析失败", ex);
        }
    }
}
