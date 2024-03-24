package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

import java.util.HashMap;
import java.util.Stack;

public class ScopedIdentification {


    Stack<HashMap<String, Declaration>> _table;

    public ScopedIdentification() {
        _table = new Stack<>();
        openScope();
        addSystem();
        addPrintStream();
        addString();
    }

    public void openScope() {
        _table.push(new HashMap<>());
    }

    public void closeScope() {
        if (_table.size() >= 2 && _table.peek().size() == 1) throw new IdentificationError("no dec in own scope");
        _table.pop();
    }

    public void addDeclaration(Declaration decl) {
        //Iterator<HashMap<String, Declaration>> iter = _table.iterator();
        for (int i = _table.size() - 1; i >= 2; i--)
            if (_table.get(i).containsKey(decl.name)) throw new IdentificationError("Variable already declared");
        _table.peek().put(decl.name, decl);
    }

    public Declaration findDeclaration(String name) {
        for (HashMap<String, Declaration> map : _table) {
            if (map.containsKey(name)) return map.get(name);
        }
        throw new IdentificationError("Declaration not found");
    }


    private void addSystem() {
        FieldDeclList fdl = new FieldDeclList();
        fdl.add(new FieldDecl(false, true, new ClassType(
                new Identifier(
                        new Token(TokenType.ID, "_PrintStream")), null), "out", null));
        MethodDeclList mdl = new MethodDeclList();
        addDeclaration(new ClassDecl("System", fdl, mdl, null));
    }

    private void addPrintStream() {
        FieldDeclList fdl = new FieldDeclList();
        MethodDeclList mdl = new MethodDeclList();
        ParameterDeclList pdl = new ParameterDeclList();
        pdl.add(new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null));
        mdl.add(new MethodDecl(new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "out", null), pdl, new StatementList(), null));
        addDeclaration(new ClassDecl("_PrintStream", fdl, mdl, null));
    }

    private void addString() {
        addDeclaration(new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null));
    }
}

class IdentificationError extends RuntimeException {

    public IdentificationError(String message) {
        super(message);
    }
}
