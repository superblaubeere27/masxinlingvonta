package net.superblaubeere27.masxinlingvaj.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.tree.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * A very cheap heuristic that determines how expensive it is to compile a method to native code.
 * <p>
 * It assumes that every part of the code runs equally often (-> cheap heuristic)
 */
public class BytecodeMethodAnalyzer implements Opcodes {
    public static final int OUTSOURCE_DISCOUNT = 300;

    /**
     * When an object is passed to the JVM it has to be verified
     */
    public static final int OBJECT_VERIFICATION_COST = 1;
    public static final int SET_STATIC_FIELD_COST = 6;
    public static final int SET_FIELD_COST = 10;

    public static final int STATIC_INVOKE_COST = 27;
    public static final int NONVIRTUAL_INVOKE_COST = 37;

    public static final int ARRAY_STORE_COST = 16;
    /**
     * Array object stores use a different JNI-Function so the timings differ from the usual ARRAY_STORE_COST + OBJECT_VERIFICATION_COST
     */
    public static final int ARRAY_OBJECT_STORE_COST = 20;

    public static MethodInfo analyze(CompilerMethod method, ControlFlowGraph cfg) {
        int baseCost = 0;
        HashMap<MethodCall, Integer> methodCalls = new HashMap<>();

        // Labels which are leading up to throw instructions
        HashSet<LabelNode> labelsCausingTraps = findTrapCausingLabels(method);

        // Is the current instruction leading up to a throw?
        var doesCodeCauseTrap = false;

        for (AbstractInsnNode instruction : method.getNode().instructions) {
            // Did we encounter a label? Then update the doesCodeCauseTrap flag
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                //noinspection SuspiciousMethodCalls
                doesCodeCauseTrap = labelsCausingTraps.contains(instruction);
            }

            int instructionCost = 0;

            switch (instruction.getOpcode()) {
                case GETFIELD:
                case GETSTATIC:
                case PUTSTATIC:
                case PUTFIELD: {
                    var cost = (instruction.getOpcode() == Opcodes.PUTSTATIC || instruction.getOpcode() == Opcodes.GETSTATIC) ? SET_STATIC_FIELD_COST : SET_FIELD_COST;

                    char c = ((FieldInsnNode) instruction).desc.charAt(0);

                    if (c == '[' || c == 'L') {
                        cost += OBJECT_VERIFICATION_COST;
                    }

                    if (instruction.getOpcode() == GETSTATIC || instruction.getOpcode() == GETFIELD) {
                        cost /= 4;
                    }

                    instructionCost = cost;
                    break;
                }
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC: {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;

                    MethodCall call = new MethodCall(instruction.getOpcode() == INVOKESPECIAL, instruction.getOpcode() == INVOKESTATIC, (int) Arrays.stream(Type.getArgumentTypes(methodInsnNode.desc)).filter(x -> x.getSort() == Type.OBJECT || x.getSort() == Type.ARRAY).count(), new MethodOrFieldIdentifier(methodInsnNode));

                    methodCalls.put(call, methodCalls.getOrDefault(call, 0) + 1);

                    break;
                }
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case BALOAD:
                case ALOAD:
                case SALOAD:
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                    instructionCost = ARRAY_STORE_COST;
                    break;
                case AALOAD:
                case AASTORE:
                    instructionCost = ARRAY_OBJECT_STORE_COST;
                    break;
            }

            if (doesCodeCauseTrap) {
                instructionCost /= 15;
            }

            baseCost += instructionCost;
        }

        if (cfg != null)
            methodCalls = obtainCfgMethodCalls(cfg);

