package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.Objects;

public class TypeChecking implements Visitor<Object, Object> {

    public ErrorReporter _errors;

    public TypeChecking(ErrorReporter _errors) {
        this._errors = _errors;
    }

    public void runTypeChecking(Package p) {
        try {
            p.visit(this, null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        prog.classDeclList.forEach(cd -> cd.visit(this, null));
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        cd.fieldDeclList.forEach(fd -> fd.visit(this, null));
        cd.methodDeclList.forEach(md -> md.visit(this, null));
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        return fd.type;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.parameterDeclList.forEach(pd -> pd.visit(this, null));
        md.statementList.forEach(s -> s.visit(this, md));
        return md.type;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        return pd.type;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        if (decl.type instanceof ClassType &&
                ((ClassType) decl.type).className.spelling.equals("String"))
            return new BaseType(TypeKind.UNSUPPORTED, null);
        return decl.type;
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        return null;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        return null;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        stmt.sl.forEach(s -> s.visit(this, arg));
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        //System.out.println(stmt.initExp.getClass());
        checkType((TypeDenoter) stmt.varDecl.visit(this, null),
                (TypeDenoter) stmt.initExp.visit(this, arg));
        return null;
    }

    private void checkType(TypeDenoter left, TypeDenoter right) {
        Object placeholder = left.typeKind + " " + right.typeKind;
        //System.out.println(placeholder);
        if (!left.equals(right)) {
            if (left instanceof ClassType && right instanceof ClassType) {
                Object placeholder2 = ((ClassType) left).className.spelling.equals(((ClassType) right).className.spelling);
            }
            if (left instanceof BaseType && right instanceof BaseType) {
                Object placeholder2 = ((BaseType) left).typeKind.equals(((BaseType) right).typeKind);
            }
            reportError("incorrect type");
        }
        //if (left instanceof BaseType)
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        Object placeholder = stmt.ref.decl.type.getClass();
        if (stmt.ref.decl.type instanceof BaseType || stmt.ref.decl.type instanceof ClassType || stmt.ref.decl.type instanceof ArrayType) {
            checkType((TypeDenoter) stmt.ref.visit(this, null),
                    (TypeDenoter) stmt.val.visit(this, null));
            return null;
        }
        reportError("wrong type during assignment");
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        if (!(stmt.ref.decl.type instanceof ArrayType)) {
            reportError("array type expected");
            return null;
        }
        if (!((TypeDenoter) stmt.ix.visit(this, null)).typeKind.equals(TypeKind.INT)) reportError("expected int");
        checkType(((ArrayType) stmt.ref.decl.type).eltType, (TypeDenoter) stmt.exp.visit(this, null));
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        if ((stmt.methodRef.decl instanceof MethodDecl)) {
            MethodDecl md = (MethodDecl) stmt.methodRef.decl;
            if (md.parameterDeclList.size() != stmt.argList.size()) reportError("incorrect argument size");
            for (int i = 0; i < md.parameterDeclList.size(); i++) {
                checkType((TypeDenoter) md.parameterDeclList.get(i).visit(this, null), (TypeDenoter) stmt.argList.get(i).visit(this, null));
            }
        } else {
            reportError("should be method decl");
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        MethodDecl md = (MethodDecl) arg;
        if (stmt.returnExpr == null && !md.type.typeKind.equals(TypeKind.VOID)) {
            reportError("need return stmt");
        }
        TypeDenoter rTD = (TypeDenoter) Objects.requireNonNull(stmt.returnExpr).visit(this, null);
        checkType(md.type, rTD);
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        if (!((TypeDenoter) stmt.cond.visit(this, null)).typeKind.equals(TypeKind.BOOLEAN))
            reportError("condition not bolean");
        stmt.thenStmt.visit(this, arg);
        if (stmt.elseStmt != null)
            stmt.elseStmt.visit(this, arg);
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        if (!((TypeDenoter) stmt.cond.visit(this, null)).typeKind.equals(TypeKind.BOOLEAN))
            reportError("while cond should be boolean");
        stmt.body.visit(this, arg);
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        TypeDenoter exp = (TypeDenoter) expr.expr.visit(this, null);
        switch (expr.operator.spelling) {
            case "!":
                if (!exp.typeKind.equals(TypeKind.BOOLEAN)) _errors.reportError("should be boolean expression");
                return new BaseType(TypeKind.BOOLEAN, null);
            case "-":
                if (!exp.typeKind.equals(TypeKind.INT)) _errors.reportError("should be int expression");
                return new BaseType(TypeKind.INT, null);
            default:
                reportError("non unary operator");
                return new BaseType(TypeKind.ERROR, null);
        }
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        //System.out.println("here");
        TypeDenoter left = (TypeDenoter) expr.left.visit(this, arg);
        TypeDenoter right = (TypeDenoter) expr.right.visit(this, arg);

        switch (expr.operator.spelling) {
            case ">":
            case "<":
            case ">=":
            case "<=":
            case "!=":
                if (!left.typeKind.equals(TypeKind.INT)) _errors.reportError("should be int expression");
                if (!right.typeKind.equals(TypeKind.INT)) _errors.reportError("should be int expression");
                return new BaseType(TypeKind.BOOLEAN, null);

            case "==":
                checkType(left, right);
                return new BaseType(TypeKind.BOOLEAN, null);

            case "&&":
            case "||":
                if (!left.typeKind.equals(TypeKind.BOOLEAN)) _errors.reportError("should be boolean expression");
                if (!right.typeKind.equals(TypeKind.BOOLEAN)) _errors.reportError("should be boolean expression");
                return new BaseType(TypeKind.BOOLEAN, null);

            case "+":
            case "-":
            case "*":
            case "/":
                if (!left.typeKind.equals(TypeKind.INT)) _errors.reportError("should be int expression");
                if (!right.typeKind.equals(TypeKind.INT)) _errors.reportError("should be int expression");
                return new BaseType(TypeKind.INT, null);

            default:
                _errors.reportError("invalid operator combination");
                return new BaseType(TypeKind.ERROR, null);
        }
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        return expr.ref.visit(this, arg);
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        if (expr.ref.visit(this, null) instanceof ArrayType) {
            ArrayType at = (ArrayType) expr.ref.visit(this, null);
            if (!((TypeDenoter) expr.ixExpr.visit(this, null)).typeKind.equals(TypeKind.INT))
                _errors.reportError("expected int when accessing array");

            return at.eltType;
        }
        _errors.reportError("should be array type");
        return new BaseType(TypeKind.ERROR, null);

    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        if (expr.functionRef.decl instanceof MethodDecl) {
            MethodDecl md = (MethodDecl) expr.functionRef.decl;
            if (md.parameterDeclList.size() != expr.argList.size()) reportError("incorrect argument size");
            for (int i = 0; i < md.parameterDeclList.size(); i++)
                checkType((TypeDenoter) md.parameterDeclList.get(i).visit(this, null), (TypeDenoter) expr.argList.get(i).visit(this, null));
            return md.type;
        }
        _errors.reportError("should be method call");
        return new BaseType(TypeKind.ERROR, null);
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        return expr.lit.visit(this, null);
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        if (!expr.classtype.className.spelling.equals("String"))
            return expr.classtype;
        else
            return new BaseType(TypeKind.UNSUPPORTED, null);
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        checkType(new BaseType(TypeKind.INT, null), (TypeDenoter) expr.sizeExpr.visit(this, null));
        return new ArrayType(expr.eltType, null);
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        return ref.decl.type;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        if (arg instanceof MethodDecl && ((MethodDecl) arg).isStatic && ref.decl instanceof MemberDecl && !((MemberDecl) ref.decl).isStatic) {
            reportError("static var used in non static context");
        }
        return ref.decl.type;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        return ref.decl.type;
    }

    @Override
    public Object visitNullRef(NullRef nullRef, Object arg) {
        return new BaseType(TypeKind.CLASS, null);
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        return id.decl.visit(this, null);
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        return new BaseType(TypeKind.INT, null);
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        return new BaseType(TypeKind.BOOLEAN, null);
    }

    public void reportError(String message) {
        _errors.reportError(message);
        throw new TypeCheckingError(message);
    }

}

class TypeCheckingError extends RuntimeException {
    public TypeCheckingError(String message) {
        super(message);

    }
}