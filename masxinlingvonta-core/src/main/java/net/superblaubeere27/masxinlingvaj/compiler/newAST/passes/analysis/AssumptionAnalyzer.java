package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class AssumptionAnalyzer {

    /**
     * Remaps the given assumption by function fun. i.e. y=(Box(x >= 5) AND NOT-NULL) AND y=Box(x < 1000), this function
     * could extract the box and turn the assumption into: x >= 5 AND x < 1000.
     *
     * @param fun Remapping function. May return null or NO_ASSUMPTION if the assumption cannot be remapped.
     */
    public static Assumption remapAssumption(Assumption assumption, Function<Assumption, Assumption> fun) {
        if (assumption instanceof LinkedAssumptions linkedAssumption) {
            var assumptions = linkedAssumption.getAssumptionList().stream().map(fun).filter(Objects::nonNull).toArray(Assumption[]::new);

            return switch (linkedAssumption.getLinkType()) {
                case AND -> LinkedAssumptions.and(assumptions);
                case OR -> LinkedAssumptions.or(assumptions);
            };
        }

        var remappedAssumption = fun.apply(assumption);

        return remappedAssumption != null ? remappedAssumption : Assumption.NoAssumption.INSTANCE;
    }

    /**
     * Tells which values an assumption could have, i.e.
     * <ul>
     *     <li>y=5 OR ((y=9 OR y=4) AND x=true), here the function would return Some([5, 9, 4]) as values for y</li>
     *     <li>y=5 OR ((y=9 OR y=???) AND x=true), here the function would return None since the y could be anything</li>
     * </ul>
     */
    public static <T> Optional<HashSet<T>> extractPossibleValues(Assumption assumption, Function<Assumption, Optional<T>> leafPredicate) {
        HashSet<T> values = new HashSet<>();

        // Check if discrete values have been found. If not, we cannot give an answer to which values this assumption
        // might have
        if (extractPossibleValues0(assumption, leafPredicate, values)) {
            return Optional.of(values);
        } else {
            return Optional.empty();
        }
    }

    private static <T> boolean extractPossibleValues0(Assumption assumption, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> possibleValues) {
        if (assumption instanceof LinkedAssumptions linkedAssumption) {
            // It might seem paradox that AND is handled by or assumption and vice-versa.
            // But since the AND-link means that all assumptions apply at the same time.
            return switch (linkedAssumption.getLinkType()) {
                case AND -> orPossibleValues(linkedAssumption.getAssumptionList(), leafPredicate, possibleValues);
                case OR -> andPossibleValues(linkedAssumption.getAssumptionList(), leafPredicate, possibleValues);
            };
        }

        var leafValue = leafPredicate.apply(assumption);

        if (leafValue.isEmpty()) {
            return false;
        }

        possibleValues.add(leafValue.get());

        return true;
    }

    /**
     * Tells which values an assumption <b>actually</b> has. This is different from {@link #extractPossibleValues(Assumption, Function)}
     * since this function will return the actual values of the assumption, i.e.
     * <ul>
     *     <li>y in [5, 2, 4] OR ((y in [1, 3] OR y=9) AND x=true), here the function would return None as values for y</li>
     *     <li>y in [5, 2, 4] OR (y in [2, 4] AND x=true), here the function would return Some([2, 4]) as values for y</li>
     * </ul>
     */
    public static <T> HashSet<T> extractActualValues(Assumption assumption, Function<Assumption, Set<T>> leafPredicate) {
        if (assumption instanceof LinkedAssumptions linkedAssumptions) {
            return switch (linkedAssumptions.getLinkType()) {
                case AND -> orValues(linkedAssumptions.getAssumptionList(), leafPredicate);
                case OR -> andValues(linkedAssumptions.getAssumptionList(), leafPredicate);
            };
        }

        return new HashSet<>(leafPredicate.apply(assumption));
    }

    private static <T> HashSet<T> andValues(List<Assumption> assumptionList, Function<Assumption, Set<T>> leafPredicate) {
        HashSet<T> values = null;

        for (Assumption assumption : assumptionList) {
            var extractedValues = extractActualValues(assumption, leafPredicate);

            if (values != null) {
                values.retainAll(extractedValues);
            } else {
                values = extractedValues;
            }
        }

        return values == null ? new HashSet<>() : values;
    }

    private static <T> HashSet<T> orValues(List<Assumption> assumptionList, Function<Assumption, Set<T>> leafPredicate) {
        HashSet<T> values = null;

        for (Assumption assumption : assumptionList) {
            var extractedValues = extractActualValues(assumption, leafPredicate);

            if (values != null) {
                values.addAll(extractedValues);
            } else {
                values = extractedValues;
            }
        }

        return values == null ? new HashSet<>() : values;
    }

    public static <T> Optional<T> extractValue(Assumption assumption, Function<Assumption, Optional<T>> leafPredicate) {
        if (assumption instanceof LinkedAssumptions linkedAssumption) {
            // It might seem paradox that AND is handled by or assumption and vice-versa.
            // But since the AND-link means that all assumptions apply at the same time.
            return switch (linkedAssumption.getLinkType()) {
                case AND -> orValue(linkedAssumption.getAssumptionList(), leafPredicate);
                case OR -> andValue(linkedAssumption.getAssumptionList(), leafPredicate);
            };
        }

        return leafPredicate.apply(assumption);
    }

    public static boolean canBeAssumed(Assumption assumption, Predicate<Assumption> leafPredicate) {
        if (assumption instanceof LinkedAssumptions linkedAssumption) {
            // It might seem paradox that AND is handled by or assumption and vice-versa.
            // But since the AND-link means that all assumptions apply at the same time.
            return switch (linkedAssumption.getLinkType()) {
                case AND -> or(linkedAssumption.getAssumptionList(), leafPredicate);
                case OR -> and(linkedAssumption.getAssumptionList(), leafPredicate);
            };
        }

        return leafPredicate.test(assumption);
    }

    /**
     * If all leaf predicates yield the same value, this function will return this value.
     */
    private static <T> Optional<T> andValue(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate) {
        boolean first = true;
        Optional<T> currentValue = Optional.empty();

        for (Assumption assumption : assumptionList) {
            var extractedValue = extractValue(assumption, leafPredicate);

            if (extractedValue.isEmpty())
                return Optional.empty();

            if (first) {
                currentValue = extractedValue;
                first = false;

                continue;
            }

            // This assumption yields either no or a different value, so no assumption can be made of the value
            if (!extractedValue.get().equals(currentValue.get()))
                return Optional.empty();
        }

        return currentValue;
    }

    /**
     * If all leaf predicates yield the same value, this function will return this value.
     */
    private static <T> boolean andPossibleValues(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> values) {
        if (assumptionList.isEmpty())
            return false;

        for (Assumption assumption : assumptionList) {
            var extractedValue = extractValue(assumption, leafPredicate);

            // When there is no discrete value for this assumption, there is no discrete value for this OR
            if (extractedValue.isEmpty())
                return false;

            values.add(extractedValue.get());
        }

        return true;
    }

    private static <T> Optional<T> orValue(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate) {
        return assumptionList.stream().map(x -> extractValue(x, leafPredicate)).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

    private static <T> boolean orPossibleValues(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> values) {
        // For a (valid) AND assumption it is enough to find exactly one child that yields discrete values.
        // This is because every child applies at the same time and different child cannot contradict.
        // i.e. (y=5 OR x=false) AND (y=5 OR y=4) -> [5,4]
        for (Assumption assumption : assumptionList) {
            var possibleValues = extractPossibleValues(assumption, leafPredicate);

            if (possibleValues.isEmpty()) {
                continue;
            }

            values.addAll(possibleValues.get());

            return true;
        }

        return false;
    }

    private static boolean or(List<Assumption> assumptionList, Predicate<Assumption> leafPredicate) {
        return assumptionList.stream().anyMatch(assumption -> canBeAssumed(assumption, leafPredicate));
    }

    private static boolean and(List<Assumption> assumptionList, Predicate<Assumption> leafPredicate) {
        return !assumptionList.isEmpty() && assumptionList.stream().allMatch(assumption -> canBeAssumed(assumption, leafPredicate));
    }

}
