package org.leavesmc.leaves.worldgen;

import java.util.List;

public final class SuperEarthRegistryAccess {

    private static volatile SuperEarthDatapackIndex.SuperEarthPackIndex cachedIndex = new SuperEarthDatapackIndex.SuperEarthPackIndex(java.util.List.of(), java.util.Map.of(), java.util.Map.of());
    private static volatile List<String> cachedProblems = List.of();

    private SuperEarthRegistryAccess() {
    }

    public static void refresh() {
        cachedIndex = SuperEarthDatapackIndex.scan();
        cachedProblems = SuperEarthReferenceValidator.validate(cachedIndex);
    }

    public static SuperEarthDatapackIndex.SuperEarthPackIndex index() {
        return cachedIndex;
    }

    public static List<String> problems() {
        return cachedProblems;
    }
}
