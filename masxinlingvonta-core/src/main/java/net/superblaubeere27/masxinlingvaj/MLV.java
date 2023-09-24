package net.superblaubeere27.masxinlingvaj;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.OptimizerSettings;
import net.superblaubeere27.masxinlingvaj.io.InputLoader;
import net.superblaubeere27.masxinlingvaj.io.OutputWriter;
import net.superblaubeere27.masxinlingvaj.postprocessor.CompilerPostprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import net.superblaubeere27.masxinlingvaj.utils.ExecutorServiceFactory;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMPassManagerBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMPassManagerRef;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.bytedeco.llvm.global.LLVM.*;

public class MLV {
    private static final ExecutorServiceFactory EXECUTOR_SERVICE_FACTORY = () -> Executors.newFixedThreadPool(12);

    private final CompilerPreprocessor preprocessor;
    private InputLoader.ReadInput input;
    private InputLoader.ReadInput libraries;
    private MLVCompiler compiler;

    public MLV(CompilerPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    public void loadInput(File jar) throws IOException {
        this.input = InputLoader.loadFiles(Collections.singletonList(jar.toURI().toURL()), EXECUTOR_SERVICE_FACTORY, false);
    }

    public void loadLibraries(List<URL> jars) throws IOException {
        this.libraries = InputLoader.loadFiles(jars, EXECUTOR_SERVICE_FACTORY, true);
    }

    public void preprocessAndCompile(OptimizerSettings optimizerSettings) throws Exception {
        this.compiler = new MLVCompiler(this.input.getClassNodes(), this.libraries.getClassNodes(), optimizerSettings);

        preprocessor.preprocess(compiler);

        compiler.compile(preprocessor);

        new CompilerPostprocessor().postprocess(this.compiler);
    }

    public void writeOutput(File file) throws IOException {
        var encoded = OutputWriter.encodeChangedClasses(this.compiler, this.input.getRawData());

        try (FileOutputStream fos = new FileOutputStream(file)) {
            OutputWriter.writeZipFile(fos, encoded);
        }
    }

    public void optimize(int lvl) {
        var module = compiler.getModule();

        BytePointer error = new BytePointer((Pointer) null);

        LLVMPassManagerRef pass = LLVMCreatePassManager();
        LLVMPassManagerBuilderRef passManagerBuilder = LLVMPassManagerBuilderCreate();

        LLVMPassManagerBuilderSetOptLevel(passManagerBuilder, lvl);

        LLVMPassManagerBuilderPopulateModulePassManager(passManagerBuilder, pass);

        LLVMVerifyModule(module, LLVMAbortProcessAction, error);
        LLVMDisposeMessage(error); // Handler == LLVMAbortProcessAction -> No need to check errors

        LLVMRunPassManager(pass, module);

        LLVMDisposePassManager(pass);
    }

    public LLVMModuleRef getLLVMModule() {
        return this.compiler.getModule();
    }
}
