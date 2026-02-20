package com.venkat.rag.service;

import com.venkat.rag.model.Chunk;
import com.venkat.rag.model.Document;
import com.venkat.rag.model.VectorRecord;
import com.venkat.rag.store.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

  private final Chunker chunker;
  private final EmbeddingClient embeddingClient;
  private final VectorStore vectorStore;

  public RagService(Chunker chunker, EmbeddingClient embeddingClient, VectorStore vectorStore) {
    this.chunker = chunker;
    this.embeddingClient = embeddingClient;
    this.vectorStore = vectorStore;
  }

  public int ingest(List<Document> docs) {
    int totalChunks = 0;
    for (Document doc : docs) {
      List<Chunk> chunks = chunker.chunk(doc);
      totalChunks += chunks.size();

      for (Chunk c : chunks) {
        List<Double> vec = embeddingClient.embed(c.text());
        vectorStore.upsert(new VectorRecord(c.chunkId(), vec, c));
      }
    }
    return totalChunks;
  }

  public List<VectorStore.ScoredRecord> retrieve(String query, int topK) {
    List<Double> qVec = embeddingClient.embed(query);
    return vectorStore.search(qVec, topK);
  }
}
