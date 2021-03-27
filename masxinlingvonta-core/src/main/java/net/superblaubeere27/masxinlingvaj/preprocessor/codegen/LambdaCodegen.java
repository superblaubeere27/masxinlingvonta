/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package net.superblaubeere27.masxinlingvaj.preprocessor.codegen;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.lang.invoke.LambdaConversionException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.invoke.MethodHandleInfo.*;

/**
 * Java's Lamba metafactory, ported to ASM
 */
public final class LambdaCodegen implements Opcodes {
    private static final int CLASSFILE_VERSION = V1_8;
    private static final String METHOD_DESCRIPTOR_VOID = "()V";
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String NAME_CTOR = "<init>";
    private static final String NAME_FACTORY = "get$Lambda";

    //Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda";
    private static final String NAME_NOT_SERIALIZABLE_EXCEPTION = "java/io/NotSerializableException";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String DESCR_METHOD_WRITE_OBJECT = "(Ljava/io/ObjectOutputStream;)V";
    private static final String DESCR_METHOD_READ_OBJECT = "(Ljava/io/ObjectInputStream;)V";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_METHOD_READ_OBJECT = "readObject";
    private static final String NAME_METHOD_WRITE_OBJECT = "writeObject";

    private static final String DESCR_CLASS = "Ljava/lang/Class;";
    private static final String DESCR_STRING = "Ljava/lang/String;";
    private static final String DESCR_OBJECT = "Ljava/lang/Object;";
    private static final String DESCR_CTOR_SERIALIZED_LAMBDA
            = "(" + DESCR_CLASS + DESCR_STRING + DESCR_STRING + DESCR_STRING + "I"
            + DESCR_STRING + DESCR_STRING + DESCR_STRING + DESCR_STRING + "[" + DESCR_OBJECT + ")V";

    private static final String DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION = "(Ljava/lang/String;)V";
    private static final String[] SER_HOSTILE_EXCEPTIONS = new String[]{NAME_NOT_SERIALIZABLE_EXCEPTION};


    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implClass;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods "(I)Ljava/lang/String;"
    private final String constructorType;        // Generated class constructor type "(CC)void"
    private final ClassNode cw;                    // ASM class writer
    private final String[] argNames;                 // Generated names for the constructor arguments
    private final String[] argDescs;                 // Type descriptors for the constructor arguments
    private final String lambdaClassName;            // Generated name for the generated class "X$$Lambda$1"
    private final String implMethodType;
    private final String invokedType;
    private final boolean isInterface;
    private final int implKind;
    private final String samBase;
    private final String samMethodName;
    private final String samMethodDesc;
    private final String instantiatedMethodType;

    public LambdaCodegen(Handle implInfo, String invokedName, String samMethodDesc, String invokedType, String instantiatedMethodType, String lambdaClassName, boolean isInterface) {
        this.implMethodType = implInfo.getDesc();
        this.invokedType = invokedType;
        this.isInterface = isInterface;

        switch (implInfo.getTag()) {
            case H_INVOKEVIRTUAL:
            case H_INVOKEINTERFACE:
                this.implClass = Type.getArgumentTypes(invokedType)[0].getInternalName();
                // reference kind reported by implInfo may not match implMethodType's first param
                // Example: implMethodType is (Cloneable)String, implInfo is for Object.toString
                this.implKind = isInterface ? H_INVOKEINTERFACE : H_INVOKEVIRTUAL;
                boolean implIsInstanceMethod = true;
                break;
            case REF_invokeSpecial:
                // JDK-8172817: should use referenced class here, but we don't know what it was
                this.implClass = implInfo.getOwner();
                this.implKind = REF_invokeSpecial;
                implIsInstanceMethod = true;
                break;
            case REF_invokeStatic:
            case REF_newInvokeSpecial:
                // JDK-8172817: should use referenced class here for invokestatic, but we don't know what it was
                this.implClass = implInfo.getOwner();
                this.implKind = implInfo.getTag();
                implIsInstanceMethod = false;
                break;
            default:
                throw new IllegalArgumentException(String.format("Unsupported MethodHandle kind: %s", implInfo));
        }

        this.samBase = Type.getReturnType(invokedType).getInternalName();
        this.samMethodName = invokedName;
        this.samMethodDesc = samMethodDesc;
        this.instantiatedMethodType = instantiatedMethodType;

        var invokedMethodArgumentTypes = Type.getArgumentTypes(invokedType);

        implMethodName = implInfo.getName();
        implMethodDesc = implInfo.getDesc();
        constructorType = methodType(invokedMethodArgumentTypes, Type.VOID_TYPE);
        this.lambdaClassName = lambdaClassName;
        cw = new ClassNode();
        int parameterCount = invokedMethodArgumentTypes.length;
        if (parameterCount > 0) {
            argNames = new String[parameterCount];
            argDescs = new String[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                argNames[i] = "arg$" + (i + 1);
                argDescs[i] = invokedMethodArgumentTypes[i].getDescriptor();
            }
        } else {
            argNames = argDescs = EMPTY_STRING_ARRAY;
        }
    }

