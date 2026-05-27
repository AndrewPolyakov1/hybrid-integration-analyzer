package ru.itmo.analyzer.verifier;

import ru.itmo.analyser.specification.parser.automaton.FiniteAutomaton;
import ru.itmo.analyser.specification.parser.automaton.FireResult;
import ru.itmo.analyser.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyzer.mapper.MethodMapping;
import ru.itmo.analyzer.trace.model.TraceEvent;
import ru.itmo.analyzer.verifier.model.VerificationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Верификатор трейса вызовов по конечному автомату.
 *
 * <p>Для каждого уникального экземпляра объекта (определяемого по
 * {@code instanceClass}) создаётся отдельная копия автомата. При
 * поступлении ENTER-события, соответствующего функции автомата,
 * верификатор пытается выполнить переход. Если переход невозможен —
 * фиксируется нарушение.
 *
 * <h3>Пример использования:</h3>
 * <pre>{@code
 * var verifier = new TraceVerifier(automatonDecl, mapping);
 * for (TraceEvent event : traceEvents) {
 *     verifier.processEvent(event);
 * }
 * VerificationResult result = verifier.getResult();
 * System.out.println(result);
 * }</pre>
 */
public class TraceVerifier {

    // ── Поля ───────────────────────────────────────────────────
    private final AutomatonDeclaration declaration;
    private final MethodMapping mapping;
    private final InstanceKeyStrategy keyStrategy;
    private final Map<String, FiniteAutomaton> instances = new LinkedHashMap<>();
    private final List<VerificationResult.Violation> violations = new ArrayList<>();
    private int totalEvents;
    private int relevantEvents;
    private int successfulTransitions;

    public TraceVerifier(
            AutomatonDeclaration declaration,
            MethodMapping mapping
    ) {
        this(declaration, mapping, InstanceKeyStrategy.BY_INSTANCE_CLASS);
    }

    // ═══════════════════════════════════════════════════════════
    //  Конструкторы
    // ═══════════════════════════════════════════════════════════

    public TraceVerifier(
            AutomatonDeclaration declaration,
            MethodMapping mapping,
            InstanceKeyStrategy keyStrategy
    ) {
        this.declaration = Objects.requireNonNull(declaration);
        this.mapping = Objects.requireNonNull(mapping);
        this.keyStrategy = Objects.requireNonNull(keyStrategy);
    }

    /**
     * Обработать одно событие трейса.
     * <p>
     * Учитываются только ENTER-события, для которых есть маппинг
     * на функцию автомата.
     */
    public void processEvent(TraceEvent event) {
        totalEvents++;

        // Только ENTER-фаза запускает переходы
        if (!event.isEnter()) return;

        // Разрешить маппинг
        String className = event.classInfo().name();
        String methodName = event.method().name();

        Optional<String> functionOpt = mapping.resolve(className, methodName);
        if (functionOpt.isEmpty()) return;   // метод не отслеживается

        String functionName = functionOpt.get();
        relevantEvents++;

        // Получить или создать экземпляр автомата
        String instanceId = computeInstanceKey(event);
        FiniteAutomaton automaton = instances.computeIfAbsent(
                instanceId,
                ignored -> FiniteAutomaton.fromDeclaration(declaration)
        );

        // Попытка перехода
        FireResult result = automaton.tryFire(functionName, null);

        switch (result) {
            case FireResult.Success s -> successfulTransitions++;

            case FireResult.NoTransition nt -> violations.add(new VerificationResult.Violation(
                    totalEvents,
                    event,
                    instanceId,
                    nt.currentState(),
                    functionName,
                    nt.availableTriggers(),
                    VerificationResult.Violation.ViolationType.NO_TRANSITION,
                    "No transition for '%s' from state '%s'"
                            .formatted(functionName, nt.currentState()),
                    event.lineNumber()
            ));

            case FireResult.GuardFailed gf -> violations.add(new VerificationResult.Violation(
                    totalEvents,
                    event,
                    instanceId,
                    gf.currentState(),
                    functionName,
                    List.of(functionName + " (guard blocked)"),
                    VerificationResult.Violation.ViolationType.GUARD_FAILED,
                    "Guard '%s' failed for '%s' in state '%s'"
                            .formatted(gf.guardName(), functionName,
                                    gf.currentState()),
                    event.lineNumber()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Обработка событий
    // ═══════════════════════════════════════════════════════════

    /**
     * Обработать список событий.
     */
    public void processAll(List<TraceEvent> events) {
        events.forEach(this::processEvent);
    }

    /**
     * Потоковая обработка: верификация + возврат результата.
     */
    public VerificationResult verify(List<TraceEvent> events) {
        processAll(events);
        return getResult();
    }

    public VerificationResult getResult() {
        var finalStates = new LinkedHashMap<String, String>();
        instances.forEach((id, automaton) ->
                finalStates.put(id, automaton.getCurrentState()));

        return new VerificationResult(
                declaration.name(),
                List.copyOf(violations),
                Collections.unmodifiableMap(finalStates),
                totalEvents,
                relevantEvents,
                successfulTransitions
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  Результат
    // ═══════════════════════════════════════════════════════════

    /**
     * Текущие нарушения.
     */
    public List<VerificationResult.Violation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    /**
     * Текущие экземпляры автоматов.
     */
    public Map<String, FiniteAutomaton> getInstances() {
        return Collections.unmodifiableMap(instances);
    }

    /**
     * Сброс верификатора.
     */
    public void reset() {
        instances.clear();
        violations.clear();
        totalEvents = 0;
        relevantEvents = 0;
        successfulTransitions = 0;
    }

    private String computeInstanceKey(TraceEvent event) {
        String instanceClass = event.instanceClass().isNull()
                ? "static:" + event.classInfo().name()
                : event.instanceClass().name();

        return switch (keyStrategy) {
            case BY_INSTANCE_CLASS -> instanceClass;
            case BY_THREAD_AND_INSTANCE_CLASS -> event.thread().name() + "::" + instanceClass;
            case BY_THREAD -> event.thread().name();
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  Вычисление ключа экземпляра
    // ═══════════════════════════════════════════════════════════

    // ── Стратегия формирования ключа экземпляра ────────────────
    public enum InstanceKeyStrategy {
        /**
         * Ключ = instanceClass (все экземпляры одного класса — один автомат).
         */
        BY_INSTANCE_CLASS,
        /**
         * Ключ = thread + instanceClass (изоляция по потокам).
         */
        BY_THREAD_AND_INSTANCE_CLASS,
        /**
         * Ключ = thread (один автомат на поток).
         */
        BY_THREAD
    }
}