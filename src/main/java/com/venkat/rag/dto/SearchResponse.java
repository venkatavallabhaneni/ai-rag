package com.venkat.rag.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {
  private String query;
  private int topK;
  private List<Result> results;
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class Result {
    private double score;
    private String documentId;
    private String documentTitle;
    private String source;
    private int chunkIndex;
    private String text;
  }
}
