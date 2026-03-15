package ru.itmo.analyzer.specification.parser;


import ru.itmo.analyzer.specification.parser.error.ParseException;
import ru.itmo.analyzer.specification.parser.model.ast.Statement;
import ru.itmo.analyzer.specification.parser.model.ast.Expression;
import ru.itmo.analyzer.specification.parser.model.ast.AutomatonDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.FunctionDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Parameter;
import ru.itmo.analyzer.specification.parser.model.ast.Requirement;
import ru.itmo.analyzer.specification.parser.model.ast.ShiftDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.Specification;
import ru.itmo.analyzer.specification.parser.model.ast.StateDeclaration;
import ru.itmo.analyzer.specification.parser.model.tokenizer.Token;
import ru.itmo.analyzer.specification.parser.model.tokenizer.TokenType;
import ru.itmo.analyzer.specification.parser.model.ast.TypeDeclaration;
import ru.itmo.analyzer.specification.parser.model.ast.VariableDeclaration;

import java.util.ArrayList;
import java.util.List;

public class LibSlParser {

    private final List<Token> tokens;
    private int pos = 0;

    public LibSlParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ═══════════════════════════════════════════════════════════
    //  Публичная точка входа
    // ═══════════════════════════════════════════════════════════
    public Specification parse() {
        String libslVersion = parseLibslVersion();
        String[] lib = parseLibraryDecl();

        List<TypeDeclaration> types = check(TokenType.TYPES)
                ? parseTypesBlock()
                : List.of();

        var automata = new ArrayList<AutomatonDeclaration>();
        while (!check(TokenType.EOF)) {
            automata.add(parseAutomaton());
        }

        return new Specification(
                libslVersion, lib[0], lib[1], types, automata);
    }

    // ═══════════════════════════════════════════════════════════
    //  Верхний уровень
    // ═══════════════════════════════════════════════════════════

    // libsl "x.y.z";
    private String parseLibslVersion() {
        expect(TokenType.LIBSL);
        String ver = expect(TokenType.STRING_LITERAL).value();
        expect(TokenType.SEMICOLON);
        return ver;
    }

