package com.faforever.neroxis.generator.decal;

import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.Pipeline;

public class BasicDecalGenerator extends DecalGenerator {
    @Override
    public void placeDecals() {
        Pipeline.await(fieldDecal, slopeDecal);
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "placeDecals", () -> {
            decalPlacer.placeDecals(fieldDecal.getFinalMask(),
                                    generatorParameters.biome().decalMaterials().getFieldNormals(), 32, 32, 24,
                                    32);
            decalPlacer.placeDecals(fieldDecal.getFinalMask(),
                                    generatorParameters.biome().decalMaterials().getFieldAlbedos(), 64, 128, 24,
                                    32);
            decalPlacer.placeDecals(slopeDecal.getFinalMask(),
                                    generatorParameters.biome().decalMaterials().getSlopeNormals(), 16, 32, 16,
                                    32);
            decalPlacer.placeDecals(slopeDecal.getFinalMask(),
                                    generatorParameters.biome().decalMaterials().getSlopeAlbedos(), 64, 128, 32,
                                    48);
        });
    }

    @Override
    public void setupPipeline() {
        fieldDecal.init(passableLand);
        slopeDecal.init(slope, .25f);
        fieldDecal.subtract(slopeDecal.copy().inflate(16));
    }
}
