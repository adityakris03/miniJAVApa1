/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class LocalDecl extends Declaration {
    public int offset = -1;
    public LocalDecl(String name, TypeDenoter t, SourcePosition posn) {
        super(name, t, posn);
    }

}
