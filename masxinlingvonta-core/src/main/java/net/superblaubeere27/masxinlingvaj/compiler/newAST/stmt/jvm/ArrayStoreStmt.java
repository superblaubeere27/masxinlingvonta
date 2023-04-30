package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt.jvm;

import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIEnv;
import net.superblaubeere27.masxinlingvaj.compiler.jni.JNIType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.CodeUnit;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Expr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.ImmToLLVMIRCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.Stmt;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.utils.TabbedStringWriter;
import net.superblaubeere27.masxinlingvaj.utils.LLVMIntrinsic;
import net.superblaubeere27.masxinlingvaj.utils.LLVMUtils;
import org.bytedeco.llvm.global.LLVM;

public class ArrayStoreStmt extends Stmt {
    private final JNIType type;

    private Expr array;
    private Expr index;
    private Expr element;

    public ArrayStoreStmt(JNIType type, Expr array, Expr index, Expr element) {
        super(ARRAY_STORE);

        this.type = type;

        writeAt(array, 0);
        writeAt(index, 1);
        writeAt(element, 2);
    }

    @Override
    public void onChildUpdated(int ptr) {
        if (ptr == 0) {
            this.array = read(0);
        } else if (ptr == 1) {
            this.index = read(1);
        } else if (ptr == 2) {
            this.element = read(2);
        } else {
            raiseChildOutOfBounds(ptr);
        }
    }

    public JNIType getType() {
        return type;
    }

    public Expr getArray() {
        return array;
    }

    public Expr getIndex() {
        return index;
    }

    public Expr getElement() {
        return element;
    }

    @Override
    public void toString(TabbedStringWriter writer) {
        this.array.toString(writer);

        writer.print("[");

        this.index.toString(writer);

        writer.print("] = ");

        this.element.toString(writer);

        writer.print(" (" + this.type.getDisplayName() + "[]);");
    }

    @Override
    public boolean isTerminating() {
        return false;
    }

    @Override
    public boolean equivalent(CodeUnit s) {
        if (!(s instanceof ArrayStoreStmt)) {
            return false;
        }

        var other = ((ArrayStoreStmt) s);

        return other.array.equivalent(this.array) && other.element.equivalent(this.element) && other.index.equivalent(this.index);
    }

    @Override
    public Stmt copy() {
        return new ArrayStoreStmt(this.type, this.array.copy(), this.index.copy(), this.element.copy());
    }

    @Override
    public void compile(ImmToLLVMIRCompiler ctx) {
        ctx.compileArrayStore(this);
    }

    public JNIEnv.JNIEnvMethod getJNIMethod() {
        switch (this.type) {
            case BOOLEAN:
                return JNIEnv.JNIEnvMethod.SetBooleanArrayRegion;
            case CHAR:
                return JNIEnv.JNIEnvMethod.SetCharArrayRegion;
            case BYTE:
                return JNIEnv.JNIEnvMethod.SetByteArrayRegion;
            case SHORT:
                return JNIEnv.JNIEnvMethod.SetShortArrayRegion;
            case INT:
                return JNIEnv.JNIEnvMethod.SetIntArrayRegion;
            case LONG:
                return JNIEnv.JNIEnvMethod.SetLongArrayRegion;
            case FLOAT:
                return JNIEnv.JNIEnvMethod.SetFloatArrayRegion;
            case DOUBLE:
                return JNIEnv.JNIEnvMethod.SetDoubleArrayRegion;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }
}
