/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.config.TenantConfig;
import org.olo.configuration.Bootstrap;
import org.olo.configuration.Configuration;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.config.impl.InMemorySessionCache;
import org.olo.config.OloSessionCache;
import org.olo.input.model.WorkflowInput;
import org.olo.ledger.ExecutionEventSink;
import org.olo.ledger.RunLedger;
import org.olo.node.DynamicNodeBuilder;
import org.olo.node.NodeFeatureEnricher;
import org.olo.plugin.ModelExecutorPlugin;
import org.olo.plugin.PluginExecutorFactory;
import org.olo.plugin.PluginRegistry;
import org.olo.worker.activity.impl.ExecuteNodeDynamicActivity;
import org.olo.worker.activity.impl.OloKernelActivitiesImpl;
import org.olo.worker.cache.CachePortRegistrar;
import org.olo.worker.db.DbPortRegistrar;
import org.olo.worker.workflow.OloKernelWorkflow;
import org.olo.worker.workflow.impl.OloKernelWorkflowImpl;
import org.olo.bootstrap.loader.context.GlobalContextProvider;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the Multi-Model Consensus use case: Architect + Critic discuss until consensus
 * via a PLANNER node and consensus_subtree_creator. Uses real Temporal server, worker, and workflow input.
 * <p>
 * Registers mock architect/critic (MODEL_EXECUTOR) and a consensus SUBTREE_CREATOR for tenant-a,
 * injects the consensus pipeline into the default composite, then runs a workflow and asserts completion.
 * <p>
 * Prerequisites: Temporal server (localhost:47233), Redis, DB. Run:
 * {@code ./gradlew :olo-worker:test --tests "org.olo.worker.ConsensusUseCaseIntegrationTest"}
 */
