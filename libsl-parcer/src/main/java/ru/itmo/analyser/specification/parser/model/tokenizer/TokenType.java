package ru.itmo.analyser.specification.parser.model.tokenizer;

/**
 * Token types recognized by the LibSL specification lexer.
 *
 * <p>Tokens are grouped into four categories:
 * <ul>
 *   <li><b>Keywords</b> — reserved words of the LibSL language
 *       ({@link #LIBSL}, {@link #LIBRARY}, {@link #AUTOMATON}, etc.).</li>
 *   <li><b>Literals and identifiers</b> — user-defined names and constant
 *       values ({@link #IDENTIFIER}, {@link #STRING_LITERAL},
 *       {@link #INT_LITERAL}).</li>
 *   <li><b>Symbols</b> — punctuation and operators
 *       ({@link #LBRACE}, {@link #ARROW}, {@link #EQUALS}, etc.).</li>
 *   <li><b>Synthetic</b> — the {@link #EOF} token marking the end of
 *       input.</li>
 * </ul>
 *
 * @see Token
 */
public enum TokenType {
    /**
     * Keyword {@code libsl}.
     */
    LIBSL,
    /**
     * Keyword {@code library}.
     */
    LIBRARY,
    /**
     * Keyword {@code version}.
     */
    VERSION,
    /**
     * Keyword {@code types}.
     */
    TYPES,
    /**
     * Keyword {@code automaton}.
     */
    AUTOMATON,
    /**
     * Keyword {@code initstate}.
     */
    INITSTATE,
    /**
     * Keyword {@code state}.
     */
    STATE,
    /**
     * Keyword {@code var}.
     */
    VAR,
    /**
     * Keyword {@code shift}.
     */
    SHIFT,
    /**
     * Keyword {@code fun}.
     */
    FUN,
    /**
     * Keyword {@code requires}.
     */
    REQUIRES,
    /**
     * Boolean literal {@code true}.
     */
    TRUE,
    /**
     * Boolean literal {@code false}.
     */
    FALSE,
    /**
     * A double-quoted string literal.
     */
    STRING_LITERAL,
    /**
     * An unquoted identifier.
     */
    IDENTIFIER,
    /**
     * An integer literal.
     */
    INT_LITERAL,
    /**
     * Left curly brace {@code &#123;}.
     */
    LBRACE,
    /**
     * Right curly brace {@code &#125;}.
     */
    RBRACE,
    /**
     * Left parenthesis {@code (}.
     */
    LPAREN,
    /**
     * Right parenthesis {@code )}.
     */
    RPAREN,
    /**
     * Semicolon {@code ;}.
     */
    SEMICOLON,
    /**
     * Colon {@code :}.
     */
    COLON,
    /**
     * Equals sign {@code =}.
     */
    EQUALS,
    /**
     * Arrow {@code ->}.
     */
    ARROW,
    /**
     * Comma {@code ,}.
     */
    COMMA,
    /**
     * Exclamation mark {@code !}.
     */
    EXCLAMATION,
    /**
     * Synthetic token indicating the end of input.
     */
    EOF
}