package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;

import static org.bytedeco.llvm.global.LLVM.LLVMBuildRetVoid;

public class RetVoidStmt extends Stmt {

    public RetVoidStmt() {
        super(RET_VOID);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("ret void;");
    }

    @Override
    public boolean isTerminating() {
        return true;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s.getOpcode() == RET_VOID;
    }

    @Override
    public Stmt copy() {
        return new RetVoidStmt();
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileRetVoid(this);
    }

}
