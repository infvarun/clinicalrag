package com.pfizer.ai.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody; // Change this import
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pfizer.ai.api.request.AIPromptRequest;
import com.pfizer.ai.service.AIService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AIBasicApi {

    @Autowired
    @Qualifier("AIServiceImpl")
    private AIService aiService;
    
    @GetMapping(path = "/v1/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello() {
        return "Hello, AI!";
    }

    @PostMapping(path = "/v1/basic", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> basicAI(@RequestBody @Valid AIPromptRequest request) { // Now using Spring's @RequestBody
        var response = aiService.generateBasicResponse(request.systemPrompt(), request.userPrompt());
        return Mono.just(response);
    }

    @PostMapping(path = "/v1/basic/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> basicStreamAI(@RequestBody @Valid AIPromptRequest request) { // Now using Spring's @RequestBody
        var response = aiService.streamBasicResponse(request.systemPrompt(), request.userPrompt());
        return response;
    }
}
