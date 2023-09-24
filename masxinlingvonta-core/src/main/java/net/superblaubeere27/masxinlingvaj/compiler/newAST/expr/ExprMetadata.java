package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.InstProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ExprMetadata {
    private final ExprClass exprClass;
    private final Set<InstProperty> properties;

    public ExprMetadata(ExprClass exprClass, Set<InstProperty> positionDependence) {
        this.exprClass = exprClass;
        this.properties = positionDependence;
    }

    public ExprMetadata(ExprClass exprClass, Collection<InstProperty> positionDependence) {
        this(exprClass, new HashSet<>(positionDependence));
    }

    public ExprClass getExprClass() {
        return exprClass;
    }

    public Set<InstProperty> getProperties() {
        return properties;
    }

    public enum ExprClass {
        FIRST,
        SECOND
    }
}
