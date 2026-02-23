package com.venkat.rag.controller;

import com.venkat.rag.model.Document;
import com.venkat.rag.service.AgentService;
import com.venkat.rag.service.Chunker;
import com.venkat.rag.service.RagService;
import com.venkat.rag.store.MilvusVectorStore;
import com.venkat.rag.store.VectorStore;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import com.venkat.rag.dto.IngestRequest;
import com.venkat.rag.dto.IngestResponse;
import com.venkat.rag.dto.SearchRequest;
import com.venkat.rag.dto.SearchResponse;
import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagController {

  private final RagService ragService;
  private final Chunker chunker;
  private final MilvusVectorStore milvusVectorStore; // for showing stored count
  
  public RagController(RagService ragService, Chunker chunker, MilvusVectorStore milvusVectorStore,
      AgentService agentService, @Value("${rag.chunkSize}") int chunkSize, @Value("${rag.overlap}") int overlap) {
    this.ragService = ragService;
    this.chunker = chunker;
    this.milvusVectorStore = milvusVectorStore;
    this.chunker.configure(chunkSize, overlap);
   
  }

  @PostMapping("/ingest")
  public IngestResponse ingest(@Valid @RequestBody IngestRequest req) {
    List<Document> docs = req.getDocuments().stream()
        .map(d -> new Document(d.getDocumentId(), d.getTitle(), d.getSource(), d.getText())).toList();

    int chunksStored = ragService.ingest(docs);
    return IngestResponse.builder().documentsIngested(docs.size()).chunksStored(chunksStored).build();
  }

  @GetMapping("/ask")
  public String ask(@RequestParam("prompt") String prompt) {
    return ragService.ask(prompt);
  }

 

  @PostMapping("/search")
  public SearchResponse search(@Valid @RequestBody SearchRequest req) {
    int topK = (req.getTopK() == null || req.getTopK() <= 0) ? 5 : req.getTopK();

    List<VectorStore.ScoredRecord> scored = ragService.retrieve(req.getQuery(), topK);

    var results = scored.stream().map(sr -> {
      var c = sr.record().payload();
      return SearchResponse.Result.builder().score(sr.score()).documentId(c.documentId())
          .documentTitle(c.documentTitle()).source(c.source()).chunkIndex(c.chunkIndex()).text(c.text()).build();
    }).toList();

    return SearchResponse.builder().query(req.getQuery()).topK(topK).results(results).build();
  }

}
