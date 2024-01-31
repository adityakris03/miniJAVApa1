package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

import java.io.IOException;
import java.io.InputStream;

public class Scanner {
    private final InputStream _in;
    private final ErrorReporter _errors;
    private final int _currentLine;
    private final int _currentCol;
    private StringBuilder _currentText;
    private char _currentChar;

    public Scanner(InputStream in, ErrorReporter errors) {
        this._in = in;
        this._errors = errors;
        this._currentText = new StringBuilder();
        this._currentLine = 0; //every newline, increment this and set col to 0
        this._currentCol = 0; //increment ever nextChar()

        nextChar();
    }

    public Token scan() { // returns null if no token can be returned
        // TODO: This function should check the current char to determine what the token could be.

        // TODO: Consider what happens if the current char is whitespace
        if (_currentChar == ' ' || _currentChar == '\t' || _currentChar == '\n' || _currentChar == '\r') {
            skipIt();
            return scan();
        }
        // TODO: Consider what happens if there is a comment (// or /* */)
        if (_currentChar == '/') {
            skipIt();
            if (_currentChar == '/') {
                skipIt();
                while (_currentChar != '\n') skipIt();
                return scan();
            } else if (_currentChar == '*') {
                skipIt();
                while (true) {
                    switch (_currentChar) {
                        case '*':
                            skipIt();
                            if (_currentChar == '/') return scan();
                            break;
                        case '\uFFFF':
                            _errors.reportError("No end of block comment");
                            return null;
                        default:
                    }
                }
            } else {
                _currentText = new StringBuilder("/");
                return makeToken(TokenType.OP);
            }
        }
        // TODO: What happens if there are no more tokens?
        if (_currentChar == '\uFFFF') return makeToken(TokenType.EOT);


        // TODO: Determine what the token is. For example, if it is a number
        //  keep calling takeIt() until _currentChar is not a number. Then
        //  create the token via makeToken(TokenType.IntegerLiteral) and return it.
        if (Character.isLetter(_currentChar)) {
            while (Character.isLetterOrDigit(_currentChar) || _currentChar == '_') {
                takeIt();
            }
            switch (_currentText.toString()) {
                case "class":
                    return makeToken(TokenType.CLASS);
                case "this":
                    return makeToken(TokenType.THIS);
                case "public":
                case "private":
                    return makeToken(TokenType.VISIBILITY);
                case "static":
                    return makeToken(TokenType.ACCESS);
                case "int":
                    return makeToken(TokenType.INT);
                case "boolean":
                    return makeToken(TokenType.BOOLEAN);
                case "true":
                    return makeToken(TokenType.TRUE);
                case "false":
                    return makeToken(TokenType.FALSE);
                case "while":
                    return makeToken(TokenType.WHILE);
                case "if":
                    return makeToken(TokenType.IF);
                case "return":
                    return makeToken(TokenType.RETURN);
                case "else":
                    return makeToken(TokenType.ELSE);
                case "void":
                    return makeToken(TokenType.VOID);
                case "new":
                    return makeToken(TokenType.NEW);
                default:
                    return makeToken(TokenType.ID);


            }
        } else if (Character.isDigit(_currentChar)) {
            while (Character.isDigit(_currentChar)) {
                takeIt();
            }
            return makeToken(TokenType.NUM);
        } else {
            switch (_currentChar) {
                case '>':
                case '<':
                case '!':
                case '=':
                    takeIt();
                    if (_currentChar == '=') takeIt();
                    return !_currentText.toString().equals("=") ? makeToken(TokenType.OP) : makeToken(TokenType.ASSIGNEQUALS);
                case '+':
                case '-':
                case '*':
                    takeIt();
                    return makeToken(TokenType.OP);
				case '|':
				case '&':
					takeIt();
					if (_currentChar != _currentText.charAt(0)) {
						_errors.reportError("Expected another " + _currentChar);
						return null;
					}
					return makeToken(TokenType.OP);
				case ';':
                    takeIt();
					return makeToken(TokenType.SEMICOLON);
				case '.':
                    takeIt();
                    return makeToken(TokenType.PERIOD);
				case '[':
                    takeIt();
                    return makeToken(TokenType.LBRACKET);
				case ']':
                    takeIt();
                    return makeToken(TokenType.RBRACKET);
                case ',':
                    takeIt();
                    return makeToken(TokenType.COMMA);
                case '(':
                    takeIt();
                    return makeToken(TokenType.LPAREN);
                case ')':
                    takeIt();
                    return makeToken(TokenType.RPAREN);
                case '{':
                    takeIt();
                    return makeToken(TokenType.LCURLY);
                case '}':
                    takeIt();
                    return makeToken(TokenType.RCURLY);
                default:
					_errors.reportError("Didn't understand character " + _currentChar + ". Code: " + (int)_currentChar);
                    return null;
            }
        }

    }

    private void takeIt() {
        _currentText.append(_currentChar);
        nextChar();
    }

    private void skipIt() {
        nextChar();
    }

    private void nextChar() {
        try {
            int c = _in.read();
            _currentChar = (char) c;

            // TODO: What happens if c == -1?


            // TODO: What happens if c is not a regular ASCII character?
            if ((c < 32 || c > 126) && c != 10 && c != 13 && c != -1 && c != 9) _errors.reportError("Not printable ASCII char " + c);
        } catch (IOException e) {
            // TODO: Report an error here
            _errors.reportError("IO exception, Line: " + _currentLine + " Column: " + _currentCol);
        }
    }

    private Token makeToken(TokenType toktype) {
        //System.out.println(_currentText);
        // TODO: return a new Token with the appropriate type and text
        //  contained in
        Token token = new Token(toktype, _currentText.toString());
        _currentText = new StringBuilder();
        return token;
    }
}
