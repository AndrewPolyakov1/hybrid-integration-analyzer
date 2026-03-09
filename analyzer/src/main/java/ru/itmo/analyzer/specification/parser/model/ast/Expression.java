package ru.itmo.analyzer.specification.parser.model.ast;

/**
 * Выражения
 *
 * @author andrew
 */
public sealed interface Expression permits Expression.BoolLiteral, Expression.IntLiteral, Expression.Not,
        Expression.StringLiteral, Expression.VarRef {
    record BoolLiteral(boolean value) implements Expression {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record IntLiteral(int value) implements Expression {
        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record StringLiteral(String value) implements Expression {
        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    record VarRef(String name) implements Expression {
        @Override
        public String toString() {
            return name;
        }
    }

    record Not(Expression operand) implements Expression {
        @Override
        public String toString() {
            return "!" + operand;
        }
    }
}
