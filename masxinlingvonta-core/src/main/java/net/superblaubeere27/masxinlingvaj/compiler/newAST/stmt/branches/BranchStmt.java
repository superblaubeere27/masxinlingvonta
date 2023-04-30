package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches;

import net.superblaubeere27.masxinlingvaj.compiler.graph.BasicFlowEdge;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;


public abstract class BranchStmt extends Stmt {
    protected final BasicBlock[] nextBasicBlocks;

    protected BranchStmt(int opcode, BasicBlock[] nextBasicBlocks) {
        super(opcode);

        this.nextBasicBlocks = nextBasicBlocks;
    }

    public void updateEdges() {
        onRemoval(this.getBlock());
        onAddition(this.getBlock());
    }

    @Override
    public void onAddition(BasicBlock basicBlock) {
        super.onAddition(basicBlock);

        for (BasicBlock dst : this.nextBasicBlocks) {
            basicBlock.getGraph().addEdge(new BasicFlowEdge(basicBlock, dst));
        }
    }

    @Override
    public void onRemoval(BasicBlock basicBlock) {
        super.onRemoval(basicBlock);

        ControlFlowGraph cfg = basicBlock.getGraph();

        for (BasicBlock dst : this.nextBasicBlocks) {
            cfg.removeEdge(new BasicFlowEdge(basicBlock, dst));
        }
    }

    public BasicBlock[] getNextBasicBlocks() {
        return nextBasicBlocks;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return this.isConditionEquivalent(s) && Arrays.equals(((BranchStmt) s).nextBasicBlocks, this.nextBasicBlocks);
    }

    /**
     * Checks if the condition of two branches are equivalent. The targets do not matter
     */
    public abstract boolean isConditionEquivalent(CodeUnit s);

    @Override
    public boolean isTerminating() {
        return true;
    }

    public int replaceBasicBlock(BasicBlock oldBlock, BasicBlock newBlock) {
        var nextBlocks = this.getNextBasicBlocks();

        int replacedCount = 0;

        onRemoval(this.getBlock());

        for (int i = 0; i < nextBlocks.length; i++) {
            if (nextBlocks[i].equals(oldBlock)) {
                nextBlocks[i] = newBlock;
                replacedCount++;
            }
        }

        onAddition(this.getBlock());

        return replacedCount;
    }

    public void refactor(HashMap<BasicBlock, BasicBlock> forwardedBlocks) {
        BasicBlock[] nextBasicBlocks = this.getNextBasicBlocks();

        if (this.getBlock() != null)
            this.onRemoval(this.getBlock());

        for (int i = 0; i < nextBasicBlocks.length; i++) {
            if (forwardedBlocks.containsKey(nextBasicBlocks[i])) {
                nextBasicBlocks[i] = forwardedBlocks.get(nextBasicBlocks[i]);
            }
        }

        if (this.getBlock() != null)
            this.onAddition(this.getBlock());
    }
}
