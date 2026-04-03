/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.configuration.Bootstrap;
import org.olo.configuration.Configuration;
import org.olo.config.OloSessionCache;
import org.olo.config.impl.InMemorySessionCache;
import org.olo.input.model.WorkflowInput;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.plugin.PluginExecutorFactory;
import org.olo.worker.activity.impl.ExecuteNodeDynamicActivity;
import org.olo.worker.activity.impl.OloKernelActivitiesImpl;
import org.olo.worker.cache.CachePortRegistrar;
import org.olo.worker.db.DbPortRegistrar;
import org.olo.worker.workflow.OloKernelWorkflow;
import org.olo.worker.workflow.impl.OloKernelWorkflowImpl;
import org.olo.bootstrap.loader.context.GlobalContextProvider;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: loads workflow input from {@code configuration/debug/workflow/},
 * connects to a <strong>real Temporal server</strong>, <strong>starts the worker</strong> in-process
 * to consume the task, submits the workflow, waits for completion, then compares the debug dump file.
 * <p>
 * Flow: 1) Bootstrap (Redis, DB, ensure-schema), 2) Start worker polling Temporal server,
 * 3) Submit workflow to same server, 4) Worker consumes and completes workflow,
 * 5) Compare dump file to input and expected LocalContext.
 * <p>
 * <strong>Prerequisites:</strong> Temporal server running (default {@code localhost:47233}).
 * Redis and DB required for bootstrap so the worker has pipelines.
 * <p>
 * Run: {@code ./gradlew :olo-worker:workflowInputDebug} or
 * {@code ./gradlew :olo-worker:test --tests "org.olo.worker.WorkflowInputDebugIntegrationTest"}
 */
