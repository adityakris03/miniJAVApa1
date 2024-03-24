/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class AST {

    public SourcePosition posn;

    public AST(SourcePosition posn) {
        this.posn = posn;
    }

    public String toString() {
        String fullClassName = this.getClass().getName();
        String cn = fullClassName.substring(1 + fullClassName.lastIndexOf('.'));
        if (ASTDisplay.showPosition && posn != null)
            cn = cn + " " + posn;
        return cn;
    }

    public abstract <A, R> R visit(Visitor<A, R> v, A o);
}
