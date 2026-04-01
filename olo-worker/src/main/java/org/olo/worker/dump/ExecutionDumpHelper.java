/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.dump;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.bootstrap.runtime.OloRuntimeContext;
import org.olo.configuration.ConfigurationProvider;
import org.olo.executiontree.config.PipelineDefinition;
import org.olo.executiontree.tree.ExecutionTreeNode;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.input.model.WorkflowInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * When {@code olo.app.dump=true}, writes input JSON, global context summary, and OloRuntimeContext
 * to {@code olo.app.dump.dir} on every execution.
 */
public final class ExecutionDumpHelper {

    private static final Logger log = LoggerFactory.getLogger(ExecutionDumpHelper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Config key: enable dump on every execution. Env: OLO_APP_DUMP. */
    public static final String OLO_APP_DUMP = "olo.app.dump";
    /** Config key: directory for dump files. Env: OLO_APP_DUMP_DIR. */
    public static final String OLO_APP_DUMP_DIR = "olo.app.dump.dir";

    private ExecutionDumpHelper() {}

    /**
     * If {@code olo.app.dump} is true, writes three artifacts under {@code olo.app.dump.dir}:
     * input JSON, global context summary JSON, and OloRuntimeContext summary JSON.
     * Uses {@code runId} and timestamp for unique filenames.
     */
    public static void dumpIfEnabled(OloRuntimeContext runtimeContext, String runId) {
        try {
            boolean dumpEnabled = false;
            String dir = "build/debug-dump";
            var config = ConfigurationProvider.get();
            if (config != null) {
                dumpEnabled = config.getBoolean(OLO_APP_DUMP, false);
                String d = config.get(OLO_APP_DUMP_DIR, null);
                if (d != null && !d.isBlank()) dir = d;
            }
            if (!dumpEnabled) {
                String prop = System.getProperty(OLO_APP_DUMP, "");
                dumpEnabled = "true".equalsIgnoreCase(prop) || "1".equals(prop);
            }
            if (!dumpEnabled) return;
            String dirProp = System.getProperty(OLO_APP_DUMP_DIR, null);
            if (dirProp != null && !dirProp.isBlank()) dir = dirProp;
            if (dir == null || dir.isBlank()) return;
            Path baseDir = Paths.get(dir);
            Files.createDirectories(baseDir);
            String prefix = "run-" + (runId != null && !runId.isBlank() ? runId : "no-run") + "-" + Instant.now().toEpochMilli();

            writeInputDump(baseDir, prefix, runtimeContext);
            writeGlobalContextDump(baseDir, prefix);
            writeOloRuntimeContextDump(baseDir, prefix, runtimeContext);
        } catch (Exception e) {
            log.warn("Execution dump setup failed: {}", e.getMessage());
        }
    }

    private static void writeInputDump(Path baseDir, String prefix, OloRuntimeContext runtimeContext) {
        try {
            WorkflowInput input = runtimeContext.getWorkflowInput();
            if (input == null) return;
            Path inputFile = baseDir.resolve(prefix + "-input.json");
            Files.writeString(inputFile, input.toJson());
            log.info("Dump: input written {}", inputFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Dump: failed to write input file: {}", e.getMessage());
        }
    }

    private static void writeGlobalContextDump(Path baseDir, String prefix) {
        try {
            Map<String, Object> globalContext = buildGlobalContextSummary();
            Path globalFile = baseDir.resolve(prefix + "-global-context.json");
            Files.writeString(globalFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(globalContext));
            log.info("Dump: global context written {}", globalFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Dump: failed to write global-context file: {}", e.getMessage());
        }
    }

    private static void writeOloRuntimeContextDump(Path baseDir, String prefix, OloRuntimeContext runtimeContext) {
        try {
            Map<String, Object> runtimeContextSummary = buildOloRuntimeContextSummary(runtimeContext);
            Path runtimeFile = baseDir.resolve(prefix + "-olo-runtime-context.json");
            Files.writeString(runtimeFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(runtimeContextSummary));
            log.info("Dump: OloRuntimeContext written {}", runtimeFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Dump: failed to write olo-runtime-context file: {}", e.getMessage());
        }
    }

    private static Map<String, Object> buildGlobalContextSummary() {
        Map<String, Object> root = new LinkedHashMap<>();
        try {
            Map<String, CompositeConfigurationSnapshot> snapshotMap = ConfigurationProvider.getSnapshotMap();
            if (snapshotMap == null || snapshotMap.isEmpty()) return root;
            Map<String, Object> byRegion = new LinkedHashMap<>();
            for (Map.Entry<String, CompositeConfigurationSnapshot> e : snapshotMap.entrySet()) {
                String region = e.getKey();
                CompositeConfigurationSnapshot composite = e.getValue();
                if (composite == null) continue;
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("region", composite.getRegion());
                summary.put("coreVersion", composite.getCoreVersion());
                summary.put("pipelinesVersion", composite.getPipelinesVersion());
                summary.put("connectionsVersion", composite.getConnectionsVersion());
                Set<String> pipelineIds = composite.getPipelines().keySet();
                summary.put("pipelineIds", pipelineIds.isEmpty() ? java.util.List.<String>of() : pipelineIds.stream().sorted().collect(Collectors.toList()));
                byRegion.put(region, summary);
            }
            root.put("servedRegions", ConfigurationProvider.getConfiguredRegions());
            root.put("snapshotsByRegion", byRegion);
        } catch (Exception e) {
            root.put("error", e.getMessage());
        }
        return root;
    }

    private static Map<String, Object> buildOloRuntimeContextSummary(OloRuntimeContext runtimeContext) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            if (runtimeContext.getWorkflowInput() != null) {
                out.put("workflowInput", MAPPER.readValue(runtimeContext.getWorkflowInput().toJson(), Map.class));
            }
            PipelineDefinition pipeline = runtimeContext.getPipelineDefinition();
            if (pipeline != null) {
                Map<String, Object> pipelineSummary = new LinkedHashMap<>();
                pipelineSummary.put("name", pipeline.getName());
                pipelineSummary.put("executionType", pipeline.getExecutionType() != null ? pipeline.getExecutionType().name() : null);
                if (pipeline instanceof org.olo.executiontree.PipelineDefinition def) {
                    pipelineSummary.put("isDynamicPipeline", def.isDynamicPipeline());
                    pipelineSummary.put("isDebugPipeline", def.isDebugPipeline());
                }
                ExecutionTreeNode root = pipeline.getExecutionTree();
                if (root != null) {
                    pipelineSummary.put("executionTreeRootId", root.getId());
                    pipelineSummary.put("executionTreeRootType", root.getType() != null ? root.getType().name() : null);
                }
                out.put("pipeline", pipelineSummary);
            }
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        return out;
    }
}
