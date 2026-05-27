package ru.itmo.analyser.specification.parser.automaton;

import ru.itmo.analyser.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Expression;
import ru.itmo.analyser.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Requirement;
import ru.itmo.analyser.specification.parser.model.ast.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An Extended Finite State Machine (EFSM) constructed from a LibSL specification.
 *
 * <p>This class represents a stateful automaton with named states, guarded transitions,
 * and internal variables. A {@code FiniteAutomaton} is built from an
 * {@link AutomatonDeclaration} via the {@link #fromDeclaration(AutomatonDeclaration)}
 * factory method.
 *
 * <p>The automaton maintains a current state and a set of variables that can be
 * mutated by transition actions. Transitions are triggered by named events and
 * may be guarded by boolean conditions over the automaton's variables.
 *
 * <p>Instances of this class are <b>not</b> thread-safe.
 *
 * @see AutomatonDeclaration
 * @see Transition
 * @see FireResult
 */
public class FiniteAutomaton {

    private final String name;
    private final Set<String> states;
    private final String initialState;
    private final List<Transition> transitions;
    private final Map<String, String> variableTypes;
    private final Map<String, Object> initialValues;
    private final Map<String, Object> variables;
    private String currentState;

    private FiniteAutomaton(
            String name,
            Set<String> states,
            String initialState,
            List<Transition> transitions,
            Map<String, String> variableTypes,
            Map<String, Object> initialValues) {
        this.name = name;
        this.states = states;
        this.initialState = initialState;
        this.transitions = transitions;
        this.variableTypes = variableTypes;
        this.initialValues = initialValues;
        this.variables = new LinkedHashMap<>(initialValues);
        this.currentState = initialState;
    }

    /**
     * Creates a {@code FiniteAutomaton} from the given AST declaration.
     *
     * <p>The declaration must contain exactly one initial state. All declared
     * variables must have constant initializers (boolean, integer, or string
     * literals).
     *
     * @param decl the automaton declaration to convert
     * @return a new {@code FiniteAutomaton} ready for simulation
     * @throws IllegalArgumentException if the declaration contains multiple
     *                                  initial states, no initial state, or a non-constant variable
     *                                  initializer
     * @throws NullPointerException     if {@code decl} is {@code null}
     */
    public static FiniteAutomaton fromDeclaration(AutomatonDeclaration decl) {
        Objects.requireNonNull(decl, "decl");

        var stateSet = new LinkedHashSet<String>();
        String init = null;
        for (var sd : decl.states()) {
            stateSet.add(sd.name());
            if (sd.isInitial()) {
                if (init != null) {
                    throw new IllegalArgumentException(
                            "Multiple initial states: '%s' and '%s'"
                                    .formatted(init, sd.name()));
                }
                init = sd.name();
            }
        }
        if (init == null) {
            throw new IllegalArgumentException(
                    "No initial state in automaton " + decl.name());
        }

        var varTypes = new LinkedHashMap<String, String>();
        var varValues = new LinkedHashMap<String, Object>();
        for (var vd : decl.variables()) {
            varTypes.put(vd.name(), vd.type());
            varValues.put(vd.name(), evalConstant(vd.initialValue()));
        }

        var funcMap = new LinkedHashMap<String, FunctionDeclaration>();
        for (var fd : decl.functions()) {
            funcMap.put(fd.name(), fd);
        }

        var trans = new ArrayList<Transition>();
        for (var shift : decl.shifts()) {
            for (String funcName : shift.functions()) {
                var func = funcMap.get(funcName);
                trans.add(new Transition(
                        shift.fromState(),
                        shift.toState(),
                        funcName,
                        func != null ? func.requirements() : List.of(),
                        func != null ? func.body() : List.of()));
            }
        }

        return new FiniteAutomaton(
                decl.name(), stateSet, init, trans, varTypes, varValues);
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

    /**
     * Attempts to fire a transition for the given trigger from the current state.
     *
     * <p>If a matching transition is found and all its guards are satisfied,
     * the transition's actions are executed and the automaton moves to the
     * target state. Otherwise, the automaton state remains unchanged.
     *
     * @param trigger the name of the triggering event
     * @param context
     * @return a {@link FireResult} describing the outcome
     * @throws NullPointerException if {@code trigger} is {@code null}
     * @see FireResult
     */
    public FireResult tryFire(String trigger, Map<String, Object> context) {
        Objects.requireNonNull(trigger, "Function name is missing");

        var matching = transitions.stream()
                .filter(t -> t.fromState().equals(currentState)
                        && t.trigger().equals(trigger))
                .toList();

        if (matching.isEmpty()) {
            return new FireResult.NoTransition(
                    currentState, trigger, getAvailableTriggers());
        }

        for (var t : matching) {
            String failedGuard = findFailedGuard(t.guards(), context);
            if (failedGuard == null) {
                for (var action : t.actions()) {
                    executeStatement(action, context);
                }
                String prev = currentState;
                currentState = t.toState();
                return new FireResult.Success(prev, t.toState(), trigger);
            }
        }

        String failedGuard = findFailedGuard(matching.getFirst().guards(), context);
        return new FireResult.GuardFailed(
                currentState, trigger,
                failedGuard != null ? failedGuard : "unknown");
    }

    /**
     * Fires a transition for the given trigger, throwing an exception on failure.
     *
     * <p>On success, the transition is logged to {@link System#out}.
     *
     * @param trigger the name of the triggering event
     * @throws IllegalStateException if no matching transition exists or all
     *                               guards fail
     * @throws NullPointerException  if {@code trigger} is {@code null}
     */
    public void fire(String trigger) {
        switch (tryFire(trigger, null)) {
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

    /**
     * Resets this automaton to its initial state and restores all variables
     * to their declared initial values.
     */
    public void reset() {
        currentState = initialState;
        variables.clear();
        variables.putAll(initialValues);
    }

    /**
     * Returns the name of this automaton.
     *
     * @return the automaton name, never {@code null}
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the current state of this automaton.
     *
     * @return the current state name, never {@code null}
     */
    public String getCurrentState() {
        return currentState;
    }

    /**
     * Returns an unmodifiable view of the set of all declared states.
     *
     * @return the state names in declaration order
     */
    public Set<String> getStates() {
        return Collections.unmodifiableSet(states);
    }

    /**
     * Returns the name of the initial state.
     *
     * @return the initial state name, never {@code null}
     */
    public String getInitialState() {
        return initialState;
    }

    /**
     * Returns an unmodifiable view of all transitions.
     *
     * @return the transitions in declaration order
     */
    public List<Transition> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    /**
     * Returns an unmodifiable view of variable-name-to-type mappings.
     *
     * @return the variable type map
     */
    public Map<String, String> getVariableTypes() {
        return Collections.unmodifiableMap(variableTypes);
    }

    /**
     * Returns an unmodifiable snapshot of the current variable values.
     *
     * @return the variable value map
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Returns {@code true} if the given trigger can fire from the current
     * state without actually executing the transition.
     *
     * @param trigger the name of the triggering event
     * @return {@code true} if at least one matching, guard-passing transition exists
     * @throws NullPointerException if {@code trigger} is {@code null}
     */
    public boolean canFire(String trigger) {
        Objects.requireNonNull(trigger, "trigger");
        return transitions.stream()
                .anyMatch(t -> t.fromState().equals(currentState)
                        && t.trigger().equals(trigger));
    }

    /**
     * Returns the list of triggers that can currently fire (i.e., have at
     * least one matching transition from the current state whose guards pass).
     *
     * @return an unmodifiable list of distinct trigger names
     */
    public List<String> getAvailableTriggers() {
        return transitions.stream()
                .filter(t -> t.fromState().equals(currentState))
                .map(Transition::trigger)
                .distinct()
                .toList();
    }

    /**
     * Returns a human-readable summary of this automaton, including its
     * states, current state, variables, and transitions.
     *
     * @return a multi-line string representation
     */
    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Automaton '").append(name).append("'\n");
        sb.append("  States:   ").append(states).append('\n');
        sb.append("  Initial:  ").append(initialState).append('\n');
        sb.append("  Current:  ").append(currentState).append('\n');
        sb.append("  Vars:     ").append(variables).append('\n');
        sb.append("  Transitions:\n");
        for (var t : transitions) {
            sb.append("    ").append(t).append('\n');
        }
        return sb.toString();
    }

    private String findFailedGuard(List<Requirement> guards, Map<String, Object> context) {
        for (var guard : guards) {
            Object result = evalExpression(guard.condition(), context);
            if (result instanceof Boolean b && !b) {
                return guard.name();
            }
        }
        return null;
    }

    private Object evalExpression(Expression expr, Map<String, Object> context) {
        return switch (expr) {
            case Expression.BoolLiteral(var v) -> v;
            case Expression.IntLiteral(var v) -> v;
            case Expression.StringLiteral(var v) -> v;
            case Expression.VarRef(var n) -> {
                if (context.containsKey(n)) {
                    yield context.get(n);
                }
                yield variables.get(n);
            }
            case Expression.Not(var operand) -> !(Boolean) evalExpression(operand, context);
        };
    }

    private void executeStatement(Statement stmt, Map<String, Object> stringObjectMap) {
        if (stmt instanceof Statement.Assignment(var variable, var value)) {
            variables.put(variable, evalExpression(value, stringObjectMap));
        }
    }

}