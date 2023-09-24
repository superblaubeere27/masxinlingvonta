package net.superblaubeere27.masxinlingvaj;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.OptimizerSettings;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.AbstractPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import net.superblaubeere27.masxinlingvaj.utils.ExecutorServiceFactory;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.bytedeco.llvm.global.LLVM.LLVMDumpModule;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;

public class Main {
    public static final boolean RUN_TESTS = false;
    private static final ExecutorServiceFactory EXECUTOR_SERVICE_FACTORY = () -> Executors.newFixedThreadPool(12);
    private static final String[] LIBRARIES = {
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/charsets.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/access-bridge-64.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/cldrdata.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/dnsns.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/jaccess.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/localedata.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/nashorn.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/sunec.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/sunjce_provider.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/sunmscapi.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/sunpkcs11.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/ext/zipfs.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/jce.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/jfr.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/jsse.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/management-agent.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/resources.jar",
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/rt.jar"
    };

    public static void main(String[] args) throws Exception {
        var mlv = new MLV(new CompilerPreprocessor(
                new AbstractPreprocessor() {
                    @Override
                    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
                        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
                            if (aClass.isLibrary())
                                continue;

//                            if (!aClass.getClassNode().name.startsWith("net/ccbluex/liquidbounce/"))
//                                continue;
                            if ((aClass.getClassNode().access & Opcodes.ACC_INTERFACE) != 0)
                                continue;
//                            if (RUN_TESTS && !aClass.getClassNode().name.equals("Test") && !aClass.getClassNode().name.startsWith("Test$"))
//                                continue;
                            if (!RUN_TESTS && (aClass.getClassNode().name.equals("Asserts") || aClass.getClassNode().name.equals("MainLoader")))
                                continue;

                            for (CompilerMethod method : aClass.getMethods()) {
                                if ((method.getNode().access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0)
                                    continue;
                                if (method.getNode().name.startsWith("<")
                                        || method.getIdentifier().getName().equals("main")
//                                        || RUN_TESTS && !method.getNode().name.startsWith("setup")
                                        || RUN_TESTS && !method.getNode().name.startsWith("test")
                                        || !RUN_TESTS && !(method.getIdentifier().toString().startsWith("APathfinding") || method.getIdentifier().toString().startsWith("Frame"))
//                                        || !method.getParent().getName().equals("Test$Vec3") || !method.getNode().name.equals("absSquared")
                                )
                                    continue;

                                preprocessor.markForCompilation(method);
                            }
                        }
                    }

                    @Override
                    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {

                    }
                }
//        ,
//                new AnnotationPreprocessor()
        ));

        if (!RUN_TESTS) {
            mlv.loadInput(new File("testJars/APathfinding-Visual.jar"));
        } else {
            mlv.loadInput(new File("testJars/Test.jar"));
        }

        mlv.loadLibraries(Arrays.stream(LIBRARIES).map(x -> {
            try {
                return new File(x).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList()));


        mlv.preprocessAndCompile(new OptimizerSettings(true));

        if (!RUN_TESTS) {
            mlv.writeOutput(new File("testJars/APathfinding-Visual-obf.jar"));
        } else {
            mlv.writeOutput(new File("testJars/Test-obf.jar"));
        }

        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testJars/test-native.ll", new byte[0]);

        mlv.optimize(3);

//        System.out.println("SSA: \n" + mlv.);

        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testJars/test-native.ll", new byte[0]);

        if (RUN_TESTS)
            LLVMDumpModule(mlv.getLLVMModule());

//        MLVCompiler compiler = new MLVCompiler(InputLoader.loadFiles(Collections.singletonList(new File("testJars/Test.jar").toURI().toURL()), EXECUTOR_SERVICE_FACTORY).getClassNodes());
//
//        CompilerMethod method = null;
//
//        O:
//        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
//            if (!aClass.getName().equals("Test")) {
//                continue;
//            }
//
//            for (CompilerMethod aClassMethod : aClass.getMethods()) {
//                if (aClassMethod.getIdentifier().getName().equals("main")) {
//                    method = aClassMethod;
//                    break O;
//                }
//            }
//
//        }
//
//        System.out.println(ASMUtils.toString(method.getNode()));
//
//        ControlFlowGraph cfg = NewCodeConverter.convert(compiler, Objects.requireNonNull(method));
//
//        System.out.println("Register: \n" + cfg);
//        cfg.verify();
//
//        RegisterToSSA registerToSSA = new RegisterToSSA(cfg);
//        registerToSSA.process();
//
//        cfg.verify();
//
//
//        System.out.println("SSA: \n" + cfg);
//
//        InlineLocalPass inlineLocalPass = new InlineLocalPass();
//
//        inlineLocalPass.apply(cfg);
//        cfg.verify();
//
//        RedundantExpressionAndAssignmentRemover redundantExpressionAndAssignmentRemover = new RedundantExpressionAndAssignmentRemover();
//
//        redundantExpressionAndAssignmentRemover.apply(cfg);
//
//        cfg.verify();
//
//        CallGraphPruningPass pruningPass = new CallGraphPruningPass();
//
//        pruningPass.apply(cfg);
//        cfg.verify();
//
//        InstSimplifyPass simplifyPass = new InstSimplifyPass();
//
//        simplifyPass.apply(cfg);
//        cfg.verify();
//
//        DeleteLocalsRefsPass deleteLocalsRefsPass = new DeleteLocalsRefsPass();
//
//        deleteLocalsRefsPass.apply(cfg);
//
//        cfg.verify();
//
//        pruningPass.apply(cfg);
//
//        cfg.verify();
//
//        System.out.println(cfg);
////
//        FunctionCodegenContext function = FunctionCodegenContext.createFunction(compiler, cfg);
//
//        function.compile();
//
//        BytePointer error = new BytePointer((Pointer) null);
//
//        LLVM.LLVMDumpModule(compiler.getModule());
//        LLVMVerifyModule(compiler.getModule(), LLVMAbortProcessAction, error);
    }

    private static Runnable lambda() {
        return () -> System.out.println("Helo");
    }

}
