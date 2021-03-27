package net.superblaubeere27.masxinlingvaj.compiler.code;

import org.objectweb.asm.tree.TryCatchBlockNode;

public class ExceptionHandler {
    private final String type;
    private final Block handlerBlock;

    public ExceptionHandler(TryCatchBlockNode type, Block handlerBlock) {
        this.type = type.type;
        this.handlerBlock = handlerBlock;
    }

    public String getType() {
        return type;
    }

    public Block getHandlerBlock() {
        return handlerBlock;
    }
}
