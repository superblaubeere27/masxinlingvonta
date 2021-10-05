package net.superblaubeere27.masxinlingvaj.postprocessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;

public class CompilerPostprocessor {
    private static final List<AbstractPostprocessor> PREPROCESSORS = Arrays.asList(
//            new LambdaPrecompiler(),
            new StandardPostProcessor(),
            new LoaderAdderPostprocessor()
    );

    private final ArrayList<AbstractPostprocessor> preprocessors;

    public CompilerPostprocessor(AbstractPostprocessor... preprocessors) {
        this.preprocessors = new ArrayList<>();

        this.preprocessors.addAll(Arrays.asList(preprocessors));
        this.preprocessors.addAll(PREPROCESSORS);
    }

    /**
     * Runs all postprocessors
     *
     * @throws Exception can be thrown by the postprocessors
     */
    public void postprocess(MLVCompiler compiler) throws Exception {
        for (AbstractPostprocessor preprocessor : this.preprocessors) {
            preprocessor.postProcess(compiler);
        }
    }

}
