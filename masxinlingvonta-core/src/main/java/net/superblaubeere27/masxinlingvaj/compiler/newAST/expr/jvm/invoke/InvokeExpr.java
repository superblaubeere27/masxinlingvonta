package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke;

import net.superblaubeere27.masxinlingvaj.compiler.code.instructions.InvokeInstruction;
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
import org.bytedeco.llvm.LLVM.LLVMValueRef;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Objects;

public abstract class InvokeExpr extends Expr {
    protected final Type[] argTypes;
    protected final Type returnType;
    protected MethodOrFieldIdentifier target;

    protected InvokeExpr(int opcode, MethodOrFieldIdentifier target, Expr[] args) {
        super(opcode);

        this.target = target;
        this.argTypes = Type.getArgumentTypes(target.getDesc());
        this.returnType = Type.getReturnType(target.getDesc());

        for (int i = 0; i < args.length; i++) {
            writeAt(args[i], i);
        }
    }

    @Override
    public final boolean isTerminating() {
        return false;
    }

    @Override
    public ImmType getType() {
        return ImmType.fromJVMType(returnType);
    }

    public MethodOrFieldIdentifier getTarget() {
        return target;
    }

    public void setTarget(MethodOrFieldIdentifier target) {
        this.target = target;
    }

    public Type[] getArgTypes() {
        return argTypes;
    }

    public Type getReturnType() {
        return returnType;
    }
}
