/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input;

import org.olo.workflow.input.model.Input;
import org.olo.workflow.input.model.OloWorkerRequest;
import org.olo.workflow.input.model.enums.ExecutionPriority;
import org.olo.workflow.input.parser.OloPayloadParser;
import org.olo.workflow.input.parser.VersionStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OloPayloadParserTest {
  private static final String FULL_JSON = """
      {
        "schemaVersion": "1.0",
        "runId": "953a8f7f-0a3c-4078-aa2c-6970291f8fbd",
        "requestId": "req-abc-123",
        "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
        "idempotencyKey": "chat-req-987654321",
        "requestTime": "2026-03-07T15:22:11Z",
        "environment": "prod",
        "region": "ap-south-1",
        "trace": {
          "traceId": "b9e41d3d-6b47-4c5c-8d12-5b2f8a8b9c9e",
          "spanId": "olo-worker-root",
          "parentSpanId": ""
        },
        "routing": {
          "pipeline": "olo-chat-queue-ollama",
          "pipelineVersion": "1.0",
          "transactionType": "QUESTION_ANSWER"
        },
        "userContext": {
          "userId": "user-42",
          "groupId": "engineering",
          "roles": ["PUBLIC", "ADMIN"],
          "permissions": ["STORAGE", "CACHE", "S3"],
          "sessionId": "chat-session-88",
          "callbackBaseUrl": "https://api.example.com/callbacks",
          "correlationId": "req-987654321"
        },
        "inputs": [
          {
            "name": "userQuery",
            "type": "STRING",
            "storage": { "type": "LOCAL" },
            "value": "Explain microkernel architecture"
          },
          {
            "name": "greeting",
            "type": "STRING",
            "storage": { "type": "LOCAL" },
            "value": "Hi!"
          },
          {
            "name": "cachedConversation",
            "type": "STRING",
            "storage": {
              "type": "CACHE",
              "resource": "olo:resource:connection:redis:cache",
              "key": "olo:worker:8huqpd42mizzgjOhJEH9C:conversation"
            }
          },
          {
            "name": "ragDocument",
            "type": "FILE",
            "storage": {
              "type": "FILE",
              "resource": "olo:resource:connection:local:fs",
              "path": "rag/8huqpd42mizzgjOhJEH9C/readme.md"
            }
          }
        ],
        "metadata": {
          "source": "chat-ui",
          "environment": "production",
          "clientVersion": "2.4.1",
          "requestLanguage": "en",
          "requestChannel": "web",
          "featureFlags": ["rag-enabled", "streaming-enabled"]
        },
        "labels": {
          "team": "search",
          "experiment": "rag-v2"
        },
        "context": {
          "conversationId": "conv-12345",
          "conversationTurn": 7,
          "ragEnabled": true
        },
        "runtime": {
          "models": {
            "strategy": "PRIMARY_FALLBACK",
            "primary": "olo:resource:model:gpt-4o",
            "fallback": ["olo:resource:model:gpt-4o-mini", "olo:resource:model:claude-3-haiku"]
          },
          "connections": {
            "olo:resource:connection:openai:primary": {
              "provider": "OPENAI",
              "endpoint": "https://api.openai.com/v1",
              "credential": "olo:resource:credential:openai:primary"
            }
          },
          "credentials": {
            "olo:resource:credential:openai:primary": {
              "type": "API_KEY",
              "provider": "OPENAI",
              "apiKey": "sk-xxxxxxxxxxxxxxxxxxxx"
            }
          },
          "resources": {
            "plugins": {
              "olo:resource:model:ollama32": { "enabled": true }
            },
            "features": {
              "olo:resource:feature:internal:ledger": { "enabled": true }
            }
          }
        },
        "events": {
          "webhook": "https://api.example.com/olo/events",
          "sinks": [
            { "type": "WEBHOOK", "endpoint": "https://api.example.com/olo/events" }
          ],
          "subscriptions": ["workflow.started", "node.started", "node.completed", "workflow.completed", "workflow.failed"]
        },
        "execution": {
          "mode": "SYNC",
          "priority": "NORMAL",
          "timeoutSeconds": 600,
          "retryPolicy": { "maxAttempts": 3, "backoffSeconds": 5 }
        },
        "configVersion": "2026-03-07T14:00:00Z",
        "extensions": {
          "rag": { "topK": 5 },
          "evaluation": { "enabled": false }
        }
      }
      """;

  @Test
  void parsesFullPayload() {
    OloWorkerRequest req = OloPayloadParser.parse(FULL_JSON);
    assertEquals("1.0", req.getSchemaVersion());
    assertEquals("953a8f7f-0a3c-4078-aa2c-6970291f8fbd", req.getRunId());
    assertEquals("req-abc-123", req.getRequestId());
    assertEquals("2a2a91fb-f5b4-4cf0-b917-524d242b2e3d", req.getTenantId());
    assertEquals("chat-req-987654321", req.getIdempotencyKey());
    assertEquals("2026-03-07T15:22:11Z", req.getRequestTime());
    assertEquals("prod", req.getEnvironment());
    assertEquals("ap-south-1", req.getRegion());
    assertEquals("2026-03-07T14:00:00Z", req.getConfigVersion());
    assertNotNull(req.getLabels());
    assertEquals("search", req.getLabels().get("team"));
    assertEquals("rag-v2", req.getLabels().get("experiment"));
    assertNotNull(req.getExtensions());
    @SuppressWarnings("unchecked")
    Map<String, Object> ragExt = (Map<String, Object>) req.getExtensions().get("rag");
    assertNotNull(ragExt);
    assertEquals(5, ((Number) ragExt.get("topK")).intValue());

    assertNotNull(req.getTrace());
    assertEquals("b9e41d3d-6b47-4c5c-8d12-5b2f8a8b9c9e", req.getTrace().getTraceId());
    assertEquals("olo-worker-root", req.getTrace().getSpanId());

    assertNotNull(req.getRouting());
    assertEquals("olo-chat-queue-ollama", req.getRouting().getPipeline());
    assertEquals("1.0", req.getRouting().getPipelineVersion());
    assertEquals("QUESTION_ANSWER", req.getRouting().getTransactionType());

    assertNotNull(req.getUserContext());
    assertEquals("user-42", req.getUserContext().getUserId());
    assertEquals("engineering", req.getUserContext().getGroupId());

    assertNotNull(req.getInputs());
    assertEquals(4, req.getInputs().size());
    Input first = req.getInputs().get(0);
    assertEquals("userQuery", first.getName());
    assertEquals("STRING", first.getType());
    assertNotNull(first.getStorage());
    assertEquals("LOCAL", first.getStorage().get("type"));
    assertEquals("Explain microkernel architecture", first.getValue());

    assertEquals(first, req.getInput("userQuery"));
    assertNotNull(req.getInput("greeting"));
    assertNotNull(req.getInput("cachedConversation"));
    assertNull(req.getInput("nonexistent"));
    assertNull(req.getInput(null));
    assertEquals(4, req.getInputMap().size());
    assertTrue(req.getInputMap().containsKey("userQuery"));
    assertThrows(UnsupportedOperationException.class, () -> req.getInputMap().put("x", null));

    assertNotNull(req.getMetadata());
    assertEquals("chat-ui", req.getMetadata().get("source"));
    assertEquals("production", req.getMetadata().get("environment"));

    assertNotNull(req.getContext());
    assertEquals("conv-12345", req.getContext().get("conversationId"));
    assertEquals(7, ((Number) req.getContext().get("conversationTurn")).intValue());

    assertNotNull(req.getRuntime());
    assertNotNull(req.getRuntime().getModels());
    assertEquals("PRIMARY_FALLBACK", req.getRuntime().getModels().getStrategy());
    assertEquals("olo:resource:model:gpt-4o", req.getRuntime().getModels().getPrimary());
    assertNotNull(req.getRuntime().getModels().getFallback());
    assertEquals(2, req.getRuntime().getModels().getFallback().size());
    assertNotNull(req.getRuntime().getConnections());
    assertTrue(req.getRuntime().getConnections().containsKey("olo:resource:connection:openai:primary"));
    Map<String, Object> conn = req.getRuntime().getConnections().get("olo:resource:connection:openai:primary");
    assertEquals("OPENAI", conn.get("provider"));

    assertNotNull(req.getRuntime().getResources());
    assertNotNull(req.getRuntime().getResources().getPlugins());
    assertTrue(req.getRuntime().getResources().getPlugins().containsKey("olo:resource:model:ollama32"));
    assertNotNull(req.getRuntime().getResources().getFeatures());
    assertTrue(req.getRuntime().getResources().getFeatures().containsKey("olo:resource:feature:internal:ledger"));

    assertNotNull(req.getEvents());
    assertNotNull(req.getEvents().getSinks());
    assertEquals(1, req.getEvents().getSinks().size());
    assertEquals("WEBHOOK", req.getEvents().getSinks().get(0).getType());
    assertEquals("https://api.example.com/olo/events", req.getEvents().getSinks().get(0).getEndpoint());
    assertNotNull(req.getEvents().getSubscriptions());
    assertTrue(req.getEvents().getSubscriptions().contains("workflow.started"));

    assertNotNull(req.getExecution());
    assertEquals("SYNC", req.getExecution().getMode());
    assertEquals(ExecutionPriority.NORMAL, req.getExecution().getPriority());
    assertEquals(600, req.getExecution().getTimeoutSeconds());
    assertNotNull(req.getExecution().getRetryPolicy());
    assertEquals(3, req.getExecution().getRetryPolicy().getMaxAttempts());
    assertEquals(5, req.getExecution().getRetryPolicy().getBackoffSeconds());
  }

  @Test
  void roundTripSerialization() {
    OloWorkerRequest req = OloPayloadParser.parse(FULL_JSON);
    String json = OloPayloadParser.toJson(req);
    assertNotNull(json);
    OloWorkerRequest req2 = OloPayloadParser.parse(json);
    assertEquals(req.getSchemaVersion(), req2.getSchemaVersion());
    assertEquals(req.getRunId(), req2.getRunId());
    assertNotNull(req2.getRouting());
    assertEquals(req.getRouting().getPipeline(), req2.getRouting().getPipeline());
    assertNotNull(req2.getInputs());
    assertEquals(req.getInputs().size(), req2.getInputs().size());
    assertNotNull(req2.getMetadata());
    assertEquals(req.getMetadata().get("source"), req2.getMetadata().get("source"));
  }

  @Test
  void rejectsEmptyJson() {
    assertThrows(IllegalArgumentException.class, () -> OloPayloadParser.parse(""));
    assertThrows(IllegalArgumentException.class, () -> OloPayloadParser.parse("   "));
    assertThrows(IllegalArgumentException.class, () -> OloPayloadParser.parse(null));
  }

  @Test
  void toJsonRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> OloPayloadParser.toJson(null));
  }

  @Test
  void parseWithVersionStrategy() {
    String json1 = "{\"schemaVersion\":\"1.0\",\"runId\":\"r\",\"tenantId\":\"t\",\"routing\":{\"pipeline\":\"p\"}}";
    OloWorkerRequest req1 = OloPayloadParser.parse(json1, VersionStrategy.defaultStrategy());
    assertEquals("1.0", req1.getSchemaVersion());

    String json11 = "{\"schemaVersion\":\"1.1\",\"runId\":\"r\",\"tenantId\":\"t\",\"routing\":{\"pipeline\":\"p\"}}";
    OloWorkerRequest req11 = OloPayloadParser.parse(json11, VersionStrategy.defaultStrategy());
    assertEquals("1.1", req11.getSchemaVersion());

    String jsonUnknown = "{\"schemaVersion\":\"2.0\",\"runId\":\"r\",\"tenantId\":\"t\",\"routing\":{\"pipeline\":\"p\"}}";
    assertThrows(IllegalArgumentException.class,
        () -> OloPayloadParser.parse(jsonUnknown, VersionStrategy.defaultStrategy()));
  }

  @Test
  void parseWithNullVersionUsesDefaultStrategy() {
    String json = "{\"runId\":\"r\",\"tenantId\":\"t\",\"routing\":{\"pipeline\":\"p\"}}";
    OloWorkerRequest req = OloPayloadParser.parse(json, VersionStrategy.defaultStrategy());
    assertNull(req.getSchemaVersion());
  }
}
