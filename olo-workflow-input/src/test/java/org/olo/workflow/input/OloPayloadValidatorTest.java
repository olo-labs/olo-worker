/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.workflow.input;

import org.olo.workflow.input.model.Execution;
import org.olo.workflow.input.model.Input;
import org.olo.workflow.input.model.OloWorkerRequest;
import org.olo.workflow.input.model.Routing;
import org.olo.workflow.input.validation.OloPayloadValidator;
import org.olo.workflow.input.validation.OloValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OloPayloadValidatorTest {

  private static OloWorkerRequest validRequest() {
    return OloWorkerRequest.builder()
        .tenantId("tenant-1")
        .runId("run-1")
        .routing(Routing.builder().pipeline("olo-chat-queue-ollama").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of(Input.builder().name("userQuery").build()))
        .build();
  }

  @Test
  void validRequestPasses() {
    assertDoesNotThrow(() -> OloPayloadValidator.validate(validRequest()));
  }

  @Test
  void nullRequestThrows() {
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(null));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("null")));
  }

  @Test
  void missingTenantIdFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .runId("run-1")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("tenantId")));
  }

  @Test
  void blankTenantIdFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("   ")
        .runId("run-1")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("tenantId")));
  }

  @Test
  void missingRunIdFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("runId")));
  }

  @Test
  void missingRoutingFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .runId("r")
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("routing")));
  }

  @Test
  void missingRoutingPipelineFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .runId("r")
        .routing(Routing.builder().pipeline(null).build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("pipeline")));
  }

  @Test
  void invalidExecutionModeFails() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .runId("r")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("INVALID").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("execution.mode")));
  }

  @Test
  void validExecutionModesPass() {
    for (String mode : List.of("SYNC", "ASYNC", "sync", "async")) {
      OloWorkerRequest req = OloWorkerRequest.builder()
          .tenantId("t")
          .runId("r")
          .routing(Routing.builder().pipeline("p").build())
          .execution(Execution.builder().mode(mode).build())
          .inputs(List.of())
          .build();
      assertDoesNotThrow(() -> OloPayloadValidator.validate(req), "mode=" + mode);
    }
  }

  @Test
  void duplicateInputNamesFail() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .runId("r")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of(
            Input.builder().name("x").build(),
            Input.builder().name("x").build()))
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("duplicate") && e.contains("x")));
  }

  @Test
  void uniqueInputNamesPass() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .tenantId("t")
        .runId("r")
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of(
            Input.builder().name("a").build(),
            Input.builder().name("b").build()))
        .build();
    assertDoesNotThrow(() -> OloPayloadValidator.validate(req));
  }

  @Test
  void exceptionExposesErrorList() {
    OloWorkerRequest req = OloWorkerRequest.builder()
        .routing(Routing.builder().pipeline("p").build())
        .execution(Execution.builder().mode("SYNC").build())
        .inputs(List.of())
        .build();
    OloValidationException ex = assertThrows(OloValidationException.class,
        () -> OloPayloadValidator.validate(req));
    assertEquals(2, ex.getErrors().size());
    assertTrue(ex.getMessage().contains("tenantId"));
    assertTrue(ex.getMessage().contains("runId"));
  }
}
