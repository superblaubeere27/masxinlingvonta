package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.Objects;

public class ReadsMemoryProperty extends InstProperty {
    public static final ReadsMemoryProperty INSTANCE = new ReadsMemoryProperty();
    private final MethodOrFieldIdentifier writtenField;

    public ReadsMemoryProperty(MethodOrFieldIdentifier writtenField) {
        this.writtenField = Objects.requireNonNull(writtenField);
    }

    private ReadsMemoryProperty() {
        this.writtenField = null;
    }

    public MethodOrFieldIdentifier getWrittenField() {
        return writtenField;
    }

    @Override
    public boolean conflictsWith(InstProperty other) {
        return other instanceof WritesMemoryProperty && (this.writtenField == null || ((WritesMemoryProperty) other).getWrittenField() == null || this.writtenField.equals(((WritesMemoryProperty) other).getWrittenField()));
    }

    @Override
    public boolean changesState() {
        return false;
    }
}
