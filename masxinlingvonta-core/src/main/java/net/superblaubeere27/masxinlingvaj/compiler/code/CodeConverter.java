package net.superblaubeere27.masxinlingvaj.compiler.code;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.*;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches.*;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants.ConstantNumberInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants.NullInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants.StringInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.constants.TypeInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.stackmanipulation.*;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.utils.OpcodeUtils;
import net.superblaubeere27.masxinlingvaj.utils.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * Converts a method body to a list of basic blocks
 */
public class CodeConverter implements Opcodes {

    public static ArrayList<Block> convert(MLVCompiler compiler, CompilerMethod compilerMethod, TranslatedMethod translatedMethod) throws AnalyzerException {
        var frames = new Analyzer<>(new SourceInterpreter()).analyze(compilerMethod.getParent().getName(),
                                                                     compilerMethod.getNode());

        var blocks = new ArrayList<Block>();

        var labelMap = new HashMap<AbstractInsnNode, Block>();

        for (AbstractInsnNode instruction : compilerMethod.getNode().instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                labelMap.put(instruction, new Block());
            }
        }

        var currentBlockInstructions = new ArrayList<Instruction>();
        var currentBlock = new Block();
        var nextBlock = new Block();

        // Was the instruction that terminated the last basic block a terminating instruction?
        // If it wasn't and the following for-loop terminates, the method being translated could run out of instructions
        var wasTerminated = false;

        for (AbstractInsnNode instruction : compilerMethod.getNode().instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                var next = labelMap.get(instruction);

                // Terminate the current block and make the execution continue on the next one
                currentBlockInstructions.add(new UnconditionalBranchInstruction(next));

                // Set the instructions of the current block, add it to the list of blocks and clear the list of current instructions
                currentBlock.setInstructions(currentBlockInstructions.toArray(new Instruction[0]));
                blocks.add(currentBlock);
                currentBlockInstructions.clear();

                // Set the current block to the block that corresponds to the label
                currentBlock = next;

                continue;
            }

            var convertedInstruction = convertInstruction(compiler,
                                                          instruction,
                                                          frames[compilerMethod.getNode().instructions.indexOf(
                                                                  instruction)],
                                                          labelMap,
                                                          nextBlock);

            if (convertedInstruction == null)
                continue;

            // Add the instruction to the current block
            currentBlockInstructions.add(convertedInstruction);

