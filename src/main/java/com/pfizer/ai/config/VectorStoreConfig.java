package com.pfizer.ai.config;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.neo4j.node-label:Document}")
    private String nodeLabel;

    @Value("${spring.ai.vectorstore.neo4j.text-property:content}")
    private String textProperty;

    @Value("${spring.ai.vectorstore.neo4j.embedding-property:embedding}")
    private String embeddingProperty;

    @Bean
    @Primary
    public CustomNeo4jVectorStore customVectorStore(Driver neo4jDriver, CustomOpenAiClient customOpenAiClient) {
        CustomNeo4jVectorStore vectorStore = new CustomNeo4jVectorStore(
            neo4jDriver, 
            customOpenAiClient,
            null, nodeLabel,
                        textProperty,
                        embeddingProperty, embeddingProperty
        );
        
        // Create the schema if needed
        vectorStore.createSchema();
        
        return vectorStore;
    }
}