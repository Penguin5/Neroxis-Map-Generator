package com.faforever.neroxis.generator.prop;

import com.faforever.neroxis.biomes.Biome;
import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.ParameterConstraints;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.Pipeline;

public class RockFieldPropGenerator extends BasicPropGenerator {
    protected BooleanMask largeRockFieldMask;

    public RockFieldPropGenerator() {
        parameterConstraints = ParameterConstraints.builder().reclaimDensity(.25f, 1f).build();
    }

    @Override
    public void initialize(SCMap map, long seed, GeneratorParameters generatorParameters,
                           SymmetrySettings symmetrySettings, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, generatorParameters, symmetrySettings, terrainGenerator);
        largeRockFieldMask = new BooleanMask(1, random.nextLong(), symmetrySettings, "largeRockFieldMask", true);
    }

    @Override
    public void placePropsWithExclusion() {
        Pipeline.await(treeMask, cliffRockMask, largeRockFieldMask, fieldStoneMask);
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "placeProps", () -> {
            Biome biome = generatorParameters.biome();
            propPlacer.placeProps(treeMask.getFinalMask().subtract(noProps), biome.propMaterials().getTreeGroups(),
                                  3f, 7f);
            propPlacer.placeProps(cliffRockMask.getFinalMask().subtract(noProps), biome.propMaterials().getRocks(),
                                  .5f, 3f);
            propPlacer.placeProps(largeRockFieldMask.getFinalMask().subtract(noProps),
                                  biome.propMaterials().getRocks(), .5f, 3.5f);
            propPlacer.placeProps(fieldStoneMask.getFinalMask().subtract(noProps),
                                  biome.propMaterials().getBoulders(), 20f);
        });
    }

    @Override
    public void setupPipeline() {
        super.setupPipeline();
        setupRockFieldPipeline();
    }

    protected void setupRockFieldPipeline() {
        int mapSize = map.getSize();
        float reclaimDensity = generatorParameters.reclaimDensity();
        largeRockFieldMask.setSize(mapSize / 4);

        largeRockFieldMask.randomize((reclaimDensity * .75f + random.nextFloat() * .25f) * .00075f)
                          .fillEdge(32, false)
                          .dilute(.5f, 8)
                          .setSize(mapSize + 1);
        largeRockFieldMask.multiply(passableLand);
    }
}
