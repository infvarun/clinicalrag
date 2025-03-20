package com.pfizer.ai.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
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
    
    @Value("${spring.ai.vectorstore.neo4j.id-property:id}")
    private String idProperty;
    
    @Value("${spring.ai.vectorstore.neo4j.index-name:document_embeddings}")
    private String indexName;
    
    @Value("${spring.ai.vectorstore.neo4j.initialize-schema:true}")
    private boolean initializeSchema;
    
    @Value("${spring.ai.vectorstore.neo4j.database-name:}")
    private String databaseName;

    /**
     * Creates a customized Neo4j vector store that uses our CustomOpenAiClient
     * for embedding generation instead of the standard Spring AI client.
     */
    @Bean
    @Primary
    public CustomNeo4jVectorStore customVectorStore(Driver neo4jDriver, CustomOpenAiClient customOpenAiClient) {
        // Create a proper SessionConfig
        SessionConfig sessionConfig;
        
        // If a specific database name is provided, configure it
        if (databaseName != null && !databaseName.isEmpty()) {
            sessionConfig = SessionConfig.builder()
                .withDatabase(databaseName)
                .build();
        } else {
            sessionConfig = SessionConfig.defaultConfig();
        }
        
        // Create custom vector store with explicit SessionConfig
        CustomNeo4jVectorStore vectorStore = new CustomNeo4jVectorStore(
            neo4jDriver, 
            customOpenAiClient,
            sessionConfig,
            nodeLabel,
            textProperty,
            embeddingProperty,
            idProperty,
            indexName
        );
        
        // Create the schema if needed
        if (initializeSchema) {
            vectorStore.createSchema();
        }
        
        return vectorStore;
    }
}