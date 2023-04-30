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

public class ConstFloatExpr extends ConstExpr {
    private final float value;

    public ConstFloatExpr(float value) {
        super(CONST_FLOAT);

        this.value = value;
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(this.value + "f");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ConstFloatExpr && ((ConstFloatExpr) s).value == this.value;
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new ConstFloatExpr(this.value);
    }

    @Override
    public ImmType getType() {
        return ImmType.FLOAT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.FIRST, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return LLVM.LLVMConstReal(JNIType.FLOAT.getLLVMType(), this.value);
    }
}
