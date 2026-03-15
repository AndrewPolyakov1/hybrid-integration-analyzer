package ru.itmo.analyzer.specification.parser.model;


import ru.itmo.analyzer.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Expression;
import ru.itmo.analyzer.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Requirement;
import ru.itmo.analyzer.specification.parser.model.ast.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Расширенный конечный автомат (EFSM), построенный из спецификации LibSL.
 */
public class FiniteAutomaton {

    // ── Поля модели ────────────────────────────────────────────
    private final String name;
    private final Set<String> states;
    private final String initialState;
    private final List<Transition> transitions;
    private final Map<String, String> variableTypes;
    private final Map<String, Object> initialValues;
    private final Map<String, Object> variables;
    // ── Состояние симуляции ─────────────────────────────────────
    private String currentState;

    // ═══════════════════════════════════════════════════════════
    //  Конструктор
    // ═══════════════════════════════════════════════════════════
    private FiniteAutomaton(
            String name,
            Set<String> states,
            String initialState,
            List<Transition> transitions,
            Map<String, String> variableTypes,
            Map<String, Object> initialValues
    ) {
        this.name = name;
        this.states = states;
        this.initialState = initialState;
        this.transitions = transitions;
        this.variableTypes = variableTypes;
        this.initialValues = initialValues;
        this.variables = new LinkedHashMap<>(initialValues);
        this.currentState = initialState;
    }

    // ═══════════════════════════════════════════════════════════
    //  Фабричный метод: AST → FiniteAutomaton
    // ═══════════════════════════════════════════════════════════
    public static FiniteAutomaton fromDeclaration(AutomatonDeclaration decl) {
        // 1. Состояния
        var stateSet = new LinkedHashSet<String>();
        String init = null;
        for (var sd : decl.states()) {
            stateSet.add(sd.name());
            if (sd.isInitial()) {
                if (init != null)
                    throw new IllegalArgumentException(
                            "Multiple initial states: '%s' and '%s'"
                                    .formatted(init, sd.name()));
                init = sd.name();
            }
        }
        if (init == null)
            throw new IllegalArgumentException(
                    "No initial state in automaton " + decl.name());

        // 2. Переменные
        var varTypes = new LinkedHashMap<String, String>();
        var varValues = new LinkedHashMap<String, Object>();
        for (var vd : decl.variables()) {
            varTypes.put(vd.name(), vd.type());
            varValues.put(vd.name(), evalConstant(vd.initialValue()));
        }

        // 3. Справочник функций
        var funcMap = new LinkedHashMap<String, FunctionDeclaration>();
        for (var fd : decl.functions())
            funcMap.put(fd.name(), fd);

        // 4. Переходы
        var transitions = new ArrayList<Transition>();
        for (var shift : decl.shifts()) {
            for (String funcName : shift.functions()) {
                var func = funcMap.get(funcName);
                transitions.add(new Transition(
                        shift.fromState(),
                        shift.toState(),
                        funcName,
                        func != null ? func.requirements() : List.of(),
                        func != null ? func.body() : List.of()
                ));
            }
        }

        return new FiniteAutomaton(
                decl.name(), stateSet, init, transitions, varTypes, varValues);
    }

