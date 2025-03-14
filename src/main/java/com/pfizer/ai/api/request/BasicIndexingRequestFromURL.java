package com.pfizer.ai.api.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BasicIndexingRequestFromURL(
        @NotBlank @Pattern(regexp = "^(?i)(http|https)://.*$") String url,
        @NotBlank String outputFilename,
        boolean appendIfFileExists,
        List<String> keywords) {

}
