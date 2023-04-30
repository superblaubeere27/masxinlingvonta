package net.superblaubeere27.masxinlingvaj.utils;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ASMUtils {
    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);

    public static String toString(MethodNode insnNode) {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static String toString(AbstractInsnNode insnNode) {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static String toString(InsnList insnNode) {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

}