            // Does the current instruction end the basic block?
            // If it does, end it
            if (convertedInstruction.isTerminating()) {
                // Set the instructions of the current block, add it to the list of blocks and clear the list of current instructions
                currentBlock.setInstructions(currentBlockInstructions.toArray(new Instruction[0]));
                currentBlockInstructions.clear();

                blocks.add(currentBlock);

                currentBlock = nextBlock;
                nextBlock = new Block();
                wasTerminated = true;
            }
        }

        if (compilerMethod.getNode().tryCatchBlocks != null) {
            addExceptionHandlers(compilerMethod, labelMap);
        }

        if (!wasTerminated) {
            throw new IllegalStateException("The method could run out of instructions to execute");
        }

        // Add the blocks to the translated method
        for (Block block : blocks) {
            block.addToMethod(translatedMethod);
        }

        return blocks;
    }

    private static void addExceptionHandlers(CompilerMethod compilerMethod, HashMap<AbstractInsnNode, Block> labelMap) {
        for (TryCatchBlockNode tryCatchBlock : compilerMethod.getNode().tryCatchBlocks) {
            var exceptionHandler = new ExceptionHandler(tryCatchBlock,
                                                        Objects.requireNonNull(labelMap.get(tryCatchBlock.handler)));

            AbstractInsnNode currentInstruction = tryCatchBlock.start;

            do {
                if (currentInstruction == null)
                    throw new IllegalStateException("Invalid exception bounds");

                // Is this instruction a label? If it is, add this exception handler to the corresponding block
                if (currentInstruction.getType() == AbstractInsnNode.LABEL) {
                    labelMap.get(currentInstruction).addExceptionHandler(exceptionHandler);
                }

                currentInstruction = currentInstruction.getNext();
            } while (currentInstruction != tryCatchBlock.end);
        }
    }

    private static Instruction convertInstruction(MLVCompiler compiler, AbstractInsnNode instruction, Frame<SourceValue> stackFrame, HashMap<AbstractInsnNode, Block> labelMap, Block nextBlock) {
        var opcode = instruction.getOpcode();

        switch (opcode) {
            case NOP: // visitInsn
            case POP:
            case POP2:
            case -1:
                // Do nothing
                return null;
            case DUP2: {
                var stackTypeA = getStackFrameType(compiler, stackFrame.getStack(stackFrame.getStackSize() - 2));

                if (!stackTypeA.isWide()) {
                    return new Dup2Instruction(stackTypeA,
                                               getStackFrameType(compiler,
                                                                 stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                               stackFrame.getStackSize());
                }
            }
            case DUP: {
                return new DupInstruction(new StackSlot(getStackFrameType(compiler,
                                                                          stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                                        stackFrame.getStackSize() - 1),
                                          new StackSlot(getStackFrameType(compiler,
                                                                          stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                                        stackFrame.getStackSize()));
            }
            case DUP_X2: {
                if (!getStackFrameType(compiler, stackFrame.getStack(stackFrame.getStackSize() - 2)).isWide()) {
                    return new DupX2Instruction(getStackFrameType(compiler,
                                                                  stackFrame.getStack(stackFrame.getStackSize() - 3)),
                                                getStackFrameType(compiler,
                                                                  stackFrame.getStack(stackFrame.getStackSize() - 2)),
                                                getStackFrameType(compiler,
                                                                  stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                                stackFrame.getStackSize());
                }
            }
            case DUP_X1:
                return new DupX1Instruction(getStackFrameType(compiler,
                                                              stackFrame.getStack(stackFrame.getStackSize() - 2)),
                                            getStackFrameType(compiler,
                                                              stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                            stackFrame.getStackSize());
//            case DUP2_X1:
//            case DUP2_X2:
            case SWAP:
                return new SwapInstruction(new StackSlot(getStackFrameType(compiler,
                                                                           stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                                         stackFrame.getStackSize() - 1),
                                           new StackSlot(getStackFrameType(compiler,
                                                                           stackFrame.getStack(stackFrame.getStackSize() - 1)),
                                                         stackFrame.getStackSize()));
//                // Do nothing
//                return NoopInstruction.INSTANCE;
            case ACONST_NULL:
                return new NullInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize()));
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                // Push a constant number
                return new ConstantNumberInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize()),
                                                     opcode - ICONST_0);
            case LCONST_0:
            case LCONST_1:
                return new ConstantNumberInstruction(new StackSlot(JNIType.LONG, stackFrame.getStackSize()),
                                                     (long) (opcode - LCONST_0));
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                return new ConstantNumberInstruction(new StackSlot(JNIType.FLOAT, stackFrame.getStackSize()),
                                                     (float) (opcode - FCONST_0));
            case DCONST_0:
            case DCONST_1:
                return new ConstantNumberInstruction(new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize()),
                                                     (double) (opcode - DCONST_0));
            case BIPUSH: // visitIntInsn
            case SIPUSH:
                return new ConstantNumberInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize()),
                                                     ((IntInsnNode) instruction).operand);
            case LDC: {// visitLdcInsn
                var cst = ((LdcInsnNode) instruction).cst;

                if (cst instanceof Integer) {
                    return new ConstantNumberInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize()),
                                                         (Integer) cst);
                } else if (cst instanceof Long) {
                    return new ConstantNumberInstruction(new StackSlot(JNIType.LONG, stackFrame.getStackSize()),
                                                         (Long) cst);
                } else if (cst instanceof Float) {
                    return new ConstantNumberInstruction(new StackSlot(JNIType.FLOAT, stackFrame.getStackSize()),
                                                         (Float) cst);
                } else if (cst instanceof Double) {
                    return new ConstantNumberInstruction(new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize()),
                                                         (Double) cst);
                } else if (cst instanceof String) {
                    return new StringInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize()),
                                                 (String) cst);
                } else if (cst instanceof Type) {
                    return new TypeInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize()), (Type) cst);
                }

                throw new IllegalArgumentException("Invalid LDC");
            }
            case ILOAD: // visitVarInsn
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD:
                return new LocalInstruction(new StackSlot(compiler.getJni().toNativeType(OpcodeUtils.getReturnType(
                        instruction)), stackFrame.getStackSize()), ((VarInsnNode) instruction).var, false);
            case IALOAD: // visitInsn
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD: {
                var type = OpcodeUtils.getReturnType(instruction);

                return new ArrayModificationInstruction(
                        new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 2),
                        new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                        new StackSlot(compiler.getJni().toNativeType(type).getStackStorageType(),
                                      stackFrame.getStackSize() - 2),
                        false
                );
            }
            case ISTORE: // visitVarInsn
            case LSTORE:
            case FSTORE:
            case DSTORE:
            case ASTORE: {
                var type = OpcodeUtils.getReturnType(instruction);

                return new LocalInstruction(
                        new StackSlot(compiler.getJni().toNativeType(type), stackFrame.getStackSize() - 1),
                        ((VarInsnNode) instruction).var,
                        true);
            }
            case IASTORE: // visitInsn
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE: {
                var type = OpcodeUtils.getReturnType(instruction);

                return new ArrayModificationInstruction(
                        new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 3),
                        new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                        new StackSlot(compiler.getJni().toNativeType(OpcodeUtils.getReturnType(instruction)).getStackStorageType(),
                                      stackFrame.getStackSize() - 1),
                        true
                );
            }
            case IADD:
            case LADD:
            case FADD:
            case DADD:
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
            case IREM:
            case LREM:
            case FREM:
            case DREM:
            case ISHL:
            case LSHL:
            case ISHR:
            case LSHR:
            case IUSHR:
            case LUSHR:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR: {
                var type = OpcodeUtils.getReturnType(instruction);
                var jniType = compiler.getJni().toNativeType(type);

                return new BinaryOperationInstruction(
                        new StackSlot(jniType, stackFrame.getStackSize() - 2),
                        new StackSlot(jniType, stackFrame.getStackSize() - 1),
                        new StackSlot(jniType, stackFrame.getStackSize() - 2),
                        OpcodeUtils.getBinaryOperation(instruction.getOpcode())
                );
            }
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG: {
                var type = OpcodeUtils.getReturnType(instruction);
                var jniType = compiler.getJni().toNativeType(type);

                return new UnaryInstruction(
                        new StackSlot(jniType, stackFrame.getStackSize() - 1),
                        new StackSlot(jniType, stackFrame.getStackSize() - 1),
                        UnaryInstruction.UnaryOperationType.NEG
                );
            }
            case IINC: // visitIincInsn
                return new IincInstruction(((IincInsnNode) instruction).var, ((IincInsnNode) instruction).incr);
            case I2L: // visitInsn
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
            case I2S:
                return PrimitiveCastInstruction.fromOpcode(instruction.getOpcode(), stackFrame.getStackSize());
            case FCMPG:
                return new NumberCompareInstruction(new StackSlot(JNIType.FLOAT, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.FLOAT, stackFrame.getStackSize() - 1),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    true);
            case DCMPG:
                return new NumberCompareInstruction(new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize() - 1),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    true);
            case LCMP:
                return new NumberCompareInstruction(new StackSlot(JNIType.LONG, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.LONG, stackFrame.getStackSize() - 1),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    false);
            case FCMPL:
                return new NumberCompareInstruction(new StackSlot(JNIType.FLOAT, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.FLOAT, stackFrame.getStackSize() - 1),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    false);
            case DCMPL: {
                return new NumberCompareInstruction(new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.DOUBLE, stackFrame.getStackSize() - 1),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    false);
            }
            case IFEQ: // visitJumpInsn
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
                return new CmpToZeroBranchInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                                      labelMap.get(((JumpInsnNode) instruction).label),
                                                      nextBlock,
                                                      ComparisionType.fromOpcode(instruction.getOpcode()));
            case IFNULL: // visitJumpInsn
            case IFNONNULL:
                return new CmpToZeroBranchInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                                      labelMap.get(((JumpInsnNode) instruction).label),
                                                      nextBlock,
                                                      ComparisionType.fromOpcode(instruction.getOpcode()));
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
                return new CompareBranchInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                                    labelMap.get(((JumpInsnNode) instruction).label),
                                                    nextBlock,
                                                    ComparisionType.fromOpcode(instruction.getOpcode()));
            case IF_ACMPEQ:
            case IF_ACMPNE:
                return new CompareBranchInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 2),
                                                    new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                                    labelMap.get(((JumpInsnNode) instruction).label),
                                                    nextBlock,
                                                    ComparisionType.fromOpcode(instruction.getOpcode()));
            case GOTO:
                return new UnconditionalBranchInstruction(labelMap.get(((JumpInsnNode) instruction).label));
