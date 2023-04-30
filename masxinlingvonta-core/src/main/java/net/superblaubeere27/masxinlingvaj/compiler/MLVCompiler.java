package net.superblaubeere27.masxinlingvaj.compiler;

import net.superblaubeere27.masxinlingvaj.analysis.BytecodeMethodAnalyzer;
import net.superblaubeere27.masxinlingvaj.analysis.CallGraph;
import net.superblaubeere27.masxinlingvaj.compiler.code.codegen.OnLoadBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.code.codegen.IntrinsicMethods;
import net.superblaubeere27.masxinlingvaj.compiler.graph.SimpleDfs;
import net.superblaubeere27.masxinlingvaj.compiler.graph.algorithm.RegisterToSSA;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNI;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.heap2reg.Heap2RegPass;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining.InliningPass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.*;

import static org.bytedeco.llvm.global.LLVM.LLVMModuleCreateWithName;

public class MLVCompiler {
    private final CompilerIndex index;
    private JNI jni;
    private LLVMModuleRef module;
    private final IntrinsicMethods intrinsicMethods;
    private final OnLoadBuilder onLoadBuilder;
    private final HashMap<CompilerMethod, FunctionCodegenContext> functionCodegenContexts = new HashMap<>();
    private CallGraph callGraph;

    public MLVCompiler(ArrayList<ClassNode> inputClasses, ArrayList<ClassNode> libraryClasses) {
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

            runInlinePass(preprocessor, methodCfgMap);

            if (!wasNewMethodCompiled)
                break;

            this.analyzeMethods(methodCfgMap);
        }

        for (ControlFlowGraph value : methodCfgMap.values()) {
            runPasses(value);
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

        optimize(cfg);

        DeleteLocalsRefsPass deleteLocalsRefsPass = new DeleteLocalsRefsPass();

        deleteLocalsRefsPass.apply(cfg);

        optimize(cfg);
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

        RedundantExpressionAndAssignmentRemover redundantExpressionAndAssignmentRemover = new RedundantExpressionAndAssignmentRemover();

        redundantExpressionAndAssignmentRemover.apply(cfg);

        CallGraphPruningPass pruningPass = new CallGraphPruningPass();

        pruningPass.apply(cfg);

        cfg.verify();


        InstSimplifyPass simplifyPass = new InstSimplifyPass(this.index);

        simplifyPass.apply(cfg);

        cfg.verify();

        InvokeSpecificator specificator = new InvokeSpecificator(this.index);

        specificator.apply(cfg);
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
