package com.venkat.rag.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
public class AgentController {

  private final com.venkat.rag.service.AgentService agentService;

  public AgentController(com.venkat.rag.service.AgentService agentService) {
    this.agentService = agentService;
  }

  @PostMapping("/tool-call")
  public com.venkat.rag.dto.AgentResponse callAgent(@Valid @RequestBody com.venkat.rag.dto.AgentRequest req,
                                 @RequestHeader(value = "x-request-id", required = false) String requestId) throws Exception {
    String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
    return agentService.run(req.getMessage(), rid, req.getRetrievedChunkIds());
  }
}