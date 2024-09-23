package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.functionAssumptions;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.GetFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.LoadFieldExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.jvm.invoke.InvokeExpr;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.AssumptionAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.Assumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalInfoSnapshot;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.LocalVariableAnalyzer;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.NullStateAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.object.specialObject.BoxSpecialObjectAssumption;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.analysis.locals.relations.LinkedAssumptions;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import javax.annotation.Nullable;

public class InstrinsicAssumptions {

    @Nullable
    public static Assumption getAssumptionForInvoke(LocalVariableAnalyzer analyzer, LocalInfoSnapshot snapshot, InvokeExpr invokeExpr) {
        if (invokeExpr.getTarget().equals(new MethodOrFieldIdentifier("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"))) {
            var assumption = analyzer.processExpression(snapshot, invokeExpr.getChildrenInStackOrder()[0]);

            return LinkedAssumptions.and(new BoxSpecialObjectAssumption("java/lang/Integer", assumption), NullStateAssumption.IS_NON_NULL);
        } else if (invokeExpr.getTarget().equals(new MethodOrFieldIdentifier("java/lang/Integer", "intValue", "()I"))) {
            var assumption = analyzer.processExpression(snapshot, invokeExpr.getChildrenInStackOrder()[0]);
            var assumptionsAboutContent = AssumptionAnalyzer.extractAssumption(assumption, x -> {
                if (x instanceof BoxSpecialObjectAssumption box && box.getBoxType().equals("java/lang/Integer")) {
                    return box.getAssumption();
                }

                return Assumption.NoAssumption.INSTANCE;
            });

            return assumptionsAboutContent;
        }

        return Assumption.NoAssumption.INSTANCE;
    }

    @Nullable
    public static Assumption getAssumptionForFieldLoad(LocalVariableAnalyzer analyzer, LocalInfoSnapshot snapshot, LoadFieldExpr fieldExpr) {
        if (fieldExpr instanceof GetFieldExpr getField && fieldExpr.getTarget().equals(new MethodOrFieldIdentifier("java/lang/Integer", "value", "I"))) {
            var assumption = analyzer.processExpression(snapshot, getField.getInstance());

            var assumptionsAboutContent = AssumptionAnalyzer.extractAssumption(assumption, x -> {
                if (x instanceof BoxSpecialObjectAssumption box && box.getBoxType().equals("java/lang/Integer")) {
                    return box.getAssumption();
                }

                return Assumption.NoAssumption.INSTANCE;
            });

            return assumptionsAboutContent;
        }

        return Assumption.NoAssumption.INSTANCE;
    }

}
