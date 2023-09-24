package net.superblaubeere27.masxinlingvaj.compiler;

import net.superblaubeere27.masxinlingvaj.analysis.BytecodeMethodAnalyzer;
import net.superblaubeere27.masxinlingvaj.analysis.CallGraph;
import net.superblaubeere27.masxinlingvaj.compiler.code.codegen.IntrinsicMethods;
import net.superblaubeere27.masxinlingvaj.compiler.code.codegen.OnLoadBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.graph.SimpleDfs;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.RegisterToSSA;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.SSABlockLivenessAnalyser;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNI;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstStringExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetStaticExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeInstanceExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.DeleteLocalsRefsPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.InlineLocalPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.InvokeSpecificator;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.RedundantExpressionAndAssignmentRemover;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.cfg.CfgPruning;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.InliningPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heap2reg.Heap2RegPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.InstSimplifyPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.instSimplify.ReuseLocalsPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.ExpressionStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyPhiStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.bytedeco.llvm.global.LLVM.LLVMModuleCreateWithName;

public class MLVCompiler {
    private final CompilerIndex index;
    private final IntrinsicMethods intrinsicMethods;
    private final OnLoadBuilder onLoadBuilder;
    private final HashMap<CompilerMethod, FunctionCodegenContext> functionCodegenContexts = new HashMap<>();
    private final OptimizerSettings optimizerSettings;
    private JNI jni;
    private LLVMModuleRef module;
    private CallGraph callGraph;

    public MLVCompiler(ArrayList<ClassNode> inputClasses, ArrayList<ClassNode> libraryClasses, OptimizerSettings optimizerSettings) {
        this.optimizerSettings = optimizerSettings;
        this.index = new CompilerIndex(inputClasses, libraryClasses);

        this.index.buildHierarchy();

        this.analyzeMethods(new HashMap<>());

        this.createModule();

        this.onLoadBuilder = new OnLoadBuilder(this);
        this.intrinsicMethods = IntrinsicMethods.create(this);
    }

    private void createModule() {
        this.module = LLVMModuleCreateWithName("mlv");
        this.jni = new JNI(this.module);

        var fltUsedConst = LLVM.LLVMAddGlobal(this.module, LLVM.LLVMInt32Type(), "_fltused");
        LLVM.LLVMSetGlobalConstant(fltUsedConst, 1);
    }


