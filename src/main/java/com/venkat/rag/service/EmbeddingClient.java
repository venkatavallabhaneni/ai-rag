package com.venkat.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClient {
  private static final MediaType JSON = MediaType.parse("application/json");

  private final OkHttpClient http = new OkHttpClient();
  private final ObjectMapper om = new ObjectMapper();
  private final String gatewayUrl;
  private final String embeddingModel;
  private final String chatModel;
  private final String apiKey;

  public EmbeddingClient(
      @Value("${aigateway.baseurl}") String gatewayUrl,
      @Value("${aigateway.embedding.model}") String embeddingModel,
      @Value("${aigateway.chat.model}") String chatModel,
      @Value("${aigateway.api-key:}") String apiKey) {
    this.gatewayUrl = gatewayUrl.replaceAll("/$", ""); // remove trailing slash
    this.embeddingModel = embeddingModel;
    this.chatModel = chatModel;
    this.apiKey = apiKey;
  }

  public List<Double> embed(String text) {
    try {
      String embedUrl = gatewayUrl + "/v1/embeddings";
      String payload = om.writeValueAsString(
          Map.of("model", embeddingModel, "input", text));

      Request req = new Request.Builder()
          .url(embedUrl)
          .post(RequestBody.create(payload, JSON))
          .addHeader("Content-Type", "application/json")
          .addHeader("Authorization", "Bearer " + apiKey)
          .build();

      try (Response resp = http.newCall(req).execute()) {
        if (!resp.isSuccessful()) {
          String err = resp.body() != null ? resp.body().string() : "";
          throw new IOException("Embed failed: " + resp.code() + " " + err);
        }
        String body = resp.body() != null ? resp.body().string() : "{}";
        JsonNode root = om.readTree(body);
        JsonNode dataArray = root.get("data");
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
          throw new IOException("Missing 'data' in embed response: " + body);
        }
        JsonNode vecNode = dataArray.get(0).get("embedding");
        if (vecNode == null || !vecNode.isArray()) {
          throw new IOException("Missing 'embedding' in response: " + body);
        }
        List<Double> vector = new ArrayList<>(vecNode.size());
        for (JsonNode n : vecNode) vector.add(n.asDouble());
        return vector;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String chat(String userMessage, String systemPrompt) {
    try {
      String chatUrl = gatewayUrl + "/v1/chat/completions";
      String payload = om.writeValueAsString(
          Map.of(
              "model", chatModel,
              "messages", new Object[] {
                  Map.of("role", "system", "content", systemPrompt),
                  Map.of("role", "user", "content", userMessage)
              },
              "temperature", 0.7));

      Request req = new Request.Builder()
          .url(chatUrl)
          .post(RequestBody.create(payload, JSON))
          .addHeader("Content-Type", "application/json")
          .addHeader("Authorization", "Bearer " + apiKey)
          .build();

      try (Response resp = http.newCall(req).execute()) {
        if (!resp.isSuccessful()) {
          String err = resp.body() != null ? resp.body().string() : "";
          throw new IOException("Chat failed: " + resp.code() + " " + err);
        }
        String body = resp.body() != null ? resp.body().string() : "{}";
        JsonNode root = om.readTree(body);
        JsonNode choicesNode = root.get("choices");
        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
          throw new IOException("Missing 'choices' in response: " + body);
        }
        JsonNode contentNode = choicesNode.get(0).get("message").get("content");
        if (contentNode == null) {
          throw new IOException("Missing 'content' in response: " + body);
        }
        return contentNode.asText();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
