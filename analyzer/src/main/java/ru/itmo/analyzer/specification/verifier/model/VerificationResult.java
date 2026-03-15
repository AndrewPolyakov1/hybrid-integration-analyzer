package ru.itmo.analyzer.specification.verifier.model;

import ru.itmo.analyzer.trace.model.TraceEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Результат верификации трейса по конечному автомату.
 */
public record VerificationResult(
        String automatonName,
        List<Violation> violations,
        Map<String, String> finalStates,          // instanceId → финальное состояние
        int totalEvents,
        int relevantEvents,
        int successfulTransitions
) {
    public boolean isValid() {
        return violations.isEmpty();
    }

    // ── Красивый отчёт ─────────────────────────────────────────
    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("╔════════════════════════════════════════════════════╗\n");
        sb.append("║           VERIFICATION REPORT                     ║\n");
        sb.append("╠════════════════════════════════════════════════════╣\n");
        sb.append("║  Automaton:     %-35s║\n".formatted(automatonName));
        sb.append("║  Total events:  %-35d║\n".formatted(totalEvents));
        sb.append("║  Relevant:      %-35d║\n".formatted(relevantEvents));
        sb.append("║  Transitions:   %-35d║\n".formatted(successfulTransitions));
        sb.append("║  Violations:    %-35d║\n".formatted(violations.size()));
        sb.append("║  Result:        %-35s║\n".formatted(
                isValid() ? "✅ PASS" : "❌ FAIL"));
        sb.append("╠════════════════════════════════════════════════════╣\n");

        if (!finalStates.isEmpty()) {
            sb.append("║  Final states:                                    ║\n");
            finalStates.forEach((id, state) ->
                    sb.append("║    %-20s → %-24s║\n".formatted(id, state)));
            sb.append("╠════════════════════════════════════════════════════╣\n");
        }

        if (!violations.isEmpty()) {
            sb.append("║  VIOLATIONS:                                      ║\n");
            sb.append("╠════════════════════════════════════════════════════╣\n");
            for (var v : violations) {
                sb.append(v.toString().indent(2));
            }
        }

        sb.append("╚════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    // ── Группировка нарушений ──────────────────────────────────
    public Map<String, List<Violation>> violationsByInstance() {
        return violations.stream()
                .collect(Collectors.groupingBy(Violation::instanceId));
    }

    public Map<Violation.ViolationType, List<Violation>> violationsByType() {
        return violations.stream()
                .collect(Collectors.groupingBy(Violation::type));
    }

    // ── Нарушение ──────────────────────────────────────────────
    public record Violation(
            int eventIndex,
            TraceEvent event,
            String instanceId,
            String currentState,
            String attemptedFunction,
            List<String> availableFunctions,
            ViolationType type,
            String detail,
            Integer lineNum
    ) {
        @Override
        public String toString() {
            return """
                    ⚠ VIOLATION #%d [%s]
                      Event:    %s.%s() at %s (thread: %s)
                      Instance: %s
                      Line number: %s
                      State:    %s
                      Trigger:  %s
                      Detail:   %s
                      Available triggers: %s\
                    """.formatted(
                    eventIndex, type,
                    event.classInfo().name(), event.method().name(),
                    event.timestamp(), event.thread().name(),
                    instanceId,
                    lineNum,
                    currentState,
                    attemptedFunction,
                    detail,
                    availableFunctions
            );
        }

        public enum ViolationType {
            /**
             * Нет перехода из текущего состояния по данному триггеру.
             */
            NO_TRANSITION,
            /**
             * Переход существует, но guard-условие не выполнено.
             */
            GUARD_FAILED
        }
    }
}