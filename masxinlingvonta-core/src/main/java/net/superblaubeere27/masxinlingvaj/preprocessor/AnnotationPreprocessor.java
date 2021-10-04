package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvonta.annotation.Outsource;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

/**
 * Processes {@link net.superblaubeere27.masxinlingvonta.annotation.Outsource} annotation
 */
public class AnnotationPreprocessor extends AbstractPreprocessor {
    private final String OUTSOURCE_ANNOTATION_TYPE = "L" + Outsource.class.getName().replace('.', '/') + ";";

    private MLVCompiler compiler;

    @Override
    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) {
        this.compiler = compiler;
    }

    @Override
    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {
        var methodNode = method.getNode();

        if (methodNode.invisibleAnnotations != null) {
            for (AnnotationNode invisibleAnnotation : methodNode.invisibleAnnotations) {
                processAnnotation(method, preprocessor, invisibleAnnotation, true);
            }
        }

        if (methodNode.visibleAnnotations != null) {
            for (AnnotationNode invisibleAnnotation : methodNode.visibleAnnotations) {
                processAnnotation(method, preprocessor, invisibleAnnotation, false);
            }
        }
    }

    private void processAnnotation(CompilerMethod method, CompilerPreprocessor preprocessor, AnnotationNode annotation, boolean isVisible) {
        if (annotation.desc.equals(OUTSOURCE_ANNOTATION_TYPE)) {
            preprocessor.markForCompilation(method);

            findLambdas(method, preprocessor);

            if (isVisible) {
                method.getNode().visibleAnnotations.remove(annotation);
            }else {
                method.getNode().invisibleAnnotations.remove(annotation);
            }
        }
    }

    private void findLambdas(CompilerMethod method, CompilerPreprocessor preprocessor) {
        for (AbstractInsnNode instruction : method.getNode().instructions) {
            if (instruction.getType() != AbstractInsnNode.INVOKE_DYNAMIC_INSN)
                continue;

            InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) instruction;

            if (!invokeDynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !invokeDynamic.bsm.getName().equals(
                    "metafactory"))
                continue;

            if (!(invokeDynamic.bsmArgs[1] instanceof Handle))
                continue;

            var lambdaHandle = (Handle) invokeDynamic.bsmArgs[1];

            var targetMethod = this.compiler.getIndex().getMethod(lambdaHandle.getOwner(),
                                                                  lambdaHandle.getName(),
                                                                  lambdaHandle.getDesc());

            if (targetMethod == null)
                continue;

            preprocessor.markForCompilation(targetMethod);
        }
    }

}
