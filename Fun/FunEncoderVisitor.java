//////////////////////////////////////////////////////////////
//
// A visitor for code generation for Fun.
//
// Developed August 2015 by Simon Gay (University of Glasgow).
//
// Based on a previous version by David Watt.
//
// Updated November 2016 by Gabriela Georgieva,
// student at UofG, matr num: 2130120
//
//////////////////////////////////////////////////////////////

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.*;

import java.util.*;

public class FunEncoderVisitor extends AbstractParseTreeVisitor<Void> implements FunVisitor<Void> {

	private SVM obj = new SVM();

	private int globalvaraddr = 0;
	private int localvaraddr = 0;
	private int currentLocale = Address.GLOBAL;

	private SymbolTable<Address> addrTable = new SymbolTable<Address>();

	private void predefine () {
	// Add predefined procedures to the address table.
		addrTable.put("read", new Address(SVM.READOFFSET, Address.CODE));
		addrTable.put("write", new Address(SVM.WRITEOFFSET, Address.CODE));
	}

	public SVM getSVM() {
	    return obj;
	}

	/**
	 * Visit a parse tree produced by the {@code prog}
	 * labeled alternative in {@link FunParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProg(FunParser.ProgContext ctx) {
    predefine();
    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
    for (FunParser.Var_declContext vd : var_decl) {
			visit(vd);
		}
    int calladdr = obj.currentOffset();
    obj.emit12(SVM.CALL, 0);
    obj.emit1(SVM.HALT);
    List<FunParser.Proc_declContext> proc_decl = ctx.proc_decl();
    for (FunParser.Proc_declContext pd : proc_decl) {
			visit(pd);
		}
    int mainaddr = addrTable.get("main").offset;
    obj.patch12(calladdr, mainaddr);
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proc}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProc(FunParser.ProcContext ctx) {
    String id = ctx.ID().getText();
    Address procaddr = new Address(obj.currentOffset(), Address.CODE);
    addrTable.put(id, procaddr);
    addrTable.enterLocalScope();
    currentLocale = Address.LOCAL;
    localvaraddr = 2;
    // ... allows 2 words for link data
    FunParser.Formal_declContext fd = ctx.formal_decl();
    if (fd != null) {
			visit(fd);
		}
    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
    for (FunParser.Var_declContext vd : var_decl) {
			visit(vd);
		}
    visit(ctx.seq_com());
    obj.emit11(SVM.RETURN, 0);
    addrTable.exitLocalScope();
    currentLocale = Address.GLOBAL;
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code func}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFunc(FunParser.FuncContext ctx) {
    String id = ctx.ID().getText();
    Address procaddr = new Address(obj.currentOffset(), Address.CODE);
    addrTable.put(id, procaddr);
    addrTable.enterLocalScope();
    currentLocale = Address.LOCAL;
    localvaraddr = 2;
    // ... allows 2 words for link data
    FunParser.Formal_declContext fd = ctx.formal_decl();
    if (fd != null) {
			visit(fd);
		}
    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
    for (FunParser.Var_declContext vd : var_decl) {
			visit(vd);
		}
    visit(ctx.seq_com());
    visit(ctx.expr());
    obj.emit11(SVM.RETURN, 1);
    addrTable.exitLocalScope();
    currentLocale = Address.GLOBAL;
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code formal}
	 * labeled alternative in {@link FunParser#formal_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFormal(FunParser.FormalContext ctx) {
    FunParser.TypeContext tc = ctx.type();
    if (tc != null) {
			String id = ctx.ID().getText();
			addrTable.put(id, new Address(localvaraddr++, Address.LOCAL));
			obj.emit11(SVM.COPYARG, 1);
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code var}
	 * labeled alternative in {@link FunParser#var_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitVar(FunParser.VarContext ctx) {
    visit(ctx.expr());
    String id = ctx.ID().getText();
    switch (currentLocale) {
    	case Address.LOCAL:
				addrTable.put(id, new Address(localvaraddr++, Address.LOCAL));
				break;
    	case Address.GLOBAL:
				addrTable.put(id, new Address(globalvaraddr++, Address.GLOBAL));
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code bool}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitBool(FunParser.BoolContext ctx) {
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code int}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitInt(FunParser.IntContext ctx) {
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code assn}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitAssn(FunParser.AssnContext ctx) {
    visit(ctx.expr());
    String id = ctx.ID().getText();
    Address varaddr = addrTable.get(id);
    switch (varaddr.locale) {
    	case Address.GLOBAL:
				obj.emit12(SVM.STOREG,varaddr.offset);
				break;
    	case Address.LOCAL:
				obj.emit12(SVM.STOREL,varaddr.offset);
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proccall}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProccall(FunParser.ProccallContext ctx) {
    visit(ctx.actual());
    String id = ctx.ID().getText();
    Address procaddr = addrTable.get(id);
    // Assume procaddr.locale == CODE.
    obj.emit12(SVM.CALL,procaddr.offset);
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code if}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitIf(FunParser.IfContext ctx) {
    visit(ctx.expr());
    int condaddr = obj.currentOffset();
    obj.emit12(SVM.JUMPF, 0);
    if (ctx.c2 == null) { // IF without ELSE
			visit(ctx.c1);
			int exitaddr = obj.currentOffset();
			obj.patch12(condaddr, exitaddr);
    } else {                // IF ... ELSE
			visit(ctx.c1);
			int jumpaddr = obj.currentOffset();
			obj.emit12(SVM.JUMP, 0);
			int elseaddr = obj.currentOffset();
			obj.patch12(condaddr, elseaddr);
			visit(ctx.c2);
			int exitaddr = obj.currentOffset();
			obj.patch12(jumpaddr, exitaddr);
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code while}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitWhile(FunParser.WhileContext ctx) {
    int startaddr = obj.currentOffset();
    visit(ctx.expr());
    int condaddr = obj.currentOffset();
    obj.emit12(SVM.JUMPF, 0);
    visit(ctx.seq_com());
    obj.emit12(SVM.JUMP, startaddr);
    int exitaddr = obj.currentOffset();
    obj.patch12(condaddr, exitaddr);
    return null;
	}

	/** SWITCH statement                                                              // EXTENSION
	------------------CODE TEMPLATE-------------------------
	 -            code to evaluate expr
	 - (top)      code to evaluate a guard
	 -            JUMPF to next
	 -            sequence of commands to be executed
	 -            JUMP end
	 - (next)     get next guard
	 -            JUMPF to default
	 -            JUMP to top
	 - (default)  sequence of commands to be executed
	 - (end)      done
	 ------------------------------------------------------
	 */

