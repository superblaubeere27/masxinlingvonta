package net.superblaubeere27.masxinlingvaj.compiler.newAST.passes.inlining;

import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.InstProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ReadsMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.ThrowsProperty;
import net.superblaubeere27.masxinlingvaj.compiler.newAST.expr.properties.WritesMemoryProperty;
import net.superblaubeere27.masxinlingvaj.compiler.tree.MethodOrFieldIdentifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class JavaIntrinsicMethods {
    private static final HashMap<MethodOrFieldIdentifier, MethodIntrinsicData> INTRINSICS = new HashMap<>();

    static {
        INTRINSICS.put(new MethodOrFieldIdentifier("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;"), new MethodIntrinsicData(true, false, SideEffectType.READ_NONE));
        INTRINSICS.put(new MethodOrFieldIdentifier("java/lang/Integer", "intValue", "()I"), new MethodIntrinsicData(true, false, SideEffectType.READ_NONE));
    }

    static boolean mayInline(MethodOrFieldIdentifier method) {
        var methodIntrinsicData = INTRINSICS.get(method);

        return methodIntrinsicData == null || !methodIntrinsicData.preventInline();
    }

    public static Collection<InstProperty> getPropertiesOfMethod(MethodOrFieldIdentifier method) {
        var methodIntrinsicData = INTRINSICS.get(method);

        if (methodIntrinsicData == null) {
            return null;
        }

        var properties = new ArrayList<InstProperty>();

        if (methodIntrinsicData.mayThrow) {
            properties.add(ThrowsProperty.INSTANCE);
        }

        switch (methodIntrinsicData.sideEffects) {
            case READ_NONE -> {
            }
            case READ_ONLY -> properties.add(ReadsMemoryProperty.INSTANCE);
            case WRITES -> {
                properties.add(ReadsMemoryProperty.INSTANCE);
                properties.add(WritesMemoryProperty.INSTANCE);
            }
        }

        return properties;
    }

    enum SideEffectType {
        READ_NONE,
        READ_ONLY,
        WRITES
    }

    record MethodIntrinsicData(boolean preventInline, boolean mayThrow, SideEffectType sideEffects) {

    }
}
