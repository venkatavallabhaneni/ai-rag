package com.venkat.rag.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EmbeddingClient {
  private final OpenAIClient client;
  private final String model;

  public EmbeddingClient(
      @Value("${OPENAI_API_KEY:}") String apiKey,
      @Value("${openai.embedding.model}") String model) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is required");
    }
    this.client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    this.model = model;
  }

  public List<Double> embed(String text) {
    CreateEmbeddingResponse response = client.embeddings().create(
        EmbeddingCreateParams.builder()
            .model(model)
            .input(text)
            .build());

    if (response.data().isEmpty()) {
      throw new IllegalStateException("OpenAI returned empty embedding data");
    }

    return response.data().get(0).embedding().stream()
        .map(Double::valueOf)
        .toList();
  }
}
