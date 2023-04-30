package net.superblaubeere27.masxinlingvaj.compiler.newAST;

import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.Objects;

public class ExceptionHandler {
    private final String type;
    private final BasicBlock handlerBlock;

    public ExceptionHandler(TryCatchBlockNode type, BasicBlock handlerBlock) {
        this.type = type.type;
        this.handlerBlock = Objects.requireNonNull(handlerBlock);
    }

    public String getType() {
        return type;
    }

    public BasicBlock getHandlerBlock() {
        return handlerBlock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExceptionHandler that = (ExceptionHandler) o;
        return Objects.equals(type, that.type) && Objects.equals(handlerBlock, that.handlerBlock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, handlerBlock);
    }
}
