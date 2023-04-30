package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.FloatingPointCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.ObjectCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
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
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.SwitchStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.OpcodeUtils;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.OpcodeUtils.OpcodeAnalysisContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.stream.IntStream;

import static net.superblaubeere27.masxinlingvaj.utils.TypeUtils.getEffectiveArgumentTypes;

public class NewCodeConverter implements Opcodes {
    private final MLVCompiler compiler;

    public NewCodeConverter(MLVCompiler compiler) {
        this.compiler = compiler;
    }


    public static ControlFlowGraph convert(MLVCompiler compiler, CompilerMethod compilerMethod) throws AnalyzerException {
        var cfg = new ControlFlowGraph(new LocalsPool(), compilerMethod, Arrays.stream(getEffectiveArgumentTypes(compilerMethod)).map(ImmType::fromJVMType).toArray(ImmType[]::new), ImmType.fromJVMType(Type.getReturnType(compilerMethod.getNode().desc)));

        var frames = new Analyzer<>(new SourceInterpreter()).analyze(compilerMethod.getParent().getName(), compilerMethod.getNode());

        // Maps a basic block to every label in the method
        var labelMap = new HashMap<LabelNode, BasicBlock>();
        // Contains all labels that there are in this method
        var labels = new ArrayList<LabelNode>();

        for (AbstractInsnNode instruction : compilerMethod.getNode().instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                LabelNode label = (LabelNode) instruction;

                labelMap.put(label, new BasicBlock(cfg));
                labels.add(label);
            }
        }

        // Contains the responsible exception handlers for the given labels
        HashMap<LabelNode, List<ExceptionHandler>> exceptionHandlers = new HashMap<>();

        for (TryCatchBlockNode tryCatchBlock : compilerMethod.getNode().tryCatchBlocks) {
            var exceptionHandler = new ExceptionHandler(tryCatchBlock, labelMap.get(tryCatchBlock.handler));

            boolean capturing = false;

            for (LabelNode label : labels) {
                if (label == tryCatchBlock.start) {
                    capturing = true;
                } else if (label == tryCatchBlock.end) {
                    if (!capturing)
                        throw new IllegalStateException("TryCatch ends before it begins?");

                    capturing = false;
                    break;
                }
                if (capturing) {
                    exceptionHandlers.computeIfAbsent(label, e -> new ArrayList<>()).add(exceptionHandler);
                }
            }

            if (capturing)
                throw new IllegalStateException("TryCatch is not terminated.");
        }

        // Used to cancel the method execution
        Stmt nullReturnStatement;

        if (cfg.getReturnType() == ImmType.VOID) {
            nullReturnStatement = new RetVoidStmt();
        } else {
            nullReturnStatement = new RetStmt(cfg.getReturnType().createConstNull());
        }

        var defaultExceptionHandler = new BasicBlock(cfg);

        defaultExceptionHandler.add(nullReturnStatement.copy());

        // This map contains the blocks that should be jumped to when an exception occurs
        HashMap<LabelNode, BasicBlock> exceptionHandlerBlocks = new HashMap<>();

        {
            HashMap<List<ExceptionHandler>, BasicBlock> handlerBlockMap = new HashMap<>();

            for (Map.Entry<LabelNode, List<ExceptionHandler>> exceptionHandler : exceptionHandlers.entrySet()) {
                // Is there already a handler block compiled for the given handler?
                if (handlerBlockMap.containsKey(exceptionHandler.getValue())) {
                    exceptionHandlerBlocks.put(exceptionHandler.getKey(), handlerBlockMap.get(exceptionHandler.getValue()));
                    continue;
                }

                BasicBlock handlerBegin = new BasicBlock(cfg);

                HandlerBuilder:
                {
                    BasicBlock currentBlock = handlerBegin;

                    var caughtExceptionLocal = cfg.getLocals().getStackLocal(0, ImmType.OBJECT);

                    currentBlock.add(new CopyVarStmt(new VarExpr(caughtExceptionLocal), new CatchExpr()));

                    for (ExceptionHandler handler : exceptionHandler.getValue()) {
                        if (handler.getType() == null) {
                            currentBlock.add(new ClearExceptionStateStmt());
                            currentBlock.add(new UnconditionalBranch(handler.getHandlerBlock()));

                            break HandlerBuilder;
                        }

                        BasicBlock handlerBlock = new BasicBlock(cfg);

                        handlerBlock.add(new ClearExceptionStateStmt());
                        handlerBlock.add(new UnconditionalBranch(handler.getHandlerBlock()));

                        BasicBlock nextBasicBlock = new BasicBlock(cfg);

                        currentBlock.add(new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.NOT_EQUAL, new InstanceOfExpr(handler.getType(), new VarExpr(caughtExceptionLocal)), new ConstIntExpr(0)), handlerBlock, nextBasicBlock));

                        currentBlock = nextBasicBlock;
                    }

                    // Exception wasn't caught? Stop method execution.
                    currentBlock.add(nullReturnStatement.copy());
                }

