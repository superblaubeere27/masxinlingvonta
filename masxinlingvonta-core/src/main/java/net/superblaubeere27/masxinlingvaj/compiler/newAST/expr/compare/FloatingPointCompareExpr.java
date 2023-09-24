package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.FloatingPointArithmeticsExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Arrays;
import java.util.Collections;

public class FloatingPointCompareExpr extends Expr {
    private final FloatingPointArithmeticsExpr.FloatingPointType type;
    private final Operator operator;
    private Expr lhs;
    private Expr rhs;

    public FloatingPointCompareExpr(FloatingPointArithmeticsExpr.FloatingPointType type, Operator operator, Expr lhs, Expr rhs) {
        super(FLOAT_COMPARE);

        this.type = type;
        this.operator = operator;

        writeAt(lhs, 0);
        writeAt(rhs, 1);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print((this.type == FloatingPointArithmeticsExpr.FloatingPointType.FLOAT ? "fcmp" : "dcmp") + "(" + this.operator.getOperatorName() + ", ");

        this.lhs.toString(printer);

        printer.print(", ");

        this.rhs.toString(printer);

        printer.print(")");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof FloatingPointCompareExpr && ((FloatingPointCompareExpr) s).operator == this.operator && ((FloatingPointCompareExpr) s).lhs.equivalent(this.lhs) && ((FloatingPointCompareExpr) s).rhs.equivalent(this.rhs);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.lhs = this.read(0);
        } else if (ptr == 1) {
            this.rhs = this.read(1);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    @Override
    public Expr copy() {
        return new FloatingPointCompareExpr(type, this.operator, this.lhs.copy(), this.rhs.copy());
    }

    @Override
    public ImmType getType() {
        return ImmType.BOOL;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return LLVM.LLVMBuildFCmp(ctx.getBuilder(), this.operator.llvmOpcode, this.lhs.compile(ctx), this.rhs.compile(ctx), "fcmp");
    }


    public enum Operator {
        EQUAL("==", LLVM.LLVMRealOEQ, true),
        NOT_EQUAL("!=", LLVM.LLVMRealONE, true),
        LOWER("<", LLVM.LLVMRealOLT, false),
        LOWER_EQUAL("<=", LLVM.LLVMRealOLE, false),
        GREATER(">", LLVM.LLVMRealOGT, false),
        GREATER_EQUAL(">=", LLVM.LLVMRealOGE, false),
        UNORDERED("isnan", LLVM.LLVMRealUNO, true);

        private final String operatorName;
        private final int llvmOpcode;
        private final boolean canSwap;

        Operator(String operatorName, int llvmOpcode, boolean canSwap) {
            this.operatorName = operatorName;
            this.llvmOpcode = llvmOpcode;
            this.canSwap = canSwap;
        }

        public static Operator getByName(String name) {
            return Arrays.stream(values()).filter(x -> x.operatorName.equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid fcmp opcode " + name));
        }

        public String getOperatorName() {
            return operatorName;
        }
    }
}
