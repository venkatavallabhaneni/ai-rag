package com.venkat.rag.store;

import com.venkat.rag.model.VectorRecord;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class InMemoryVectorStore implements VectorStore {
  private final List<VectorRecord> records = new ArrayList<>();

  @Override
  public void upsert(VectorRecord record) {
    records.add(record);
  }

  @Override
  public List<ScoredRecord> search(List<Double> queryVector, int topK) {
    PriorityQueue<ScoredRecord> heap = new PriorityQueue<>(Comparator.comparingDouble(ScoredRecord::score));
    for (VectorRecord r : records) {
      double score = cosineSimilarity(queryVector, r.vector());
      if (heap.size() < topK) heap.offer(new ScoredRecord(r, score));
      else if (score > heap.peek().score()) { heap.poll(); heap.offer(new ScoredRecord(r, score)); }
    }
    List<ScoredRecord> out = new ArrayList<>(heap);
    out.sort((a, b) -> Double.compare(b.score(), a.score()));
    return out;
  }

  public int size() { return records.size(); }

  private static double cosineSimilarity(List<Double> a, List<Double> b) {
    if (a.size() != b.size()) throw new IllegalArgumentException("Vector size mismatch");
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < a.size(); i++) {
      double x = a.get(i), y = b.get(i);
      dot += x * y; na += x * x; nb += y * y;
    }
    double denom = Math.sqrt(na) * Math.sqrt(nb);
    return denom == 0 ? 0 : dot / denom;
  }
}
