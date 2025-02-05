package com.faforever.neroxis.utilities;

import com.faforever.neroxis.brushes.Brushes;
import com.faforever.neroxis.map.Symmetry;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.util.ArgumentParser;
import com.faforever.neroxis.util.vector.Vector2;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.faforever.neroxis.util.ImageUtil.writeAutoScaledPNGFromMask;
import static com.faforever.neroxis.util.ImageUtil.writeAutoScaledPNGFromMasks;

public class ImageGenerator {
    public static boolean DEBUG = false;
    private String folderPath;
    private int size = 512;
    private int numberToGenerate = 1;
    private boolean textures;
    private boolean brushes;
    private float colorVariation = 25;
    private float redStrength = -1;
    private float greenStrength = -1;
    private float blueStrength = -1;
    private int levelOfDetail = 100;
    private int maxFeatureSize = 100;
    private FloatMask redMask;
    private FloatMask greenMask;
    private FloatMask blueMask;

    public static void main(String[] args) throws IOException {

        Locale.setDefault(Locale.ROOT);

        ImageGenerator imageGenerator = new ImageGenerator();

        imageGenerator.interpretArguments(args);

        System.out.println("Creating image files at " + imageGenerator.folderPath);
        imageGenerator.generate();
    }

    public void interpretArguments(String[] args) {
        interpretArguments(ArgumentParser.parse(args));
    }

    private void interpretArguments(Map<String, String> arguments) {
        if (arguments.containsKey("help")) {
            System.out.println("""
                               image-generator usage:
                               --help                 produce help message
                               --folder-path arg      required, set the folder where the images will appear
                               --brushes              optional, generate brushes
                               --textures             optional, generate textures
                               --size arg             optional, set the size (side length) of images that will be generated
                               --num arg              optional, set the number of images to generate
                               --color-variation arg  optional, set the percent of color variation for textures that will be generated
                               --red arg              optional, set the average percent strength of red for textures that will be generated
                               --green arg            optional, set the average percent strength of green for textures that will be generated
                               --blue arg             optional, set the average percent strength of blue for textures that will be generated
                               --level-of-detail arg  optional, set the amount of fullness/detail for the textures that will be generated
                               - positive numerical input - default is 100, but there is no limit (higher numbers will have higher processing times)
                               --max-feature-size arg optional, set the maximum size of features/details for the textures that will be generated
                               - positive numerical input - default is 100, but there is no limit
                               --debug                optional, turn on debugging options
                               *** Note that generating images will overwrite previously made images of the same name in the same folder ***""");
            System.exit(0);
        }

        if (arguments.containsKey("debug")) {
            DEBUG = true;
        }

        if (!arguments.containsKey("folder-path")) {
            System.out.println("Folder path not Specified");
            System.exit(1);
        }

        folderPath = arguments.get("folder-path");

        if (arguments.containsKey("size")) {
            size = Integer.parseInt(arguments.get("size"));
        }

        if (arguments.containsKey("num")) {
            numberToGenerate = Integer.parseInt(arguments.get("num"));
        }

        if (arguments.containsKey("brushes")) {
            brushes = true;
        }

        if (arguments.containsKey("textures")) {
            textures = true;
        }

        if (arguments.containsKey("color-variation")) {
            colorVariation = Float.parseFloat(arguments.get("color-variation"));
        }

        if (arguments.containsKey("red")) {
            redStrength = Float.parseFloat(arguments.get("red"));
        }

        if (arguments.containsKey("green")) {
            greenStrength = Float.parseFloat(arguments.get("green"));
        }

        if (arguments.containsKey("blue")) {
            blueStrength = Float.parseFloat(arguments.get("blue"));
        }

        if (arguments.containsKey("level-of-detail")) {
            levelOfDetail = Integer.parseInt(arguments.get("level-of-detail"));
        }

        if (arguments.containsKey("max-feature-size")) {
            maxFeatureSize = Integer.parseInt(arguments.get("max-feature-size"));
        }
    }

