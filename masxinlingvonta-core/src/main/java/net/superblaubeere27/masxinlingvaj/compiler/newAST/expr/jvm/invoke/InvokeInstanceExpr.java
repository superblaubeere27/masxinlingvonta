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

public class InvokeInstanceExpr extends InvokeExpr {
    private InvokeInstanceType type;

    public InvokeInstanceExpr(MethodOrFieldIdentifier target, Expr instance, Expr[] args, InvokeInstanceType type) {
        this(type, target, getTargetArray(instance, args));
    }

    private InvokeInstanceExpr(InvokeInstanceType type, MethodOrFieldIdentifier target, Expr[] children) {
        super(INVOKE_INSTANCE, target, children);

        this.type = type;
    }

    private static Expr[] getTargetArray(Expr instance, Expr[] args) {
        Expr[] targets = new Expr[args.length + 1];

        targets[targets.length - 1] = instance;

        System.arraycopy(args, 0, targets, 0, args.length);

        return targets;
    }

    public Expr getInstanceExpr() {
        return read(this.argTypes.length);
    }

    @Override
    public void toString(TabbedStringWriter printer) {
        printer.print("((" + target.getOwner() + ") ");

        read(this.argTypes.length).toString(printer);

        printer.print(")." + target.getName() + target.getDesc() + "<" + this.type + ">(");

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
        return s instanceof InvokeInstanceExpr
                && ((InvokeInstanceExpr) s).type.equals(this.type)
                && ((InvokeInstanceExpr) s).target.equals(this.target)
                && s.childrenEquivalent(this);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr >= this.argTypes.length + 1)
            raiseChildOutOfBounds(ptr);
    }

    @Override
    public Expr copy() {
        return new InvokeInstanceExpr(type, this.target, Arrays.stream(this.children, 0, this.argTypes.length + 1).map(Expr::copy).toArray(Expr[]::new));
    }

    @Override
    public Expr[] getChildrenInStackOrder() {
        Expr[] params = new Expr[this.argTypes.length + 1];

        params[0] = this.read(this.argTypes.length);

        System.arraycopy(this.children, this.getChildPointer(), params, 1, this.argTypes.length);

        return params;
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJVMType(returnType);
    }

    public void setType(InvokeInstanceType type) {
        this.type = type;
    }

    @Override
    public LLVMValueRef compile(FunctionCodegenContext ctx) {
        var classId = ctx.buildFindClass(this.target.getOwner());
        var methodId = ctx.buildGetMethodID(this.target, classId, false);

        FunctionCodegenContext targetMethodCtx = null;

        if (this.type == InvokeInstanceType.INVOKE_SPECIAL) {
            targetMethodCtx = ctx.getCompiler().getFunctionCodegenContext(ctx.getCompiler().getIndex().getMethod(this.target));
        }

        LLVMValueRef returnValue;

        if (targetMethodCtx != null) {
            returnValue = compileDirectCall(ctx, targetMethodCtx);
        } else {
            returnValue = compileJNICall(ctx, classId, methodId);
        }

        var returnType = ctx.toNativeType(this.returnType);

        return returnType == JNIType.VOID ? returnValue : ctx.fixTypeOut(returnValue, returnType, returnType.getStackStorageType());
    }

    private LLVMValueRef compileDirectCall(FunctionCodegenContext ctx, FunctionCodegenContext otherCtx) {
        LLVMValueRef returnValue;

        var arguments = new LLVMValueRef[2 + this.argTypes.length];

        var instance = this.children[this.argTypes.length].compile(ctx);
        var newLocalRefToInstance = ctx.callEnvironmentMethod(JNIEnv.JNIEnvMethod.NewLocalRef, instance);

        arguments[0] = ctx.getEnvPtr();
        arguments[1] = newLocalRefToInstance;

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

    private LLVMValueRef compileJNICall(FunctionCodegenContext ctx, LLVMValueRef classId, LLVMValueRef methodId) {
        LLVMValueRef[] arguments;

        if (this.type == InvokeInstanceType.INVOKE_SPECIAL) {
            arguments = new LLVMValueRef[3 + this.argTypes.length];

            arguments[0] = this.children[this.argTypes.length].compile(ctx);
            arguments[1] = classId;
            arguments[2] = methodId;

            for (int i = 0; i < this.argTypes.length; i++) {
                arguments[i + 3] = ctx.fixTypeIn(this.children[i].compile(ctx), ctx.toNativeType(this.argTypes[i]), true);
            }
        } else {
            arguments = new LLVMValueRef[2 + this.argTypes.length];

            arguments[0] = this.children[this.argTypes.length].compile(ctx);
            arguments[1] = methodId;

            for (int i = 0; i < this.argTypes.length; i++) {
                arguments[i + 2] = ctx.fixTypeIn(this.children[i].compile(ctx), ctx.toNativeType(this.argTypes[i]), true);
            }
        }

        var returnValue = ctx.callEnvironmentMethod(InvokeInstruction.getJNIMethod(ctx.toNativeType(this.returnType), false, this.type == InvokeInstanceType.INVOKE_SPECIAL), arguments);
        return returnValue;
    }

    public InvokeInstanceType getInvokeType() {
        return this.type;
    }

    @Override
    public ExprMetadata getMetadata() {
        return new ExprMetadata(ExprMetadata.ExprClass.SECOND, Arrays.asList(ReadsMemoryProperty.INSTANCE, WritesMemoryProperty.INSTANCE, ThrowsProperty.INSTANCE));
    }

    public enum InvokeInstanceType {
        INVOKE_VIRTUAL("invokevirtual"),
        INVOKE_SPECIAL("invokespecial"),
        INVOKE_INTERFACE("invokeinterface");

        private final String displayName;

        InvokeInstanceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
