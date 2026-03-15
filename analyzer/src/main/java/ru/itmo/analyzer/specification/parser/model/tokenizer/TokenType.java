package ru.itmo.analyzer.specification.parser.model.tokenizer;

// ── Типы токенов ───────────────────────────────────────────────
public enum TokenType {
    // Ключевые слова
    LIBSL, LIBRARY, VERSION, TYPES, AUTOMATON,
    INITSTATE, STATE, VAR, SHIFT, FUN, REQUIRES,
    TRUE, FALSE,
    // Литералы / идентификаторы
    STRING_LITERAL, IDENTIFIER, INT_LITERAL,
    // Символы
    LBRACE, RBRACE, LPAREN, RPAREN,
    SEMICOLON, COLON, EQUALS, ARROW, COMMA,
    EXCLAMATION,
    // Конец ввода
    EOF
}
