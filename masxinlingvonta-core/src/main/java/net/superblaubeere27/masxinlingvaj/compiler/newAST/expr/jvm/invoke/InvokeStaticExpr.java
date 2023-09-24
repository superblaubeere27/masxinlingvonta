package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke;

import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.InvokeInstruction;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.codegen.FunctionCodegenContext;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.ExprMetadata;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ThrowsProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.WritesMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.bytedeco.llvm.global.LLVM;

import java.util.Arrays;

public class InvokeStaticExpr extends InvokeExpr {

    public InvokeStaticExpr(MethodOrFieldIdentifier target, Expr[] argTypes) {
        super(INVOKE_STATIC, target, argTypes);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print(this.target.getOwner() + "::" + this.target.getName() + this.target.getDesc() + "(");

        for (int i = 0; i < this.argTypes.length; i++) {
            this.read(i).toString(printer);

            if (i != argTypes.length - 1) {
                printer.print(", ");
            }
        }
        printer.print(")");
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        return s instanceof InvokeStaticExpr
                && ((InvokeStaticExpr) s).target.equals(this.target)
                && s.childrenEquivalent(this);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr >= this.argTypes.length)
            raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new InvokeStaticExpr(this.target, Arrays.stream(this.children, 0, this.argTypes.length).map(x -> x == null ? null : x.copy()).toArray(Expr[]::new));
    }

    @Override
    public Expr[] getChildrenInStackOrder() {
        return Arrays.copyOfRange(this.children, this.getChildPointer(), this.getChildPointer() + this.argTypes.length);
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJVMType(returnType);
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Arrays.asList(ReadsMemoryProperty.INSTANCE, WritesMemoryProperty.INSTANCE, ThrowsProperty.INSTANCE));
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var classId = ctx.buildFindClass(this.target.getOwner());

        FunctionCodegenContext otherCtx = ctx.getCompiler().getFunctionCodegenContext(ctx.getCompiler().getIndex().getMethod(this.target));

        LLVMValueRef returnValue;

        if (otherCtx != null) {
            returnValue = compileDirectCall(ctx, classId, otherCtx);
        } else {
            returnValue = compileJNICall(ctx, classId);
        }

        var returnType = ctx.toNativeType(this.returnType);

        return returnType == JNIType.VOID ? returnValue : ctx.fixTypeOut(returnValue, returnType, returnType.getStackStorageType());
    }

    private LLVMValueRef compileDirectCall(FunctionCodegenContext ctx, LLVMValueRef classId, FunctionCodegenContext otherCtx) {
        LLVMValueRef returnValue;
        var arguments = new LLVMValueRef[2 + this.argTypes.length];

        arguments[0] = ctx.getEnvPtr();
        arguments[1] = classId;

        for (int i = 0; i < this.argTypes.length; i++) {
            LLVMValueRef arg = ctx.fixTypeIn(this.children[i].compile(ctx), ctx.toNativeType(this.argTypes[i]), false);

            if (this.children[i].getType() == ImmType.OBJECT) {
                arg = ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.NewLocalRef, arg);
            }

            arguments[i + 2] = arg;
        }

        returnValue = LLVM.LLVMBuildCall(ctx.getBuilder(), otherCtx.getLLVMFunction(), new PointerPointer<>(arguments), arguments.length, otherCtx.getCfg().getReturnType() == ImmType.VOID ? "" : "call_direct");

        return returnValue;
    }

    private LLVMValueRef compileJNICall(FunctionCodegenContext ctx, LLVMValueRef classId) {
        var methodId = ctx.buildGetMethodID(this.target, classId, true);

        var arguments = new LLVMValueRef[2 + this.argTypes.length];

        arguments[0] = classId;
        arguments[1] = methodId;

        for (int i = 0; i < this.argTypes.length; i++) {
            arguments[i + 2] = ctx.fixTypeIn(this.children[i].compile(ctx), ctx.toNativeType(this.argTypes[i]), true);
        }

        return ctx.callEnvironmentMethod(InvokeInstruction.getJNIMethod(ctx.toNativeType(this.returnType), true, false), arguments);
    }
}
