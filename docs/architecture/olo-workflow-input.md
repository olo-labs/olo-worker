<!-- Copyright (c) 2026 Olo Labs. All rights reserved. -->

## `olo-workflow-input` module (execution contract)

This module owns the **execution input contract** for `olo-worker`. The payload is **universal**: it drives pipeline, agent, workflow, job, tool, and task execution—not only workflows. Consider renaming the module to **`olo-execution-contract`** or **`olo-execution-input`** in a future release.

- **Immutable POJO model** (Lombok `@Value` + `@Builder` + `@Jacksonized`) for stable fields
- **Key/value map representation** for dynamic or shape-changing sections
- **Serialization + deserialization** and **payload versioning** (`VersionStrategy`) by **schemaVersion** (payload schema) vs **routing.pipelineVersion** (execution/pipeline version)

---

## Universal execution envelope

The payload structure is a **universal execution envelope**: every execution in OLO can be triggered with the same contract, **`OloWorkerRequest`**.

The same envelope drives:

| Kind        | Description                    |
|------------|--------------------------------|
| **pipeline** | Multi-step pipeline execution |
| **agent**    | Agent run                      |
| **workflow** | Temporal (or other) workflow  |
| **job**      | Batch / scheduled job         |
| **tool**     | Single tool invocation        |
| **task**     | Generic task                  |

**Benefits:**

- **One contract** for the platform: gateways, workers, and services all speak the same payload.
- **Simpler routing**: optional `routing.executionKind` (see `ExecutionKind` enum) plus `routing.pipeline` and task queues determine how the request is executed.
- **Consistent identity and context**: `runId`, `requestId`, `tenantId`, `trace`, `userContext`, `inputs`, `runtime`, and `execution` apply uniformly regardless of whether the run is a pipeline, agent, workflow, job, tool, or task.

## Recommended top-level structure

| Field | Purpose |
|-------|---------|
| **schemaVersion** | Payload schema version (e.g. "1.0", "1.1"). Distinct from **routing.pipelineVersion** (execution/pipeline version). |
| **runId**, **requestId**, **tenantId** | Identity. |
| **idempotencyKey** | Retry protection, duplicate prevention, exactly-once workflows. |
| **requestTime** | ISO-8601 timestamp; timeouts, replay detection, queue metrics, debugging. |
| **environment**, **region** | Multi-environment and multi-region routing. |
| **trace** | Distributed tracing. |
| **routing** | pipeline, **pipelineVersion**, executionKind, transactionType. |
| **userContext**, **inputs**, **metadata**, **labels**, **context** | User and request context; **labels** for observability (logs, metrics, tracing). |
| **runtime** | models, connections (prefer **credentialRef**; avoid raw secrets in payload), resources. |
| **events** | **sinks** (WEBHOOK, KAFKA, PUBSUB, WEBSOCKET) and subscriptions. |
| **execution** | mode, priority, timeoutSeconds, **retryPolicy** (maxAttempts, backoffSeconds). |
| **configVersion** | Worker config version at request time; config consistency checks. |
| **extensions** | Extensible payload for plugins (e.g. rag, evaluation). |

**Input storage:** Prefer **type** + **resource** (e.g. `olo:resource:connection:redis:cache`) + **key** (or **path**); legacy **mode** + **provider** + **key** still supported.

## Module structure

```
olo-workflow-input
├── model
│   ├── OloWorkerRequest
│   ├── Trace
│   ├── Routing
│   ├── UserContext
│   ├── Input
│   ├── Events
│   ├── EventSink
│   ├── Execution
│   ├── RetryPolicy
│   ├── Runtime
│   ├── RuntimeModels
│   ├── RuntimeResources
│   └── enums
│       ├── ExecutionKind   (PIPELINE, AGENT, WORKFLOW, JOB, TOOL, TASK)
│       ├── ExecutionMode
│       ├── ExecutionPriority
│       └── InputType
├── parser
│   └── OloPayloadParser (+ VersionStrategy)
├── validation
│   └── OloPayloadValidator (+ OloValidationException)
└── util
    └── InputResolver
```

