package net.superblaubeere27.masxinlingvaj.utils;

import javax.annotation.Nullable;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ASMUtils {
    /**
     * Get the main method in provided class node, if it's not exist, null will be returned.
     *
     * @param classNode the class node to be checked.
     * @return Returns null or main method node.
     */
    @Nullable
    public static MethodNode getMainMethod(ClassNode classNode) {
        return classNode.methods.stream().filter(ASMUtils::isMainMethod).findAny().orElse(null);
    }

    public static boolean isMainMethod(MethodNode methodNode) {
        return methodNode.name.equals("main") &&
               methodNode.desc.equals("([Ljava/lang/String;)V");
    }
}
