package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import com.google.common.collect.HashMultimap;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LinkedAssumptions extends Assumption {
    private final LinkType linkType;
    private final HashMultimap<AssumptionKey, Assumption> assumptions;

    private LinkedAssumptions(LinkType linkType, HashMultimap<AssumptionKey, Assumption> assumptions) {
        this.linkType = linkType;
        this.assumptions = assumptions;
    }

    /**
     * Or linkage between assumptions (either one of the assumption applies)
     */
    public static Assumption or(@Nonnull Assumption... assumptions) {
        var mergedAssumptions = HashMultimap.<AssumptionKey, Assumption>create();

        for (Assumption assumption : assumptions) {
            if (assumption == null)
                throw new IllegalArgumentException("Assumptions cannot be null!");

            // Unwind OR linkages (a OR (b OR C) -> a OR b or C)
            if (assumption instanceof LinkedAssumptions linkedAssumptions && linkedAssumptions.linkType == LinkType.OR) {
                mergedAssumptions.putAll(linkedAssumptions.assumptions);

                continue;
            }

            mergedAssumptions.put(assumption.getKey(), assumption);
        }

        // If any of the assumptions is nothing, it means that we know exactly nothing.
        if (mergedAssumptions.containsKey(NoAssumption.INSTANCE.getKey())) {
            return NoAssumption.INSTANCE;
        }

        if (mergedAssumptions.size() == 1) {
            return mergedAssumptions.values().stream().findFirst().orElseThrow();
        }

        return new LinkedAssumptions(LinkType.OR, mergedAssumptions);
    }

    /**
     * And linkage between assumptions (all assumptions apply at the same time).
     * Returns the minimal representation of the given assumption.
     */
    public static Assumption and(Assumption... assumptions) {
        var result = HashMultimap.<AssumptionKey, Assumption>create();

        for (Assumption assumption : assumptions) {
            if (assumption instanceof LinkedAssumptions linkedAssumptions && linkedAssumptions.getLinkType() == LinkType.AND) {
                result.putAll(linkedAssumptions.assumptions);

                continue;
            }

            result.put(assumption.getKey(), assumption);
        }

        var mappedOrValues = new ArrayList<Assumption>();

        // This function shall yield the minimal representation of this assumption. Thus (a OR (b AND c)) AND c must be
        // optimized to (a OR b) AND c. Since AND linkages were already spilled (s.a.) we only need to process OR values.
        for (Iterator<Assumption> iterator = result.get(new LinkedAssumptionsKey(LinkType.OR)).iterator(); iterator.hasNext(); ) {
            Assumption assumption = iterator.next();

            var remapped = assumption.remapAssumption(childAssumption -> result.containsValue(childAssumption) ? NoAssumption.INSTANCE : null);

            if (remapped != null) {
                mappedOrValues.add(remapped);

                iterator.remove();
            }
        }

        for (Assumption mappedOrValue : mappedOrValues) {
            result.put(mappedOrValue.getKey(), mappedOrValue);
        }

        result.removeAll(NoAssumption.INSTANCE.getKey());

        if (result.isEmpty()) {
            return NoAssumption.INSTANCE;
        } else if (result.size() == 1) {
            return result.values().stream().findFirst().orElseThrow();
        }

        return new LinkedAssumptions(LinkType.AND, result);
    }

    public LinkType getLinkType() {
        return linkType;
    }

    @Override
    public String toString() {
        return "(LINK:\n\t- " + this.linkType + " " + this.assumptions.values().stream().map(assumption -> assumption.toString().replace("\n", "\n\t")).collect(Collectors.joining("\n\t- " + this.linkType.toString() + " ")) + ")";
    }

    @Override
    public Assumption remapAssumption(Function<Assumption, Assumption> remapper) {
        var target = new ArrayList<Assumption>();
        var changed = false;

        for (Assumption assumption : this.assumptions.values()) {
            var remapped = assumption.remapAssumption(remapper);

            if (remapped != null) {
                target.add(remapped);
                changed = true;
            } else {
                target.add(assumption);
            }
        }

        if (changed) {
            return switch (this.linkType) {
                case AND -> and(target.toArray(Assumption[]::new));
                case OR -> or(target.toArray(Assumption[]::new));
            };
        } else {
            return this;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedAssumptions that = (LinkedAssumptions) o;
        return linkType == that.linkType && Objects.equals(assumptions, that.assumptions);
    }

    @Override
    public AssumptionKey getKey() {
        return new LinkedAssumptionsKey(this.linkType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkType, assumptions);
    }

    public Collection<Assumption> getInnerAssumptions() {
        return Collections.unmodifiableCollection(this.assumptions.values());
    }

    public enum LinkType {
        AND,
        OR
    }

    private static class LinkedAssumptionsKey extends AssumptionKey {
        private final LinkType linkType;

        private LinkedAssumptionsKey(LinkType linkType) {
            this.linkType = linkType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LinkedAssumptionsKey that = (LinkedAssumptionsKey) o;
            return linkType == that.linkType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(LinkedAssumptionsKey.class, linkType);
        }
    }
}
