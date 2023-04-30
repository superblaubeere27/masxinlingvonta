package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ExprProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExprMetadata {
    private final ExprClass exprClass;
    private final Set<ExprProperty> properties;

    public ExprMetadata(ExprClass exprClass, Set<ExprProperty> positionDependence) {
        this.exprClass = exprClass;
        this.properties = positionDependence;
    }

    public ExprMetadata(ExprClass exprClass, Collection<ExprProperty> positionDependence) {
        this(exprClass, new HashSet<>(positionDependence));
    }

    public ExprClass getExprClass() {
        return exprClass;
    }

    public Set<ExprProperty> getProperties() {
        return properties;
    }

    public enum ExprClass {
        FIRST,
        SECOND
    }
}
