package com.faforever.neroxis.generator.prop;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.ParameterConstraints;
import com.faforever.neroxis.generator.Visibility;
import com.faforever.neroxis.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.Army;
import com.faforever.neroxis.map.Group;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.map.placement.UnitPlacer;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.Pipeline;

import java.util.ArrayList;

public class SmallBattlePropGenerator extends ReducedNaturalPropGenerator {
    protected BooleanMask landWreckMask;
    protected BooleanMask noWrecks;

    public SmallBattlePropGenerator() {
        parameterConstraints = ParameterConstraints.builder().landDensity(.25f, 1f).reclaimDensity(.5f, 1f).build();
    }

    @Override
    public void initialize(SCMap map, long seed, GeneratorParameters generatorParameters,
                           SymmetrySettings symmetrySettings, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, generatorParameters, symmetrySettings, terrainGenerator);
        landWreckMask = new BooleanMask(1, random.nextLong(), symmetrySettings, "landWreckMask", true);
        noWrecks = new BooleanMask(1, random.nextLong(), symmetrySettings);
    }

    @Override
    public void placeUnits() {
        if ((generatorParameters.visibility() != Visibility.UNEXPLORED)) {
            generateUnitExclusionMasks();
            Pipeline.await(landWreckMask);
            DebugUtil.timedRun("com.faforever.neroxis.map.generator", "placeUnits", () -> {
                Army army17 = new Army("ARMY_17", new ArrayList<>());
                Group army17Wreckage = new Group("WRECKAGE", new ArrayList<>());
                army17.addGroup(army17Wreckage);
                map.addArmy(army17);
                BooleanMask placementMask = landWreckMask.getFinalMask().subtract(noWrecks);
                unitPlacer.placeUnits(placementMask, UnitPlacer.T1_Land, army17, army17Wreckage, 3f, 4f);
                unitPlacer.placeUnits(placementMask, UnitPlacer.T2_Land, army17, army17Wreckage, 5f, 8f);
            });
        }
    }

    @Override
    public void setupPipeline() {
        super.setupPipeline();
        setupWreckPipeline();
    }

    protected void setupWreckPipeline() {
        int mapSize = map.getSize();
        float reclaimDensity = generatorParameters.reclaimDensity();
        landWreckMask.setSize(mapSize / 8);

        landWreckMask.randomize((reclaimDensity * .8f + random.nextFloat() * .2f) * .005f).setSize(mapSize + 1);
        landWreckMask.inflate(6f).multiply(passableLand).fillEdge(32, false);
    }

    protected void generateUnitExclusionMasks() {
        noWrecks.init(unbuildable.getFinalMask());
        generateExclusionZones(noWrecks, 128, 8, 32);
    }
}
