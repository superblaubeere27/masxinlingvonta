package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions.and;
import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions.or;
import static org.junit.jupiter.api.Assertions.*;

class AssumptionAnalyzerTest {

    private static Optional<Integer> getShit(Assumption a) {
        return a instanceof TestAssumption testA ? Optional.of(testA.value) : Optional.empty();
    }

    @Test
    void extractValue() {
        var tsetAssumption = new TsetAssumption("yeet");
        var testAssumption3 = new TestAssumption(3);
        var testAssumption5 = new TestAssumption(5);

        Function<Assumption, Optional<Integer>> a = (x) -> AssumptionAnalyzer.extractValue(x, AssumptionAnalyzerTest::getShit);

        assertEquals(Optional.empty(), a.apply(Assumption.NoAssumption.INSTANCE));
        assertEquals(Optional.empty(), a.apply(tsetAssumption));
        assertEquals(Optional.of(3), a.apply(testAssumption3));
        assertEquals(Optional.empty(), a.apply(or(testAssumption5, testAssumption3)));
        assertEquals(Optional.empty(), a.apply(or(tsetAssumption, testAssumption3)));
        assertEquals(Optional.of(5), a.apply(or(testAssumption5, testAssumption5)));
        assertEquals(Optional.of(3), a.apply(and(tsetAssumption, testAssumption3)));
    }

    @Test
    void extractPossibleValues() {
        var tsetAssumption = new TsetAssumption("yeet");
        var testAssumption3 = new TestAssumption(3);
        var testAssumption5 = new TestAssumption(5);

        Function<Assumption, Optional<HashSet<Integer>>> a = (x) -> AssumptionAnalyzer.extractPossibleValues(x, AssumptionAnalyzerTest::getShit);

        assertEquals(Optional.empty(), a.apply(Assumption.NoAssumption.INSTANCE));
        assertEquals(Optional.empty(), a.apply(tsetAssumption));
        assertEquals(Optional.of(new HashSet<>(List.of(3))), a.apply(testAssumption3));
        assertEquals(Optional.of(new HashSet<>(List.of(3))), a.apply(and(tsetAssumption, testAssumption3)));
        assertEquals(Optional.of(new HashSet<>(List.of(5))), a.apply(and(testAssumption5, testAssumption5)));
        assertEquals(Optional.of(new HashSet<>(List.of(5))), a.apply(or(testAssumption5, testAssumption5)));
        assertEquals(Optional.of(new HashSet<>(List.of(3, 5))), a.apply(or(testAssumption3, testAssumption5)));
    }

    @Test
    void extractActualValues() {
        var nA = new TsetAssumption("yeet");
        var t3 = new TestAssumption(3);
        var t5 = new TestAssumption(5);
        var t8 = new TestAssumption(8);

        Function<Assumption, Set<Integer>> a = (x) -> AssumptionAnalyzer.extractActualValues(x, y -> AssumptionAnalyzerTest.getShit(y).map(Collections::singleton).orElse(Collections.emptySet()));

        assertEquals(Collections.emptySet(), a.apply(Assumption.NoAssumption.INSTANCE));
        assertEquals(Collections.emptySet(), a.apply(nA));
        assertEquals(new HashSet<>(List.of(3)), a.apply(t3));
        assertEquals(new HashSet<>(List.of(3, 5)), a.apply(and(t5, t3)));
        assertEquals(Collections.emptySet(), a.apply(or(t5, t3)));
        assertEquals(new HashSet<>(List.of(3)), a.apply(or(and(t5, t3), and(t8, t3))));
        assertEquals(new HashSet<>(List.of(5)), a.apply(or(and(t5, t3), or(and(t8, t5), and(t3, t5)))));
    }

    @Test
    void canBeAssumed() {
        var tsetAssumption = new TsetAssumption("yeet");
        var testAssumption3 = new TestAssumption(3);
        var testAssumption5 = new TestAssumption(5);

        Predicate<Assumption> a = (x) -> AssumptionAnalyzer.canBeAssumed(x, (y) -> getShit(y).orElse(0) == 5);

        assertFalse(a.test(Assumption.NoAssumption.INSTANCE));
        assertFalse(a.test(testAssumption3));
        assertTrue(a.test(testAssumption5));
        assertFalse(a.test(and(tsetAssumption, testAssumption3)));
        assertTrue(a.test(and(tsetAssumption, testAssumption5)));
        assertTrue(a.test(or(testAssumption5, testAssumption5)));
        assertFalse(a.test(or(tsetAssumption, testAssumption5)));
    }

    private static class TsetAssumption extends Assumption {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TsetAssumption that = (TsetAssumption) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        private final String value;

        private TsetAssumption(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return null;
        }
    }

    private static class TestAssumption extends Assumption {
        private final int value;

        private TestAssumption(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestAssumption that = (TestAssumption) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }
}