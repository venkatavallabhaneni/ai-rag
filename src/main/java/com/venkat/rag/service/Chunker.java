package com.venkat.rag.service;

import com.venkat.rag.model.Chunk;
import com.venkat.rag.model.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Chunker {
  private int chunkSize = 500;
  private int overlap = 100;

  public void configure(int chunkSize, int overlap) {
    if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be > 0");
    if (overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("overlap must be >=0 and < chunkSize");
    this.chunkSize = chunkSize;
    this.overlap = overlap;
  }

  public List<Chunk> chunk(Document doc) {
    String text = normalize(doc.text());
    List<Chunk> out = new ArrayList<>();
    int step = Math.max(1, chunkSize - overlap);

    int i = 0;
    int idx = 0;
    while (i < text.length()) {
      int end = Math.min(text.length(), i + chunkSize);
      String chunkText = text.substring(i, end).trim();
      if (!chunkText.isBlank()) {
        String chunkId = doc.documentId() + "_chunk_" + idx;
        out.add(new Chunk(chunkId, doc.documentId(), doc.title(), doc.source(), idx, chunkText));
        idx++;
      }
      i += step;
    }
    return out;
  }

  private static String normalize(String s) {
    return s.replace("\r\n", "\n")
        .replaceAll("[ \t]+", " ")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
  }
}
