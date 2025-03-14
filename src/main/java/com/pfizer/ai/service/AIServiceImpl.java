package com.pfizer.ai.service;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;

@Service
public class AIServiceImpl implements AIService {

    private final ChatClient chatClient;

    public AIServiceImpl(@Qualifier("openAIChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generateBasicResponse(String systemPrompt, String userPrompt) {
        Assert.hasText(userPrompt, "User prompt must not be empty");
        return this.chatClient.prompt()
                .system(Optional.ofNullable(systemPrompt).orElse(""))
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public Flux<String> streamBasicResponse(String systemPrompt, String userPrompt) {
        Assert.hasText(userPrompt, "User prompt must not be empty");

        return this.chatClient.prompt()
                .system(Optional.ofNullable(systemPrompt).orElse(""))
                .user(userPrompt)
                .stream()
                .content();
    }

}
