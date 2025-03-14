package com.pfizer.ai.service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

@Service
public class RAGBasicProcessorService {

    private static final String KEY_CUSTOM_CONTEXT = "customContext";

    private static final String KEY_QUESTION = "question";

    private PromptTemplate basicAugmentationTemplate;

    @Autowired
    @Qualifier("AIServiceImpl")
    private AIService aiService;

    public RAGBasicProcessorService() {
        var ragBasicPromptTemplate = new ClassPathResource("prompts/rag-basic-template.st");
        basicAugmentationTemplate = new PromptTemplate(ragBasicPromptTemplate);
    }
    
    private String retrieveCustomContext(String fromFilename) {
        try {
            return new String(Files.readAllBytes(Paths.get(fromFilename)));
        } catch (Exception e) {
            return "";
        }
    }

    private String augmentUserPrompt(String originalUserPrompt, String customContext) {
        var templateMap = new HashMap<String, Object>();

        templateMap.put(KEY_QUESTION, originalUserPrompt);
        templateMap.put(KEY_CUSTOM_CONTEXT, customContext);

        return basicAugmentationTemplate.render(templateMap);
    }

    public String generateRAGResponse(String systemPrompt, String userPrompt, String filenameForCustomContext) {
        var customContext = retrieveCustomContext(filenameForCustomContext);
        var augmentedUserPrompt = augmentUserPrompt(userPrompt, customContext);

        return aiService.generateBasicResponse(systemPrompt, augmentedUserPrompt);
    }

    public Flux<String> streamRAGResponse(String systemPrompt, String userPrompt, String filenameForCustomContext) {
        var customContext = retrieveCustomContext(filenameForCustomContext);
        var augmentedUserPrompt = augmentUserPrompt(userPrompt, customContext);

        return aiService.streamBasicResponse(systemPrompt, augmentedUserPrompt);
    }

}