        return new MethodInfo(baseCost, methodCalls);
    }


    private static HashMap<BytecodeMethodAnalyzer.MethodCall, Integer> obtainCfgMethodCalls(ControlFlowGraph cfg) {
        HashMap<MethodCall, Integer> methodCalls = new HashMap<>();

        cfg.allExprStream().filter(x -> x instanceof InvokeExpr).forEach(x -> {
            var call = new BytecodeMethodAnalyzer.MethodCall(
                    (x instanceof InvokeInstanceExpr invokeInstanceExpr && invokeInstanceExpr.getInvokeType() == InvokeInstanceExpr.InvokeInstanceType.INVOKE_SPECIAL),
                    x instanceof InvokeStaticExpr,
                    (int) Arrays.stream(((InvokeExpr) x).getArgTypes()).filter(y -> ImmType.fromJVMType(y) == ImmType.OBJECT).count(),
                    ((InvokeExpr) x).getTarget()
            );

            methodCalls.put(call, methodCalls.getOrDefault(call, 0));
        });

        return methodCalls;
    }

    /**
     * Which labels are definitely leading up to a trap?
     * NOTE: Yes, there might be more labels, but this is a cheap heuristic ;D
     */
    private static HashSet<LabelNode> findTrapCausingLabels(CompilerMethod method) {
        HashSet<LabelNode> labelsCausingTraps = new HashSet<>();

        LabelNode currentLabel = null;

        for (AbstractInsnNode instruction : method.getNode().instructions) {
            if (instruction instanceof LabelNode) {
                currentLabel = (LabelNode) instruction;
            } else if (instruction.getOpcode() == ATHROW) {
                if (currentLabel != null) {
                    labelsCausingTraps.add(currentLabel);
                }

                currentLabel = null;
            }
        }
        return labelsCausingTraps;
    }

    /**
     * Since method outsourcing cost depends on which methods are outsourced they are
     */
    public static class MethodInfo {
        private final int baseCost;
        private final HashMap<MethodCall, Integer> methodCallCount;
        private MethodCostEstimation currentEstimation;

        public MethodInfo(int baseCost, HashMap<MethodCall, Integer> methodCallCount) {
            this.baseCost = baseCost;
            this.methodCallCount = methodCallCount;
        }

        public MethodCostEstimation getCurrentEstimation() {
            return currentEstimation;
        }

        public MethodCostEstimation updateCostEstimation(MLVCompiler compiler) {
            HashSet<MethodCall> outsourcedMethods = new HashSet<>();

            var cost = this.baseCost;

            for (Map.Entry<MethodCall, Integer> methodCallCountEntry : this.methodCallCount.entrySet()) {
                MethodCall call = methodCallCountEntry.getKey();
                var methodCallCost = call.estimateCost();

                var target = compiler.getIndex().getMethod(call.identifier);

                if (target != null && target.getMethodAnalysisInfo() != null) {
                    var analysisInfo = target.getMethodAnalysisInfo();

                    if (analysisInfo.currentEstimation != null) {
                        var newCost = Math.max(analysisInfo.currentEstimation.estimatedCost - OUTSOURCE_DISCOUNT, 0);

                        // Should we outsource the method?
                        if (newCost < methodCallCost) {
                            methodCallCost = newCost;
                            outsourcedMethods.add(call);
                        }
                    }
                }

                cost += methodCallCost;
            }

            MethodCostEstimation costEstimation = new MethodCostEstimation(cost, outsourcedMethods);

            this.currentEstimation = costEstimation;

            return costEstimation;
        }

        public HashMap<MethodCall, Integer> getMethodCallCount() {
            return methodCallCount;
        }
    }

    public static class MethodCostEstimation {
        private final int estimatedCost;
        private final Set<MethodCall> outsourcedCalls;

        public MethodCostEstimation(int estimatedCost, Set<MethodCall> outsourcedCalls) {
            this.estimatedCost = estimatedCost;
            this.outsourcedCalls = outsourcedCalls;
        }

        public Set<MethodCall> getOutsourcedCalls() {
            return outsourcedCalls;
        }

        public int getEstimatedCost() {
            return estimatedCost;
        }
    }

    public static class MethodCall {
        private final boolean nonVirtual;
        private final boolean isStatic;
        private final int objectParameters;
        private final MethodOrFieldIdentifier identifier;

        public MethodCall(boolean nonVirtual, boolean isStatic, int objectParameters, MethodOrFieldIdentifier identifier) {
            this.nonVirtual = nonVirtual;
            this.isStatic = isStatic;
            this.objectParameters = objectParameters;
            this.identifier = identifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodCall that = (MethodCall) o;
            return nonVirtual == that.nonVirtual && isStatic == that.isStatic && objectParameters == that.objectParameters && Objects.equals(identifier, that.identifier);
        }

        public MethodOrFieldIdentifier getIdentifier() {
            return identifier;
        }

        @Override
        public int hashCode() {
            return Objects.hash(nonVirtual, isStatic, objectParameters, identifier);
        }

        public int estimateCost() {
            var interfaceCost = OBJECT_VERIFICATION_COST * this.objectParameters;

            if (this.nonVirtual) {
                return NONVIRTUAL_INVOKE_COST + interfaceCost;
            } else if (this.isStatic) {
                return STATIC_INVOKE_COST + interfaceCost;
            } else {
                // VTABLE lookups are assumed to be zero-cost operations (which they are not)
                return NONVIRTUAL_INVOKE_COST + interfaceCost;
            }
        }

        public boolean isNonVirtual() {
            return nonVirtual;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public List<CompilerMethod> getPossibleImplementations(CompilerIndex index) {
            CompilerClass compilerClass = index.getClass(this.identifier.getOwner());

            if (compilerClass == null)
                return null;

            return ClassHierarchyBuilder.getPossibleImplementationsCached(index, compilerClass, new MethodOrFieldName(this.identifier));
        }
    }

}
