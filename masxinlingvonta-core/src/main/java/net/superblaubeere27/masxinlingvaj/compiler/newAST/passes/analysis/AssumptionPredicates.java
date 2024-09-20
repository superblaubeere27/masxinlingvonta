package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectLocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.ConstantRelationObject;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.NumberRelation;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class AssumptionPredicates {
    public static final Function<Assumption, Optional<Boolean>> GET_NULL_STATE_PREDICATE = AssumptionPredicates::getNullState;
    public static final Function<Assumption, Optional<Long>> GET_INT_OR_LONG_PREDICATE = AssumptionPredicates::getIntOrLong;
    public static final Function<Assumption, Optional<Double>> GET_FLOAT_OR_DOUBLE_PREDICATE = AssumptionPredicates::getFloatOrDouble;
    public static final Function<Assumption, Optional<ObjectTypeAssumptionState>> GET_TYPE_ASSUMPTION_PREDICATE = AssumptionPredicates::getObjectTypeAssumption;
    public static final Function<Assumption, Set<ObjectTypeAssumptionState>> GET_TYPE_ASSUMPTION_PREDICATE_SET = assumption -> getObjectTypeAssumption(assumption).map(Collections::singleton).orElse(Collections.emptySet());

    private static Optional<Boolean> getNullState(Assumption assumption) {
        if (assumption instanceof ObjectLocalInfo objectLocalInfo) {
            var isNullAssumption = objectLocalInfo.getIsNullAssumption();

            if (!isNullAssumption.isUnknown()) {
                return Optional.of(isNullAssumption.getAssumedValue());
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

    private static Optional<ObjectTypeAssumptionState> getObjectTypeAssumption(Assumption assumption) {
        if (assumption instanceof ObjectLocalInfo objectLocalInfo) {
            var objectTypeAssumption = objectLocalInfo.getObjectTypeAssumption();

            if (objectTypeAssumption.getKnownInfos().length > 0)
                return Optional.of(objectTypeAssumption);
        }

        return Optional.empty();
    }

    public static boolean canBeCastedTo(Assumption assumption, CompilerIndex index, String type) {
        if (!(assumption instanceof ObjectLocalInfo objectLocalInfo)) {
            return false;
        }
        // If something is null, it cannot be cast to anything.
        if (assumption.extractValue(AssumptionPredicates.GET_NULL_STATE_PREDICATE).orElse(true)) {
            return false;
        }
        // We can cast every object to Object.
        if (type.equals("java/lang/Object"))
            return true;

        var checkedClass = index.getClass(type);

        for (var info : objectLocalInfo.getObjectTypeAssumption().getKnownInfos()) {
            var targetClass = index.getClass(info.type().getTypeOfObjectOrArray());

            if (info.type().isArray()) {
                if (info.type().getTypeOfObjectOrArray().equals(type))
                    return true;
                else
                    continue;
            }

            Objects.requireNonNull(targetClass, "Unknown class");

            if (info.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF || info.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_EXACTLY
                    && !info.inverted()
                    && (ClassHierarchyBuilder.isInstanceOf(targetClass, checkedClass)
                    || info.type().getTypeOfObjectOrArray().equals(type)))
                return true;
        }

        return false;
    }

}
