package com.pfizer.ai.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.neo4j.Neo4jVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import com.pfizer.ai.rag.indexing.RAGTikaDocumentReader;



@Service
public class RAGVectorIndexingService {

    private static final Logger LOG = LoggerFactory.getLogger(RAGBasicIndexingService.class);

    @Autowired
    private RAGTikaDocumentReader tikaDocumentReader;

    @Autowired
    private TextSplitter textSplitter;

    @Autowired
    private Neo4jVectorStore vectorStore;

    private static final String CUSTOM_KEYWORDS_METADATA_KEY = "custom_keywords";

    private void addCustomMetadata(Document document, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        Assert.notNull(document, "Document must not be null");

        document.getMetadata().putAll(Map.of(CUSTOM_KEYWORDS_METADATA_KEY, keywords));
    }

    private List<Document> processDocument(Resource resource, List<String> keywords) {
        Assert.isTrue(resource != null && resource.exists(), "Resource must not be null and must exist");

        var parsedDocuments = tikaDocumentReader.readFrom(resource);
        var splittedDocuments = textSplitter.split(parsedDocuments);

        splittedDocuments.forEach(document -> addCustomMetadata(document, keywords));

        vectorStore.add(splittedDocuments);

        LOG.info("Original document splitted into {} chunks and saved to Neo4j vector store", splittedDocuments.size());

        return splittedDocuments;
    }

    public List<Document> indexDocumentFromFilesystem(
            String sourcePath, List<String> keywords) {
        var resource = new FileSystemResource(sourcePath);

        return processDocument(resource, keywords);
    }

    public List<Document> indexDocumentFromURL(
            String sourcePath, List<String> keywords) {
        try {
            var resource = new UrlResource(sourcePath);

            return processDocument(resource, keywords);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + sourcePath, e);
        }
    }

}
