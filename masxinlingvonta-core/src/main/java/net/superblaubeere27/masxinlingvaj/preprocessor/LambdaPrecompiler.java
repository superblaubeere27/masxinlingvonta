package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.codegen.LambdaCodegen;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.concurrent.atomic.AtomicInteger;

public class LambdaPrecompiler extends AbstractPreprocessor {
    private final AtomicInteger lambdaCounter = new AtomicInteger(0);

    @Override
    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
    }

    @Override
    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {
        for (AbstractInsnNode instruction : method.getNode().instructions) {
            if (instruction.getType() != AbstractInsnNode.INVOKE_DYNAMIC_INSN)
                continue;

            InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) instruction;

            if (!invokeDynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !invokeDynamic.bsm.getName().equals(
                    "metafactory"))
                continue;

            var lambdaClassName = "Lambda" + this.lambdaCounter.getAndIncrement();
            var fullLambdaClassName = method.getParent().getName() + "$" + lambdaClassName;

            LambdaCodegen codegen = new LambdaCodegen((Handle) invokeDynamic.bsmArgs[1],
                    invokeDynamic.name,
                    ((Type) invokeDynamic.bsmArgs[0]).getDescriptor(),
                    invokeDynamic.desc,
                    ((Type) invokeDynamic.bsmArgs[2]).getDescriptor(),
                    fullLambdaClassName,
                    false);

            var innerClass = new InnerClassNode(fullLambdaClassName,
                    method.getParent().getName(),
                    lambdaClassName,
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);


            var callSite = codegen.spinInnerClass();

            method.getParent().getClassNode().innerClasses.add(innerClass);
            method.getParent().getClassNode().nestMembers.add(fullLambdaClassName);

            callSite.getClassNode().innerClasses.add(innerClass);
            callSite.getClassNode().nestHostClass = method.getParent().getName();

            var handle = callSite.getHandle();

            MethodInsnNode replacement;

            if (handle.getTag() == Opcodes.H_INVOKESTATIC) {
                replacement = new MethodInsnNode(Opcodes.INVOKESTATIC,
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        handle.isInterface());
            } else {
                throw new IllegalStateException("Invalid tag");
            }

            method.getNode().instructions.insert(instruction, replacement);
            method.getNode().instructions.remove(instruction);

            preprocessor.addClass(callSite.getClassNode());

            method.getParent().setModifiedFlag();
        }
    }

}
