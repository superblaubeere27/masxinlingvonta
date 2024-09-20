package net.superblaubeere27.masxinlingvaj.compiler.newAST.asm2ir;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.FloatingPointCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.AllocObjectExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.AllocArrayExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLengthExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.array.ArrayLoadExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.CheckCastExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.SwitchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.*;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.OpcodeUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Objects;
import java.util.stream.IntStream;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.OpcodeUtils.*;

public class InstructionConverter {
    private final NewCodeConverter codeConverter;
    private final AbstractInsnNode instruction;

    private final OpcodeUtils.OpcodeAnalysisContext analysisContext;
    private final Frame<SourceValue> currentFrame;
    /**
     * Which block should we jump to if an exception occurred?
     */
    private final BasicBlock exceptionHandler;

    public InstructionConverter(NewCodeConverter codeConverter, AbstractInsnNode instruction, BasicBlock exceptionHandler) {
        this.codeConverter = codeConverter;
        this.instruction = instruction;

        this.analysisContext = new OpcodeUtils.OpcodeAnalysisContext(
                codeConverter.frames,
                codeConverter.compilerMethod.getNode().instructions
        );
        this.currentFrame = analysisContext.getFrameOfInstruction(instruction);
        this.exceptionHandler = exceptionHandler;
    }

    private static Expr ensureCategory1(Expr expr) {
        if (expr.getType().getJvmStackSize() != 1)
            throw new IllegalStateException("This instruction shall only be used with category 1 types");

        return expr;
    }