	/**
	 * Visit a parse tree produced by the {@code switch}                               // EXTENSION
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitSwitch(FunParser.SwitchContext ctx) {

		List<FunParser.GuardContext> guards = ctx.guard();
		List<FunParser.Seq_comContext> seq_coms = ctx.seq_com();
		List<Integer> addresses = new ArrayList<Integer>();

		for (int i = 0; i < guards.size(); i++){
			visit(ctx.expr());
			visit(guards.get(i));
			int guardaddr = obj.currentOffset();
			obj.emit12(SVM.JUMPF, 0);
			visit(seq_coms.get(i));
			int condaddr = obj.currentOffset();
			addresses.add(condaddr);
			obj.emit12(SVM.JUMP, 0);
			int nextguardaddr = obj.currentOffset();
			obj.patch12(guardaddr, nextguardaddr);
		}

		visit(seq_coms.get(seq_coms.size() - 1));
		int exitaddr = obj.currentOffset();
		for(int address : addresses) {
			obj.patch12(address, exitaddr);
		}
		return null;
	}

	/**
	 * Visit a parse tree produced by the {@code seq}
	 * labeled alternative in {@link FunParser#seq_com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitSeq(FunParser.SeqContext ctx) {
	  visitChildren(ctx);
	  return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitExpr(FunParser.ExprContext ctx) {
    visit(ctx.e1);
    if (ctx.e2 != null) {
			visit(ctx.e2);
			switch (ctx.op.getType()) {
				case FunParser.EQ:
	    		obj.emit1(SVM.CMPEQ);
	    		break;
				case FunParser.LT:
	    		obj.emit1(SVM.CMPLT);
	    		break;
				case FunParser.GT:
	    		obj.emit1(SVM.CMPGT);
	    		break;
			}
  	}
    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#sec_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitSec_expr(FunParser.Sec_exprContext ctx) {
    visit(ctx.e1);
    if (ctx.e2 != null) {
			visit(ctx.e2);
			switch (ctx.op.getType()) {
				case FunParser.PLUS:
	    		obj.emit1(SVM.ADD);
	    		break;
				case FunParser.MINUS:
	    		obj.emit1(SVM.SUB);
	    		break;
				case FunParser.TIMES:
	    		obj.emit1(SVM.MUL);
	    		break;
				case FunParser.DIV:
	    		obj.emit1(SVM.DIV);
	    		break;
			}
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code false}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFalse(FunParser.FalseContext ctx) {
	  obj.emit12(SVM.LOADC, 0);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code true}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitTrue(FunParser.TrueContext ctx) {
	  obj.emit12(SVM.LOADC, 1);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code num}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitNum(FunParser.NumContext ctx) {
	  int value = Integer.parseInt(ctx.NUM().getText());
	  obj.emit12(SVM.LOADC, value);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code id}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitId(FunParser.IdContext ctx) {
    String id = ctx.ID().getText();
    Address varaddr = addrTable.get(id);
    switch (varaddr.locale) {
    	case Address.GLOBAL:
				obj.emit12(SVM.LOADG,varaddr.offset);
				break;
    	case Address.LOCAL:
				obj.emit12(SVM.LOADL,varaddr.offset);
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code funccall}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFunccall(FunParser.FunccallContext ctx) {
    visit(ctx.actual());
    String id = ctx.ID().getText();
    Address funcaddr = addrTable.get(id);
    // Assume that funcaddr.locale == CODE.
    obj.emit12(SVM.CALL,funcaddr.offset);
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code not}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitNot(FunParser.NotContext ctx) {
    visit(ctx.prim_expr());
    obj.emit1(SVM.INV);
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitParens(FunParser.ParensContext ctx) {
    visit(ctx.expr());
    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#actual}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitActual(FunParser.ActualContext ctx) {
    FunParser.ExprContext ec = ctx.expr();
    if (ec != null) {
			visit(ec);
    }
    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code falseg}
	 * labeled alternative in {@link FunParser#guard}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFalseg(FunParser.FalsegContext ctx) {                           // EXTENSION
	  obj.emit12(SVM.LOADC, 0);
		obj.emit1(SVM.COMPG);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code trueg}
	 * labeled alternative in {@link FunParser#guard}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitTrueg(FunParser.TruegContext ctx) {                              // EXTENSION
	  obj.emit12(SVM.LOADC, 1);
		obj.emit1(SVM.COMPG);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code numg}
	 * labeled alternative in {@link FunParser#guard}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitNumg(FunParser.NumgContext ctx) {                                  // EXTENSION
	  int value = Integer.parseInt(ctx.NUM().getText());
	  obj.emit12(SVM.LOADC, value);
		obj.emit1(SVM.COMPG);
	  return null;
	}

	/**
	 * Visit a parse tree produced by the {@code rangeg}
	 * labeled alternative in {@link FunParser#guard}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitRangeg(FunParser.RangegContext ctx) {                                // EXTENSION
	  int i = Integer.parseInt(ctx.NUM(0).getText());
		int j = Integer.parseInt(ctx.NUM(1).getText());
		obj.emit12(SVM.LOADC, i);
		obj.emit12(SVM.LOADC, j);
		obj.emit1(SVM.LOADR);
	  return null;
	}
}
