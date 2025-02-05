package com.faforever.neroxis.generator.style;

import com.faforever.neroxis.generator.GeneratorParameters;
import com.faforever.neroxis.generator.ParameterConstraints;
import com.faforever.neroxis.generator.prop.BasicPropGenerator;
import com.faforever.neroxis.generator.prop.EnemyCivPropGenerator;
import com.faforever.neroxis.generator.prop.NavyWrecksPropGenerator;
import com.faforever.neroxis.generator.prop.NeutralCivPropGenerator;
import com.faforever.neroxis.generator.prop.RockFieldPropGenerator;
import com.faforever.neroxis.generator.prop.SmallBattlePropGenerator;
import com.faforever.neroxis.generator.terrain.BigIslandsTerrainGenerator;

import java.util.Arrays;

public class BigIslandsStyleGenerator extends StyleGenerator {
    public BigIslandsStyleGenerator() {
        weight = 4;
        parameterConstraints = ParameterConstraints.builder()
                                                   .landDensity(0f, .75f)
                                                   .plateauDensity(0, .5f)
                                                   .mapSizes(768, 1024)
                                                   .build();
    }

    @Override
    protected void initialize(GeneratorParameters generatorParameters, long seed) {
        super.initialize(generatorParameters, seed);
        terrainGenerator = new BigIslandsTerrainGenerator();
        propGenerators.addAll(
                Arrays.asList(new BasicPropGenerator(), new EnemyCivPropGenerator(), new NavyWrecksPropGenerator(),
                              new NeutralCivPropGenerator(), new RockFieldPropGenerator(),
                              new SmallBattlePropGenerator()));
    }
}


