package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Collections;

public class IntegerArithmeticsExpr extends Expr {
    private final Operator op;
    private final IntegerType type;
    private Expr lhs;
    private Expr rhs;

    public IntegerArithmeticsExpr(Operator op, IntegerType type, Expr lhs, Expr rhs) {
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
        if (this.type == IntegerType.INT) {
            printer.print(this.op.getOperatorName() + " " + this.lhs + ", " + this.rhs);
        } else {
            printer.print('l' + this.op.getOperatorName() + " " + this.lhs + ", " + this.rhs);
        }
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (!(s instanceof IntegerArithmeticsExpr other)) {
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
        if ((this.op == Operator.SHL || this.op == Operator.SHR || this.op == Operator.USHR) && this.type == IntegerType.LONG)
            return expr;

        if (!expr.getType().equals(this.type.getImmType()))
            throw new IllegalArgumentException("Invalid type " + expr.getType() + " expected " + this.type.getImmType());

        return expr;
    }

    @Override
    public Expr copy() {
        return new IntegerArithmeticsExpr(this.op, this.type, this.lhs.copy(), this.rhs.copy());
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

        LLVMValueRef valueRef;

        switch (this.op) {
            case ADD:
                valueRef = LLVM.LLVMBuildAdd(builder, lhs, rhs, "add");
                break;
            case SUB:
                valueRef = LLVM.LLVMBuildSub(builder, lhs, rhs, "add");
                break;
            case MUL:
                valueRef = LLVM.LLVMBuildMul(builder, lhs, rhs, "add");
                break;
            case DIV:
                valueRef = LLVM.LLVMBuildSDiv(builder, lhs, rhs, "add");
                break;
            case REM:
                valueRef = LLVM.LLVMBuildSRem(builder, lhs, rhs, "add");
                break;
            case SHL:
                if (this.type != IntegerType.LONG) {
                    valueRef = LLVM.LLVMBuildShl(builder, lhs, rhs, "add");
                } else {
                    valueRef = LLVM.LLVMBuildShl(builder, lhs, LLVM.LLVMBuildZExt(builder, rhs, JNIType.LONG.getLLVMType(), "zext"), "add");
                }
                break;
            case SHR:
                if (this.type != IntegerType.LONG) {
                    valueRef = LLVM.LLVMBuildAShr(builder, lhs, rhs, "add");
                } else {
                    valueRef = LLVM.LLVMBuildAShr(builder, lhs, LLVM.LLVMBuildZExt(builder, rhs, JNIType.LONG.getLLVMType(), "zext"), "add");
                }
                break;
            case USHR:
                if (this.type != IntegerType.LONG) {
                    valueRef = LLVM.LLVMBuildLShr(builder, lhs, rhs, "add");
                } else {
                    valueRef = LLVM.LLVMBuildLShr(builder, lhs, LLVM.LLVMBuildZExt(builder, rhs, JNIType.LONG.getLLVMType(), "zext"), "add");
                }
                break;
            case OR:
                valueRef = LLVM.LLVMBuildOr(builder, lhs, rhs, "add");
                break;
            case AND:
                valueRef = LLVM.LLVMBuildAnd(builder, lhs, rhs, "add");
                break;
            case XOR:
                valueRef = LLVM.LLVMBuildXor(builder, lhs, rhs, "add");
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + this.op);
        }

        return valueRef;
    }

    public enum IntegerType {
        INT(ImmType.INT),
        LONG(ImmType.LONG);

        private final ImmType immType;

        IntegerType(ImmType type) {
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
        REM("rem", false),
        SHL("shl", false),
        SHR("shr", false),
        USHR("ushr", false),
        OR("or", true),
        AND("and", true),
        XOR("xor", true);

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