    private static Object evalConstant(Expression expr) {
        return switch (expr) {
            case Expression.BoolLiteral(var v) -> v;
            case Expression.IntLiteral(var v) -> v;
            case Expression.StringLiteral(var v) -> v;
            default -> throw new IllegalArgumentException(
                    "Non-constant initializer: " + expr);
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  tryFire — тихая попытка перехода (для верификатора)
    // ═══════════════════════════════════════════════════════════
    public FireResult tryFire(String trigger) {
        // Найти переходы из текущего состояния по триггеру
        var matching = transitions.stream()
                .filter(t -> t.fromState().equals(currentState)
                        && t.trigger().equals(trigger))
                .toList();

        if (matching.isEmpty()) {
            return new FireResult.NoTransition(
                    currentState, trigger, getAvailableTriggers());
        }

        // Попытка найти переход с выполненными guard'ами
        for (var t : matching) {
            String failedGuard = findFailedGuard(t.guards());
            if (failedGuard == null) {
                // Guard'ы прошли — выполнить действия и сменить состояние
                for (var action : t.actions())
                    executeStatement(action);
                String prev = currentState;
                currentState = t.toState();
                return new FireResult.Success(prev, t.toState(), trigger);
            }
        }

        // Все переходы заблокированы guard'ами
        String failedGuard = findFailedGuard(matching.getFirst().guards());
        return new FireResult.GuardFailed(
                currentState, trigger,
                failedGuard != null ? failedGuard : "unknown");
    }

    // ═══════════════════════════════════════════════════════════
    //  fire — переход с выбросом исключения (обратная совместимость)
    // ═══════════════════════════════════════════════════════════
    public void fire(String trigger) {
        switch (tryFire(trigger)) {
            case FireResult.Success s -> System.out.printf("  [transition] %s --[%s]--> %s%n",
                    s.fromState(), s.trigger(), s.toState());
            case FireResult.NoTransition n -> throw new IllegalStateException(
                    "No transition for '%s' in state '%s'. Available: %s"
                            .formatted(trigger, n.currentState(),
                                    n.availableTriggers()));
            case FireResult.GuardFailed g -> throw new IllegalStateException(
                    "Guard '%s' failed for '%s' in state '%s'"
                            .formatted(g.guardName(), trigger,
                                    g.currentState()));
        }
    }

    public void reset() {
        currentState = initialState;
        variables.clear();
        variables.putAll(initialValues);
    }

    // ═══════════════════════════════════════════════════════════
    //  Запросы состояния
    // ═══════════════════════════════════════════════════════════

    public String getCurrentState() {
        return currentState;
    }

    public String getName() {
        return name;
    }

    public Set<String> getStates() {
        return Collections.unmodifiableSet(states);
    }

    public String getInitialState() {
        return initialState;
    }

    public List<Transition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    public Map<String, String> getVariableTypes() {
        return Collections.unmodifiableMap(variableTypes);
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public boolean canFire(String trigger) {
        return tryFire(trigger) instanceof FireResult.Success;
        // Проблема: tryFire мутирует состояние при успехе!
        // Нужен метод проверки без мутации. См. ниже.
    }

    /**
     * Проверяет возможность перехода БЕЗ его выполнения.
     */
    public boolean canFireWithoutExecuting(String trigger) {
        return transitions.stream()
                .anyMatch(t -> t.fromState().equals(currentState)
                        && t.trigger().equals(trigger)
                        && findFailedGuard(t.guards()) == null);
    }

    public List<String> getAvailableTriggers() {
        return transitions.stream()
                .filter(t -> t.fromState().equals(currentState))
                .filter(t -> findFailedGuard(t.guards()) == null)
                .map(Transition::trigger)
                .distinct()
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    //  toString
    // ═══════════════════════════════════════════════════════════
    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Automaton '%s'\n".formatted(name));
        sb.append("  States:   %s\n".formatted(states));
        sb.append("  Initial:  %s\n".formatted(initialState));
        sb.append("  Current:  %s\n".formatted(currentState));
        sb.append("  Vars:     %s\n".formatted(variables));
        sb.append("  Transitions:\n");
        for (var t : transitions)
            sb.append("    ").append(t).append('\n');
        return sb.toString();
    }

    /**
     * Возвращает имя первого проваленного guard'а, или null если все пройдены.
     */
    private String findFailedGuard(List<Requirement> guards) {
        for (var guard : guards) {
            Object result = evalExpression(guard.condition());
            if (result instanceof Boolean b && !b)
                return guard.name();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  Внутренние методы
    // ═══════════════════════════════════════════════════════════

    private Object evalExpression(Expression expr) {
        return switch (expr) {
            case Expression.BoolLiteral(var v) -> v;
            case Expression.IntLiteral(var v) -> v;
            case Expression.StringLiteral(var v) -> v;
            case Expression.VarRef(var n) -> variables.get(n);
            case Expression.Not(var operand) -> !(Boolean) evalExpression(operand);
        };
    }

    private void executeStatement(Statement stmt) {
        if (stmt instanceof Statement.Assignment(var variable, var value))
            variables.put(variable, evalExpression(value));
    }

    // ── Результат попытки перехода ──────────────────────────────
    public sealed interface FireResult {
        record Success(String fromState, String toState, String trigger)
                implements FireResult {
        }

        record NoTransition(String currentState, String trigger,
                            List<String> availableTriggers)
                implements FireResult {
        }

        record GuardFailed(String currentState, String trigger,
                           String guardName)
                implements FireResult {
        }
    }

    // ── Переход ────────────────────────────────────────────────
    public record Transition(
            String fromState,
            String toState,
            String trigger,
            List<Requirement> guards,
            List<Statement> actions
    ) {
        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append(fromState).append(" --[").append(trigger)
                    .append("]--> ").append(toState);
            if (!guards.isEmpty()) {
                sb.append("  guards: ");
                sb.append(guards.stream()
                        .map(g -> g.name() + ": " + g.condition())
                        .collect(Collectors.joining(", ")));
            }
            if (!actions.isEmpty()) {
                sb.append("  actions: ");
                sb.append(actions.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("; ")));
            }
            return sb.toString();
        }
    }
}