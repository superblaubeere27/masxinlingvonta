package net.superblaubeere27.masxinlingvaj.preprocessor.codegen;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class LambdaCodegenTest {

    @Test
    void getEffectiveType() {
        assertEquals("()V", LambdaCodegen.getEffectiveType(new Handle(Opcodes.H_INVOKESTATIC, "a/b/c", "def", "()V", false)));
        assertEquals("(La/b/c;)V", LambdaCodegen.getEffectiveType(new Handle(Opcodes.H_INVOKESPECIAL, "a/b/c", "def", "()V", false)));
        assertEquals("(La/b/c;Ljava/lang/String;)V", LambdaCodegen.getEffectiveType(new Handle(Opcodes.H_INVOKESPECIAL, "a/b/c", "def", "(Ljava/lang/String;)V", false)));
    }
}