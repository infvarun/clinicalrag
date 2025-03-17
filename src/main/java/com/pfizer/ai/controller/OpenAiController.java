package com.pfizer.ai.controller;

import com.pfizer.ai.service.OpenAiService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/openai/v1")
public class OpenAiController {
    private final OpenAiService openAiService;
    
    public OpenAiController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @GetMapping(path = "/v1/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello() {
        return "Hello, AI!";
    }
    
    
    @PostMapping("/chat")
    public Map<String, String> generateChatResponse(@RequestBody Map<String, String> request) {
        String response = openAiService.getChatResponse(request.get("message"));
        return Map.of("response", response);
    }
    
    @PostMapping("/embeddings")
    public Map<String, List<Float>> generateEmbeddings(@RequestBody Map<String, String> request) {
        List<Float> embeddings = openAiService.getEmbeddings(request.get("text"));
        return Map.of("embeddings", embeddings);
    }
}