package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvonta.annotation.Outsource;
import org.objectweb.asm.tree.AnnotationNode;

/**
 * Processes {@link net.superblaubeere27.masxinlingvonta.annotation.Outsource} annotation
 */
public class AnnotationPreprocessor extends AbstractPreprocessor {

    private final String OUTSOURCE_ANNOTATION_TYPE = "L" + Outsource.class.getName().replace('.', '/') + ";";

    @Override
    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {

    }

    @Override
    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {
        var methodNode = method.getNode();

        if (methodNode.invisibleAnnotations != null) {
            for (AnnotationNode invisibleAnnotation : methodNode.invisibleAnnotations) {
                processAnnotation(method, preprocessor, invisibleAnnotation);
            }
        }

        if (methodNode.visibleAnnotations != null) {
            for (AnnotationNode invisibleAnnotation : methodNode.visibleAnnotations) {
                processAnnotation(method, preprocessor, invisibleAnnotation);
            }
        }
    }

    private void processAnnotation(CompilerMethod method, CompilerPreprocessor preprocessor, AnnotationNode annotation) {
        if (annotation.desc.equals(OUTSOURCE_ANNOTATION_TYPE)) {
            preprocessor.markForCompilation(method);
        }
    }

}
