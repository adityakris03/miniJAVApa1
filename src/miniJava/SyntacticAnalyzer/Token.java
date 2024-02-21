package miniJava.SyntacticAnalyzer;

public class Token {
    private final TokenType _type;
    private final String _text;

    public Token(TokenType type, String text) {
        // TODO: Store the token's type and text
        _type = type;
        _text = text;
    }

    public TokenType getTokenType() {
        // TODO: Return the token type
        return _type;
    }

    public String getTokenText() {
        // TODO: Return the token text
        return _text;
    }

    public SourcePosition getTokenPosition() {
        return null;
    }
}
