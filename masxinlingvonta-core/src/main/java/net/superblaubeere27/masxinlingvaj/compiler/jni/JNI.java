package net.superblaubeere27.masxinlingvaj.compiler.jni;

import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.objectweb.asm.Type;

import static org.bytedeco.llvm.global.LLVM.LLVMGetModuleContext;

public class JNI {
    public static int JNI_OK = 0;
    public static int JNI_ERR = -1;
    /**
     * JNI version of this interface, currently JNI_VERSION_1_2
     */
    public static int JNI_VERSION = 0x00010002;

    private final LLVMModuleRef module;
    private final JNIEnv jniEnv;

    public JNI(LLVMModuleRef module) {
        this.module = module;
        this.jniEnv = new JNIEnv(LLVMGetModuleContext(module));
    }

    public JNIEnv getJniEnv() {
        return jniEnv;
    }

    public LLVMModuleRef getModule() {
        return module;
    }

    public JNIType toNativeType(Type returnType) {
        switch (returnType.getSort()) {
            case 0:
                return JNIType.VOID;
            case 1:
                return JNIType.BOOLEAN;
            case 2:
                return JNIType.CHAR;
            case 3:
                return JNIType.BYTE;
            case 4:
                return JNIType.SHORT;
            case 5:
                return JNIType.INT;
            case 6:
                return JNIType.FLOAT;
            case 7:
                return JNIType.LONG;
            case 8:
                return JNIType.DOUBLE;
            case 9:
            case 10:
                return JNIType.OBJECT;
            default:
                throw new IllegalArgumentException("Unsupported type exception " + returnType);
        }
    }

}
