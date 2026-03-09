package ru.itmo.analyzer.specification.parser.model.ast;

/**
 * Определение переменной
 *
 * @param name         название переменной
 * @param type         тип переменной
 * @param initialValue начальное значение
 */
public record VariableDeclaration(String name, String type, Expression initialValue) {
}
