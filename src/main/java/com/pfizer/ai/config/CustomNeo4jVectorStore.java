package com.pfizer.ai.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.neo4j.filter.Neo4jVectorFilterExpressionConverter;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom implementation of Neo4jVectorStore that uses a CustomOpenAiClient for embeddings
 * rather than the default Spring AI OpenAI client.
 */
public class CustomNeo4jVectorStore implements VectorStore {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomNeo4jVectorStore.class);
    
    // Constants copied from Neo4jVectorStore
    private static final int DEFAULT_TRANSACTION_SIZE = 10_000;
    private static final String DEFAULT_LABEL = "Document";
    private static final String DEFAULT_INDEX_NAME = "spring-ai-document-index";
    private static final String DEFAULT_EMBEDDING_PROPERTY = "embedding";
    private static final String DEFAULT_ID_PROPERTY = "id";
    
    private final Driver driver;
    private final SessionConfig sessionConfig;
    private final CustomOpenAiClient customOpenAiClient;
    private final String label;
    private final String embeddingProperty;
    private final String idProperty;
    private final String indexName;
    private final Neo4jVectorFilterExpressionConverter filterExpressionConverter = new Neo4jVectorFilterExpressionConverter();
    
    /**
     * Create a custom Neo4j vector store with a custom OpenAI client
     */
    public CustomNeo4jVectorStore(Driver driver, CustomOpenAiClient customOpenAiClient) {
        this(driver, 
             customOpenAiClient, 
             SessionConfig.defaultConfig(),
             DEFAULT_LABEL,
             DEFAULT_EMBEDDING_PROPERTY,
             DEFAULT_ID_PROPERTY,
             DEFAULT_INDEX_NAME);
    }
    
    /**
     * Create a fully customized Neo4j vector store
     */
    public CustomNeo4jVectorStore(
            Driver driver, 
            CustomOpenAiClient customOpenAiClient,
            SessionConfig sessionConfig,
            String label,
            String embeddingProperty,
            String idProperty,
            String indexName) {
        
        this.driver = driver;
        this.customOpenAiClient = customOpenAiClient;
        this.sessionConfig = sessionConfig;
        this.label = label;
        this.embeddingProperty = embeddingProperty;
        this.idProperty = idProperty;
        this.indexName = indexName;
        
        logger.info("CustomNeo4jVectorStore initialized with label: {}, indexName: {}", label, indexName);
    }
    
    /**
     * Add documents to the vector store with custom embeddings
     */
    @Override
    public void add(List<Document> documents) {
        if (documents.isEmpty()) {
            return;
        }
        
        logger.debug("Adding {} documents to vector store", documents.size());
        
        // Process documents in batches to avoid memory issues
        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            
            // Generate embeddings using custom client
            List<List<Float>> embeddings = batch.stream()
                .map(doc -> customOpenAiClient.generateEmbeddings(doc.getFormattedContent()))
                .collect(Collectors.toList());
            
            // Create records for Neo4j
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                Document doc = batch.get(j);
                List<Float> embedding = embeddings.get(j);
                rows.add(documentToRecord(doc, embedding));
            }
            
            // Store in Neo4j
            try (var session = driver.session(sessionConfig)) {
                var statement = """
                    UNWIND $rows AS row
                    MERGE (u:%s {%s: row.id})
                    SET u += row.properties
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row[$embeddingProperty])
                    """.formatted(label, idProperty);
                
                session.executeWrite(tx -> tx.run(statement, 
                        Map.of("rows", rows, "embeddingProperty", embeddingProperty))
                    .consume());
            }
            
            logger.debug("Added batch of {} documents to Neo4j", batch.size());
        }
    }
    
   
    
    /**
     * Delete multiple documents by ID list
     */
    @Override
    public void delete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        
        logger.debug("Deleting {} documents by ID", idList.size());
        
        try (var session = driver.session(sessionConfig)) {
            session.executeWrite(tx -> {
                // Delete all nodes with IDs in the list
                String cypher = """
                    UNWIND $ids AS id
                    MATCH (node:%s {%s: id})
                    DETACH DELETE node
                    """.formatted(label, idProperty);
                
                return tx.run(cypher, Map.of("ids", idList)).consume();
            });
            
            logger.debug("Successfully deleted {} documents", idList.size());
        }
        catch (Exception e) {
            logger.error("Error batch deleting documents: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete documents", e);
        }
    }
    /**
     * Delete documents matching a filter expression
     */
    @Override
    public void delete(@NonNull Expression filterExpression) {
        logger.debug("Deleting documents by filter expression");
        
        try (var session = driver.session(sessionConfig)) {
            session.executeWrite(tx -> {
                // Convert the filter expression to a Cypher WHERE clause
                String whereClause = filterExpressionConverter.convertExpression(filterExpression);
                
                // Delete all nodes that match the filter
                String cypher = """
                    MATCH (node:%s)
                    WHERE %s
                    DETACH DELETE node
                    """.formatted(label, whereClause);
                
                return tx.run(cypher).consume();
            });
            
            logger.debug("Successfully deleted documents matching filter");
        }
        catch (Exception e) {
            logger.error("Error deleting documents by filter: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete documents by filter", e);
        }
    }

    /**
     * Similarity search using query text
     */
    @Override
    public List<Document> similaritySearch(String query) {
        logger.debug("Performing similarity search for query: {}", query);
        return similaritySearch(SearchRequest.builder().query(query).build());
    }
    
    /**
     * Similarity search with advanced options
     */
    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        logger.debug("Performing similarity search with request: {}", request);
        
        // Get embedding from custom OpenAI client
        List<Float> queryEmbedding = customOpenAiClient.generateEmbeddings(request.getQuery());
        
        try (var session = driver.session(sessionConfig)) {
            StringBuilder condition = new StringBuilder("score >= $threshold");
            if (request.hasFilterExpression()) {
                condition.append(" AND ")
                    .append(filterExpressionConverter.convertExpression(request.getFilterExpression()));
            }
            
            // Using Neo4j's vector index for similarity search
            String query = """
                CALL db.index.vector.queryNodes($indexName, $numberOfNearestNeighbours, $embeddingValue)
                YIELD node, score
                WHERE %s
                RETURN node, score
                """.formatted(condition);
            
            return session.executeRead(tx -> tx
                .run(query,
                    Map.of(
                        "indexName", indexName, 
                        "numberOfNearestNeighbours", request.getTopK(),
                        "embeddingValue", queryEmbedding, 
                        "threshold", request.getSimilarityThreshold()))
                .list(this::recordToDocument));
        }
        catch (Exception e) {
            logger.error("Error during similarity search: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Create the Neo4j schema (vector index) if it doesn't exist
     */
    public void createSchema() {
        logger.info("Initializing Neo4j schema for vector search");
        
        try (var session = driver.session(sessionConfig)) {
            // Create constraint to ensure uniqueness of documents
            session.executeWriteWithoutResult(tx -> {
                // Create unique constraint on ID
                tx.run("""
                    CREATE CONSTRAINT %s_unique_idx IF NOT EXISTS 
                    FOR (n:%s) REQUIRE n.%s IS UNIQUE
                    """.formatted(label, label, idProperty)).consume();
                
                // Create vector index - assuming 1536 dimensions for OpenAI embeddings
                var indexStatement = """
                    CREATE VECTOR INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)
                    OPTIONS {indexConfig: {
                        `vector.dimensions`: 1536,
                        `vector.similarity_function`: 'cosine'
                    }}
                    """.formatted(indexName, label, embeddingProperty);
                
                tx.run(indexStatement).consume();
            });
            
            // Wait for indexes to be available
            session.run("CALL db.awaitIndexes()").consume();
            
            logger.info("Neo4j schema initialization complete");
        }
        catch (Exception e) {
            logger.error("Error initializing Neo4j schema: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Neo4j schema", e);
        }
    }
    
    /**
     * Test connection to Neo4j without requiring embeddings
     */
    public boolean testConnection() {
        try (var session = driver.session(sessionConfig)) {
            var result = session.run("RETURN 1 as test");
            return result.hasNext() && result.next().get("test").asInt() == 1;
        }
        catch (Exception e) {
            logger.error("Neo4j connection test failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get document count without requiring embeddings
     */
    public int getDocumentCount() {
        try (var session = driver.session(sessionConfig)) {
            var result = session.run(
                "MATCH (d:%s) RETURN count(d) as count".formatted(label));
            return result.single().get("count").asInt();
        }
        catch (Exception e) {
            logger.error("Failed to get document count: {}", e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * Convert a Neo4j Record to a Document
     */
    private Document recordToDocument(Record neoRecord) {
        var node = neoRecord.get("node").asNode();
        var score = neoRecord.get("score").asFloat();
        
        // Extract metadata
        var metadata = new HashMap<String, Object>();
        metadata.put(DocumentMetadata.DISTANCE.value(), 1 - score);
        
        node.keys().forEach(key -> {
            if (key.startsWith("metadata.")) {
                metadata.put(key.substring(key.indexOf(".") + 1), node.get(key).asObject());
            }
        });
        
        // Build the document
        return Document.builder()
            .id(node.get(idProperty).asString())
            .text(node.get("text").asString())
            .metadata(Map.copyOf(metadata))
            .score((double) score)
            .build();
    }
    
    /**
     * Convert a Document to a Neo4j record format
     */
    private Map<String, Object> documentToRecord(Document document, List<Float> embedding) {
        var row = new HashMap<String, Object>();
        
        // Document ID
        row.put("id", document.getId());
        
        // Document properties
        var properties = new HashMap<String, Object>();
        properties.put("text", document.getText());
        
        // Metadata as properties
        document.getMetadata().forEach((k, v) -> properties.put("metadata." + k, v));
        row.put("properties", properties);
        
        // Embedding
        float[] embeddingArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            embeddingArray[i] = embedding.get(i);
        }
        row.put(embeddingProperty, embeddingArray);
        
        return row;
    }
}