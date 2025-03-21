package com.pfizer.ai.config;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;


@Configuration
public class OpenAICustomConfiguration {

    @Value("${ping.openai.client-id}")
    private String clientId;

    @Value("${ping.openai.client-secret}")
    private String clientSecret;

    @Value("${ping.openai.token-url}")
    private String tokenUrl;

    @Value("${ping.openai.api-base-url}")
    private String apiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModel;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PingFederateTokenProvider pingFederateTokenProvider(RestTemplate restTemplate) {
        return new PingFederateTokenProvider(restTemplate, clientId, clientSecret, tokenUrl);
    }

    @Bean
    @Primary
    public OpenAiApi customOpenAiApi(PingFederateTokenProvider tokenProvider) {
        ApiKey customApiKey = new ApiKey() {
            @Override
            public String getValue() {
                // Get token from PingFederate
                return tokenProvider.getAccessToken();
            }
        };

        return OpenAiApi.builder()
                .apiKey(customApiKey)
                .baseUrl(apiBaseUrl)
                .build();
    }
}