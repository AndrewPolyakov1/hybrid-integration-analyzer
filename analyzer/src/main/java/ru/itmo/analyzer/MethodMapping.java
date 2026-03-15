package ru.itmo.analyzer;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Конфигурация маппинга между методами из трейса и функциями автомата.
 * <p>
 * Пример:
 * <pre>{@code
 * var mapping = MethodMapping.builder()
 *     .map("com.company.FileUtils", "openFile",  "open")
 *     .map("com.company.FileUtils", "closeFile", "close")
 *     .build();
 * }</pre>
 */
public class MethodMapping {

    private final Map<MethodKey, String> explicit;
    private final Map<String, String> classMappings;   // className → «все методы 1:1»

    private MethodMapping(
            Map<MethodKey, String> explicit,
            Map<String, String> classMappings
    ) {
        this.explicit = Map.copyOf(explicit);
        this.classMappings = Map.copyOf(classMappings);
    }

    // ═══════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Разрешить имя функции автомата по имени класса и метода из трейса.
     *
     * @return имя функции автомата, или {@code Optional.empty()} если метод не отслеживается
     */
    public Optional<String> resolve(String className, String methodName) {
        // 1. Сначала проверяем точное совпадение
        String fn = explicit.get(new MethodKey(className, methodName));
        if (fn != null) return Optional.of(fn);

        // 2. Если класс зарегистрирован для 1:1 маппинга — метод = функция
        if (classMappings.containsKey(className))
            return Optional.of(methodName);

        return Optional.empty();
    }

    /**
     * Все зарегистрированные классы, за которыми ведётся наблюдение.
     */
    public Set<String> getTrackedClasses() {
        var classes = new HashSet<String>();
        explicit.keySet().forEach(k -> classes.add(k.className()));
        classes.addAll(classMappings.keySet());
        return Collections.unmodifiableSet(classes);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("MethodMapping {\n");
        explicit.forEach((k, v) ->
                sb.append("  ").append(k).append(" → ").append(v).append('\n'));
        classMappings.forEach((cls, aut) ->
                sb.append("  ").append(cls).append(" → [1:1 match]\n"));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Ключ маппинга: полное имя класса + имя метода.
     */
    public record MethodKey(String className, String methodName) {
        @Override
        public String toString() {
            return className + "#" + methodName;
        }
    }

    /**
     * Результат разрешения маппинга.
     */
    public record Resolution(String functionName) {
    }

    public static class Builder {
        private final Map<MethodKey, String> explicit = new LinkedHashMap<>();
        private final Map<String, String> classMapping = new LinkedHashMap<>();

        /**
         * Явно отобразить метод класса на функцию автомата.
         *
         * @param className    полное имя класса в трейсе
         * @param methodName   имя метода в трейсе
         * @param functionName имя функции в автомате
         */
        public Builder map(String className, String methodName, String functionName) {
            explicit.put(new MethodKey(className, methodName), functionName);
            return this;
        }

        /**
         * Зарегистрировать класс для 1:1 маппинга: имя метода = имя функции.
         * <p>
         * Используется когда имена методов в трейсе совпадают с именами
         * функций в автомате.
         */
        public Builder mapClassDirect(String className) {
            classMapping.put(className, "");
            return this;
        }

        /**
         * Пакетная регистрация: все указанные функции автомата маппятся
         * как одноимённые методы указанного класса.
         */
        public Builder mapMethods(
                String className,
                List<String> functionNames
        ) {
            for (String fn : functionNames)
                explicit.put(new MethodKey(className, fn), fn);
            return this;
        }

        public MethodMapping build() {
            return new MethodMapping(explicit, classMapping);
        }
    }
}