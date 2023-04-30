package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ControlFlowGraph;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class ParamExpr extends Expr {
    private final ImmType type;
    private final int paramIdx;

    public ParamExpr(ControlFlowGraph cfg, int paramIdx) {
        super(GET_PARAM);

        this.type = cfg.getArgumentTypes()[paramIdx];
        this.paramIdx = paramIdx;
    }

    private ParamExpr(ImmType type, int paramIdx) {
        super(GET_PARAM);

        this.type = type;
        this.paramIdx = paramIdx;
    }

    public int getParamIdx() {
        return paramIdx;
    }

    @Override
    public ParamExpr copy() {
        return new ParamExpr(this.type, paramIdx);
    }

    @Override
    public ImmType getType() {
        return this.type;
    }

    @Override
    public void onChildUpdated(int ptr) {
        raiseChildOutOfBounds(ptr);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
//		printer.print("(" + type + ")" + local.toString());
        printer.print("params[" + this.paramIdx + "]");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (s instanceof ParamExpr) {
            ParamExpr var = (ParamExpr) s;

            return this.paramIdx == var.paramIdx;
        }
        return false;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return ctx.fixTypeOut(LLVM.LLVMGetParam(ctx.getLLVMFunction(), this.paramIdx + (ctx.getCfg().getCompilerMethod().isStatic() ? 2 : 1)), this.type.toNativeType(), this.type.toNativeType().getStackStorageType());
    }
}
