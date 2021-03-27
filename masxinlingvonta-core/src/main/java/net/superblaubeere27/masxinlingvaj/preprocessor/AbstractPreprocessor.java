package net.superblaubeere27.masxinlingvaj.preprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;

public abstract class AbstractPreprocessor {

    public abstract void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception;

    public abstract void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception;

}
