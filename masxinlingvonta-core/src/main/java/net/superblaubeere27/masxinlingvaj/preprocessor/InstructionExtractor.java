package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Extracts not supported instructions to methods
 */
public class InstructionExtractor extends AbstractPreprocessor implements Opcodes {

    private static Type[] getArgumentTypesOfInstruction(AbstractInsnNode instruction) {
        if (instruction instanceof MultiANewArrayInsnNode) {
            var multiANewArray = ((MultiANewArrayInsnNode) instruction);

            Type[] types = new Type[multiANewArray.dims];

            Arrays.fill(types, Type.getType(multiANewArray.desc).getElementType());

            return types;
        } else if (instruction instanceof InvokeDynamicInsnNode) {
            var invokeDynamic = ((InvokeDynamicInsnNode) instruction);

            return Type.getArgumentTypes(invokeDynamic.desc);
        }

        throw new IllegalArgumentException("Instruction not supported");
    }

    private static Type getReturnTypeOfInstruction(AbstractInsnNode instruction) {
        if (instruction instanceof MultiANewArrayInsnNode) {
            return Type.getType(((MultiANewArrayInsnNode) instruction).desc);
        } else if (instruction instanceof InvokeDynamicInsnNode) {
            var invokeDynamic = ((InvokeDynamicInsnNode) instruction);

            return Type.getReturnType(invokeDynamic.desc);
        }

        throw new IllegalArgumentException("Instruction not supported");
    }

    @Override
    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {

    }

    @Override
    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {
        if (!method.wasMarkedForCompilation())
            return;

        var parent = method.getParent();

        var instructions = method.getNode().instructions;

        for (AbstractInsnNode instruction : instructions.toArray()) {
            if (!shouldExtract(instruction))
                continue;

            var argumentTypes = getArgumentTypesOfInstruction(instruction);
            var returnType = getReturnTypeOfInstruction(instruction);

            MethodNode extractedMethod = buildWrapperMethod(method, instruction, argumentTypes, returnType);

            // Replace the instruction of the current method
            instructions.insert(instruction,
                    new MethodInsnNode(INVOKESTATIC, parent.getName(), extractedMethod.name, extractedMethod.desc));
            instructions.remove(instruction);

            parent.setModifiedFlag();

            preprocessor.addMethod(parent, extractedMethod);
        }
    }

    private MethodNode buildWrapperMethod(CompilerMethod method, AbstractInsnNode instruction, Type[] argumentTypes, Type returnType) {
        // Build the method descriptor, e.g. (II)J
        String methodDesc = "(" + Arrays.stream(argumentTypes).map(Type::toString).collect(Collectors.joining()) + ")" + returnType.toString();

        MethodNode extractedMethod = new MethodNode(ACC_PRIVATE | ACC_STATIC,
                method.getParent().suggestStaticMethodName(methodDesc),
                methodDesc,
                null,
                new String[0]);

        InsnList insnList = new InsnList();

        int paramIndex = 0;

        // Load the arguments to the stack
        for (Type argumentType : argumentTypes) {
            insnList.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD), paramIndex));

            paramIndex += argumentType.getSize();
        }

        // Execute the instruction
        insnList.add(instruction.clone(null));

        insnList.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));

        extractedMethod.instructions = insnList;
        return extractedMethod;
    }

    /**
     * Should this instruction be extracted to a method?
     */
    private boolean shouldExtract(AbstractInsnNode instruction) {
        return instruction.getOpcode() == MULTIANEWARRAY || instruction.getOpcode() == INVOKEDYNAMIC;
    }
}