    public void generate() throws IOException {
        if (brushes) {
            generateCustomBrushes(size, numberToGenerate);
        }
        if (textures) {
            generateCustomTextures(size, numberToGenerate, colorVariation);
        }
    }

    public void generateCustomBrushes(int size, int numberToGenerate) throws IOException {

        for (int i = 0; i < numberToGenerate; i++) {
            int brushListLength = Brushes.GENERATOR_BRUSHES.size();
            int reducedSize = size * 2 / 3;
            int variationDistance = StrictMath.max(size - reducedSize - 3, 0);
            int center = size / 2;
            int mountainsBrushSize = size / 10;
            Random random = new Random();

            String brush1 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength));
            String brush2 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength));
            String brush3 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength));
            String brush4 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength));
            String brush5 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength));

            BooleanMask base = new BooleanMask(size, random.nextLong(),
                                               new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));

            addBrushAroundCenter(base, center, random, variationDistance, brush1, reducedSize);
            addBrushAroundCenter(base, center, random, variationDistance, brush2, reducedSize);
            addBrushAroundCenter(base, center, random, variationDistance, brush3, reducedSize);
            addBrushAroundCenter(base, center, random, variationDistance, brush4, reducedSize);
            addBrushAroundCenter(base, center, random, variationDistance, brush5, reducedSize);

            BooleanMask mountains = new BooleanMask(size, random.nextLong(),
                                                    new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
            for (int x = 0; x < 10; x++) {
                Vector2 loc = base.getRandomPosition();
                if (loc == null) {
                    loc = new Vector2(center + random.nextInt(variationDistance) - random.nextInt(variationDistance),
                                      center + random.nextInt(variationDistance) - random.nextInt(variationDistance));
                }
                mountains.guidedWalkWithBrush(loc, base.getRandomPosition(), brush1, mountainsBrushSize, 7, 0.1f, 1f,
                                              mountainsBrushSize / 2, false);
            }
            mountains.multiply(base);
            BooleanMask mountainsBase = mountains.copy().inflate(15);
            BooleanMask mountainsBaseEdge = mountainsBase.copy().inflate(15).subtract(mountainsBase);

            FloatMask newBrush = new FloatMask(size, random.nextLong(),
                                               new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
            newBrush.useBrushWithinAreaWithDensity(mountains, brush2, variationDistance, 0.05f,
                                                   (float) 5 + random.nextInt(30), false);
            newBrush.useBrushWithinAreaWithDensity(mountainsBase, brush2, variationDistance, 0.005f,
                                                   (float) 5 + random.nextInt(30), false);
            newBrush.useBrushWithinAreaWithDensity(mountainsBaseEdge, brush2, variationDistance, 0.05f,
                                                   (float) 0.25 * (5 + random.nextInt(30)), false);
            newBrush.clampMin(0f);
            writeAutoScaledPNGFromMask(newBrush, Paths.get(folderPath + "\\Brush_" + (i + 1) + ".png"));
        }
    }

    private BooleanMask addBrushAroundCenter(BooleanMask base, int center, Random random, int variationDistance,
                                             String brush1, int reducedSize) {
        return base.addBrush(new Vector2(center + random.nextInt(variationDistance) - random.nextInt(variationDistance),
                                         center + random.nextInt(variationDistance) - random.nextInt(
                                                 variationDistance)), brush1, random.nextFloat(), 1f, reducedSize);
    }

    public void generateCustomTextures(int size, int numberToGenerate, float colorVariation) throws IOException {

        float redLocus;
        float greenLocus;
        float blueLocus;

        for (int i = 0; i < numberToGenerate; i++) {

            int brushListLength = Brushes.GENERATOR_BRUSHES.size();
            Random random = new Random();
            boolean tooEmpty = true;

            redMask = new FloatMask(size, random.nextLong(),
                                    new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
            greenMask = new FloatMask(size, random.nextLong(),
                                      new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
            blueMask = new FloatMask(size, random.nextLong(),
                                     new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));

            BooleanMask wholeImage = new BooleanMask(size, random.nextLong(),
                                                     new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
            wholeImage.fillRect(0, 0, size, size, true);
            BooleanMask areaToTexture = wholeImage;

            if (redStrength == -1) {
                redLocus = random.nextFloat();
            } else {
                redLocus = redStrength / 100;
            }
            if (greenStrength == -1) {
                greenLocus = random.nextFloat();
            } else {
                greenLocus = greenStrength / 100;
            }
            if (blueStrength == -1) {
                blueLocus = random.nextFloat();
            } else {
                blueLocus = blueStrength / 100;
            }

            for (int a = 0; a < levelOfDetail; a++) {
                int chainBrushSize = random.nextInt(maxFeatureSize) + 1;
                int chainTextureBrushSize = random.nextInt(maxFeatureSize) + 1;
                BooleanMask chain = new BooleanMask(size, random.nextLong(),
                                                    new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE));
                FloatMask chainTexture = new FloatMask(size, random.nextLong(),
                                                       new SymmetrySettings(Symmetry.NONE, Symmetry.NONE,
                                                                            Symmetry.NONE));
                if (a > 0.75 * levelOfDetail && tooEmpty) {
                    FloatMask wholeImageTexture = redMask.copy().add(greenMask).add(blueMask);
                    areaToTexture = wholeImageTexture.copyAsBooleanMask(0f, 0.1f);
                    if (areaToTexture.getCount() < size * 3) {
                        tooEmpty = false;
                        areaToTexture = wholeImage;
                    }
                }
                List<Vector2> possibleLocations = areaToTexture.getAllCoordinatesEqualTo(true, 1);
                int numPossibleLocations = possibleLocations.size();
                for (int x = 0; x < 5; x++) {
                    Vector2 loc = possibleLocations.get(random.nextInt(numPossibleLocations));
                    while (loc == null) {
                        loc = wholeImage.getRandomPosition();
                    }
                    Vector2 target = possibleLocations.get(random.nextInt(numPossibleLocations));
                    while (target == null) {
                        target = wholeImage.getRandomPosition();
                    }
                    chain.guidedWalkWithBrush(loc, target,
                                              Brushes.GENERATOR_BRUSHES.get(random.nextInt(brushListLength)),
                                              chainBrushSize, random.nextInt(15) + 1, 0.1f, 1f, chainBrushSize / 2,
                                              true);
                }
                chainTexture.useBrushWithinAreaWithDensity(chain, Brushes.GENERATOR_BRUSHES.get(
                        random.nextInt(brushListLength)), chainTextureBrushSize, 0.05f, 5 * random.nextFloat(), true);

                float redWeight = redLocus + ((random.nextBoolean() ? 1 : -1) * random.nextFloat() * colorVariation
                                              / 100);
                float greenWeight = greenLocus + ((random.nextBoolean() ? 1 : -1) * random.nextFloat() * colorVariation
                                                  / 100);
                float blueWeight = blueLocus + ((random.nextBoolean() ? 1 : -1) * random.nextFloat() * colorVariation
                                                / 100);

                if (redWeight < 0) {
                    redWeight = 0;
                }
                if (greenWeight < 0) {
                    greenWeight = 0;
                }
                if (blueWeight < 0) {
                    blueWeight = 0;
                }

                color(redWeight, greenWeight, blueWeight, chainTexture);
            }
            writeAutoScaledPNGFromMasks(redMask, greenMask, blueMask,
                                        Paths.get(folderPath + "\\Texture_" + (i + 1) + ".png"));
        }
    }

    private void color(float redPercent, float greenPercent, float bluePercent, FloatMask other) {
        redMask.add(other.copy().multiply(redPercent));
        greenMask.add(other.copy().multiply(greenPercent));
        blueMask.add(other.copy().multiply(bluePercent));
    }
}
