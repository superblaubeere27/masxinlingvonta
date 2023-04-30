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

public class PrimitiveCastExpr extends Expr {
    private CastTarget target;
    private Expr input;

    public PrimitiveCastExpr(Expr input, CastTarget target) {
        super(PRIMITIVE_CAST);

        this.target = target;

        this.writeAt(input, 0);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("cast<" + this.target.getDisplayName() + ">(");

        this.input.toString(printer);

        printer.print(")");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof PrimitiveCastExpr && ((PrimitiveCastExpr) s).target == this.target && ((PrimitiveCastExpr) s).input.equivalent(this.input);
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
        return new PrimitiveCastExpr(this.input.copy(), this.target);
    }

    @Override
    public ImmType getType() {
        return this.target.getStorageType();
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Collections.emptyList());
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var inputType = this.input.getType();
        var outType = this.target.realType;
        var builder = ctx.getBuilder();

        var operand = this.input.compile(ctx);

        if (inputType.isInteger() && this.target.storageType.isInteger()) {
            return ctx.fixTypeOut(LLVM.LLVMBuildIntCast2(builder, operand, outType.getLLVMType(), this.target != CastTarget.CHAR ? 1 : 0, ""), this.target.realType, this.target.storageType.toNativeType());
        } else if (inputType.isFloat() && this.target.storageType.isFloat()) {
            return LLVM.LLVMBuildFPCast(builder, operand, outType.getLLVMType(), "");
        } else if (inputType.isFloat() && this.target.storageType.isInteger()) {
            return LLVM.LLVMBuildFPToSI(builder, operand, outType.getLLVMType(), "");
        } else if (inputType.isInteger() && this.target.storageType.isFloat()) {
            return LLVM.LLVMBuildSIToFP(builder, operand, outType.getLLVMType(), "");
        } else {
            throw new IllegalStateException("???");
        }
    }

    public enum CastTarget {
        BYTE("byte", ImmType.INT, JNIType.BYTE),
        SHORT("short", ImmType.INT, JNIType.SHORT),
        CHAR("char", ImmType.INT, JNIType.CHAR),
        INT("int", ImmType.INT, JNIType.INT),
        LONG("long", ImmType.LONG, JNIType.LONG),
        FLOAT("float", ImmType.FLOAT, JNIType.FLOAT),
        DOUBLE("double", ImmType.DOUBLE, JNIType.DOUBLE);

        private final String displayName;
        private final ImmType storageType;
        private final JNIType realType;

        CastTarget(String displayName, ImmType storageType, JNIType realType) {
            this.displayName = displayName;
            this.storageType = storageType;
            this.realType = realType;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ImmType getStorageType() {
            return storageType;
        }
    }
}
