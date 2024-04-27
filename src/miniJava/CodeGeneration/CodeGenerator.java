package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;

import java.lang.reflect.Method;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	private int staticVars = 0;
	private int offset = -8;
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		
		// If you haven't refactored the name "ModRMSIB" to something like "R",
		//  go ahead and do that now. You'll be needing that object a lot.
		// Here is some example code.
		
		// Simple operations:
		// _asm.add( new Push(0) ); // push the value zero onto the stack
		// _asm.add( new Pop(Reg64.RCX) ); // pop the top of the stack into RCX
		
		// Fancier operations:
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,Reg64.RDI)) ); // cmp rcx,rdi
		// _asm.add( new Cmp(new ModRMSIB(Reg64.RCX,0x10,Reg64.RDI)) ); // cmp [rcx+0x10],rdi
		// _asm.add( new Add(new ModRMSIB(Reg64.RSI,Reg64.RCX,4,0x1000,Reg64.RDX)) ); // add [rsi+rcx*4+0x1000],rdx
		
		// Thus:
		// new ModRMSIB( ... ) where the "..." can be:
		//  RegRM, RegR						== rm, r
		//  RegRM, int, RegR				== [rm+int], r
		//  RegRD, RegRI, intM, intD, RegR	== [rd+ ri*intM + intD], r
		// Where RegRM/RD/RI are just Reg64 or Reg32 or even Reg8
		//
		// Note there are constructors for ModRMSIB where RegR is skipped.
		// This is usually used by instructions that only need one register operand, and often have an immediate
		//   So they actually will set RegR for us when we create the instruction. An example is:
		// _asm.add( new Mov_rmi(new ModRMSIB(Reg64.RDX,true), 3) ); // mov rdx,3
		//   In that last example, we had to pass in a "true" to indicate whether the passed register
		//    is the operand RM or R, in this case, true means RM
		//  Similarly:
		// _asm.add( new Push(new ModRMSIB(Reg64.RBP,16)) );
		//   This one doesn't specify RegR because it is: push [rbp+16] and there is no second operand register needed
		
		// Patching example:
		// Instruction someJump = new Jmp((int)0); // 32-bit offset jump to nowhere
		// _asm.add( someJump ); // populate listIdx and startAddress for the instruction
		// ...
		// ... visit some code that probably uses _asm.add
		// ...
		// patch method 1: calculate the offset yourself
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size() - someJump.startAddress - 5) );
		// -=-=-=-
		// patch method 2: let the jmp calculate the offset
		//  Note the false means that it is a 32-bit immediate for jumping (an int)
		//     _asm.patch( someJump.listIdx, new Jmp(asm.size(), someJump.startAddress, false) );
		
		prog.visit(this,null);
		
		// Output the file "a.out" if no errors
		if( !_errors.hasErrors() )
			makeElf("a.out");
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		// TODO: visit relevant parts of our AST
		prog.classDeclList.forEach(cd -> cd.visit(this, null));
		return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		cd.fieldDeclList.forEach(fd -> fd.visit(this, cd));
		cd.methodDeclList.forEach(md -> md.visit(this, cd));
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		if (fd.isStatic) {
			fd.offset = staticVars;
			staticVars++;
		} else {
			fd.offset = ((ClassDecl) arg)._size;
			((ClassDecl) arg)._size++;
		}
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		md.instructionNum = _asm.getSize();
		md.parameterDeclList.forEach(pd -> pd.visit(this, md));
		md.statementList.forEach(s -> s.visit(this, md));

		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		pd.offset = ((MethodDecl) arg).parameterDeclList.size() - ((MethodDecl) arg).args;
		((MethodDecl) arg).args++;
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		decl.offset = offset;
		_asm.add(new Push(0));
		offset -= 8;
		return null;
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
		int og = ((MethodDecl) arg).stackSize;
		stmt.sl.forEach(s -> s.visit(this, arg));
		int local = ((MethodDecl)arg).stackSize - og;
		for (int i = local; i > 0; i--)
			_asm.add(new Pop((Reg64) Reg64.RegFromIdx(i, true)));

		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		stmt.varDecl.visit(this, null);
		stmt.initExp.visit(this, null);
		_asm.add(new Pop(Reg64.RAX));
		_asm.add(new Mov_rmr(new R(Reg64.RBP, stmt.varDecl.offset, Reg64.RAX)));
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		stmt.ref.visit(this, true);
		stmt.val.visit(this, null);
		_asm.add(new Pop(Reg64.RCX));
		_asm.add(new Pop(Reg64.RAX));

		_asm.add(new Mov_rmr(new R(Reg64.RAX, 0, Reg64.RCX)));
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		stmt.ref.visit(this, null);
		stmt.exp.visit(this, null);
		stmt.ix.visit(this, null);
		_asm.add(new Pop(Reg64.RCX));
		_asm.add(new Pop(Reg64.RBX));
		_asm.add(new Pop(Reg64.RAX));


		_asm.add(new Mov_rmr(new R(Reg64.RAX, Reg64.RCX, 8, 0, Reg64.RBX)));
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		stmt.argList.forEach(s -> s.visit(this, null));
		stmt.methodRef.visit(this, null);
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		_asm.add(new Pop(Reg64.RAX));
		_asm.add(new Cmp(new R(Reg64.RAX, false), 0));
		int currAddr = _asm.getSize();
		int toPatch = _asm.add(new CondJmp(Condition.E, 0));
		stmt.thenStmt.visit(this, null);

		if (stmt.elseStmt != null) {
			int currAddrJmp = _asm.getSize();
			int toPatchJump = _asm.add(new Jmp(0));
			stmt.elseStmt.visit(this, null);
			_asm.patch(toPatchJump, new Jmp(currAddrJmp, _asm.getSize(), false));
		}
		_asm.patch(toPatch, new CondJmp(Condition.E, currAddr, _asm.getSize(), false));

		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		_asm.add(new Pop(Reg64.RAX));
		int currAddr = _asm.getSize();
		int toPatch = _asm.add(new Cmp(new R(Reg64.RAX, false), 0));
		stmt.body.visit(this, null);
		_asm.patch(toPatch, new CondJmp(Condition.E, currAddr, _asm.getSize(), false));
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		Operator op = expr.operator;
		expr.expr.visit(this, null);
		_asm.add(new Pop(Reg64.RAX));
		switch (op.spelling) {
			case "!":
				_asm.add(new Not(new R(Reg64.RAX, false)));
				break;
			case "-":
				_asm.add(new Neg(new R(Reg64.RAX, false)));
				break;
		}
		_asm.add(new Push(Reg64.RAX));
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		Operator op = expr.operator;
		expr.left.visit(this, null);
		expr.right.visit(this, null);
		_asm.add(new Pop(Reg64.RCX)); //puts right in rcx
		_asm.add(new Pop(Reg64.RAX)); //puts left in rax

        switch (op.spelling) {
            case "+":
                _asm.add(new Add(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            case "-":
                _asm.add(new Sub(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            case "*":
                _asm.add(new Imul(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            case "/":
                _asm.add(new Idiv(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            case "||":
                _asm.add(new Or(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            case "&&":
                _asm.add(new And(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new Push(Reg64.RAX));
                break;
            default:
                _asm.add(new Xor(new R(Reg64.RBX, Reg64.RBX)));

                _asm.add(new Cmp(new R(Reg64.RAX, Reg64.RCX)));
                _asm.add(new SetCond(Condition.getCond(op), Reg8.DL));

                _asm.add(new Push(Reg64.RDX));
                break;
        }
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		expr.ref.visit(this, null);
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		expr.ref.visit(this, null);
		expr.ixExpr.visit(this, null);
		_asm.add(new Pop(Reg64.RCX));
		_asm.add(new Pop(Reg64.RAX));
		_asm.add(new Mov_rrm(new R(Reg64.RAX, Reg64.RCX, 8, 0, Reg64.RAX)));
		_asm.add(new Push(Reg64.RAX));
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		expr.functionRef.decl.visit(this, null);
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		expr.lit.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		_asm.add(new Push(0));
		makeMalloc();
		_asm.add(new Mov_rmr(new R(Reg64.RBP, -8, Reg64.RAX)));
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		_asm.add(new Push(0));
		makeMalloc();
		_asm.add(new Mov_rmr(new R(Reg64.RBP, -8, Reg64.RAX)));
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		_asm.add(new Push(new R(Reg64.RBP, 16)));
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		if(ref.id.decl instanceof LocalDecl) {
			LocalDecl ld = (LocalDecl) ref.id.decl;
			if(arg instanceof Boolean) {
				_asm.add(new Lea(new R(Reg64.RBP, ld.offset, Reg64.RAX)));
				_asm.add(new Push(Reg64.RAX));
			} else {
				_asm.add(new Push(new R(Reg64.RBP, ld.offset)));
			}
		} else if(ref.id.decl instanceof FieldDecl) {
			FieldDecl fD = (FieldDecl) ref.id.decl;
			_asm.add(new Mov_rmr(new R(Reg64.RBP, 16, Reg64.RAX)));
			if(arg instanceof Boolean) {
				_asm.add(new Lea(new R(Reg64.RAX, fD.offset, Reg64.RAX)));
				_asm.add(new Push(Reg64.RAX));
			} else {
				_asm.add(new Push(new R(Reg64.RAX, fD.offset)));
			}
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		ref.ref.visit(this, true);
		_asm.add(new Pop(Reg64.RAX));
		_asm.add(new Add(new R(Reg64.RAX, false), ((FieldDecl)ref.id.decl).offset));
		_asm.add(new Push(Reg64.RAX));
		return null;
	}

	@Override
	public Object visitNullRef(NullRef ref, Object arg) {
		_asm.add(new Push(0));
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		return null;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		_asm.add(new Push(Integer.parseInt(num.spelling)));
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		_asm.add(new Push(bool.spelling.equals("true") ? 1 : 0));
		return null;
	}

	public void makeElf(String fname) {
		ELFMaker elf = new ELFMaker(_errors, _asm.getSize(), 8); // bss ignored until PA5, set to 8
		elf.outputELF(fname, _asm.getBytes(), ??); // TODO: set the location of the main method
	}
	
	private int makeMalloc() {
		int idxStart = _asm.add( new Mov_rmi(new R(Reg64.RAX,true),0x09) ); // mmap
		
		_asm.add( new Xor(		new R(Reg64.RDI,Reg64.RDI)) 	); // addr=0
		_asm.add( new Mov_rmi(	new R(Reg64.RSI,true),0x1000) ); // 4kb alloc
		_asm.add( new Mov_rmi(	new R(Reg64.RDX,true),0x03) 	); // prot read|write
		_asm.add( new Mov_rmi(	new R(Reg64.R10,true),0x22) 	); // flags= private, anonymous
		_asm.add( new Mov_rmi(	new R(Reg64.R8, true),-1) 	); // fd= -1
		_asm.add( new Xor(		new R(Reg64.R9,Reg64.R9)) 	); // offset=0
		_asm.add( new Syscall() );
		
		// pointer to newly allocated memory is in RAX
		// return the index of the first instruction in this method, if needed
		return idxStart;
	}
	
	private int makePrintln() {
		// TODO: how can we generate the assembly to println?
		return -1;
	}
}
