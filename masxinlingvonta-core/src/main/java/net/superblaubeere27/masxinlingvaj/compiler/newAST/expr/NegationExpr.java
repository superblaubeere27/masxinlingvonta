package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class NegationExpr extends Expr {
    private Expr input;

    public NegationExpr(Expr input) {
        super(NEGATION);

        writeAt(input, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("-(");

        this.input.toString(printer);

        printer.print(')');
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof NegationExpr && ((NegationExpr) s).input.equivalent(this.input);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.input = read(0);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new NegationExpr(this.input.copy());
    }

    @Override
    public ImmType getType() {
        return this.input.getType();
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        if (this.getType().isFloat()) {
            return LLVM.LLVMBuildFNeg(ctx.getBuilder(), this.input.compile(ctx), "fneg");
        } else {
            return LLVM.LLVMBuildNeg(ctx.getBuilder(), this.input.compile(ctx), "neg");
        }
    }
}
