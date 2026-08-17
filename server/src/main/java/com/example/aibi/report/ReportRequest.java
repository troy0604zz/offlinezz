package com.example.aibi.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportRequest(@NotBlank @Size(max = 500) String title,
                            @NotBlank @Size(max = 2000) String request,
                            String knowledgeDomain) {}

