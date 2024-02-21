package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class StatementReference extends Statement {
    public StatementReference(SourcePosition posn) {
        super(posn);
    }
}
