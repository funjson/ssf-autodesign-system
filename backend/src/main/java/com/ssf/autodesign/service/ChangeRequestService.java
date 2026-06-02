package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.ChangeRequestEntity;
import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.dto.ChangeRequestDto;
import com.ssf.autodesign.dto.SpecGraphDtos.AcceptanceNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.AnnotationNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ComponentNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.FeatureNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.RuleNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ScreenNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.SpecGraphDto;
import com.ssf.autodesign.repository.ChangeRequestRepository;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates one-shot change request files for external CLI agents.
 *
 * The system does not rely on a remembered AI session. Each CR includes the
 * selected prototype context plus enough product facts for a fresh agent run.
 */
@Service
public class ChangeRequestService {
    private static final Pattern CR_FILE = Pattern.compile("CR-(\\d{3})\\.md");

    private final SpiInstanceRepository spiInstanceRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final SpecGraphService specGraphService;
    private final DtoMapper mapper;

    public ChangeRequestService(SpiInstanceRepository spiInstanceRepository,
                                ChangeRequestRepository changeRequestRepository,
                                SpecGraphService specGraphService,
                                DtoMapper mapper) {
        this.spiInstanceRepository = spiInstanceRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.specGraphService = specGraphService;
        this.mapper = mapper;
    }

    @Transactional
    public ChangeRequestDto create(Long instanceId,
                                   String title,
                                   String screenId,
                                   String componentId,
                                   String userIntent,
                                   String extraNotes) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        if (userIntent == null || userIntent.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "修改意图不能为空");
        }

        SpecGraphDto graph = specGraphService.loadGraph(instanceId);
        Optional<ScreenNodeDto> selectedScreen = findScreen(graph, screenId);
        Optional<ComponentNodeDto> selectedComponent = findComponent(graph, screenId, componentId);
        String changeCode = nextChangeCode(Path.of(instance.getRootPath()).resolve("change-requests"));
        String finalTitle = normalizeTitle(title, selectedScreen, selectedComponent);
        String markdown = buildMarkdown(changeCode, finalTitle, graph, selectedScreen, selectedComponent, userIntent, extraNotes);
        Path filePath = writeChangeFile(instance, changeCode, markdown);

        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setSpiInstance(instance);
        entity.setChangeCode(changeCode);
        entity.setTitle(finalTitle);
        entity.setStatus("draft");
        entity.setScreenId(screenId == null ? "" : screenId);
        entity.setComponentId(componentId == null ? "" : componentId);
        entity.setUserIntent(userIntent.trim());
        entity.setExtraNotes(extraNotes == null ? "" : extraNotes.trim());
        entity.setFilePath(filePath.toString());
        return mapper.toChangeRequestDto(changeRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestDto> list(Long instanceId) {
        return changeRequestRepository.findBySpiInstanceIdOrderByCreatedAtDesc(instanceId).stream()
                .map(mapper::toChangeRequestDto)
                .toList();
    }

    private Optional<ScreenNodeDto> findScreen(SpecGraphDto graph, String screenId) {
        if (screenId == null || screenId.isBlank()) {
            return Optional.empty();
        }
        return graph.screens().stream().filter(screen -> screen.screenId().equals(screenId)).findFirst();
    }

    private Optional<ComponentNodeDto> findComponent(SpecGraphDto graph, String screenId, String componentId) {
        if (componentId == null || componentId.isBlank()) {
            return Optional.empty();
        }
        return graph.components().stream()
                .filter(component -> component.componentId().equals(componentId))
                .filter(component -> screenId == null || screenId.isBlank() || component.screenId().equals(screenId))
                .findFirst();
    }

    private String normalizeTitle(String title, Optional<ScreenNodeDto> screen, Optional<ComponentNodeDto> component) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (component.isPresent()) {
            return "调整 " + component.get().componentId() + " " + component.get().name();
        }
        if (screen.isPresent()) {
            return "调整 " + screen.get().screenId() + " " + screen.get().title();
        }
        return "产品与原型变更";
    }

    private String nextChangeCode(Path dir) {
        int max = 0;
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    Matcher matcher = CR_FILE.matcher(file.getFileName().toString());
                    if (matcher.matches()) {
                        max = Math.max(max, Integer.parseInt(matcher.group(1)));
                    }
                }
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "读取 change-requests 失败：" + ex.getMessage());
            }
        }
        return "CR-" + String.format("%03d", max + 1);
    }

    private Path writeChangeFile(SpiInstanceEntity instance, String changeCode, String markdown) {
        Path dir = Path.of(instance.getRootPath()).resolve("change-requests");
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(changeCode + ".md");
            Files.writeString(file, markdown, StandardCharsets.UTF_8);
            return file;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "写入 change-request 失败：" + ex.getMessage());
        }
    }

    private String buildMarkdown(String changeCode,
                                 String title,
                                 SpecGraphDto graph,
                                 Optional<ScreenNodeDto> selectedScreen,
                                 Optional<ComponentNodeDto> selectedComponent,
                                 String userIntent,
                                 String extraNotes) {
        // Start with the clicked page/component, then pull in annotation links so the CR is self-contained.
        Set<String> featureIds = new LinkedHashSet<>();
        Set<String> ruleIds = new LinkedHashSet<>();
        Set<String> acceptanceIds = new LinkedHashSet<>();
        selectedScreen.ifPresent(screen -> {
            featureIds.addAll(screen.relatedFeatures());
            ruleIds.addAll(screen.relatedRules());
            acceptanceIds.addAll(screen.relatedAcceptances());
        });
        selectedComponent.ifPresent(component -> {
            featureIds.addAll(component.relatedFeatures());
            ruleIds.addAll(component.relatedRules());
            acceptanceIds.addAll(component.relatedAcceptances());
        });
        List<AnnotationNodeDto> relatedAnnotations = graph.annotations().stream()
                .filter(annotation -> selectedScreen.map(screen -> screen.screenId().equals(annotation.screenId())).orElse(false))
                .filter(annotation -> selectedComponent.isEmpty()
                        || annotation.componentId() == null
                        || annotation.componentId().equals(selectedComponent.get().componentId()))
                .toList();
        relatedAnnotations.forEach(annotation -> {
            featureIds.addAll(annotation.relatedFeatures());
            ruleIds.addAll(annotation.relatedRules());
            acceptanceIds.addAll(annotation.relatedAcceptances());
        });

        List<FeatureNodeDto> features = graph.features().stream()
                .filter(feature -> featureIds.contains(feature.featureId()))
                .toList();
        List<RuleNodeDto> rules = graph.rules().stream()
                .filter(rule -> ruleIds.contains(rule.ruleId()) || featureIds.contains(rule.featureId()))
                .toList();
        List<AcceptanceNodeDto> acceptances = graph.acceptances().stream()
                .filter(acceptance -> acceptanceIds.contains(acceptance.acId()) || featureIds.contains(acceptance.featureId()))
                .toList();

        StringBuilder md = new StringBuilder();
        md.append("# ").append(changeCode).append(' ').append(title).append("\n\n");
        md.append("> 本文件由 ssf-autodesign 生成，用于把用户在原型上的修改意图转成一次性可交给 Codex / Cursor / Claude CLI 的结构化任务上下文。\n\n");
        md.append("## 0. 元信息\n\n");
        md.append("| 字段 | 内容 |\n|---|---|\n");
        md.append("| change_id | ").append(changeCode).append(" |\n");
        md.append("| instance_id | ").append(escapeTable(graph.instanceCode())).append(" |\n");
        md.append("| product_name | ").append(escapeTable(graph.productName())).append(" |\n");
        md.append("| status | draft |\n");
        md.append("| generated_at | ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(" |\n");
        md.append("| selected_screen | ").append(selectedScreen.map(ScreenNodeDto::screenId).orElse("无")).append(" |\n");
        md.append("| selected_component | ").append(selectedComponent.map(ComponentNodeDto::componentId).orElse("无")).append(" |\n\n");

        md.append("## 1. 用户修改意图\n\n").append(userIntent.trim()).append("\n\n");
        if (extraNotes != null && !extraNotes.isBlank()) {
            md.append("## 2. 补充说明\n\n").append(extraNotes.trim()).append("\n\n");
        }

        md.append("## 3. 被点击的原型上下文\n\n");
        selectedScreen.ifPresentOrElse(screen -> {
            md.append("### 3.1 页面\n\n");
            md.append("| screen_id | frame_name | title | goal | entry | exit |\n|---|---|---|---|---|---|\n");
            md.append("| ").append(screen.screenId()).append(" | ").append(escapeTable(screen.frameName())).append(" | ")
                    .append(escapeTable(screen.title())).append(" | ").append(escapeTable(screen.goal())).append(" | ")
                    .append(escapeTable(screen.entry())).append(" | ").append(escapeTable(screen.exit())).append(" |\n\n");
        }, () -> md.append("未选择具体页面。\n\n"));
        selectedComponent.ifPresent(component -> {
            md.append("### 3.2 组件\n\n");
            md.append("| component_id | screen_id | name | visible_text | interaction | relations |\n|---|---|---|---|---|---|\n");
            md.append("| ").append(component.componentId()).append(" | ").append(component.screenId()).append(" | ")
                    .append(escapeTable(component.name())).append(" | ").append(escapeTable(component.visibleText())).append(" | ")
                    .append(escapeTable(component.interaction())).append(" | ").append(escapeTable(component.relationText())).append(" |\n\n");
        });

        md.append("## 4. 相关产品设计事实\n\n");
        md.append("### 4.1 功能任务\n\n");
        appendFeatures(md, features);
        md.append("### 4.2 业务规则\n\n");
        appendRules(md, rules);
        md.append("### 4.3 验收标准\n\n");
        appendAcceptances(md, acceptances);
        md.append("### 4.4 原型标注\n\n");
        appendAnnotations(md, relatedAnnotations);

        md.append("## 5. 建议修改策略\n\n");
        md.append("1. 先判断用户意图是新增需求、修改功能描述、修改业务规则、修改验收标准，还是仅修改原型表达。\n");
        md.append("2. 以 `product-spec/` 为事实源进行反向修改，再同步更新 `prototype-input/` 中受影响的 screen contract、prompt、annotation、review checklist。\n");
        md.append("3. 如果修改影响产品架构边界或模块职责，需要在结果中明确提示进入 architecture delta review。\n");
        md.append("4. 不直接把 ID 渲染为用户可见文字；ID 应保留在 frame/layer/annotation 或结构化文档中。\n");
        md.append("5. 完成后输出受影响文件清单、变更摘要和需要重新导出/刷新原型的操作建议。\n\n");

        md.append("## 6. CLI 执行提示词\n\n");
        md.append("```text\n");
        md.append("你是 ssf-product-pm 产品设计文档维护 Agent。请读取当前 SPI 实例的 manifest.md、product-spec/ 和 prototype-input/，并基于本 change-request 执行最小必要修改。\n");
        md.append("要求：先修改产品设计事实源，再同步派生原型输入包；保持所有 SCR/CMP/FEAT/BR/AC ID 可追踪；不要新增未定义页面；完成后运行一致性检查并说明风险。\n");
        md.append("```\n");
        return md.toString();
    }

    private void appendFeatures(StringBuilder md, List<FeatureNodeDto> features) {
        if (features.isEmpty()) {
            md.append("无直接关联 FEAT，执行时需从用户意图重新判断影响范围。\n\n");
            return;
        }
        for (FeatureNodeDto feature : features) {
            md.append("#### ").append(feature.featureId()).append(' ').append(feature.name()).append("\n\n");
            md.append("- 目标：").append(blankTo(feature.goal(), "未解析到目标")).append("\n");
            md.append("- 关联页面：").append(blankTo(feature.relatedScreensText(), "未解析到页面")).append("\n");
            md.append("- 来源：").append(feature.sourcePath()).append("\n\n");
        }
    }

    private void appendRules(StringBuilder md, List<RuleNodeDto> rules) {
        if (rules.isEmpty()) {
            md.append("无直接关联 BR。\n\n");
            return;
        }
        md.append("| rule_id | feature_id | type | trigger | rule | result | test_focus |\n|---|---|---|---|---|---|---|\n");
        for (RuleNodeDto rule : rules) {
            md.append("| ").append(rule.ruleId()).append(" | ").append(rule.featureId()).append(" | ")
                    .append(escapeTable(rule.type())).append(" | ").append(escapeTable(rule.trigger())).append(" | ")
                    .append(escapeTable(rule.rule())).append(" | ").append(escapeTable(rule.result())).append(" | ")
                    .append(escapeTable(rule.testFocus())).append(" |\n");
        }
        md.append('\n');
    }

    private void appendAcceptances(StringBuilder md, List<AcceptanceNodeDto> acceptances) {
        if (acceptances.isEmpty()) {
            md.append("无直接关联 AC。\n\n");
            return;
        }
        md.append("| ac_id | feature_id | point | type | Given | When | Then |\n|---|---|---|---|---|---|---|\n");
        for (AcceptanceNodeDto ac : acceptances) {
            md.append("| ").append(ac.acId()).append(" | ").append(ac.featureId()).append(" | ")
                    .append(escapeTable(ac.point())).append(" | ").append(escapeTable(ac.type())).append(" | ")
                    .append(escapeTable(ac.given())).append(" | ").append(escapeTable(ac.when())).append(" | ")
                    .append(escapeTable(ac.thenText())).append(" |\n");
        }
        md.append('\n');
    }

    private void appendAnnotations(StringBuilder md, List<AnnotationNodeDto> annotations) {
        if (annotations.isEmpty()) {
            md.append("无直接关联 PANN。\n\n");
            return;
        }
        md.append("| panno_id | screen_id | component_id | layer_name | note |\n|---|---|---|---|---|\n");
        for (AnnotationNodeDto annotation : annotations) {
            md.append("| ").append(annotation.pannoId()).append(" | ").append(annotation.screenId()).append(" | ")
                    .append(blankTo(annotation.componentId(), "页面级")).append(" | ")
                    .append(escapeTable(annotation.layerName())).append(" | ")
                    .append(escapeTable(annotation.note())).append(" |\n");
        }
        md.append('\n');
    }

    private static String escapeTable(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", "<br>").trim();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
