package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import java.util.*;
import java.util.stream.Collectors;

public final class ObjectTypeAssumptionState {
    private static final ObjectTypeAssumptionState IDENTITY = new ObjectTypeAssumptionState(new ObjectTypeInfo[0]);

    private final ObjectTypeInfo[] knownInfos;

    private ObjectTypeAssumptionState(ObjectTypeInfo[] knownInfos) {
        this.knownInfos = knownInfos;
    }

    public static ObjectTypeAssumptionState assumeUnknown() {
        return IDENTITY;
    }

    public ObjectTypeAssumptionState assume(ObjectTypeInfo info) {
        // If there is no other info, just return the old info
        if (this.knownInfos.length == 0) {
            return new ObjectTypeAssumptionState(new ObjectTypeInfo[]{info});
        }
        // Don't add an info that is already there
        if (Arrays.asList(this.knownInfos).contains(info))
            return this;

        // Construct a new array that contains all assumptions already known and the new one
        var newAssumptions = new ObjectTypeInfo[this.knownInfos.length + 1];

        System.arraycopy(this.knownInfos, 0, newAssumptions, 0, this.knownInfos.length);

        newAssumptions[newAssumptions.length - 1] = info;

        var exactlyKnownTypeInfos = Arrays.stream(newAssumptions).filter(x -> x.relation == ObjectTypeRelation.IS_EXACTLY && !x.inverted).toArray(ObjectTypeInfo[]::new);

        if (exactlyKnownTypeInfos.length == 1) {
            // An object can only be exactly one class at once, so we can just forget about the other types
            return new ObjectTypeAssumptionState(exactlyKnownTypeInfos);
        } else if (exactlyKnownTypeInfos.length > 1) {
            throw new IllegalStateException("A type is assumed to be more than one type at a time (???)");
        }

        // If a type is known to be a specific type, it cannot be another type (obviously) so we can just throw the other infos away
        return new ObjectTypeAssumptionState(newAssumptions);
    }

    public ObjectTypeAssumptionState merge(ObjectTypeAssumptionState other) {
        // Shortcuts for empty assumptions
        if (this.knownInfos.length == 0)
            return this;
        else if (other.knownInfos.length == 0)
            return other;

        // Tracks if an object time was seen twice. absent = not seen yet, false = seen once, true = seen twice
        var objectTypeInfosCounter = new HashMap<ObjectTypeInfo, Boolean>();

        for (ObjectTypeInfo knownInfo : this.knownInfos) {
            objectTypeInfosCounter.put(knownInfo, false);
        }

        // When we encounter an info twice, allow it to persist
        for (ObjectTypeInfo knownInfo : other.knownInfos) {
            objectTypeInfosCounter.computeIfPresent(knownInfo, (a, b) -> true);
        }

        var result = new ArrayList<ObjectTypeInfo>();

        for (Map.Entry<ObjectTypeInfo, Boolean> objectTypeInfoBooleanEntry : objectTypeInfosCounter.entrySet()) {
            if (objectTypeInfoBooleanEntry.getValue()) {
                result.add(objectTypeInfoBooleanEntry.getKey());
            }
        }

        return new ObjectTypeAssumptionState(result.toArray(new ObjectTypeInfo[0]));
    }

    public ObjectTypeInfo[] getKnownInfos() {
        return knownInfos;
    }

    /**
     * Is there an IS_EXACTLY info? If there is, return it.
     */
    public Optional<ObjectType> getExactTypeIfKnown() {
        return Arrays.stream(this.knownInfos).filter(x -> x.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_EXACTLY && !x.inverted()).findAny().map(x -> x.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectTypeAssumptionState that = (ObjectTypeAssumptionState) o;

        O:
        for (ObjectTypeInfo knownInfo : that.knownInfos) {
            for (ObjectTypeInfo info : this.knownInfos) {
                if (knownInfo.equals(info))
                    continue O;
            }

            return false;
        }

        O:
        for (ObjectTypeInfo knownInfo : this.knownInfos) {
            for (ObjectTypeInfo info : that.knownInfos) {
                if (knownInfo.equals(info))
                    continue O;
            }

            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(knownInfos);
    }

    @Override
    public String toString() {
        return "ObjectTypeAssumptionState{" +
                "knownInfos=" + Arrays.toString(knownInfos) +
                '}';
    }

    public enum ObjectTypeRelation {
        IS_INSTANCE_OF,
        IS_EXACTLY
    }

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
    public record ObjectTypeInfo(ObjectTypeRelation relation, boolean inverted, ObjectType type) {
        static ObjectTypeInfo isExactly(ObjectType type) {
            return new ObjectTypeInfo(ObjectTypeRelation.IS_EXACTLY, false, type);
        }

        static ObjectTypeInfo isInstanceOf(ObjectType type) {
            return new ObjectTypeInfo(ObjectTypeRelation.IS_INSTANCE_OF, false, type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ObjectTypeInfo that = (ObjectTypeInfo) o;

            if (inverted != that.inverted) return false;
            if (relation != that.relation) return false;
            return Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            int result = relation != null ? relation.hashCode() : 0;
            result = 31 * result + (inverted ? 1 : 0);
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }
    }
}
