package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.branches;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.BasicBlock;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;


public class UnconditionalBranch extends BranchStmt {
    public UnconditionalBranch(BasicBlock target) {
        super(UNCONDITIONAL_BRANCH, new BasicBlock[]{target});
    }

    @Override
    public void onChildUpdated(int ptr) {

    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("br " + this.getTarget().toString() + ";");
    }

    public BasicBlock getTarget() {
        return this.nextBasicBlocks[0];
    }

    @Override
    public boolean isConditionEquivalent(CodeUnit s) {
        return s instanceof UnconditionalBranch;
    }

    @Override
    public Stmt copy() {
        return new UnconditionalBranch(this.getTarget());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileBr(this);
    }
}