    private static boolean isPrimitive(Type t) {
        return t.getSort() < Type.ARRAY;
    }

    private String methodType(Type[] argumentTypes, Type returnType) {
        return "(" + Arrays.stream(argumentTypes).map(Type::getDescriptor).collect(Collectors.joining()) + ")" + returnType;
    }

    /**
     * Generate a class file which implements the functional
     * interface, define and return the class.
     *
     * @return a Class which implements the functional interface
     * @throws LambdaConversionException If properly formed functional interface
     *                                   is not found
     * @implNote The class that is generated does not include signature
     * information for exceptions that may be present on the SAM method.
     * This is to reduce classfile size, and is harmless as checked exceptions
     * are erased anyway, no one will ever compile against this classfile,
     * and we make no guarantees about the reflective properties of lambda
     * objects.
     */
    public PreGeneratedCallSite spinInnerClass() throws LambdaConversionException {
        String[] interfaces = new String[]{this.samBase};

        cw.visit(CLASSFILE_VERSION, ACC_SUPER + ACC_FINAL,
                lambdaClassName, null,
                JAVA_LANG_OBJECT, interfaces);

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argDescs.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL,
                    argNames[i],
                    argDescs[i],
                    null, null);
            fv.visitEnd();
        }

        generateConstructor();

        generateFactory();

        // Forward the SAM method
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, this.samMethodName,
                samMethodDesc, null, null);

        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);

        new ForwardingMethodGenerator(mv).generate(samMethodDesc);

        generateSerializationHostileMethods();

        cw.visitEnd();

        return new PreGeneratedCallSite(new Handle(H_INVOKESTATIC,
                this.lambdaClassName,
                NAME_FACTORY,
                this.invokedType,
                false), cw);
    }

    /**
     * Generate the factory method for the class
     */
    private void generateFactory() {
        MethodVisitor m = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, NAME_FACTORY, invokedType, null, null);
        m.visitCode();
        m.visitTypeInsn(NEW, lambdaClassName);
        m.visitInsn(Opcodes.DUP);

        Type[] argumentTypes = Type.getArgumentTypes(invokedType);

        int parameterCount = argumentTypes.length;

        for (int typeIndex = 0, varIndex = 0; typeIndex < parameterCount; typeIndex++) {
            Type argType = argumentTypes[typeIndex];

            m.visitVarInsn(argType.getOpcode(ILOAD), varIndex);

            varIndex += argType.getSize();
        }

        m.visitMethodInsn(INVOKESPECIAL, lambdaClassName, NAME_CTOR, constructorType, false);
        m.visitInsn(ARETURN);
        m.visitMaxs(-1, -1);
        m.visitEnd();
    }

    /**
     * Generate the constructor for the class
     */
    private void generateConstructor() {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PRIVATE, NAME_CTOR,
                constructorType, null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, JAVA_LANG_OBJECT, NAME_CTOR,
                METHOD_DESCRIPTOR_VOID, false);

        Type[] argumentTypes = Type.getArgumentTypes(invokedType);

        int parameterCount = argumentTypes.length;

        for (int i = 0, lvIndex = 0; i < parameterCount; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            Type argType = argumentTypes[i];
            ctor.visitVarInsn(argType.getOpcode(ILOAD), lvIndex + 1);
            lvIndex += argType.getSize();
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i], argDescs[i]);
        }
        ctor.visitInsn(RETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
    }

    /**
     * Generate a readObject/writeObject method that is hostile to serialization
     */
    private void generateSerializationHostileMethods() {
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                NAME_METHOD_WRITE_OBJECT, DESCR_METHOD_WRITE_OBJECT,
                null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("MLV generated lambdas are not serializable");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                NAME_METHOD_READ_OBJECT, DESCR_METHOD_READ_OBJECT,
                null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("MLV generated lambdas are not serializable");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * This class generates a method body which calls the lambda implementation
     * method, converting arguments, as needed.
     */
    private class ForwardingMethodGenerator extends TypeConvertingMethodAdapter {

        ForwardingMethodGenerator(MethodVisitor mv) {
            super(ASM5, mv);
        }

        void generate(String methodType) {
            visitCode();

            if (implKind == H_NEWINVOKESPECIAL) {
                visitTypeInsn(NEW, implClass);
                visitInsn(DUP);
            }
            for (int i = 0; i < argNames.length; i++) {
                visitVarInsn(ALOAD, 0);
                visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argDescs[i]);
            }

            convertArgumentTypes(methodType);

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implClass,
                    implMethodName, implMethodDesc,
                    isInterface);

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return'
            // instruction will pop the unneeded result
            Type implReturnClass = Type.getReturnType(implMethodType);
            Type samReturnClass = Type.getReturnType(methodType);
            convertType(implReturnClass, samReturnClass, samReturnClass);
            visitInsn(samReturnClass.getOpcode(IRETURN));
            // Maxs computed by ClassWriter.COMPUTE_MAXS,these arguments ignored
            visitMaxs(-1, -1);
            visitEnd();
        }

        private void convertArgumentTypes(String samType) {
            int lvIndex = 0;

            Type[] samParameters = Type.getArgumentTypes(samType);
            int captureArity = Type.getArgumentTypes(invokedType).length;
            var implMethodParamTypes = Type.getArgumentTypes(implMethodType);
            var instantiatedMethodTypes = Type.getArgumentTypes(instantiatedMethodType);

            for (int i = 0; i < samParameters.length; i++) {
                Type argType = samParameters[i];

                visitVarInsn(argType.getOpcode(ILOAD), lvIndex + 1);

                lvIndex += argType.getSize();

                convertType(argType, implMethodParamTypes[captureArity + i], instantiatedMethodTypes[i]);
            }
        }

        void convertType(Type arg, Type target, Type functional) {
            if (arg.equals(target) && arg.equals(functional)) {
                return;
            }
            if (arg.getSort() == Type.VOID || target.getSort() == Type.VOID) {
                return;
            }

            if (isPrimitive(arg)) {
                TypeWrapper wArg = TypeWrapper.forPrimitiveType(arg);

                if (isPrimitive(target)) {
                    // Both primitives: widening
                    widen(wArg, TypeWrapper.forPrimitiveType(target));
                } else {
                    // Primitive argument to reference target
                    String dTarget = target.getInternalName();
                    TypeWrapper wPrimTarget = wrapperOrNullFromDescriptor(dTarget);

                    if (wPrimTarget != null) {
                        // The target is a boxed primitive type, widen to get there before boxing
                        widen(wArg, wPrimTarget);
                        box(wPrimTarget);
                    } else {
                        // Otherwise, box and cast
                        box(wArg);
                        cast(wrapperName(wArg), dTarget);
                    }
                }
            } else {
                String dArg = arg.getInternalName();
                String dSrc;

                if (isPrimitive(functional)) {
                    dSrc = dArg;
                } else {
                    // Cast to convert to possibly more specific type, and generate CCE for invalid arg
                    dSrc = functional.getInternalName();
                    cast(dArg, dSrc);
                }

                String dTarget = target.getInternalName();

                if (isPrimitive(target)) {
                    TypeWrapper wTarget = toWrapper(dTarget);
                    // Reference argument to primitive target
                    TypeWrapper wps = wrapperOrNullFromDescriptor(dSrc);
                    if (wps != null) {
                        if (wps.isSigned() || wps.isFloating()) {
                            // Boxed number to primitive
                            unbox(wrapperName(wps), wTarget);
                        } else {
                            // Character or Boolean
                            unbox(wrapperName(wps), wps);
                            widen(wps, wTarget);
                        }
                    } else {
                        // Source type is reference type, but not boxed type,
                        // assume it is super type of target type
                        String intermediate;

                        if (wTarget.isSigned() || wTarget.isFloating()) {
                            // Boxed number to primitive
                            intermediate = "java/lang/Number";
                        } else {
                            // Character or Boolean
                            intermediate = wrapperName(wTarget);
                        }
                        cast(dSrc, intermediate);
                        unbox(intermediate, wTarget);
                    }
                } else {
                    // Both reference types: just case to target type
                    cast(dSrc, dTarget);
                }
            }
        }

        private int invocationOpcode() throws InternalError {
            switch (implKind) {
                case H_INVOKESTATIC:
                    return INVOKESTATIC;
                case H_NEWINVOKESPECIAL:
                case H_INVOKESPECIAL:
                    return INVOKESPECIAL;
                case H_INVOKEVIRTUAL:
                    return INVOKEVIRTUAL;
                case H_INVOKEINTERFACE:
                    return INVOKEINTERFACE;
                default:
                    throw new InternalError("Unexpected invocation kind: " + implKind);
            }
        }
    }

}
