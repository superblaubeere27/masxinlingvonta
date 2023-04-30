package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.PrimitiveAssumptionState;

public final class ObjectLocalInfo extends LocalInfo {
    private static final ObjectLocalInfo IDENTITY = new ObjectLocalInfo(PrimitiveAssumptionState.assumeUnknown(), ObjectTypeAssumptionState.assumeUnknown());

    private final PrimitiveAssumptionState<Boolean> isNull;
    private final ObjectTypeAssumptionState objectType;

    private ObjectLocalInfo(PrimitiveAssumptionState<Boolean> isNull, ObjectTypeAssumptionState objectType) {
        this.isNull = isNull;
        this.objectType = objectType;
    }

    public static ObjectLocalInfo create() {
        return IDENTITY;
    }

    public PrimitiveAssumptionState<Boolean> getIsNullAssumption() {
        return isNull;
    }

    public ObjectTypeAssumptionState getObjectTypeAssumption() {
        return objectType;
    }

    public ObjectLocalInfo assumeIsNull(PrimitiveAssumptionState<Boolean> newAssumption) {
        return new ObjectLocalInfo(newAssumption, objectType);
    }

    public ObjectLocalInfo assumeIsNull(boolean newAssumption) {
        return assumeIsNull(PrimitiveAssumptionState.assume(newAssumption));
    }

    public ObjectLocalInfo assumeObjectType(ObjectTypeAssumptionState.ObjectTypeInfo state) {
        return new ObjectLocalInfo(this.isNull, this.objectType.assume(state));
    }

    @Override
    public ObjectLocalInfo merge(LocalInfo other) {
        if (other == null)
            return IDENTITY;

        if (!(other instanceof ObjectLocalInfo)) {
            throw new IllegalArgumentException();
        }

        var isNullMerged = this.isNull.merge(((ObjectLocalInfo) other).isNull);

        var unknownThis = this.isNull.isUnknown();
        var unknownOther = ((ObjectLocalInfo) other).isNull.isUnknown();

        if (!unknownThis && this.isNull.getAssumedValue())
            return new ObjectLocalInfo(isNullMerged, ((ObjectLocalInfo) other).objectType);

        if (!unknownOther && ((ObjectLocalInfo) other).isNull.getAssumedValue())
            return new ObjectLocalInfo(isNullMerged, this.objectType);

        var postMerge = objectType.merge(((ObjectLocalInfo) other).objectType);

        return new ObjectLocalInfo(isNullMerged, postMerge);
    }

    @Override
    public boolean equivalent(LocalInfo other) {
        return other instanceof ObjectLocalInfo && this.isNull.equals(((ObjectLocalInfo) other).isNull) && this.objectType.equals(((ObjectLocalInfo) other).objectType);
    }

    @Override
    public String toString() {
        return "ObjectLocalInfo{" +
                "isNull=" + isNull +
                ", objectType=" + objectType +
                '}';
    }
}
