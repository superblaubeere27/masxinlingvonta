package net.superblaubeere27.masxinlingvaj.compiler.newAST.stmt;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.InstProperty;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class StmtMetadata {
    private final Set<InstProperty> properties;

    public StmtMetadata(Collection<InstProperty> positionDependence) {
        this.properties = new HashSet<>(positionDependence);
    }

    public Set<InstProperty> getProperties() {
        return properties;
    }
}
