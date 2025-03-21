package com.pfizer.ai.api.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pfizer.ai.api.request.AIPromptRequest;
import com.pfizer.ai.api.request.VectorIndexingRequestFromFilesystem;
import com.pfizer.ai.api.request.VectorIndexingRequestFromURL;
import com.pfizer.ai.api.response.BasicIndexingResponse;
import com.pfizer.ai.service.RAGBasicProcessorService;
import com.pfizer.ai.service.RAGVectorIndexingService;
import com.pfizer.ai.service.RAGVectorProcessorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/rag/vector")
@Validated
public class AIVectorRAGApi {

        @Autowired
        private RAGVectorIndexingService ragIndexingService;

        @Autowired
        private RAGBasicProcessorService ragProcessorService;

        @Autowired
        private RAGVectorProcessorService ragVectorProcessorService;

        @PostMapping(path = "/indexing/document/filesystem", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<BasicIndexingResponse> indexDocumentFromFilesystem(
                        @RequestBody @Valid VectorIndexingRequestFromFilesystem request) {
                var indexedDocuments = ragIndexingService.indexDocumentFromFilesystem(
                                request.path(),
                                request.keywords());

                return ResponseEntity.ok(
                                new BasicIndexingResponse(true,
                                                "Document successfully indexed as " + indexedDocuments.size()
                                                                + " chunks"));
        }

        @PostMapping(path = "/indexing/document/url", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<BasicIndexingResponse> indexDocumentFromURL(
                        @RequestBody @Valid VectorIndexingRequestFromURL request) {
                var indexedDocuments = ragIndexingService.indexDocumentFromURL(
                                request.url(),
                                request.keywords());

                return ResponseEntity.ok(
                                new BasicIndexingResponse(true,
                                                "Document successfully indexed as " + indexedDocuments.size()
                                                                + " chunks"));
        }

        @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
        public Mono<String> basicRAG(@RequestBody @Valid AIPromptRequest request,
                        @RequestParam(name = "filename", required = true) @NotBlank String filenameForCustomContext) {
                var response = ragProcessorService.generateRAGResponse(request.systemPrompt(),
                                request.userPrompt(), filenameForCustomContext);

                return Mono.just(response);
        }

        @PostMapping(path = "/ask/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
        public Flux<String> basicStreamRAG(@RequestBody @Valid AIPromptRequest request,
                        @RequestParam(name = "filename", required = true) @NotBlank String filenameForCustomContext) {
                return ragProcessorService.streamRAGResponse(request.systemPrompt(),
                                request.userPrompt(), filenameForCustomContext);
        }

        @PostMapping(path = "/ask-vector", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
        public Mono<String> vectorRAG(@RequestBody @Valid AIPromptRequest request,
                        @RequestParam(name = "top-k", required = false, defaultValue = "0") int topK) {
                var response = ragVectorProcessorService.generateRAGResponse(request.systemPrompt(),
                                request.userPrompt(), topK);

                return Mono.just(response);
        }

        @PostMapping(path = "/ask/stream-vector", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_NDJSON_VALUE)
        public Flux<String> vectorStreamRAG(@RequestBody @Valid AIPromptRequest request,
                        @RequestParam(name = "top-k", required = false, defaultValue = "0") int topK) {
                return ragVectorProcessorService.streamRAGResponse(request.systemPrompt(),
                                request.userPrompt(), topK);
        }

        @GetMapping(path = "/diagnostics", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Map<String, Object>> getDiagnostics() {
                Map<String, Object> diagnostics = new HashMap<>();

                try {
                        // Test Neo4j connection
                        boolean neo4jConnected = ragVectorProcessorService.testNeo4jConnection();
                        diagnostics.put("neo4jConnected", neo4jConnected);

                        // Get document count
                        int documentCount = ragVectorProcessorService.getDocumentCount();
                        diagnostics.put("documentCount", documentCount);

                        // Test embedding generation
                        boolean embeddingsWork = ragVectorProcessorService.testEmbeddings("test embedding generation");
                        diagnostics.put("embeddingsWork", embeddingsWork);

                        return ResponseEntity.ok(diagnostics);
                } catch (Exception e) {
                        diagnostics.put("error", e.getMessage());
                        return ResponseEntity.status(500).body(diagnostics);
                }
        }
}
