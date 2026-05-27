package ru.itmo.analyser.specification.parser.model.ast;

/**
 * Определение состояния
 *
 * @param name      название
 * @param isInitial начальное ли
 */
public record StateDeclaration(String name, boolean isInitial) {
}
