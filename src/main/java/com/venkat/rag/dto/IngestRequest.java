package com.venkat.rag.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequest {
  @NotEmpty
  private List<DocumentDto> documents;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DocumentDto {
    private String documentId;
    private String title;
    private String source;
    private String text;
  }
}
