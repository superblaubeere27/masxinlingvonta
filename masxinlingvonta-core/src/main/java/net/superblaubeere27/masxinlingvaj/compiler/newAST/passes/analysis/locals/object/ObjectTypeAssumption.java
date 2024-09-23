package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectType;

import java.util.Objects;

/**
 * Contains single information that was collected about an object. For example:
 * <p>
 * The variable A...
 * <ul>
 *     <li>...is/is not ({@code inverted})</li>
 *     <li>...an instance of/is exactly ({@code relation})</li>
 *     <li>...the class XY ({@code type})</li>
 * </ul>
 */
public class ObjectTypeAssumption extends Assumption {
    private final ObjectTypeRelation relation;
    private final boolean isInverted;
    private final ObjectType type;

    public ObjectTypeAssumption(ObjectTypeRelation relation, boolean isInverted, ObjectType type) {
        this.relation = relation;
        this.isInverted = isInverted;
        this.type = type;
    }

    public static Assumption assumeInstanceOf(ObjectType objectType) {
        return new ObjectTypeAssumption(ObjectTypeRelation.IS_INSTANCE_OF, false, objectType);
    }

    public static Assumption assumeClassIsExactly(ObjectType objectType) {
        return new ObjectTypeAssumption(ObjectTypeRelation.IS_EXACTLY, false, objectType);
    }

    public ObjectTypeRelation getRelation() {
        return relation;
    }

    public boolean isInverted() {
        return isInverted;
    }

    public ObjectType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectTypeAssumption that = (ObjectTypeAssumption) o;
        return isInverted == that.isInverted && relation == that.relation && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relation, isInverted, type);
    }

    @Override
    public String toString() {
        var innerText = (this.relation == ObjectTypeRelation.IS_INSTANCE_OF ? "instanceof " : "is-exactly ") + this.type;

        return this.isInverted ? ("NOT " + innerText) : innerText;
    }

    public enum ObjectTypeRelation {
        IS_INSTANCE_OF,
        IS_EXACTLY
    }

}
