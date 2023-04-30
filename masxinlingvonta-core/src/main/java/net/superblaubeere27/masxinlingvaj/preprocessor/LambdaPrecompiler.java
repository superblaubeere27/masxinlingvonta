package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import net.superblaubeere27.masxinlingvaj.preprocessor.codegen.LambdaCodegen;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class LambdaPrecompiler extends AbstractPreprocessor {
    private final AtomicInteger lambdaCounter = new AtomicInteger(0);
    private MLVCompiler compiler;

    private static void linkGeneratedClassToParentClass(CompilerMethod method, InnerClassNode innerClass) {
        ClassNode parentClassNode = method.getParent().getClassNode();

        parentClassNode.innerClasses.add(innerClass);

        if (parentClassNode.nestMembers == null)
            parentClassNode.nestMembers = new ArrayList<>();

        parentClassNode.nestMembers.add(innerClass.name);
    }

    @Override
    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
        this.compiler = compiler;
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

            var targetMethodHandle = (Handle) invokeDynamic.bsmArgs[1];

            makeLambdaFunctionAccessible(targetMethodHandle);

            LambdaCodegen codegen = new LambdaCodegen(targetMethodHandle,
                    invokeDynamic.name,
                    ((Type) invokeDynamic.bsmArgs[0]).getDescriptor(),
                    invokeDynamic.desc,
                    ((Type) invokeDynamic.bsmArgs[2]).getDescriptor(),
                    fullLambdaClassName,
                    false);

            var innerClass = new InnerClassNode(fullLambdaClassName,
                    method.getParent().getName(),
                    null,
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);


            var callSite = codegen.spinInnerClass();

            linkGeneratedClassToParentClass(method, innerClass);

            callSite.getClassNode().nestHostClass = method.getParent().getName();
            callSite.getClassNode().outerClass = method.getParent().getName();

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

    private void makeLambdaFunctionAccessible(Handle targetMethodHandle) {
        this.compiler.getIndex().getMethod(new MethodOrFieldIdentifier(targetMethodHandle)).getNode().access &= ~(Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE);
    }

}
