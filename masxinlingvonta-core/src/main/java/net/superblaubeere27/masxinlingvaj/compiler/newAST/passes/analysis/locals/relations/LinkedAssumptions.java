package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LinkedAssumptions extends Assumption {
    private final LinkType linkType;
    private final List<Assumption> assumptionList;

    private LinkedAssumptions(LinkType linkType, List<Assumption> assumptionList) {
        this.linkType = linkType;
        this.assumptionList = assumptionList;
    }

    /**
     * Or linkage between assumptions (either one of the assumption applies)
     */
    public static Assumption or(@Nonnull Assumption... assumptions) {
        var mergedAssumptions = new ArrayList<Assumption>(assumptions.length);

        for (Assumption assumption : assumptions) {
            if (assumption == null)
                throw new IllegalArgumentException("Assumptions cannot be null!");

            // If the assumption is not a linked OR assumption, we just pass it through
            if (!(assumption instanceof LinkedAssumptions linkedAssumptions) || linkedAssumptions.linkType != LinkType.OR) {
                // If an assumption is a NO_ASSUMPTION, all other assumptions cannot be applied for certain, so we can
                // just discard this or linkage.
                if (assumption == NoAssumption.INSTANCE) {
                    return NoAssumption.INSTANCE;
                }

                addIfNew(mergedAssumptions, assumption);

                continue;
            }

            // If it is an OR linkage, unwind the assumptions
            for (Assumption nestedAssumption : linkedAssumptions.assumptionList) {
                if (nestedAssumption == NoAssumption.INSTANCE) {
                    return NoAssumption.INSTANCE;
                }

                addIfNew(mergedAssumptions, nestedAssumption);
            }
        }

        if (mergedAssumptions.size() == 1) {
            return mergedAssumptions.get(0);
        }

        return new LinkedAssumptions(LinkType.OR, mergedAssumptions);
    }

    /**
     * And linkage between assumptions (all assumptions apply at the same time)
     */
    public static Assumption and(Assumption... assumptions) {
        var resultAssumptions = new ArrayList<Assumption>(assumptions.length);

        for (Assumption assumption : assumptions) {
            if (assumption == null)
                throw new IllegalArgumentException("Assumptions cannot be null!");

            addAssumptionToAndLink(resultAssumptions, assumption);
        }

        // Last step: remapping assumptions (-> Remove assumptions that have become obsolete)
        remapAssumptionListForAnd(resultAssumptions);

        if (resultAssumptions.size() == 1) {
            return resultAssumptions.get(0);
        }

        return new LinkedAssumptions(LinkType.AND, resultAssumptions);
    }

    /**
     * Compares every assumption in the list to every other assumption in that list and remaps them
     */
    private static void remapAssumptionListForAnd(ArrayList<Assumption> resultAssumptions) {
        for (int i = 0; i < resultAssumptions.size(); i++) {
            var assumption = resultAssumptions.get(i);

            for (int j = 0; j < resultAssumptions.size(); j++) {
                if (j == i)
                    continue;

                var otherAssumption = resultAssumptions.get(j);

                var remappedAssumption = assumption.remapAssumptionForAnd(otherAssumption);

                // If the assumption has updated due to the other assumption being present, replace it
                if (remappedAssumption.isPresent()) {
                    assumption = remappedAssumption.get();

                    // If the assumption has become a no-assumption, there will be no more updates to it.
                    if (assumption == NoAssumption.INSTANCE) {
                        break;
                    }
                }
            }

            resultAssumptions.set(i, assumption);
        }

        // Remove all assumptions that have turned into no-assumptions
        resultAssumptions.removeIf(x -> x == NoAssumption.INSTANCE);
    }

    private static void addIfNew(ArrayList<Assumption> assumptions, Assumption newAssumption) {
        // Is an equivalent assumption already in the list? If it is not, add it
        if (assumptions.stream().anyMatch(x -> x.equivalent(newAssumption)))
            return;

        assumptions.add(newAssumption);
    }

    private static void addAssumptionToAndLink(ArrayList<Assumption> resultAssumptions, Assumption assumption) {
        // Is an equivalent assumption already in the list? If it is not, add it
        if (resultAssumptions.stream().anyMatch(x -> x.equivalent(assumption)))
            return;

        if (assumption == NoAssumption.INSTANCE)
            return;

        if (assumption instanceof LinkedAssumptions linkedAssumption && linkedAssumption.getLinkType() == LinkType.AND) {
            for (Assumption inner : linkedAssumption.getAssumptionList()) {
                addAssumptionToAndLink(resultAssumptions, inner);
            }
        } else {
            resultAssumptions.add(assumption);
        }
    }

    public LinkType getLinkType() {
        return linkType;
    }

    @Override
    public Assumption merge(Assumption other) {
        return or(this, other);
    }

    @Override
    public boolean equivalent(Assumption other) {
        if (!(other instanceof LinkedAssumptions otherLinkedAssumptions))
            return false;

        if (this.linkType != otherLinkedAssumptions.linkType)
            return false;

        if (this.assumptionList.size() != otherLinkedAssumptions.assumptionList.size())
            return false;

        // Is there for every assumption in this object an equivalent assumption in the other
        O:
        for (Assumption assumption : this.assumptionList) {
            for (Assumption otherAssumption : otherLinkedAssumptions.assumptionList) {
                if (assumption.equivalent(otherAssumption))
                    continue O;
            }

            return false;
        }

        // Link type is equivalent, both have same amount of assumptions and contain similar stuff
        return true;
    }

    @Override
    public Optional<Assumption> remapAssumptionForAnd(Assumption other) {
        ArrayList<Assumption> output = new ArrayList<>();

        boolean changed = false;

        for (Assumption assumption : this.assumptionList) {
            var remappedOptional = assumption.remapAssumptionForAnd(other);

            if (remappedOptional.isPresent()) {
                changed = true;

                if (remappedOptional.get() == NoAssumption.INSTANCE) {
                    switch (this.linkType) {
                        case AND -> {
                            continue;
                        }
                        case OR -> {
                            return Optional.of(NoAssumption.INSTANCE);
                        }
                    }
                }

                addIfNew(output, remappedOptional.get());
            } else {
                addIfNew(output, assumption);
            }
        }

        if (output.size() == 0) {
            return Optional.of(NoAssumption.INSTANCE);
        } else if (output.size() == 1) {
            return Optional.of(output.get(0));
        }

        return changed ? Optional.of(new LinkedAssumptions(this.linkType, output)) : Optional.empty();
    }

    @Override
    public String toString() {
        return "(LINK:\n\t- " + this.linkType + " " + this.assumptionList.stream().map(assumption -> assumption.toString().replace("\n", "\n\t")).collect(Collectors.joining("\n\t- " + this.linkType.toString() + " ")) + ")";
    }


    public List<Assumption> getAssumptionList() {
        return Collections.unmodifiableList(assumptionList);
    }

    public enum LinkType {
        AND,
        OR
    }
}