    StateChangeAfterInstructionConversion convert() {
        int opcode = instruction.getOpcode();

        switch (opcode) {
            case NOP: // visitInsn
            case POP:
            case POP2: {
                // Do nothing lol
                return StateChangeAfterInstructionConversion.NONE;
            }
            case ACONST_NULL: {
                _push(new ConstNullExpr());
                return StateChangeAfterInstructionConversion.NONE;
            }
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                _push(new ConstIntExpr(opcode - ICONST_0));

                return StateChangeAfterInstructionConversion.NONE;
            case LCONST_0:
            case LCONST_1:
                _push(new ConstLongExpr(opcode - LCONST_0));

                return StateChangeAfterInstructionConversion.NONE;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                _push(new ConstFloatExpr((float) (opcode - FCONST_0)));

                return StateChangeAfterInstructionConversion.NONE;
            case DCONST_0:
            case DCONST_1:
                _push(new ConstDoubleExpr(opcode - DCONST_0));

                return StateChangeAfterInstructionConversion.NONE;
            case BIPUSH:
            case SIPUSH:
                _push(new ConstIntExpr(((IntInsnNode) instruction).operand));

                return StateChangeAfterInstructionConversion.NONE;
            case LDC: {// visitLdcInsn
                var cst = ((LdcInsnNode) instruction).cst;

                var buildExceptionHandler = false;

                if (cst instanceof Integer) {
                    _push(new ConstIntExpr((Integer) cst));
                } else if (cst instanceof Long) {
                    _push(new ConstLongExpr((Long) cst));
                } else if (cst instanceof Float) {
                    _push(new ConstFloatExpr((Float) cst));
                } else if (cst instanceof Double) {
                    _push(new ConstDoubleExpr((Double) cst));
                } else if (cst instanceof String) {
                    _push(new ConstStringExpr((String) cst));
                } else if (cst instanceof Type) {
                    _push(new ConstTypeExpr((Type) cst));

                    // If the specified type was not found, this expression might throw an exception
                    buildExceptionHandler = true;
                } else {
                    throw new IllegalArgumentException("Invalid LDC");
                }

                return new StateChangeAfterInstructionConversion(
                        buildExceptionHandler,
                        false
                );
            }
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD: {
                var local = ((VarInsnNode) instruction).var;

                _push(_lload(local, getLoadType(opcode)));

                return StateChangeAfterInstructionConversion.NONE;
            }
            case ISTORE:
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE: {
                var local = ((VarInsnNode) instruction).var;

                _lstore(local, _peek(getStoreType(opcode)));

                return StateChangeAfterInstructionConversion.NONE;
            }
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD: {
                var jniType = getArrayLoadJNIType(opcode);

                Expr instance = _peek(ImmType.OBJECT, 2);

                _buildNullCheck(instance.copy());

                _push(
                        new ArrayLoadExpr(
                                jniType,
                                instance,
                                _peek(ImmType.INT, 1)
                        ),
                        2
                );

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE: {
                var jniType = getArrayStoreJNIType(opcode);
                var immType = ImmType.fromJNIType(jniType);

                Expr instance = _peek(ImmType.OBJECT, 3);

                _buildNullCheck(instance.copy());

                _build(
                        new ArrayStoreStmt(
                                jniType,
                                instance,
                                _peek(ImmType.INT, 2),
                                _peek(immType, 1)
                        )
                );

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case DUP:
                _push(_peek(getStackFrameType(instruction, 0)));

                return StateChangeAfterInstructionConversion.NONE;
            case DUP_X1: {
                var value1 = ensureCategory1(_peek(getStackFrameType(instruction, 0), 1));
                var value2 = ensureCategory1(_peek(getStackFrameType(instruction, 1), 2));

                _dupx1(value1, value2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case DUP_X2: {
                var value1 = ensureCategory1(_peek(getStackFrameType(instruction, 0), 1));
                var value2 = _peek(getStackFrameType(instruction, 1), 2);

                if (value2.getType().getJvmStackSize() == 2) {
                    _dupx1(value1, value2);
                } else {
                    var value3 = _peek(getStackFrameType(instruction, 2), 3);

                    _dupx2(value1, value2, value3);
                }

                return StateChangeAfterInstructionConversion.NONE;
            }
            case DUP2: {
                var value1 = _peek(getStackFrameType(instruction, 0), 1);

                if (value1.getType().getJvmStackSize() == 2) {
                    _push(value1);
                } else {
                    ensureCategory1(value1);

                    var value2 = ensureCategory1(_peek(getStackFrameType(instruction, 1), 2));

                    _push(value2);
                    _push(value1, -1);
                }

                return StateChangeAfterInstructionConversion.NONE;
            }
            case DUP2_X1: {
                var value1 = _peek(getStackFrameType(instruction, 0), 1);
                var value2 = ensureCategory1(_peek(getStackFrameType(instruction, 1), 2));

                if (value1.getType().getJvmStackSize() == 2) {
                    _dupx1(value1, value2);
                } else {
                    var value3 = _peek(getStackFrameType(instruction, 2), 3);

                    _dupx3(value1, value2, value3);
                }

                return StateChangeAfterInstructionConversion.NONE;
            }
            case DUP2_X2: {
                var value1 = _peek(getStackFrameType(instruction, 0), 1);
                var value2 = _peek(getStackFrameType(instruction, 1), 2);

                if (value1.getType().getJvmStackSize() == 2 && value2.getType().getJvmStackSize() == 2) {
                    _dupx1(value1, value2);
                    break;
                }
                var value3 = _peek(getStackFrameType(instruction, 2), 3);

                if (value3.getType().getJvmStackSize() == 2) {
                    _dupx3(value1, value2, value3);
                } else if (value1.getType().getJvmStackSize() == 2) {
                    _dupx2(value1, value2, value3);
                } else {
                    var value4 = _peek(getStackFrameType(instruction, 3), 4);

                    var tmpValue1 = _buildTmpVar(value1);
                    var tmpValue2 = _buildTmpVar(value2);
                    var tmpValue3 = _buildTmpVar(value3);
                    var tmpValue4 = _buildTmpVar(value4);

                    _push(new VarExpr(tmpValue2), 4);
                    _push(new VarExpr(tmpValue1), 3);
                    _push(new VarExpr(tmpValue4), 2);
                    _push(new VarExpr(tmpValue3), 1);
                    _push(new VarExpr(tmpValue2));
                    _push(new VarExpr(tmpValue1), -1);
                }

                return StateChangeAfterInstructionConversion.NONE;
            }
            case SWAP:
                _push(_peek(getStackFrameType(instruction, 0)), 2);
                _push(_peek(getStackFrameType(instruction, 1)), 1);

                return StateChangeAfterInstructionConversion.NONE;
            case IADD:
            case LADD:
            case ISUB:
            case LSUB:
            case IMUL:
            case LMUL:
            case IDIV:
            case LDIV:
            case IREM:
            case LREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR: {
                var type = getIntegerArithmeticsType(opcode);

                // TODO: Implement throwing ArithmeticException

                _push(
                        new IntegerArithmeticsExpr(
                                getIntegerArithmeticsOperator(opcode),
                                type,
                                _peek(type.getImmType(), 2),
                                _peek(type.getImmType(), 1)
                        ),
                        2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case LUSHR:
            case LSHR:
            case LSHL: {
                _push(
                        new IntegerArithmeticsExpr(
                                getIntegerArithmeticsOperator(opcode),
                                IntegerArithmeticsExpr.IntegerType.LONG,
                                _peek(ImmType.LONG, 2),
                                _peek(ImmType.INT, 1)
                        ),
                        2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case FADD:
            case DADD:
            case FSUB:
            case DSUB:
            case FMUL:
            case DMUL:
            case FDIV:
            case DDIV:
            case FREM:
            case DREM: {
                var type = getFloatingPointArithmeticsType(opcode);

                // TODO: Implement throwing ArithmeticException

                _push(
                        new FloatingPointArithmeticsExpr(
                                getFloatingPointArithmeticsOperator(opcode),
                                type,
                                _peek(type.getImmType(), 2),
                                _peek(type.getImmType(), 1)
                        ),
                        2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG: {
                _push(
                        new NegationExpr(_peek(getNegationType(opcode))),
                        1
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case IINC: {
                var iinc = (IincInsnNode) instruction;
                var affectedLocal = codeConverter.cfg.getLocals().getLocal(iinc.var, ImmType.INT);

                _lstore(iinc.var, new IntegerArithmeticsExpr(IntegerArithmeticsExpr.Operator.ADD, IntegerArithmeticsExpr.IntegerType.INT, new VarExpr(affectedLocal), new ConstIntExpr(iinc.incr)));

                return StateChangeAfterInstructionConversion.NONE;
            }
            case I2L:
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
            case I2B:
            case I2C:
            case I2S: {
                var inputType = getCastInputType(opcode);
                var target = getCastTarget(opcode);

                _push(
                        new PrimitiveCastExpr(
                                _peek(inputType),
                                target
                        ),
                        1
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case LCMP: {
                var cfg = codeConverter.cfg;

                var lhs = _peek(ImmType.LONG, 2);
                var rhs = _peek(ImmType.LONG, 1);
                var tmpVar = cfg.getLocals().allocSynthetic(ImmType.INT);

                var greaterBlock = new BasicBlock(cfg);
                var lowerBlock = new BasicBlock(cfg);
                var eqBlock = new BasicBlock(cfg);
                var neqBlock = new BasicBlock(cfg);
                var nextBlock = new BasicBlock(cfg);

                // if (lhs == rhs)

                _build(
                        new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.EQUAL, lhs, rhs), eqBlock, neqBlock)
                );

                this.codeConverter.advanceBasicBlock(neqBlock);

                // if (lhs < rhs)
                _build(
                        new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.LOWER, lhs.copy(), rhs.copy()), lowerBlock, greaterBlock)
                );

                // eq Block
                this.codeConverter.advanceBasicBlock(eqBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(0)));
                _build(new UnconditionalBranch(nextBlock));

                // lower Block
                this.codeConverter.advanceBasicBlock(lowerBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(-1)));
                _build(new UnconditionalBranch(nextBlock));

                // lower Block
                this.codeConverter.advanceBasicBlock(greaterBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(1)));
                _build(new UnconditionalBranch(nextBlock));

                this.codeConverter.advanceBasicBlock(nextBlock);

                _push(new VarExpr(tmpVar), 2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG: {
                var comparedType = opcode > FCMPG ? FloatingPointArithmeticsExpr.FloatingPointType.DOUBLE : FloatingPointArithmeticsExpr.FloatingPointType.FLOAT;

                var cfg = codeConverter.cfg;

                var lhs = _peek(comparedType.getImmType(), 2);
                var rhs = _peek(comparedType.getImmType(), 1);
                var tmpVar = cfg.getLocals().allocSynthetic(ImmType.INT);

                var greaterBlock = new BasicBlock(cfg);
                var lowerBlock = new BasicBlock(cfg);
                var eqBlock = new BasicBlock(cfg);
                var notNaNBlock = new BasicBlock(cfg);
                var neqBlock = new BasicBlock(cfg);
                var nextBlock = new BasicBlock(cfg);

                var nanBlock = (opcode == FCMPL || opcode == DCMPL) ? lowerBlock : greaterBlock;

                // if (isnan(lhs) || isnan(rhs))

                _build(
                        new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.UNORDERED, lhs, rhs), nanBlock, notNaNBlock)
                );

                codeConverter.advanceBasicBlock(notNaNBlock);

                // if (lhs == rhs)

                _build(
                        new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.EQUAL, lhs.copy(), rhs.copy()), eqBlock, neqBlock)
                );

                codeConverter.advanceBasicBlock(neqBlock);

                // if (lhs < rhs)
                _build(
                        new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.LOWER, lhs.copy(), rhs.copy()), lowerBlock, greaterBlock)
                );

                // eq Block
                codeConverter.advanceBasicBlock(eqBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(0)));
                _build(new UnconditionalBranch(nextBlock));

                // lower Block
                codeConverter.advanceBasicBlock(lowerBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(-1)));
                _build(new UnconditionalBranch(nextBlock));

                // lower Block
                codeConverter.advanceBasicBlock(greaterBlock);

                _build(new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(1)));
                _build(new UnconditionalBranch(nextBlock));

                codeConverter.advanceBasicBlock(nextBlock);

                _push(new VarExpr(tmpVar), 2);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE: {
                var ifTarget = this.codeConverter.labelMap.get(((JumpInsnNode) instruction).label);
                var elseTarget = new BasicBlock(this.codeConverter.cfg);

                Expr lhs;
                Expr rhs;

                if (opcode <= IFLE) {
                    lhs = _peek(ImmType.INT);
                    rhs = new ConstIntExpr(0);
                } else {
                    lhs = _peek(ImmType.INT, 2);
                    rhs = _peek(ImmType.INT);
                }

                _build(
                        new ConditionalBranch(
                                new IntegerCompareExpr(getIntegerCompareType(opcode), lhs, rhs),
                                ifTarget,
                                elseTarget
                        )
                );

                this.codeConverter.advanceBasicBlock(elseTarget);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IFNULL:
            case IFNONNULL: {
                var ifTarget = this.codeConverter.labelMap.get(((JumpInsnNode) instruction).label);
                var elseTarget = new BasicBlock(this.codeConverter.cfg);

                var rhs = (opcode == IFNULL || opcode == IFNONNULL) ? new ConstNullExpr() : _peek(ImmType.OBJECT, 2);

                BasicBlock effectiveElseTarget = elseTarget;

                if (isObjectConvertInverted(opcode)) {
                    var tmp = ifTarget;

                    ifTarget = elseTarget;
                    effectiveElseTarget = tmp;
                }

                _build(
                        new ConditionalBranch(
                                new ObjectCompareExpr(_peek(ImmType.OBJECT), rhs),
                                ifTarget,
                                effectiveElseTarget
                        )
                );

                this.codeConverter.advanceBasicBlock(elseTarget);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case GOTO:
                _build(
                        new UnconditionalBranch(this.codeConverter.labelMap.get(((JumpInsnNode) instruction).label))
                );

                this.codeConverter.advanceBasicBlock(null);

                return StateChangeAfterInstructionConversion.NONE;
            case JSR:
            case RET:
                throw new IllegalStateException("JSR/RET instructions are not supported (This instruction is only produced by older java compiler versions)");
            case TABLESWITCH:
            case LOOKUPSWITCH: {
                int[] keys;
                BasicBlock defaultBlock;
                BasicBlock[] basicBlocks;

                if (opcode == TABLESWITCH) {
                    TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) instruction;

                    defaultBlock = Objects.requireNonNull(this.codeConverter.labelMap.get(tableSwitch.dflt));
                    basicBlocks = IntStream.range(tableSwitch.min, tableSwitch.max + 1).mapToObj(x -> Objects.requireNonNull(this.codeConverter.labelMap.get(tableSwitch.labels.get(x)))).toArray(BasicBlock[]::new);
                    keys = IntStream.range(tableSwitch.min, tableSwitch.max + 1).toArray();
                } else {
                    LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) instruction;

                    defaultBlock = Objects.requireNonNull(this.codeConverter.labelMap.get(lookupSwitch.dflt));
                    basicBlocks = lookupSwitch.labels.stream().map(x -> Objects.requireNonNull(this.codeConverter.labelMap.get(x))).toArray(BasicBlock[]::new);
                    keys = lookupSwitch.keys.stream().mapToInt(Integer::intValue).toArray();
                }

                _build(new SwitchStmt(_peek(ImmType.INT), keys, basicBlocks, defaultBlock));

                if (instruction.getNext() != null && instruction.getNext().getType() != AbstractInsnNode.LABEL)
                    throw new IllegalStateException("Unlabled code after switch instruction");

                this.codeConverter.advanceBasicBlock(null);

                return StateChangeAfterInstructionConversion.NONE;
            }
            case DRETURN:
            case LRETURN:
            case IRETURN:
            case FRETURN:
            case ARETURN: {
                var type = getValueReturnType(opcode);

                _build(new RetStmt(_peek(type)));

                return StateChangeAfterInstructionConversion.NONE;
            }
            case RETURN:
                _build(new RetVoidStmt());

                return StateChangeAfterInstructionConversion.NONE;
            case GETSTATIC: {
                _push(new GetStaticExpr(new MethodOrFieldIdentifier((FieldInsnNode) instruction)));

                return StateChangeAfterInstructionConversion.NONE;
            }
            case GETFIELD: {
                Expr instance = _peek(ImmType.OBJECT);

                _buildNullCheck(instance.copy());

                _push(
                        new GetFieldExpr(
                                new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                instance
                        ),
                        1
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case PUTSTATIC: {
                MethodOrFieldIdentifier target = new MethodOrFieldIdentifier((FieldInsnNode) instruction);

                _build(

                        new PutStaticStmt(
                                target,
                                _peek(ImmType.fromJVMType(Type.getType(target.getDesc())))
                        )
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case PUTFIELD: {
                var instanceValue = _peek(ImmType.OBJECT, 2);

                _buildNullCheck(instanceValue.copy());

                MethodOrFieldIdentifier target = new MethodOrFieldIdentifier((FieldInsnNode) instruction);

                _build(
                        new PutFieldStmt(
                                target,
                                instanceValue,
                                _peek(ImmType.fromJVMType(Type.getType(target.getDesc())), 1)
                        )
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case INVOKESTATIC: {
                MethodOrFieldIdentifier target = new MethodOrFieldIdentifier(((MethodInsnNode) instruction));
                var argTypes = Type.getArgumentTypes(target.getDesc());

                var callExpr = new InvokeStaticExpr(
                        target,
                        IntStream.range(0, argTypes.length)
                                .mapToObj(idx -> _peek(ImmType.fromJVMType(argTypes[idx]), argTypes.length - idx))
                                .toArray(Expr[]::new)
                );

                if (Type.getReturnType(target.getDesc()).getSort() == Type.VOID) {
                    _build(new ExpressionStmt(callExpr));
                } else {
                    _push(callExpr, argTypes.length);
                }

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case INVOKEVIRTUAL:
            case INVOKESPECIAL:
            case INVOKEINTERFACE: {
                MethodOrFieldIdentifier target = new MethodOrFieldIdentifier(((MethodInsnNode) instruction));
                var argTypes = Type.getArgumentTypes(target.getDesc());

                if (target.getOwner().equals("java/lang/invoke/MethodHandle")) {
                    throw new IllegalStateException("MethodHandle function calls cannot be compiled to and should already have been filtered out by one the preprocessors.");
                }


                Expr instance = _peek(ImmType.OBJECT, argTypes.length + 1);

                _buildNullCheck(instance.copy());

                var callExpr = new InvokeInstanceExpr(
                        target,
                        instance,
                        IntStream.range(0, argTypes.length)
                                .mapToObj(idx -> _peek(ImmType.fromJVMType(argTypes[idx]), argTypes.length - idx))
                                .toArray(Expr[]::new),
                        getInvokeInstanceType(opcode)
                );

                if (Type.getReturnType(target.getDesc()).getSort() == Type.VOID) {
                    _build(new ExpressionStmt(callExpr));
                } else {
                    _push(callExpr, argTypes.length + 1);
                }

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case INVOKEDYNAMIC:
                throw new IllegalStateException("INVOKEDYNAMIC is not implemented and should already have been filtered out by one the preprocessors.");
            case NEW: {
                // TODO Should an exception check be implemented here?
                _push(
                        new AllocObjectExpr(((TypeInsnNode) instruction).desc)
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case NEWARRAY:
            case ANEWARRAY: {
                Type type = opcode == NEWARRAY ? getNewArrayType(((IntInsnNode) instruction).operand) : Type.getObjectType(((TypeInsnNode) instruction).desc);

                _push(
                        new AllocArrayExpr(type, _peek(ImmType.INT)),
                        1
                );

                return StateChangeAfterInstructionConversion.NONE;
            }
            case MULTIANEWARRAY:
                throw new IllegalStateException("MULTIANEWARRAY is not implemented and should already have been filtered out by one the preprocessors.");
            case ARRAYLENGTH: {
                Expr instance = _peek(ImmType.OBJECT);

                _buildNullCheck(instance.copy());

                _push(
                        new ArrayLengthExpr(instance),
                        1
                );

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case ATHROW: {
                Expr instance = _peek(ImmType.OBJECT);

                _buildNullCheck(instance.copy());

                _build(
                        new ThrowStmt(instance)
                );

                // Tell the exception handler builder to unconditionally jump
                return new StateChangeAfterInstructionConversion(true, true);
            }
            case CHECKCAST: {
                _push(
                        new CheckCastExpr(((TypeInsnNode) instruction).desc, _peek(ImmType.OBJECT)),
                        1
                );

                return new StateChangeAfterInstructionConversion(true, false);
            }
            case INSTANCEOF:
                _push(
                        new InstanceOfExpr(((TypeInsnNode) instruction).desc, _peek(ImmType.OBJECT)),
                        1
                );

                return StateChangeAfterInstructionConversion.NONE;
            case MONITORENTER:
            case MONITOREXIT: {
                Expr instance = _peek(ImmType.OBJECT);

                _buildNullCheck(instance.copy());

                _build(
                        new MonitorStmt(
                                opcode == MONITORENTER ? MonitorStmt.MonitorType.ENTER : MonitorStmt.MonitorType.EXIT,
                                instance
                        )
                );

                return new StateChangeAfterInstructionConversion(true, false);
            }
        }

        throw new IllegalStateException("Unexpected value: " + opcode);
    }

    private void _buildNullCheck(Expr instanceValue) {
        var locals = codeConverter.cfg.getLocals();

        var ifNull = new BasicBlock(codeConverter.cfg);
        var notNull = new BasicBlock(codeConverter.cfg);

        _build(
                new ConditionalBranch(
                        new ObjectCompareExpr(instanceValue, new ConstNullExpr()),
                        ifNull,
                        notNull
                )
        );

        this.codeConverter.advanceBasicBlock(ifNull);

        var npeVar = locals.allocSynthetic(ImmType.OBJECT);

        _build(new CopyVarStmt(new VarExpr(npeVar), new AllocObjectExpr("java/lang/NullPointerException")));
        _build(new ExpressionStmt(new InvokeInstanceExpr(new MethodOrFieldIdentifier("java/lang/NullPointerException", "<init>", "()V"), new VarExpr(npeVar), new Expr[0], InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL)));
        _build(new ThrowStmt(new VarExpr(npeVar)));
        _build(new UnconditionalBranch(this.exceptionHandler));

        this.codeConverter.advanceBasicBlock(notNull);
    }

    private void _dupx3(Expr value1, Expr value2, Expr value3) {
        var tmpValue1 = _buildTmpVar(value1);
        var tmpValue2 = _buildTmpVar(value2);

        _push(value3, 1);
        _push(new VarExpr(tmpValue2), 3);
        _push(new VarExpr(tmpValue2));
        _push(new VarExpr(tmpValue1), 2);
        _push(new VarExpr(tmpValue1), -1);
    }

    private void _dupx2(Expr value1, Expr value2, Expr value3) {
        var tmpValue1 = _buildTmpVar(value1);
        var tmpValue2 = _buildTmpVar(value2);

        _push(value3, 2);
        _push(new VarExpr(tmpValue2), 1);
        _push(new VarExpr(tmpValue1), 3);
        _push(new VarExpr(tmpValue1));
    }

    private void _dupx1(Expr value1, Expr value2) {
        var tmpValue1 = _buildTmpVar(value1);

        _push(value2, 1);
        _push(new VarExpr(tmpValue1), 2);
        _push(new VarExpr(tmpValue1));
    }

    private Local _buildTmpVar(Expr value1) {
        var locals = codeConverter.cfg.getLocals();
        var tmpValue1 = locals.allocSynthetic(value1.getType());

        _build(new CopyVarStmt(new VarExpr(tmpValue1), value1));

        return tmpValue1;
    }

    private ImmType getStackFrameType(AbstractInsnNode instruction, int offset) {
        var currentFrame = analysisContext.getFrameOfInstruction(instruction);
        var stack = currentFrame.getStack(currentFrame.getStackSize() - 1 - offset);

        Type currentType = null;

        for (AbstractInsnNode insn : stack.insns) {
            Type t = net.superblaubeere27.masxinlingvaj.utils.OpcodeUtils.getReturnType(analysisContext, insn);

            if (currentType == null || t.getSort() == currentType.getSort()) {
                currentType = t;
            } else {
                throw new IllegalArgumentException("Can't merge two types :/");
            }
        }

        ImmType type;

        // If there is no instruction leading to this, it is properly an exception handler
        if (currentType == null)
            type = ImmType.OBJECT;
        else
            type = ImmType.fromJVMType(currentType);

        return type;
    }

    private void _build(Stmt stmt) {
        getCurrentBasicBlock().add(stmt);
    }

    private void _push(Expr expr) {
        _push(expr, 0);
    }

    private void _push(Expr expr, int offset) {
        var locals = this.codeConverter.currentBasicBlock.getGraph().getLocals();

        var targetPos = locals.getStackLocal(this.currentFrame.getStackSize() - offset, expr.getType());

        this.codeConverter.currentBasicBlock.add(new CopyVarStmt(new VarExpr(targetPos), expr));
    }

    private void _lstore(int local, Expr expr) {
        var block = getCurrentBasicBlock();

        block.add(new CopyVarStmt(new VarExpr(block.getGraph().getLocals().getLocal(local, expr.getType())), expr));
    }

    private Expr _lload(int local, ImmType type) {
        return new VarExpr(getCurrentBasicBlock().getGraph().getLocals().getLocal(local, type));
    }

    private Expr _peek(ImmType type) {
        return _peek(type, 1);
    }

    private Expr _peek(ImmType type, int depth) {
        var block = getCurrentBasicBlock();
        var locals = block.getGraph().getLocals();

        var targetPos = locals.getStackLocal(this.currentFrame.getStackSize() - depth, type);

        return new VarExpr(targetPos);
    }

    private BasicBlock getCurrentBasicBlock() {
        return this.codeConverter.currentBasicBlock;
    }

    /**
     * @param exceptionHandlerFlag    Should an exception handler be build after the current instruction?
     * @param assumeExceptionOccurred True if we are sure that an exception occurred (used to implement ATHROW)
     */
    record StateChangeAfterInstructionConversion(
            boolean exceptionHandlerFlag,
            boolean assumeExceptionOccurred
    ) {
        private static final StateChangeAfterInstructionConversion NONE = new StateChangeAfterInstructionConversion(false, false);
    }
}
