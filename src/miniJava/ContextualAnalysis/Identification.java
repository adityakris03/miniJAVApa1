package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;

import javax.management.RuntimeErrorException;
import java.util.List;
import java.util.Objects;

public class Identification implements Visitor<Object, Object> {

    ScopedIdentification si;
    ErrorReporter _errors;
    String refNotUsed;

    public Identification(ScopedIdentification si, ErrorReporter _errors) {
        this.si = si;
        this._errors = _errors;
    }

    public void runIdentification(Package p) {
        try {
            p.visit(this, null);
        } catch (IdentificationError e) {
            _errors.reportError(e.getLocalizedMessage());
        }
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        //opened level 0
        prog.classDeclList.forEach(si::addDeclaration);
        visitClassDecl((ClassDecl) si.findDeclaration("String"), null);
        visitClassDecl((ClassDecl) si.findDeclaration("_PrintStream"), null);
        visitClassDecl((ClassDecl) si.findDeclaration("System"), null);
        prog.classDeclList.forEach(cd -> cd.visit(this, null));
        si.closeScope();
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        if (arg != null) {

            return visitClassDeclHelper(cd, (String) arg);
        }
        si.openScope();
        cd.fieldDeclList.forEach(si::addDeclaration);
        cd.fieldDeclList.forEach(fd -> fd.visit(this, cd));
        cd.methodDeclList.forEach(si::addDeclaration);
        cd.methodDeclList.forEach(md -> md.visit(this, cd));
        si.closeScope();
        return null;
    }

    private Object visitClassDeclHelper(ClassDecl cd, String arg) {
        for (MemberDecl md : cd.fieldDeclList)
            if (md.name.equals(arg))
                return md;
        for (MemberDecl md : cd.methodDeclList)
            if (md.name.equals(arg))
                return md;
        throw new IdentificationError("not found");
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        fd.type.visit(this, null);
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.type.visit(this, md);
        si.openScope();
        md.parameterDeclList.forEach(pd -> pd.visit(this, null));
        si.openScope();
        md.statementList.forEach(sl -> sl.visit(this, md));
        si.closeScope();
        si.closeScope();
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        pd.type.visit(this, null);
        si.addDeclaration(pd);
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        decl.type.visit(this, null);
        si.addDeclaration(decl);
        return null;
    }

    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        return null;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        if(!(si._table.get(0).get(type.className.spelling) instanceof ClassDecl)) throw new IdentificationError("not class type");
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        type.eltType.visit(this, null);
        return null;
    }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        si.openScope();
        stmt.sl.forEach(s -> s.visit(this, arg));
        if (stmt.sl.size() == 1 && stmt.sl.get(0) instanceof VarDeclStmt) throw new IdentificationError("one line scope");
        si.closeScope();
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.varDecl.visit(this, null);
        refNotUsed = stmt.varDecl.name;
        stmt.initExp.visit(this, arg);
        refNotUsed = null;
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.ref.visit(this, arg);
        Object ret = stmt.val.visit(this, arg);
        if (ret instanceof MethodDecl)
            throw new IdentificationError("cant use method");
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.ref.visit(this, arg);
        stmt.ix.visit(this, arg);
        stmt.exp.visit(this, arg);
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        stmt.methodRef.visit(this, arg);
        stmt.argList.forEach(argu -> argu.visit(this, arg));
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        if (stmt.returnExpr != null) stmt.returnExpr.visit(this, arg);
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.cond.visit(this, arg);
        stmt.thenStmt.visit(this, arg);
        if (stmt.thenStmt instanceof VarDeclStmt) throw new IdentificationError("one line scope");
        if (stmt.elseStmt == null) {
            return null;
        }
        stmt.elseStmt.visit(this, arg);
        if (stmt.elseStmt instanceof VarDeclStmt) throw new IdentificationError("one line scope");
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.cond.visit(this, arg);
        stmt.body.visit(this, arg);
        if (stmt.body instanceof VarDeclStmt) throw new IdentificationError("one line scope");
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.operator.visit(this, arg);
        expr.expr.visit(this, arg);
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.operator.visit(this, arg);
        expr.left.visit(this, arg);
        expr.right.visit(this, arg);
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {

        Object visit = expr.ref.visit(this, arg);
        return visit;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.ref.visit(this, arg);
        expr.ixExpr.visit(this, arg);
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        expr.functionRef.visit(this, arg);
        expr.argList.forEach(argu -> argu.visit(this, arg));
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.eltType.visit(this, null);
        expr.sizeExpr.visit(this, arg);
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        MethodDecl md = (MethodDecl) arg;
        if (((MethodDecl) arg).isStatic) _errors.reportError("static method using this keyword");
        ref.decl = md.insideClass;
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.decl = (Declaration) ref.id.visit(this, arg);
        if (ref.id.spelling.equals(refNotUsed)) throw new IdentificationError("used same id in vardecl");
        if (ref.decl instanceof ClassDecl && !Objects.equals(ref.decl.name, ref.id.spelling)) return visitClassDecl((ClassDecl) ref.decl, ref.id.spelling);
        return ref.decl;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        ref.ref.visit(this, arg);
        Declaration context = ref.ref.decl;

        if (context == null) {
            _errors.reportError("referencing without context");
            return null;
        }
        if (context instanceof ClassDecl) {
            ClassDecl cd = (ClassDecl) context;
            MemberDecl decl = (MemberDecl) cd.visit(this, ref.id.spelling);

            if (decl == null) {
                _errors.reportError("failed to find declaration of id in class");
                return null;
            }

            if (((MethodDecl) arg).isStatic && !decl.isStatic) {
                _errors.reportError("static reference to non-static variable");
                return null;
            }

            if (decl.isPrivate && cd != ((MethodDecl) arg).insideClass)
                _errors.reportError("private reference");
            ref.id.decl = decl;
            ref.decl = ref.id.decl;
        } else if (context instanceof LocalDecl || context instanceof MemberDecl) {
            if (Objects.requireNonNull(context.type.typeKind) == TypeKind.CLASS) {
                ClassType ct = (ClassType) context.type;
                ClassDecl cd = (ClassDecl) si.findDeclaration(ct.className.spelling);
                //if (((MethodDecl) arg).isStatic) _errors.reportError("static method using this keyword");
                Declaration d = (Declaration) cd.visit(this, ref.id.spelling);
                if (d == null) {
                    _errors.reportError("reference not found in class");
                    return null;
                }

                if (d instanceof MemberDecl) {
                    MemberDecl md = (MemberDecl) d;
                    if (md.isPrivate && cd != ((MethodDecl) arg).insideClass)
                        _errors.reportError("private reference");
                }

                ref.id.decl = d;
                ref.decl = ref.id.decl;
            } else {
                _errors.reportError("incorrect qualref");
            }
        }else {
            _errors.reportError("incorrect qualref");
        }
        return null;
    }

    @Override
    public Object visitNullRef(NullRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        //if (((MethodDecl) arg).isStatic) _errors.reportError("static method using this keyword");
        return si.findDeclaration(id.spelling);
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        return null;
    }
}
