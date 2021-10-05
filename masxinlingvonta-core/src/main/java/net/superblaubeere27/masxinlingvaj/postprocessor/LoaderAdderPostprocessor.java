package net.superblaubeere27.masxinlingvaj.postprocessor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.postprocessor.extensions.NativeLoaderExtension;
import net.superblaubeere27.masxinlingvaj.utils.ASMUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class LoaderAdderPostprocessor extends AbstractPostprocessor {

    private final static String NATIVE_LOADER_NAME = NativeLoaderExtension.class.getName().replace('.', '/');

    @Override
    public void postProcess(MLVCompiler compiler) {
        AtomicInteger counter = new AtomicInteger(0);

        for (CompilerClass classNode : compiler.getIndex().getClasses()) {
            MethodNode mainMethod = ASMUtils.getMainMethod(classNode.getClassNode());

            if (mainMethod == null) {
                continue;
            }

            mainMethod.instructions.insertBefore(mainMethod.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, NATIVE_LOADER_NAME, "loadNatives", "()V"));
            counter.incrementAndGet();
        }

        if (counter.get() == 0) {
            System.err.println("Main method is not found.");
        }else {
            try {
                compiler.getIndex().addGeneratedClass(generateLoaderClass(compiler.getIndex().getClasses().get(0).getClassNode().version));
            } catch (IOException ioException) {
                System.err.println("Failed to generate loader class.");
                ioException.printStackTrace();
            }
        }
    }

    /**
     * This generates a new class node for loading native libraries depending on NativeLoaderExtension.class.
     *
     * @param version version of the new class node.
     * @return native loader class node.
     * @throws IOException
     */
    private ClassNode generateLoaderClass(int version) throws IOException {
        ClassNode NATIVE_LOADER_CLASS = new ClassNode();
        ClassReader classReader = new ClassReader(NATIVE_LOADER_NAME);
        classReader.accept(NATIVE_LOADER_CLASS, ClassReader.SKIP_DEBUG);

        ClassNode loaderClass = new ClassNode();
        loaderClass.visit(version, Opcodes.ACC_PUBLIC, NATIVE_LOADER_NAME, null, "java/lang/Object", null);

        loaderClass.fields.addAll(NATIVE_LOADER_CLASS.fields);
        loaderClass.methods.addAll(NATIVE_LOADER_CLASS.methods);

        return loaderClass;
    }
}
