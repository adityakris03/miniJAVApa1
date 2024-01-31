package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

import java.util.LinkedList;
import java.util.Queue;

public class Parser {
    private final Scanner _scanner;
    private final ErrorReporter _errors;
    private Token _currentToken;

    public Parser(Scanner scanner, ErrorReporter errors) {
        this._scanner = scanner;
        this._errors = errors;
        this._currentToken = this._scanner.scan();
    }

    public void parse() {
        try {
            // The first thing we need to parse is the Program symbol
            if (_currentToken == null) throw new SyntaxError();
            parseProgram();
        } catch (SyntaxError e) {

        }
    }

    // Program ::= (ClassDeclaration)* eot
    private void parseProgram() throws SyntaxError {
        // TODO: Keep parsing class declarations until eot
        while (_currentToken.getTokenType() == TokenType.CLASS) {
            parseClassDeclaration();
        }
        accept(TokenType.EOT);
    }

    // ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
    private void parseClassDeclaration() throws SyntaxError {
        // TODO: Take in a "class" token (check by the TokenType)
        //  What should be done if the first token isn't "class"?
        accept(TokenType.CLASS);
        // TODO: Take in an identifier token
        accept(TokenType.ID);
        // TODO: Take in a {
        accept(TokenType.LCURLY);
        // TODO: Parse either a FieldDeclaration or MethodDeclaration
        while (_currentToken.getTokenType() != TokenType.RCURLY) {
            if (_currentToken.getTokenType() == TokenType.VISIBILITY) accept(TokenType.VISIBILITY);
            if (_currentToken.getTokenType() == TokenType.ACCESS) accept(TokenType.ACCESS);
            if (_currentToken.getTokenType() == TokenType.VOID) {
                accept(TokenType.VOID);
            } else parseType();
            accept(TokenType.ID);
            switch (_currentToken.getTokenType()) {
                case SEMICOLON:
                    parseFieldDeclaration();
                    break;
                case LPAREN:
                    parseMethodDeclaration();
                    break;
                default:
                    _errors.reportError("Not a field or method declaration");
                    throw new SyntaxError();
            }
        }
        // TODO: Take in a }
        accept(TokenType.RCURLY);
    }

