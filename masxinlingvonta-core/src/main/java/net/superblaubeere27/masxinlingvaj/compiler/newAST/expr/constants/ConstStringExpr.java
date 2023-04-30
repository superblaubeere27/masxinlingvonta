package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.constants;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.Collections;

public class ConstStringExpr extends ConstExpr {
    private final String value;

    public ConstStringExpr(String value) {
        super(CONST_STRING);

        this.value = value;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("\"", "\\\"");
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("\"" + escape(this.value) + "\"");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof ConstStringExpr && ((ConstStringExpr) s).value.equals(this.value);
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new ConstStringExpr(this.value);
    }

    @Override
    public ImmType getType() {
        return ImmType.OBJECT;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.NewStringUTF, ctx.buildGlobalString(this.value, "str"));
    }
}
