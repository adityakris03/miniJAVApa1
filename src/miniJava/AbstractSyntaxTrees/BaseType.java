/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

import java.util.Objects;

public class BaseType extends TypeDenoter {
    public BaseType(TypeKind t, SourcePosition posn) {
        super(t, posn);
    }


    public <A, R> R visit(Visitor<A, R> v, A o) {
        return v.visitBaseType(this, o);
    }

    public boolean equals(TypeDenoter o) {
        if (this == o) return true;
        if (!(o instanceof BaseType)) return false;
        BaseType baseType = (BaseType) o;
        return Objects.equals(typeKind, baseType.typeKind);
    }
}
