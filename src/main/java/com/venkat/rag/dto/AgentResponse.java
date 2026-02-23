package com.venkat.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentResponse {
  private String requestId;
  private String model;
  private long latencyMs;
  private Usage usage;
  private String answer;

  @Data
  @Builder
  public static class Usage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
  }
} 