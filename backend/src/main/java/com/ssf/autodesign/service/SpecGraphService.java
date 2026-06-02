package com.ssf.autodesign.service;

import com.ssf.autodesign.domain.SpiInstanceEntity;
import com.ssf.autodesign.dto.SpecGraphDtos.AcceptanceNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.AnnotationNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.BranchFlowDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ComponentNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.DocumentSourceDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ExceptionFlowDto;
import com.ssf.autodesign.dto.SpecGraphDtos.FeatureNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.FeatureSectionDto;
import com.ssf.autodesign.dto.SpecGraphDtos.FlowNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.KeyValueDto;
import com.ssf.autodesign.dto.SpecGraphDtos.MainFlowStepDto;
import com.ssf.autodesign.dto.SpecGraphDtos.RuleNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.ScreenNodeDto;
import com.ssf.autodesign.dto.SpecGraphDtos.SpecGraphDto;
import com.ssf.autodesign.dto.SpecGraphDtos.TableDto;
import com.ssf.autodesign.repository.SpiInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the read model used by the prototype workspace.
 *
 * product-spec is treated as the product truth source, while prototype-input
 * provides clickable screen/component contracts and handoff annotations.
 */
@Service
public class SpecGraphService {
    private static final Pattern SCREEN_HEADING = Pattern.compile("^##\\s+\\d+\\.\\s+(SCR-\\d{3})\\s+(.+)$");
    private static final Pattern FEATURE_HEADING = Pattern.compile("^#\\s+(FEAT-\\d{3})\\s+(.+)$");
    private static final Pattern FEATURE_SECTION_HEADING = Pattern.compile("^##\\s+(2\\.\\d+)\\s+(.+)$");
    private static final Pattern ANY_ID = Pattern.compile("\\b(FEAT|BR|AC|SCR|CMP|PANN|PFLOW)-\\d{3}\\b");

    private final SpiInstanceRepository spiInstanceRepository;
    private final MarkdownFileService markdownFileService;
    private final PrototypeAssetService prototypeAssetService;

    public SpecGraphService(SpiInstanceRepository spiInstanceRepository,
                            MarkdownFileService markdownFileService,
                            PrototypeAssetService prototypeAssetService) {
        this.spiInstanceRepository = spiInstanceRepository;
        this.markdownFileService = markdownFileService;
        this.prototypeAssetService = prototypeAssetService;
    }

    @Transactional(readOnly = true)
    public SpecGraphDto loadGraph(Long instanceId) {
        SpiInstanceEntity instance = spiInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPI 实例不存在：" + instanceId));
        Path root = Path.of(instance.getRootPath());
        List<String> warnings = new ArrayList<>();
        Map<String, ScreenModel> screens = new LinkedHashMap<>();
        Map<String, ComponentModel> components = new LinkedHashMap<>();
        Map<String, FeatureModel> features = new LinkedHashMap<>();
        Map<String, RuleModel> rules = new LinkedHashMap<>();
        Map<String, AcceptanceModel> acceptances = new LinkedHashMap<>();
        List<AnnotationNodeDto> annotations = new ArrayList<>();
        List<FlowNodeDto> flows = new ArrayList<>();

        // Merge prototype-facing contracts first, then enrich them with product facts.
        parseScreenContracts(root.resolve("prototype-input/02-screen-contracts.md"), warnings, screens, components);
        parseAnnotations(root.resolve("prototype-input/06-ui-annotation-handoff.md"), warnings, screens, components, annotations, flows);
        parseFeatureSpec(root.resolve("product-spec/06-feature-task-spec.md"), warnings, features, rules, acceptances);

        List<ScreenNodeDto> screenDtos = screens.values().stream()
                .map(ScreenModel::toDto)
                .sorted(Comparator.comparing(ScreenNodeDto::screenId))
                .toList();
        List<ComponentNodeDto> componentDtos = components.values().stream()
                .map(ComponentModel::toDto)
                .sorted(Comparator.comparing(ComponentNodeDto::screenId).thenComparing(ComponentNodeDto::componentId))
                .toList();
        return new SpecGraphDto(
                instance.getId(),
                instance.getInstanceCode(),
                instance.getInstanceName(),
                instance.getProductName(),
                instance.getRootPath(),
                buildSources(root),
                prototypeAssetService.buildSource(instance.getId(), root, screenDtos),
                screenDtos,
                componentDtos,
                features.values().stream().map(FeatureModel::toDto).toList(),
                rules.values().stream().map(RuleModel::toDto).toList(),
                acceptances.values().stream().map(AcceptanceModel::toDto).toList(),
                annotations,
                flows,
                warnings
        );
    }

