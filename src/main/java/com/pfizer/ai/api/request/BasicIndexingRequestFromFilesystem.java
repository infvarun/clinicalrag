package com.pfizer.ai.api.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record BasicIndexingRequestFromFilesystem(
        @NotBlank String path,
        @NotBlank String outputFilename,
        boolean appendIfFileExists,
        List<String> keywords) {

}
