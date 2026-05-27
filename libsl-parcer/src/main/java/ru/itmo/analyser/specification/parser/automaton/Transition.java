package ru.itmo.analyser.specification.parser.automaton;


import ru.itmo.analyser.specification.parser.model.ast.Requirement;
import ru.itmo.analyser.specification.parser.model.ast.Statement;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A single transition in the automaton, consisting of source and target
 * states, a trigger name, a list of guard conditions, and a list of
 * actions to execute upon firing.
 *
 * @param fromState the source state
 * @param toState   the target state
 * @param trigger   the event name that activates this transition
 * @param guards    the guard conditions that must all hold
 * @param actions   the statements executed when the transition fires
 */
public record Transition(
        String fromState,
        String toState,
        String trigger,
        List<Requirement> guards,
        List<Statement> actions) {

    /**
     * Returns a human-readable representation of this transition,
     * including guards and actions if present.
     *
     * @return a formatted string
     */
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
