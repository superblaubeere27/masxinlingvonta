package net.superblaubeere27.masxinlingvaj.compiler.tree;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClassVTable {
    private final HashMap<MethodOrFieldName, Entry> currentImplementations;

    public ClassVTable() {
        this.currentImplementations = new HashMap<>();
    }

    private ClassVTable(ClassVTable parent) {
        this.currentImplementations = new HashMap<>(parent.currentImplementations);
    }

    public static ClassVTable create(@Nullable ClassVTable parent, CompilerClass cc) {
        var vtable = parent == null ? new ClassVTable() : new ClassVTable(parent);

        for (CompilerMethod method : cc.getMethods()) {
            if (method.getNode().name.equals("<init>"))
                continue;

            if (method.isStatic())
                continue;

            vtable.registerMethod(method);
        }

        return vtable;
    }

    public void registerMethod(CompilerMethod implementation) {
        var name = new MethodOrFieldName(implementation.getIdentifier());

        Entry entry = currentImplementations.get(name);

        CompilerMethod newImplementation = !implementation.isAbstract() ? implementation : null;

        if (entry == null) {
            entry = new Entry(newImplementation, new CopyOnWriteArraySet<>(Collections.singletonList(implementation.getIdentifier())));
        } else {
            entry = new Entry(newImplementation == null ? entry.currentImplementation : newImplementation, entry.implementedMethods);
        }

        currentImplementations.put(name, entry);
    }

    @Nullable
    public Entry getEntry(MethodOrFieldName method) {
        return this.currentImplementations.get(method);
    }

    public void addMethodsFromInterface(ClassVTable interfaceVTable) {
        for (Map.Entry<MethodOrFieldName, Entry> interfaceEntry : interfaceVTable.currentImplementations.entrySet()) {
            Entry currentEntry = currentImplementations.get(interfaceEntry.getKey());

            if (currentEntry == null) {
                currentImplementations.put(interfaceEntry.getKey(), interfaceEntry.getValue());
            } else {
                currentEntry.implementedMethods.addAll(interfaceEntry.getValue().implementedMethods);
            }
        }
    }

    public record Entry(
            CompilerMethod currentImplementation,
            Set<MethodOrFieldIdentifier> implementedMethods
    ) {

    }

}
