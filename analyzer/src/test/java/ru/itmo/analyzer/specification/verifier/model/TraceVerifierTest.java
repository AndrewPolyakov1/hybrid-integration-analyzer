package ru.itmo.analyzer.specification.verifier.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itmo.analyser.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Expression;
import ru.itmo.analyser.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Requirement;
import ru.itmo.analyser.specification.parser.model.ast.ShiftDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.StateDeclaration;
import ru.itmo.analyzer.mapper.MethodMapping;
import ru.itmo.analyzer.trace.model.ClassInfo;
import ru.itmo.analyzer.trace.model.MethodInfo;
import ru.itmo.analyzer.trace.model.ThreadInfo;
import ru.itmo.analyzer.trace.model.TraceEvent;
import ru.itmo.analyzer.verifier.TraceVerifier;
import ru.itmo.analyzer.verifier.model.VerificationResult;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceVerifierTest {

    private MethodMapping mapping;
    private AutomatonDeclaration declaration;

    @BeforeEach
    void setup() {
        mapping = mock(MethodMapping.class);

        declaration = new AutomatonDeclaration(
                "A",
                "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("foo"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "foo",
                                List.of(),
                                List.of(),
                                List.of()
                        )
                )
        );
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private TraceEvent event(
            boolean isEnter,
            String className,
            String methodName,
            String instanceClass,
            String thread
    ) {
        TraceEvent e = mock(TraceEvent.class);

        var classInfo = mock(ClassInfo.class);
        when(classInfo.name()).thenReturn(className);

        var method = mock(MethodInfo.class);
        when(method.name()).thenReturn(methodName);

        var instance = mock(ClassInfo.class);
        when(instance.name()).thenReturn(instanceClass);
        when(instance.isNull()).thenReturn(instanceClass == null);

        var threadObj = mock(ThreadInfo.class);
        when(threadObj.name()).thenReturn(thread);

        when(e.isEnter()).thenReturn(isEnter);
        when(e.classInfo()).thenReturn(classInfo);
        when(e.method()).thenReturn(method);
        when(e.instanceClass()).thenReturn(instance);
        when(e.thread()).thenReturn(threadObj);
        when(e.lineNumber()).thenReturn(42);

        return e;
    }

    // ─────────────────────────────────────────────
    // Basic flow
    // ─────────────────────────────────────────────

    @Test
    void shouldIgnoreNonEnterEvents() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        var event = event(false, "A", "m", "X", "t");

        verifier.processEvent(event);

        assertEquals(1, verifier.getResult().totalEvents());
        assertEquals(0, verifier.getResult().relevantEvents());
    }

    @Test
    void shouldIgnoreUnmappedMethods() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve(any(), any())).thenReturn(Optional.empty());

        var event = event(true, "A", "m", "X", "t");

        verifier.processEvent(event);

        assertTrue(verifier.getViolations().isEmpty());
    }

    @Test
    void shouldPerformSuccessfulTransition() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve("A", "m")).thenReturn(Optional.of("foo"));

        var event = event(true, "A", "m", "X", "t");

        verifier.processEvent(event);

        var result = verifier.getResult();

        assertEquals(1, result.successfulTransitions());
        assertTrue(result.violations().isEmpty());
    }

    // ─────────────────────────────────────────────
    // Violations
    // ─────────────────────────────────────────────

    @Test
    void shouldRecordNoTransitionViolation() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve("A", "m")).thenReturn(Optional.of("unknown"));

        var event = event(true, "A", "m", "X", "t");

        verifier.processEvent(event);

        var violations = verifier.getViolations();

        assertEquals(1, violations.size());
        assertEquals(VerificationResult.Violation.ViolationType.NO_TRANSITION,
                violations.getFirst().type());
    }

    @Test
    void shouldRecordGuardFailedViolation() {
        var decl = new AutomatonDeclaration(
                "A",
                "T",
                List.of(
                        new StateDeclaration("S1", true),
                        new StateDeclaration("S2", false)
                ),
                List.of(),
                List.of(
                        new ShiftDeclaration("S1", "S2", List.of("foo"))
                ),
                List.of(
                        new FunctionDeclaration(
                                "foo",
                                List.of(),
                                List.of(
                                        new Requirement("check",
                                                new Expression.BoolLiteral(false))
                                ),
                                List.of()
                        )
                )
        );

        TraceVerifier verifier = new TraceVerifier(decl, mapping);

        when(mapping.resolve("A", "m")).thenReturn(Optional.of("foo"));

        var event = event(true, "A", "m", "X", "t");

        verifier.processEvent(event);

        var violations = verifier.getViolations();

        assertEquals(1, violations.size());
        assertEquals(VerificationResult.Violation.ViolationType.GUARD_FAILED,
                violations.getFirst().type());
    }

    // ─────────────────────────────────────────────
    // Instance separation
    // ─────────────────────────────────────────────

    @Test
    void shouldCreateSeparateInstancesPerClass() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve(any(), any())).thenReturn(Optional.of("foo"));

        verifier.processEvent(event(true, "A", "m", "X1", "t"));
        verifier.processEvent(event(true, "A", "m", "X2", "t"));

        assertEquals(2, verifier.getInstances().size());
    }

    @Test
    void shouldUseThreadStrategy() {
        TraceVerifier verifier = new TraceVerifier(
                declaration,
                mapping,
                TraceVerifier.InstanceKeyStrategy.BY_THREAD
        );

        when(mapping.resolve(any(), any())).thenReturn(Optional.of("foo"));

        verifier.processEvent(event(true, "A", "m", "X", "t1"));
        verifier.processEvent(event(true, "A", "m", "X", "t2"));

        assertEquals(2, verifier.getInstances().size());
    }

    // ─────────────────────────────────────────────
    // verify / processAll
    // ─────────────────────────────────────────────

    @Test
    void shouldProcessAllEvents() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve(any(), any())).thenReturn(Optional.of("foo"));

        var events = List.of(
                event(true, "A", "m", "X", "t"),
                event(true, "A", "m", "X", "t")
        );

        verifier.processAll(events);

        assertEquals(2, verifier.getResult().totalEvents());
    }

    @Test
    void shouldVerifyShortcut() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve(any(), any())).thenReturn(Optional.of("foo"));

        var result = verifier.verify(List.of(
                event(true, "A", "m", "X", "t")
        ));

        assertEquals(1, result.successfulTransitions());
    }

    // ─────────────────────────────────────────────
    // Reset
    // ─────────────────────────────────────────────

    @Test
    void shouldResetState() {
        TraceVerifier verifier = new TraceVerifier(declaration, mapping);

        when(mapping.resolve(any(), any())).thenReturn(Optional.of("foo"));

        verifier.processEvent(event(true, "A", "m", "X", "t"));

        verifier.reset();

        assertTrue(verifier.getInstances().isEmpty());
        assertTrue(verifier.getViolations().isEmpty());
        assertEquals(0, verifier.getResult().totalEvents());
    }
}