package com.pfizer.ai.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Service
public class CustomOpenAiClient {
    private static final Logger logger = LoggerFactory.getLogger(CustomOpenAiClient.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final int embeddingDimensions;

    // PingFederate auth properties
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    
    private String accessToken;
    private Instant tokenExpiry;

    public CustomOpenAiClient(
            @Value("${ping.openai.client-id}") String clientId,
            @Value("${ping.openai.client-secret}") String clientSecret,
            @Value("${ping.openai.token-url}") String tokenUrl,
            @Value("${ping.openai.api-base-url}") String apiBaseUrl,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String chatModel,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String embeddingModel,
            @Value("${spring.ai.openai.embedding.options.dimensions:1536}") int embeddingDimensions) {
        
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.apiBaseUrl = apiBaseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        
        logger.info("Custom OpenAI client initialized with API URL: {}", apiBaseUrl);
    }

    /**
     * Generate a chat completion using the OpenAI API
     */
    public String generateChatCompletion(String userMessage) {
        String accessToken = getAccessToken();
        
        // Prepare the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        
        // Prepare the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", chatModel);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        
        requestBody.put("messages", messages);
        
        // Create the HTTP entity
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        // Send the request
        String url = apiBaseUrl + "/chat/completions";
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
        );
        
        // Process the response
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();
            // Extract response based on OpenAI's structure
            try {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            } catch (Exception e) {
                logger.error("Error parsing OpenAI response", e);
            }
        }
        
        return "Error: Failed to generate response";
    }
    
    /**
     * Generate embeddings for a text using the OpenAI API
     */
    public List<Float> generateEmbeddings(String text) {
        String accessToken = getAccessToken();
        
        // Prepare headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        
        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", embeddingModel);
        requestBody.put("input", text);
        requestBody.put("dimensions", embeddingDimensions);
        
        // Create HTTP entity
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        // Send request
        String url = apiBaseUrl + "/embeddings";
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
        );
        
        // Process response
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();
            try {
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                if (data != null && !data.isEmpty()) {
                    return (List<Float>) data.get(0).get("embedding");
                }
            } catch (Exception e) {
                logger.error("Error parsing embedding response", e);
            }
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Get a valid access token, refreshing if necessary
     */
    private synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            logger.debug("Token expired or not initialized, refreshing PingFederate access token");
            refreshToken();
        }
        return accessToken;
    }
    
    /**
     * Refresh the access token from PingFederate
     */
    private void refreshToken() {
        logger.debug("Requesting new token from PingFederate");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse token = response.getBody();
                this.accessToken = token.accessToken;
                this.tokenExpiry = Instant.now().plusSeconds(token.expiresIn - 60);
                logger.info("Successfully obtained new PingFederate access token, valid until: {}", tokenExpiry);
            } else {
                logger.error("Failed to obtain access token, status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to obtain access token from PingFederate");
            }
        } catch (Exception e) {
            logger.error("Error during token acquisition from PingFederate", e);
            throw new RuntimeException("Failed to obtain access token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Response structure for OAuth2 token endpoint
     */
    private static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("expires_in")
        private int expiresIn;
    }
}