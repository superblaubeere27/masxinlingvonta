package net.superblaubeere27.masxinlingvaj.compiler.tree;

import net.superblaubeere27.masxinlingvaj.analysis.BytecodeMethodAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;

public class CompilerMethod {
    private final CompilerClass parent;
    private final MethodOrFieldIdentifier identifier;
    private final MethodNode methodNode;
    private boolean isMarkedForCompilation;
    private boolean wasCompiled;
    private boolean wasOutsourced;

    private BytecodeMethodAnalyzer.MethodInfo methodAnalysisInfo = null;

    public CompilerMethod(CompilerClass cc, MethodNode methodNode) {
        this.parent = cc;
        this.identifier = new MethodOrFieldIdentifier(cc.getName(), methodNode.name, methodNode.desc);
        this.methodNode = methodNode;
    }

    public CompilerClass getParent() {
        return parent;
    }

    public MethodNode getNode() {
        return methodNode;
    }

    public MethodOrFieldIdentifier getIdentifier() {
        return identifier;
    }

    public boolean isStatic() {
        return Modifier.isStatic(this.methodNode.access);
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(this.methodNode.access);
    }

    /**
     * Should only be called from {@link CompilerPreprocessor#markForCompilation(CompilerMethod)}
     */
    public void markForCompilation(CompilerPreprocessor preprocessor) {
        this.isMarkedForCompilation = true;
    }

    public void setWasCompiled(boolean outsource) {
        if (outsource) {
            this.parent.setModifiedFlag();
        }

        this.wasOutsourced = outsource;
        this.wasCompiled = true;
    }

    public BytecodeMethodAnalyzer.MethodInfo getMethodAnalysisInfo() {
        return methodAnalysisInfo;
    }

    public void updateAnalysisInfo(ControlFlowGraph cfg) {
        this.methodAnalysisInfo = BytecodeMethodAnalyzer.analyze(this, cfg);
    }

    public boolean wasCompiled() {
        return this.wasCompiled;
    }

    public boolean wasOutsourced() {
        return this.wasOutsourced;
    }

    public boolean wasMarkedForCompilation() {
        return this.isMarkedForCompilation;
    }

    public boolean canBeOutsourced() {
        return !this.parent.isInterface() && (this.methodNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
    }

    public boolean isLibrary() {
        return this.parent.isLibrary();
    }
}
