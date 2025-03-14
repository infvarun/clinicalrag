package com.pfizer.ai.service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class RAGVectorProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(RAGVectorProcessorService.class);

    private static final String KEY_CUSTOM_CONTEXT = "customContext";

    private static final String KEY_QUESTION = "question";

    private static final int TOP_K = 4;

    private static final double SIMILARITY_THRESHOLD = 0.7;

    private PromptTemplate basicAugmentationTemplate;

    @Autowired
    private Neo4jVectorStore vectorStore;

    @Autowired
    @Qualifier("AIServiceImpl")
    private AIService aiService;

    public RAGVectorProcessorService() {
        var ragBasicPromptTemplate = new ClassPathResource("prompts/rag-basic-template.st");
        this.basicAugmentationTemplate = new PromptTemplate(ragBasicPromptTemplate);
    }
    
    private String retrieveCustomContext(String userPrompt, int topK) {
        try {
            var similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(userPrompt)
                    .topK(topK > 0 ? topK : TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .build()
            );
            var customContext = new StringBuilder();
            if (similarDocuments != null) {
                similarDocuments.forEach(document -> customContext.append(document.getMetadata().get("custom_keywords")).append(" "));
            }
            return customContext.toString();
        } catch (Exception e) {
            LOG.error(userPrompt, e);
            return "";
        }
    }

    private String augmentUserPrompt(String originalUserPrompt, String customContext) {
        var templateMap = new HashMap<String, Object>();

        templateMap.put(KEY_QUESTION, originalUserPrompt);
        templateMap.put(KEY_CUSTOM_CONTEXT, customContext);

        return basicAugmentationTemplate.render(templateMap);
    }

    public String generateRAGResponse(String systemPrompt, String userPrompt, int topK) {
        try {
            // Default to 5 if topK is 0 or negative
            int effectiveTopK = (topK <= 0) ? 5 : topK;
            
            // Get relevant documents using vector similarity search
            List<Document> relevantDocuments = vectorStore.similaritySearch(userPrompt);
            
            // If no documents found, return default response
            if (relevantDocuments.isEmpty()) {
                LOG.warn("No relevant documents found for query: {}", userPrompt);
                return "I don't know.";
            }
            
            // Build context from relevant documents
            StringBuilder contextBuilder = new StringBuilder();
            for (Document doc : relevantDocuments) {
                contextBuilder.append(doc.getFormattedContent()).append("\n\n");
            }
            String context = contextBuilder.toString();
            
            // Build prompt with context
            String fullSystemPrompt = systemPrompt + "\n\nContext information:\n" + context;
            
            // Generate response using OpenAI
            return aiService.generateBasicResponse(fullSystemPrompt, userPrompt);
        } catch (Exception e) {
            LOG.error("Error generating RAG response", e);
            return "I don't know.";
        }
    }

    public Flux<String> streamRAGResponse(String systemPrompt, String userPrompt, int topK) {
        var customContext = retrieveCustomContext(userPrompt, topK);
        var augmentedUserPrompt = augmentUserPrompt(userPrompt, customContext);

        return aiService.streamBasicResponse(systemPrompt, augmentedUserPrompt);
    }

    public boolean testNeo4jConnection() {
        try {
            // Use a simple Neo4j operation to test connection
            vectorStore.similaritySearch(SearchRequest.builder().query("test").topK(1).build());
            return true;
        } catch (Exception e) {
            LOG.error("Neo4j connection test failed", e);
            return false;
        }
    }

    public int getDocumentCount() {
        try {
            var result = vectorStore.similaritySearch(SearchRequest.builder().query("").topK(1000).build());
            return result.size();
        } catch (Exception e) {
            LOG.error("Failed to get document count", e);
            return -1;
        }
    }

    public boolean testEmbeddings(String testString) {
        try {
            var result = vectorStore.similaritySearch(SearchRequest.builder().query(testString).topK(1).build());
            return result.size() > 0;
        } catch (Exception e) {
            LOG.error("Failed to test embeddings", e);
            return false;
        }
    }

}