    private List<DocumentSourceDto> buildSources(Path root) {
        List<String> paths = List.of(
                "product-spec/05-prd.md",
                "product-spec/06-feature-task-spec.md",
                "product-spec/07-ui-ia-screen-inventory.md",
                "product-spec/08-structured-ui-interaction-spec.md",
                "product-spec/09-prototype-prompt-ui-annotation.md",
                "product-spec/10-product-baseline-change.md",
                "prototype-input/00-prototype-master-brief.md",
                "prototype-input/02-screen-contracts.md",
                "prototype-input/03-flow-contracts.md",
                "prototype-input/04-sample-data.md",
                "prototype-input/05-figma-make-prompts.md",
                "prototype-input/06-ui-annotation-handoff.md",
                "prototype-input/07-prototype-review-checklist.md"
        );
        return paths.stream()
                .map(path -> new DocumentSourceDto(path, root.resolve(path).toString(), Files.exists(root.resolve(path))))
                .toList();
    }

    private void parseScreenContracts(Path file,
                                      List<String> warnings,
                                      Map<String, ScreenModel> screens,
                                      Map<String, ComponentModel> components) {
        // 02-screen-contracts.md is the MVP source for synthetic prototype blocks.
        List<String> lines = markdownFileService.readUtf8Lines(file, warnings);
        String currentScreenId = null;
        for (String line : lines) {
            Matcher heading = SCREEN_HEADING.matcher(line.trim());
            if (heading.find()) {
                currentScreenId = heading.group(1);
                ScreenModel screen = screens.computeIfAbsent(currentScreenId, ScreenModel::new);
                if (screen.title == null || screen.title.isBlank()) {
                    screen.title = heading.group(2).trim();
                }
                continue;
            }

            List<String> cells = parseTableRow(line);
            if (cells.isEmpty() || isSeparatorRow(cells)) {
                continue;
            }
            String first = cells.get(0);
            if (first.matches("SCR-\\d{3}") && cells.size() >= 6) {
                ScreenModel screen = screens.computeIfAbsent(first, ScreenModel::new);
                screen.frameName = cells.get(1);
                screen.title = cells.get(2);
                screen.goal = cells.get(3);
                screen.entry = cells.get(4);
                screen.exit = cells.get(5);
                mergeIds(screen.features, cells.get(5), "FEAT");
                mergeIds(screen.rules, cells.get(5), "BR");
                mergeIds(screen.acceptances, cells.get(5), "AC");
                continue;
            }

            List<String> componentIds = extractIds(first, "CMP");
            if (currentScreenId != null && !componentIds.isEmpty() && cells.size() >= 4) {
                String screenId = currentScreenId;
                for (String componentId : componentIds) {
                    ComponentModel component = components.computeIfAbsent(componentKey(screenId, componentId), key -> new ComponentModel(componentId, screenId));
                    component.visibleText = cells.get(1);
                    component.interaction = cells.get(2);
                    component.relationText = cells.get(3);
                    component.name = deriveComponentName(componentId, component.visibleText);
                    mergeIds(component.features, component.relationText, "FEAT");
                    mergeIds(component.rules, component.relationText, "BR");
                    mergeIds(component.acceptances, component.relationText, "AC");
                    ScreenModel screen = screens.computeIfAbsent(currentScreenId, ScreenModel::new);
                    screen.componentIds.add(componentId);
                    screen.features.addAll(component.features);
                    screen.rules.addAll(component.rules);
                    screen.acceptances.addAll(component.acceptances);
                }
            }
        }
    }

