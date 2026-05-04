package org.leavesmc.leaves.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

public final class SuperEarthWorldgenSelector {

    private SuperEarthWorldgenSelector() {
    }

    public static NoiseGeneratorSettings optimizeNoiseSettings(NoiseGeneratorSettings base) {
        NoiseRouter router = base.noiseRouter();
        NoiseRouter optimized = new NoiseRouter(
            router.barrierNoise(),
            router.fluidLevelFloodednessNoise(),
            router.fluidLevelSpreadNoise(),
            router.lavaNoise(),
            router.temperature(),
            router.vegetation(),
            SuperEarthDensityFunctionSelector.cacheDensity(router.continents()),
            SuperEarthDensityFunctionSelector.cacheDensity(router.erosion()),
            SuperEarthDensityFunctionSelector.cacheDensity(router.depth()),
            SuperEarthDensityFunctionSelector.cacheDensity(router.ridges()),
            SuperEarthDensityFunctionSelector.cacheDensity(router.preliminarySurfaceLevel()),
            optimizeFinalDensity(router.finalDensity()),
            router.veinToggle(),
            router.veinRidged(),
            router.veinGap()
        );
        return new NoiseGeneratorSettings(
            base.noiseSettings(),
            base.defaultBlock(),
            base.defaultFluid(),
            optimized,
            base.surfaceRule(),
            base.spawnTarget(),
            base.seaLevel(),
            base.disableMobGeneration(),
            base.aquifersEnabled(),
            base.oreVeinsEnabled(),
            base.useLegacyRandomSource()
        );
    }

    private static DensityFunction optimizeFinalDensity(DensityFunction function) {
        return SuperEarthDensityFunctionSelector.optimizeDensity(function);
    }
}
