package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class AssumptionAnalyzer {

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

    public static <T> boolean extractPossibleValues0(Assumption assumption, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> possibleValues) {
        if (assumption instanceof LinkedAssumptions linkedAssumption) {
            // It might seem paradox that AND is handled by or assumption and vice-versa.
            // But since the AND-link means that all assumptions apply at the same time.
            return switch (linkedAssumption.getLinkType()) {
                case AND -> orValues(linkedAssumption.getAssumptionList(), leafPredicate, possibleValues);
                case OR -> andValues(linkedAssumption.getAssumptionList(), leafPredicate, possibleValues);
            };
        }

        var leafValue = leafPredicate.apply(assumption);

        if (leafValue.isEmpty()) {
            return false;
        }

        possibleValues.add(leafValue.get());

        return true;
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
        Optional<T> currentValue = Optional.empty();

        for (Assumption assumption : assumptionList) {
            var extractedValue = extractValue(assumption, leafPredicate);

            if (currentValue.isEmpty()) {
                currentValue = extractedValue;

                continue;
            }

            // This assumption yields either no or a different value, so no assumption can be made of the value
            if (extractedValue.isEmpty() || !extractedValue.get().equals(currentValue.get()))
                return Optional.empty();
        }

        return currentValue;
    }

    /**
     * If all leaf predicates yield the same value, this function will return this value.
     */
    private static <T> boolean andValues(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> values) {
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

    private static <T> boolean orValues(List<Assumption> assumptionList, Function<Assumption, Optional<T>> leafPredicate, HashSet<T> values) {
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
