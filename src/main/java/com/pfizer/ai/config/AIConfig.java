package com.pfizer.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AIConfig {
    @Bean("defaultRestClientBuilder")
    RestClient.Builder defaultRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean("openAIChatClient")
    ChatClient openAIChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    TextSplitter textSplitter() {
        return new TokenTextSplitter();
    }
}
