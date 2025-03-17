package com.pfizer.ai.service;

import com.pfizer.ai.config.CustomOpenAiClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiService {
    private final CustomOpenAiClient openAiClient;
    
    public OpenAiService(CustomOpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }
    
    /**
     * Get a response from the OpenAI chat model
     */
    public String getChatResponse(String userMessage) {
        return openAiClient.generateChatCompletion(userMessage);
    }
    
    /**
     * Generate embeddings for text
     */
    public List<Float> getEmbeddings(String text) {
        return openAiClient.generateEmbeddings(text);
    }
}