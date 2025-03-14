package com.pfizer.ai.rag.indexing;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RAGTikaDocumentReader {

    public List<Document> readFrom(Resource resource) {
        var tikaDocumentReader = new TikaDocumentReader(resource);

        return tikaDocumentReader.read();
    }

}
