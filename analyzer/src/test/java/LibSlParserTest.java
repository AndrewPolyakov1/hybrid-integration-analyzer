import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.itmo.analyzer.specification.parser.Lexer;
import ru.itmo.analyzer.specification.parser.LibSlParser;
import ru.itmo.analyzer.specification.parser.model.FiniteAutomaton;

public class LibSlParserTest {

    private final String input = """
            libsl "1.0.0";
            library File version "1.0.0";
            types {
                File (custom.lib.File);
                String (string)
            }
            
            automaton custom.lib.File : File {
                initstate Unknown;
                state Created;
                state Opened;
                state Closed;
            
                var exists: bool = false;
            
                shift Unknown -> Created(create);
                shift Created -> Opened(open);
                shift Opened -> Closed(close);
            
                fun create()
                    requires isNotExists: !exists
                {
                    exists = true;
                }
            
                fun open();
                fun close();
            }
            """;

    private static void printStatus(FiniteAutomaton fa) {
        System.out.println("  state     = " + fa.getCurrentState());
        System.out.println("  variables = " + fa.getVariables());
        System.out.println("  available = " + fa.getAvailableTriggers());
    }

    @Test
    @DisplayName("Простая проверка")
    public void testLexer() {
        var lexer = new Lexer(input);
        var tokens = lexer.tokenize();

        var parser = new LibSlParser(tokens);
        var spec = parser.parse();


        System.out.println("LibSL version : " + spec.libslVersion());
        System.out.println("Library       : " + spec.libraryName()
                + " v" + spec.libraryVersion());
        System.out.println("Types         : " + spec.types());
        System.out.println("Automata count: " + spec.automata().size());


        // ── 3. Построение конечного автомата ────────────────────
        for (var decl : spec.automata()) {
            System.out.println("\n═══ КОНЕЧНЫЙ АВТОМАТ ═══");
            FiniteAutomaton fa = FiniteAutomaton.fromDeclaration(decl);
            System.out.println(fa);

            // ── 4. Симуляция ───────────────────────────────────
            System.out.println("═══ СИМУЛЯЦИЯ ═══");
            printStatus(fa);

            System.out.println("\n▶ fire(\"create\"):");
            fa.fire("create");
            printStatus(fa);

            System.out.println("\n▶ fire(\"open\"):");
            fa.fire("open");
            printStatus(fa);

            System.out.println("\n▶ fire(\"close\"):");
            fa.fire("close");
            printStatus(fa);

            // ── 5. Проверка guard'а ────────────────────────────
            System.out.println("\n═══ ПРОВЕРКА GUARD ═══");
            fa.reset();
            fa.fire("create");
            System.out.println("  exists = " + fa.getVariables().get("exists"));
            System.out.println("  canFire(\"create\") = " + fa.canFire("create")
                    + "  (автомат уже в Created → нет перехода)");

            fa.reset();
            System.out.println("\n  After reset:");
            printStatus(fa);
        }
    }
}