## Responsibilities

- **Define the input schema**: `org.olo.workflow.input.model.OloWorkerRequest`
- **Deserialize** incoming JSON into the POJO: `OloPayloadParser.parse(String)`
- **Serialize** the POJO back to JSON (for logging, callbacks, persistence): `OloPayloadParser.toJson(OloWorkerRequest)`
- **Preserve unknown fields** safely by using maps for dynamic sections (instead of tightly-coupled nested POJOs)

## Public API

- **`org.olo.workflow.input.parser.OloPayloadParser`**
  - **`parse(String json)`** → `OloWorkerRequest` (uses default version strategy)
  - **`parse(String json, VersionStrategy versionStrategy)`** → `OloWorkerRequest` (for 1.0, 1.1, 2.0, etc.)
  - **`toJson(OloWorkerRequest request)`** → `String`
- **`org.olo.workflow.input.parser.VersionStrategy`**
  - **`requestTypeForVersion(String schemaVersion)`** → `Class<? extends OloWorkerRequest>` (throws if unsupported). Uses **schemaVersion** (or legacy **version**) from payload.
  - **`VersionStrategy.defaultStrategy()`** — supports `"1.0"` and `"1.1"`; others throw.
- **`org.olo.workflow.input.validation.OloPayloadValidator`**
  - **`validate(OloWorkerRequest request)`** — throws `OloValidationException` if invalid.
- **`org.olo.workflow.input.validation.OloValidationException`**
  - **`getErrors()`** — list of validation error messages.
- **`org.olo.workflow.input.util.InputResolver`**
  - **`resolveToString(Input input)`** — resolves LOCAL storage to string; CACHE/FILE/S3 throw (for use in activities).

## Data model overview

`OloWorkerRequest` uses:

- **Top-level identifiers**: `version`, `runId`, `requestId` (API gateway / logs / metrics), `tenantId`
- **POJOs for broad categories (stable contract)**:
  - `trace` (`traceId`, `spanId`, `parentSpanId`)
  - `routing` (`pipeline`, optional `executionKind` enum, `transactionType`, `version`)
  - `userContext` (`userId`, `groupId`, `roles`, `permissions`, `sessionId`, `callbackBaseUrl`, `correlationId`)
  - `inputs[]` (each input has fixed `name`, `type`, dynamic `storage`, and `value`). Helpers: **`getInputMap()`** (name → Input, first wins for duplicates), **`getInput(String name)`**
  - `events` (`webhook`, `subscriptions[]`)
  - `execution` (`mode`, `priority` enum, `timeoutSeconds`)

- **Maps for dynamic sections (variable keys / variable shapes)**:
  - `metadata: Map<String, Object>`
  - `context: Map<String, Object>`
  - `inputs[].storage: Map<String, Object>` (e.g. `mode`, `provider`, `key`, `path`)
  - `runtime` is a POJO that contains maps:
    - `runtime.models: RuntimeModels` (stable: `strategy`, `primary`, `fallback[]`)
    - `runtime.connections: Map<String, Map<String, Object>>`
    - `runtime.credentials: Map<String, Map<String, Object>>`
    - `runtime.resources: RuntimeResources` (stable categories: `plugins`, `features`)

## Flow diagram

```mermaid
flowchart LR
  A[Incoming JSON payload] --> B[olo-workflow-input\nOloPayloadParser.parse]
  B --> C[OloWorkerRequest POJO\n(stable fields + maps)]
  C --> D[olo-worker\nTemporal Workflow / Activities]
  D --> E[Optional: serialize for logs/callbacks\nOloPayloadParser.toJson]
```

## Sample payload (workflow input)

