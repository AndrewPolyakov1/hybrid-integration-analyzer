package ru.itmo.analyzer.mapper;



import ru.itmo.analyser.specification.parser.model.ast.Specification;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps traced methods to automaton function names.
 *
 * <p>Supports both explicit method mappings and class-level mappings where
 * method names are used directly as function names.
 *
 * <p>Example usage:
 * <pre>{@code
 * MethodMapping mapping = MethodMapping.builder()
 *     .map("com.company.FileUtils", "openFile", "open")
 *     .map("com.company.FileUtils", "closeFile", "close")
 *     .build();
 * }</pre>
 */
public final class MethodMapping {

    private final Map<MethodKey, String> explicit;
    private final Map<String, String> classMappings;

    private MethodMapping(Map<MethodKey, String> explicit,
                          Map<String, String> classMappings) {
        this.explicit = Map.copyOf(explicit);
        this.classMappings = Map.copyOf(classMappings);
    }

    /**
     * Returns a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves a function name by class and method.
     *
     * @param className  fully qualified class name
     * @param methodName method name
     * @return the corresponding function name, or an empty {@code Optional}
     * if no mapping is defined
     */
    public Optional<String> resolve(String className, String methodName) {
        String fn = explicit.get(new MethodKey(className, methodName));
        if (fn != null) {
            return Optional.of(fn);
        }
        if (classMappings.containsKey(className)) {
            return Optional.of(methodName);
        }
        return Optional.empty();
    }

    /**
     * Returns all classes that are tracked by this mapping.
     */
    public Set<String> getTrackedClasses() {
        Set<String> result = new HashSet<>();
        explicit.keySet().forEach(k -> result.add(k.className()));
        result.addAll(classMappings.keySet());
        return Collections.unmodifiableSet(result);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MethodMapping{\n");
        explicit.forEach((k, v) ->
                sb.append("  ").append(k).append(" -> ").append(v).append('\n'));
        classMappings.forEach((cls, v) ->
                sb.append("  ").append(cls).append(" -> [direct]\n"));
        sb.append('}');
        return sb.toString();
    }

    /**
     * A mapping key consisting of a class name and a method name.
     */
    public record MethodKey(String className, String methodName) {
        @Override
        public String toString() {
            return className + "#" + methodName;
        }
    }

    /**
     * Result of a mapping resolution.
     */
    public record Resolution(String functionName) {
    }

    /**
     * Builder for {@link MethodMapping}.
     */
    public static final class Builder {
        private final Map<MethodKey, String> explicit = new LinkedHashMap<>();
        private final Map<String, String> classMappings = new LinkedHashMap<>();

        /**
         * Adds an explicit mapping from a method to a function.
         *
         * @param className    fully qualified class name
         * @param methodName   method name
         * @param functionName target function name
         * @return this builder
         */
        public Builder map(String className, String methodName, String functionName) {
            explicit.put(new MethodKey(className, methodName), functionName);
            return this;
        }

        public Builder fromSpec(Specification specification) {
            for (var automaton : specification.automata()) {
                String classname = automaton.name();
                for (var method : automaton.functions()) {
                    this.map(classname, method.name(), method.name());
                }
            }
            return this;
        }

        /**
         * Registers a class for direct mapping where method names are used
         * as function names.
         *
         * @param className fully qualified class name
         * @return this builder
         */
        public Builder mapClassDirect(String className) {
            classMappings.put(className, "");
            return this;
        }

        /**
         * Adds mappings for multiple methods where each method name is used
         * as its function name.
         *
         * @param className     fully qualified class name
         * @param functionNames method/function names
         * @return this builder
         */
        public Builder mapMethods(String className, List<String> functionNames) {
            for (String name : functionNames) {
                explicit.put(new MethodKey(className, name), name);
            }
            return this;
        }

        /**
         * Builds a {@link MethodMapping} instance.
         */
        public MethodMapping build() {
            return new MethodMapping(explicit, classMappings);
        }
    }
}