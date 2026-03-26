package ru.itmo.analyzer.specification.parser.automaton;

import java.util.List;

/**
 * The result of a {@link #tryFire(String)} attempt.
 *
 * <p>This is a sealed interface with three permitted implementations:
 * <ul>
 *   <li>{@link Success} — the transition was executed successfully.</li>
 *   <li>{@link NoTransition} — no transition matched the trigger in the
 *       current state.</li>
 *   <li>{@link GuardFailed} — a matching transition was found but its
 *       guard condition evaluated to {@code false}.</li>
 * </ul>
 */
public sealed interface FireResult {

    /**
     * Indicates that a transition was successfully fired.
     *
     * @param fromState the state before the transition
     * @param toState   the state after the transition
     * @param trigger   the trigger that caused the transition
     */
    record Success(String fromState, String toState, String trigger)
            implements FireResult {
    }

    /**
     * Indicates that no transition exists for the given trigger
     * in the current state.
     *
     * @param currentState      the state at the time of the attempt
     * @param trigger           the trigger that was attempted
     * @param availableTriggers the triggers currently available
     */
    record NoTransition(String currentState, String trigger,
                        List<String> availableTriggers)
            implements FireResult {
    }

    /**
     * Indicates that a matching transition was found but its guard
     * condition was not satisfied.
     *
     * @param currentState the state at the time of the attempt
     * @param trigger      the trigger that was attempted
     * @param guardName    the name of the guard that failed
     */
    record GuardFailed(String currentState, String trigger,
                       String guardName)
            implements FireResult {
    }
}
