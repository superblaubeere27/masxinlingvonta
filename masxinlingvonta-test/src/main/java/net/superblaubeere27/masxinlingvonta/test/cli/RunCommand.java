package net.superblaubeere27.masxinlingvonta.test.cli;

import net.superblaubeere27.masxinlingvaj.MLV;
import net.superblaubeere27.masxinlingvaj.compiler.OptimizerSettings;
import net.superblaubeere27.masxinlingvaj.preprocessor.AnnotationPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import net.superblaubeere27.masxinlingvonta.test.runner.TestCompiler;
import net.superblaubeere27.masxinlingvonta.test.runner.TestManifest;
import net.superblaubeere27.masxinlingvonta.test.runner.cfgTests.AbstractCfgTest;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;

@CommandLine.Command(name = "run", mixinStandardHelpOptions = true)
class RunCommand implements Callable<Integer> {
    public static final String TEST_RUNTIME = "D:\\Projects\\IntelliJ\\masxinlingvaj\\masxinlingvonta-test-runtime\\target\\masxinlingvonta-test-runtime-0.1.0-SNAPSHOT.jar";
    public static final String ANNOTATIONS = "D:\\Projects\\IntelliJ\\masxinlingvaj\\masxinlingvonta-annotations\\target\\masxinlingvonta-annotations-0.1.0-SNAPSHOT.jar";

    static final String[] LIBRARIES = {
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
            "C:/Users/superblaubeere27/.jdks/adopt-openjdk-1.8.0_302/jre/lib/rt.jar",
            TEST_RUNTIME,
            ANNOTATIONS
    };
    private static final String[] RUNTIME_LIBRARIES = {
            TEST_RUNTIME,
            ANNOTATIONS
    };

    @CommandLine.Parameters(index = "0", description = "Test class name i.e. <net.a.b.Test>")
    private String name;

    @CommandLine.Option(names = {"--only-compile-with-mlv"})
    private final boolean onlyCompileWithMLV = false;

    static MLV loadMLV(File inputFile) throws IOException {
        var mlv = new MLV(new CompilerPreprocessor(new AnnotationPreprocessor()));

        mlv.loadInput(inputFile);

        mlv.loadLibraries(Arrays.stream(LIBRARIES).map(x -> {
            try {
                return new File(x).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList()));

        return mlv;
    }

    @Override
    public Integer call() throws Exception {
        var input = new File("D:\\Projects\\IntelliJ\\masxinlingvaj\\masxinlingvonta-test\\src\\test\\java");
        var output = new File("testScrap/test.jar");

        if (onlyCompileWithMLV) {
            System.out.println("Compiling with MLV...");

            compileWithMLV(output);

            return 0;
        }

        System.out.println("Compiling with Javac...");

        compileWithJavac(input, output);


        System.out.println("Verifying tests...");

        if (!runTest("test.jar", "null")) {
            System.err.println("Error while verifying tests");
            return 1;
        }

        System.out.println("Compiling with MLV...");

        compileWithMLV(output);

        System.out.println("Compiling with Clang...");

        var clangOutput = new ProcessBuilder("C:\\Program Files\\LLVM\\bin\\clang.exe", "-shared", "-o", "testScrap/test-native.dll", "testScrap/test-native.ll").inheritIO().start().waitFor();

        if (clangOutput != 0) {
            System.err.println("Error while compiling with clang");
            return 1;
        }

        System.out.println("Running tests...");

        if (!runTest("test-obf.jar", "test-native")) {
            System.err.println("Error while running tests");
            return 1;
        }

        return 0;
    }

    private boolean runTest(String testJar, String natives) throws InterruptedException, IOException {
        var testRuntime = String.join(File.pathSeparator, RUNTIME_LIBRARIES) + File.pathSeparator + testJar;

        var javaProcess = new ProcessBuilder("java", "-cp", testRuntime, "net.superblaubeere27.masxinlingvonta.test.framework.TestExecutor", natives, this.name).directory(new File("testScrap")).inheritIO().start().waitFor();

        return javaProcess == 0;
    }

    private void compileWithMLV(File output) throws Exception {
        var mlv = loadMLV(output);

        mlv.preprocessAndCompile(new OptimizerSettings(true));

        mlv.dumpClassCfg(Collections.singletonList(this.name.replace('.', '/')), System.out);

        runCfgTests(mlv);

        mlv.writeOutput(new File("testScrap/test-obf.jar"));

        mlv.optimize(3);
        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testScrap/test-native.ll", new byte[0]);
    }

    private void runCfgTests(MLV mlv) {
        var internalName = this.name.replace('.', '/');
        var testClass = mlv.getCompiler().getIndex().getClass(internalName);

        File cfgTestFile = new File("D:\\Projects\\IntelliJ\\masxinlingvaj\\masxinlingvonta-test\\src\\test\\java", internalName + ".json");

        if (!cfgTestFile.exists()) {
            return;
        }

        try (var reader = Files.newBufferedReader(cfgTestFile.toPath())) {
            var cfgTest = TestManifest.loadFrom(reader);

            for (Map.Entry<String, List<AbstractCfgTest>> entry : cfgTest.cfgTests.entrySet()) {
                var methodName = entry.getKey();
                var method = testClass.getMethods().stream().filter(x -> x.getNode().name.equals(methodName)).findFirst().orElseThrow();
                var cfg = mlv.getCompiler().getFunctionCodegenContext(method).getCfg();

                try {
                    for (AbstractCfgTest test : entry.getValue()) {
                        test.run(cfg);
                    }
                } catch (Exception e) {
                    System.err.println(cfg);

                    throw new RuntimeException("Error while running cfg test for " + internalName + "." + methodName, e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void compileWithJavac(File input, File output) throws IOException, InterruptedException {
        var cp = Arrays.stream(LIBRARIES).map(File::new).toList();

        var compiler = new TestCompiler(input, output, cp);

        System.out.println("Compiling java code...");

        compiler.compile(Collections.singletonList(this.name));
    }
}
