package net.superblaubeere27.masxinlingvaj.compiler.newAST.asm2ir;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ParamExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstTypeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.RetVoidStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ExceptionCheckStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.MonitorStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.StatementTransaction;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static net.superblaubeere27.masxinlingvaj.utils.TypeUtils.getEffectiveArgumentTypes;

public class NewCodeConverter implements Opcodes {
    private final MLVCompiler compiler;
    final CompilerMethod compilerMethod;

    final ControlFlowGraph cfg;
    final Frame<SourceValue>[] frames;
    /**
     * Contains all labels that there are in this method in order
     */
    final ArrayList<LabelNode> labels;
    /**
     * Maps a basic block to every label in the method
     */
    final HashMap<LabelNode, BasicBlock> labelMap;

    BasicBlock currentBasicBlock = null;


    public NewCodeConverter(MLVCompiler compiler, CompilerMethod compilerMethod) throws AnalyzerException {
        this.compiler = compiler;
        this.compilerMethod = compilerMethod;

        this.cfg = new ControlFlowGraph(new LocalsPool(), compilerMethod, Arrays.stream(getEffectiveArgumentTypes(compilerMethod)).map(ImmType::fromJVMType).toArray(ImmType[]::new), ImmType.fromJVMType(Type.getReturnType(compilerMethod.getNode().desc)));

        this.frames = new Analyzer<>(new SourceInterpreter()).analyze(compilerMethod.getParent().getName(), compilerMethod.getNode());

        this.labels = getLabels(compilerMethod.getNode().instructions);
        this.labelMap = createBlocksForLabels(this.cfg, this.labels);
    }