//            case JSR: 
//            case RET: // visitVarInsn
            case TABLESWITCH: {
                var lut = (TableSwitchInsnNode) instruction;

                var cases = new SwitchInstruction.SwitchCase[lut.max - lut.min + 1];

                for (int i = 0; i < lut.max - lut.min + 1; i++) {
                    cases[i] = new SwitchInstruction.SwitchCase(lut.min + i, labelMap.get(lut.labels.get(i)));
                }

                return new SwitchInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                             labelMap.get(lut.dflt),
                                             cases);
            }
            case LOOKUPSWITCH: {
                var lus = (LookupSwitchInsnNode) instruction;

                var cases = new SwitchInstruction.SwitchCase[lus.keys.size()];

                for (int i = 0; i < lus.keys.size(); i++) {
                    cases[i] = new SwitchInstruction.SwitchCase(lus.keys.get(i), labelMap.get(lus.labels.get(i)));
                }

                return new SwitchInstruction(new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                             labelMap.get(lus.dflt),
                                             cases);
            }
            case IRETURN: // visitInsn
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN: {
                var type = OpcodeUtils.getReturnType(instruction);

                return new ValueReturnInstruction(new StackSlot(compiler.getJni().toNativeType(OpcodeUtils.getReturnType(
                        instruction)), stackFrame.getStackSize() - 1));
            }
            case RETURN:
                return new ReturnInstruction();
            case GETSTATIC: {// visitFieldInsn
                var type = compiler.getJni().toNativeType(Type.getType(((FieldInsnNode) instruction).desc));

                return new StaticFieldInstruction(new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                                  new StackSlot(type.getStackStorageType(), stackFrame.getStackSize()),
                                                  type,
                                                  false);
            }
            case PUTSTATIC: {
                var type = compiler.getJni().toNativeType(Type.getType(((FieldInsnNode) instruction).desc));

                return new StaticFieldInstruction(new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                                  new StackSlot(type.getStackStorageType(),
                                                                stackFrame.getStackSize() - 1),
                                                  type,
                                                  true);
            }
            case GETFIELD: {
                var type = compiler.getJni().toNativeType(Type.getType(((FieldInsnNode) instruction).desc));

                return new FieldInstruction(new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                            new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                            new StackSlot(type.getStackStorageType(), stackFrame.getStackSize() - 1),
                                            type,
                                            false);
            }
            case PUTFIELD: {
                var type = compiler.getJni().toNativeType(Type.getType(((FieldInsnNode) instruction).desc));

                return new FieldInstruction(new MethodOrFieldIdentifier((FieldInsnNode) instruction),
                                            new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 2),
                                            new StackSlot(type.getStackStorageType(), stackFrame.getStackSize() - 1),
                                            type,
                                            true);
            }
            case INVOKEVIRTUAL: // visitMethodInsn
            case INVOKESPECIAL:
            case INVOKEINTERFACE: {
                var methodInsn = (MethodInsnNode) instruction;
                var types = TypeUtils.getEffectiveArgumentTypes(methodInsn.desc, false);
                var returnType = compiler.getJni().toNativeType(Type.getReturnType(methodInsn.desc));

                var params = new StackSlot[types.length];
                var targetTypes = new JNIType[params.length];

                int stackIndex = 0;

                for (int i = types.length - 1; i >= 0; i--) {
                    stackIndex += 1;

                    var type = compiler.getJni().toNativeType(types[i]);

                    params[i] = new StackSlot(type.getStackStorageType(), stackFrame.getStackSize() - stackIndex);
                    targetTypes[i] = type;
                }

                return new InvokeInstruction(
                        new MethodOrFieldIdentifier(methodInsn),
                        params,
                        targetTypes,
                        returnType == JNIType.VOID ? null : new StackSlot(returnType.getStackStorageType(),
                                                                          stackFrame.getStackSize() - stackIndex),
                        false,
                        instruction.getOpcode() == INVOKESPECIAL);
            }
            case INVOKESTATIC: {
                var methodInsn = (MethodInsnNode) instruction;
                var types = Type.getArgumentTypes(methodInsn.desc);
                var returnType = compiler.getJni().toNativeType(Type.getReturnType(methodInsn.desc));

                var params = new StackSlot[types.length];
                var targetTypes = new JNIType[params.length];

                int stackIndex = 0;

                for (int i = types.length - 1; i >= 0; i--) {
                    stackIndex += 1;

                    var type = compiler.getJni().toNativeType(types[i]);

                    params[i] = new StackSlot(type.getStackStorageType(), stackFrame.getStackSize() - stackIndex);
                    targetTypes[i] = type;
                }

                return new InvokeInstruction(
                        new MethodOrFieldIdentifier(methodInsn),
                        params,
                        targetTypes,
                        returnType == JNIType.VOID ? null : new StackSlot(returnType.getStackStorageType(),
                                                                          stackFrame.getStackSize() - stackIndex),
                        true,
                        false);
            }
