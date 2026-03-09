package ru.itmo.analyzer.trace.model;

/**
 * Информация о классе
 *
 * @param name имя класса
 */
public record ClassInfo(String name) {
    public boolean isNull() {
        return name == null || name.isBlank();
    }
}

