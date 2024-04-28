package miniJava.CodeGeneration;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.CodeGeneration.x64.*;
import miniJava.CodeGeneration.x64.ISA.*;

import java.util.*;

public class CodeGenerator implements Visitor<Object, Object> {
	private ErrorReporter _errors;
	private InstructionList _asm; // our list of instructions that are used to make the code section
	private int staticVars = 0;
	private int offset = -8;
	private Map<String, List<Instruction>> patch = new HashMap<>();
	private int mainAddr = -1;
	private int printlnAddr;
	public CodeGenerator(ErrorReporter errors) {
		this._errors = errors;
	}
	
	public void parse(Package prog) {
		_asm = new InstructionList();
		//_asm.markOutputStart();
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
		if (mainAddr == -1) _errors.reportError("No main method");
		//_asm.outputFromMark();
		// Output the file "a.out" if no errors
		if( !_errors.hasErrors() )
			makeElf("a.out");
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		// TODO: visit relevant parts of our AST
		printlnAddr = makePrintln();
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
			fd.offset = staticVars*8;
			staticVars++;
		} else {
			fd.offset = ((ClassDecl) arg)._size*8;
			((ClassDecl) arg)._size++;
		}
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		md.instructionAddr = _asm.getSize();
		_asm.add(new Push(Reg64.RBP));
		_asm.add(new Mov_rmr(new R(Reg64.RBP, Reg64.RSP)));
		boolean isMain = md.name.equals("main") &&
				md.isStatic &&
				!md.isPrivate &&
				md.type.typeKind == TypeKind.VOID &&
				md.parameterDeclList.size() == 1 &&
				md.parameterDeclList.get(0).name.equals("args") &&
				md.parameterDeclList.get(0).type instanceof ArrayType &&
				((ArrayType)md.parameterDeclList.get(0).type).eltType.typeKind == TypeKind.UNSUPPORTED /*&&
				((ClassType)((ArrayType)md.parameterDeclList.get(0).type).eltType).className.spelling.equals("String")*/;
		if (isMain) {
			if (mainAddr == -1)
				mainAddr = md.instructionAddr;
			else _errors.reportError("more than one main method");
		}
		md.parameterDeclList.forEach(pd -> pd.visit(this, md));
		md.statementList.forEach(s -> s.visit(this, md));
		if (md.type.typeKind != TypeKind.VOID && (md.statementList.get(md.statementList.size()-1) instanceof ReturnStmt)) _errors.reportError("No return statement for non void method.");
		if (patch.containsKey(md.name)) {
			patch.get(md.name).forEach(instruction -> _asm.patch(instruction.listIdx, new Call(instruction.startAddress, md.instructionAddr)));
			patch.remove(md.name);
		}
		_asm.add(new Mov_rmr(new R(Reg64.RSP, Reg64.RBP)));
		_asm.add(new Pop(Reg64.RBP));
		if (!isMain)
			_asm.add(new Ret());
		else {
			_asm.add(new Mov_rmi(new R(Reg64.RAX, true), 60));
			_asm.add(new Xor(new R(Reg64.RDI, Reg64.RDI)));
			_asm.add(new Syscall());
		}
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		pd.offset = (((MethodDecl) arg).parameterDeclList.size() - ((MethodDecl) arg).args)*8 + (((MethodDecl) arg).isStatic ? 8 : 16);
		((MethodDecl) arg).args++;
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		((MethodDecl)arg).locals++;
		decl.offset = ((MethodDecl)arg).locals * -8;
		_asm.add(new Push(1));
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
		_asm.add(new Mov_rrm(new R(Reg64.R15, Reg64.RBP)));
		stmt.sl.forEach(s -> s.visit(this, arg));
		_asm.add(new Mov_rrm(new R(Reg64.RBP, Reg64.R15)));

		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		stmt.varDecl.visit(this, arg);
		stmt.initExp.visit(this, Boolean.TRUE);
		_asm.add(new Pop(Reg64.RAX));
		//System.out.println(stmt.varDecl.offset);
		_asm.add(new Mov_rmr(new R(Reg64.RBP, stmt.varDecl.offset, Reg64.RAX)));
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		stmt.ref.visit(this, Boolean.TRUE);
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
		for (int i = stmt.argList.size() - 1; i >= 0; i--) {
			//System.out.println(i+1000);
			stmt.argList.get(i).visit(this, Boolean.TRUE);
		}
		if (stmt.methodRef instanceof QualRef) {
			if (!((MethodDecl)stmt.methodRef.decl).isStatic) {
				((QualRef) stmt.methodRef).ref.visit(this, Boolean.TRUE);
				}
			if (((QualRef)stmt.methodRef).id.spelling.equals("println")) {
				//System.out.println("asdasdasdasda");
				_asm.add(new Pop(Reg64.R15));
				_asm.add(new Call(_asm.getSize(), printlnAddr));
			}
			else if (((MethodDecl)((QualRef) stmt.methodRef).id.decl).instructionAddr == -1) {
				Call jmp = new Call(0);
				_asm.add(jmp);
				Object s = patch.containsKey(((QualRef) stmt.methodRef).id.spelling) ?
						patch.get(((QualRef) stmt.methodRef).id.spelling).add(jmp) :
						patch.put(((QualRef) stmt.methodRef).id.spelling, new ArrayList<>(Collections.singletonList(jmp)));

			}else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl)((QualRef) stmt.methodRef).id.decl).instructionAddr));
			}
		} else {
			if (!((MethodDecl)stmt.methodRef.decl).isStatic)
				(stmt.methodRef).visit(this, Boolean.TRUE);
			if (((MethodDecl)(stmt.methodRef).decl).instructionAddr == -1) {
				Call jmp = new Call(0);
				_asm.add(jmp);
				Object s = patch.containsKey((stmt.methodRef).decl.name) ?
						patch.get((stmt.methodRef).decl.name).add(jmp) :
						patch.put((stmt.methodRef).decl.name, new ArrayList<>(Collections.singletonList(jmp)));

			}else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl)(stmt.methodRef).decl).instructionAddr));
			}
		}
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
		expr.ref.visit(this, arg);
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
		for (int i = expr.argList.size() - 1; i > 0; i--) expr.argList.get(i).visit(this, null);
		if (expr.functionRef instanceof QualRef) {
			if (!((MethodDecl)expr.functionRef.decl).isStatic)
				((QualRef) expr.functionRef).ref.visit(this, Boolean.TRUE);
			if (((QualRef)expr.functionRef).id.spelling.equals("println"))
				_asm.add(new Call(_asm.getSize(), printlnAddr));
			else if (((MethodDecl)((QualRef) expr.functionRef).id.decl).instructionAddr == -1) {
				Call jmp = new Call(0);
				_asm.add(jmp);
				Object s = patch.containsKey(((QualRef) expr.functionRef).id.spelling) ?
						patch.get(((QualRef) expr.functionRef).id.spelling).add(jmp) :
						patch.put(((QualRef) expr.functionRef).id.spelling, new ArrayList<>(Collections.singletonList(jmp)));

			}else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl)((QualRef) expr.functionRef).id.decl).instructionAddr));
			}
		} else {
			if (!((MethodDecl)expr.functionRef.decl).isStatic)
				(expr.functionRef).visit(this, Boolean.TRUE);
			if (((MethodDecl)(expr.functionRef).decl).instructionAddr == -1) {
				Call jmp = new Call(0);
				_asm.add(jmp);
				Object s = patch.containsKey((expr.functionRef).decl.name) ?
						patch.get((expr.functionRef).decl.name).add(jmp) :
						patch.put((expr.functionRef).decl.name, new ArrayList<>(Collections.singletonList(jmp)));

			}else {
				_asm.add(new Call(_asm.getSize(), ((MethodDecl)(expr.functionRef).decl).instructionAddr));
			}
		}
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
		//System.out.print(ref.decl.getClass() + " and ");
		//System.out.println(arg);
		if(ref.decl instanceof LocalDecl) {
			//System.out.println(1);
			LocalDecl ld = (LocalDecl) ref.decl;
			if(arg instanceof Boolean) {
				//System.out.println(2);
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
		ref.ref.visit(this, Boolean.TRUE);
		_asm.add(new Pop(Reg64.RAX));
		_asm.add(new Add(new R(Reg64.RAX, true), ((FieldDecl)ref.id.decl).offset));
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
		elf.outputELF(fname, _asm.getBytes(), mainAddr); // TODO: set the location of the main method
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
		int addr = _asm.add(new Mov_rmi(new R(Reg64.RAX, true), 1));
		_asm.add(new Mov_rmi(new R(Reg64.RDI, true), 1));
		_asm.add(new Mov_rrm(new R(Reg64.R15, Reg64.RSI)));
		_asm.add(new Mov_rmi(new R(Reg64.RDX, true), 1));
		_asm.add(new Syscall());
//		_asm.add(new Mov_rmi(new R(Reg64.R14, true), 10));
//		_asm.add(new Mov_rmr(new R(Reg64.R15, 0, Reg64.R14)));
//		_asm.add(new Syscall());
		_asm.add(new Ret());
		return addr;
	}

}