//            case INVOKEDYNAMIC: // visitInvokeDynamicInsn
            case NEW: // visitTypeInsn
                return new NewInstruction(((TypeInsnNode) instruction).desc,
                                          new StackSlot(JNIType.OBJECT, stackFrame.getStackSize()));
            case NEWARRAY: // visitIntInsn
                return new NewArrayInstruction(OpcodeUtils.getNewArrayType((IntInsnNode) instruction),
                                               new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                               new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1));
            case ANEWARRAY: // visitTypeInsn
                return new NewArrayInstruction(Type.getType("L" + ((TypeInsnNode) instruction).desc + ";"),
                                               new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1),
                                               new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1));
            case ARRAYLENGTH: // visitInsn
                return new ArrayLenInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                               new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1));
            case ATHROW:
                return new ThrowInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1));
            case CHECKCAST: // visitTypeInsn
                return new CheckCastInstruction(((TypeInsnNode) instruction).desc,
                                                new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                                new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1));
            case INSTANCEOF:
                return new InstanceOfInstruction(((TypeInsnNode) instruction).desc,
                                                 new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                                 new StackSlot(JNIType.INT, stackFrame.getStackSize() - 1));
            case MONITORENTER: // visitInsn
            case MONITOREXIT:
                return new MonitorInstruction(new StackSlot(JNIType.OBJECT, stackFrame.getStackSize() - 1),
                                              opcode == MONITORENTER);
//            case MULTIANEWARRAY: // visitMultiANewArrayInsn
            default:
                throw new IllegalArgumentException("Unknown opcode");
        }
    }

    private static JNIType getStackFrameType(MLVCompiler compiler, SourceValue stack) {
        Type currentType = null;

        for (AbstractInsnNode insn : stack.insns) {
            Type t = OpcodeUtils.getReturnType(insn);

            if (currentType == null || t.equals(currentType)) {
                currentType = t;
            } else {
                throw new IllegalArgumentException("Can't merge two types :/");
            }
        }

        JNIType type;

        // If there is no instruction leading to this, it is properly an exception handler
        if (currentType == null)
            type = JNIType.OBJECT;
        else
            type = compiler.getJni().toNativeType(currentType);
        return type;
    }

}