@Tag("integration")
class WorkflowInputDebugIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TASK_QUEUE = "olo.default.consensus-pipeline";
    private static final String SAMPLE_INPUT_PATH = "configuration/debug/workflow/sample-consensus-pipeline.json";
    private static final String TEMPORAL_TARGET_ENV = "OLO_TEMPORAL_TARGET";
    private static final String TEMPORAL_NAMESPACE_ENV = "OLO_TEMPORAL_NAMESPACE";
    private static final String DEFAULT_TARGET = "localhost:47233";
    private static final String DEFAULT_NAMESPACE = "default";
    private static final String DUMP_DIR_PROP = "olo.debug.dump.input.dir";
    private static final String APP_DUMP_PROP = "olo.app.dump";
    private static final String APP_DUMP_DIR_PROP = "olo.app.dump.dir";
    private static final String DEFAULT_DUMP_DIR = "build/debug-dump";
    private static final String DEFAULT_DB_PASSWORD = "pgpass";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 15;

    private WorkerFactory workerFactory;
    private WorkflowServiceStubs service;

    @AfterEach
    void tearDown() throws InterruptedException {
        Bootstrap.stopTenantRegionRefreshScheduler();
        Bootstrap.stopRefreshScheduler();
        if (workerFactory != null) {
            workerFactory.shutdown();
            workerFactory.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        if (service != null) {
            service.shutdown();
        }
        System.clearProperty(DUMP_DIR_PROP);
        System.clearProperty(APP_DUMP_PROP);
        System.clearProperty(APP_DUMP_DIR_PROP);
    }

    @Test
    void submitWorkflowToTemporalServerThenCompareDumpFile() throws Exception {
        Configuration config = Bootstrap.loadConfiguration();
        runEnsureSchemaScript(config);

        DbPortRegistrar.registerDefaults();
        CachePortRegistrar.registerDefaults();
        Bootstrap.run(true);
        var globalContext = GlobalContextProvider.getGlobalContext();
        Map<String, CompositeConfigurationSnapshot> snapshotMap = ConfigurationProvider.getSnapshotMap();
        if (snapshotMap != null) {
            for (CompositeConfigurationSnapshot composite : snapshotMap.values()) {
                if (composite != null) globalContext.rebuildTreeForRegion(composite);
            }
        }

        String inputJson = loadWorkflowInputJson();
        assertNotNull(inputJson, "Workflow input JSON not found at " + SAMPLE_INPUT_PATH);
        WorkflowInput workflowInput = WorkflowInput.fromJson(inputJson);

        Path dumpDir = Paths.get(DEFAULT_DUMP_DIR).toAbsolutePath();
        Files.createDirectories(dumpDir);
        try (Stream<Path> stream = Files.list(dumpDir)) {
            List<Path> toDelete = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return (name.startsWith("workflow-input-") || name.startsWith("run-")) && name.endsWith(".json");
                    })
                    .toList();
            for (Path path : toDelete) {
                Files.delete(path);
            }
        }
        System.setProperty(DUMP_DIR_PROP, dumpDir.toString());
        System.setProperty(APP_DUMP_PROP, "true");
        System.setProperty(APP_DUMP_DIR_PROP, dumpDir.toString());

        String target = System.getenv(TEMPORAL_TARGET_ENV);
        if (target == null || target.isBlank()) target = System.getProperty("olo.temporal.target", DEFAULT_TARGET);
        String namespace = System.getenv(TEMPORAL_NAMESPACE_ENV);
        if (namespace == null || namespace.isBlank()) namespace = System.getProperty("olo.temporal.namespace", DEFAULT_NAMESPACE);

        service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target.trim()).build()
        );
        WorkflowClient client = WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder().setNamespace(namespace.trim()).build()
        );

        OloSessionCache sessionCache = new InMemorySessionCache();
        RunLedger runLedger = new org.olo.ledger.impl.NoOpRunLedger(new org.olo.ledger.impl.NoOpLedgerStore());
        ExecutionEventSink executionEventSink = ExecutionEventSink.noOp();
        PluginExecutorFactory pluginExecutorFactory = createPluginExecutorFactory();
        DynamicNodeBuilder dynamicNodeBuilder = (spec, ctx) -> {
            throw new UnsupportedOperationException("DynamicNodeBuilder not wired in test.");
        };
        NodeFeatureEnricher nodeFeatureEnricher = (node, ctx) -> node;

        OloKernelActivitiesImpl activities = new OloKernelActivitiesImpl(
                sessionCache,
                List.of("tenant-a"),
                runLedger,
                executionEventSink,
                pluginExecutorFactory,
                dynamicNodeBuilder,
                nodeFeatureEnricher
        );
        ExecuteNodeDynamicActivity executeNodeDynamicActivity = new ExecuteNodeDynamicActivity(activities);

        workerFactory = WorkerFactory.newInstance(client);
        Worker worker = workerFactory.newWorker(TASK_QUEUE, WorkerOptions.newBuilder()
                .setMaxConcurrentActivityExecutionSize(5)
                .setMaxConcurrentWorkflowTaskExecutionSize(5)
                .build());
        worker.registerWorkflowImplementationTypes(OloKernelWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities, executeNodeDynamicActivity);
        workerFactory.start();

        OloKernelWorkflow workflow = client.newWorkflowStub(
                OloKernelWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("workflow-input-debug-test-" + System.currentTimeMillis())
                        .build()
        );

        String result = workflow.run(workflowInput);
        assertNotNull(result, "Workflow should return a result");

        Path dumpDirResolved = Paths.get(System.getProperty(DUMP_DIR_PROP, DEFAULT_DUMP_DIR)).toAbsolutePath();
        assertTrue(Files.isDirectory(dumpDirResolved), "Dump dir should exist: " + dumpDirResolved);
        List<Path> dumps = Files.list(dumpDirResolved)
                .filter(p -> p.getFileName().toString().startsWith("workflow-input-") && p.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .collect(Collectors.toList());
        assertTrue(dumps.size() >= 1, "Expected at least one debug dump file under " + dumpDirResolved);

        Path dumpFile = dumps.get(dumps.size() - 1);
        JsonNode dump = JSON.readTree(Files.readString(dumpFile, StandardCharsets.UTF_8));

        JsonNode dumpInput = dump.get("workflowInput");
        assertNotNull(dumpInput, "Dump must contain workflowInput");
        assertEquals(workflowInput.getVersion(), dumpInput.path("version").asText(), "dump.workflowInput.version");
        assertEquals(workflowInput.getRouting().getPipeline(), dumpInput.path("routing").path("pipeline").asText(), "dump.workflowInput.routing.pipeline");
        assertEquals(workflowInput.getContext().getTenantId(), dumpInput.path("context").path("tenantId").asText(), "dump.workflowInput.context.tenantId");
        assertEquals(workflowInput.getRouting().getTransactionId(), dumpInput.path("routing").path("transactionId").asText(), "dump.workflowInput.routing.transactionId");
        assertTrue(dumpInput.has("inputs") && dumpInput.get("inputs").isArray(), "dump.workflowInput.inputs");
        assertEquals(workflowInput.getInputs().size(), dumpInput.get("inputs").size(), "dump.workflowInput.inputs.size");

        JsonNode localCtx = dump.get("localContext");
        assertNotNull(localCtx, "Dump must contain localContext");
        assertEquals(TASK_QUEUE, localCtx.path("effectiveQueue").asText(), "dump.localContext.effectiveQueue");
        assertEquals(workflowInput.getContext().getTenantId(), localCtx.path("tenantId").asText(), "dump.localContext.tenantId");
        JsonNode pipelineIdsNode = localCtx.get("pipelineIds");
        if (pipelineIdsNode != null && pipelineIdsNode.isArray() && pipelineIdsNode.size() > 0) {
            assertTrue(pipelineIdsNode.size() >= 1, "dump.localContext.pipelineIds when non-empty must have at least one id");
        }

        List<Path> runDumps = Files.list(dumpDirResolved)
                .filter(p -> p.getFileName().toString().startsWith("run-") && p.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .collect(Collectors.toList());
        if (!runDumps.isEmpty()) {
            assertTrue(runDumps.size() >= 3, "Expected at least 3 run dump files (input, global-context, olo-runtime-context) under " + dumpDirResolved + ", got: " + runDumps.size());
            List<String> names = runDumps.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList());
            assertTrue(names.stream().anyMatch(n -> n.contains("-input.json")), "Missing run-*-input.json in " + names);
            assertTrue(names.stream().anyMatch(n -> n.contains("-global-context.json")), "Missing run-*-global-context.json in " + names);
            assertTrue(names.stream().anyMatch(n -> n.contains("-olo-runtime-context.json")), "Missing run-*-olo-runtime-context.json in " + names);
        }
    }

    private String loadWorkflowInputJson() throws Exception {
        Path p = Paths.get(SAMPLE_INPUT_PATH);
        if (!Files.exists(p)) {
            p = Paths.get("../" + SAMPLE_INPUT_PATH);
        }
        if (Files.exists(p)) {
            return Files.readString(p);
        }
        try (var in = WorkflowInputDebugIntegrationTest.class.getResourceAsStream("/" + SAMPLE_INPUT_PATH.replace('\\', '/'))) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void runEnsureSchemaScript(Configuration config) throws Exception {
        String jdbcUrl = buildJdbcUrl(config);
        if (jdbcUrl.isEmpty()) return;
        String sql = loadEnsureSchemaSql();
        if (sql == null || sql.isBlank()) return;
        String user = config.get("olo.db.username", config.get("olo.db.user", "olo")).trim();
        if (user.isEmpty()) user = "olo";
        String password = config.get("olo.db.password", DEFAULT_DB_PASSWORD).trim();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement st = conn.createStatement()) {
            for (String statement : splitSqlStatements(sql)) {
                if (!statement.isBlank() && !statement.stripLeading().startsWith("--")) {
                    st.execute(statement);
                }
            }
        }
    }

    private String loadEnsureSchemaSql() throws Exception {
        try (var in = WorkflowInputDebugIntegrationTest.class.getResourceAsStream("/configuration/debug/ensure-schema.sql")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path p = Paths.get("configuration/debug/ensure-schema.sql");
        if (!Files.exists(p)) p = Paths.get("../configuration/debug/ensure-schema.sql");
        if (Files.exists(p)) return Files.readString(p);
        return null;
    }

    private static List<String> splitSqlStatements(String sql) {
        String withoutLineComments = sql.lines()
                .filter(line -> !line.stripLeading().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutLineComments.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String buildJdbcUrl(Configuration config) {
        String url = config.get("olo.db.url", "").trim();
        if (!url.isEmpty()) return url;
        String host = config.get("olo.db.host", "").trim();
        if (host.isEmpty()) return "";
        int port = config.getInteger("olo.db.port", 5432);
        String name = config.get("olo.db.name", "olo").trim();
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    private static PluginExecutorFactory createPluginExecutorFactory() {
        try {
            Class<?> clazz = Class.forName("org.olo.plugin.impl.DefaultPluginExecutorFactory");
            return (PluginExecutorFactory) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return (tenantId, cache) -> new org.olo.plugin.PluginExecutor() {
                @Override
                public String execute(String pluginId, String inputsJson, String nodeId) { return "{}"; }
                @Override
                public String toJson(java.util.Map<String, Object> map) { return "{}"; }
                @Override
                public java.util.Map<String, Object> fromJson(String json) { return java.util.Map.of(); }
            };
        }
    }
}