    // library Name version "x.y.z";
    private String[] parseLibraryDecl() {
        expect(TokenType.LIBRARY);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.VERSION);
        String ver = expect(TokenType.STRING_LITERAL).value();
        expect(TokenType.SEMICOLON);
        return new String[]{name, ver};
    }

    // types { Name (impl); ... }
    private List<TypeDeclaration> parseTypesBlock() {
        expect(TokenType.TYPES);
        expect(TokenType.LBRACE);
        var types = new ArrayList<TypeDeclaration>();
        while (!check(TokenType.RBRACE)) {
            String name = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.LPAREN);
            String impl = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.RPAREN);
            if (check(TokenType.SEMICOLON)) advance();   // точка с запятой необязательна
            types.add(new TypeDeclaration(name, impl));
        }
        expect(TokenType.RBRACE);
        return types;
    }

    // ═══════════════════════════════════════════════════════════
    //  Автомат
    // ═══════════════════════════════════════════════════════════

    // automaton qualified.Name : TypeName { ... }
    private AutomatonDeclaration parseAutomaton() {
        expect(TokenType.AUTOMATON);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String typeName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LBRACE);

        var states = new ArrayList<StateDeclaration>();
        var variables = new ArrayList<VariableDeclaration>();
        var shifts = new ArrayList<ShiftDeclaration>();
        var functions = new ArrayList<FunctionDeclaration>();

        while (!check(TokenType.RBRACE)) {
            switch (current().type()) {
                case INITSTATE -> {
                    advance();
                    states.add(new StateDeclaration(
                            expect(TokenType.IDENTIFIER).value(), true));
                    expect(TokenType.SEMICOLON);
                }
                case STATE -> {
                    advance();
                    states.add(new StateDeclaration(
                            expect(TokenType.IDENTIFIER).value(), false));
                    expect(TokenType.SEMICOLON);
                }
                case VAR -> variables.add(parseVariable());
                case SHIFT -> shifts.add(parseShift());
                case FUN -> functions.add(parseFunction());
                default -> throw new ParseException(
                        "Unexpected token in automaton body", current());
            }
        }
        expect(TokenType.RBRACE);

        return new AutomatonDeclaration(
                name, typeName, states, variables, shifts, functions);
    }

    // var name: type = expr;
    private VariableDeclaration parseVariable() {
        expect(TokenType.VAR);
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String type = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.EQUALS);
        Expression init = parseExpression();
        expect(TokenType.SEMICOLON);
        return new VariableDeclaration(name, type, init);
    }

    // shift From -> To(func1, func2, ...);
    private ShiftDeclaration parseShift() {
        expect(TokenType.SHIFT);
        String from = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.ARROW);
        String to = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.LPAREN);

        var funcs = new ArrayList<String>();
        if (!check(TokenType.RPAREN)) {
            funcs.add(expect(TokenType.IDENTIFIER).value());
            while (check(TokenType.COMMA)) {
                advance();
                funcs.add(expect(TokenType.IDENTIFIER).value());
            }
        }
        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new ShiftDeclaration(from, to, funcs);
    }

    // fun name(params) requires ...: ... { body } | ;
    private FunctionDeclaration parseFunction() {
        expect(TokenType.FUN);
        String name = expect(TokenType.IDENTIFIER).value();

        // Параметры
        expect(TokenType.LPAREN);
        var params = new ArrayList<Parameter>();
        if (!check(TokenType.RPAREN)) {
            params.add(parseParameter());
            while (check(TokenType.COMMA)) {
                advance();
                params.add(parseParameter());
            }
        }
        expect(TokenType.RPAREN);

        // Предусловия: requires name: expression
        var requirements = new ArrayList<Requirement>();
        while (check(TokenType.REQUIRES)) {
            advance();
            String reqName = expect(TokenType.IDENTIFIER).value();
            expect(TokenType.COLON);
            Expression cond = parseExpression();
            requirements.add(new Requirement(reqName, cond));
        }

        // Тело или точка с запятой
        var body = new ArrayList<Statement>();
        if (check(TokenType.LBRACE)) {
            advance();
            while (!check(TokenType.RBRACE)) {
                body.add(parseStatement());
            }
            advance();
        } else {
            expect(TokenType.SEMICOLON);
        }

        return new FunctionDeclaration(name, params, requirements, body);
    }

    // name: Type
    private Parameter parseParameter() {
        String name = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.COLON);
        String type = expect(TokenType.IDENTIFIER).value();
        return new Parameter(name, type);
    }

    // ═══════════════════════════════════════════════════════════
    //  Инструкции
    // ═══════════════════════════════════════════════════════════

    // variable = expression;
    private Statement parseStatement() {
        String varName = expect(TokenType.IDENTIFIER).value();
        expect(TokenType.EQUALS);
        Expression value = parseExpression();
        expect(TokenType.SEMICOLON);
        return new Statement.Assignment(varName, value);
    }

    // ═══════════════════════════════════════════════════════════
    //  Выражения
    // ═══════════════════════════════════════════════════════════

    private Expression parseExpression() {
        if (check(TokenType.EXCLAMATION)) {
            advance();
            return new Expression.Not(parseExpression());
        }
        return parsePrimary();
    }

    private Expression parsePrimary() {
        if (check(TokenType.TRUE)) {
            advance();
            return new Expression.BoolLiteral(true);
        }
        if (check(TokenType.FALSE)) {
            advance();
            return new Expression.BoolLiteral(false);
        }

        if (check(TokenType.STRING_LITERAL))
            return new Expression.StringLiteral(advance().value());
        if (check(TokenType.INT_LITERAL))
            return new Expression.IntLiteral(Integer.parseInt(advance().value()));
        if (check(TokenType.IDENTIFIER))
            return new Expression.VarRef(advance().value());

        if (check(TokenType.LPAREN)) {
            advance();
            Expression expr = parseExpression();
            expect(TokenType.RPAREN);
            return expr;
        }

        throw new ParseException("Expected expression", current());
    }

    // ═══════════════════════════════════════════════════════════
    //  Вспомогательные методы
    // ═══════════════════════════════════════════════════════════

    private Token current() {
        return pos < tokens.size() ? tokens.get(pos) : tokens.getLast();
    }

    private boolean check(TokenType type) {
        return current().type() == type;
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type) {
        if (!check(type))
            throw new ParseException("Expected " + type, current());
        return advance();
    }
}