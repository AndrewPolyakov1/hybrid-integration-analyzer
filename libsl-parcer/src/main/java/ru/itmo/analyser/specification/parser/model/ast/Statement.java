package ru.itmo.analyser.specification.parser.model.ast;

/**
 * Определение инструкции
 *
 * @author andrew
 */
public sealed interface Statement {
    record Assignment(String variable, Expression value) implements Statement {
        @Override
        public String toString() {
            return variable + " = " + value;
        }
    }
}
