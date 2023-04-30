package net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties;

import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.Objects;

public class WritesMemoryProperty extends ExprProperty {
    public static final WritesMemoryProperty INSTANCE = new WritesMemoryProperty();
    private final MethodOrFieldIdentifier writtenField;

    public WritesMemoryProperty(MethodOrFieldIdentifier writtenField) {
        this.writtenField = Objects.requireNonNull(writtenField);
    }

    private WritesMemoryProperty() {
        this.writtenField = null;
    }

    public MethodOrFieldIdentifier getWrittenField() {
        return writtenField;
    }

    @Override
    public boolean conflictsWith(ExprProperty other) {
        return other instanceof ReadsMemoryProperty && (this.writtenField == null || ((ReadsMemoryProperty) other).getWrittenField() == null || this.writtenField.equals(((ReadsMemoryProperty) other).getWrittenField()));
    }

    @Override
    public boolean changesState() {
        return true;
    }
}
