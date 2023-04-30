package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CompilerPreprocessor {
    private static final List<AbstractPreprocessor> PREPROCESSORS = Arrays.asList(
            new LambdaPrecompiler(),
            new InstructionExtractor()
    );

    private final ArrayList<AbstractPreprocessor> preprocessors;
    private final ArrayList<CompilerMethod> methodsToCompile = new ArrayList<>();

    private HashMap<CompilerClass, MethodNode> methodsToAdd;
    private ArrayList<ClassNode> classesToAdd;

    public CompilerPreprocessor(AbstractPreprocessor... preprocessors) {
        this.preprocessors = new ArrayList<>();

        this.preprocessors.addAll(Arrays.asList(preprocessors));
        this.preprocessors.addAll(PREPROCESSORS);
    }

    /**
     * Runs all preprocessors
     *
     * @throws Exception can be thrown by the preprocessors
     */
    public void preprocess(MLVCompiler compiler) throws Exception {
        methodsToAdd = new HashMap<>();
        classesToAdd = new ArrayList<>();

        for (AbstractPreprocessor preprocessor : this.preprocessors) {
            preprocessor.init(compiler, this);
        }

        for (AbstractPreprocessor preprocessor : this.preprocessors) {
            for (CompilerClass cc : compiler.getIndex().getClasses()) {
                if (cc.isLibrary())
                    continue;

                for (CompilerMethod method : cc.getMethods()) {
                    preprocessor.preprocess(method, this);
                }
            }
        }

        this.methodsToAdd.forEach((compilerClass, extractedMethod) -> compilerClass.addMethod(compiler,
                extractedMethod));
        compiler.getIndex().addGeneratedClasses(this.classesToAdd);
    }

    public void markForCompilation(CompilerMethod compilerMethod) {
        this.methodsToCompile.add(compilerMethod);

        compilerMethod.markForCompilation(this);
    }

    public ArrayList<CompilerMethod> getMethodsToCompile() {
        return methodsToCompile;
    }

    public void addMethod(CompilerClass clazz, MethodNode method) {
        this.methodsToAdd.put(clazz, method);
    }

    public void addClass(ClassNode classNode) {
        this.classesToAdd.add(classNode);
    }
}
