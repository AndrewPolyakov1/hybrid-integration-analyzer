package ru.itmo.analyser.specification.parser.model.ast;

/**
 * Требование к переходу
 *
 * @param name      название требования
 * @param condition условие выполнения
 * @author andrew
 */
public record Requirement(String name, Expression condition) {
}
