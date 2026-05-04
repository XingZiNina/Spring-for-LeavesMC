package org.leavesmc.leaves.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.IdentityHashMap;
import java.util.Map;

public final class SuperEarthDensityGraphOptimizer {

    private SuperEarthDensityGraphOptimizer() {
    }

    public static DensityFunction optimize(DensityFunction function) {
        return function.mapAll(new OptimizingVisitor());
    }

    private static final class OptimizingVisitor implements DensityFunction.Visitor {
        private final Map<DensityFunction, DensityFunction> cache = new IdentityHashMap<>();

        @Override
        public DensityFunction apply(DensityFunction densityFunction) {
            DensityFunction cached = this.cache.get(densityFunction);
            if (cached != null) {
                return cached;
            }
            DensityFunction optimized = optimizeNode(densityFunction);
            this.cache.put(densityFunction, optimized);
            return optimized;
        }

        private DensityFunction optimizeNode(DensityFunction function) {
            if (function.minValue() == function.maxValue()) {
                return function;
            }
            DensityFunction cached = DensityFunctions.cacheOnce(DensityFunctions.cache2d(DensityFunctions.flatCache(function)));
            return DensityFunctions.cacheAllInCell(cached);
        }
    }
}
