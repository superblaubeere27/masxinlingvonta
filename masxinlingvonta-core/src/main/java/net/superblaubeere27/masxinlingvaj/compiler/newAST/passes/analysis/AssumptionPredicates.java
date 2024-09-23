package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.Local;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.NullStateAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.ConstantRelationObject;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.NumberRelation;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.VariableRelationObject;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption.ObjectTypeRelation.IS_EXACTLY;
import static net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption.ObjectTypeRelation.IS_INSTANCE_OF;

public class AssumptionPredicates {
    public static final Function<Assumption, Optional<Boolean>> GET_NULL_STATE_PREDICATE = AssumptionPredicates::getNullState;
    public static final Function<Assumption, Optional<Long>> GET_INT_OR_LONG_PREDICATE = AssumptionPredicates::getIntOrLong;
    public static final Function<Assumption, Optional<Double>> GET_FLOAT_OR_DOUBLE_PREDICATE = AssumptionPredicates::getFloatOrDouble;
    public static final Function<Assumption, Optional<ObjectTypeAssumption>> GET_TYPE_ASSUMPTION_PREDICATE = AssumptionPredicates::getObjectTypeAssumption;
    public static final Function<Assumption, Set<ObjectTypeAssumption>> GET_TYPE_ASSUMPTION_PREDICATE_SET = assumption -> getObjectTypeAssumption(assumption).map(Collections::singleton).orElse(Collections.emptySet());

    private static Optional<Boolean> getNullState(Assumption assumption) {
        if (assumption instanceof NullStateAssumption nullStateAssumption) {
            return Optional.of(nullStateAssumption.isNull());
        }

        return Optional.empty();
    }

    private static Optional<Local> getAliasLocals(Assumption assumption) {
        if (assumption instanceof NumberRelation<?> numberRelation) {
            if (numberRelation.getOperator() == IntegerCompareExpr.Operator.EQUAL && numberRelation.getRhs() instanceof VariableRelationObject variableRelationObject) {
                return Optional.of(variableRelationObject.getVariable());
            }
        }

        return Optional.empty();
    }

    private static Optional<Long> getIntOrLong(Assumption assumption) {
        if (!(assumption instanceof NumberRelation<?> numberRelation)) {
            return Optional.empty();
        }

        var rhs = numberRelation.getRhs();

        if (!(rhs instanceof ConstantRelationObject<? extends Number> constantRelationObject))
            return Optional.empty();

        Number subject = constantRelationObject.getSubject();

        if (!(subject instanceof Long || subject instanceof Integer))
            return Optional.empty();

        return Optional.of(subject.longValue());
    }

    private static Optional<Double> getFloatOrDouble(Assumption assumption) {
        // TODO Implement this
        return Optional.empty();
    }

    private static Optional<ObjectTypeAssumption> getObjectTypeAssumption(Assumption assumption) {
        if (assumption instanceof ObjectTypeAssumption objectLocalInfo) {
            return Optional.of(objectLocalInfo);
        }

        return Optional.empty();
    }

    /**
     * Checks if it is safe to assume that the referenced object is an instance of type. This is basically not null + {@link #canAssumeIsAssignableFrom(Assumption, CompilerIndex, String)}
     */
    public static boolean canAssumeIsInstanceOf(Assumption assumption, CompilerIndex index, String type) {
        // If something is null, it cannot be cast to anything.
        if (assumption.extractValue(AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(true)) {
            return false;
        }

        return canAssumeIsAssignableFrom(assumption, index, type);
    }

    /**
     * Will a cast from the referenced object to the given type succeed?
     */
    public static boolean canAssumeIsAssignableFrom(Assumption assumption, CompilerIndex index, String type) {
        // We can cast every object to Object.
        if (type.equals("java/lang/Object"))
            return true;

        var isArray = type.charAt(0) == '[';
        var checkedClass = index.getClass(type);

        return assumption.canBeAssumed(innerAssumption -> {
            if (!(innerAssumption instanceof ObjectTypeAssumption info)) {
                return false;
            }

            // Arrays have special cases since they might contain primitives and objects ([I vs. [Ljava/lang/Object;)
            // Object arrays handle intheritance, but this is too complex for us now.
            if (isArray || info.getType().isArray()) {
                return info.getType().getTypeOfObjectOrArray().equals(type);
            }

            var targetClass = index.getClass(info.getType().getTypeOfObjectOrArray());

            Objects.requireNonNull(targetClass, "Unknown class");

            return (info.getRelation() == IS_INSTANCE_OF || info.getRelation() == IS_EXACTLY)
                    && !info.isInverted()
                    && (ClassHierarchyBuilder.isInstanceOf(targetClass, checkedClass)
                    || info.getType().getTypeOfObjectOrArray().equals(type));
        });
    }

}
