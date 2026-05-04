package org.leavesmc.leaves.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class SuperEarthDensityFunctionSelector {

    private SuperEarthDensityFunctionSelector() {
    }

    public static DensityFunction cacheDensity(DensityFunction function) {
        return DensityFunctions.cacheOnce(DensityFunctions.cache2d(DensityFunctions.flatCache(function)));
    }

    public static DensityFunction optimizeDensity(DensityFunction function) {
        return SuperEarthDensityGraphOptimizer.optimize(cacheDensity(function));
    }
}
