package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.compare.IntegerCompareExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.ObjectType;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.NullStateAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.ObjectTypeAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.specialObject.BoxSpecialObjectAssumption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkedAssumptionsTest {

    @Test
    public void testOrLinkedAssumptionsSameTypes() {
        var assumptionA = LinkedAssumptions.and(
                new BoxSpecialObjectAssumption("java/lang/Integer", new NumberRelation<>(new ConstantRelationObject<>(5), IntegerCompareExpr.Operator.EQUAL)),
                NullStateAssumption.IS_NON_NULL,
                ObjectTypeAssumption.assumeInstanceOf(new ObjectType("java/lang/Integer"))
        );
        var assumptionB = LinkedAssumptions.and(
                new BoxSpecialObjectAssumption("java/lang/Integer", new NumberRelation<>(new ConstantRelationObject<>(5), IntegerCompareExpr.Operator.EQUAL)),
                NullStateAssumption.IS_NON_NULL,
                ObjectTypeAssumption.assumeInstanceOf(new ObjectType("java/lang/Integer"))
        );

        var link = LinkedAssumptions.or(assumptionA, assumptionB);

        assertEquals(assumptionA, link);
    }

    @Test
    public void testAndElision() {
        var assumptionNotNull = NullStateAssumption.IS_NON_NULL;
        var assumptionType = ObjectTypeAssumption.assumeClassIsExactly(new ObjectType("[C"));

        var andLeaf = LinkedAssumptions.and(assumptionNotNull, assumptionType);
        var orLeaf = LinkedAssumptions.or(assumptionType, andLeaf);

        var andNode = LinkedAssumptions.and(orLeaf, assumptionNotNull);

        assertEquals(LinkedAssumptions.and(assumptionType, assumptionNotNull), andNode);
    }

//    @Test
//    public void testOrLinkedAssumptionsRemoval() {
//        var assumptionA = LinkedAssumptions.and(
//                new BoxSpecialObjectAssumption("java/lang/Integer", new NumberRelation<>(new ConstantRelationObject<>(5), IntegerCompareExpr.Operator.EQUAL)),
//                ObjectLocalInfo.create().assumeIsNull(true),
//                ObjectLocalInfo.create().assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType("java/lang/Integer")))
//        );
//        var assumptionB = LinkedAssumptions.and(
//                new BoxSpecialObjectAssumption("java/lang/Integer", new NumberRelation<>(new ConstantRelationObject<>(5), IntegerCompareExpr.Operator.EQUAL)),
//                ObjectLocalInfo.create().assumeIsNull(false),
//                ObjectLocalInfo.create().assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType("java/lang/Integer")))
//        );
//        var mergedShould = LinkedAssumptions.and(
//                new BoxSpecialObjectAssumption("java/lang/Integer", new NumberRelation<>(new ConstantRelationObject<>(5), IntegerCompareExpr.Operator.EQUAL)),
//                ObjectLocalInfo.create().assumeObjectType(new ObjectTypeAssumptionState.ObjectTypeInfo(ObjectTypeAssumptionState.ObjectTypeRelation.IS_INSTANCE_OF, false, new ObjectType("java/lang/Integer")))
//        );
//
//        var link = LinkedAssumptions.or(assumptionA, assumptionB);
//
//        assertEquals(mergedShould, link);
//    }

}