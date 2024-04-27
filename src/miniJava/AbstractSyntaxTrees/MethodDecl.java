/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class MethodDecl extends MemberDecl {

    public ParameterDeclList parameterDeclList;
    public StatementList statementList;
    public ClassDecl insideClass;
    public int instructionAddr = -1;
    public int args = 0;
    public int stackSize = 0;
    public MethodDecl(MemberDecl md, ParameterDeclList pl, StatementList sl, SourcePosition posn) {
        super(md, posn);
        parameterDeclList = pl;
        statementList = sl;
    }

    public <A, R> R visit(Visitor<A, R> v, A o) {
        return v.visitMethodDecl(this, o);
    }
}
