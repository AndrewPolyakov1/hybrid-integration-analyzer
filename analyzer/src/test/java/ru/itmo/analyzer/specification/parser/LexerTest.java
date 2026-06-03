package ru.itmo.analyzer.specification.parser;

import org.junit.jupiter.api.Test;
import ru.itmo.analyser.specification.parser.Lexer;
import ru.itmo.analyser.specification.parser.error.ParseException;
import ru.itmo.analyser.specification.parser.model.tokenizer.Token;
import ru.itmo.analyser.specification.parser.model.tokenizer.TokenType;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LexerTest {

    @Test
    void shouldTokenizeSimpleKeywords() {
        Lexer lexer = new Lexer("libsl \"1.0\";");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.LIBSL, tokens.get(0).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(1).type());
        assertEquals("1.0", tokens.get(1).value());
        assertEquals(TokenType.SEMICOLON, tokens.get(2).type());
        assertEquals(TokenType.EOF, tokens.getLast().type());
    }

    @Test
    void shouldTokenizeIdentifierWithDots() {
        Lexer lexer = new Lexer("custom.lib.File");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.getFirst().type());
        assertEquals("custom.lib.File", tokens.getFirst().value());
    }

    @Test
    void shouldTokenizeArrow() {
        Lexer lexer = new Lexer("a -> b");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals(TokenType.ARROW, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
    }

    @Test
    void shouldThrowOnInvalidArrow() {
        Lexer lexer = new Lexer("-");
        assertThrows(ParseException.class, lexer::tokenize);
    }

    @Test
    void shouldParseStringWithEscapes() {
        Lexer lexer = new Lexer("\"line\\ntext\"");
        List<Token> tokens = lexer.tokenize();

        assertEquals("line\ntext", tokens.getFirst().value());
    }

    @Test
    void shouldThrowOnUnterminatedString() {
        Lexer lexer = new Lexer("\"unterminated");
        assertThrows(ParseException.class, lexer::tokenize);
    }

    @Test
    void shouldSkipSingleLineComment() {
        Lexer lexer = new Lexer("""
                // comment
                libsl "1.0";
                """);

        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.LIBSL, tokens.getFirst().type());
    }

    @Test
    void shouldSkipMultiLineComment() {
        Lexer lexer = new Lexer("""
                /* multi
                   line */
                libsl "1.0";
                """);

        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.LIBSL, tokens.getFirst().type());
    }

    @Test
    void shouldParseIntegerLiteral() {
        Lexer lexer = new Lexer("12345");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.INT_LITERAL, tokens.getFirst().type());
        assertEquals("12345", tokens.getFirst().value());
    }

    @Test
    void shouldThrowOnUnexpectedCharacter() {
        Lexer lexer = new Lexer("@");
        assertThrows(ParseException.class, lexer::tokenize);
    }
}