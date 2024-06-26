/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

import java.util.Objects;

public class ClassType extends TypeDenoter {
    public Identifier className;

    public ClassType(Identifier cn, SourcePosition posn) {
        super(TypeKind.CLASS, posn);
        className = cn;
    }

    public <A, R> R visit(Visitor<A, R> v, A o) {
        return v.visitClassType(this, o);
    }

    @Override
    public boolean equals(TypeDenoter o) {
        if (this == o) return true;
        if (o instanceof BaseType) return this.typeKind.equals(o.typeKind);
        if (!(o instanceof ClassType)) return false;
        ClassType classType = (ClassType) o;
        //System.out.println(className.spelling.equals(classType.className.spelling) + " about to return");
        return className.spelling.equals(classType.className.spelling);
    }
}
