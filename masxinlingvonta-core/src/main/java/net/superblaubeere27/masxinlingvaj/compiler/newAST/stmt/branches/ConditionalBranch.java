package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.*;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;


public class ConditionalBranch extends BranchStmt {
    public static final int ON_IF_IDX = 0;
    public static final int ON_ELSE_IDX = 1;
    private Expr cond;

    public ConditionalBranch(Expr cond, BasicBlock ifTarget, BasicBlock elseTarget) {
        super(CONDITIONAL_BRANCH, new BasicBlock[]{ifTarget, elseTarget});

        writeAt(cond, 0);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            Expr cond = read(0);

            if (cond.getType() != ImmType.BOOL)
                throw new IllegalArgumentException("Conditional branch needs a boolean condition");

            this.cond = cond;
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public BasicBlock getIfTarget() {
        return this.getNextBasicBlocks()[0];
    }

    public BasicBlock getElseTarget() {
        return this.getNextBasicBlocks()[1];
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("br ");

        this.cond.toString(printer);

        printer.print(" if " + this.getIfTarget() + ", else " + this.getElseTarget());
    }

    @Override
    public boolean isConditionEquivalent(CodeUnit s) {
        return s instanceof ConditionalBranch && this.cond.equivalent(((ConditionalBranch) s).cond);
    }

    @Override
    public Stmt copy() {
        return new ConditionalBranch(this.cond.copy(), getIfTarget(), getElseTarget());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileCondBr(this);
    }

    public Expr getCond() {
        return cond;
    }
}
