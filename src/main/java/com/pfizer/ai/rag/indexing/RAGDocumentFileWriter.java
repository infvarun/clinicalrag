package com.pfizer.ai.rag.indexing;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.writer.FileDocumentWriter;
import org.springframework.stereotype.Component;

@Component
public class RAGDocumentFileWriter {

    public void writeDocumentsToFile(List<Document> documents, String filename, boolean appendIfFileExists) {
        var writer = new FileDocumentWriter(filename, true, MetadataMode.ALL, appendIfFileExists);

        writer.accept(documents);
    }

}
