package ru.itmo.analyzer.report;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import ru.itmo.analyzer.specification.verifier.model.VerificationResult;
import ru.itmo.analyzer.specification.verifier.model.VerificationResult.Violation;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Генерирует HTML-отчёт по результатам верификации трейса.
 * <p>
 * Использует FreeMarker-шаблон {@code templates/report/verification-report.ftl}.
 *
 * <pre>{@code
 *   var generator = new HtmlReportGenerator();
 *   generator.generateToFile(result, Path.of("report.html"));
 * }</pre>
 */
public class HtmlReportGenerator {

    private static final String TEMPLATE_DIR = "/templates";
    private static final String TEMPLATE_NAME = "verification-report.ftl";

    private final Configuration freemarker;

    public HtmlReportGenerator() {
        freemarker = new Configuration();
        freemarker.setClassForTemplateLoading(this.getClass(), TEMPLATE_DIR);
//        freemarker.setTemplateLoader(new ClassTemplateLoader(HtmlReportGenerator.class));
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(
                TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /* ── public API ──────────────────────────────────────────── */

    private static String fmt(double value) {
        return String.format("%.1f", value);
    }

    /**
     * Генерирует HTML-строку отчёта.
     */
    public String generate(VerificationResult result)
            throws IOException, TemplateException {

        Template template = freemarker.getTemplate(TEMPLATE_NAME);
        var model = buildModel(result);
        var writer = new StringWriter(8_192);

        template.process(model, writer);
        return writer.toString();
    }

    /* ── model builder ───────────────────────────────────────── */

    /**
     * Генерирует отчёт и сохраняет в файл.
     */
    public void generateToFile(VerificationResult result, Path output)
            throws IOException, TemplateException {

        String html = generate(result);
        Files.createDirectories(output.getParent());
        Files.writeString(output, html);
    }

    private Map<String, Object> buildModel(VerificationResult result) {

        Map<String, Object> model = new LinkedHashMap<>();

        // ── скаляры ────────────────────────────────────────────
        model.put("automatonName", result.automatonName());
        model.put("totalEvents", result.totalEvents());
        model.put("relevantEvents", result.relevantEvents());
        model.put("irrelevantEvents",
                result.totalEvents() - result.relevantEvents());
        model.put("successfulTransitions", result.successfulTransitions());
        model.put("violationCount", result.violations().size());
        model.put("isValid", result.isValid());
        model.put("generatedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.put("generatedAtShort", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // ── процентные метрики ─────────────────────────────────
        double relevantPct = result.totalEvents() > 0
                ? 100.0 * result.relevantEvents() / result.totalEvents()
                : 0;
        double successPct = result.relevantEvents() > 0
                ? 100.0 * result.successfulTransitions() / result.relevantEvents()
                : 0;
        model.put("relevantPercent", fmt(relevantPct));
        model.put("transitionSuccessRate", fmt(successPct));

        // ── финальные состояния ────────────────────────────────
        List<Map<String, String>> finalStates = new ArrayList<>();
        result.finalStates().forEach((id, state) ->
                finalStates.add(Map.of("instanceId", id, "state", state)));
        model.put("finalStates", finalStates);

        // ── нарушения (плоский список) ─────────────────────────
        List<Map<String, Object>> violations =
                result.violations().stream()
                        .map(this::violationToMap)
                        .toList();
        model.put("violations", violations);

        // ── группировки ────────────────────────────────────────
        model.put("violationsByInstance",
                violations.stream().collect(
                        Collectors.groupingBy(v -> (String) v.get("instanceId"),
                                LinkedHashMap::new, Collectors.toList())));

        model.put("violationsByType",
                violations.stream().collect(
                        Collectors.groupingBy(v -> (String) v.get("type"),
                                LinkedHashMap::new, Collectors.toList())));

        return model;
    }

    private Map<String, Object> violationToMap(Violation v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventIndex", v.eventIndex());
        m.put("type", v.type().name());
        m.put("className", v.event().classInfo().name());
        m.put("methodName", v.event().method().name());
        m.put("timestamp", Objects.toString(v.event().timestamp()));
        m.put("threadName", v.event().thread().name());
        m.put("instanceId", v.instanceId());
        m.put("callerClass", v.event().callerClass().name());
        m.put("callerMethod", v.event().callerMethod().name());
        m.put("lineNum", v.lineNum() != null
                ? v.lineNum().toString() : "N/A");
        m.put("currentState", v.currentState());
        m.put("attemptedFunction", v.attemptedFunction());
        m.put("detail", v.detail());
        m.put("availableFunctions", v.availableFunctions());
        return m;
    }
}