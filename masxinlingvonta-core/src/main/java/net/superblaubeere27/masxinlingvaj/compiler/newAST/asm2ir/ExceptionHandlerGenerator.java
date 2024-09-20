package net.superblaubeere27.masxinlingvaj.compiler.newAST.asm2ir;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ExceptionHandler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.VarExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants.ConstIntExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.exceptionHandling.CatchExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.object.InstanceOfExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.ConditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches.UnconditionalBranch;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.copy.CopyVarStmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm.ClearExceptionStateStmt;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.*;

class ExceptionHandlerGenerator {
    private final NewCodeConverter codeConverter;
    /**
     * Contains the responsible exception handlers for the given labels
     */
    private final HashMap<LabelNode, List<ExceptionHandler>> exceptionHandlers;

    /**
     * This map contains the blocks that should be jumped to when an exception occurs
     */
    private final HashMap<LabelNode, BasicBlock> exceptionHandlerBlocks;

    /**
     * If the exception was now caught, this block will be jumped to.
     */
    private final BasicBlock defaultExceptionHandler;

    ExceptionHandlerGenerator(NewCodeConverter codeConverter) {
        this.codeConverter = codeConverter;

        this.exceptionHandlers = findExceptionHandlers(codeConverter);
        this.exceptionHandlerBlocks = buildExceptionHandlerBlocks(codeConverter, this.exceptionHandlers);

        this.defaultExceptionHandler = createDefaultExceptionHandler(codeConverter);
    }

    private static BasicBlock createDefaultExceptionHandler(NewCodeConverter codeConverter) {
        var defaultHandler = new BasicBlock(codeConverter.cfg);

        defaultHandler.add(codeConverter.createReturnNull());

        return defaultHandler;
    }

    private static HashMap<LabelNode, BasicBlock> buildExceptionHandlerBlocks(
            NewCodeConverter codeConverter,
            HashMap<LabelNode, List<ExceptionHandler>> exceptionHandlers
    ) {
        var cfg = codeConverter.cfg;
        var handlerBlockMap = new HashMap<List<ExceptionHandler>, BasicBlock>();
        var blockHandlerMap = new HashMap<LabelNode, BasicBlock>();

        for (Map.Entry<LabelNode, List<ExceptionHandler>> exceptionHandler : exceptionHandlers.entrySet()) {
            var handlerList = exceptionHandler.getValue();

            // Is there already a handler block compiled for the given handler?
            if (handlerBlockMap.containsKey(handlerList)) {
                blockHandlerMap.put(exceptionHandler.getKey(), handlerBlockMap.get(handlerList));
                continue;
            }

            BasicBlock handlerBegin = new BasicBlock(cfg);

            buildHandler(codeConverter, handlerBegin, handlerList);

            handlerBlockMap.put(handlerList, handlerBegin);
            blockHandlerMap.put(exceptionHandler.getKey(), handlerBegin);
        }

        return blockHandlerMap;
    }

    private static void buildHandler(NewCodeConverter codeConverter, BasicBlock handlerBegin, List<ExceptionHandler> handlers) {
        var cfg = codeConverter.cfg;

        BasicBlock currentBlock = handlerBegin;

        var caughtExceptionLocal = cfg.getLocals().getStackLocal(0, ImmType.OBJECT);

        currentBlock.add(new CopyVarStmt(new VarExpr(caughtExceptionLocal), new CatchExpr()));

        for (ExceptionHandler handler : handlers) {
            if (handler.getType() == null) {
                currentBlock.add(new ClearExceptionStateStmt());
                currentBlock.add(new UnconditionalBranch(handler.getHandlerBlock()));

                return;
            }

            BasicBlock handlerBlock = new BasicBlock(cfg);

            handlerBlock.add(new ClearExceptionStateStmt());
            handlerBlock.add(new UnconditionalBranch(handler.getHandlerBlock()));

            BasicBlock nextBasicBlock = new BasicBlock(cfg);

            currentBlock.add(new ConditionalBranch(new IntegerCompareExpr(IntegerCompareExpr.Operator.NOT_EQUAL, new InstanceOfExpr(handler.getType(), new VarExpr(caughtExceptionLocal)), new ConstIntExpr(0)), handlerBlock, nextBasicBlock));

            currentBlock = nextBasicBlock;
        }

        // Exception wasn't caught? Stop method execution.
        currentBlock.add(codeConverter.createReturnNull());
    }

    private static HashMap<LabelNode, List<ExceptionHandler>> findExceptionHandlers(NewCodeConverter codeConverter) {
        var handlers = new HashMap<LabelNode, List<ExceptionHandler>>();

        for (TryCatchBlockNode tryCatchBlock : codeConverter.compilerMethod.getNode().tryCatchBlocks) {
            var exceptionHandler = new ExceptionHandler(tryCatchBlock, codeConverter.labelMap.get(tryCatchBlock.handler));

            var startIdx = codeConverter.labels.indexOf(tryCatchBlock.start);
            var endIdx = codeConverter.labels.indexOf(tryCatchBlock.end);

            if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx)
                throw new IllegalStateException("Invalid try catch block.");

            for (LabelNode labelNode : codeConverter.labels.subList(startIdx, endIdx)) {
                handlers.computeIfAbsent(labelNode, e -> new ArrayList<>()).add(exceptionHandler);
            }
        }

        return handlers;
    }

    public BasicBlock getExceptionHandler(LabelNode lastSeenLabel) {
        var handlerBlock = lastSeenLabel != null ? exceptionHandlerBlocks.get(lastSeenLabel) : null;

        // When there is no exception handler specified, just jump to the default handler
        if (handlerBlock != null) {
            return handlerBlock;
        } else {
            return defaultExceptionHandler;
        }
    }

    public Collection<BasicBlock> getHandlerBlocks() {
        return exceptionHandlerBlocks.values();
    }
}
