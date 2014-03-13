/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.modules.decompiler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.fernflower.code.CodeConstants;
import de.fernflower.code.Instruction;
import de.fernflower.code.InstructionSequence;
import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.decompiler.exps.ArrayExprent;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.ExitExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FieldExprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.IfExprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.MonitorExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.exps.SwitchExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.sforms.DirectNode;
import de.fernflower.modules.decompiler.sforms.FlattenStatementsHelper;
import de.fernflower.modules.decompiler.sforms.FlattenStatementsHelper.FinallyPathWrapper;
import de.fernflower.modules.decompiler.stats.BasicBlockStatement;
import de.fernflower.modules.decompiler.stats.CatchAllStatement;
import de.fernflower.modules.decompiler.stats.CatchStatement;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.consts.ConstantPool;
import de.fernflower.struct.consts.PrimitiveConstant;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class ExprProcessor implements CodeConstants {

	public static final String UNDEFINED_TYPE_STRING = "<undefinedtype>";
	public static final String UNKNOWN_TYPE_STRING = "<unknown>";
	public static final String NULL_TYPE_STRING = "<null>";

	private static final HashMap<Integer, Integer> mapConsts = new HashMap<Integer, Integer>();

	static {

		// mapConsts.put(new Integer(opc_i2l), new
		// Integer(FunctionExprent.FUNCTION_I2L));
		// mapConsts.put(new Integer(opc_i2f), new
		// Integer(FunctionExprent.FUNCTION_I2F));
		// mapConsts.put(new Integer(opc_i2d), new
		// Integer(FunctionExprent.FUNCTION_I2D));
		// mapConsts.put(new Integer(opc_l2i), new
		// Integer(FunctionExprent.FUNCTION_L2I));
		// mapConsts.put(new Integer(opc_l2f), new
		// Integer(FunctionExprent.FUNCTION_L2F));
		// mapConsts.put(new Integer(opc_l2d), new
		// Integer(FunctionExprent.FUNCTION_L2D));
		// mapConsts.put(new Integer(opc_f2i), new
		// Integer(FunctionExprent.FUNCTION_F2I));
		// mapConsts.put(new Integer(opc_f2l), new
		// Integer(FunctionExprent.FUNCTION_F2L));
		// mapConsts.put(new Integer(opc_f2d), new
		// Integer(FunctionExprent.FUNCTION_F2D));
		// mapConsts.put(new Integer(opc_d2i), new
		// Integer(FunctionExprent.FUNCTION_D2I));
		// mapConsts.put(new Integer(opc_d2l), new
		// Integer(FunctionExprent.FUNCTION_D2L));
		// mapConsts.put(new Integer(opc_d2f), new
		// Integer(FunctionExprent.FUNCTION_D2F));
		// mapConsts.put(new Integer(opc_i2b), new
		// Integer(FunctionExprent.FUNCTION_I2B));
		// mapConsts.put(new Integer(opc_i2c), new
		// Integer(FunctionExprent.FUNCTION_I2C));
		// mapConsts.put(new Integer(opc_i2s), new
		// Integer(FunctionExprent.FUNCTION_I2S));

		mapConsts.put(new Integer(opc_arraylength), new Integer(FunctionExprent.FUNCTION_ARRAYLENGTH));
		mapConsts.put(new Integer(opc_checkcast), new Integer(FunctionExprent.FUNCTION_CAST));
		mapConsts.put(new Integer(opc_instanceof), new Integer(FunctionExprent.FUNCTION_INSTANCEOF));

	}

	private static final VarType[] consts = new VarType[] { VarType.VARTYPE_INT, VarType.VARTYPE_FLOAT, VarType.VARTYPE_LONG, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_CLASS,
			VarType.VARTYPE_STRING };

	private static final VarType[] vartypes = new VarType[] { VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT };

	private static final VarType[] arrtypes = new VarType[] { VarType.VARTYPE_INT, VarType.VARTYPE_LONG, VarType.VARTYPE_FLOAT, VarType.VARTYPE_DOUBLE, VarType.VARTYPE_OBJECT,
			VarType.VARTYPE_BOOLEAN, VarType.VARTYPE_CHAR, VarType.VARTYPE_SHORT };

	private static final int[] func1 = new int[] { FunctionExprent.FUNCTION_ADD, FunctionExprent.FUNCTION_SUB, FunctionExprent.FUNCTION_MUL, FunctionExprent.FUNCTION_DIV,
			FunctionExprent.FUNCTION_REM };

	private static final int[] func2 = new int[] { FunctionExprent.FUNCTION_SHL, FunctionExprent.FUNCTION_SHR, FunctionExprent.FUNCTION_USHR, FunctionExprent.FUNCTION_AND,
			FunctionExprent.FUNCTION_OR, FunctionExprent.FUNCTION_XOR };

	private static final int[] func3 = new int[] { FunctionExprent.FUNCTION_I2L, FunctionExprent.FUNCTION_I2F, FunctionExprent.FUNCTION_I2D, FunctionExprent.FUNCTION_L2I,
			FunctionExprent.FUNCTION_L2F, FunctionExprent.FUNCTION_L2D, FunctionExprent.FUNCTION_F2I, FunctionExprent.FUNCTION_F2L, FunctionExprent.FUNCTION_F2D,
			FunctionExprent.FUNCTION_D2I, FunctionExprent.FUNCTION_D2L, FunctionExprent.FUNCTION_D2F, FunctionExprent.FUNCTION_I2B, FunctionExprent.FUNCTION_I2C,
			FunctionExprent.FUNCTION_I2S };

	private static final int[] func4 = new int[] { FunctionExprent.FUNCTION_LCMP, FunctionExprent.FUNCTION_FCMPL, FunctionExprent.FUNCTION_FCMPG, FunctionExprent.FUNCTION_DCMPL,
			FunctionExprent.FUNCTION_DCMPG };

	private static final int[] func5 = new int[] { IfExprent.IF_EQ, IfExprent.IF_NE, IfExprent.IF_LT, IfExprent.IF_GE, IfExprent.IF_GT, IfExprent.IF_LE };

	private static final int[] func6 = new int[] { IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPGT, IfExprent.IF_ICMPLE,
			IfExprent.IF_ACMPEQ, IfExprent.IF_ACMPNE };

	private static final int[] func7 = new int[] { IfExprent.IF_NULL, IfExprent.IF_NONNULL };

	private static final int[] func8 = new int[] { MonitorExprent.MONITOR_ENTER, MonitorExprent.MONITOR_EXIT };

	private static final int[] arr_type = new int[] { CodeConstants.TYPE_BOOLEAN, CodeConstants.TYPE_CHAR, CodeConstants.TYPE_FLOAT, CodeConstants.TYPE_DOUBLE,
			CodeConstants.TYPE_BYTE, CodeConstants.TYPE_SHORT, CodeConstants.TYPE_INT, CodeConstants.TYPE_LONG };

	private static final int[] negifs = new int[] { IfExprent.IF_NE, IfExprent.IF_EQ, IfExprent.IF_GE, IfExprent.IF_LT, IfExprent.IF_LE, IfExprent.IF_GT, IfExprent.IF_NONNULL,
			IfExprent.IF_NULL, IfExprent.IF_ICMPNE, IfExprent.IF_ICMPEQ, IfExprent.IF_ICMPGE, IfExprent.IF_ICMPLT, IfExprent.IF_ICMPLE, IfExprent.IF_ICMPGT, IfExprent.IF_ACMPNE,
			IfExprent.IF_ACMPEQ };

	private static final String[] typeNames = new String[] { "byte", "char", "double", "float", "int", "long", "short", "boolean", };

	private VarProcessor varProcessor = (VarProcessor) DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR);

	public void processStatement(RootStatement root, ConstantPool pool) {

		FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
		DirectGraph dgraph = flatthelper.buildDirectGraph(root);

		// try {
		// DotExporter.toDotFile(dgraph, new File("c:\\Temp\\gr12_my.dot"));
		// } catch(Exception ex) {ex.printStackTrace();}

		// collect finally entry points
		Set<String> setFinallyShortRangeEntryPoints = new HashSet<String>();
		for (List<FinallyPathWrapper> lst : dgraph.mapShortRangeFinallyPaths.values()) {
			for (FinallyPathWrapper finwrap : lst) {
				setFinallyShortRangeEntryPoints.add(finwrap.entry);
			}
		}

		Set<String> setFinallyLongRangeEntryPaths = new HashSet<String>();
		for (List<FinallyPathWrapper> lst : dgraph.mapLongRangeFinallyPaths.values()) {
			for (FinallyPathWrapper finwrap : lst) {
				setFinallyLongRangeEntryPaths.add(finwrap.source + "##" + finwrap.entry);
			}
		}

		Map<String, VarExprent> mapCatch = new HashMap<String, VarExprent>();
		collectCatchVars(root, flatthelper, mapCatch);

		Map<DirectNode, Map<String, PrimitiveExprsList>> mapData = new HashMap<DirectNode, Map<String, PrimitiveExprsList>>();

		LinkedList<DirectNode> stack = new LinkedList<DirectNode>();
		LinkedList<LinkedList<String>> stackEntryPoint = new LinkedList<LinkedList<String>>();

		stack.add(dgraph.first);
		stackEntryPoint.add(new LinkedList<String>());

		Map<String, PrimitiveExprsList> map = new HashMap<String, PrimitiveExprsList>();
		map.put(null, new PrimitiveExprsList());
		mapData.put(dgraph.first, map);

		while (!stack.isEmpty()) {

			DirectNode node = stack.removeFirst();
			LinkedList<String> entrypoints = stackEntryPoint.removeFirst();

			PrimitiveExprsList data;
			if (mapCatch.containsKey(node.id)) {
				data = getExpressionData(mapCatch.get(node.id));
			} else {
				data = mapData.get(node).get(buildEntryPointKey(entrypoints));
			}

			BasicBlockStatement block = node.block;
			if (block != null) {
				processBlock(block, data, pool);
				block.setExprents(data.getLstExprents());
			}

			String currentEntrypoint = entrypoints.isEmpty() ? null : entrypoints.getLast();

			for (DirectNode nd : node.succs) {

				boolean isSuccessor = true;
				if (currentEntrypoint != null && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
					isSuccessor = false;
					for (FinallyPathWrapper finwraplong : dgraph.mapLongRangeFinallyPaths.get(node.id)) {
						if (finwraplong.source.equals(currentEntrypoint) && finwraplong.destination.equals(nd.id)) {
							isSuccessor = true;
							break;
						}
					}
				}

				if (isSuccessor) {

					Map<String, PrimitiveExprsList> mapSucc = mapData.get(nd);
					if (mapSucc == null) {
						mapData.put(nd, mapSucc = new HashMap<String, PrimitiveExprsList>());
					}

					LinkedList<String> ndentrypoints = new LinkedList<String>(entrypoints);

					if (setFinallyLongRangeEntryPaths.contains(node.id + "##" + nd.id)) {
						ndentrypoints.addLast(node.id);
					} else if (!setFinallyShortRangeEntryPoints.contains(nd.id) && dgraph.mapLongRangeFinallyPaths.containsKey(node.id)) {
						ndentrypoints.removeLast(); // currentEntrypoint should
													// not be null at this point
					}

					String ndentrykey = buildEntryPointKey(ndentrypoints);
					if (!mapSucc.containsKey(ndentrykey)) {

						mapSucc.put(ndentrykey, copyVarExprents(data.copyStack()));

						stack.add(nd);
						stackEntryPoint.add(ndentrypoints);
					}
				}
			}
		}

		initStatementExprents(root);
	}

	// FIXME: Ugly code, to be rewritten. A tuple class is needed.
	private String buildEntryPointKey(LinkedList<String> entrypoints) {
		if (entrypoints.isEmpty()) {
			return null;
		} else {
			StringBuilder buffer = new StringBuilder();
			for (String point : entrypoints) {
				buffer.append(point);
				buffer.append(":");
			}
			return buffer.toString();
		}
	}

	private PrimitiveExprsList copyVarExprents(PrimitiveExprsList data) {
		ExprentStack stack = data.getStack();
		for (int i = 0; i < stack.size(); i++) {
			stack.set(i, stack.get(i).copy());
		}
		return data;
	}

	private void collectCatchVars(Statement stat, FlattenStatementsHelper flatthelper, Map<String, VarExprent> map) {

		List<VarExprent> lst = null;

		if (stat.type == Statement.TYPE_CATCHALL) {
			CatchAllStatement catchall = (CatchAllStatement) stat;
			if (!catchall.isFinally()) {
				lst = catchall.getVars();
			}
		} else if (stat.type == Statement.TYPE_TRYCATCH) {
			lst = ((CatchStatement) stat).getVars();
		}

		if (lst != null) {
			for (int i = 1; i < stat.getStats().size(); i++) {
				map.put(flatthelper.getMapDestinationNodes().get(stat.getStats().get(i).id)[0], lst.get(i - 1));
			}
		}

		for (Statement st : stat.getStats()) {
			collectCatchVars(st, flatthelper, map);
		}
	}

	private void initStatementExprents(Statement stat) {
		stat.initExprents();

		for (Statement st : stat.getStats()) {
			initStatementExprents(st);
		}
	}

	public void processBlock(BasicBlockStatement stat, PrimitiveExprsList data, ConstantPool pool) {

		BasicBlock block = stat.getBlock();

		ExprentStack stack = data.getStack();
		List<Exprent> exprlist = data.getLstExprents();

		InstructionSequence seq = block.getSeq();

		for (int i = 0; i < seq.length(); i++) {

			Instruction instr = seq.getInstr(i);

			switch (instr.opcode) {
			case opc_aconst_null:
				pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_NULL, null));
				break;
			case opc_bipush:
			case opc_sipush:
				pushEx(stack, exprlist, new ConstExprent(instr.getOperand(0), true));
				break;
			case opc_lconst_0:
			case opc_lconst_1:
				pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_LONG, new Long(instr.opcode - opc_lconst_0)));
				break;
			case opc_fconst_0:
			case opc_fconst_1:
			case opc_fconst_2:
				pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_FLOAT, new Float(instr.opcode - opc_fconst_0)));
				break;
			case opc_dconst_0:
			case opc_dconst_1:
				pushEx(stack, exprlist, new ConstExprent(VarType.VARTYPE_DOUBLE, new Double(instr.opcode - opc_dconst_0)));
				break;
			case opc_ldc:
			case opc_ldc_w:
			case opc_ldc2_w:
				PrimitiveConstant cn = pool.getPrimitiveConstant(instr.getOperand(0));
				pushEx(stack, exprlist, new ConstExprent(consts[cn.type - CONSTANT_Integer], cn.value));
				break;
			case opc_iload:
			case opc_lload:
			case opc_fload:
			case opc_dload:
			case opc_aload:
				pushEx(stack, exprlist, new VarExprent(instr.getOperand(0), vartypes[instr.opcode - opc_iload], varProcessor));
				break;
			case opc_iaload:
			case opc_laload:
			case opc_faload:
			case opc_daload:
			case opc_aaload:
			case opc_baload:
			case opc_caload:
			case opc_saload:
				Exprent index = stack.pop();
				Exprent arr = stack.pop();

				VarType vartype = null;
				switch (instr.opcode) {
				case opc_laload:
					vartype = VarType.VARTYPE_LONG;
					break;
				case opc_daload:
					vartype = VarType.VARTYPE_DOUBLE;
				}
				pushEx(stack, exprlist, new ArrayExprent(arr, index, arrtypes[instr.opcode - opc_iaload]), vartype);
				break;
			case opc_istore:
			case opc_lstore:
			case opc_fstore:
			case opc_dstore:
			case opc_astore:
				Exprent top = stack.pop();
				int varindex = instr.getOperand(0);
				AssignmentExprent assign = new AssignmentExprent(new VarExprent(varindex, vartypes[instr.opcode - opc_istore], varProcessor), top);
				exprlist.add(assign);
				break;
			case opc_iastore:
			case opc_lastore:
			case opc_fastore:
			case opc_dastore:
			case opc_aastore:
			case opc_bastore:
			case opc_castore:
			case opc_sastore:
				Exprent value = stack.pop();
				Exprent index_store = stack.pop();
				Exprent arr_store = stack.pop();
				AssignmentExprent arrassign = new AssignmentExprent(new ArrayExprent(arr_store, index_store, arrtypes[instr.opcode - opc_iastore]), value);
				exprlist.add(arrassign);
				break;
			case opc_iadd:
			case opc_ladd:
			case opc_fadd:
			case opc_dadd:
			case opc_isub:
			case opc_lsub:
			case opc_fsub:
			case opc_dsub:
			case opc_imul:
			case opc_lmul:
			case opc_fmul:
			case opc_dmul:
			case opc_idiv:
			case opc_ldiv:
			case opc_fdiv:
			case opc_ddiv:
			case opc_irem:
			case opc_lrem:
			case opc_frem:
			case opc_drem:
				pushEx(stack, exprlist, new FunctionExprent(func1[(instr.opcode - opc_iadd) / 4], stack));
				break;
			case opc_ishl:
			case opc_lshl:
			case opc_ishr:
			case opc_lshr:
			case opc_iushr:
			case opc_lushr:
			case opc_iand:
			case opc_land:
			case opc_ior:
			case opc_lor:
			case opc_ixor:
			case opc_lxor:
				pushEx(stack, exprlist, new FunctionExprent(func2[(instr.opcode - opc_ishl) / 2], stack));
				break;
			case opc_ineg:
			case opc_lneg:
			case opc_fneg:
			case opc_dneg:
				pushEx(stack, exprlist, new FunctionExprent(FunctionExprent.FUNCTION_NEG, stack));
				break;
			case opc_iinc:
				VarExprent vevar = new VarExprent(instr.getOperand(0), VarType.VARTYPE_INT, varProcessor);
				exprlist.add(new AssignmentExprent(vevar, new FunctionExprent(instr.getOperand(1) < 0 ? FunctionExprent.FUNCTION_SUB : FunctionExprent.FUNCTION_ADD, Arrays
						.asList(new Exprent[] { vevar.copy(), new ConstExprent(VarType.VARTYPE_INT, new Integer(Math.abs(instr.getOperand(1)))) }))));
				break;
			case opc_i2l:
			case opc_i2f:
			case opc_i2d:
			case opc_l2i:
			case opc_l2f:
			case opc_l2d:
			case opc_f2i:
			case opc_f2l:
			case opc_f2d:
			case opc_d2i:
			case opc_d2l:
			case opc_d2f:
			case opc_i2b:
			case opc_i2c:
			case opc_i2s:
				pushEx(stack, exprlist, new FunctionExprent(func3[instr.opcode - opc_i2l], stack));
				break;
			case opc_lcmp:
			case opc_fcmpl:
			case opc_fcmpg:
			case opc_dcmpl:
			case opc_dcmpg:
				pushEx(stack, exprlist, new FunctionExprent(func4[instr.opcode - opc_lcmp], stack));
				break;
			case opc_ifeq:
			case opc_ifne:
			case opc_iflt:
			case opc_ifge:
			case opc_ifgt:
			case opc_ifle:
				exprlist.add(new IfExprent(negifs[func5[instr.opcode - opc_ifeq]], stack));
				break;
			case opc_if_icmpeq:
			case opc_if_icmpne:
			case opc_if_icmplt:
			case opc_if_icmpge:
			case opc_if_icmpgt:
			case opc_if_icmple:
			case opc_if_acmpeq:
			case opc_if_acmpne:
				exprlist.add(new IfExprent(negifs[func6[instr.opcode - opc_if_icmpeq]], stack));
				break;
			case opc_ifnull:
			case opc_ifnonnull:
				exprlist.add(new IfExprent(negifs[func7[instr.opcode - opc_ifnull]], stack));
				break;
			case opc_tableswitch:
			case opc_lookupswitch:
				exprlist.add(new SwitchExprent(stack.pop()));
				break;
			case opc_ireturn:
			case opc_lreturn:
			case opc_freturn:
			case opc_dreturn:
			case opc_areturn:
			case opc_return:
			case opc_athrow:
				exprlist.add(new ExitExprent(instr.opcode == opc_athrow ? ExitExprent.EXIT_THROW : ExitExprent.EXIT_RETURN, instr.opcode == opc_return ? null : stack.pop(),
						instr.opcode == opc_athrow ? null : ((MethodDescriptor) DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_DESCRIPTOR)).ret));
				break;
			case opc_monitorenter:
			case opc_monitorexit:
				exprlist.add(new MonitorExprent(func8[instr.opcode - opc_monitorenter], stack.pop()));
				break;
			case opc_checkcast:
			case opc_instanceof:
				stack.push(new ConstExprent(new VarType(pool.getPrimitiveConstant(instr.getOperand(0)).getString(), true), null));
			case opc_arraylength:
				pushEx(stack, exprlist, new FunctionExprent(mapConsts.get(instr.opcode).intValue(), stack));
				break;
			case opc_getstatic:
			case opc_getfield:
				pushEx(stack, exprlist, new FieldExprent(pool.getLinkConstant(instr.getOperand(0)), instr.opcode == opc_getstatic ? null : stack.pop()));
				break;
			case opc_putstatic:
			case opc_putfield:
				Exprent valfield = stack.pop();
				Exprent exprfield = new FieldExprent(pool.getLinkConstant(instr.getOperand(0)), instr.opcode == opc_putstatic ? null : stack.pop());
				exprlist.add(new AssignmentExprent(exprfield, valfield));
				break;
			case opc_invokevirtual:
			case opc_invokespecial:
			case opc_invokestatic:
			case opc_invokeinterface:
			case opc_invokedynamic:
				if(instr.opcode != opc_invokedynamic || instr.bytecode_version >= CodeConstants.BYTECODE_JAVA_7) {
					InvocationExprent exprinv = new InvocationExprent(instr.opcode, pool.getLinkConstant(instr.getOperand(0)), stack);
					if (exprinv.getDescriptor().ret.type == CodeConstants.TYPE_VOID) {
						exprlist.add(exprinv);
					} else {
						pushEx(stack, exprlist, exprinv);
					}
				}
				break;
			case opc_new:
			case opc_anewarray:
			case opc_multianewarray:
				int arrdims = (instr.opcode == opc_new) ? 0 : (instr.opcode == opc_anewarray) ? 1 : instr.getOperand(1);
				VarType arrtype = new VarType(pool.getPrimitiveConstant(instr.getOperand(0)).getString(), true);
				if (instr.opcode != opc_multianewarray) {
					arrtype.arraydim += arrdims;
				}
				pushEx(stack, exprlist, new NewExprent(arrtype, stack, arrdims));
				break;
			case opc_newarray:
				pushEx(stack, exprlist, new NewExprent(new VarType(arr_type[instr.getOperand(0) - 4], 1), stack, 1));
				break;
			case opc_dup:
				pushEx(stack, exprlist, stack.getByOffset(-1).copy());
				break;
			case opc_dup_x1:
				insertByOffsetEx(-2, stack, exprlist, -1);
				break;
			case opc_dup_x2:
				if (stack.getByOffset(-2).getExprType().stack_size == 2) {
					insertByOffsetEx(-2, stack, exprlist, -1);
				} else {
					insertByOffsetEx(-3, stack, exprlist, -1);
				}
				break;
			case opc_dup2:
				if (stack.getByOffset(-1).getExprType().stack_size == 2) {
					pushEx(stack, exprlist, stack.getByOffset(-1).copy());
				} else {
					pushEx(stack, exprlist, stack.getByOffset(-2).copy());
					pushEx(stack, exprlist, stack.getByOffset(-2).copy());
				}
				break;
			case opc_dup2_x1:
				if (stack.getByOffset(-1).getExprType().stack_size == 2) {
					insertByOffsetEx(-2, stack, exprlist, -1);
				} else {
					insertByOffsetEx(-3, stack, exprlist, -2);
					insertByOffsetEx(-3, stack, exprlist, -1);
				}
				break;
			case opc_dup2_x2:
				if (stack.getByOffset(-1).getExprType().stack_size == 2) {
					if (stack.getByOffset(-2).getExprType().stack_size == 2) {
						insertByOffsetEx(-2, stack, exprlist, -1);
					} else {
						insertByOffsetEx(-3, stack, exprlist, -1);
					}
				} else {
					if (stack.getByOffset(-3).getExprType().stack_size == 2) {
						insertByOffsetEx(-3, stack, exprlist, -2);
						insertByOffsetEx(-3, stack, exprlist, -1);
					} else {
						insertByOffsetEx(-4, stack, exprlist, -2);
						insertByOffsetEx(-4, stack, exprlist, -1);
					}
				}
				break;
			case opc_swap:
				insertByOffsetEx(-2, stack, exprlist, -1);
				stack.pop();
				break;
			case opc_pop:
			case opc_pop2:
				stack.pop();
			}

		}

	}

	private void pushEx(ExprentStack stack, List<Exprent> exprlist, Exprent exprent) {
		pushEx(stack, exprlist, exprent, null);
	}

	private void pushEx(ExprentStack stack, List<Exprent> exprlist, Exprent exprent, VarType vartype) {
		int varindex = VarExprent.STACK_BASE + stack.size();
		VarExprent var = new VarExprent(varindex, vartype == null ? exprent.getExprType() : vartype, varProcessor);
		var.setStack(true);

		exprlist.add(new AssignmentExprent(var, exprent));
		stack.push(var.copy());
	}

	private void insertByOffsetEx(int offset, ExprentStack stack, List<Exprent> exprlist, int copyoffset) {

		int base = VarExprent.STACK_BASE + stack.size();

		LinkedList<VarExprent> lst = new LinkedList<VarExprent>();

		for (int i = -1; i >= offset; i--) {
			Exprent varex = stack.pop();
			VarExprent varnew = new VarExprent(base + i + 1, varex.getExprType(), varProcessor);
			varnew.setStack(true);
			exprlist.add(new AssignmentExprent(varnew, varex));
			lst.add(0, (VarExprent) varnew.copy());
		}

		Exprent exprent = lst.get(lst.size() + copyoffset).copy();
		VarExprent var = new VarExprent(base + offset, exprent.getExprType(), varProcessor);
		var.setStack(true);
		exprlist.add(new AssignmentExprent(var, exprent));
		lst.add(0, (VarExprent) var.copy());

		for (VarExprent expr : lst) {
			stack.push(expr);
		}

	}

	public static String getTypeName(VarType type) {
		return getTypeName(type, true);
	}

	public static String getTypeName(VarType type, boolean getShort) {

		int tp = type.type;
		if (tp <= CodeConstants.TYPE_BOOLEAN) {
			return typeNames[tp];
		} else if (tp == CodeConstants.TYPE_UNKNOWN) {
			return UNKNOWN_TYPE_STRING; // INFO: should not occur
		} else if (tp == CodeConstants.TYPE_NULL) {
			return NULL_TYPE_STRING; // INFO: should not occur
		} else if (tp == CodeConstants.TYPE_VOID) {
			return "void";
		} else if (tp == CodeConstants.TYPE_OBJECT) {
			String ret = ExprProcessor.buildJavaClassName(type.value);
			if (getShort) {
				ret = DecompilerContext.getImpcollector().getShortName(ret);
			}

			if (ret == null) {
				// FIXME: a warning should be logged
				ret = UNDEFINED_TYPE_STRING;
			}
			return ret;
		}

		throw new RuntimeException("invalid type");
	}

	public static String getCastTypeName(VarType type) {
		return getCastTypeName(type, true);
	}

	public static String getCastTypeName(VarType type, boolean getShort) {
		String s = getTypeName(type, getShort);
		int dim = type.arraydim;
		while (dim-- > 0) {
			s += "[]";
		}
		return s;
	}

	public static PrimitiveExprsList getExpressionData(VarExprent var) {
		PrimitiveExprsList prlst = new PrimitiveExprsList();
		VarExprent vartmp = new VarExprent(VarExprent.STACK_BASE, var.getExprType(), var.getProcessor());
		vartmp.setStack(true);

		prlst.getLstExprents().add(new AssignmentExprent(vartmp, var.copy()));
		prlst.getStack().push(vartmp.copy());
		return prlst;
	}

	public static boolean endsWithSemikolon(Exprent expr) {
		int type = expr.type;
		return !(type == Exprent.EXPRENT_SWITCH || type == Exprent.EXPRENT_MONITOR || type == Exprent.EXPRENT_IF || (type == Exprent.EXPRENT_VAR && ((VarExprent) expr)
				.isClassdef()));
	}

	public static String jmpWrapper(Statement stat, int indent, boolean semicolon) {
		StringBuffer buf = new StringBuffer(stat.toJava(indent));

		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		List<StatEdge> lstSuccs = stat.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL);
		if (lstSuccs.size() == 1) {
			StatEdge edge = lstSuccs.get(0);
			if (edge.getType() != StatEdge.TYPE_REGULAR && edge.explicit == true && edge.getDestination().type != Statement.TYPE_DUMMYEXIT) {
				buf.append(InterpreterUtil.getIndentString(indent));

				switch (edge.getType()) {
				case StatEdge.TYPE_BREAK:
					buf.append("break");
					break;
				case StatEdge.TYPE_CONTINUE:
					buf.append("continue");
				}

				if (edge.labeled) {
					buf.append(" label" + edge.closure.id);
				}
				buf.append(";" + new_line_separator);
			}
		}

		if (buf.length() == 0 && semicolon) {
			buf.append(InterpreterUtil.getIndentString(indent) + ";" + new_line_separator);
		}

		return buf.toString();
	}

	public static String buildJavaClassName(String name) {
		String res = name.replace('/', '.');

		if (res.indexOf("$") >= 0) { // attempt to invoke foreign member
										// classes correctly
			StructClass cl = DecompilerContext.getStructcontext().getClass(name);
			if (cl == null || !cl.isOwn()) {
				res = res.replace('$', '.');
			}
		}

		return res;
	}

	public static String listToJava(List<Exprent> lst, int indent) {
		String indstr = InterpreterUtil.getIndentString(indent);

		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new StringBuffer();

		for (Exprent expr : lst) {
			String content = expr.toJava(indent);
			if (content.length() > 0) {
				if (expr.type != Exprent.EXPRENT_VAR || !((VarExprent) expr).isClassdef()) {
					buf.append(indstr);
				}
				buf.append(content);
				if (expr.type == Exprent.EXPRENT_MONITOR && ((MonitorExprent) expr).getMontype() == MonitorExprent.MONITOR_ENTER) {
					buf.append("{}"); // empty synchronized block
				}
				if (ExprProcessor.endsWithSemikolon(expr)) {
					buf.append(";");
				}
				buf.append(new_line_separator);
			}
		}

		return buf.toString();
	}

	public static ConstExprent getDefaultArrayValue(VarType arrtype) {

		ConstExprent defaultval;
		if (arrtype.type == CodeConstants.TYPE_OBJECT || arrtype.arraydim > 0) {
			defaultval = new ConstExprent(VarType.VARTYPE_NULL, null);
		} else if (arrtype.type == CodeConstants.TYPE_FLOAT) {
			defaultval = new ConstExprent(VarType.VARTYPE_FLOAT, new Float(0));
		} else if (arrtype.type == CodeConstants.TYPE_LONG) {
			defaultval = new ConstExprent(VarType.VARTYPE_LONG, new Long(0));
		} else if (arrtype.type == CodeConstants.TYPE_DOUBLE) {
			defaultval = new ConstExprent(VarType.VARTYPE_DOUBLE, new Double(0));
		} else { // integer types
			defaultval = new ConstExprent(0, true);
		}

		return defaultval;
	}

	public static boolean getCastedExprent(Exprent exprent, VarType leftType, StringBuilder buffer, int indent, boolean castNull) {
		return getCastedExprent(exprent, leftType, buffer, indent, castNull, false);
	}

	public static boolean getCastedExprent(Exprent exprent, VarType leftType, StringBuilder buffer, int indent, boolean castNull, boolean castAlways) {

		boolean ret = false;
		VarType rightType = exprent.getExprType();

		String res = exprent.toJava(indent);

		boolean cast = !leftType.isSuperset(rightType) && (rightType.equals(VarType.VARTYPE_OBJECT) || leftType.type != CodeConstants.TYPE_OBJECT);
		cast |= castAlways;

		if (!cast && castNull && rightType.type == CodeConstants.TYPE_NULL) {
			// check for a nameless anonymous class
			cast = !UNDEFINED_TYPE_STRING.equals(getTypeName(leftType));
		}
		if (!cast) {
			cast = isIntConstant(exprent) && VarType.VARTYPE_INT.isStrictSuperset(leftType);
		}

		if (cast) {
			if (exprent.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
				res = "(" + res + ")";
			}

			res = "(" + ExprProcessor.getCastTypeName(leftType) + ")" + res;
			ret = true;
		}

		buffer.append(res);

		return ret;
	}

	private static boolean isIntConstant(Exprent exprent) {

		if (exprent.type == Exprent.EXPRENT_CONST) {
			ConstExprent cexpr = (ConstExprent) exprent;
			switch (cexpr.getConsttype().type) {
			case CodeConstants.TYPE_BYTE:
			case CodeConstants.TYPE_BYTECHAR:
			case CodeConstants.TYPE_SHORT:
			case CodeConstants.TYPE_SHORTCHAR:
			case CodeConstants.TYPE_INT:
				return true;
			}
		}

		return false;
	}

}
