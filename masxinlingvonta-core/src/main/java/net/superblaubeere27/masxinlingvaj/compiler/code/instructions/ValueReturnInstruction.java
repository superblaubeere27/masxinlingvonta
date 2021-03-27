package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.branches.TerminatingInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import org.bytedeco.llvm.global.LLVM;
import org.objectweb.asm.Type;

public class ValueReturnInstruction extends TerminatingInstruction {
    private final StackSlot value;

    public ValueReturnInstruction(StackSlot value) {
        this.value = value;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var value = translatedMethod.getStack().buildStackLoad(translatedMethod.getLlvmBuilder(), this.value);

        var returnType = Type.getReturnType(translatedMethod.getCompilerMethod().getNode().desc);

        if (this.value.getType() == JNIType.INT && returnType.getSort() != Type.INT) {
            value = LLVM.LLVMBuildIntCast(translatedMethod.getLlvmBuilder(),
                    value,
                    compiler.getJni().toNativeType(Type.getReturnType(translatedMethod.getCompilerMethod().getNode().desc)).getLLVMType(),
                    "cst");
        }

        // Not much to do here...
        LLVM.LLVMBuildRet(translatedMethod.getLlvmBuilder(), value);
    }
}
