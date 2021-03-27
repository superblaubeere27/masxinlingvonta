package net.superblaubeere27.masxinlingvaj.compiler.code.instructions;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.TranslatedMethod;
import net.superblaubeere27.masxinlingvaj.compiler.code.Block;
import net.superblaubeere27.masxinlingvaj.compiler.code.Instruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.stack.StackSlot;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.global.LLVM;

public class InstanceOfInstruction extends Instruction {
    private final String type;
    private final StackSlot input;
    private final StackSlot output;

    public InstanceOfInstruction(String type, StackSlot input, StackSlot output) {
        this.type = type;
        this.input = input;
        this.output = output;
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public void compile(MLVCompiler compiler, TranslatedMethod translatedMethod, Block block) {
        var builder = translatedMethod.getLlvmBuilder();

        var operand = translatedMethod.getStack().buildStackLoad(builder, this.input);

        LLVMUtils.buildIfElse(
                translatedMethod,
                LLVM.LLVMBuildIsNull(builder, operand, ""),
                () -> translatedMethod.getStack().buildStackStore(builder,
                        this.output,
                        LLVM.LLVMConstInt(this.output.getType().getLLVMType(), 0, 0)),
                () -> {
                    var instanceofResult = compiler.getJni().getJniEnv().callEnvironmentMethod(
                            translatedMethod, translatedMethod.getEnvPtr(),
                            JNIEnv.JNIEnvMethod.IsInstanceOf,
                            operand,
                            translatedMethod.buildFindClass(compiler.getJni(), this.type)
                    );

                    translatedMethod.getStack().buildStackStore(builder, this.output, instanceofResult, true);
                }
        );
    }
}