    public void compile(CompilerPreprocessor preprocessor) throws AnalyzerException {
        var methodCfgMap = new HashMap<CompilerMethod, ControlFlowGraph>();

        for (int i = 0; i < 3; i++) {
            boolean wasNewMethodCompiled = false;

            addAdditionalMethodsToOutsource(preprocessor);

            var methods = preprocessor.getMethodsToCompile();

            for (CompilerMethod method : methods) {
                if (methodCfgMap.containsKey(method))
                    continue;

                methodCfgMap.put(method, createControlFlowGraph(method));

                wasNewMethodCompiled = true;
            }

            if (this.optimizerSettings.inline())
                runInlinePass(preprocessor, methodCfgMap);

            if (!wasNewMethodCompiled)
                break;

            this.analyzeMethods(methodCfgMap);
        }

        for (ControlFlowGraph value : methodCfgMap.values()) {
            runPasses(value);

            if (value.getCompilerMethod().getIdentifier().toString().contains("Test.testShit")) {
                try (PrintStream writer = new PrintStream(new FileOutputStream("testJars/test.dot"))) {
                    value.verify();
                    value.toGraphViz(writer);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

//                addDebug(value, true);
            }

        }

        for (Map.Entry<CompilerMethod, ControlFlowGraph> entry : methodCfgMap.entrySet()) {
            this.functionCodegenContexts.put(entry.getKey(), FunctionCodegenContext.createFunction(this, entry.getValue()));
        }

        for (Map.Entry<CompilerMethod, FunctionCodegenContext> codegenContextEntry : this.functionCodegenContexts.entrySet()) {
            var method = codegenContextEntry.getKey();
            var functionContext = codegenContextEntry.getValue();

            System.out.println(method.getIdentifier() + ": ");
            System.out.println(functionContext.getCfg());

            functionContext.compile();

            method.setWasCompiled(!method.isLibrary() && !method.getNode().name.startsWith("<"));
        }

        this.onLoadBuilder.compileMethods();
    }

    private void addDebug(ControlFlowGraph cfg, boolean debugVars) {
        var livenessAnalyser = new SSABlockLivenessAnalyser(cfg);

        livenessAnalyser.compute();

        var sysout = cfg.getLocals().allocStatic(ImmType.OBJECT);

        for (BasicBlock block : cfg.vertices()) {
            var n = block.stream().takeWhile(x -> x instanceof CopyPhiStmt).count();
            var printStatements = new ArrayList<Stmt>();

            if (debugVars) {
                printStatements.add(
                        new ExpressionStmt(new InvokeInstanceExpr(
                                new MethodOrFieldIdentifier("java/io/PrintStream", "print", "(Ljava/lang/String;)V"),
                                new VarExpr(sysout),
                                new Expr[]{new ConstStringExpr("Finished " + block + " [")},
                                InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL))
                );

                boolean first = true;

                for (Local local : livenessAnalyser.in(block)) {
                    printStatements.add(
                            new ExpressionStmt(new InvokeInstanceExpr(
                                    new MethodOrFieldIdentifier("java/io/PrintStream", "print", "(Ljava/lang/String;)V"),
                                    new VarExpr(sysout),
                                    new Expr[]{new ConstStringExpr((first ? "" : ", ") + local + " = ")},
                                    InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL))
                    );

                    printStatements.add(
                            new ExpressionStmt(new InvokeInstanceExpr(
                                    new MethodOrFieldIdentifier("java/io/PrintStream", "print", "(" + local.getType().getJVMTypeSignature() + ")V"),
                                    new VarExpr(sysout),
                                    new Expr[]{new VarExpr(local)},
                                    InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL))
                    );

                    first = false;
                }

                printStatements.add(
                        new ExpressionStmt(new InvokeInstanceExpr(
                                new MethodOrFieldIdentifier("java/io/PrintStream", "println", "(Ljava/lang/String;)V"),
                                new VarExpr(sysout),
                                new Expr[]{new ConstStringExpr("]")},
                                InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL))
                );
            } else {
                printStatements.add(new ExpressionStmt(new InvokeInstanceExpr(
                        new MethodOrFieldIdentifier("java/io/PrintStream", "println", "(Ljava/lang/String;)V"),
                        new VarExpr(sysout),
                        new Expr[]{new ConstStringExpr("Reached " + block)},
                        InvokeInstanceExpr.InvokeInstanceType.INVOKE_VIRTUAL)));
            }

            block.addAll((int) n, printStatements);
        }

        cfg.getEntry().add(0, new CopyVarStmt(new VarExpr(sysout), new GetStaticExpr(new MethodOrFieldIdentifier("java/lang/System", "out", "Ljava/io/PrintStream;"))));
    }

    private void runInlinePass(CompilerPreprocessor preprocessor, HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap) {
        InliningPass pass = new InliningPass(this, this.callGraph, methodCfgMap);

        HashSet<CompilerMethod> processed = new HashSet<>();

        for (CompilerMethod method : preprocessor.getMethodsToCompile()) {
            List<CallGraph.CallGraphMethod> topoorder = SimpleDfs.topoorder(callGraph, callGraph.getMethodVertex(method.getIdentifier()));

            for (CallGraph.CallGraphMethod curr : topoorder) {
                var currTarget = this.index.getMethod(curr.getMethod());
                var currCfg = methodCfgMap.get(currTarget);

                if (currTarget == null || currCfg == null) {
                    continue;
                }
                if (currTarget.isLibrary())
                    continue;

                if (processed.add(currTarget)) {
                    while (pass.runSingleInlinePass(currCfg))
                        optimize(currCfg);
                }
            }
        }
    }

    private void runPasses(ControlFlowGraph cfg) {
        Heap2RegPass heap2RegPass = new Heap2RegPass(this.index);

        heap2RegPass.apply(cfg);

        cfg.verify();

        optimize(cfg);

        DeleteLocalsRefsPass deleteLocalsRefsPass = new DeleteLocalsRefsPass();

        deleteLocalsRefsPass.apply(cfg);

        optimizeAfterDeleteLocals(cfg);
    }

    private ControlFlowGraph createControlFlowGraph(CompilerMethod method) throws AnalyzerException {
        ControlFlowGraph cfg = NewCodeConverter.convert(this, Objects.requireNonNull(method));

        RegisterToSSA registerToSSA = new RegisterToSSA(cfg);
        registerToSSA.process();

        optimize(cfg);

        return cfg;
    }

    private void optimize(ControlFlowGraph cfg) {
        InlineLocalPass inlineLocalPass = new InlineLocalPass();

        inlineLocalPass.apply(cfg);

        cfg.verify();

        RedundantExpressionAndAssignmentRemover redundantExpressionAndAssignmentRemover = new RedundantExpressionAndAssignmentRemover();

        redundantExpressionAndAssignmentRemover.apply(cfg);

        cfg.verify();

        CfgPruning pruningPass = new CfgPruning(this);

        pruningPass.apply(cfg);

        cfg.verify();

        ReuseLocalsPass reuseLocalsPass = new ReuseLocalsPass();

        reuseLocalsPass.apply(cfg);

        cfg.verify();

        InstSimplifyPass simplifyPass = new InstSimplifyPass(this.index);

        simplifyPass.apply(cfg);

        cfg.verify();

        redundantExpressionAndAssignmentRemover.apply(cfg);

        InvokeSpecificator specificator = new InvokeSpecificator(this.index);

        specificator.apply(cfg);
    }

    private void optimizeAfterDeleteLocals(ControlFlowGraph cfg) {
        CfgPruning pruningPass = new CfgPruning(this);

        pruningPass.apply(cfg);

        cfg.verify();
    }

    /**
     * Analyzes all methods and their call graphs.
     * If possible, it will try to obtain the call graph from the {@code methodCfgMap} parameter.
     */
    public void analyzeMethods(HashMap<CompilerMethod, ControlFlowGraph> methodCfgMap) {
        this.callGraph = new CallGraph();

        for (CompilerClass aClass : this.index.getClasses()) {
            for (CompilerMethod method : aClass.getMethods()) {
                method.updateAnalysisInfo(methodCfgMap.get(method));

                CallGraph.CallGraphMethod methodVertex = callGraph.getMethodVertex(method.getIdentifier());

                for (Map.Entry<BytecodeMethodAnalyzer.MethodCall, Integer> entry : method.getMethodAnalysisInfo().getMethodCallCount().entrySet()) {
                    var call = entry.getKey();

                    var target = call.getIdentifier();

                    if (!call.isStatic() && !call.isNonVirtual()) {
                        var impls = call.getPossibleImplementations(index);

                        if (impls == null || impls.size() != 1)
                            continue;

                        target = impls.get(0).getIdentifier();
                    }

                    callGraph.addEdge(new CallGraph.CallGraphMethodCall(methodVertex, callGraph.getMethodVertex(target), entry.getValue()));
                }
            }
        }
    }

    private void addAdditionalMethodsToOutsource(CompilerPreprocessor preprocessor) {
        HashSet<CompilerMethod> additionalMethods = new HashSet<>();

        for (CompilerMethod method : preprocessor.getMethodsToCompile()) {
            List<CallGraph.CallGraphMethod> topoorder = SimpleDfs.topoorder(callGraph, callGraph.getMethodVertex(method.getIdentifier()));

            for (int i = topoorder.size() - 1; i >= 0; i--) {
                CallGraph.CallGraphMethod call = topoorder.get(i);

                CompilerMethod referencedCompilerMethod = this.index.getMethod(call.getMethod());

                if (referencedCompilerMethod != null) {
                    referencedCompilerMethod.getMethodAnalysisInfo().updateCostEstimation(this);

                    System.out.println(referencedCompilerMethod.getIdentifier() + ": " + referencedCompilerMethod.getMethodAnalysisInfo().getCurrentEstimation().getEstimatedCost());
                }
            }

            addAdditionalMethodsToOutsource0(additionalMethods, method, 10);
        }

        System.out.println("Additionally outsourced: ");

        for (CompilerMethod additionalMethod : additionalMethods) {
            System.out.println("- " + additionalMethod.getIdentifier());
        }

        additionalMethods.forEach(preprocessor::markForCompilation);
    }

    private void addAdditionalMethodsToOutsource0(HashSet<CompilerMethod> additionalMethods, CompilerMethod method, int maxRecursions) {
        if (maxRecursions == 0)
            return;

        for (BytecodeMethodAnalyzer.MethodCall outsourcedCall : method.getMethodAnalysisInfo().getCurrentEstimation().getOutsourcedCalls()) {
            var outsourcedMethod = Objects.requireNonNull(this.index.getMethod(outsourcedCall.getIdentifier()));

            if (!outsourcedMethod.canBeOutsourced() || outsourcedMethod.getMethodAnalysisInfo().getCurrentEstimation().getEstimatedCost() > BytecodeMethodAnalyzer.OUTSOURCE_DISCOUNT)
                continue;

            additionalMethods.add(outsourcedMethod);

            this.addAdditionalMethodsToOutsource0(additionalMethods, outsourcedMethod, maxRecursions - 1);
        }
    }

    public CompilerIndex getIndex() {
        return index;
    }

    public JNI getJni() {
        return jni;
    }

    public LLVMModuleRef getModule() {
        return module;
    }

    public IntrinsicMethods getIntrinsicMethods() {
        return intrinsicMethods;
    }

    public FunctionCodegenContext getFunctionCodegenContext(CompilerMethod method) {
        return this.functionCodegenContexts.get(method);
    }

    public OnLoadBuilder getOnLoadBuilder() {
        return onLoadBuilder;
    }
}