                handlerBlockMap.put(exceptionHandler.getValue(), handlerBegin);
                exceptionHandlerBlocks.put(exceptionHandler.getKey(), handlerBegin);
            }
        }

        // Start with a fresh basic block since there is not always a label at the beginning of a method
        var currentBasicBlock = new BasicBlock(cfg);

        // Tell the CFG to consider the entry block as an entry
        cfg.getEntries().add(currentBasicBlock);

        var paramStackIdx = 0;

        var argumentTypes = cfg.getArgumentTypes();

        // Put the method parameters in the stack
        for (int i = 0; i < argumentTypes.length; i++) {
            var argumentType = argumentTypes[i];

            currentBasicBlock.add(new CopyVarStmt(new VarExpr(cfg.getLocals().getLocal(paramStackIdx, argumentType)), new ParamExpr(cfg, i)));

            paramStackIdx += argumentType.getJvmStackSize();
        }

        LabelNode lastSeenLabel = null;

        for (AbstractInsnNode instruction : compilerMethod.getNode().instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                var nextBasicBlock = labelMap.get(((LabelNode) instruction));

                if (currentBasicBlock != null) {
                    // Ensure that the current basic block is present in the CFG
                    cfg.addVertex(currentBasicBlock);

                    if (!currentBasicBlock.isTerminated()) {
                        currentBasicBlock.add(new UnconditionalBranch(nextBasicBlock));
                    }
                }

                currentBasicBlock = nextBasicBlock;
                lastSeenLabel = (LabelNode) instruction;

                continue;
            }

            if (currentBasicBlock == null) {
                throw new IllegalStateException("Current basic block is null");
            }

            if (currentBasicBlock.isTerminated()) {
                throw new IllegalStateException("This should never ever happen.");
            }

            var locals = cfg.getLocals();
            var analysisContext = new OpcodeAnalysisContext(frames, compilerMethod.getNode().instructions);
            var currentFrame = analysisContext.getFrameOfInstruction(instruction);

            // Should an exception handler be build after the current instruction?
            boolean exceptionHandlerFlag = false;
            // True if we are sure that an exception occurred (used to implement ATHROW)
            boolean assumeExceptionOccurred = false;

            int opcode = instruction.getOpcode();

            switch (opcode) {
                case NOP: // visitInsn
                case POP:
                case POP2: {
                    // Do nothing lol
                    break;
                }
                case ACONST_NULL: {
                    _push(currentBasicBlock, currentFrame, new ConstNullExpr());
                    break;
                }
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                    _push(currentBasicBlock, currentFrame, new ConstIntExpr(opcode - ICONST_0));
                    break;
                case LCONST_0:
                case LCONST_1:
                    _push(currentBasicBlock, currentFrame, new ConstLongExpr(opcode - LCONST_0));
                    break;
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                    _push(currentBasicBlock, currentFrame, new ConstFloatExpr((float) (opcode - FCONST_0)));
                    break;
                case DCONST_0:
                case DCONST_1:
                    _push(currentBasicBlock, currentFrame, new ConstDoubleExpr(opcode - DCONST_0));
                    break;
                case BIPUSH:
                case SIPUSH:
                    _push(currentBasicBlock, currentFrame, new ConstIntExpr(((IntInsnNode) instruction).operand));
                    break;
                case LDC: {// visitLdcInsn
                    var cst = ((LdcInsnNode) instruction).cst;

                    if (cst instanceof Integer) {
                        _push(currentBasicBlock, currentFrame, new ConstIntExpr((Integer) cst));
                    } else if (cst instanceof Long) {
                        _push(currentBasicBlock, currentFrame, new ConstLongExpr((Long) cst));
                    } else if (cst instanceof Float) {
                        _push(currentBasicBlock, currentFrame, new ConstFloatExpr((Float) cst));
                    } else if (cst instanceof Double) {
                        _push(currentBasicBlock, currentFrame, new ConstDoubleExpr((Double) cst));
                    } else if (cst instanceof String) {
                        _push(currentBasicBlock, currentFrame, new ConstStringExpr((String) cst));
                    } else if (cst instanceof Type) {
                        _push(currentBasicBlock, currentFrame, new ConstTypeExpr((Type) cst));

                        exceptionHandlerFlag = true; // If the specified type was not found, this expression might throw an exception
                    } else {
                        throw new IllegalArgumentException("Invalid LDC");
                    }

                    break;
                }
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD: {
                    var local = ((VarInsnNode) instruction).var;

                    _push(currentBasicBlock, currentFrame, _lload(currentBasicBlock, local, OpcodeUtils.getLoadType(opcode)));
                    break;
                }
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE: {
                    var local = ((VarInsnNode) instruction).var;

                    _lstore(currentBasicBlock, local, _peek(currentBasicBlock, currentFrame, OpcodeUtils.getStoreType(opcode)));
                    break;
                }
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD: {
                    var jniType = OpcodeUtils.getArrayLoadJNIType(opcode);

                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT, 2);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new ArrayLoadExpr(
                                    jniType,
                                    instance,
                                    _peek(currentBasicBlock, currentFrame, ImmType.INT, 1)
                            ),
                            2
                    );

                    exceptionHandlerFlag = true;

                    break;
                }
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE: {
                    var jniType = OpcodeUtils.getArrayStoreJNIType(opcode);
                    var immType = ImmType.fromJNIType(jniType);

                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT, 3);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _build(
                            currentBasicBlock,
                            new ArrayStoreStmt(
                                    jniType,
                                    instance,
                                    _peek(currentBasicBlock, currentFrame, ImmType.INT, 2),
                                    _peek(currentBasicBlock, currentFrame, immType, 1)
                            )
                    );

                    exceptionHandlerFlag = true;

                    break;
                }
                case DUP:
                    _push(currentBasicBlock, currentFrame, _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0)));
                    break;
                case DUP_X1: {
                    var value1 = ensureCategory1(_peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0), 1));
                    var value2 = ensureCategory1(_peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1), 2));

                    _dupx1(currentBasicBlock, locals, currentFrame, value1, value2);
                    break;
                }
                case DUP_X2: {
                    var value1 = ensureCategory1(_peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0), 1));
                    var value2 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1), 2);

                    if (value2.getType().getJvmStackSize() == 2) {
                        _dupx1(currentBasicBlock, locals, currentFrame, value1, value2);
                    } else {
                        var value3 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 2), 3);

                        _dupx2(currentBasicBlock, locals, currentFrame, value1, value2, value3);
                    }
                    break;
                }
                case DUP2: {
                    var value1 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0), 1);

                    if (value1.getType().getJvmStackSize() == 2) {
                        _push(currentBasicBlock, currentFrame, value1);
                    } else {
                        ensureCategory1(value1);

                        var value2 = ensureCategory1(_peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1), 2));

                        _push(currentBasicBlock, currentFrame, value2);
                        _push(currentBasicBlock, currentFrame, value1, -1);
                    }

                    break;
                }
                case DUP2_X1: {
                    var value1 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0), 1);
                    var value2 = ensureCategory1(_peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1), 2));

                    if (value1.getType().getJvmStackSize() == 2) {
                        _dupx1(currentBasicBlock, locals, currentFrame, value1, value2);
                    } else {
                        var value3 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 2), 3);

                        _dupx3(currentBasicBlock, currentFrame, locals, value1, value2, value3);
                    }

                    break;
                }
                case DUP2_X2: {
                    var value1 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0), 1);
                    var value2 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1), 2);

                    if (value1.getType().getJvmStackSize() == 2 && value2.getType().getJvmStackSize() == 2) {
                        _dupx1(currentBasicBlock, locals, currentFrame, value1, value2);
                        break;
                    }
                    var value3 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 2), 3);

                    if (value3.getType().getJvmStackSize() == 2) {
                        _dupx3(currentBasicBlock, currentFrame, locals, value1, value2, value3);
                    } else if (value1.getType().getJvmStackSize() == 2) {
                        _dupx2(currentBasicBlock, locals, currentFrame, value1, value2, value3);
                    } else {
                        var value4 = _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 3), 4);

                        var tmpValue1 = _buildTmpVar(currentBasicBlock, locals, value1);
                        var tmpValue2 = _buildTmpVar(currentBasicBlock, locals, value2);
                        var tmpValue3 = _buildTmpVar(currentBasicBlock, locals, value3);
                        var tmpValue4 = _buildTmpVar(currentBasicBlock, locals, value4);

                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue2), 4);
                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), 3);
                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue4), 2);
                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue3), 1);
                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue2));
                        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), -1);
                    }

                    break;
                }
                case SWAP:
                    _push(currentBasicBlock, currentFrame, _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 0)), 2);
                    _push(currentBasicBlock, currentFrame, _peek(currentBasicBlock, currentFrame, getStackFrameType(analysisContext, instruction, 1)), 1);
                    break;
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
                    var type = OpcodeUtils.getIntegerArithmeticsType(opcode);

                    // TODO: Implement throwing ArithmeticException

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new IntegerArithmeticsExpr(
                                    OpcodeUtils.getIntegerArithmeticsOperator(opcode),
                                    type,
                                    _peek(currentBasicBlock, currentFrame, type.getImmType(), 2),
                                    _peek(currentBasicBlock, currentFrame, type.getImmType(), 1)
                            ),
                            2);
                    break;
                }
                case LUSHR:
                case LSHR:
                case LSHL: {
                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new IntegerArithmeticsExpr(
                                    OpcodeUtils.getIntegerArithmeticsOperator(opcode),
                                    IntegerArithmeticsExpr.IntegerType.LONG,
                                    _peek(currentBasicBlock, currentFrame, ImmType.LONG, 2),
                                    _peek(currentBasicBlock, currentFrame, ImmType.INT, 1)
                            ),
                            2);
                    break;
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
                    var type = OpcodeUtils.getFloatingPointArithmeticsType(opcode);

                    // TODO: Implement throwing ArithmeticException

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new FloatingPointArithmeticsExpr(
                                    OpcodeUtils.getFloatingPointArithmeticsOperator(opcode),
                                    type,
                                    _peek(currentBasicBlock, currentFrame, type.getImmType(), 2),
                                    _peek(currentBasicBlock, currentFrame, type.getImmType(), 1)
                            ),
                            2);
                    break;
                }
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG: {
                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new NegationExpr(_peek(currentBasicBlock, currentFrame, OpcodeUtils.getNegationType(opcode))),
                            1
                    );

                    break;
                }
                case IINC: {
                    var iinc = (IincInsnNode) instruction;
                    var affectedLocal = cfg.getLocals().getLocal(iinc.var, ImmType.INT);

                    _lstore(currentBasicBlock, iinc.var, new IntegerArithmeticsExpr(IntegerArithmeticsExpr.Operator.ADD, IntegerArithmeticsExpr.IntegerType.INT, new VarExpr(affectedLocal), new ConstIntExpr(iinc.incr)));

                    break;
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
                    var inputType = OpcodeUtils.getCastInputType(opcode);
                    var target = OpcodeUtils.getCastTarget(opcode);

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new PrimitiveCastExpr(
                                    _peek(currentBasicBlock, currentFrame, inputType),
                                    target
                            ),
                            1
                    );

                    break;
                }
                case LCMP: {
                    var lhs = _peek(currentBasicBlock, currentFrame, ImmType.LONG, 2);
                    var rhs = _peek(currentBasicBlock, currentFrame, ImmType.LONG, 1);
                    var tmpVar = locals.allocSynthetic(ImmType.INT);

                    var greaterBlock = new BasicBlock(cfg);
                    var lowerBlock = new BasicBlock(cfg);
                    var eqBlock = new BasicBlock(cfg);
                    var neqBlock = new BasicBlock(cfg);
                    var nextBlock = new BasicBlock(cfg);

                    cfg.addVertex(currentBasicBlock);

                    // if (lhs == rhs)

                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.EQUAL, lhs, rhs), eqBlock, neqBlock)
                    );

                    currentBasicBlock = neqBlock;

                    // if (lhs < rhs)
                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.LOWER, lhs.copy(), rhs.copy()), lowerBlock, greaterBlock)
                    );

                    // eq Block
                    currentBasicBlock = eqBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(0)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    // lower Block
                    currentBasicBlock = lowerBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(-1)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    // lower Block
                    currentBasicBlock = greaterBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(1)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    currentBasicBlock = nextBlock;

                    _push(currentBasicBlock, currentFrame, new VarExpr(tmpVar), 2);

                    break;
                }
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG: {
                    var comparedType = opcode >= FCMPG ? FloatingPointArithmeticsExpr.FloatingPointType.DOUBLE : FloatingPointArithmeticsExpr.FloatingPointType.FLOAT;


                    var lhs = _peek(currentBasicBlock, currentFrame, comparedType.getImmType(), 2);
                    var rhs = _peek(currentBasicBlock, currentFrame, comparedType.getImmType(), 1);
                    var tmpVar = locals.allocSynthetic(ImmType.INT);

                    var greaterBlock = new BasicBlock(cfg);
                    var lowerBlock = new BasicBlock(cfg);
                    var eqBlock = new BasicBlock(cfg);
                    var notNaNBlock = new BasicBlock(cfg);
                    var neqBlock = new BasicBlock(cfg);
                    var nextBlock = new BasicBlock(cfg);

                    var nanBlock = (opcode == FCMPL || opcode == DCMPL) ? lowerBlock : greaterBlock;

                    cfg.addVertex(currentBasicBlock);

                    // if (isnan(lhs) || isnan(rhs))

                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.UNORDERED, lhs, rhs), nanBlock, notNaNBlock)
                    );

                    currentBasicBlock = notNaNBlock;

                    // if (lhs == rhs)

                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.EQUAL, lhs.copy(), rhs.copy()), eqBlock, neqBlock)
                    );

                    currentBasicBlock = neqBlock;

                    // if (lhs < rhs)
                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(new FloatingPointCompareExpr(comparedType, FloatingPointCompareExpr.Operator.LOWER, lhs.copy(), rhs.copy()), lowerBlock, greaterBlock)
                    );

                    // eq Block
                    currentBasicBlock = eqBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(0)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    // lower Block
                    currentBasicBlock = lowerBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(-1)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    // lower Block
                    currentBasicBlock = greaterBlock;

                    _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpVar), new ConstIntExpr(1)));
                    _build(currentBasicBlock, new UnconditionalBranch(nextBlock));

                    currentBasicBlock = nextBlock;

                    _push(currentBasicBlock, currentFrame, new VarExpr(tmpVar), 2);

                    break;
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
                    var ifTarget = labelMap.get(((JumpInsnNode) instruction).label);
                    var elseTarget = new BasicBlock(cfg);

                    Expr lhs;
                    Expr rhs;

                    if (opcode <= IFLE) {
                        lhs = _peek(currentBasicBlock, currentFrame, ImmType.INT);
                        rhs = new ConstIntExpr(0);
                    } else {
                        lhs = _peek(currentBasicBlock, currentFrame, ImmType.INT, 2);
                        rhs = _peek(currentBasicBlock, currentFrame, ImmType.INT);
                    }

                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(
                                    new IntegerCompareExpr(OpcodeUtils.getIntegerCompareType(opcode), lhs, rhs),
                                    ifTarget,
                                    elseTarget
                            )
                    );

                    cfg.addVertex(currentBasicBlock);

                    currentBasicBlock = elseTarget;

                    break;
                }
                case IF_ACMPEQ:
                case IF_ACMPNE:
                case IFNULL:
                case IFNONNULL: {
                    var ifTarget = labelMap.get(((JumpInsnNode) instruction).label);
                    var elseTarget = new BasicBlock(cfg);

                    var rhs = (opcode == IFNULL || opcode == IFNONNULL) ? new ConstNullExpr() : _peek(currentBasicBlock, currentFrame, ImmType.OBJECT, 2);

                    BasicBlock effectiveElseTarget = elseTarget;

                    if (OpcodeUtils.isObjectConvertInverted(opcode)) {
                        var tmp = ifTarget;

                        ifTarget = elseTarget;
                        effectiveElseTarget = tmp;
                    }

                    _build(
                            currentBasicBlock,
                            new ConditionalBranch(
                                    new ObjectCompareExpr(_peek(currentBasicBlock, currentFrame, ImmType.OBJECT), rhs),
                                    ifTarget,
                                    effectiveElseTarget
                            )
                    );

                    cfg.addVertex(currentBasicBlock);

                    currentBasicBlock = elseTarget;

                    break;
                }
                case GOTO:
                    _build(
                            currentBasicBlock,
                            new UnconditionalBranch(labelMap.get(((JumpInsnNode) instruction).label))
                    );

                    cfg.addVertex(currentBasicBlock);

                    currentBasicBlock = null;

                    break;
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

                        defaultBlock = Objects.requireNonNull(labelMap.get(tableSwitch.dflt));
                        basicBlocks = IntStream.range(tableSwitch.min, tableSwitch.max + 1).mapToObj(x -> Objects.requireNonNull(labelMap.get(tableSwitch.labels.get(x)))).toArray(BasicBlock[]::new);
                        keys = IntStream.range(tableSwitch.min, tableSwitch.max + 1).toArray();
                    } else {
                        LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) instruction;

                        defaultBlock = Objects.requireNonNull(labelMap.get(lookupSwitch.dflt));
                        basicBlocks = lookupSwitch.labels.stream().map(x -> Objects.requireNonNull(labelMap.get(x))).toArray(BasicBlock[]::new);
                        keys = lookupSwitch.keys.stream().mapToInt(Integer::intValue).toArray();
                    }

                    _build(currentBasicBlock, new SwitchStmt(_peek(currentBasicBlock, currentFrame, ImmType.INT), keys, basicBlocks, defaultBlock));

                    if (instruction.getNext() != null && instruction.getNext().getType() != AbstractInsnNode.LABEL)
                        throw new IllegalStateException("Unlabled code after switch instruction");

                    currentBasicBlock = null;

                    break;
                }
                case DRETURN:
                case LRETURN:
                case IRETURN:
                case FRETURN:
                case ARETURN: {
                    var type = OpcodeUtils.getValueReturnType(opcode);

                    _build(currentBasicBlock, new RetStmt(_peek(currentBasicBlock, currentFrame, type)));
                    break;
                }
                case RETURN:
                    _build(currentBasicBlock, new RetVoidStmt());
                    break;
                case GETSTATIC: {
                    _push(currentBasicBlock, currentFrame, new GetStaticExpr(new MethodOrFieldIdentifier((FieldInsnNode) instruction)));
                    break;
                }
                case GETFIELD: {
                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new GetFieldExpr(
                                    new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                    instance
                            ),
                            1
                    );

                    break;
                }
                case PUTSTATIC: {
                    MethodOrFieldIdentifier target = new MethodOrFieldIdentifier((FieldInsnNode) instruction);

                    _build(
                            currentBasicBlock,

                            new PutStaticStmt(
                                    target,
                                    _peek(currentBasicBlock, currentFrame, ImmType.fromJVMType(Type.getType(target.getDesc())))
                            )
                    );

                    break;
                }
                case PUTFIELD: {
                    var instanceValue = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT, 2);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instanceValue.copy());

                    MethodOrFieldIdentifier target = new MethodOrFieldIdentifier((FieldInsnNode) instruction);

                    _build(
                            currentBasicBlock,
                            new PutFieldStmt(
                                    target,
                                    instanceValue,
                                    _peek(currentBasicBlock, currentFrame, ImmType.fromJVMType(Type.getType(target.getDesc())), 1)
                            )
                    );

                    break;
                }
                case INVOKESTATIC: {
                    MethodOrFieldIdentifier target = new MethodOrFieldIdentifier(((MethodInsnNode) instruction));
                    var argTypes = Type.getArgumentTypes(target.getDesc());

                    BasicBlock finalCurrentBasicBlock = currentBasicBlock;

                    var callExpr = new InvokeStaticExpr(
                            target,
                            IntStream.range(0, argTypes.length)
                                    .mapToObj(idx -> _peek(finalCurrentBasicBlock, currentFrame, ImmType.fromJVMType(argTypes[idx]), argTypes.length - idx))
                                    .toArray(Expr[]::new)
                    );

                    if (Type.getReturnType(target.getDesc()).getSort() == Type.VOID) {
                        _build(currentBasicBlock, new ExpressionStmt(callExpr));
                    } else {
                        _push(currentBasicBlock, currentFrame, callExpr, argTypes.length);
                    }

                    exceptionHandlerFlag = true;

                    break;
                }
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKEINTERFACE: {
                    MethodOrFieldIdentifier target = new MethodOrFieldIdentifier(((MethodInsnNode) instruction));
                    var argTypes = Type.getArgumentTypes(target.getDesc());

                    if (target.getOwner().equals("java/lang/invoke/MethodHandle")) {
                        throw new IllegalStateException("MethodHandle function calls cannot be compiled to and should already have been filtered out by one the preprocessors.");
                    }

                    BasicBlock finalCurrentBasicBlock = currentBasicBlock;

                    Expr instance = _peek(finalCurrentBasicBlock, currentFrame, ImmType.OBJECT, argTypes.length + 1);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    var callExpr = new InvokeInstanceExpr(
                            target,
                            instance,
                            IntStream.range(0, argTypes.length)
                                    .mapToObj(idx -> _peek(finalCurrentBasicBlock, currentFrame, ImmType.fromJVMType(argTypes[idx]), argTypes.length - idx))
                                    .toArray(Expr[]::new),
                            OpcodeUtils.getInvokeInstanceType(opcode)
                    );

                    if (Type.getReturnType(target.getDesc()).getSort() == Type.VOID) {
                        _build(currentBasicBlock, new ExpressionStmt(callExpr));
                    } else {
                        _push(currentBasicBlock, currentFrame, callExpr, argTypes.length + 1);
                    }

                    exceptionHandlerFlag = true;

                    break;
                }
                case INVOKEDYNAMIC:
                    throw new IllegalStateException("INVOKEDYNAMIC is not implemented and should already have been filtered out by one the preprocessors.");
                case NEW: {
                    // TODO Should an exception check be implemented here?
                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new AllocObjectExpr(((TypeInsnNode) instruction).desc)
                    );

                    break;
                }
                case NEWARRAY:
                case ANEWARRAY: {
                    Type type = opcode == NEWARRAY ? OpcodeUtils.getNewArrayType(((IntInsnNode) instruction).operand) : Type.getObjectType(((TypeInsnNode) instruction).desc);

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new AllocArrayExpr(type, _peek(currentBasicBlock, currentFrame, ImmType.INT)),
                            1
                    );
                    break;
                }
                case MULTIANEWARRAY:
                    throw new IllegalStateException("MULTIANEWARRAY is not implemented and should already have been filtered out by one the preprocessors.");
                case ARRAYLENGTH: {
                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new ArrayLengthExpr(instance),
                            1
                    );

                    exceptionHandlerFlag = true;
                    break;
                }
                case ATHROW: {
                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _build(
                            currentBasicBlock,
                            new ThrowStmt(instance)
                    );

                    exceptionHandlerFlag = true;
                    assumeExceptionOccurred = true; // Tell the exception handler builder to unconditionally jump
                    break;
                }
                case CHECKCAST: {
                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new CheckCastExpr(((TypeInsnNode) instruction).desc, _peek(currentBasicBlock, currentFrame, ImmType.OBJECT)),
                            1
                    );


                    exceptionHandlerFlag = true;
                    break;
                }
                case INSTANCEOF:
                    _push(
                            currentBasicBlock,
                            currentFrame,
                            new InstanceOfExpr(((TypeInsnNode) instruction).desc, _peek(currentBasicBlock, currentFrame, ImmType.OBJECT)),
                            1
                    );
                    break;
                case MONITORENTER:
                case MONITOREXIT: {
                    Expr instance = _peek(currentBasicBlock, currentFrame, ImmType.OBJECT);

                    currentBasicBlock = _buildNullCheck(cfg, defaultExceptionHandler, exceptionHandlerBlocks, currentBasicBlock, lastSeenLabel, locals, instance.copy());

                    _build(
                            currentBasicBlock,
                            new MonitorStmt(
                                    opcode == MONITORENTER ? MonitorStmt.MonitorType.ENTER : MonitorStmt.MonitorType.EXIT,
                                    instance
                            )
                    );

                    exceptionHandlerFlag = true;
                    break;
                }
            }

            // Build an exception check if needed
            if (exceptionHandlerFlag) {
                if (currentBasicBlock.isTerminated())
                    throw new IllegalStateException("This should not happen.");

                BasicBlock handlerBlock = getExceptionHandlerBlock(defaultExceptionHandler, exceptionHandlerBlocks, lastSeenLabel);

                if (assumeExceptionOccurred) {
                    currentBasicBlock.add(new UnconditionalBranch(handlerBlock));
                } else {
                    var nextBlock = new BasicBlock(cfg);

                    currentBasicBlock.add(new ExceptionCheckStmt(nextBlock, handlerBlock));

                    currentBasicBlock = nextBlock;
                }
            }
        }


        // Ensure that the last basic block is also in the CFG
        if (currentBasicBlock != null) {
            cfg.addVertex(currentBasicBlock);
        }

        return cfg;
    }

    private static BasicBlock _buildNullCheck(ControlFlowGraph cfg, BasicBlock defaultExceptionHandler, HashMap<LabelNode, BasicBlock> exceptionHandlerBlocks, BasicBlock currentBasicBlock, LabelNode lastSeenLabel, LocalsPool locals, Expr instanceValue) {
        BasicBlock ifNull = new BasicBlock(cfg);
        BasicBlock notNull = new BasicBlock(cfg);

        _build(currentBasicBlock,
                new ConditionalBranch(
                        new ObjectCompareExpr(instanceValue, new ConstNullExpr()),
                        ifNull,
                        notNull
                ));

        var npeVar = locals.allocSynthetic(ImmType.OBJECT);

        _build(ifNull, new CopyVarStmt(new VarExpr(npeVar), new AllocObjectExpr("java/lang/NullPointerException")));
        _build(ifNull, new ExpressionStmt(new InvokeInstanceExpr(new MethodOrFieldIdentifier("java/lang/NullPointerException", "<init>", "()V"), new VarExpr(npeVar), new Expr[0], InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL)));
        _build(ifNull, new ThrowStmt(new VarExpr(npeVar)));
        _build(ifNull, new UnconditionalBranch(getExceptionHandlerBlock(defaultExceptionHandler, exceptionHandlerBlocks, lastSeenLabel)));
        return notNull;
    }

    private static BasicBlock getExceptionHandlerBlock(BasicBlock defaultExceptionHandler, HashMap<LabelNode, BasicBlock> exceptionHandlerBlocks, LabelNode lastSeenLabel) {
        var handlerBlock = lastSeenLabel != null ? exceptionHandlerBlocks.get(lastSeenLabel) : null;

        // When there is no exception handler specified, just jump to the default handler
        if (handlerBlock == null) {
            handlerBlock = defaultExceptionHandler;
        }
        return handlerBlock;
    }

    private static void _dupx3(BasicBlock currentBasicBlock, Frame<SourceValue> currentFrame, LocalsPool locals, Expr value1, Expr value2, Expr value3) {
        var tmpValue1 = _buildTmpVar(currentBasicBlock, locals, value1);
        var tmpValue2 = _buildTmpVar(currentBasicBlock, locals, value2);

        _push(currentBasicBlock, currentFrame, value3, 1);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue2), 3);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue2));
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), 2);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), -1);
    }

    private static void _dupx2(BasicBlock currentBasicBlock, LocalsPool locals, Frame<SourceValue> currentFrame, Expr value1, Expr value2, Expr value3) {
        var tmpValue1 = _buildTmpVar(currentBasicBlock, locals, value1);
        var tmpValue2 = _buildTmpVar(currentBasicBlock, locals, value2);

        _push(currentBasicBlock, currentFrame, value3, 2);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue2), 1);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), 3);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1));
    }

    private static void _dupx1(BasicBlock currentBasicBlock, LocalsPool locals, Frame<SourceValue> currentFrame, Expr value1, Expr value2) {
        var tmpValue1 = _buildTmpVar(currentBasicBlock, locals, value1);

        _push(currentBasicBlock, currentFrame, value2, 1);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1), 2);
        _push(currentBasicBlock, currentFrame, new VarExpr(tmpValue1));
    }

    private static Local _buildTmpVar(BasicBlock currentBasicBlock, LocalsPool locals, Expr value1) {
        var tmpValue1 = locals.allocSynthetic(value1.getType());

        _build(currentBasicBlock, new CopyVarStmt(new VarExpr(tmpValue1), value1));

        return tmpValue1;
    }

    private static Expr ensureCategory1(Expr expr) {
        if (expr.getType().getJvmStackSize() != 1)
            throw new IllegalStateException("This instruction shall only be used with category 1 types");

        return expr;
    }

    private static ImmType getStackFrameType(OpcodeAnalysisContext analysisContext, AbstractInsnNode instruction, int off) {
        var currentFrame = analysisContext.getFrameOfInstruction(instruction);
        var stack = currentFrame.getStack(currentFrame.getStackSize() - 1 - off);

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

    private static void _build(BasicBlock basicBlock, Stmt stmt) {
        basicBlock.add(stmt);
    }

    private static void _push(BasicBlock block, Frame<SourceValue> frame, Expr expr) {
        _push(block, frame, expr, 0);
    }

    private static void _push(BasicBlock block, Frame<SourceValue> frame, Expr expr, int offset) {
        block.add(new CopyVarStmt(new VarExpr(block.getGraph().getLocals().getStackLocal(frame.getStackSize() - offset, expr.getType())), expr));
    }

    private static void _lstore(BasicBlock block, int local, Expr expr) {
        block.add(new CopyVarStmt(new VarExpr(block.getGraph().getLocals().getLocal(local, expr.getType())), expr));
    }

    private static Expr _lload(BasicBlock block, int local, ImmType type) {
        return new VarExpr(block.getGraph().getLocals().getLocal(local, type));
    }

    private static Expr _peek(BasicBlock block, Frame<SourceValue> frame, ImmType type) {
        return _peek(block, frame, type, 1);
    }

    private static Expr _peek(BasicBlock block, Frame<SourceValue> frame, ImmType type, int depth) {
        return new VarExpr(block.getGraph().getLocals().getStackLocal(frame.getStackSize() - depth, type));
    }

}
