package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import java.util.List;
import java.util.Objects;

public class ReadsExceptionStateProperty extends ExprProperty {
    public static final ReadsExceptionStateProperty INSTANCE = new ReadsExceptionStateProperty();

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return other instanceof ThrowsProperty;
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
