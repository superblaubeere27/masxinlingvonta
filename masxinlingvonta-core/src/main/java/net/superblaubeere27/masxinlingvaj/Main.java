package net.superblaubeere27.masxinlingvaj;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.AbstractPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.objectweb.asm.Opcodes;

import java.io.File;

import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToFile;

public class Main {

    public static void main(String[] args) throws Exception {
        var mlv = new MLV(new CompilerPreprocessor(
                new AbstractPreprocessor() {
                    @Override
                    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
                        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
                            if (!aClass.getClassNode().name.startsWith("net/ccbluex/liquidbounce/"))
                                continue;

                            for (CompilerMethod method : aClass.getMethods()) {
                                if ((method.getNode().access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0)
                                    continue;
                                if (method.getNode().name.startsWith("<"))
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

        mlv.loadInput(new File("testJars/liquidbounce.jar"));

        mlv.preprocessAndCompile();

        mlv.writeOutput(new File("testJars/liquidbounce-obf.jar"));

        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testJars/lb-native.ll", new byte[0]);

        mlv.optimize(3);

        LLVMPrintModuleToFile(mlv.getLLVMModule(), "testJars/lb-native.ll", new byte[0]);
//        LLVMDumpModule(mlv.getLLVMModule());
    }

    private static Runnable lambda() {
        return () -> System.out.println("Helo");
    }

}
