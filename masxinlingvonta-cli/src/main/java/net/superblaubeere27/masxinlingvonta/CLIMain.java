package net.superblaubeere27.masxinlingvonta;

import com.google.gson.Gson;
import net.superblaubeere27.masxinlingvaj.MLV;
import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.AbstractPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.AnnotationPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToString;

public class CLIMain {

    public static void main(String[] args) {
        // create Options object
        Options options = new Options();

        options.addRequiredOption("i", "inputJar", true, "input jar");
        options.addRequiredOption("o", "outputJar", true, "output file");

        options.addOption("ll", "irOutput", true, "ir output file");
        options.addOption("llvmDir", true, "llvm install location");
        options.addOption("outputDir", true, "a folder where the built shared libraries will be placed");
        options.addOption("compileFor", true, "select OSs to compile to");
        options.addOption("c", "config", true, "compiler config");
        options.addOption("help", "prints a help page");

        DefaultParser parser = new DefaultParser();

        CommandLine parse;

        try {
            parse = parser.parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("masxinlingvonta", options);

            return;
        }

        if (parse.hasOption("help")) {
            new HelpFormatter().printHelp("masxinlingvonta", options);

            return;
        }

        MLVCLIConfig config = null;

        var configFile = parse.getOptionValue("config");

        if (configFile != null) {
            try (FileInputStream fin = new FileInputStream(configFile)) {
                config = new Gson().fromJson(new InputStreamReader(fin), MLVCLIConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to read config:");
                e.printStackTrace();
                return;
            }
        }

        MLVCLIConfig finalConfig = config;

        var mlv = new MLV(new CompilerPreprocessor(
                new AbstractPreprocessor() {
                    @Override
                    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
                        if (finalConfig != null && finalConfig.additionalMethodsToCompile != null) {
                            for (MLVMethod mlvMethod : finalConfig.additionalMethodsToCompile) {
                                preprocessor.markForCompilation(compiler.getIndex().getMethod(mlvMethod.owner,
                                                                                              mlvMethod.name,
                                                                                              mlvMethod.desc));
                            }
                        }
                    }

                    @Override
                    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {

                    }
                }
                , new AnnotationPreprocessor()
        ));

        try {
            System.out.println("Loading input...");
            mlv.loadInput(new File(parse.getOptionValue("inputJar")));

            System.out.println("Compiling...");
            mlv.preprocessAndCompile();

            System.out.println("Writing...");
            mlv.writeOutput(new File(parse.getOptionValue("outputJar")));

            System.out.println("Optimizing IR...");
            mlv.optimize(3);
        } catch (Exception e) {
            System.err.println("Exception while compiling: ");

            e.printStackTrace();
            return;
        }

        String llvmDir = parse.getOptionValue("llvmDir");
        String outputDir = parse.getOptionValue("outputDir");

        try {
            File tmpIROutput = File.createTempFile("mlv_llvmir", ".ll");

            try {
                var ir = LLVMPrintModuleToString(mlv.getLLVMModule()).getStringBytes();

                Files.write(tmpIROutput.toPath(), ir);

                String irOutput = parse.getOptionValue("irOutput");

                if (irOutput != null)
                    Files.write(Paths.get(irOutput), ir);

                if (parse.getOptionValue("compileFor") != null) {
                    var oss = Arrays.stream(parse.getOptionValue("compileFor").split(",")).map(OS::fromString).collect(
                            Collectors.toSet());

                    for (OS os : oss) {
                        if (os == OS.MAC)
                            throw new IllegalStateException("Mac OS is not supported yet");

                        compileFor(tmpIROutput, llvmDir, outputDir, os);
                    }

                }
            } finally {
                tmpIROutput.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Compilation finished.");
    }

    private static void compileFor(File irInput, String llvmBasePath, String outputDir, OS os) throws IOException {
        File tmpObjFile = File.createTempFile("mlv_obj", ".o");

        System.out.println("Compiling for " + os + "...");

        try {
            {
                Process compilerProcess;

                if (os == OS.WINDOWS) {
                    compilerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "clang"),
                                                         "-O3",
                                                         "-c",
                                                         "-target",
                                                         os.getTargetTriple(),
                                                         "-o",
                                                         tmpObjFile.getAbsolutePath(),
                                                         irInput.getAbsolutePath()
                    ).start();
                } else if (os == OS.LINUX || os == OS.MAC) {
                    compilerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "clang"),
                                                         "-O3",
                                                         "-c",
                                                         "-target",
                                                         os.getTargetTriple(),
                                                         "-fPIC",
                                                         "-o",
                                                         tmpObjFile.getAbsolutePath(),
                                                         irInput.getAbsolutePath()
                    ).start();
                } else {
                    throw new IllegalArgumentException();
                }

                var exitCode = awaitProcess(compilerProcess);

                if (exitCode != 0)
                    throw new IOException("Compiler returned a non-zero exit code: " + exitCode);
            }

            Process linkerProcess;

            if (os == OS.WINDOWS) {
                linkerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "lld-link"),
                                                   "/dll",
                                                   "/noentry",
                                                   "/out:\"" + getFilePath(outputDir, "mlv-win64.dll") + "\"",
                                                   tmpObjFile.getAbsolutePath()
                ).start();
            } else if (os == OS.LINUX) {
                linkerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "ld.lld"),
                                                   "-shared",
                                                   "-o",
                                                   getFilePath(outputDir, "mlv-linux64.dll"),
                                                   tmpObjFile.getAbsolutePath()
                ).start();
            } else if (os == OS.MAC) {
                linkerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "ld64.lld"),
                                                   "-dylib",
                                                   "-o",
                                                   getFilePath(outputDir, "mlv-macosx.dll"),
                                                   tmpObjFile.getAbsolutePath()
                ).start();
            } else {
                throw new IllegalArgumentException();
            }

            var exitCode = awaitProcess(linkerProcess);

            if (exitCode != 0)
                throw new IOException("Linker returned a non-zero exit code: " + exitCode);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            tmpObjFile.delete();
        }


    }

    private static int awaitProcess(Process process) throws InterruptedException, IOException {
        process.waitFor();

        process.getInputStream().transferTo(System.err);
        process.getErrorStream().transferTo(System.err);

        return process.exitValue();
    }

    private static String getFilePath(String basePath, String fileName) {
        if (basePath == null)
            return fileName;

        return new File(basePath, fileName).getAbsolutePath();
    }

    private static class MLVCLIConfig {
        private MLVMethod[] additionalMethodsToCompile;
    }

    private static class MLVMethod {
        private String owner;
        private String name;
        private String desc;
    }

    enum OS {
        WINDOWS,
        LINUX,
        MAC;

        static OS fromString(String name) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "windows":
                    return WINDOWS;
                case "linux":
                    return LINUX;
                case "mac":
                    return MAC;
            }

            throw new IllegalArgumentException("Invalid OS name: " + name + ". Supported OSes: " + Arrays.toString(
                    values()));
        }

        String getTargetTriple() {
            switch (this) {
                case WINDOWS:
                    return "x86_64-pc-windows";
                case LINUX:
                    return "x86_64-pc-linux-gnu";
                case MAC:
                    return "x86_64-apple-darwin";
            }

            throw new IllegalStateException("Unexpected value: " + this);
        }
    }

}
