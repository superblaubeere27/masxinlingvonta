package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Arrays;
import java.util.Collections;

public class IntegerCompareExpr extends Expr {
    private final Operator operator;
    private Expr lhs;
    private Expr rhs;

    public IntegerCompareExpr(Operator operator, Expr lhs, Expr rhs) {
        super(INTEGER_COMPARE);

        this.operator = operator;

        writeAt(lhs, 0);
        writeAt(rhs, 1);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        this.lhs.toString(printer);

        printer.print(" " + this.operator.getOperatorName() + " ");

        this.rhs.toString(printer);
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof IntegerCompareExpr && ((IntegerCompareExpr) s).operator == this.operator && ((IntegerCompareExpr) s).lhs.equivalent(this.lhs) && ((IntegerCompareExpr) s).rhs.equivalent(this.rhs);
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

    public Operator getOperator() {
        return operator;
    }

    public Expr getLhs() {
        return lhs;
    }

    public Expr getRhs() {
        return rhs;
    }

    @Override
    public ImmType getType() {
        return ImmType.BOOL;
    }

    @Override
    public Expr copy() {
        return new IntegerCompareExpr(this.operator, this.lhs.copy(), this.rhs.copy());
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        return LLVM.LLVMBuildICmp(ctx.getBuilder(), this.operator.llvmOpcode, this.lhs.compile(ctx), this.rhs.compile(ctx), "icmp");
    }

    public enum Operator {
        EQUAL("==", LLVM.LLVMIntEQ, true),
        NOT_EQUAL("!=", LLVM.LLVMIntNE, true),
        LOWER("<", LLVM.LLVMIntSLT, false),
        LOWER_EQUAL("<=", LLVM.LLVMIntSLE, false),
        GREATER(">", LLVM.LLVMIntSGT, false),
        GREATER_EQUAL(">=", LLVM.LLVMIntSGE, false);

        private final String operatorName;
        private final int llvmOpcode;
        private final boolean canSwap;

        Operator(String operatorName, int llvmOpcode, boolean canSwap) {
            this.operatorName = operatorName;
            this.llvmOpcode = llvmOpcode;
            this.canSwap = canSwap;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public int getLlvmOpcode() {
            return llvmOpcode;
        }

        public static Operator getByName(String name) {
            return Arrays.stream(values()).filter(x -> x.operatorName.equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Invalid fcmp opcode " + name));
        }
    }
}