```json
{
  "version": "1.0",
  "runId": "953a8f7f-0a3c-4078-aa2c-6970291f8fbd",  
  "requestId": "req-abc-123",
  "tenantId": "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d",
  "trace": {
    "traceId": "b9e41d3d-6b47-4c5c-8d12-5b2f8a8b9c9e",
    "spanId": "olo-worker-root",
    "parentSpanId": ""
  },
  "routing": {
    "region": "default",
    "pipeline": "olo-chat-queue-ollama",
    "transactionType": "QUESTION_ANSWER",
    "version": "1.0"
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
      "storage": {
        "mode": "LOCAL"
      },
      "value": "Explain microkernel architecture"
    },
    {
      "name": "greeting",
      "type": "STRING",
      "storage": {
        "mode": "LOCAL"
      },
      "value": "Hi!"
    },
    {
      "name": "cachedConversation",
      "type": "STRING",
      "storage": {
        "mode": "CACHE",
        "provider": "REDIS",
        "key": "olo:worker:8huqpd42mizzgjOhJEH9C:conversation"
      }
    },
    {
      "name": "ragDocument",
      "type": "FILE",
      "storage": {
        "mode": "FILE",
        "provider": "LOCAL_FS",
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
  "context": {
    "conversationId": "conv-12345",
    "conversationTurn": 7,
    "ragEnabled": true
  },
  "runtime": {
    "models": {
      "strategy": "PRIMARY_FALLBACK",
      "primary": "olo:resource:model:gpt-4o",
      "fallback": [
        "olo:resource:model:gpt-4o-mini",
        "olo:resource:model:claude-3-haiku"
      ]
    },
    "connections": {
      "olo:resource:connection:openai:primary": {
        "provider": "OPENAI",
        "endpoint": "https://api.openai.com/v1",
        "credential": "olo:resource:credential:openai:primary"
      },
      "olo:resource:connection:ollama:local": {
        "provider": "OLLAMA",
        "endpoint": "http://ollama:11434",
        "credential": "olo:resource:credential:ollama:none"
      },
      "olo:resource:connection:redis:cache": {
        "provider": "REDIS",
        "endpoint": "redis://redis:6379",
        "credential": "olo:resource:credential:redis:cache"
      },
      "olo:resource:connection:s3:documents": {
        "provider": "AWS_S3",
        "endpoint": "https://s3.amazonaws.com",
        "credential": "olo:resource:credential:aws:s3-default"
      }
    },
    "credentials": {
      "olo:resource:credential:openai:primary": {
        "type": "API_KEY",
        "provider": "OPENAI",
        "apiKey": "sk-xxxxxxxxxxxxxxxxxxxx"
      },
      "olo:resource:credential:ollama:none": {
        "type": "NONE"
      },
      "olo:resource:credential:redis:cache": {
        "type": "PASSWORD",
        "username": "default",
        "password": "redis-password"
      },
      "olo:resource:credential:aws:s3-default": {
        "type": "ACCESS_KEY",
        "provider": "AWS",
        "accessKeyId": "AKIAxxxxxxxxxxxx",
        "secretAccessKey": "xxxxxxxxxxxxxxxxxxxx"
      }
    },
    "resources": {
      "plugins": {
        "olo:resource:model:ollama32": {
          "enabled": true
        },
        "olo:resource:model:tool:web-search": {
          "enabled": true
        }
      },
      "features": {
        "olo:resource:feature:internal:ledger": {
          "enabled": true
        },
        "olo:resource:feature:internal:debug": {
          "enabled": true
        },
        "olo:resource:feature:internal:metrics": {
          "enabled": true
        }
      }
    }
  },
  "events": {
    "webhook": "https://api.example.com/olo/events",
    "subscriptions": [
      "workflow.started",
      "node.started",
      "node.completed",
      "workflow.completed",
      "workflow.failed"
    ]
  },
  "execution": {
    "mode": "SYNC",
    "priority": "NORMAL",
    "timeoutSeconds": 600
  }
}
```

## Notes / conventions

- **Dynamic sections stay maps**: prefer `Map<String, Object>` (or `Map<String, Map<String, Object>>`) when keys are resource-ids or when schema evolves frequently.
- **Stable contract stays POJO**: keep top-level/broad categories (trace, routing, userContext, execution, events, input envelope) as POJOs to make worker code readable and refactor-safe.

