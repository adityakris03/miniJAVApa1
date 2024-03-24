package miniJava.SyntacticAnalyzer;

import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class Parser {
    private static final String[][] BINOPS = {{"||"}, {"&&"}, {"==", "!="}, {"<=", "<", ">", ">="}, {"+", "-"}, {"*", "/"}, {"-", "!"}};
    private final Scanner _scanner;
    private final ErrorReporter _errors;
    private Token _currentToken;

    public Parser(Scanner scanner, ErrorReporter errors) {
        this._scanner = scanner;
        this._errors = errors;
        this._currentToken = this._scanner.scan();
    }

    public Package parse() {
        try {
            // The first thing we need to parse is the Program symbol
            if (_currentToken == null) throw new SyntaxError();
            return new Package(parseProgram(), null);
        } catch (SyntaxError e) {
            return null;
        }
    }

    // Program ::= (ClassDeclaration)* eot
    private ClassDeclList parseProgram() throws SyntaxError {
        // TODO: Keep parsing class declarations until eot
        ClassDeclList cdl = new ClassDeclList();
        while (_currentToken.getTokenType() == TokenType.CLASS) {
            cdl.add(parseClassDeclaration());
        }
        accept(TokenType.EOT);
        return cdl;
    }

    // ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
    private ClassDecl parseClassDeclaration() throws SyntaxError {
        // TODO: Take in a "class" token (check by the TokenType)
        //  What should be done if the first token isn't "class"?
        accept(TokenType.CLASS);
        // TODO: Take in an identifier token
        String cn = _currentToken.getTokenText();
        accept(TokenType.ID);
        // TODO: Take in a {
        accept(TokenType.LCURLY);
        // TODO: Parse either a FieldDeclaration or MethodDeclaration
        MethodDeclList mdl = new MethodDeclList();
        FieldDeclList fdl = new FieldDeclList();
        while (_currentToken.getTokenType() != TokenType.RCURLY) {
            boolean isPrivate = Objects.equals(_currentToken.getTokenText(), "private");
            if (_currentToken.getTokenType() == TokenType.VISIBILITY) accept(TokenType.VISIBILITY);
            boolean isStatic = false;
            if (_currentToken.getTokenType() == TokenType.ACCESS) {
                accept(TokenType.ACCESS);
                isStatic = true;
            }
            TypeDenoter t;
            if (_currentToken.getTokenType() == TokenType.VOID) {
                t = new BaseType(TypeKind.VOID, null);
                accept(TokenType.VOID);
                String name = _currentToken.getTokenText();
                accept(TokenType.ID);
                mdl.add(parseMethodDeclaration(new FieldDecl(isPrivate, isStatic, t, name, null)));
            } else {
                t = parseType();
                String name = _currentToken.getTokenText();
                accept(TokenType.ID);
                switch (_currentToken.getTokenType()) {
                    case SEMICOLON:
                        fdl.add(parseFieldDeclaration(new FieldDecl(isPrivate, isStatic, t, name, null)));
                        break;
                    case LPAREN:
                        mdl.add(parseMethodDeclaration(new FieldDecl(isPrivate, isStatic, t, name, null)));
                        break;
                    default:
                        _errors.reportError("Not a field or method declaration");
                        throw new SyntaxError();
                }

            }
        }
        // TODO: Take in a }
        accept(TokenType.RCURLY);
        return new ClassDecl(cn, fdl, mdl, null);
    }

    private FieldDecl parseFieldDeclaration(MemberDecl md) throws SyntaxError {
        accept(TokenType.SEMICOLON);
        return new FieldDecl(md, md.posn);
    }

    private MethodDecl parseMethodDeclaration(MemberDecl md) throws SyntaxError {
        accept(TokenType.LPAREN);
        ParameterDeclList pdl = new ParameterDeclList();
        while (_currentToken.getTokenType() != TokenType.RPAREN) {
            TypeDenoter t = parseType();
            pdl.add(new ParameterDecl(t, _currentToken.getTokenText(), null));
            accept(TokenType.ID);
            while (_currentToken.getTokenType() == TokenType.COMMA) {
                accept(TokenType.COMMA);
                t = parseType();
                pdl.add(new ParameterDecl(t, _currentToken.getTokenText(), null));
                accept(TokenType.ID);
            }
        }
        accept(TokenType.RPAREN);
        accept(TokenType.LCURLY);
        StatementList sl = new StatementList();
        while (_currentToken.getTokenType() != TokenType.RCURLY) {
            sl.add(parseStatement());
        }
        accept(TokenType.RCURLY);
        return new MethodDecl(md, pdl, sl, md.posn);
    }

    private TypeDenoter parseType() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case ID:
            case INT:
                Token token = _currentToken;
                accept(_currentToken.getTokenType());
                TypeDenoter td;
                if (token.getTokenType() == TokenType.INT) td = new BaseType(TypeKind.INT, null);
                else if (token.getTokenText().equals("String")) td = new BaseType(TypeKind.UNSUPPORTED, null);
                else td = new ClassType(new Identifier(token), null);
                if (_currentToken.getTokenType() == TokenType.LBRACKET) {
                    accept(TokenType.LBRACKET);
                    accept(TokenType.RBRACKET);
                    return new ArrayType(td, null);
                }
                return td;
            case BOOLEAN:
                accept(TokenType.BOOLEAN);
                return new BaseType(TypeKind.BOOLEAN, null);
            default:
                _errors.reportError("Invalid Type " + _currentToken.getTokenType());
                throw new SyntaxError();
        }
    }

    private Statement parseStatement() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case LCURLY:
                accept(TokenType.LCURLY);
                StatementList sl = new StatementList();
                while (_currentToken.getTokenType() != TokenType.RCURLY) {
                    sl.add(parseStatement());
                }
                accept(TokenType.RCURLY);
                return new BlockStmt(sl, null);
            case RETURN:
                accept(TokenType.RETURN);
                Expression return_e = null;
                if (_currentToken.getTokenType() != TokenType.SEMICOLON) {
                    return_e = parseExpression();
                }
                accept(TokenType.SEMICOLON);
                return new ReturnStmt(return_e, null);
            case IF:
                accept(TokenType.IF);
                accept(TokenType.LPAREN);
                Expression if_b = parseExpression();
                accept(TokenType.RPAREN);
                Statement if_s = parseStatement();
                if (_currentToken.getTokenType() == TokenType.ELSE) {
                    accept(TokenType.ELSE);
                    Statement el = parseStatement();
                    return new IfStmt(if_b, if_s, el, null);
                }
                return new IfStmt(if_b, if_s, null);
            case WHILE:
                accept(TokenType.WHILE);
                accept(TokenType.LPAREN);
                Expression while_e = parseExpression();
                accept(TokenType.RPAREN);
                Statement while_s = parseStatement();
                return new WhileStmt(while_e, while_s, null);
            case THIS:
                Reference thisref = new ThisRef(null);
                accept(TokenType.THIS);
                while (_currentToken.getTokenType() == TokenType.PERIOD) {
                    accept(TokenType.PERIOD);
                    thisref = new QualRef(thisref, new Identifier(_currentToken), null);
                    accept(TokenType.ID);
                }
                return parseStatementReference(thisref);
            case INT:
            case BOOLEAN:
                TypeDenoter t = parseType();
                String varDecl_name = _currentToken.getTokenText();
                accept(TokenType.ID);
                VarDecl vd = new VarDecl(t, varDecl_name, null);
                accept(TokenType.ASSIGNEQUALS);
                Expression varDecl_e = parseExpression();
                accept(TokenType.SEMICOLON);
                return new VarDeclStmt(vd, varDecl_e, null);
            case ID:
                TypeDenoter id_t = new ClassType(new Identifier(_currentToken), null);
                Reference id = new IdRef(new Identifier(_currentToken), null);
                accept(TokenType.ID);
                switch (_currentToken.getTokenType()) {
                    case LBRACKET:
                        accept(TokenType.LBRACKET);
                        if (_currentToken.getTokenType() != TokenType.RBRACKET) {
                            Expression i = parseExpression();
                            accept(TokenType.RBRACKET);
                            accept(TokenType.ASSIGNEQUALS);
                            Expression e = parseExpression();
                            accept(TokenType.SEMICOLON);
                            return new IxAssignStmt(id, i, e, null);
                        } else {
                            accept(TokenType.RBRACKET);
                            id_t = new ArrayType(id_t, null);
                        }
                    case ID:
                        String name = _currentToken.getTokenText();
                        accept(TokenType.ID);
                        accept(TokenType.ASSIGNEQUALS);
                        Expression id_e = parseExpression();
                        accept(TokenType.SEMICOLON);
                        VarDecl id_vd = new VarDecl(id_t, name, null);
                        return new VarDeclStmt(id_vd, id_e, null);
                    case PERIOD:
                        while (_currentToken.getTokenType() == TokenType.PERIOD) {
                            accept(TokenType.PERIOD);
                            id = new QualRef(id, new Identifier(_currentToken), null);
                            accept(TokenType.ID);
                        }
                    default:
                        return parseStatementReference(id);
                }

            default:
                _errors.reportError("Invalid Statement");
                throw new SyntaxError();
        }
    }

    private StatementReference parseStatementReference(Reference r) throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case ASSIGNEQUALS:
                accept(TokenType.ASSIGNEQUALS);
                Expression assign_e = parseExpression();
                accept(TokenType.SEMICOLON);
                return new AssignStmt(r, assign_e, null);
            case LBRACKET:
                accept(TokenType.LBRACKET);
                Expression i = parseExpression();
                accept(TokenType.RBRACKET);
                accept(TokenType.ASSIGNEQUALS);
                Expression e = parseExpression();
                accept(TokenType.SEMICOLON);
                return new IxAssignStmt(r, i, e, null);
            case LPAREN:
                accept(TokenType.LPAREN);
                ExprList el = parseArgumentList();
                accept(TokenType.RPAREN);
                accept(TokenType.SEMICOLON);
                return new CallStmt(r, el, null);
            default:
                _errors.reportError("Invalid Statement with Reference");
                throw new SyntaxError();
        }
    }

    private Expression parseExpression() {
        return parseDisjunction();
    }

    private Expression parseDisjunction() {
        return binopExpr(Arrays.asList(BINOPS[0]), this::parseConjunction);
    }

    private Expression parseConjunction() {
        return binopExpr(Arrays.asList(BINOPS[1]), this::parseEquality);
    }

    private Expression parseEquality() {
        return binopExpr(Arrays.asList(BINOPS[2]), this::parseRelational);
    }

    private Expression parseRelational() {
        return binopExpr(Arrays.asList(BINOPS[3]), this::parseAdditive);
    }

    private Expression parseAdditive() {
        return binopExpr(Arrays.asList(BINOPS[4]), this::parseMultiplicative);
    }

    private Expression parseMultiplicative() {
        return binopExpr(Arrays.asList(BINOPS[5]), this::parseUnary);
    }

    private Expression parseUnary() {
        if (Arrays.asList(BINOPS[6]).contains(_currentToken.getTokenText())) {
            Operator o = new Operator(_currentToken);
            accept(TokenType.OP);
            return new UnaryExpr(o, parseUnary(), null);
        }
        return parseExpressionNormal();
    }

    private Expression parseExpressionNormal() throws SyntaxError {
        Expression e;
        switch (_currentToken.getTokenType()) {
            case NUM:
            case TRUE:
            case FALSE:
                Terminal t = _currentToken.getTokenType() == TokenType.NUM ? new IntLiteral(_currentToken) : new BooleanLiteral(_currentToken);
                e = new LiteralExpr(t, null);
                accept(_currentToken.getTokenType());
                break;
            case NEW:
                accept(TokenType.NEW);
                switch (_currentToken.getTokenType()) {
                    case ID:
                        Identifier i = new Identifier(_currentToken);
                        accept(TokenType.ID);
                        if (_currentToken.getTokenType() == TokenType.LPAREN) {
                            ClassType ct = new ClassType(i, null);
                            accept(TokenType.LPAREN);
                            accept(TokenType.RPAREN);
                            e = new NewObjectExpr(ct, null);
                        } else {
                            accept(TokenType.LBRACKET);
                            Expression newArr_e = parseExpression();
                            accept(TokenType.RBRACKET);
                            e = new NewArrayExpr(new ClassType(i, null), newArr_e, null);
                        }
                        break;
                    case INT:
                        accept(TokenType.INT);
                        accept(TokenType.LBRACKET);
                        Expression newArr_e = parseExpression();
                        accept(TokenType.RBRACKET);
                        e = new NewArrayExpr(new BaseType(TypeKind.INT, null), newArr_e, null);
                        break;
                    default:
                        _errors.reportError("Illegal use of new");
                        throw new SyntaxError();
                }
                break;
            case LPAREN:
                accept(TokenType.LPAREN);
                e = parseExpression();
                accept(TokenType.RPAREN);
                break;
            case ID:
            case THIS:
                Reference r = _currentToken.getTokenType() == TokenType.ID ? new IdRef(new Identifier(_currentToken), null) : new ThisRef(null);

                accept(_currentToken.getTokenType());
                while (_currentToken.getTokenType() == TokenType.PERIOD) {
                    accept(TokenType.PERIOD);
                    r = new QualRef(r, new Identifier(_currentToken), null);
                    accept(TokenType.ID);
                }
                e = new RefExpr(r, null);
                switch (_currentToken.getTokenType()) {
                    case LBRACKET:
                        accept(TokenType.LBRACKET);
                        Expression ix_e = parseExpression();
                        accept(TokenType.RBRACKET);
                        e = new IxExpr(r, ix_e, null);
                        break;
                    case LPAREN:
                        accept(TokenType.LPAREN);
                        ExprList el = parseArgumentList();
                        accept(TokenType.RPAREN);
                        e = new CallExpr(r, el, null);
                }
                break;
            default:
                _errors.reportError("Invalid Expression");
                throw new SyntaxError();
        }
        return e;
    }

    private Expression binopExpr(List<String> OP, Supplier<Expression> func) {
        Expression e = func.get();
        while (_currentToken.getTokenType() == TokenType.OP && OP.contains(_currentToken.getTokenText())) {
            Operator o = new Operator(_currentToken);
            accept(TokenType.OP);
            Expression bin_e = func.get();
            e = new BinaryExpr(o, e, bin_e, null);
        }
        return e;
    }

    private ExprList parseArgumentList() throws SyntaxError {
        ExprList el = new ExprList();
        if (_currentToken.getTokenType() != TokenType.RPAREN) {
            el.add(parseExpression());
            while (_currentToken.getTokenType() == TokenType.COMMA) {
                accept(TokenType.COMMA);
                el.add(parseExpression());
            }
        }

        return el;
    }

    // This method will accept the token and retrieve the next token.
    //  Can be useful if you want to error check and accept all-in-one.

    /* if the queue is not empty, I want to accept the tokens from the queue first and then
     * and then start checking the _currentToken field for accepting tokens.

     */
    private void accept(TokenType expectedType) throws SyntaxError {
        //System.out.println(_currentToken.getTokenType());
        if (_currentToken.getTokenType() == expectedType) {
            _currentToken = _scanner.scan();
            if (_currentToken == null) throw new SyntaxError();
            return;
        }

        // TODO: Report an error here.
        //  "Expected token X, but got Y"
        _errors.reportError(String.format("Expected token %s, but got %s", expectedType, _currentToken.getTokenType()));
        throw new SyntaxError();
    }

    static class SyntaxError extends Error {
        private static final long serialVersionUID = -6461942006097999362L;
    }

}
