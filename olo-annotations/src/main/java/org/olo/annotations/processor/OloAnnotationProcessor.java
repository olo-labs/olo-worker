/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.annotations.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.olo.annotations.FeatureInfo;
import org.olo.annotations.FeaturePhase;
import org.olo.annotations.OloFeature;
import org.olo.annotations.OloPlugin;
import org.olo.annotations.OloPluginParam;
import org.olo.annotations.OloUiComponent;
import org.olo.annotations.PluginInfo;
import org.olo.annotations.PluginParamInfo;
import org.olo.annotations.UiComponentInfo;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Generates META-INF/olo-features.json and META-INF/olo-ui-components.json from
 * {@link OloFeature} and {@link OloUiComponent} annotations so they can be loaded at bootstrap.
 */
@SupportedAnnotationTypes({
    "org.olo.annotations.OloFeature",
    "org.olo.annotations.OloPlugin",
    "org.olo.annotations.OloUiComponent"
})
public class OloAnnotationProcessor extends AbstractProcessor {

    private static final String FEATURES_RESOURCE = "META-INF/olo-features.json";
    private static final String PLUGINS_RESOURCE = "META-INF/olo-plugins.json";
    private static final String UI_COMPONENTS_RESOURCE = "META-INF/olo-ui-components.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        List<FeatureInfo> features = new ArrayList<>();
        List<PluginInfo> plugins = new ArrayList<>();
        List<UiComponentInfo> components = new ArrayList<>();

        for (Element e : roundEnv.getElementsAnnotatedWith(OloFeature.class)) {
            if (!(e instanceof TypeElement)) continue;
            TypeElement te = (TypeElement) e;
            OloFeature ann = te.getAnnotation(OloFeature.class);
            if (ann == null) continue;
            String className = te.getQualifiedName().toString();
            FeaturePhase phase = ann.phase();
            String[] arr = ann.applicableNodeTypes();
            List<String> nodeTypes = (arr == null || arr.length == 0) ? null : java.util.Arrays.asList(arr);
            String contractVersion = ann.contractVersion() != null && !ann.contractVersion().isEmpty() ? ann.contractVersion() : "1.0";
            features.add(new FeatureInfo(ann.name(), contractVersion, phase.name(), nodeTypes, className));
        }

        for (Element e : roundEnv.getElementsAnnotatedWith(OloPlugin.class)) {
            if (!(e instanceof TypeElement)) continue;
            TypeElement te = (TypeElement) e;
            OloPlugin ann = te.getAnnotation(OloPlugin.class);
            if (ann == null) continue;
            String className = te.getQualifiedName().toString();
            String displayName = emptyToNull(ann.displayName());
            if (displayName == null) displayName = ann.id();
            List<PluginParamInfo> inParams = toParamList(ann.inputParameters());
            List<PluginParamInfo> outParams = toParamList(ann.outputParameters());
            String contractVersion = ann.contractVersion() != null && !ann.contractVersion().isEmpty() ? ann.contractVersion() : "1.0";
            plugins.add(new PluginInfo(
                    ann.id(),
                    displayName,
                    ann.contractType(),
                    contractVersion,
                    emptyToNull(ann.description()),
                    emptyToNull(ann.category()),
                    emptyToNull(ann.icon()),
                    inParams,
                    outParams,
                    className
            ));
        }

        for (Element e : roundEnv.getElementsAnnotatedWith(OloUiComponent.class)) {
            if (!(e instanceof TypeElement)) continue;
            TypeElement te = (TypeElement) e;
            OloUiComponent ann = te.getAnnotation(OloUiComponent.class);
            if (ann == null) continue;
            String className = te.getQualifiedName().toString();
            String name = ann.name() == null || ann.name().isEmpty() ? ann.id() : ann.name();
            components.add(new UiComponentInfo(
                    ann.id(),
                    name,
                    emptyToNull(ann.category()),
                    emptyToNull(ann.description()),
                    emptyToNull(ann.icon()),
                    className
            ));
        }

        if (!features.isEmpty()) {
            try {
                FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", FEATURES_RESOURCE);
                try (Writer w = f.openWriter()) {
                    mapper.writeValue(w, features);
                }
            } catch (IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write " + FEATURES_RESOURCE + ": " + ex.getMessage());
            }
        }
        if (!plugins.isEmpty()) {
            try {
                FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", PLUGINS_RESOURCE);
                try (Writer w = f.openWriter()) {
                    mapper.writeValue(w, plugins);
                }
            } catch (IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write " + PLUGINS_RESOURCE + ": " + ex.getMessage());
            }
        }
        if (!components.isEmpty()) {
            try {
                FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", UI_COMPONENTS_RESOURCE);
                try (Writer w = f.openWriter()) {
                    mapper.writeValue(w, components);
                }
            } catch (IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write " + UI_COMPONENTS_RESOURCE + ": " + ex.getMessage());
            }
        }

        return false;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static List<PluginParamInfo> toParamList(OloPluginParam[] params) {
        if (params == null || params.length == 0) return List.of();
        List<PluginParamInfo> list = new ArrayList<>();
        for (OloPluginParam p : params) {
            list.add(new PluginParamInfo(p.name(), p.type(), p.required()));
        }
        return list;
    }
}
