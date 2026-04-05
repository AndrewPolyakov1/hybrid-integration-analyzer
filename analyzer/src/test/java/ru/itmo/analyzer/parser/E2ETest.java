package ru.itmo.analyzer.parser;

import freemarker.template.TemplateException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.itmo.analyzer.mapper.MethodMapping;
import ru.itmo.analyzer.report.HtmlReportGenerator;
import ru.itmo.analyzer.specification.parser.Lexer;
import ru.itmo.analyzer.specification.parser.LibSlParser;
import ru.itmo.analyzer.specification.parser.automaton.FiniteAutomaton;
import ru.itmo.analyzer.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Specification;
import ru.itmo.analyzer.specification.verifier.model.TraceVerifier;
import ru.itmo.analyzer.specification.verifier.model.VerificationResult;
import ru.itmo.analyzer.trace.model.TraceEvent;
import ru.itmo.analyzer.trace.parser.TraceEventParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class E2ETest {
    private static final Logger logger = Logger.getLogger(E2ETest.class.getName());
    // ═════════════════════════════════════════════════════════
    //  1. СПЕЦИФИКАЦИЯ LibSL
    // ═════════════════════════════════════════════════════════
    private final String libslSpec = """
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
                shift Unknown -> Opened(open);
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

    private VerificationResult res;

    @Test
    public void testFullPipeline() throws TemplateException, IOException {
        System.out.println("═══ 1. PARSING LibSL SPEC ═══\n");

        var parser = new LibSlParser(libslSpec);
        var spec = parser.parse();

        AutomatonDeclaration automatonDecl = spec.automata().getFirst();
        FiniteAutomaton reference = FiniteAutomaton.fromDeclaration(automatonDecl);
        System.out.println(reference);

        // ═════════════════════════════════════════════════════════
        //  4. ПАРСИНГ ТРЕЙСА
        // ═════════════════════════════════════════════════════════
        System.out.println("═══ 2. PARSING TRACE ═══\n");
        File source = new File("src/test/resources/agent-method-calls.jsonl");
        List<TraceEvent> events = List.of();
        TraceEventParser traceEventParser = new TraceEventParser();
        try {
            var result = traceEventParser.parse(source);
            events = result;
            logger.info(String.valueOf(result));
        } catch (IOException e) {
            fail(e.getMessage());
        }
        System.out.println("Parsed " + events.size() + " trace events:");
        events.forEach(e -> System.out.println("  " + e));

        // ═════════════════════════════════════════════════════════
        //  5. НАСТРОЙКА МАППИНГА
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 3. METHOD MAPPING ═══\n");

        // Сценарий A: openFile → open, closeFile → close
        // (create не вызывается в трейсе → нарушение!)
        var mappingA = MethodMapping.builder()
                .map("com.company.contest.FileUtils", "openFile", "open")
                .map("com.company.contest.FileUtils", "closeFile", "close")
                .build();

        System.out.println("Mapping A (без create):");
        System.out.println(mappingA);

        // ═════════════════════════════════════════════════════════
        //  6. ВЕРИФИКАЦИЯ — СЦЕНАРИЙ A (ожидаем нарушение)
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 4. VERIFICATION — SCENARIO A ═══");
        System.out.println("   (open/close без предварительного create)\n");

        var verifierA = new TraceVerifier(automatonDecl, mappingA);
        VerificationResult resultA = verifierA.verify(events);
        System.out.println(resultA);

        var generator = new HtmlReportGenerator();
        generator.generateToFile(resultA, Path.of("build", "reports", "verification.html"));

        // ═════════════════════════════════════════════════════════
        //  7. ВЕРИФИКАЦИЯ — СЦЕНАРИЙ B (корректный трейс)
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 5. VERIFICATION — SCENARIO B ═══");
        System.out.println("   (полный корректный трейс: create → open → close)\n");
    }

    @Test
    public void testFullPipelineFail() throws TemplateException, IOException {
        System.out.println("═══ 1. PARSING LibSL SPEC ═══\n");

        var lexer = new Lexer(libslSpec);
        var tokens = lexer.tokenize();
        var parser = new LibSlParser(tokens);
        var spec = parser.parse();

        AutomatonDeclaration automatonDecl = spec.automata().getFirst();
        FiniteAutomaton reference = FiniteAutomaton.fromDeclaration(automatonDecl);
        System.out.println(reference);

        // ═════════════════════════════════════════════════════════
        //  4. ПАРСИНГ ТРЕЙСА
        // ═════════════════════════════════════════════════════════
        System.out.println("═══ 2. PARSING TRACE ═══\n");
        File source = new File("src/test/resources/agent-method-calls-bad.jsonl");
        List<TraceEvent> events = List.of();
        TraceEventParser traceEventParser = new TraceEventParser();
        try {
            var result = traceEventParser.parse(source);
            events = result;
            logger.info(String.valueOf(result));
        } catch (IOException e) {
            fail(e.getMessage());
        }
        System.out.println("Parsed " + events.size() + " trace events:");
        events.forEach(e -> System.out.println("  " + e));

        // ═════════════════════════════════════════════════════════
        //  5. НАСТРОЙКА МАППИНГА
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 3. METHOD MAPPING ═══\n");

        // Сценарий A: openFile → open, closeFile → close
        // (create не вызывается в трейсе → нарушение!)
        var mappingA = MethodMapping.builder()
                .map("com.company.contest.FileUtils", "openFile", "open")
                .map("com.company.contest.FileUtils", "closeFile", "close")
                .build();

        System.out.println("Mapping A (без create):");
        System.out.println(mappingA);

        // ═════════════════════════════════════════════════════════
        //  6. ВЕРИФИКАЦИЯ — СЦЕНАРИЙ A (ожидаем нарушение)
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 4. VERIFICATION — SCENARIO A ═══");
        System.out.println("   (open/close без предварительного create)\n");

        var verifierA = new TraceVerifier(automatonDecl, mappingA);
        VerificationResult resultA = verifierA.verify(events);
        System.out.println(resultA);

        var generator = new HtmlReportGenerator();
        generator.generateToFile(resultA, Path.of("build", "reports", "verification.html"));

        // ═════════════════════════════════════════════════════════
        //  7. ВЕРИФИКАЦИЯ — СЦЕНАРИЙ B (корректный трейс)
        // ═════════════════════════════════════════════════════════
        System.out.println("\n═══ 5. VERIFICATION — SCENARIO B ═══");
        System.out.println("   (полный корректный трейс: create → open → close)\n");
    }
}
