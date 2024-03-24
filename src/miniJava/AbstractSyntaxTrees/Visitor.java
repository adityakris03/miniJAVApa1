/**
 * miniJava Abstract Syntax Tree classes
 *
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

/**
 * An implementation of the Visitor interface provides a method visitX
 * for each non-abstract AST class X.  
 */
public interface Visitor<ArgType, ResultType> {

    // Package
    ResultType visitPackage(Package prog, ArgType arg);

    // Declarations
    ResultType visitClassDecl(ClassDecl cd, ArgType arg);

    ResultType visitFieldDecl(FieldDecl fd, ArgType arg);

    ResultType visitMethodDecl(MethodDecl md, ArgType arg);

    ResultType visitParameterDecl(ParameterDecl pd, ArgType arg);

    ResultType visitVarDecl(VarDecl decl, ArgType arg);

    // Types
    ResultType visitBaseType(BaseType type, ArgType arg);

    ResultType visitClassType(ClassType type, ArgType arg);

    ResultType visitArrayType(ArrayType type, ArgType arg);

    // Statements
    ResultType visitBlockStmt(BlockStmt stmt, ArgType arg);

    ResultType visitVardeclStmt(VarDeclStmt stmt, ArgType arg);

    ResultType visitAssignStmt(AssignStmt stmt, ArgType arg);

    ResultType visitIxAssignStmt(IxAssignStmt stmt, ArgType arg);

    ResultType visitCallStmt(CallStmt stmt, ArgType arg);

    ResultType visitReturnStmt(ReturnStmt stmt, ArgType arg);

    ResultType visitIfStmt(IfStmt stmt, ArgType arg);

    ResultType visitWhileStmt(WhileStmt stmt, ArgType arg);

    // Expressions
    ResultType visitUnaryExpr(UnaryExpr expr, ArgType arg);

    ResultType visitBinaryExpr(BinaryExpr expr, ArgType arg);

    ResultType visitRefExpr(RefExpr expr, ArgType arg);

    ResultType visitIxExpr(IxExpr expr, ArgType arg);

    ResultType visitCallExpr(CallExpr expr, ArgType arg);

    ResultType visitLiteralExpr(LiteralExpr expr, ArgType arg);

    ResultType visitNewObjectExpr(NewObjectExpr expr, ArgType arg);

    ResultType visitNewArrayExpr(NewArrayExpr expr, ArgType arg);

    // References
    ResultType visitThisRef(ThisRef ref, ArgType arg);

    ResultType visitIdRef(IdRef ref, ArgType arg);

    ResultType visitQRef(QualRef ref, ArgType arg);

    ResultType visitNullRef(NullRef ref, ArgType arg);

    // Terminals
    ResultType visitIdentifier(Identifier id, ArgType arg);

    ResultType visitOperator(Operator op, ArgType arg);

    ResultType visitIntLiteral(IntLiteral num, ArgType arg);

    ResultType visitBooleanLiteral(BooleanLiteral bool, ArgType arg);
}
