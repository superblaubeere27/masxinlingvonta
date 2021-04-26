package net.superblaubeere27.masxinlingvaj.compiler;

import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.CodeConverter;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNI;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.ArrayList;

import static org.bytedeco.llvm.global.LLVM.LLVMModuleCreateWithName;

public class MLVCompiler {
    private final CompilerIndex index;
    private JNI jni;
    private LLVMModuleRef module;

    public MLVCompiler(ArrayList<ClassNode> classNodes) {
        this.index = new CompilerIndex(classNodes);

        this.createModule();
    }

    private void createModule() {
        this.module = LLVMModuleCreateWithName("mlv");
        this.jni = new JNI(this.module);

        var fltUsedConst = LLVM.LLVMAddGlobal(this.module, LLVM.LLVMInt32Type(), "_fltused");
        LLVM.LLVMSetGlobalConstant(fltUsedConst, 1);
    }

    public void compileMethod(CompilerMethod method) throws AnalyzerException {
        var translatedMethod = TranslatedMethod.createFromCompilerMethod(this, method);

        // Convert the instructions
        var blocks = CodeConverter.convert(this, method, translatedMethod);

        for (Block block : blocks) {
            block.compile(this, translatedMethod);
        }

        // Tie the stack allocations block to the first block
        LLVM.LLVMPositionBuilderAtEnd(translatedMethod.getLlvmBuilder(),
                translatedMethod.getStack().getAllocationsBlock());
        LLVM.LLVMBuildBr(translatedMethod.getLlvmBuilder(), blocks.get(0).getLlvmBlock());

        method.setWasCompiled();
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
}
