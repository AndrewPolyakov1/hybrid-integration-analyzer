package ru.itmo.analyzer.specification.parser.automaton;

import org.junit.jupiter.api.Test;
import ru.itmo.analyser.specification.parser.automaton.FiniteAutomaton;
import ru.itmo.analyser.specification.parser.automaton.FireResult;
import ru.itmo.analyser.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Expression;
import ru.itmo.analyser.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Requirement;
import ru.itmo.analyser.specification.parser.model.ast.ShiftDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.StateDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Statement;
import ru.itmo.analyser.specification.parser.model.ast.VariableDeclaration;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiniteAutomatonTest {

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private AutomatonDeclaration simpleAutomaton() {
        return new AutomatonDeclaration(
                "A",
                "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(
                        new VariableDeclaration("x", "Int", new Expression.IntLiteral(0))
                ),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("go"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "go",
                                List.of(),
                                List.of(),
                                List.of()
                        )
                )
        );
    }

    // ─────────────────────────────────────────────
    // fromDeclaration
    // ─────────────────────────────────────────────

    @Test
    void shouldCreateAutomatonSuccessfully() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        assertEquals("A", fa.getName());
        assertEquals("S1", fa.getInitialState());
        assertEquals("S1", fa.getCurrentState());
        assertEquals(2, fa.getStates().size());
        assertEquals(1, fa.getTransitions().size());
        assertEquals(0, fa.getVariables().get("x"));
    }

    @Test
    void shouldFailIfNoInitialState() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(new StateDeclaration("S1", false)),
                List.of(),
                List.of(),
                List.of()
        );

        assertThrows(IllegalArgumentException.class,
                () -> FiniteAutomaton.fromDeclaration(decl));
    }

    @Test
    void shouldFailIfMultipleInitialStates() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", true)
                ),
                List.of(),
                List.of(),
                List.of()
        );

        assertThrows(IllegalArgumentException.class,
                () -> FiniteAutomaton.fromDeclaration(decl));
    }

    @Test
    void shouldFailOnNonConstantInitializer() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(new StateDeclaration("S1", true)),
                List.of(
                        new VariableDeclaration("x", "Int",
                                new Expression.VarRef("y"))
                ),
                List.of(),
                List.of()
        );

        assertThrows(IllegalArgumentException.class,
                () -> FiniteAutomaton.fromDeclaration(decl));
    }

    // ─────────────────────────────────────────────
    // Transitions
    // ─────────────────────────────────────────────

    @Test
    void shouldFireTransitionSuccessfully() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        var result = fa.tryFire("go", null);

        assertInstanceOf(FireResult.Success.class, result);
        assertEquals("S2", fa.getCurrentState());
    }

    @Test
    void shouldReturnNoTransition() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        var result = fa.tryFire("unknown", null);

        assertInstanceOf(FireResult.NoTransition.class, result);
        assertEquals("S1", fa.getCurrentState());
    }

    @Test
    void shouldThrowOnFireWhenNoTransition() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        assertThrows(IllegalStateException.class,
                () -> fa.fire("unknown"));
    }

    // ─────────────────────────────────────────────
    // Guards
    // ─────────────────────────────────────────────

    @Test
    void shouldFailGuard() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("go"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "go",
                                List.of(),
                                List.of(
                                        new Requirement("check",
                                                new Expression.BoolLiteral(false))
                                ),
                                List.of()
                        )
                )
        );

        var fa = FiniteAutomaton.fromDeclaration(decl);

        var result = fa.tryFire("go", null);

        assertInstanceOf(FireResult.GuardFailed.class, result);
        assertEquals("S1", fa.getCurrentState());
    }

    @Test
    void shouldPassGuard() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("go"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "go",
                                List.of(),
                                List.of(
                                        new Requirement("check",
                                                new Expression.BoolLiteral(true))
                                ),
                                List.of()
                        )
                )
        );

        var fa = FiniteAutomaton.fromDeclaration(decl);

        assertTrue(fa.canFire("go"));

        fa.fire("go");

        assertEquals("S2", fa.getCurrentState());
    }

    // ─────────────────────────────────────────────
    // Variables & actions
    // ─────────────────────────────────────────────

    @Test
    void shouldUpdateVariableOnTransition() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(
                        new VariableDeclaration("x", "Int",
                                new Expression.IntLiteral(0))
                ),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("go"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "go",
                                List.of(),
                                List.of(),
                                List.of(
                                        new Statement.Assignment(
                                                "x",
                                                new Expression.IntLiteral(42)
                                        )
                                )
                        )
                )
        );

        var fa = FiniteAutomaton.fromDeclaration(decl);

        fa.fire("go");

        assertEquals(42, fa.getVariables().get("x"));
    }

    // ─────────────────────────────────────────────
    // Reset
    // ─────────────────────────────────────────────

    @Test
    void shouldResetStateAndVariables() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        fa.fire("go");
        assertEquals("S2", fa.getCurrentState());

        fa.reset();

        assertEquals("S1", fa.getCurrentState());
        assertEquals(0, fa.getVariables().get("x"));
    }

    // ─────────────────────────────────────────────
    // Available triggers
    // ─────────────────────────────────────────────

    @Test
    void shouldReturnAvailableTriggers() {
        var fa = FiniteAutomaton.fromDeclaration(simpleAutomaton());

        var triggers = fa.getAvailableTriggers();

        assertEquals(List.of("go"), triggers);
    }

    @Test
    void shouldRespectGuardsInAvailableTriggers() {
        var decl = new AutomatonDeclaration(
                "A", "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("go"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "go",
                                List.of(),
                                List.of(
                                        new Requirement("fail",
                                                new Expression.BoolLiteral(false))
                                ),
                                List.of()
                        )
                )
        );

        var fa = FiniteAutomaton.fromDeclaration(decl);

        assertTrue(fa.getAvailableTriggers().isEmpty());
        assertFalse(fa.canFire("go"));
    }
}