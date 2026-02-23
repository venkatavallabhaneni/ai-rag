package com.venkat.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRequest {
  @NotBlank
  private String message;
  private String[] retrievedChunkIds;
}