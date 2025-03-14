package com.pfizer.ai.api.request;

import jakarta.validation.constraints.NotBlank;

public record AIPromptRequest (

    String systemPrompt,
    @NotBlank String userPrompt

) {

}
