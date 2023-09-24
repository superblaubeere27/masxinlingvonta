package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectLocalInfo;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectTypeAssumptionState;
import net.superblaubeere27.masxinlingvaj.compiler.tree.ClassHierarchyBuilder;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerIndex;

import java.util.Optional;
import java.util.function.Function;

public class AssumptionPredicates {
    public static final Function<Assumption, Optional<Boolean>> GET_NULL_STATE_PREDICATE = AssumptionPredicates::getNullState;
    public static final Function<Assumption, Optional<ObjectTypeAssumptionState>> GET_TYPE_ASSUMPTION_PREDICATE = AssumptionPredicates::getObjectTypeAssumption;

    private static Optional<Boolean> getNullState(Assumption assumption) {
        if (assumption instanceof ObjectLocalInfo objectLocalInfo) {
            var isNullAssumption = objectLocalInfo.getIsNullAssumption();

            if (!isNullAssumption.isUnknown()) {
                return Optional.of(isNullAssumption.getAssumedValue());
            }
        }

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

        var checkedClass = index.getClass(type);

        for (var info : objectLocalInfo.getObjectTypeAssumption().getKnownInfos()) {
            var targetClass = index.getClass(info.type().getTypeOfObjectOrArray());

            if (info.type().isArray() && info.type().getTypeOfObjectOrArray().equals(type))
                return true;

            if (info.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF || info.relation() == ObjectTypeAssumptionState.ObjectTypeRelation.IS_EXACTLY
                    && !info.inverted()
                    && (ClassHierarchyBuilder.isInstanceOf(targetClass, checkedClass)
                    || info.type().getTypeOfObjectOrArray().equals(type)))
                return true;
        }

        return false;
    }

}