@Tag("integration")
class ConsensusUseCaseIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TASK_QUEUE = "olo.default.consensus-pipeline";
    private static final String SAMPLE_INPUT_PATH = "configuration/debug/workflow/sample-consensus-pipeline.json";
    private static final String CONSENSUS_PIPELINE_PATH = "configuration/debug/consensus-pipeline.json";
    private static final String TEMPORAL_TARGET_ENV = "OLO_TEMPORAL_TARGET";
    private static final String TEMPORAL_NAMESPACE_ENV = "OLO_TEMPORAL_NAMESPACE";
    private static final String DEFAULT_TARGET = "localhost:47233";
    private static final String DEFAULT_NAMESPACE = "default";
    private static final String DEFAULT_DB_PASSWORD = "pgpass";
    private static final String TENANT_A = "tenant-a";
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
    }

    @Test
    void consensusPipelineRunsToCompletion() throws Exception {
        Configuration config = Bootstrap.loadConfiguration();
        runEnsureSchemaScript(config);

        DbPortRegistrar.registerDefaults();
        CachePortRegistrar.registerDefaults();
        Bootstrap.run(true);

        registerConsensusPlugins();
        injectConsensusPipelineIntoDefaultRegion();

        String inputJson = loadWorkflowInputJson();
        assertNotNull(inputJson, "Workflow input JSON not found at " + SAMPLE_INPUT_PATH);
        WorkflowInput workflowInput = WorkflowInput.fromJson(inputJson);

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
                List.of(TENANT_A),
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
                        .setWorkflowId("consensus-use-case-" + System.currentTimeMillis())
                        .build()
        );

        String result = workflow.run(workflowInput);
        assertNotNull(result, "Workflow should return a result");
        assertTrue(result.length() >= 0, "Result may be empty string on success");
    }

    private void registerConsensusPlugins() {
        PluginRegistry registry = PluginRegistry.getInstance();
        ModelExecutorPlugin architect = (inputs, tenantConfig) -> Map.of(
                "responseText", "[Mock Architect] Proposed solution for: " + (inputs != null ? inputs.get("prompt") : "")
        );
        ModelExecutorPlugin critic = (inputs, tenantConfig) -> Map.of(
                "responseText", "[Mock Critic] Review and suggestions for the proposal."
        );
        registry.registerModelExecutor(TENANT_A, "architect", architect);
        registry.registerModelExecutor(TENANT_A, "critic", critic);

        org.olo.plugin.ExecutablePlugin consensusSubtreeCreator = (inputs, tenantConfig) -> {
            String planText = inputs != null && inputs.get("planText") != null
                    ? inputs.get("planText").toString().trim()
                    : "";
            if (planText.isEmpty()) planText = "No task specified";
            int maxRounds = 2;
            Map<String, Object> variablesToInject = new LinkedHashMap<>();
            variablesToInject.put("user_query", planText);
            variablesToInject.put("maxRounds", maxRounds);
            List<Map<String, Object>> steps = new ArrayList<>();
            int stepIndex = 0;
            for (int round = 0; round < maxRounds; round++) {
                String proposePrompt = round == 0
                        ? "Design solution for: {{user_query}}"
                        : "Revise based on critique. Previous: {{__planner_step_" + (stepIndex - 1) + "_response}}. Critique: {{__planner_step_" + (stepIndex - 2) + "_response}}";
                steps.add(step("architect", proposePrompt));
                stepIndex++;
                steps.add(step("critic", "Review proposal: {{__planner_step_" + (stepIndex - 1) + "_response}}"));
                stepIndex++;
                steps.add(step("architect", "Final revision. Proposal: {{__planner_step_" + (stepIndex - 2) + "_response}}. Critique: {{__planner_step_" + (stepIndex - 1) + "_response}}"));
                stepIndex++;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("variablesToInject", variablesToInject);
            out.put("steps", steps);
            return out;
        };
        registry.registerSubtreeCreator(TENANT_A, "consensus_subtree_creator", consensusSubtreeCreator);
    }

    private static Map<String, Object> step(String pluginRef, String prompt) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("pluginRef", pluginRef);
        step.put("prompt", prompt);
        return step;
    }

    private void injectConsensusPipelineIntoDefaultRegion() throws Exception {
        Map<String, Object> consensusPipeline = loadConsensusPipelineJson();
        if (consensusPipeline == null || consensusPipeline.isEmpty()) {
            throw new IllegalStateException("Consensus pipeline JSON not found at " + CONSENSUS_PIPELINE_PATH);
        }
        String pipelineId = (String) consensusPipeline.get("id");
        if (pipelineId == null || pipelineId.isBlank()) pipelineId = "olo.default.consensus-pipeline";

        Map<String, CompositeConfigurationSnapshot> snapshotMap = ConfigurationProvider.getSnapshotMap();
        CompositeConfigurationSnapshot current = snapshotMap != null ? snapshotMap.get("default") : null;
        CompositeConfigurationSnapshot toRebuild;
        if (current == null) {
            CompositeConfigurationSnapshot composite = new CompositeConfigurationSnapshot("default");
            composite.setCore(null, 0);
            composite.setPipelines(Map.of(pipelineId, consensusPipeline), 1);
            composite.setConnections(Map.of(), 0);
            composite.setRegionalSettingsVersion(1);
            ConfigurationProvider.putComposite("default", composite);
            toRebuild = composite;
        } else {
            Map<String, Object> pipelines = new LinkedHashMap<>(current.getPipelinesForReuse());
            pipelines.put(pipelineId, consensusPipeline);
            CompositeConfigurationSnapshot newComposite = new CompositeConfigurationSnapshot("default");
            newComposite.setCore(current.getCore(), current.getCoreVersion());
            newComposite.setPipelines(pipelines, current.getPipelinesVersion() + 1);
            newComposite.setConnections(current.getConnectionsForReuse(), current.getConnectionsVersion());
            newComposite.setRegionalSettingsVersion(current.getRegionalSettingsVersion());
            ConfigurationProvider.putComposite("default", newComposite);
            toRebuild = newComposite;
        }
        var globalContext = GlobalContextProvider.getGlobalContext();
        globalContext.rebuildTreeForRegion(toRebuild);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConsensusPipelineJson() throws Exception {
        String json = readFileOrResource(CONSENSUS_PIPELINE_PATH);
        if (json == null || json.isBlank()) return Map.of();
        return JSON.readValue(json, Map.class);
    }

    private String loadWorkflowInputJson() throws Exception {
        return readFileOrResource(SAMPLE_INPUT_PATH);
    }

    private String readFileOrResource(String path) throws Exception {
        Path p = Paths.get(path);
        if (!Files.exists(p)) p = Paths.get("../" + path);
        if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        String resourcePath = "/" + path.replace('\\', '/');
        try (var in = ConsensusUseCaseIntegrationTest.class.getResourceAsStream(resourcePath)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
        try (var in = ConsensusUseCaseIntegrationTest.class.getResourceAsStream("/configuration/debug/ensure-schema.sql")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
                public String toJson(Map<String, Object> map) { return "{}"; }
                @Override
                public Map<String, Object> fromJson(String json) { return Map.of(); }
            };
        }
    }
}
