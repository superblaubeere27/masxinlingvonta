package net.superblaubeere27.masxinlingvonta.test.framework;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MLVTestFramework {
    private static final HashMap<String, AtomicInteger> reachedMilestones = new HashMap<>();
    private static final HashMap<String, String> cfgs = new HashMap<>();

    public static void milestoneReached(String name) {
        reachedMilestones.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
    }

    static HashMap<String, Integer> clearMilestones() {
        var resultHashMap = new HashMap<String, Integer>();

        reachedMilestones.forEach((k, v) -> resultHashMap.put(k, v.get()));

        reachedMilestones.clear();

        return resultHashMap;
    }

    public static void registerCFG(String name, String cfg) {
        cfgs.put(name, cfg);
    }

    public static String getCFGTextOfMethod(Class<?> clazz, String name) {
        var prefix = clazz.getName().replace('.', '/') + "." + name;

        return cfgs
                .entrySet()
                .stream()
                .filter(x -> x.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No CFG found for " + prefix));
    }

}
