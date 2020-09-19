package generator;

import biomes.Biome;
import biomes.Biomes;
import com.google.common.io.BaseEncoding;
import export.SCMapExporter;
import export.SaveExporter;
import export.ScenarioExporter;
import export.ScriptExporter;
import lombok.Getter;
import lombok.Setter;
import map.*;
import util.ArgumentParser;
import util.FileUtils;
import util.Pipeline;
import util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public strictfp class MapGenerator {

    public static final String VERSION = "1.0.23";
    public static final BaseEncoding NAME_ENCODER = BaseEncoding.base32().omitPadding().lowerCase();
    public static final float LAND_DENSITY_MIN = .65f;
    public static final float LAND_DENSITY_MAX = .9f;
    public static final float LAND_DENSITY_RANGE = LAND_DENSITY_MAX - LAND_DENSITY_MIN;
    public static final float MOUNTAIN_DENSITY_MIN = 0f;
    public static final float MOUNTAIN_DENSITY_MAX = 1f;
    public static final float MOUNTAIN_DENSITY_RANGE = MOUNTAIN_DENSITY_MAX - MOUNTAIN_DENSITY_MIN;
    public static final float RAMP_DENSITY_MIN = .025f;
    public static final float RAMP_DENSITY_MAX = .10f;
    public static final float RAMP_DENSITY_RANGE = RAMP_DENSITY_MAX - RAMP_DENSITY_MIN;
    public static final float PLATEAU_DENSITY_MIN = .35f;
    public static final float PLATEAU_DENSITY_MAX = .5f;
    public static final float PLATEAU_DENSITY_RANGE = PLATEAU_DENSITY_MAX - PLATEAU_DENSITY_MIN;
    public static final float PLATEAU_HEIGHT = 3f;
    public static final float VALLEY_HEIGHT = -1f;
    public static final float HILL_HEIGHT = 1f;
    public static boolean DEBUG = false;
    //read from cli args
    private String pathToFolder = ".";
    private String mapName = "debugMap";
    private long seed = new Random().nextLong();
    private Random random = new Random(seed);

    //read from key value arguments or map name
    private int spawnCount = 6;
    private float landDensity;
    private float plateauDensity;
    private float mountainDensity;
    private float rampDensity;
    private int mapSize = 512;
    private float reclaimDensity;
    private int mexCount;
    private Symmetry symmetry;
    private Biome biome;

    private SCMap map;
    private int spawnSeparation;
    private float waterHeight;

    //masks used in generation
    private ConcurrentBinaryMask land;
    private ConcurrentBinaryMask mountains;
    private ConcurrentBinaryMask hills;
    private ConcurrentBinaryMask valleys;
    private ConcurrentBinaryMask plateaus;
    private ConcurrentBinaryMask cliffs;
    private ConcurrentBinaryMask shore;
    private ConcurrentBinaryMask ramps;
    private ConcurrentBinaryMask impassable;
    private ConcurrentBinaryMask passable;
    private ConcurrentBinaryMask passableLand;
    private ConcurrentBinaryMask passableWater;
    private ConcurrentFloatMask heightmapBase;
    private ConcurrentFloatMask highlightGroundTexture;
    private ConcurrentFloatMask groundTexture;
    private ConcurrentFloatMask accentGroundTexture;
    private ConcurrentFloatMask plateauTexture;
    private ConcurrentFloatMask accentPlateauTexture;
    private ConcurrentFloatMask rockTexture;
    private ConcurrentFloatMask accentRockTexture;
    private ConcurrentFloatMask highAltitudeTexture;
    private ConcurrentBinaryMask rockDecal;
    private ConcurrentBinaryMask intDecal;
    private ConcurrentBinaryMask allWreckMask;
    private ConcurrentBinaryMask spawnLandMask;
    private ConcurrentBinaryMask spawnPlateauMask;
    private ConcurrentBinaryMask resourceMask;
    private ConcurrentBinaryMask plateauResourceMask;
    private ConcurrentBinaryMask waterResourceMask;
    private ConcurrentBinaryMask t1LandWreckMask;
    private ConcurrentBinaryMask t2LandWreckMask;
    private ConcurrentBinaryMask t3LandWreckMask;
    private ConcurrentBinaryMask t2NavyWreckMask;
    private ConcurrentBinaryMask navyFactoryWreckMask;
    private ConcurrentBinaryMask treeMask;
    private ConcurrentBinaryMask cliffRockMask;
    private ConcurrentBinaryMask fieldStoneMask;
    private ConcurrentBinaryMask largeRockFieldMask;
    private ConcurrentBinaryMask smallRockFieldMask;
    private BinaryMask noProps;
    private BinaryMask noWrecks;
    private BinaryMask noDecals;

    private SymmetryHierarchy symmetryHierarchy;

    public static void main(String[] args) throws IOException {

        Locale.setDefault(Locale.US);
        if (DEBUG) {
            Path debugDir = Paths.get(".", "debug");
            FileUtils.deleteRecursiveIfExists(debugDir);
            Files.createDirectory(debugDir);
        }

        MapGenerator generator = new MapGenerator();

        generator.interpretArguments(args);

        System.out.println("Generating map " + generator.mapName.replace('/', '^'));
        generator.generate();
        generator.save();
        System.out.println("Saving map to " + Paths.get(generator.pathToFolder).toAbsolutePath() + File.separator + generator.mapName.replace('/', '^'));
        System.out.println("Seed: " + generator.seed);
        System.out.println("Biome: " + generator.biome.getName());
        System.out.println("Land Density: " + generator.landDensity);
        System.out.println("Plateau Density: " + generator.plateauDensity);
        System.out.println("Mountain Density: " + generator.mountainDensity);
        System.out.println("Ramp Density: " + generator.rampDensity);
        System.out.println("Reclaim Density: " + generator.reclaimDensity);
        System.out.println("Mex Count: " + generator.mexCount);
        System.out.println("Terrain Symmetry: " + generator.symmetry);
        System.out.println("Team Symmetry: " + generator.symmetryHierarchy.getTeamSymmetry());
        System.out.println("Spawn Symmetry: " + generator.symmetryHierarchy.getSpawnSymmetry());
        System.out.println("Spawn Separation: " + generator.spawnSeparation);
        System.out.println("Done");
    }

    public void interpretArguments(String[] args) {
        if (args.length == 0 || args[0].startsWith("--")) {
            interpretArguments(ArgumentParser.parse(args));
        } else if (args.length == 2) {
            pathToFolder = args[0];
            mapName = args[1];
            parseMapName();
        } else {
            try {
                pathToFolder = args[0];
                try {
                    seed = Long.parseLong(args[1]);
                    random = new Random(seed);
                } catch (NumberFormatException nfe) {
                    System.out.println("Seed not numeric using default seed or mapname");
                }
                if (!VERSION.equals(args[2])) {
                    System.out.println("This generator only supports version " + VERSION);
                    System.exit(-1);
                }
                if (args.length >= 4) {
                    mapName = args[3];
                    parseMapName();
                } else {
                    randomizeOptions();
                    generateMapName();
                }
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.out.println("Usage: generator [targetFolder] [seed] [expectedVersion] (mapName)");
            }
        }
    }

    private void interpretArguments(Map<String, String> arguments) {
        if (arguments.containsKey("help")) {
            System.out.println("map-gen usage:\n" +
                    "--help                 produce help message\n" +
                    "--folder-path arg      optional, set the target folder for the generated map\n" +
                    "--seed arg             optional, set the seed for the generated map\n" +
                    "--map-name arg         optional, set the map name for the generated map\n" +
                    "--spawn-count arg      optional, set the spawn count for the generated map\n" +
                    "--land-density arg     optional, set the land density for the generated map\n" +
                    "--plateau-density arg  optional, set the plateau density for the generated map\n" +
                    String.format("--mountain-density arg optional, set the mountain density for the generated map (max %.2f)\n", MOUNTAIN_DENSITY_MAX) +
                    "--ramp-density arg     optional, set the ramp density for the generated map\n" +
                    "--reclaim-density arg  optional, set the reclaim density for the generated map\n" +
                    "--mex-count arg        optional, set the mex count per player for the generated map\n" +
                    "--symmetry arg         optional, set the symmetry for the generated map (Point, X, Y, XY, YX)\n" +
                    "--map-size arg		    optional, set the map size (5km = 256, 10km = 512, 20km = 1024)\n" +
                    "--biome arg		    optional, set the biome\n" +
                    "--debug                optional, turn on debugging options");
            System.exit(0);
        }

        if (arguments.containsKey("debug")) {
            DEBUG = true;
        }

        if (arguments.containsKey("folder-path")) {
            pathToFolder = arguments.get("folder-path");
        }

        if (arguments.containsKey("map-name")) {
            mapName = arguments.get("map-name");
            parseMapName();
            return;
        }

        if (arguments.containsKey("seed")) {
            seed = Long.parseLong(arguments.get("seed"));
            random = new Random(seed);
        }

        if (arguments.containsKey("spawn-count")) {
            spawnCount = Integer.parseInt(arguments.get("spawn-count"));
        }

        if (arguments.containsKey("map-size")) {
            mapSize = Integer.parseInt(arguments.get("map-size"));
        }

        randomizeOptions();

        if (arguments.containsKey("land-density")) {
            landDensity = StrictMath.max(StrictMath.min(Float.parseFloat(arguments.get("land-density")), LAND_DENSITY_MAX), LAND_DENSITY_MIN);
            landDensity = (float) StrictMath.round((landDensity - LAND_DENSITY_MIN) / (LAND_DENSITY_RANGE) * 127f) / 127f * LAND_DENSITY_RANGE + LAND_DENSITY_MIN;
        }

        if (arguments.containsKey("plateau-density")) {
            plateauDensity = StrictMath.max(StrictMath.min(Float.parseFloat(arguments.get("plateau-density")), PLATEAU_DENSITY_MAX), PLATEAU_DENSITY_MIN);
            plateauDensity = (float) StrictMath.round((plateauDensity - PLATEAU_DENSITY_MIN) / PLATEAU_DENSITY_RANGE * 127f) / 127f * PLATEAU_DENSITY_RANGE + PLATEAU_DENSITY_MIN;
        }

        if (arguments.containsKey("mountain-density")) {
            mountainDensity = StrictMath.min(Float.parseFloat(arguments.get("mountain-density")), MOUNTAIN_DENSITY_MAX);
            mountainDensity = (float) StrictMath.round((mountainDensity - MOUNTAIN_DENSITY_MIN) / MOUNTAIN_DENSITY_RANGE * 127f) / 127f * MOUNTAIN_DENSITY_RANGE + MOUNTAIN_DENSITY_MIN;
        }

        if (arguments.containsKey("ramp-density")) {
            rampDensity = StrictMath.max(StrictMath.min(Float.parseFloat(arguments.get("ramp-density")), RAMP_DENSITY_MAX), RAMP_DENSITY_MIN);
            rampDensity = (float) StrictMath.round((rampDensity - RAMP_DENSITY_MIN) / RAMP_DENSITY_RANGE * 127f) / 127f * RAMP_DENSITY_RANGE + RAMP_DENSITY_MIN;
        }

        if (arguments.containsKey("reclaim-density")) {
            reclaimDensity = Float.parseFloat(arguments.get("reclaim-density"));
            reclaimDensity = (float) StrictMath.round(reclaimDensity * 127f) / 127f;
        }

        if (arguments.containsKey("mex-count")) {
            mexCount = Integer.parseInt(arguments.get("mex-count"));
        }

        if (arguments.containsKey("symmetry")) {
            symmetry = Symmetry.valueOf(arguments.get("symmetry"));
        }

        if (arguments.containsKey("map-size")) {
            mapSize = Integer.parseInt(arguments.get("map-size"));
        }

        if (arguments.containsKey("biome")) {
            biome = Biomes.getBiomeByName(arguments.get("biome"));
        }

        generateMapName();
    }

    private void parseMapName() {
        mapName = mapName.replace('^', '/');
        if (!mapName.startsWith("neroxis_map_generator")) {
            throw new IllegalArgumentException("Map name is not a generated map");
        }
        String[] args = mapName.split("_");
        if (args.length < 4) {
            throw new RuntimeException("Version not specified");
        }
        String version = args[3];
        if (!VERSION.equals(version)) {
            throw new RuntimeException("Unsupported generator version: " + version);
        }
        if (args.length >= 5) {
            String seedString = args[4];
            try {
                seed = Long.parseLong(seedString);
            } catch (NumberFormatException nfe) {
                byte[] seedBytes = NAME_ENCODER.decode(seedString);
                ByteBuffer seedWrapper = ByteBuffer.wrap(seedBytes);
                seed = seedWrapper.getLong();
            }
            random = new Random(seed);
            if (args.length < 6) {
                randomizeOptions();
            }
        }
        if (args.length >= 6) {
            String optionString = args[5];
            byte[] optionBytes = NAME_ENCODER.decode(optionString);
            parseOptions(optionBytes);
        }
    }

    private void randomizeOptions() {
        landDensity = random.nextInt(127) / 127f * LAND_DENSITY_RANGE + LAND_DENSITY_MIN;
        plateauDensity = random.nextInt(127) / 127f * PLATEAU_DENSITY_RANGE + PLATEAU_DENSITY_MIN;
        mountainDensity = random.nextInt(127) / 127f * MOUNTAIN_DENSITY_RANGE + MOUNTAIN_DENSITY_MIN;
        rampDensity = random.nextInt(127) / 127f * RAMP_DENSITY_RANGE + RAMP_DENSITY_MIN;
        reclaimDensity = random.nextInt(127) / 127f;
        mexCount = (int) ((8 + 4 / spawnCount + random.nextInt(36 / spawnCount)) * (.5f + mapSize / 512f * .5f));
        Symmetry[] symmetries;
        if (spawnCount == 2) {
            symmetries = new Symmetry[]{Symmetry.POINT, Symmetry.QUAD, Symmetry.DIAG};
        } else {
            symmetries = Symmetry.values();
        }
        int symmetryValue = random.nextInt(symmetries.length);
        symmetry = symmetries[symmetryValue];
        biome = Biomes.getRandomBiome(random);
    }

    private void parseOptions(byte[] optionBytes) {
        if (optionBytes.length > 0) {
            if (optionBytes[0] <= 16) {
                spawnCount = optionBytes[0];
            }
        }
        if (optionBytes.length > 1) {
            mapSize = (int) optionBytes[1] * 64;
        }

        randomizeOptions();

        if (optionBytes.length > 2) {
            landDensity = optionBytes[2] / 127f * LAND_DENSITY_RANGE + LAND_DENSITY_MIN;
        }
        if (optionBytes.length > 3) {
            plateauDensity = (float) optionBytes[3] / 127f * PLATEAU_DENSITY_RANGE + PLATEAU_DENSITY_MIN;
        }
        if (optionBytes.length > 4) {
            mountainDensity = (float) optionBytes[4] / 127f * MOUNTAIN_DENSITY_RANGE + MOUNTAIN_DENSITY_MIN;
        }
        if (optionBytes.length > 5) {
            rampDensity = (float) optionBytes[5] / 127f * RAMP_DENSITY_RANGE + RAMP_DENSITY_MIN;
        }
        if (optionBytes.length > 6) {
            reclaimDensity = (float) optionBytes[6] / 127f;
        }
        if (optionBytes.length > 7) {
            mexCount = optionBytes[7];
        }
        if (optionBytes.length > 8) {
            symmetry = Symmetry.values()[optionBytes[8]];
        }
        if (optionBytes.length > 9) {
            biome = Biomes.list.get(optionBytes[9]);
        }

    }

    private void generateMapName() {
        String mapNameFormat = "neroxis_map_generator_%s_%s_%s";
        ByteBuffer seedBuffer = ByteBuffer.allocate(8);
        seedBuffer.putLong(seed);
        String seedString = NAME_ENCODER.encode(seedBuffer.array());
        byte[] optionArray = {(byte) spawnCount,
                (byte) (mapSize / 64),
                (byte) ((landDensity - LAND_DENSITY_MIN) / LAND_DENSITY_RANGE * 127f),
                (byte) ((plateauDensity - PLATEAU_DENSITY_MIN) / PLATEAU_DENSITY_RANGE * 127f),
                (byte) ((mountainDensity - MOUNTAIN_DENSITY_MIN) / MOUNTAIN_DENSITY_RANGE * 127f),
                (byte) ((rampDensity - RAMP_DENSITY_MIN) / RAMP_DENSITY_RANGE * 127f),
                (byte) (reclaimDensity * 127f),
                (byte) (mexCount),
                (byte) (symmetry.ordinal()),
                (byte) (Biomes.list.indexOf(biome))};
        String optionString = NAME_ENCODER.encode(optionArray);
        mapName = String.format(mapNameFormat, VERSION, seedString, optionString);
    }

    public void save() {
        try {
            Path folderPath = Paths.get(pathToFolder);

            FileUtils.deleteRecursiveIfExists(folderPath.resolve(mapName));

            long startTime = System.currentTimeMillis();
            Files.createDirectory(folderPath.resolve(mapName));
            SCMapExporter.exportSCMAP(folderPath, mapName, map);
            SCMapExporter.exportPreview(folderPath, mapName, map);
            SaveExporter.exportSave(folderPath, mapName, map);
            ScenarioExporter.exportScenario(folderPath, mapName, map);
            ScriptExporter.exportScript(folderPath, mapName, map);
            System.out.printf("File export done: %d ms\n", System.currentTimeMillis() - startTime);

            startTime = System.currentTimeMillis();
            Files.createDirectory(folderPath.resolve(mapName).resolve("debug"));
            SCMapExporter.exportSCMapString(folderPath, mapName, map);
            Pipeline.toFile(folderPath.resolve(mapName).resolve("debug").resolve("pipelineMaskHashes.txt"));
            toFile(folderPath.resolve(mapName).resolve("debug").resolve("generatorParams.txt"));
            System.out.printf("Debug export done: %d ms\n", System.currentTimeMillis() - startTime);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while saving the map.");
        }
    }

    public SCMap generate() {
        long startTime = System.currentTimeMillis();

        final int spawnSize = 32;
        final int mexSpacing = 32;
        final int hydroCount = spawnCount + random.nextInt(spawnCount / 2) * 2;
        map = new SCMap(mapSize, spawnCount, mexCount * spawnCount, hydroCount, biome);
        waterHeight = biome.getWaterSettings().getElevation();

        SpawnGenerator spawnGenerator = new SpawnGenerator(map, random.nextLong(), spawnSize);
        MexGenerator mexGenerator = new MexGenerator(map, random.nextLong(), spawnSize, mexSpacing);
        HydroGenerator hydroGenerator = new HydroGenerator(map, random.nextLong(), spawnSize);
        WreckGenerator wreckGenerator = new WreckGenerator(map, random.nextLong());
        PropGenerator propGenerator = new PropGenerator(map, random.nextLong());
        DecalGenerator decalGenerator = new DecalGenerator(map, random.nextLong());
        AIMarkerGenerator aiMarkerGenerator = new AIMarkerGenerator(map, random.nextLong());

        spawnSeparation = StrictMath.max(random.nextInt(map.getSize() / 4 - map.getSize() / 16) + map.getSize() / 16, 24);

        BinaryMask[] spawnMasks = spawnGenerator.generateSpawns(spawnSeparation, symmetry, (plateauDensity - PLATEAU_DENSITY_MIN) / PLATEAU_DENSITY_RANGE);
        spawnLandMask = new ConcurrentBinaryMask(spawnMasks[0], random.nextLong(), "spawnsLand");
        spawnPlateauMask = new ConcurrentBinaryMask(spawnMasks[1], random.nextLong(), "spawnsPlateau");

        symmetryHierarchy = spawnLandMask.getSymmetryHierarchy();
        setupTerrainPipeline();
        setupHeightmapPipeline();
        setupTexturePipeline();
        setupWreckPipeline();
        setupPropPipeline();
        setupResourcePipeline();

        Pipeline.start();

        CompletableFuture<Void> aiMarkerFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(passable, passableLand, passableWater);
            long sTime = System.currentTimeMillis();
            aiMarkerGenerator.generateAIMarkers(passable.getFinalMask(), passableLand.getFinalMask(), passableWater.getFinalMask(), 16, 22);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateAIMarkers\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });


        CompletableFuture<Void> textureFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(groundTexture, accentGroundTexture, highlightGroundTexture, plateauTexture, accentPlateauTexture, rockTexture, accentRockTexture, highAltitudeTexture);
            long sTime = System.currentTimeMillis();
            map.setTextureMasksLow(groundTexture.getFinalMask(), accentGroundTexture.getFinalMask(), highlightGroundTexture.getFinalMask(), plateauTexture.getFinalMask());
            map.setTextureMasksHigh(accentPlateauTexture.getFinalMask(), rockTexture.getFinalMask(), accentRockTexture.getFinalMask(), highAltitudeTexture.getFinalMask());
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateTextures\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> resourcesFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(resourceMask, plateaus, land, ramps, impassable, allWreckMask, plateauResourceMask, waterResourceMask);
            long sTime = System.currentTimeMillis();
            mexGenerator.generateMexes(resourceMask.getFinalMask(), plateauResourceMask.getFinalMask(), waterResourceMask.getFinalMask());
            hydroGenerator.generateHydros(resourceMask.getFinalMask().deflate(4));
            generateExclusionMasks();
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateResources\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        resourcesFuture.join();

        CompletableFuture<Void> wrecksFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(t1LandWreckMask, t2LandWreckMask, t3LandWreckMask, t2NavyWreckMask, navyFactoryWreckMask);
            long sTime = System.currentTimeMillis();
            wreckGenerator.generateWrecks(t1LandWreckMask.getFinalMask().minus(noWrecks), WreckGenerator.T1_Land, 3f);
            wreckGenerator.generateWrecks(t2LandWreckMask.getFinalMask().minus(noWrecks), WreckGenerator.T2_Land, 30f);
            wreckGenerator.generateWrecks(t3LandWreckMask.getFinalMask().minus(noWrecks), WreckGenerator.T3_Land, 60f);
            wreckGenerator.generateWrecks(t2NavyWreckMask.getFinalMask().minus(noWrecks), WreckGenerator.T2_Navy, 96f);
            wreckGenerator.generateWrecks(navyFactoryWreckMask.getFinalMask().minus(noWrecks), WreckGenerator.Navy_Factory, 256f);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateWrecks\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> propsFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(treeMask, cliffRockMask, largeRockFieldMask, fieldStoneMask);
            long sTime = System.currentTimeMillis();
            propGenerator.generateProps(treeMask.getFinalMask().minus(noProps), PropGenerator.TREE_GROUPS, 3f);
            propGenerator.generateProps(cliffRockMask.getFinalMask().minus(noProps), PropGenerator.ROCKS, 2f);
            propGenerator.generateProps(largeRockFieldMask.getFinalMask().minus(noProps), PropGenerator.ROCKS, 2f);
            propGenerator.generateProps(smallRockFieldMask.getFinalMask().minus(noProps), PropGenerator.ROCKS, 2f);
            propGenerator.generateProps(fieldStoneMask.getFinalMask().minus(noProps), PropGenerator.FIELD_STONES, 60f);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateProps\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> decalsFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(intDecal, rockDecal);
            long sTime = System.currentTimeMillis();
            decalGenerator.generateDecals(intDecal.getFinalMask().minus(noDecals), DecalGenerator.INT, 96f, 64f);
            decalGenerator.generateDecals(rockDecal.getFinalMask().minus(noDecals), DecalGenerator.ROCKS, 8f, 16f);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateDecals\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> heightMapFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(heightmapBase);
            long sTime = System.currentTimeMillis();
            map.setHeightmap(heightmapBase.getFinalMask());
            map.getHeightmap().getRaster().setPixel(0, 0, new int[]{0});
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, setHeightmap\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        wrecksFuture.join();
        propsFuture.join();
        decalsFuture.join();
        aiMarkerFuture.join();
        heightMapFuture.join();

        CompletableFuture<Void> placementFuture = CompletableFuture.runAsync(() -> {
            long sTime = System.currentTimeMillis();
            spawnGenerator.setMarkerHeights();
            mexGenerator.setMarkerHeights();
            hydroGenerator.setMarkerHeights();
            propGenerator.setPropHeights();
            wreckGenerator.setWreckHeights();
            decalGenerator.setDecalHeights();
            aiMarkerGenerator.setMarkerHeights();
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, setPlacements\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        textureFuture.join();
        placementFuture.join();
        Pipeline.stop();
        long sTime = System.currentTimeMillis();
        Preview.generate(map.getPreview(), map);
        if (DEBUG) {
            System.out.printf("Done: %4d ms, %s, generatePreview\n",
                    System.currentTimeMillis() - sTime,
                    Util.getStackTraceLineInClass(MapGenerator.class));
        }

        System.out.printf("Map generation done: %d ms\n", System.currentTimeMillis() - startTime);

        return map;
    }

    private void setupTerrainPipeline() {
        land = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetryHierarchy, "land");
        mountains = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetryHierarchy, "mountains");
        plateaus = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetryHierarchy, "plateaus");
        ramps = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "ramps");
        ConcurrentBinaryMask spawnRamps = new ConcurrentBinaryMask(mapSize, random.nextLong(), symmetryHierarchy, "spawnRamps");

        land.randomize(landDensity).smooth(mapSize / 256f, .75f);

        if (random.nextBoolean()) {
            mountains.progressiveWalk(mapSize / 128, (int) (mountainDensity * mapSize / 16));
        } else {
            mountains.randomWalk((int) (mountainDensity * mapSize / 128), mapSize / 16).erode(.25f);
        }
        mountains.enlarge(mapSize / 4).erode(.5f, symmetryHierarchy.getTerrainSymmetry(), 4).grow(.5f, symmetryHierarchy.getTerrainSymmetry(), 6);
        plateaus.randomize(plateauDensity).smooth(mapSize / 128f);

        land.enlarge(mapSize / 4).erode(.5f, symmetryHierarchy.getTerrainSymmetry(), 4).grow(.5f, symmetryHierarchy.getTerrainSymmetry(), 6);
        plateaus.enlarge(mapSize / 4).erode(.5f, symmetryHierarchy.getTerrainSymmetry(), 4).grow(.5f, symmetryHierarchy.getTerrainSymmetry(), 8).intersect(land);

        land.enlarge(mapSize + 1).smooth(6f, .25f);
        mountains.enlarge(mapSize + 1).minus(plateaus.copy().outline().inflate(4)).minus(land.copy().outline().inflate(8)).smooth(8f);
        plateaus.enlarge(mapSize + 1).smooth(6f, .25f);

        spawnLandMask.shrink(mapSize / 4).erode(.5f, symmetryHierarchy.getSpawnSymmetry(), 4).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 6);
        spawnLandMask.erode(.5f, symmetryHierarchy.getSpawnSymmetry()).enlarge(mapSize + 1).smooth(4);
        spawnPlateauMask.shrink(mapSize / 4).erode(.5f, symmetryHierarchy.getSpawnSymmetry(), 4).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 6);
        spawnPlateauMask.erode(.5f, symmetryHierarchy.getSpawnSymmetry()).enlarge(mapSize + 1).smooth(4);

        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(spawnLandMask).combine(spawnPlateauMask).combine(plateaus).filterShapes(1024);
        mountains.minus(spawnLandMask);

        ramps.randomize(rampDensity);
        ramps.intersect(plateaus.copy().outline()).minus(mountains.copy().inflate(8)).inflate(12).smooth(8f, .125f);

        spawnRamps.combine(spawnLandMask.copy().outline()).combine(spawnPlateauMask.copy().outline()).flipValues(.005f).inflate(24f);

        ramps.combine(spawnRamps);

        plateaus.combine(mountains).filterShapes(1024);
        mountains.intersect(land);
        plateaus.intersect(land);

        ConcurrentBinaryMask plateauOutline = plateaus.copy().outline().minus(ramps);
        ConcurrentBinaryMask landOutline = land.copy().outline().minus(plateaus.copy().inflate(1));

        cliffs = plateauOutline.copy();
        shore = landOutline.copy();

        cliffs.shrink(mapSize / 4).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 2).enlarge(mapSize + 1).erode(.25f, symmetryHierarchy.getSpawnSymmetry(), 2);
        cliffs.combine(plateauOutline.copy().flipValues(.01f).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 16)).smooth(2f, .75f);

        shore.shrink(mapSize / 4).flipValues(random.nextFloat() * .4f + .1f).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 4).enlarge(mapSize + 1);
        shore.smooth(2f, .75f).intersect(landOutline.copy().inflate(2));

        land.combine(cliffs).combine(shore);

        impassable = new ConcurrentBinaryMask(mountains, random.nextLong(), "impassable");
        hills = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetryHierarchy, "hills");
        valleys = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetryHierarchy, "valleys");

        impassable.inflate(3).combine(plateaus.copy().outline().inflate(3).minus(ramps)).combine(cliffs.copy().inflate(3)).combine(shore.copy().inflate(3));
        hills.randomWalk(random.nextInt(5) + 3, random.nextInt(500) + 350).enlarge(mapSize + 1).smooth(10f, .25f).intersect(land.copy().deflate(8)).minus(plateaus);
        valleys.randomWalk(random.nextInt(5) + 3, random.nextInt(500) + 350).enlarge(mapSize + 1).smooth(10f, .25f).intersect(plateaus.copy().deflate(4));

        passable = new ConcurrentBinaryMask(impassable, random.nextLong(), "passable").invert();
        passableLand = new ConcurrentBinaryMask(land, random.nextLong(), "passableLand");
        passableWater = new ConcurrentBinaryMask(land, random.nextLong(), "passableWater").invert();

        passable.deflate(8).trimEdge(8);
        passableLand.deflate(4).intersect(passable);
        passableWater.deflate(16).trimEdge(8);
    }

    private void setupHeightmapPipeline() {
        heightmapBase = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapBase");
        ConcurrentFloatMask heightmapLand = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapLand");
        ConcurrentFloatMask heightmapMountains = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapMountains");
        ConcurrentFloatMask heightmapPlateaus = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapPlateaus");
        ConcurrentFloatMask heightmapCliffs = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapCliffs");
        ConcurrentFloatMask heightmapShore = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapShore");
        ConcurrentFloatMask heightmapHills = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapHills");
        ConcurrentFloatMask heightmapValleys = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "heightmapValleys");

        heightmapBase.init(land, waterHeight + .5f, waterHeight + .5f);
        heightmapPlateaus.init(plateaus, 0, PLATEAU_HEIGHT).smooth(12f, ramps);
        heightmapHills.init(hills, 0, HILL_HEIGHT).smooth(8f, land.copy().minus(plateaus));
        heightmapValleys.init(valleys, 0, VALLEY_HEIGHT).smooth(8f, plateaus);
        heightmapLand.maskToHeightmap(0.25f, 48, land).add(heightmapHills).add(heightmapValleys).smooth(4).add(heightmapPlateaus);
        heightmapCliffs.maskToMoutains(cliffs);
        heightmapShore.maskToMoutains(shore);
        heightmapMountains.maskToMoutains(mountains).smooth(2f);
        heightmapMountains.add(heightmapLand).add(heightmapCliffs).add(heightmapShore).smooth(1);

        heightmapBase.add(heightmapMountains);
    }

    private void setupTexturePipeline() {
        ConcurrentFloatMask slope = heightmapBase.copy().gradient();

        ConcurrentBinaryMask ground = new ConcurrentBinaryMask(land, random.nextLong(), "ground");
        ConcurrentBinaryMask accentGround = new ConcurrentBinaryMask(hills, random.nextLong(), "accentGround");
        ConcurrentBinaryMask highlightGround = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "highlightGround");
        ConcurrentBinaryMask plateau = new ConcurrentBinaryMask(plateaus, random.nextLong(), "plateau");
        ConcurrentBinaryMask accentPlateau = new ConcurrentBinaryMask(valleys, random.nextLong(), "accentPlateau");
        ConcurrentBinaryMask rock = new ConcurrentBinaryMask(impassable, random.nextLong(), "rock");
        ConcurrentBinaryMask accentRock = new ConcurrentBinaryMask(slope, .75f, random.nextLong(), "accentRock");
        ConcurrentBinaryMask highAltitude = new ConcurrentBinaryMask(heightmapBase, waterHeight + 10f, random.nextLong(), "highAltitude");
        intDecal = new ConcurrentBinaryMask(land, random.nextLong(), "intDecal");
        rockDecal = new ConcurrentBinaryMask(mountains, random.nextLong(), "rockDecal");
        groundTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "groundTexture");
        accentGroundTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "accentGroundTexture");
        highlightGroundTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "highlightGroundTexture");
        plateauTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "plateauTexture");
        accentPlateauTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "accentPlateauTexture");
        rockTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "rockTexture");
        accentRockTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "accentRockTexture");
        highAltitudeTexture = new ConcurrentFloatMask(mapSize / 2, random.nextLong(), symmetryHierarchy, "highAltitudeTexture");

        ground.shrink(mapSize / 4).erode(.5f, symmetryHierarchy.getSpawnSymmetry(), 6).grow(.5f, symmetryHierarchy.getSpawnSymmetry(), 4);
        ground.combine(plateaus).combine(shore.copy().inflate(16)).intersect(land).smooth(2f, .25f).filterShapes(32);
        accentGround.acid(.1f, 0).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).smooth(8f, .75f);
        highlightGround.combine(ground).minus(plateaus.copy().outline()).deflate(8).acid(.1f, 0).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).smooth(8f, .75f);
        plateau.acid(.1f, 0).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).smooth(8f, .75f);
        accentPlateau.acid(.1f, 0).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).smooth(8f, .75f);

        groundTexture.init(ground, 0, 1).smooth(8f);
        accentGroundTexture.init(accentGround, 0, 1).smooth(8f);
        highlightGroundTexture.init(highlightGround, 0, 1).smooth(8f);
        plateauTexture.init(plateau, 0, 1).smooth(8f);
        accentPlateauTexture.init(accentPlateau, 0, 1).smooth(8f);
        rockTexture.init(rock, 0, 1).smooth(2f);
        accentRockTexture.init(accentRock, 0, 1).smooth(4f);
        highAltitudeTexture.init(highAltitude, 0, 1).smooth(16f);

    }

    private void setupWreckPipeline() {
        t1LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "t1LandWreck");
        t2LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "t2LandWreck");
        t3LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "t3LandWreck");
        t2NavyWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "t2NavyWreck");
        navyFactoryWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetryHierarchy, "navyFactoryWreck");
        allWreckMask = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetryHierarchy, "allWreck");

        t1LandWreckMask.randomize(reclaimDensity * .005f).intersect(land).deflate(mapSize / 512f).trimEdge(20);
        t2LandWreckMask.randomize(reclaimDensity * .0025f).intersect(land).minus(t1LandWreckMask).trimEdge(64);
        t3LandWreckMask.randomize(reclaimDensity * .001f).intersect(land).minus(t1LandWreckMask).minus(t2LandWreckMask).trimEdge(mapSize / 8);
        navyFactoryWreckMask.randomize(reclaimDensity * .005f).minus(land.copy().inflate(16)).trimEdge(20);
        t2NavyWreckMask.randomize(reclaimDensity * .005f).intersect(land.copy().inflate(1).outline()).trimEdge(20);
        allWreckMask.combine(t1LandWreckMask).combine(t2LandWreckMask).combine(t3LandWreckMask).combine(t2NavyWreckMask).inflate(2);
    }

    private void setupPropPipeline() {
        treeMask = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetryHierarchy, "tree");
        cliffRockMask = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetryHierarchy, "cliffRock");
        fieldStoneMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetryHierarchy, "fieldStone");
        largeRockFieldMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetryHierarchy, "largeRockField");
        smallRockFieldMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetryHierarchy, "smallRockField");

        cliffRockMask.randomize(.4f).intersect(impassable).inflate(1).minus(plateaus.copy().minus(mountains.copy().inflate(4))).inflate(2).intersect(land);
        fieldStoneMask.randomize(reclaimDensity * .001f).enlarge(256).intersect(land).minus(impassable);
        fieldStoneMask.enlarge(mapSize + 1).trimEdge(10);
        treeMask.randomize(.1f).inflate(1).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).enlarge(mapSize / 4).smooth(4).erode(.5f, symmetryHierarchy.getSpawnSymmetry());
        treeMask.enlarge(mapSize / 2).intersect(land).minus(impassable);
        treeMask.enlarge(mapSize + 1).deflate(5).trimEdge(3);
        largeRockFieldMask.randomize(reclaimDensity * .001f).trimEdge(mapSize / 16).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).intersect(land).inflate(8).minus(impassable);
        smallRockFieldMask.randomize(reclaimDensity * .003f).trimEdge(mapSize / 64).erode(.5f, symmetryHierarchy.getSpawnSymmetry()).intersect(land).inflate(2).minus(impassable);
    }

    private void setupResourcePipeline() {
        resourceMask = new ConcurrentBinaryMask(land, random.nextLong(), "resource");
        waterResourceMask = new ConcurrentBinaryMask(land, random.nextLong(), "waterResource").invert();
        plateauResourceMask = new ConcurrentBinaryMask(land, random.nextLong(), "plateauResource");

        resourceMask.minus(impassable).minus(ramps.copy().deflate(8)).deflate(8);
        resourceMask.trimEdge(16).fillCenter(16, false);
        waterResourceMask.deflate(48).trimEdge(16).fillCenter(16, false);
        plateauResourceMask.combine(resourceMask).intersect(plateaus).trimEdge(16).fillCenter(16, true);
    }

    private void generateExclusionMasks() {
        noProps = new BinaryMask(impassable.getFinalMask(), 0);
        noProps.combine(ramps.getFinalMask());

        for (int i = 0; i < map.getSpawnCount(); i++) {
            noProps.fillCircle(map.getSpawn(i), 30, true);
        }
        for (int i = 0; i < map.getMexCount(); i++) {
            noProps.fillCircle(map.getMex(i), 5, true);
        }
        for (int i = 0; i < map.getHydroCount(); i++) {
            noProps.fillCircle(map.getHydro(i), 7, true);
        }

        noProps.combine(allWreckMask.getFinalMask());

        noWrecks = new BinaryMask(impassable.getFinalMask(), 0);

        for (int i = 0; i < map.getSpawnCount(); i++) {
            noWrecks.fillCircle(map.getSpawn(i), 96, true);
        }
        for (int i = 0; i < map.getMexCount(); i++) {
            noWrecks.fillCircle(map.getMex(i), 10, true);
        }
        for (int i = 0; i < map.getHydroCount(); i++) {
            noWrecks.fillCircle(map.getHydro(i), 15, true);
        }

        noDecals = new BinaryMask(mapSize + 1, 0, symmetryHierarchy);

        for (int i = 0; i < map.getSpawnCount(); i++) {
            noDecals.fillCircle(map.getSpawn(i), 24, true);
        }
    }

    public void toFile(Path path) throws IOException {

        Files.deleteIfExists(path);
        File outFile = path.toFile();
        boolean status = outFile.createNewFile();
        FileOutputStream out = new FileOutputStream(outFile);
        String summaryString = "Seed: " + seed +
                "\nBiome: " + biome.getName() +
                "\nLand Density: " + landDensity +
                "\nPlateau Density: " + plateauDensity +
                "\nMountain Density: " + mountainDensity +
                "\nRamp Density: " + rampDensity +
                "\nReclaim Density: " + reclaimDensity +
                "\nMex Count: " + mexCount +
                "\nTerrain Symmetry: " + symmetry +
                "\nTeam Symmetry: " + symmetryHierarchy.getTeamSymmetry() +
                "\nSpawn Symmetry: " + symmetryHierarchy.getSpawnSymmetry();
        out.write(summaryString.getBytes());
        out.flush();
        out.close();
    }
}
