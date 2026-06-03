package ru.itmo.analyzer.runner;

import picocli.CommandLine;
import freemarker.template.TemplateException;
import ru.itmo.analyser.specification.parser.LibSlParser;
import ru.itmo.analyser.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyser.specification.parser.model.ast.Specification;
import ru.itmo.analyzer.mapper.MethodMapping;
import ru.itmo.analyzer.report.HtmlReportGenerator;
import ru.itmo.analyzer.trace.model.TraceEvent;
import ru.itmo.analyzer.trace.parser.TraceEventParser;
import ru.itmo.analyzer.verifier.TraceVerifier;
import ru.itmo.analyzer.verifier.model.VerificationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Основной класс программы.
 * <p>
 * Usage: Main <trace-file> [--spec <spec-file>]
 */
@CommandLine.Command(name = "analyzer", mixinStandardHelpOptions = true,
		description = "Verifies a trace against a LibSL automaton")
public class Main implements java.util.concurrent.Callable<Integer> {
	private static final Logger logger = Logger.getLogger(Main.class.getName());

	@CommandLine.Parameters(index = "0", description = "Path to trace file (JSONL)")
	private Path tracePath;

	@CommandLine.Option(names = {"--spec"}, description = "Path to LibSL specification file",
			defaultValue = "spec.libsl")
	private Path specPath;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new Main()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() {
		try {
			if (tracePath == null) {
				System.err.println("Trace file is required");
				return 1;
			}

			String specText = Files.readString(specPath);
			logger.info("Loaded specification from " + specPath.toAbsolutePath());

			// Parse specification
			LibSlParser specParser = new LibSlParser(specText);
			Specification specification = specParser.parse();
			if (specification.automata().isEmpty()) {
				System.err.println("Specification contains no automata");
				return 2;
			}
			AutomatonDeclaration automatonDecl = specification.automata().get(0);

			// Parse trace
			TraceEventParser traceEventParser = new TraceEventParser();
			List<TraceEvent> events = traceEventParser.parse(tracePath.toFile());
			logger.info("Parsed " + events.size() + " trace events");

			// Build mapping from spec (methods mapped by name)
			MethodMapping mapping = MethodMapping.builder()
					.fromSpec(specification)
					.build();

			// Verify
			TraceVerifier verifier = new TraceVerifier(automatonDecl, mapping);
			VerificationResult result = verifier.verify(events);

			System.out.println(result);

			// Generate HTML report
			HtmlReportGenerator generator = new HtmlReportGenerator();
			Path out = Path.of("build", "reports", "verification.html");
			generator.generateToFile(result, out);
			logger.info("Report written to " + out.toAbsolutePath());

			return result.isValid() ? 0 : 2;
		} catch (IOException e) {
			logger.severe("IO error: " + e.getMessage());
			return 3;
		} catch (TemplateException e) {
			logger.severe("Template error: " + e.getMessage());
			return 3;
		}
	}
}
