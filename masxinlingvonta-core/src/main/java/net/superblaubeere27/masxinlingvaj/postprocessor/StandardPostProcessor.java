package net.superblaubeere27.masxinlingvaj.postprocessor;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;

public class StandardPostProcessor extends AbstractPostprocessor {

    @Override
    public void postProcess(MLVCompiler compiler) {
        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
            for (CompilerMethod method : aClass.getMethods()) {
                if (!method.wasOutsourced() || method.getParent().isLibrary()) {
                    continue;
                }

                if (method.getNode().name.startsWith("<"))
                    throw new IllegalStateException("Tried to outsource <init>/<clinit> method");

                var node = method.getNode();

                node.instructions = new InsnList();
                node.access |= Opcodes.ACC_NATIVE;
            }
        }
    }

}
