package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class ConstBoolExpr extends ConstExpr {
    private final boolean value;

    public ConstBoolExpr(boolean value) {
        super(CONST_INT);

        this.value = value;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(Boolean.toString(this.value));
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ConstBoolExpr && ((ConstBoolExpr) s).value == this.value;
    }

    @Override
    public ImmType getType() {
        return ImmType.BOOL;
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new ConstBoolExpr(this.value);
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.FIRST, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return LLVM.LLVMConstInt(JNIType.BOOLEAN.getLLVMType(), this.value ? 1 : 0, 1);
    }
}
