package com.venkat.rag.service;



import com.venkat.rag.dto.AgentResponse;
import com.venkat.tools.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentService {
  private static final Logger log = LoggerFactory.getLogger(AgentService.class);

  private final LiteLlmClient client;
  private final ToolRegistry toolRegistry;
  private final ObjectMapper om = new ObjectMapper();

  private final String model;
  private final double temperature;
  private final int maxTokens;

  public AgentService(
      LiteLlmClient client,
      ToolRegistry toolRegistry,
      @Value("${litellm.model}") String model,
      @Value("${litellm.temperature}") double temperature,
      @Value("${litellm.maxTokens}") int maxTokens
  ) {
    this.client = client;
    this.toolRegistry = toolRegistry;
    this.model = model;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
  }

  public AgentResponse run(String userMessage, String requestId, String[] retrievedChunkIds) throws Exception {
    long start = System.currentTimeMillis();

    
    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of(
        "role", "system",
        "content", "You are an assistant. If a tool can answer, call the tool. If not, respond normally."
    ));
    messages.add(Map.of(
        "role", "user",
        "content", userMessage
    ));

    // Request headers (for your proxy logging correlation)
    Map<String, String> headers = new HashMap<>();
    headers.put("x-request-id", requestId);
    if (retrievedChunkIds != null && retrievedChunkIds.length > 0) {
      headers.put("x-rag-chunk-ids", String.join(",", retrievedChunkIds));
    }

    // 1) First call with tool definitions
    Map<String, Object> req1 = baseRequest(messages);
    JsonNode resp1 = client.chatCompletions(req1, headers);

    // Extract assistant message
    JsonNode assistantMsg1 = resp1.path("choices").path(0).path("message");

    // If the model decided tool_calls, execute them and call again
    JsonNode toolCalls = assistantMsg1.path("tool_calls");
    if (toolCalls.isArray() && toolCalls.size() > 0) {
      // Append the assistant message that contains tool_calls
      messages.add(jsonToMap(assistantMsg1));

      for (JsonNode tc : toolCalls) {
        String toolCallId = tc.path("id").asText();
        String toolName = tc.path("function").path("name").asText();
        String argJson = tc.path("function").path("arguments").asText("{}");

        Map<String, Object> args = om.readValue(argJson, new TypeReference<Map<String, Object>>() {});
        log.info("tool_call requestId={} tool={} args={}", requestId, toolName, args);

        String toolResult = toolRegistry.get(toolName).execute(args);

        // Append tool result message (OpenAI format)
        messages.add(Map.of(
            "role", "tool",
            "tool_call_id", toolCallId,
            "content", toolResult
        ));
      }

      // 2) Second call to get final answer
      Map<String, Object> req2 = baseRequest(messages);
      JsonNode resp2 = client.chatCompletions(req2, headers);

      long latencyMs = System.currentTimeMillis() - start;
      return buildResponse(resp2, requestId, latencyMs);
    }

    // No tool call: return direct answer
    long latencyMs = System.currentTimeMillis() - start;
    return buildResponse(resp1, requestId, latencyMs);
  }

  private Map<String, Object> baseRequest(List<Map<String, Object>> messages) {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("model", model);
    req.put("messages", messages);
    req.put("temperature", temperature);
    req.put("max_tokens", maxTokens);

    // Tool calling
    req.put("tools", toolRegistry.getToolDefinitions());
    req.put("tool_choice", "auto");
    return req;
  }

  private AgentResponse buildResponse(JsonNode resp, String requestId, long latencyMs) {
    String modelUsed = resp.path("model").asText(model);

    String answer = resp.path("choices").path(0).path("message").path("content").asText("");

    JsonNode usage = resp.path("usage");
    Integer prompt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
    Integer completion = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
    Integer total = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : null;

    log.info("agent_done requestId={} model={} latencyMs={} promptTokens={} completionTokens={} totalTokens={}",
        requestId, modelUsed, latencyMs, prompt, completion, total);

    return AgentResponse.builder()
        .requestId(requestId)
        .model(modelUsed)
        .latencyMs(latencyMs)
        .usage(AgentResponse.Usage.builder()
            .promptTokens(prompt)
            .completionTokens(completion)
            .totalTokens(total)
            .build())
        .answer(answer)
        .build();
  }

  private Map<String, Object> jsonToMap(JsonNode node) {
    return om.convertValue(node, new TypeReference<Map<String, Object>>() {});
  }
} 
