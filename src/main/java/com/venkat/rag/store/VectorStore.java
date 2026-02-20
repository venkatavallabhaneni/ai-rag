package com.venkat.rag.store;

import com.venkat.rag.model.VectorRecord;

import java.util.List;

public interface VectorStore {
  void upsert(VectorRecord record);
  List<ScoredRecord> search(List<Double> queryVector, int topK);

  static record ScoredRecord(VectorRecord record, double score) {}
}