    /**
     * @return the labels in the given instruction list (in order)
     */
    private static ArrayList<LabelNode> getLabels(InsnList insns) {
        ArrayList<LabelNode> labels = new ArrayList<>();

        for (AbstractInsnNode instruction : insns) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                labels.add((LabelNode) instruction);
            }
        }

        return labels;
    }

    /**
     * Creates blocks for the given labels in the cfg
     *
     * @return A mapping from the label to the associated block
     */
    private HashMap<LabelNode, BasicBlock> createBlocksForLabels(ControlFlowGraph cfg, ArrayList<LabelNode> labels) {
        var labelBlockMap = new HashMap<LabelNode, BasicBlock>();

        for (LabelNode label : labels) {
            labelBlockMap.put(label, new BasicBlock(cfg));
        }

        return labelBlockMap;
    }

    public ControlFlowGraph convert() {
        // Start with a fresh basic block since there is not always a label at the beginning of a method
        this.currentBasicBlock = new BasicBlock(cfg);

        // Tell the CFG to consider the entry block as an entry
        cfg.getEntries().add(currentBasicBlock);

        // Copy the params, i.e. %1I = params[0]
        var localArray = buildCopyParams();

        ExceptionHandlerGenerator exceptionHandler = new ExceptionHandlerGenerator(this);

        LabelNode lastSeenLabel = null;

        for (AbstractInsnNode instruction : compilerMethod.getNode().instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                var nextBasicBlock = labelMap.get(((LabelNode) instruction));

                if (currentBasicBlock != null) {
                    if (!currentBasicBlock.isTerminated()) {
                        currentBasicBlock.add(new UnconditionalBranch(nextBasicBlock));
                    }
                }

                advanceBasicBlock(nextBasicBlock);

                lastSeenLabel = (LabelNode) instruction;

                continue;
            }

            if (currentBasicBlock == null) {
                throw new IllegalStateException("Current basic block is null");
            }

            if (currentBasicBlock.isTerminated()) {
                throw new IllegalStateException("This should never ever happen.");
            }

            var currentHandler = exceptionHandler.getExceptionHandler(lastSeenLabel);

            var result = new InstructionConverter(
                    this,
                    instruction,
                    currentHandler
            ).convert();

            // Build an exception check if needed
            if (result.exceptionHandlerFlag()) {
                if (currentBasicBlock.isTerminated())
                    throw new IllegalStateException("This should not happen.");

                if (result.assumeExceptionOccurred()) {
                    currentBasicBlock.add(new UnconditionalBranch(currentHandler));
                } else {
                    var nextBlock = new BasicBlock(cfg);

                    currentBasicBlock.add(new ExceptionCheckStmt(nextBlock, currentHandler));

                    advanceBasicBlock(nextBlock);
                }
            }
        }

        removeUnusedExceptionHandlers(exceptionHandler);

        // Ensure that the last basic block is also in the CFG
        advanceBasicBlock(null);

        // synchronized keyword needs special handling
        if (Modifier.isSynchronized(this.compilerMethod.getNode().access)) {
            emitSynchronizedCode(cfg, localArray);
        }

        return cfg;
    }

    private void emitSynchronizedCode(ControlFlowGraph cfg, Local[] localArray) {
        var entryBlock = cfg.getEntry();

        var entryInsertIdx = localArray.length == 0 ? 0 : (entryBlock.indexOf(cfg.getLocals().defs.get(localArray[localArray.length - 1])) + 1);

        Local lockedOnLocal;

        if (this.compilerMethod.isStatic()) {
            lockedOnLocal = cfg.getLocals().allocSynthetic(ImmType.OBJECT);

            // Lock on <method/owner/Class>.class.
            entryBlock.add(entryInsertIdx++, new CopyVarStmt(new VarExpr(lockedOnLocal), new ConstTypeExpr(Type.getObjectType(this.compilerMethod.getParent().getName()))));
        } else {
            // Lock on this
            lockedOnLocal = localArray[0];
        }

        entryBlock.add(entryInsertIdx, new MonitorStmt(MonitorStmt.MonitorType.ENTER, new VarExpr(lockedOnLocal)));

        for (BasicBlock vertex : cfg.vertices()) {
            var terminator = vertex.getTerminator();

            if (terminator instanceof RetStmt || terminator instanceof RetVoidStmt) {
                vertex.add(vertex.size() - 1, new MonitorStmt(MonitorStmt.MonitorType.EXIT, new VarExpr(lockedOnLocal)));
            }
        }
    }

    private void removeUnusedExceptionHandlers(ExceptionHandlerGenerator exceptionHandler) {
        StatementTransaction transaction = new StatementTransaction();

        for (BasicBlock handlerBlock : exceptionHandler.getHandlerBlocks()) {
            transaction.exciseBlockIfUnreferenced(handlerBlock);
        }

        transaction.apply();
    }

    void advanceBasicBlock(BasicBlock newBlock) {
        if (this.currentBasicBlock != null) {
            this.cfg.addVertex(this.currentBasicBlock);
        }

        this.currentBasicBlock = newBlock;
    }

    /**
     * @return The locals for the parameters.
     */
    private Local[] buildCopyParams() {
        var paramStackIdx = 0;

        var argumentTypes = cfg.getArgumentTypes();
        var locals = new Local[argumentTypes.length];

        // Put the method parameters in the stack
        for (int i = 0; i < argumentTypes.length; i++) {
            var argumentType = argumentTypes[i];

            var syntheticVar = cfg.getLocals().allocSynthetic(argumentType);

            currentBasicBlock.add(new CopyVarStmt(new VarExpr(syntheticVar), new ParamExpr(cfg, i)));
            currentBasicBlock.add(new CopyVarStmt(new VarExpr(cfg.getLocals().getLocal(paramStackIdx, argumentType)), new VarExpr(syntheticVar)));

            locals[i] = syntheticVar;
            paramStackIdx += argumentType.getJvmStackSize();
        }

        return locals;
    }

    /**
     * Statement that is used to cancel the method execution after an exception occurred.
     */
    Stmt createReturnNull() {
        Stmt nullReturnStatement;

        if (cfg.getReturnType() == ImmType.VOID) {
            nullReturnStatement = new RetVoidStmt();
        } else {
            nullReturnStatement = new RetStmt(cfg.getReturnType().createConstNull());
        }
        return nullReturnStatement;
    }

}
