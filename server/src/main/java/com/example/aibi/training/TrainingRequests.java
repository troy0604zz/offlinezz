package com.example.aibi.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TrainingRequests {
    private TrainingRequests() {}

    public record SchemaAsset(@NotBlank String domain, @NotBlank @Size(max=200) String name,
                              @NotBlank String dialect, @NotBlank String ddlText, String description) {}
    public record Relation(@NotBlank String domain, @NotBlank String leftTable, @NotBlank String rightTable, @NotBlank String joinType,
                           @NotBlank String joinCondition, @NotBlank String cardinality) {}
    public record Synonym(@NotBlank String domain, @NotBlank String businessTerm,
                          @NotBlank String synonyms, @NotBlank String targetExpression) {}
    public record SqlExample(@NotBlank String domain, @NotBlank @Size(max=2000) String question,
                             @NotBlank String sql, String explanation) {}
    public record GoldenQuestion(@NotBlank String domain, @NotBlank @Size(max=2000) String question,
                                 String expectedSql, String expectedResultJson) {}
}
