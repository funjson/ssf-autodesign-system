package com.ssf.autodesign.dto;

import java.util.List;

public final class SpecGraphDtos {
    private SpecGraphDtos() {
    }

    public record DocumentSourceDto(String key, String path, boolean exists) {
    }

    public record PrototypeFrameDto(String screenId, String assetPath, String assetUrl, boolean exists) {
    }

    public record PrototypeSourceDto(
            String sourceType,
            String label,
            String description,
            String rootPath,
            boolean userProvided,
            List<PrototypeFrameDto> frames
    ) {
    }

    public record ScreenNodeDto(
            String screenId,
            String frameName,
            String title,
            String goal,
            String entry,
            String exit,
            List<String> relatedFeatures,
            List<String> relatedRules,
            List<String> relatedAcceptances,
            List<String> componentIds
    ) {
    }

    public record ComponentNodeDto(
            String componentId,
            String screenId,
            String name,
            String visibleText,
            String interaction,
            String relationText,
            List<String> relatedFeatures,
            List<String> relatedRules,
            List<String> relatedAcceptances,
            List<String> annotationIds
    ) {
    }

    public record TableDto(String title, List<String> headers, List<List<String>> rows) {
    }

    public record FeatureSectionDto(
            String sectionNo,
            String title,
            String content,
            List<String> bullets,
            List<TableDto> tables
    ) {
    }

    public record KeyValueDto(String key, String value) {
    }

    public record MainFlowStepDto(String step, String actor, String action, String result, String relatedUi) {
    }

    public record BranchFlowDto(String branchId, String trigger, String flow, String result, String relatedRule) {
    }

    public record ExceptionFlowDto(
            String exceptionId,
            String scenario,
            String systemBehavior,
            String userTip,
            String recovery,
            String relatedAcceptance
    ) {
    }

    public record FeatureNodeDto(
            String featureId,
            String name,
            String goal,
            String relatedScreensText,
            String sourcePath,
            String excerpt,
            List<KeyValueDto> metadata,
            List<String> goals,
            String userStory,
            List<MainFlowStepDto> mainFlow,
            List<BranchFlowDto> branchFlows,
            List<ExceptionFlowDto> exceptionFlows,
            List<FeatureSectionDto> sections
    ) {
    }

    public record RuleNodeDto(
            String ruleId,
            String featureId,
            String type,
            String trigger,
            String rule,
            String result,
            String display,
            String testFocus,
            String sourcePath
    ) {
    }

    public record AcceptanceNodeDto(
            String acId,
            String featureId,
            String point,
            String type,
            String given,
            String when,
            String thenText,
            String sourcePath
    ) {
    }

    public record AnnotationNodeDto(
            String pannoId,
            String screenId,
            String componentId,
            String layerName,
            String note,
            List<String> relatedFeatures,
            List<String> relatedRules,
            List<String> relatedAcceptances
    ) {
    }

    public record FlowNodeDto(String flowId, String connection, String note, List<String> relatedAcceptances) {
    }

    public record SpecGraphDto(
            Long instanceId,
            String instanceCode,
            String instanceName,
            String productName,
            String rootPath,
            List<DocumentSourceDto> sources,
            PrototypeSourceDto prototypeSource,
            List<ScreenNodeDto> screens,
            List<ComponentNodeDto> components,
            List<FeatureNodeDto> features,
            List<RuleNodeDto> rules,
            List<AcceptanceNodeDto> acceptances,
            List<AnnotationNodeDto> annotations,
            List<FlowNodeDto> flows,
            List<String> warnings
    ) {
    }
}
