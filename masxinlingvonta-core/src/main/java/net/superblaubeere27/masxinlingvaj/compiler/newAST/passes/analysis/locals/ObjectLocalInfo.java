package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.PrimitiveAssumptionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ObjectLocalInfo extends Assumption {
    private static final ObjectLocalInfo IDENTITY = new ObjectLocalInfo(PrimitiveAssumptionState.assumeUnknown(), NoAssumption.INSTANCE, ObjectTypeAssumptionState.assumeUnknown());

    private final PrimitiveAssumptionState<Boolean> isNull;
    private final Assumption specialObjectAssumption;
    private final ObjectTypeAssumptionState objectType;

    private ObjectLocalInfo(PrimitiveAssumptionState<Boolean> isNull, Assumption specialObjectAssumption, ObjectTypeAssumptionState objectType) {
        this.isNull = isNull;
        this.specialObjectAssumption = specialObjectAssumption;
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
        return new ObjectLocalInfo(newAssumption, specialObjectAssumption, objectType);
    }

    public ObjectLocalInfo assumeIsNull(boolean newAssumption) {
        return assumeIsNull(PrimitiveAssumptionState.assume(newAssumption));
    }

    public ObjectLocalInfo assumeObjectType(ObjectTypeAssumptionState.ObjectTypeInfo state) {
        return new ObjectLocalInfo(this.isNull, specialObjectAssumption, this.objectType.assume(state));
    }

//    @Override
//    public Assumption merge(Assumption other) {
//        if (other == null)
//            return IDENTITY;
//
//        if (!(other instanceof ObjectLocalInfo)) {
//            return super.merge(other);
//        }
//
//        var isNullMerged = this.isNull.merge(((ObjectLocalInfo) other).isNull);
//
//        var unknownThis = this.isNull.isUnknown();
//        var unknownOther = ((ObjectLocalInfo) other).isNull.isUnknown();
//
//        var specialObjectMerged = this.specialObjectAssumption.merge(((ObjectLocalInfo) other).specialObjectAssumption);
//
//        if (!unknownThis && this.isNull.getAssumedValue())
//            return new ObjectLocalInfo(isNullMerged, specialObjectMerged, ((ObjectLocalInfo) other).objectType);
//
//        if (!unknownOther && ((ObjectLocalInfo) other).isNull.getAssumedValue())
//            return new ObjectLocalInfo(isNullMerged, specialObjectMerged, this.objectType);
//
//        var postMerge = objectType.merge(((ObjectLocalInfo) other).objectType);
//
//        return new ObjectLocalInfo(isNullMerged, specialObjectMerged, postMerge);
//    }


    @Override
    public Optional<Assumption> remapAssumptionForAnd(Assumption other) {
        if (!(other instanceof ObjectLocalInfo objectLocalInfo))
            return Optional.empty();

        // revoke null assumption state if other assumption also has an assumption
        var newIsNull = objectLocalInfo.isNull.isUnknown() ? this.isNull : PrimitiveAssumptionState.<Boolean>assumeUnknown();

        var newTypeAssumptions = new ArrayList<>();

        Outer:
        for (ObjectTypeAssumptionState.ObjectTypeInfo info : this.objectType.getKnownInfos()) {
            for (ObjectTypeAssumptionState.ObjectTypeInfo otherInfo : objectLocalInfo.objectType.getKnownInfos()) {
                // If we know the type, we know the type. We don't need any more hints.
                if (otherInfo.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_EXACTLY) {
                    newTypeAssumptions.clear();

                    break Outer;
                }

                // If the outer assumption already contained this assumption, this assumption is useless.
                if (otherInfo.relation() == info.relation()) {
                    continue Outer;
                }
            }

            // All checks passed, this assumption is still relevant
            newTypeAssumptions.add(info);
        }

        if (newIsNull.isUnknown() && newTypeAssumptions.isEmpty())
            return Optional.of(NoAssumption.INSTANCE);

        if (this.objectType.getKnownInfos().length == newTypeAssumptions.size() && newIsNull.equals(this.isNull)) {
            return Optional.empty();
        }

        return Optional.of(new ObjectLocalInfo(newIsNull, NoAssumption.INSTANCE, new ObjectTypeAssumptionState(newTypeAssumptions.toArray(ObjectTypeAssumptionState.ObjectTypeInfo[]::new))));
    }

    @Override
    public boolean equivalent(Assumption other) {
        return other instanceof ObjectLocalInfo && this.isNull.equals(((ObjectLocalInfo) other).isNull) && this.objectType.equals(((ObjectLocalInfo) other).objectType);
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("(");

        if (!this.isNull.isUnknown())
            builder.append(this.isNull.getAssumedValue() ? "null" : "not-null").append(" ");

        builder.append("[").append(Arrays.stream(this.objectType.getKnownInfos()).map(Record::toString).collect(Collectors.joining(", "))).append("]");

        builder.append(")");

        return builder.toString();
    }
}
