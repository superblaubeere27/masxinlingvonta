package net.superblaubeere27.masxinlingvaj.utils;

import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.Type;

public class TypeUtils {

    public static Type[] getEffectiveArgumentTypes(CompilerMethod compilerMethod) {
        return getEffectiveArgumentTypes(compilerMethod.getNode().desc, compilerMethod.isStatic());
    }

    public static Type[] getEffectiveArgumentTypes(String desc, boolean isStatic) {
        var argTypes = Type.getArgumentTypes(desc);

        if (isStatic)
            return argTypes;

        var types = new Type[argTypes.length + 1];

        types[0] = OpcodeUtils.OBJECT_TYPE; // Instance Object

        System.arraycopy(argTypes, 0, types, 1, argTypes.length);

        return types;
    }

}
