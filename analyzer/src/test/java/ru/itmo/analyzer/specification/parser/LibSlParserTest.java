package ru.itmo.analyzer.specification.parser;

import org.junit.jupiter.api.Test;
import ru.itmo.analyser.specification.parser.LibSlParser;
import ru.itmo.analyser.specification.parser.error.ParseException;
import ru.itmo.analyser.specification.parser.model.ast.Specification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibSlParserTest {

    @Test
    void shouldParseMinimalSpecification() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                """;

        LibSlParser parser = new LibSlParser(src);
        Specification spec = parser.parse();

        assertEquals("1.0", spec.libslVersion());
        assertEquals("Test", spec.libraryName());
        assertEquals("1.0", spec.libraryVersion());
        assertTrue(spec.automata().isEmpty());
    }

    @Test
    void shouldParseTypesBlock() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                types {
                    A(B);
                    C(D);
                }
                """;

        Specification spec = new LibSlParser(src).parse();

        assertEquals(2, spec.types().size());
        assertEquals("A", spec.types().get(0).name());
        assertEquals("B", spec.types().get(0).implementation());
    }

    @Test
    void shouldParseAutomatonWithStates() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    initstate S1;
                    state S2;
                }
                """;

        Specification spec = new LibSlParser(src).parse();

        assertEquals(1, spec.automata().size());
        var automaton = spec.automata().get(0);

        assertEquals("A", automaton.name());
        assertEquals(2, automaton.states().size());
        assertTrue(automaton.states().get(0).isInitial());
    }

    @Test
    void shouldParseVariableDeclaration() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    var x: Int = 5;
                }
                """;

        var spec = new LibSlParser(src).parse();
        var varDecl = spec.automata().get(0).variables().get(0);

        assertEquals("x", varDecl.name());
        assertEquals("Int", varDecl.type());
    }

    @Test
    void shouldParseShift() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    shift S1 -> S2(f1, f2);
                }
                """;

        var spec = new LibSlParser(src).parse();
        var shift = spec.automata().get(0).shifts().get(0);

        assertEquals("S1", shift.fromState());
        assertEquals("S2", shift.toState());
        assertEquals(2, shift.functions().size());
    }

    @Test
    void shouldParseFunctionWithBody() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    fun f(a: Int) {
                        x = 5;
                    }
                }
                """;

        var spec = new LibSlParser(src).parse();
        var func = spec.automata().get(0).functions().get(0);

        assertEquals("f", func.name());
        assertEquals(1, func.parameters().size());
        assertEquals(1, func.body().size());
    }

    @Test
    void shouldParseFunctionWithRequires() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    fun f(a: Int)
                        requires cond: true;
                }
                """;

        var spec = new LibSlParser(src).parse();
        var func = spec.automata().get(0).functions().get(0);

        assertEquals(1, func.requirements().size());
    }

    @Test
    void shouldParseBooleanExpressions() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    var x: Bool = !true;
                }
                """;

        assertDoesNotThrow(() -> new LibSlParser(src).parse());
    }

    @Test
    void shouldThrowOnInvalidSyntax() {
        String src = """
                libsl "1.0"
                library Test version "1.0";
                """;

        assertThrows(ParseException.class, () -> new LibSlParser(src).parse());
    }

    @Test
    void shouldThrowOnUnexpectedTokenInAutomaton() {
        String src = """
                libsl "1.0";
                library Test version "1.0";
                
                automaton A : T {
                    unknown stuff;
                }
                """;

        assertThrows(ParseException.class, () -> new LibSlParser(src).parse());
    }
}