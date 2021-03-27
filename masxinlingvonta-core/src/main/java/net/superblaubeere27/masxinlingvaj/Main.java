package net.superblaubeere27.masxinlingvaj;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.AbstractPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;

import java.io.File;

import static org.bytedeco.llvm.global.LLVM.LLVMDumpModule;
import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;

public class Main {

    public static void main(String[] args) throws Exception {
        var mlv = new MLV(new CompilerPreprocessor(
                new AbstractPreprocessor() {
                    @Override
                    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
//                        preprocessor.markForCompilation(compiler.getIndex().getMethod("Frame", "mouseMoved", "(Ljava/awt/event/MouseEvent;)V"));
//                        preprocessor.markForCompilation(compiler.getIndex().getMethod("Frame", "actionPerformed", "(Ljava/awt/event/ActionEvent;)V"));
//                        preprocessor.markForCompilation(compiler.getIndex().getMethod("Frame", "paintComponent", "(Ljava/awt/Graphics;)V"));
//                        preprocessor.markForCompilation(compiler.getIndex().getMethod("APathfinding", "findPath", "(LNode;)V"));
                        preprocessor.markForCompilation(compiler.getIndex().getMethod("Test",
                                "test",
                                "()Ljava/util/concurrent/Callable;"));
                    }

                    @Override
                    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {

                    }
                }
//        ,
//                new AnnotationPreprocessor()
        ));

        mlv.loadInput(new File("testJars/Test.jar"));

        mlv.preprocessAndCompile();

        mlv.writeOutput(new File("testJars/Test-obf.jar"));

        mlv.optimize(3);

        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testJars/test-native.ll", new byte[0]);
        LLVMDumpModule(mlv.getLLVMModule());
    }

}
