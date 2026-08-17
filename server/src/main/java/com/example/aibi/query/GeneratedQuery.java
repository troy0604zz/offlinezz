package com.example.aibi.query;

import java.util.List;

public record GeneratedQuery(String sql, String explanation, List<String> assumptions, double confidence) {}

