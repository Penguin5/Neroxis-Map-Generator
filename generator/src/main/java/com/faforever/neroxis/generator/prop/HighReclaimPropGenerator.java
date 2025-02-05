package com.faforever.neroxis.generator.prop;

import com.faforever.neroxis.biomes.Biome;
import com.faforever.neroxis.generator.ParameterConstraints;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.Pipeline;

public class HighReclaimPropGenerator extends BasicPropGenerator {
    public HighReclaimPropGenerator() {
        parameterConstraints = ParameterConstraints.builder()
                                                   .mountainDensity(.5f, 1f)
                                                   .plateauDensity(.5f, 1f)
                                                   .rampDensity(0f, .5f)
                                                   .reclaimDensity(.8f, 1f)
                                                   .biomes("Desert", "Frithen", "Loki", "Moonlight", "Wonder")
                                                   .build();
        weight = .5f;
    }

    @Override
    public void placeUnits() {
    }

    @Override
    public void placePropsWithExclusion() {
        Pipeline.await(treeMask, cliffRockMask, fieldStoneMask);
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "placeProps", () -> {
            Biome biome = generatorParameters.biome();
            propPlacer.placeProps(treeMask.getFinalMask().subtract(noProps), biome.propMaterials().getTreeGroups(),
                                  3f, 7f);
            propPlacer.placeProps(cliffRockMask.getFinalMask(), biome.propMaterials().getBoulders(), 3f, 8f);
            propPlacer.placeProps(fieldStoneMask.getFinalMask().subtract(noProps),
                                  biome.propMaterials().getBoulders(), 30f);
        });
    }

    @Override
    protected void setupPropPipeline() {
        int mapSize = map.getSize();
        float reclaimDensity = generatorParameters.reclaimDensity();
        treeMask.setSize(mapSize / 16);
        cliffRockMask.setSize(mapSize / 16);
        fieldStoneMask.setSize(mapSize / 4);

        cliffRockMask.randomize((reclaimDensity * .75f + random.nextFloat() * .25f) * .5f).setSize(mapSize + 1);
        cliffRockMask.multiply(impassable).dilute(.5f, 12).subtract(impassable).multiply(passableLand);
        fieldStoneMask.randomize((reclaimDensity + random.nextFloat()) / 2f * .0025f).setSize(mapSize + 1);
        fieldStoneMask.multiply(passableLand).fillEdge(10, false);
        treeMask.randomize((reclaimDensity + random.nextFloat()) / 2f * .15f).setSize(mapSize / 4);
        treeMask.inflate(2).erode(.5f);
        treeMask.setSize(mapSize + 1);
        treeMask.multiply(passableLand.copy().deflate(8)).fillEdge(8, false);
    }
}
