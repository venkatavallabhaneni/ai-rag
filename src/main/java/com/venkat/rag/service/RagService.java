package com.venkat.rag.service;

import com.venkat.rag.model.Chunk;
import com.venkat.rag.model.Document;
import com.venkat.rag.model.VectorRecord;
import com.venkat.rag.store.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

  public String ask(String query) {

    List<VectorStore.ScoredRecord> results = retrieve(query, 1);

    List<Chunk> chunksFromRag = results.stream().map(record -> record.record().payload()).toList();

    return this.getSummaryFromLLM(query, chunksFromRag);

  }

  private String getSummaryFromLLM(String prompt, List<Chunk> relevantChunks) {

    String context = relevantChunks.stream().map(c -> "- " + c.text()).collect(Collectors.joining("\n"));

    String systemPrompt = "You are a helpful RAG assistant. Answer questions based only on the provided context. If the answer is not in the context, say: \"I don't know based on the provided context.\"";
    
    String userMessage = "CONTEXT:\n" + context + "\n\nQUESTION:\n" + prompt;

    return embeddingClient.chat(userMessage, systemPrompt);
  }
}
