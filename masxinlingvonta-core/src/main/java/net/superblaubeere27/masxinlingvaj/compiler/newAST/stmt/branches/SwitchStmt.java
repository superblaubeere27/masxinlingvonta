package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a switch statement jump.
 */
public class SwitchStmt extends BranchStmt {
    private final int[] keys;
    private Expr operand;

    public SwitchStmt(Expr operand, int[] keys, BasicBlock[] blocks, BasicBlock defaultBlock) {
        this(operand, keys, getTargetArray(blocks, defaultBlock));

        if (keys.length != blocks.length) {
            throw new IllegalArgumentException();
        }
    }

    private SwitchStmt(Expr operand, int[] keys, BasicBlock[] nextBasicBlocks) {
        super(SWITCH, nextBasicBlocks);

        writeAt(operand, 0);

        this.keys = keys;

//        Arrays.sort(this.keys);
    }

    private static BasicBlock[] getTargetArray(BasicBlock[] blocks, BasicBlock defaultBlock) {
        BasicBlock[] targets = new BasicBlock[blocks.length + 1];

        targets[targets.length - 1] = defaultBlock;

        System.arraycopy(blocks, 0, targets, 0, blocks.length);

        return targets;
    }

    public int[] getKeys() {
        return keys;
    }

    public BasicBlock getDefaultBlock() {
        return this.getNextBasicBlocks()[this.getNextBasicBlocks().length - 1];
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.operand = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public Expr getOperand() {
        return operand;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("switch ");

        this.operand.toString(printer);

        printer.print(" " + IntStream.range(0, this.keys.length).mapToObj(x -> "[" + this.keys[x] + ": " + getNextBasicBlocks()[x] + "]").collect(Collectors.joining(", ")) + ", default: " + this.getNextBasicBlocks()[this.keys.length]);
    }

    @Override
    public boolean isConditionEquivalent(CodeUnit s) {
        if (!(s instanceof SwitchStmt switchStatement)) {
            return false;
        }

        return Arrays.equals(switchStatement.keys, this.keys) && switchStatement.operand.equivalent(this.operand);
    }

    @Override
    public Stmt copy() {
        return new SwitchStmt(this.operand.copy(), this.keys.clone(), this.nextBasicBlocks.clone());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileSwitch(this);
    }
}
