package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import java.util.List;
import java.util.Objects;

/**
 * Please don't repeat instruction marked with this as they allocate memory
 */
public class ThrowsProperty extends InstProperty {
    public static final ThrowsProperty INSTANCE = new ThrowsProperty();
    private final List<String> thrownExceptions;

    public ThrowsProperty(List<String> thrownExceptions) {
        this.thrownExceptions = Objects.requireNonNull(thrownExceptions);
    }

    private ThrowsProperty() {
        this.thrownExceptions = null;
    }

    public List<String> getThrownExceptions() {
        return thrownExceptions;
    }

    @Override
    public boolean conflictsWith(InstProperty other) {
        return other instanceof ReadsExceptionStateProperty;
    }

    @Override
    public boolean changesState() {
        return true;
    }
}