    private void parseAnnotations(Path file,
                                  List<String> warnings,
                                  Map<String, ScreenModel> screens,
                                  Map<String, ComponentModel> components,
                                  List<AnnotationNodeDto> annotations,
                                  List<FlowNodeDto> flows) {
        // PANN rows connect visual layers back to FEAT/BR/AC, so they enrich both page and component nodes.
        List<String> lines = markdownFileService.readUtf8Lines(file, warnings);
        String mode = "";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## 1.")) {
                mode = "page";
                continue;
            }
            if (trimmed.startsWith("## 2.")) {
                mode = "component";
                continue;
            }
            if (trimmed.startsWith("## 3.")) {
                mode = "flow";
                continue;
            }
            List<String> cells = parseTableRow(line);
            if (cells.isEmpty() || isSeparatorRow(cells)) {
                continue;
            }
            if ("page".equals(mode) && cells.size() >= 5 && cells.get(0).matches("PANN-\\d{3}")) {
                String pannoId = cells.get(0);
                String screenId = cells.get(1);
                String related = cells.get(4);
                List<String> featureIds = extractIds(related, "FEAT");
                List<String> ruleIds = extractIds(related, "BR");
                List<String> acIds = extractIds(related, "AC");
                annotations.add(new AnnotationNodeDto(pannoId, screenId, null, cells.get(2), cells.get(3), featureIds, ruleIds, acIds));
                ScreenModel screen = screens.computeIfAbsent(screenId, ScreenModel::new);
                screen.frameName = blankTo(screen.frameName, cells.get(2));
                screen.goal = blankTo(screen.goal, cells.get(3));
                screen.features.addAll(featureIds);
                screen.rules.addAll(ruleIds);
                screen.acceptances.addAll(acIds);
            }
            if ("component".equals(mode) && cells.size() >= 8 && cells.get(0).matches("PANN-\\d{3}")) {
                String pannoId = cells.get(0);
                String screenId = cells.get(1);
                List<String> componentIds = extractIds(cells.get(2), "CMP");
                List<String> featureIds = extractIds(cells.get(4), "FEAT");
                List<String> ruleIds = extractIds(cells.get(5), "BR");
                List<String> acIds = extractIds(cells.get(6), "AC");
                for (String componentId : componentIds) {
                    annotations.add(new AnnotationNodeDto(pannoId, screenId, componentId, cells.get(3), cells.get(7), featureIds, ruleIds, acIds));
                    ComponentModel component = components.computeIfAbsent(componentKey(screenId, componentId), key -> new ComponentModel(componentId, screenId));
                    component.name = blankTo(component.name, cells.get(3));
                    component.annotationIds.add(pannoId);
                    component.features.addAll(featureIds);
                    component.rules.addAll(ruleIds);
                    component.acceptances.addAll(acIds);
                    ScreenModel screen = screens.computeIfAbsent(screenId, ScreenModel::new);
                    screen.componentIds.add(componentId);
                    screen.features.addAll(featureIds);
                    screen.rules.addAll(ruleIds);
                    screen.acceptances.addAll(acIds);
                }
            }
            if ("flow".equals(mode) && cells.size() >= 4 && cells.get(0).matches("PFLOW-\\d{3}")) {
                flows.add(new FlowNodeDto(cells.get(0), cells.get(1), cells.get(2), extractIds(cells.get(3), "AC")));
            }
        }
    }

    private void parseFeatureSpec(Path file,
                                  List<String> warnings,
                                  Map<String, FeatureModel> features,
                                  Map<String, RuleModel> rules,
                                  Map<String, AcceptanceModel> acceptances) {
        // product-spec/06 is the implementation-facing source for feature goals, flows, rules, and acceptance criteria.
        List<String> lines = markdownFileService.readUtf8Lines(file, warnings);
        FeatureModel current = null;
        SectionModel currentSection = null;
        for (String line : lines) {
            String trimmed = line.trim();
            Matcher featureHeading = FEATURE_HEADING.matcher(trimmed);
            if (featureHeading.find()) {
                current = features.computeIfAbsent(featureHeading.group(1), FeatureModel::new);
                current.name = featureHeading.group(2).trim();
                currentSection = null;
                continue;
            }
            if (current == null) {
                continue;
            }

            Matcher sectionHeading = FEATURE_SECTION_HEADING.matcher(trimmed);
            if (sectionHeading.find()) {
                currentSection = current.section(sectionHeading.group(1), sectionHeading.group(2).trim());
                continue;
            }

            if (currentSection != null) {
                currentSection.lines.add(line);
            }
            if (current.excerpt.length() < 900 && !trimmed.isBlank() && !trimmed.startsWith("|---")) {
                current.excerpt.append(trimmed).append('\n');
            }
            mergeIds(current.screens, line, "SCR");

            List<String> cells = parseTableRow(line);
            if (cells.isEmpty() || isSeparatorRow(cells)) {
                continue;
            }
            String first = cells.get(0);
            if (first.matches("BR-\\d{3}") && cells.size() >= 7) {
                RuleModel rule = new RuleModel(first);
                rule.featureId = current.featureId;
                rule.type = cells.get(1);
                rule.trigger = cells.get(2);
                rule.rule = cells.get(3);
                rule.result = cells.get(4);
                rule.display = cells.get(5);
                rule.testFocus = cells.get(6);
                rules.put(first, rule);
            }
            if (first.matches("AC-\\d{3}") && cells.size() >= 6) {
                AcceptanceModel acceptance = new AcceptanceModel(first);
                acceptance.featureId = current.featureId;
                acceptance.point = cells.get(1);
                acceptance.type = cells.get(2);
                acceptance.given = cells.get(3);
                acceptance.when = cells.get(4);
                acceptance.thenText = cells.get(5);
                acceptances.put(first, acceptance);
            }
        }
    }

    private static List<String> parseTableRow(String line) {
        // The skill emits simple markdown tables; this parser intentionally keeps to that narrow contract.
        String trimmed = line.trim();
        if (!trimmed.startsWith("|")) {
            return List.of();
        }
        String[] parts = trimmed.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (int i = 1; i < parts.length - 1; i++) {
            cells.add(parts[i].trim());
        }
        return cells;
    }

    private static boolean isSeparatorRow(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static List<TableDto> parseTables(List<String> lines) {
        List<TableDto> tables = new ArrayList<>();
        String pendingTitle = "";
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("### ")) {
                pendingTitle = trimmed.substring(4).trim();
                continue;
            }
            List<String> headers = parseTableRow(trimmed);
            if (headers.isEmpty() || index + 1 >= lines.size()) {
                continue;
            }
            List<String> separator = parseTableRow(lines.get(index + 1));
            if (!isSeparatorRow(separator)) {
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            index += 2;
            while (index < lines.size()) {
                List<String> row = parseTableRow(lines.get(index));
                if (row.isEmpty()) {
                    break;
                }
                if (!isSeparatorRow(row)) {
                    rows.add(row);
                }
                index++;
            }
            tables.add(new TableDto(pendingTitle, headers, rows));
            pendingTitle = "";
        }
        return tables;
    }

    private static List<KeyValueDto> keyValueRows(SectionModel section) {
        if (section == null) {
            return List.of();
        }
        return parseTables(section.lines).stream()
                .flatMap(table -> table.rows().stream())
                .filter(row -> row.size() >= 2)
                .map(row -> new KeyValueDto(row.get(0), row.get(1)))
                .toList();
    }

    private static List<String> bullets(SectionModel section) {
        if (section == null) {
            return List.of();
        }
        return section.lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("-"))
                .map(line -> line.substring(1).trim())
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String textContent(SectionModel section) {
        if (section == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean inCodeBlock = false;
        for (String line : section.lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock || (!trimmed.isBlank() && !trimmed.startsWith("|") && !trimmed.startsWith("###"))) {
                builder.append(trimmed).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static List<MainFlowStepDto> mainFlow(SectionModel section) {
        if (section == null) {
            return List.of();
        }
        return parseTables(section.lines).stream()
                .flatMap(table -> table.rows().stream())
                .filter(row -> row.size() >= 5)
                .map(row -> new MainFlowStepDto(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4)))
                .toList();
    }

    private static List<BranchFlowDto> branchFlows(SectionModel section) {
        if (section == null) {
            return List.of();
        }
        return parseTables(section.lines).stream()
                .flatMap(table -> table.rows().stream())
                .filter(row -> row.size() >= 5)
                .map(row -> new BranchFlowDto(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4)))
                .toList();
    }

    private static List<ExceptionFlowDto> exceptionFlows(SectionModel section) {
        if (section == null) {
            return List.of();
        }
        return parseTables(section.lines).stream()
                .flatMap(table -> table.rows().stream())
                .filter(row -> row.size() >= 6)
                .map(row -> new ExceptionFlowDto(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4), row.get(5)))
                .toList();
    }

    private static List<FeatureSectionDto> sectionDtos(Map<String, SectionModel> sections) {
        return sections.values().stream()
                .map(section -> new FeatureSectionDto(
                        section.sectionNo,
                        section.title,
                        textContent(section),
                        bullets(section),
                        parseTables(section.lines)
                ))
                .toList();
    }

    private static String componentKey(String screenId, String componentId) {
        return screenId + "::" + componentId;
    }

    private static String deriveComponentName(String componentId, String visibleText) {
        if (visibleText == null || visibleText.isBlank()) {
            return componentId;
        }
        String cleaned = visibleText.replace("`", "").trim();
        if (cleaned.length() > 36) {
            cleaned = cleaned.substring(0, 36) + "...";
        }
        return componentId + " " + cleaned;
    }

    private static List<String> extractIds(String text, String prefix) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = ANY_ID.matcher(text);
        while (matcher.find()) {
            String id = matcher.group();
            if (id.startsWith(prefix + "-")) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static void mergeIds(Set<String> target, String text, String prefix) {
        target.addAll(extractIds(text, prefix));
    }

    private static String appendText(String base, String next) {
        if (next == null || next.isBlank()) {
            return base;
        }
        if (base == null || base.isBlank()) {
            return next;
        }
        return base + "\n" + next;
    }

    private static String blankTo(String current, String fallback) {
        return current == null || current.isBlank() ? fallback : current;
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static class ScreenModel {
        private final String screenId;
        private String frameName = "";
        private String title = "";
        private String goal = "";
        private String entry = "";
        private String exit = "";
        private final Set<String> features = new LinkedHashSet<>();
        private final Set<String> rules = new LinkedHashSet<>();
        private final Set<String> acceptances = new LinkedHashSet<>();
        private final Set<String> componentIds = new LinkedHashSet<>();

        private ScreenModel(String screenId) {
            this.screenId = screenId;
        }

        private ScreenNodeDto toDto() {
            return new ScreenNodeDto(screenId, frameName, title, goal, entry, exit,
                    sorted(features), sorted(rules), sorted(acceptances), sorted(componentIds));
        }
    }

    private static class ComponentModel {
        private final String componentId;
        private final String screenId;
        private String name = "";
        private String visibleText = "";
        private String interaction = "";
        private String relationText = "";
        private final Set<String> features = new LinkedHashSet<>();
        private final Set<String> rules = new LinkedHashSet<>();
        private final Set<String> acceptances = new LinkedHashSet<>();
        private final Set<String> annotationIds = new LinkedHashSet<>();

        private ComponentModel(String componentId, String screenId) {
            this.componentId = componentId;
            this.screenId = screenId;
        }

        private ComponentNodeDto toDto() {
            return new ComponentNodeDto(componentId, screenId, name, visibleText, interaction, relationText,
                    sorted(features), sorted(rules), sorted(acceptances), sorted(annotationIds));
        }
    }

    private static class FeatureModel {
        private final String featureId;
        private String name = "";
        private final Set<String> screens = new LinkedHashSet<>();
        private final StringBuilder excerpt = new StringBuilder();
        private final Map<String, SectionModel> sections = new LinkedHashMap<>();

        private FeatureModel(String featureId) {
            this.featureId = featureId;
        }

        private SectionModel section(String sectionNo, String title) {
            return sections.computeIfAbsent(sectionNo, key -> new SectionModel(sectionNo, title));
        }

        private FeatureNodeDto toDto() {
            SectionModel goalSection = sections.get("2.2");
            return new FeatureNodeDto(
                    featureId,
                    name,
                    String.join("\n", bullets(goalSection)),
                    String.join(" / ", sorted(screens)),
                    "product-spec/06-feature-task-spec.md",
                    excerpt.toString().trim(),
                    keyValueRows(sections.get("2.1")),
                    bullets(goalSection),
                    textContent(sections.get("2.3")),
                    mainFlow(sections.get("2.6")),
                    branchFlows(sections.get("2.7")),
                    exceptionFlows(sections.get("2.8")),
                    sectionDtos(sections)
            );
        }
    }

    private static class SectionModel {
        private final String sectionNo;
        private final String title;
        private final List<String> lines = new ArrayList<>();

        private SectionModel(String sectionNo, String title) {
            this.sectionNo = sectionNo;
            this.title = title;
        }
    }

    private static class RuleModel {
        private final String ruleId;
        private String featureId = "";
        private String type = "";
        private String trigger = "";
        private String rule = "";
        private String result = "";
        private String display = "";
        private String testFocus = "";

        private RuleModel(String ruleId) {
            this.ruleId = ruleId;
        }

        private RuleNodeDto toDto() {
            return new RuleNodeDto(ruleId, featureId, type, trigger, rule, result, display, testFocus,
                    "product-spec/06-feature-task-spec.md");
        }
    }

    private static class AcceptanceModel {
        private final String acId;
        private String featureId = "";
        private String point = "";
        private String type = "";
        private String given = "";
        private String when = "";
        private String thenText = "";

        private AcceptanceModel(String acId) {
            this.acId = acId;
        }

        private AcceptanceNodeDto toDto() {
            return new AcceptanceNodeDto(acId, featureId, point, type, given, when, thenText,
                    "product-spec/06-feature-task-spec.md");
        }
    }
}
