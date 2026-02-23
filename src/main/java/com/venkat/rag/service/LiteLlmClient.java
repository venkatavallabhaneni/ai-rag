
package com.venkat.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class LiteLlmClient {
  private static final MediaType JSON = MediaType.parse("application/json");

  private final OkHttpClient http = new OkHttpClient();
  private final ObjectMapper om = new ObjectMapper();

  private final String baseUrl;
  private final String chatPath;
  private final String apiKey;

  public LiteLlmClient(
      @Value("${litellm.baseUrl}") String baseUrl,
      @Value("${litellm.chatPath}") String chatPath,
      @Value("${litellm.api-key:local-test-key}") String apiKey) {
    this.baseUrl = baseUrl;
    this.chatPath = chatPath;
    this.apiKey = apiKey;
  }

  public JsonNode chatCompletions(Map<String, Object> requestBody, Map<String, String> headers) {
    try {
      String url = baseUrl + chatPath;
      String body = om.writeValueAsString(requestBody);

      Request.Builder rb = new Request.Builder().url(url).post(RequestBody.create(body, JSON))
          .addHeader("Content-Type", "application/json")
          .addHeader("Authorization", "Bearer " + apiKey);

      if (headers != null) {
        headers.forEach(rb::addHeader);
      }

      try (Response resp = http.newCall(rb.build()).execute()) {
        String respBody = resp.body() != null ? resp.body().string() : "{}";
        if (!resp.isSuccessful()) {
          throw new IOException("LiteLLM error: " + resp.code() + " " + respBody);
        }
        return om.readTree(respBody);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}