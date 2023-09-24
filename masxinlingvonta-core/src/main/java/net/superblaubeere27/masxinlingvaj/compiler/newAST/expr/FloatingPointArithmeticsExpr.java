package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class FloatingPointArithmeticsExpr extends Expr {
    private final Operator op;
    private final FloatingPointType type;
    private Expr lhs;
    private Expr rhs;

    public FloatingPointArithmeticsExpr(Operator op, FloatingPointType type, Expr lhs, Expr rhs) {
        super(BINOP);

        this.op = op;
        this.type = type;
        this.lhs = lhs;
        this.rhs = rhs;

        this.writeAt(lhs, 0);
        this.writeAt(rhs, 1);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(this.getOpName() + " " + this.lhs + ", " + this.rhs);
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (!(s instanceof FloatingPointArithmeticsExpr other)) {
            return false;
        }

        return other.op == this.op && other.lhs.equivalent(this.rhs) && other.rhs.equivalent(this.rhs);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.lhs = ensureTypeValidity(read(0));
        } else if (ptr == 1) {
            this.rhs = ensureTypeValidity(read(1));
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    private Expr ensureTypeValidity(Expr expr) {
        if (!expr.getType().equals(this.type.getImmType()))
            throw new IllegalArgumentException("Invalid type");

        return expr;
    }

    @Override
    public Expr copy() {
        return new FloatingPointArithmeticsExpr(this.op, this.type, this.lhs.copy(), this.rhs.copy());
    }

    @Override
    public ImmType getType() {
        return this.type.getImmType();
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var builder = ctx.getBuilder();

        var lhs = this.lhs.compile(ctx);
        var rhs = this.rhs.compile(ctx);

        var opName = getOpName();

        switch (this.op) {
            case ADD:
                return LLVM.LLVMBuildFAdd(builder, lhs, rhs, opName);
            case SUB:
                return LLVM.LLVMBuildFSub(builder, lhs, rhs, opName);
            case MUL:
                return LLVM.LLVMBuildFMul(builder, lhs, rhs, opName);
            case DIV:
                return LLVM.LLVMBuildFDiv(builder, lhs, rhs, opName);
            case REM:
                return LLVM.LLVMBuildFRem(builder, lhs, rhs, opName);
            default:
                throw new IllegalStateException("Unexpected value: " + this.op);
        }
    }

    private String getOpName() {
        return (this.type == FloatingPointType.FLOAT ? 'f' : 'd') + this.op.getOperatorName();
    }

    public enum FloatingPointType {
        FLOAT(ImmType.FLOAT),
        DOUBLE(ImmType.DOUBLE);

        private final ImmType immType;

        FloatingPointType(ImmType type) {
            this.immType = type;
        }

        public ImmType getImmType() {
            return immType;
        }
    }

    public enum Operator {
        ADD("add", true),
        SUB("sub", false),
        MUL("mul", true),
        DIV("div", false),
        REM("rem", false);

        private final String operatorName;
        private final boolean canSwap;

        Operator(String operatorName, boolean canSwap) {
            this.operatorName = operatorName;
            this.canSwap = canSwap;
        }

        public String getOperatorName() {
            return operatorName;
        }
    }
}
