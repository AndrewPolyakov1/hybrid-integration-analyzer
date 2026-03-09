package ru.itmo.analyzer.specification.parser.model.ast;

/**
 * Объявление типа
 *
 * @param name           название
 * @param implementation реализация
 */
public record TypeDeclaration(String name, String implementation) {
}
