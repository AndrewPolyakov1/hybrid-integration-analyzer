package filter;

import ru.itmo.analyser.specification.parser.LibSlParser;
import ru.itmo.analyser.specification.parser.model.ast.Specification;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Фильтр методов по спецификации
 *
 * @author andrew
 */
public class MethodFilter {
    private static final Logger LOGGER = Logger.getLogger(MethodFilter.class.getName());

    private final Specification specification;

    private MethodFilter() {
        String filename = System.getProperty("libsl.spec");
        if (filename == null || filename.isEmpty()) {
            filename = "spec.libsl";
        }
        File spec = new File(filename);
        try {
            LOGGER.info("Loading specification from " + spec.getAbsolutePath());
            LibSlParser parser = new LibSlParser(Files.readString(spec.toPath()));
            LOGGER.info("Parsing specification");
            specification = parser.parse();
            LOGGER.info("Specification parsed. Automaton: " + specification.automata().getFirst().name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class InstanceHolder {
        private static final MethodFilter INSTANCE = new MethodFilter();
    }

    public static MethodFilter getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public boolean accepts(String methodName) {
        return specification.automata().stream().anyMatch(automaton ->
                automaton.functions().stream().anyMatch(func -> {
                    String fqName = automaton.name() + "." + func.name();
                    return fqName.equals(methodName);
                })
        );
    }
}