    private void parseFieldDeclaration() throws SyntaxError {
        accept(TokenType.SEMICOLON);
    }
    private void parseMethodDeclaration() throws SyntaxError {
        accept(TokenType.LPAREN);
        while (_currentToken.getTokenType() != TokenType.RPAREN) {
            parseType();
            accept(TokenType.ID);
            while (_currentToken.getTokenType() == TokenType.COMMA) {
                accept(TokenType.COMMA);
                parseType();
                accept(TokenType.ID);
            }
        }
        accept(TokenType.RPAREN);
        accept(TokenType.LCURLY);
        while (_currentToken.getTokenType() != TokenType.RCURLY) {
            parseStatement();
        }
        accept(TokenType.RCURLY);
    }
    private void parseType() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case ID:
            case INT:
                accept(_currentToken.getTokenType());
                if (_currentToken.getTokenType() == TokenType.LBRACKET) {
                    accept(TokenType.LBRACKET);
                    accept(TokenType.RBRACKET);
                }
                return;
            case BOOLEAN:
                accept(TokenType.BOOLEAN);
                return;
            default:
                _errors.reportError("Invalid Type " + _currentToken.getTokenType());
                throw new SyntaxError();
        }
    }

    private void parseStatement() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case LCURLY:
                accept(TokenType.LCURLY);
                while (_currentToken.getTokenType() != TokenType.RCURLY) {
                    parseStatement();
                }
                accept(TokenType.RCURLY);
                return;
            case RETURN:
                accept(TokenType.RETURN);
                if (_currentToken.getTokenType() != TokenType.SEMICOLON) {
                    parseExpression();
                }
                accept(TokenType.SEMICOLON);
                return;
            case IF:
                accept(TokenType.IF);
                accept(TokenType.LPAREN);
                parseExpression();
                accept(TokenType.RPAREN);
                parseStatement();
                if (_currentToken.getTokenType() == TokenType.ELSE) {
                    accept(TokenType.ELSE);
                    parseStatement();
                }
                return;
            case WHILE:
                accept(TokenType.WHILE);
                accept(TokenType.LPAREN);
                parseExpression();
                accept(TokenType.RPAREN);
                parseStatement();
                return;
            case THIS:
                accept(TokenType.THIS);
                while (_currentToken.getTokenType() == TokenType.PERIOD) {
                    accept(TokenType.PERIOD);
                    accept(TokenType.ID);
                }
                parseStatementReference();
                return;
            case INT:
            case BOOLEAN:
                parseType();
                accept(TokenType.ID);
                accept(TokenType.ASSIGNEQUALS);
                parseExpression();
                accept(TokenType.SEMICOLON);
                return;
            case ID:
                accept(TokenType.ID);
                switch (_currentToken.getTokenType()) {
                    case LBRACKET:
                        accept(TokenType.LBRACKET);
                        accept(TokenType.RBRACKET);
                    case ID:
                        accept(TokenType.ID);
                        accept(TokenType.ASSIGNEQUALS);
                        parseExpression();
                        accept(TokenType.SEMICOLON);
                        return;
                    case PERIOD:
                        while (_currentToken.getTokenType() == TokenType.COMMA) {
                            accept(TokenType.COMMA);
                            accept(TokenType.ID);
                        }
                    default:
                        parseStatementReference();
                        return;
                }

            default:
                _errors.reportError("Invalid Statement");
                throw new SyntaxError();
        }
    }

    private void parseStatementReference() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case ASSIGNEQUALS:
                accept(TokenType.ASSIGNEQUALS);
                parseExpression();
                accept(TokenType.SEMICOLON);
                return;
            case LBRACKET:
                accept(TokenType.LBRACKET);
                parseExpression();
                accept(TokenType.RBRACKET);
                accept(TokenType.ASSIGNEQUALS);
                parseExpression();
                accept(TokenType.SEMICOLON);
                return;
            case LPAREN:
                accept(TokenType.LPAREN);
                parseArgumentList();
                accept(TokenType.RPAREN);
                accept(TokenType.SEMICOLON);
                return;
            default:
                _errors.reportError("Invalid Statement with Reference");
                throw new SyntaxError();
        }
    }

    private void parseExpression() throws SyntaxError {
        switch (_currentToken.getTokenType()) {
            case OP:
                if (_currentToken.getTokenText().equals("!") || _currentToken.getTokenText().equals("-")) {
                    parseExpression();
                    break;
                }
                _errors.reportError("Illegal operator");
                throw new SyntaxError();
            case NUM:
            case TRUE:
            case FALSE:
                accept(_currentToken.getTokenType());
                break;
            case NEW:
                accept(TokenType.NEW);
                switch (_currentToken.getTokenType()) {
                    case ID:
                        if (_currentToken.getTokenType() == TokenType.LPAREN) {

                            accept(TokenType.LPAREN);
                            accept(TokenType.RPAREN);
                        } else {
                            accept(TokenType.LBRACKET);
                            parseExpression();
                            accept(TokenType.RBRACKET);
                        }
                        break;
                    case INT:
                        accept(TokenType.INT);
                        accept(TokenType.LBRACKET);
                        parseExpression();
                        accept(TokenType.RBRACKET);
                        break;
                    default:
                        _errors.reportError("Illegal use of new");
                        throw new SyntaxError();
                }
                break;
            case LPAREN:
                accept(TokenType.LPAREN);
                parseExpression();
                accept(TokenType.RPAREN);
                break;
            case ID:
            case THIS:
                accept(_currentToken.getTokenType());
                while (_currentToken.getTokenType() == TokenType.PERIOD) {
                    accept(TokenType.PERIOD);
                    accept(TokenType.ID);
                }
                switch (_currentToken.getTokenType()) {
                    case LBRACKET:
                        accept(TokenType.LBRACKET);
                        parseExpression();
                        accept(TokenType.RBRACKET);
                        break;
                    case LPAREN:
                        accept(TokenType.LPAREN);
                        parseArgumentList();
                        accept(TokenType.RPAREN);
                }
                break;
            default:
                _errors.reportError("Invalid Expression");
                throw new SyntaxError();
        }
        while (_currentToken.getTokenType() == TokenType.OP && !_currentToken.getTokenText().equals("!")) {
            accept(TokenType.OP);
            parseExpression();
        }

    }

    private void parseArgumentList() throws SyntaxError {
        if (_currentToken.getTokenType() != TokenType.RPAREN) {
            parseExpression();
            while (_currentToken.getTokenType() == TokenType.COMMA) {
                accept(TokenType.COMMA);
                parseExpression();
            }
        }
    }

    // This method will accept the token and retrieve the next token.
    //  Can be useful if you want to error check and accept all-in-one.

    /* if the queue is not empty, I want to accept the tokens from the queue first and then
    * and then start checking the _currentToken field for accepting tokens.

    */
    private void accept(TokenType expectedType) throws SyntaxError {
        System.out.println(_currentToken.getTokenType());
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

    class SyntaxError extends Error {
        private static final long serialVersionUID = -6461942006097999362L;
    }

}
