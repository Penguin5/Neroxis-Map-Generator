package neroxis.generator;

import com.google.common.io.BaseEncoding;
import lombok.Getter;
import lombok.Setter;
import neroxis.biomes.Biome;
import neroxis.biomes.Biomes;
import neroxis.brushes.Brushes;
import neroxis.exporter.MapExporter;
import neroxis.exporter.SCMapExporter;
import neroxis.map.*;
import neroxis.util.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static neroxis.util.ImageUtils.readImage;

@Getter
@Setter
public strictfp class MapGenerator {

    public static final String VERSION;
    public static final BaseEncoding NAME_ENCODER = BaseEncoding.base32().omitPadding().lowerCase();
    public static final float LAND_DENSITY_MIN = .8f;
    public static final float LAND_DENSITY_MAX = .9f;
    public static final float LAND_DENSITY_RANGE = LAND_DENSITY_MAX - LAND_DENSITY_MIN;
    public static final float MOUNTAIN_DENSITY_MIN = 0f;
    public static final float MOUNTAIN_DENSITY_MAX = 1f;
    public static final float MOUNTAIN_DENSITY_RANGE = MOUNTAIN_DENSITY_MAX - MOUNTAIN_DENSITY_MIN;
    public static final float RAMP_DENSITY_MIN = 0f;
    public static final float RAMP_DENSITY_MAX = 1f;
    public static final float RAMP_DENSITY_RANGE = RAMP_DENSITY_MAX - RAMP_DENSITY_MIN;
    public static final float PLATEAU_DENSITY_MIN = .5f;
    public static final float PLATEAU_DENSITY_MAX = .75f;
    public static final float PLATEAU_DENSITY_RANGE = PLATEAU_DENSITY_MAX - PLATEAU_DENSITY_MIN;
    public static final float RECLAIM_DENSITY_MIN = 0f;
    public static final float RECLAIM_DENSITY_MAX = 1f;
    public static final float RECLAIM_DENSITY_RANGE = RECLAIM_DENSITY_MAX - RECLAIM_DENSITY_MIN;
    public static final float PLATEAU_HEIGHT = 8.5f;
    public static final float OCEAN_FLOOR = -16f;
    public static final float VALLEY_FLOOR = -5f;
    public static final float LAND_HEIGHT = .25f;
    private static final String BLANK_PREVIEW = "/images/generatedMapIcon.png";
    public static boolean DEBUG = false;

    static {
        String version = MapGenerator.class.getPackage().getImplementationVersion();
        VERSION = version != null ? version : "snapshot";
    }

    //read from cli args
    private String pathToFolder = ".";
    private String mapName = "debugMap";
    private long seed = new Random().nextLong();
    private Random random;
    private boolean tournamentStyle = false;
    private boolean blind = false;
    private boolean unexplored = false;
    private long generationTime;

    //read from key value arguments or map name
    private int spawnCount = 6;
    private float landDensity;
    private float plateauDensity;
    private float mountainDensity;
    private float rampDensity;
    private int mapSize = 512;
    private int numTeams = 2;
    private float reclaimDensity;
    private int mexCount;
    private Symmetry terrainSymmetry;
    private Biome biome;

    private SCMap map;
    private float waterHeight;
    private boolean optionsUsed = false;

    //masks used in generation
    private ConcurrentBinaryMask land;
    private ConcurrentBinaryMask mountains;
    private ConcurrentBinaryMask hills;
    private ConcurrentBinaryMask valleys;
    private ConcurrentBinaryMask plateaus;
    private ConcurrentBinaryMask ramps;
    private ConcurrentBinaryMask impassable;
    private ConcurrentBinaryMask unbuildable;
    private ConcurrentBinaryMask notFlat;
    private ConcurrentBinaryMask passable;
    private ConcurrentBinaryMask passableLand;
    private ConcurrentBinaryMask passableWater;
    private ConcurrentFloatMask slope;
    private ConcurrentFloatMask heightmapBase;
    private ConcurrentFloatMask accentGroundTexture;
    private ConcurrentFloatMask waterBeachTexture;
    private ConcurrentFloatMask accentSlopesTexture;
    private ConcurrentFloatMask accentPlateauTexture;
    private ConcurrentFloatMask slopesTexture;
    private ConcurrentFloatMask steepHillsTexture;
    private ConcurrentFloatMask rockTexture;
    private ConcurrentFloatMask accentRockTexture;
    private ConcurrentBinaryMask fieldDecal;
    private ConcurrentBinaryMask slopeDecal;
    private ConcurrentBinaryMask mountainDecal;
    private ConcurrentBinaryMask allWreckMask;
    private ConcurrentBinaryMask spawnLandMask;
    private ConcurrentBinaryMask spawnPlateauMask;
    private ConcurrentBinaryMask resourceMask;
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
    private ConcurrentBinaryMask baseMask;
    private ConcurrentBinaryMask civReclaimMask;
    private ConcurrentBinaryMask allBaseMask;
    private BinaryMask noProps;
    private BinaryMask noWrecks;
    private BinaryMask noBases;
    private BinaryMask noCivs;

    private String brush1;
    private String brush2;
    private String brush3;
    private String brush4;
    private String brush5;
    private boolean generateBrushMaps = false;
    private int mapNumber = 0;
    private int numToGenerate = 1;

    private SymmetrySettings symmetrySettings;
    private boolean hasCivilians;
    private boolean enemyCivilians;
    private float mexMultiplier = 1f;
    private boolean validArgs = true;
    private boolean generationComplete = true;

    public static void main(String[] args) throws Exception {

        Locale.setDefault(Locale.ENGLISH);
        if (DEBUG) {
            Path debugDir = Paths.get(".", "debug");
            FileUtils.deleteRecursiveIfExists(debugDir);
            Files.createDirectory(debugDir);
        }

        MapGenerator generator = new MapGenerator();

        if (!generator.validArgs) {
            return;
        }

        for (int i = 0; i < generator.numToGenerate; i++) {
            generator.interpretArguments(args);
            System.out.println(generator.mapName);
            generator.generate();
            if (generator.map == null) {
                System.out.println("Map Generation Failed see stack trace for details");
                return;
            }
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
            System.out.println("Terrain Symmetry: " + generator.terrainSymmetry);
            System.out.println("Team Symmetry: " + generator.symmetrySettings.getTeamSymmetry());
            System.out.println("Spawn Symmetry: " + generator.symmetrySettings.getSpawnSymmetry());
            System.out.println("Done");
            generator.prepareForNextMap();
        }
    }

    public void interpretArguments(String[] args) throws Exception {
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
                } catch (NumberFormatException nfe) {
                    System.out.println("Seed not numeric using default seed or map name");
                }
                if (!VERSION.equals(args[2])) {
                    System.out.println("This generator only supports version " + VERSION);
                    validArgs = false;
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
        if (!validArgs) {
            return;
        }
        setupSymmetrySettings();
    }

    private void interpretArguments(Map<String, String> arguments) throws Exception {
        if (arguments.containsKey("help")) {
            System.out.println("map-gen usage:\n" +
                    "--help                 produce help message\n" +
                    "--folder-path arg      optional, set the target folder for the generated map\n" +
                    "--seed arg             optional, set the seed for the generated map\n" +
                    "--map-name arg         optional, set the map name for the generated map\n" +
                    "--spawn-count arg      optional, set the spawn count for the generated map\n" +
                    "--num-teams arg        optional, set the number of teams for the generated map (0 is no teams asymmetric)\n" +
                    "--land-density arg     optional, set the land density for the generated map\n" +
                    "--plateau-density arg  optional, set the plateau density for the generated map\n" +
                    "--mountain-density arg optional, set the mountain density for the generated map\n" +
                    "--ramp-density arg     optional, set the ramp density for the generated map\n" +
                    "--reclaim-density arg  optional, set the reclaim density for the generated map\n" +
                    "--mex-density arg      optional, set the mex density for the generated map\n" +
                    "--mex-count arg        optional, set the mex count per player for the generated map\n" +
                    "--map-size arg		    optional, set the map size (5km = 256, 10km = 512, 20km = 1024)\n" +
                    "--biome arg		    optional, set the biome\n" +
                    "--tournament-style     optional, set map to tournament style which will remove the preview.png and add time of original generation to map\n" +
                    "--blind                optional, set map to blind style which will apply tournament style and remove in game lobby preview\n" +
                    "--unexplored           optional, set map to unexplored style which will apply tournament and blind style and add unexplored fog of war\n" +
                    "--generate arg         optional, generate arg number of maps\n" +
                    "--debug                optional, turn on debugging options\n" +
                    "--no-hash              optional, turn off pipeline hashing of masks");
            validArgs = false;
            return;
        }

        if (arguments.containsKey("no-hash")) {
            Pipeline.HASH_MASK = false;
        }

        if (arguments.containsKey("debug")) {
            DEBUG = true;
        }

        if (arguments.containsKey("folder-path")) {
            pathToFolder = arguments.get("folder-path");
        }

        if (arguments.containsKey("map-name") && arguments.get("map-name") != null) {
            mapName = arguments.get("map-name");
            parseMapName();
            return;
        }

        tournamentStyle = arguments.containsKey("tournament-style") || arguments.containsKey("blind") || arguments.containsKey("unexplored");
        blind = arguments.containsKey("blind") || arguments.containsKey("unexplored");
        unexplored = arguments.containsKey("unexplored");

        if (tournamentStyle) {
            generationTime = Instant.now().getEpochSecond();
        }

        if (arguments.containsKey("seed") && arguments.get("seed") != null) {
            seed = Long.parseLong(arguments.get("seed"));
        }

        if (arguments.containsKey("spawn-count") && arguments.get("spawn-count") != null) {
            spawnCount = Integer.parseInt(arguments.get("spawn-count"));
        }

        if (arguments.containsKey("map-size") && arguments.get("map-size") != null) {
            mapSize = Integer.parseInt(arguments.get("map-size"));
        }

        if (arguments.containsKey("num-teams") && arguments.get("num-teams") != null) {
            numTeams = Integer.parseInt(arguments.get("num-teams"));
            if (numTeams != 2) {
                optionsUsed = true;
            }
        }

        randomizeOptions();

        if (!tournamentStyle) {
            if (arguments.containsKey("land-density") && arguments.get("land-density") != null) {
                float inLandDensity = Float.parseFloat(arguments.get("land-density"));
                landDensity = StrictMath.round(inLandDensity * 127f) / 127f;
                optionsUsed = true;
            }

            if (arguments.containsKey("plateau-density") && arguments.get("plateau-density") != null) {
                float inPlateauDensity = Float.parseFloat(arguments.get("plateau-density"));
                plateauDensity = StrictMath.round(inPlateauDensity * 127f) / 127f;
                optionsUsed = true;
            }

            if (arguments.containsKey("mountain-density") && arguments.get("mountain-density") != null) {
                float inMountainDensity = Float.parseFloat(arguments.get("mountain-density"));
                mountainDensity = StrictMath.round(inMountainDensity * 127f) / 127f;
                optionsUsed = true;
            }

            if (arguments.containsKey("ramp-density") && arguments.get("ramp-density") != null) {
                float inRampDensity = Float.parseFloat(arguments.get("ramp-density"));
                rampDensity = StrictMath.round(inRampDensity * 127f) / 127f;
                optionsUsed = true;
            }

            if (arguments.containsKey("reclaim-density") && arguments.get("reclaim-density") != null) {
                float inReclaimDensity = Float.parseFloat(arguments.get("reclaim-density"));
                reclaimDensity = StrictMath.round(inReclaimDensity * 127f) / 127f;
                optionsUsed = true;
            }

            if (arguments.containsKey("mex-density") && arguments.get("mex-density") != null) {
                float mexDensity = Float.parseFloat(arguments.get("mex-density"));
                setMexCount(mexDensity);
                optionsUsed = true;
            }

            if (arguments.containsKey("mex-count") && arguments.get("mex-count") != null) {
                mexCount = Integer.parseInt(arguments.get("mex-count"));
                optionsUsed = true;
            }

            if (arguments.containsKey("symmetry") && arguments.get("symmetry") != null) {
                terrainSymmetry = Symmetry.valueOf(arguments.get("symmetry"));
                optionsUsed = true;
            }

            if (arguments.containsKey("biome") && arguments.get("biome") != null) {
                biome = Biomes.loadBiome(arguments.get("biome"));
                optionsUsed = true;
            }

            if (arguments.containsKey("generate")) {
                numToGenerate = Integer.parseInt(arguments.get("generate"));
            }
        }

        generateMapName();
    }

    private void parseMapName() throws Exception {
        if (!mapName.startsWith("neroxis_map_generator")) {
            throw new IllegalArgumentException("Map name is not a generated map");
        }
        String[] args = mapName.split("_");
        if (args.length < 4) {
            throw new RuntimeException("Version not specified");
        }
        if (args.length < 5) {
            throw new RuntimeException("Seed not specified");
        }
        String version = args[3];
        if (!VERSION.equals(version)) {
            throw new RuntimeException("Wrong generator version: " + version);
        }

        byte[] optionBytes = new byte[0];

        String seedString = args[4];
        try {
            seed = Long.parseLong(seedString);
        } catch (NumberFormatException nfe) {
            byte[] seedBytes = NAME_ENCODER.decode(seedString);
            ByteBuffer seedWrapper = ByteBuffer.wrap(seedBytes);
            seed = seedWrapper.getLong();
        }

        if (args.length >= 6) {
            String optionString = args[5];
            optionBytes = NAME_ENCODER.decode(optionString);
        }

        if (args.length >= 7) {
            String parametersString = args[6];
            byte[] parameterBytes = NAME_ENCODER.decode(parametersString);
            parseParameters(parameterBytes);
        }

        if (args.length >= 8) {
            String timeString = args[7];
            generationTime = ByteBuffer.wrap(NAME_ENCODER.decode(timeString)).getLong();
        }

        parseOptions(optionBytes);
    }

    private void randomizeOptions() throws Exception {
        if (numTeams != 0 && spawnCount % numTeams != 0) {
            throw new IllegalArgumentException("spawnCount is not a multiple of number of teams");
        }
        random = new Random(seed ^ generationTime);

        landDensity = StrictMath.round(RandomUtils.averageRandomFloat(random, 3) * 127) / 127f;
        plateauDensity = StrictMath.round(RandomUtils.averageRandomFloat(random, 3) * 127) / 127f;
        mountainDensity = StrictMath.round(RandomUtils.averageRandomFloat(random, 3) * 127) / 127f;
        rampDensity = StrictMath.round(RandomUtils.averageRandomFloat(random, 3) * 127) / 127f;
        reclaimDensity = StrictMath.round(RandomUtils.averageRandomFloat(random, 3) * 127) / 127f;
        setMexCount(RandomUtils.averageRandomFloat(random, 3));
        List<Symmetry> terrainSymmetries;
        if (spawnCount == 2) {
            terrainSymmetries = new ArrayList<>(Arrays.asList(Symmetry.POINT2, Symmetry.POINT4, Symmetry.POINT6, Symmetry.POINT8, Symmetry.QUAD, Symmetry.DIAG));
        } else {
            terrainSymmetries = new ArrayList<>(Arrays.asList(Symmetry.values()));
        }
        if (numTeams != 0) {
            terrainSymmetries.remove(Symmetry.NONE);
            terrainSymmetries.removeIf(symmetry -> symmetry.getNumSymPoints() % numTeams != 0 || symmetry.getNumSymPoints() > spawnCount * 4);
        } else {
            terrainSymmetries.clear();
            terrainSymmetries.add(Symmetry.NONE);
        }
        if (random.nextFloat() < .75f) {
            terrainSymmetries.removeIf(symmetry -> !symmetry.isPerfectSymmetry());
        }
        terrainSymmetry = terrainSymmetries.get(random.nextInt(terrainSymmetries.size()));
        biome = Biomes.loadBiome(Biomes.BIOMES_LIST.get(random.nextInt(Biomes.BIOMES_LIST.size())));
    }

    private void setMexCount(float mexDensity) {
        switch (spawnCount) {
            case 2:
                mexCount = (int) (10 + 15 * mexDensity);
                break;
            case 4:
                mexCount = (int) (9 + 8 * mexDensity);
                break;
            case 6:
            case 8:
                mexCount = (int) (8 + 5 * mexDensity);
                break;
            case 10:
                mexCount = (int) (8 + 3 * mexDensity);
                break;
            case 12:
                mexCount = (int) (6 + 4 * mexDensity);
                break;
            case 14:
                mexCount = (int) (6 + 3 * mexDensity);
                break;
            case 16:
                mexCount = (int) (6 + 2 * mexDensity);
                break;
            default:
                mexCount = (int) (8 + 8 * mexDensity);
                break;
        }
        if (mapSize < 512) {
            mexMultiplier = .75f;
        } else if (mapSize > 512) {
            switch (spawnCount) {
                case 2:
                    mexMultiplier = 1.75f;
                    break;
                case 4:
                case 6:
                    mexMultiplier = 1.5f;
                    break;
                case 8:
                case 10:
                    mexMultiplier = 1.35f;
                    break;
                default:
                    mexMultiplier = 1.25f;
                    break;
            }
        }
        mexCount *= mexMultiplier;
    }

    private void setupSymmetrySettings() {
        Symmetry spawnSymmetry;
        Symmetry teamSymmetry;
        List<Symmetry> spawns;
        List<Symmetry> teams;
        switch (terrainSymmetry) {
            case POINT2:
            case POINT3:
            case POINT4:
            case POINT5:
            case POINT6:
            case POINT7:
            case POINT8:
            case POINT9:
            case POINT10:
            case POINT11:
            case POINT12:
            case POINT13:
            case POINT14:
            case POINT15:
            case POINT16:
                spawns = new ArrayList<>(Arrays.asList(Symmetry.POINT2, Symmetry.POINT3, Symmetry.POINT4, Symmetry.POINT5,
                        Symmetry.POINT6, Symmetry.POINT7, Symmetry.POINT8, Symmetry.POINT9, Symmetry.POINT10, Symmetry.POINT11,
                        Symmetry.POINT12, Symmetry.POINT13, Symmetry.POINT14, Symmetry.POINT15, Symmetry.POINT16));
                teams = new ArrayList<>(Arrays.asList(Symmetry.POINT2, Symmetry.POINT3, Symmetry.POINT4, Symmetry.POINT5,
                        Symmetry.POINT6, Symmetry.POINT7, Symmetry.POINT8, Symmetry.POINT9, Symmetry.POINT10, Symmetry.POINT11,
                        Symmetry.POINT12, Symmetry.POINT13, Symmetry.POINT14, Symmetry.POINT15, Symmetry.POINT16,
                        Symmetry.X, Symmetry.Z, Symmetry.XZ, Symmetry.ZX, Symmetry.QUAD, Symmetry.DIAG));
                break;
            case QUAD:
                spawns = new ArrayList<>(Arrays.asList(Symmetry.POINT2, Symmetry.QUAD));
                teams = new ArrayList<>(Arrays.asList(Symmetry.X, Symmetry.Z, Symmetry.QUAD));
                break;
            case DIAG:
                spawns = new ArrayList<>(Arrays.asList(Symmetry.POINT2, Symmetry.DIAG));
                teams = new ArrayList<>(Arrays.asList(Symmetry.XZ, Symmetry.ZX, Symmetry.DIAG));
                break;
            default:
                spawns = new ArrayList<>(Collections.singletonList(terrainSymmetry));
                teams = new ArrayList<>(Collections.singletonList(terrainSymmetry));
                break;
        }
        if (numTeams != 0) {
            spawns.removeIf(symmetry -> spawnCount % symmetry.getNumSymPoints() != 0 || numTeams % symmetry.getNumSymPoints() != 0);
            teams.removeIf(symmetry -> spawnCount % symmetry.getNumSymPoints() != 0 || numTeams % symmetry.getNumSymPoints() != 0);
        }
        spawnSymmetry = spawns.get(random.nextInt(spawns.size()));
        teamSymmetry = teams.get(random.nextInt(teams.size()));
        symmetrySettings = new SymmetrySettings(terrainSymmetry, teamSymmetry, spawnSymmetry);
        if (spawnCount == 2) {
            symmetrySettings.setSpawnSymmetry(Symmetry.POINT2);
        }
    }

    private void parseOptions(byte[] optionBytes) throws Exception {
        if (optionBytes.length > 0) {
            if (optionBytes[0] <= 16) {
                spawnCount = optionBytes[0];
            }
        }
        if (optionBytes.length > 1) {
            mapSize = (int) optionBytes[1] * 64;
        }
        if (optionBytes.length > 8) {
            numTeams = optionBytes[8];
        }

        randomizeOptions();

        if (optionBytes.length > 2) {
            landDensity = optionBytes[2] / 127f;
        }
        if (optionBytes.length > 3) {
            plateauDensity = optionBytes[3] / 127f;
        }
        if (optionBytes.length > 4) {
            mountainDensity = optionBytes[4] / 127f;
        }
        if (optionBytes.length > 5) {
            rampDensity = optionBytes[5] / 127f;
        }
        if (optionBytes.length > 6) {
            reclaimDensity = optionBytes[6] / 127f;
        }
        if (optionBytes.length > 7) {
            mexCount = optionBytes[7];
        }
        if (optionBytes.length > 9) {
            terrainSymmetry = Symmetry.values()[optionBytes[9]];
        }
        if (optionBytes.length > 10) {
            biome = Biomes.loadBiome(Biomes.BIOMES_LIST.get(optionBytes[10]));
        }
    }

    private void parseParameters(byte[] parameterBytes) {
        BitSet parameters = BitSet.valueOf(parameterBytes);
        tournamentStyle = parameters.get(0);
        blind = parameters.get(1);
        unexplored = parameters.get(2);
    }

    private void generateMapName() {
        String mapNameFormat = "neroxis_map_generator_%s_%s_%s";
        ByteBuffer seedBuffer = ByteBuffer.allocate(8);
        seedBuffer.putLong(seed);
        String seedString = NAME_ENCODER.encode(seedBuffer.array());
        byte[] optionArray;
        if (optionsUsed) {
            optionArray = new byte[]{(byte) spawnCount,
                    (byte) (mapSize / 64),
                    (byte) StrictMath.round(landDensity * 127f),
                    (byte) StrictMath.round(plateauDensity * 127f),
                    (byte) StrictMath.round(mountainDensity * 127f),
                    (byte) StrictMath.round(rampDensity * 127f),
                    (byte) StrictMath.round(reclaimDensity * 127f),
                    (byte) mexCount,
                    (byte) numTeams,
                    (byte) terrainSymmetry.ordinal(),
                    (byte) Biomes.BIOMES_LIST.indexOf(biome.getName())};
        } else {
            optionArray = new byte[]{(byte) spawnCount,
                    (byte) (mapSize / 64)};
        }
        BitSet parameters = new BitSet();
        parameters.set(0, tournamentStyle);
        parameters.set(1, blind);
        parameters.set(2, unexplored);
        String optionString = NAME_ENCODER.encode(optionArray) + "_" + NAME_ENCODER.encode(parameters.toByteArray());
        if (tournamentStyle) {
            String timeString = NAME_ENCODER.encode(ByteBuffer.allocate(8).putLong(generationTime).array());
            optionString += "_" + timeString;
        }
        mapName = String.format(mapNameFormat, VERSION, seedString, optionString).toLowerCase();
    }

    public void save() {
        try {
            map.setName(mapName);
            map.setFolderName(mapName);
            map.setFilePrefix(mapName);
            Path folderPath = Paths.get(pathToFolder);

            FileUtils.deleteRecursiveIfExists(folderPath.resolve(mapName));

            long startTime = System.currentTimeMillis();
            MapExporter.exportMap(folderPath, map, !tournamentStyle);
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

    public SCMap generate() throws IOException {
        long startTime = System.currentTimeMillis();

        final int spawnSize = 36;
        final int hydroCount = spawnCount >= 4 ? spawnCount + random.nextInt(spawnCount / 4) * 2 : spawnCount;
        int mexSpacing = mapSize / 10;
        mexSpacing *= StrictMath.min(StrictMath.max(36f / (mexCount * spawnCount), .5f), 1.5f);
        hasCivilians = random.nextBoolean() && !unexplored;
        enemyCivilians = random.nextBoolean();
        map = new SCMap(mapSize, spawnCount, mexCount * spawnCount, hydroCount, biome);
        waterHeight = biome.getWaterSettings().getElevation();

        SpawnGenerator spawnGenerator = new SpawnGenerator(map, random.nextLong(), spawnSize);
        MexGenerator mexGenerator = new MexGenerator(map, random.nextLong(), mexSpacing);
        HydroGenerator hydroGenerator = new HydroGenerator(map, random.nextLong());
        PropGenerator propGenerator = new PropGenerator(map, random.nextLong());
        DecalGenerator decalGenerator = new DecalGenerator(map, random.nextLong());
        UnitGenerator unitGenerator = new UnitGenerator(random.nextLong());

        int spawnSeparation = random.nextInt(map.getSize() / 4 - map.getSize() / 16) + map.getSize() / 16;

        BinaryMask[] spawnMasks = spawnGenerator.generateSpawns(spawnSeparation, symmetrySettings, plateauDensity);
        spawnLandMask = new ConcurrentBinaryMask(spawnMasks[0], random.nextLong(), "spawnsLand");
        spawnPlateauMask = new ConcurrentBinaryMask(spawnMasks[1], random.nextLong(), "spawnsPlateau");

        setupPipeline();

        random = null;
        Pipeline.start();

        CompletableFuture<Void> aiMarkerFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(passable, passableLand, passableWater);
            long sTime = System.currentTimeMillis();
            CompletableFuture<Void> AmphibiousMarkers = CompletableFuture.runAsync(() -> AIMarkerGenerator.generateAIMarkers(passable.getFinalMask(), map.getAmphibiousAIMarkers(), "AmphPN%d"));
            CompletableFuture<Void> LandMarkers = CompletableFuture.runAsync(() -> AIMarkerGenerator.generateAIMarkers(passableLand.getFinalMask(), map.getLandAIMarkers(), "LandPN%d"));
            CompletableFuture<Void> NavyMarkers = CompletableFuture.runAsync(() -> AIMarkerGenerator.generateAIMarkers(passableWater.getFinalMask(), map.getNavyAIMarkers(), "NavyPN%d"));
            CompletableFuture<Void> AirMarkers = CompletableFuture.runAsync(() -> AIMarkerGenerator.generateAirAIMarkers(map));
            AmphibiousMarkers.join();
            LandMarkers.join();
            NavyMarkers.join();
            AirMarkers.join();
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateAIMarkers\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });


        CompletableFuture<Void> textureFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(accentGroundTexture, accentPlateauTexture, slopesTexture, accentSlopesTexture, steepHillsTexture, waterBeachTexture, rockTexture, accentRockTexture);
            long sTime = System.currentTimeMillis();
            map.setTextureMasksLowScaled(accentGroundTexture.getFinalMask(), accentPlateauTexture.getFinalMask(), slopesTexture.getFinalMask(), accentSlopesTexture.getFinalMask());
            map.setTextureMasksHighScaled(steepHillsTexture.getFinalMask(), waterBeachTexture.getFinalMask(), rockTexture.getFinalMask(), accentRockTexture.getFinalMask());
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateTextures\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> resourcesFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(resourceMask, plateaus, land, ramps, impassable, unbuildable, allWreckMask, waterResourceMask);
            long sTime = System.currentTimeMillis();
            mexGenerator.generateMexes(resourceMask.getFinalMask(), waterResourceMask.getFinalMask());
            hydroGenerator.generateHydros(resourceMask.getFinalMask().deflate(4));
            generateExclusionMasks();
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateResources\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> decalsFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(fieldDecal, slopeDecal, mountainDecal);
            long sTime = System.currentTimeMillis();
            decalGenerator.generateDecals(fieldDecal.getFinalMask(), biome.getDecalMaterials().getFieldNormals(), 32, 32, 32, 64);
            decalGenerator.generateDecals(fieldDecal.getFinalMask(), biome.getDecalMaterials().getFieldAlbedos(), 64, 128, 24, 48);
            decalGenerator.generateDecals(slopeDecal.getFinalMask(), biome.getDecalMaterials().getSlopeNormals(), 16, 32, 16, 32);
            decalGenerator.generateDecals(slopeDecal.getFinalMask(), biome.getDecalMaterials().getSlopeAlbedos(), 64, 128, 32, 48);
            decalGenerator.generateDecals(mountainDecal.getFinalMask(), biome.getDecalMaterials().getMountainNormals(), 32, 32, 32, 64);
            decalGenerator.generateDecals(mountainDecal.getFinalMask(), biome.getDecalMaterials().getMountainAlbedos(), 64, 128, 16, 24);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateDecals\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        resourcesFuture.join();

        CompletableFuture<Void> propsFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(treeMask, cliffRockMask, largeRockFieldMask, fieldStoneMask);
            long sTime = System.currentTimeMillis();
            propGenerator.generateProps(treeMask.getFinalMask().minus(noProps), biome.getPropMaterials().getTreeGroups(), 3f, 7f);
            propGenerator.generateProps(cliffRockMask.getFinalMask().minus(noProps), biome.getPropMaterials().getRocks(), .5f, 3f);
            BinaryMask noPropsInflated = noProps.copy().inflate(16);
            propGenerator.generateProps(largeRockFieldMask.getFinalMask().minus(noPropsInflated), biome.getPropMaterials().getRocks(), .5f, 3.5f);
            propGenerator.generateProps(smallRockFieldMask.getFinalMask().minus(noPropsInflated), biome.getPropMaterials().getRocks(), .5f, 3.5f);
            propGenerator.generateProps(fieldStoneMask.getFinalMask().minus(noProps), biome.getPropMaterials().getBoulders(), 30f);
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, generateProps\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        CompletableFuture<Void> unitsFuture = CompletableFuture.runAsync(() -> {
            if (!unexplored) {
                Pipeline.await(baseMask, civReclaimMask, t1LandWreckMask, t2LandWreckMask, t3LandWreckMask, t2NavyWreckMask, navyFactoryWreckMask);
                long sTime = System.currentTimeMillis();
                Army army17 = new Army("ARMY_17", new ArrayList<>());
                Group army17Initial = new Group("INITIAL", new ArrayList<>());
                Group army17Wreckage = new Group("WRECKAGE", new ArrayList<>());
                army17.addGroup(army17Initial);
                army17.addGroup(army17Wreckage);
                Army civilian = new Army("NEUTRAL_CIVILIAN", new ArrayList<>());
                Group civilianInitial = new Group("INITIAL", new ArrayList<>());
                civilian.addGroup(civilianInitial);
                map.addArmy(army17);
                map.addArmy(civilian);
                try {
                    unitGenerator.generateBases(baseMask.getFinalMask().minus(noBases), UnitGenerator.MEDIUM_ENEMY, army17, army17Initial, 512f);
                    unitGenerator.generateBases(civReclaimMask.getFinalMask().minus(noCivs), UnitGenerator.MEDIUM_RECLAIM, civilian, civilianInitial, 256f);
                } catch (IOException e) {
                    generationComplete = false;
                    System.out.println("Could not generate bases due to lua parsing error");
                    e.printStackTrace();
                }
                unitGenerator.generateUnits(t1LandWreckMask.getFinalMask().minus(noWrecks), UnitGenerator.T1_Land, army17, army17Wreckage, 1f, 4f);
                unitGenerator.generateUnits(t2LandWreckMask.getFinalMask().minus(noWrecks), UnitGenerator.T2_Land, army17, army17Wreckage, 30f);
                unitGenerator.generateUnits(t3LandWreckMask.getFinalMask().minus(noWrecks), UnitGenerator.T3_Land, army17, army17Wreckage, 128f);
                unitGenerator.generateUnits(t2NavyWreckMask.getFinalMask().minus(noWrecks), UnitGenerator.T2_Navy, army17, army17Wreckage, 128f);
                unitGenerator.generateUnits(navyFactoryWreckMask.getFinalMask().minus(noWrecks), UnitGenerator.Navy_Factory, army17, army17Wreckage, 256f);
                if (DEBUG) {
                    System.out.printf("Done: %4d ms, %s, generateBases\n",
                            System.currentTimeMillis() - sTime,
                            Util.getStackTraceLineInClass(MapGenerator.class));
                }
            }
        });

        CompletableFuture<Void> heightMapFuture = CompletableFuture.runAsync(() -> {
            Pipeline.await(heightmapBase);
            long sTime = System.currentTimeMillis();
            map.setHeightImage(heightmapBase.getFinalMask());
            map.getHeightmap().getRaster().setPixel(0, 0, new int[]{0});
            if (DEBUG) {
                System.out.printf("Done: %4d ms, %s, setHeightmap\n",
                        System.currentTimeMillis() - sTime,
                        Util.getStackTraceLineInClass(MapGenerator.class));
            }
        });

        propsFuture.join();
        decalsFuture.join();
        aiMarkerFuture.join();
        heightMapFuture.join();
        unitsFuture.join();

        CompletableFuture<Void> placementFuture = CompletableFuture.runAsync(() -> {
            long sTime = System.currentTimeMillis();
            map.setHeights();
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
        map.setGeneratePreview(!blind);
        map.setUnexplored(unexplored);
        if (unexplored) {
            map.setCartographicContourInterval(100);
            map.setCartographicDeepWaterColor(1);
            map.setCartographicMapContourColor(1);
            map.setCartographicMapShoreColor(1);
            map.setCartographicMapLandStartColor(1);
            map.setCartographicMapLandEndColor(1);
        }
        if (!blind) {
            PreviewGenerator.generatePreview(map);
        } else {
            BufferedImage blindPreview = readImage(BLANK_PREVIEW);
            map.getPreview().setData(blindPreview.getData());
        }
        StringBuilder descriptionBuilder = new StringBuilder();
        if (tournamentStyle) {
            descriptionBuilder.append(String.format("Map originally generated at %s UTC. ",
                    DateTimeFormatter.ofPattern("HH:mm:ss dd MMM uuuu")
                            .format(Instant.ofEpochSecond(generationTime).atZone(ZoneOffset.UTC))));
        }
        if (unexplored) {
            descriptionBuilder.append("Use with the Unexplored Maps Mod for best experience");
        }
        map.setDescription(descriptionBuilder.toString());
        if (DEBUG) {
            System.out.printf("Done: %4d ms, %s, generatePreview\n",
                    System.currentTimeMillis() - sTime,
                    Util.getStackTraceLineInClass(MapGenerator.class));
        }
        ScriptGenerator.generateScript(map);

        System.out.printf("Map generation done: %d ms\n", System.currentTimeMillis() - startTime);

        map.addBlank(new Marker(mapName, new Vector2f(0, 0)));
        map.addDecalGroup(new DecalGroup(mapName, new int[0]));

        if (!generationComplete) {
            map = null;
        }

        if (generateBrushMaps) {
            map.setDescription(map.getDescription() + " - Used brush " + brush1);
        }

        return map;
    }

    public void prepareForNextMap() {
        if (numToGenerate > mapNumber) {
            Pipeline.reset();
            mapNumber += 1;
            System.out.println("Finished map " + mapNumber);
            seed = new Random().nextLong();
        }
    }

    private void setupPipeline() {
        setupTerrainPipeline();
        setupHeightmapPipeline();
        setupTexturePipeline();
        setupPropPipeline();
        setupWreckPipeline();
        setupResourcePipeline();
        setupDecalPipeline();
    }

    private void setupTerrainPipeline() {
        boolean landPathed = false;
        if (landDensity >= .75f && random.nextBoolean() && random.nextFloat() < mountainDensity) {
            allLandInit();
            inversePathMountainInit();
        } else {
            if (RandomUtils.andRandomBoolean(random, 2) && random.nextFloat() > landDensity) {
                pathLandInit();
                landPathed = true;
            } else {
                smoothLandInit();
            }
            walkMountainInit();
        }

        pathPlateauInit();

        spawnPlateauMask.setSize(mapSize / 4).erode(.5f, SymmetryType.SPAWN, 4).grow(.5f, SymmetryType.SPAWN, 16);
        spawnPlateauMask.erode(.5f, SymmetryType.SPAWN).setSize(mapSize + 1).smooth(4);

        spawnLandMask.setSize(mapSize / 4).erode(.25f, SymmetryType.SPAWN, mapSize / 128).grow(.5f, SymmetryType.SPAWN, 12);
        spawnLandMask.erode(.5f, SymmetryType.SPAWN).setSize(mapSize + 1).smooth(4);

        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(spawnLandMask).combine(spawnPlateauMask);

        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(spawnLandMask).combine(spawnPlateauMask);
        if (!landPathed && mapSize > 512) {
            land.combine(spawnLandMask).combine(spawnPlateauMask).setSize(mapSize / 4)
                    .erode(.5f, SymmetryType.SPAWN, 20).setSize(mapSize + 1).smooth(8);
        } else if (!landPathed) {
            land.grow(.5f, SymmetryType.SPAWN, 16).smooth(2);
        }

        mountains.minus(spawnLandMask);

        plateaus.intersect(land).minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(plateaus).combine(spawnLandMask).combine(spawnPlateauMask);

        mountains.smooth(8, .75f);
        mountains.intersect(landPathed || landDensity < .25f ? land.copy().deflate(24) : land);
    }

    private void allLandInit() {
        land = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "land").invert();
    }

    private void smoothLandInit() {
        float scaledLandDensity = landDensity * LAND_DENSITY_RANGE + LAND_DENSITY_MIN;
        land = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetrySettings, "land");

        land.randomize(scaledLandDensity).smooth(2, .75f).erode(.5f, SymmetryType.TERRAIN, mapSize / 256);
        land.setSize(mapSize / 4).grow(.5f, SymmetryType.TERRAIN, mapSize / 128);
        land.setSize(mapSize + 1).smooth(8, .75f);
    }

    private void pathLandInit() {
        float maxStepSize = mapSize / 128f;
        int maxMiddlePoints = 8;
        int numWalkersPerPlayer = 2;
        int numWalkers = (int) (8 * landDensity + 8) / symmetrySettings.getSpawnSymmetry().getNumSymPoints();
        int bound = (int) (mapSize / 24 * (5 * (random.nextFloat() + (1 - landDensity)) / 2f + 1));
        land = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "land");

        map.getSpawns().forEach(spawn -> {
            for (int i = 0; i < numWalkersPerPlayer; i++) {
                Vector2f start = new Vector2f(spawn.getPosition());
                Vector2f end = new Vector2f(random.nextInt(mapSize + 1 - bound * 2) + bound, random.nextInt(mapSize + 1 - bound * 2) + bound);
                int numMiddlePoints = random.nextInt(maxMiddlePoints);
                land.path(start, end, maxStepSize, numMiddlePoints, SymmetryType.TERRAIN);
            }
        });

        pathInBounds(land, maxStepSize, numWalkers, maxMiddlePoints, bound);
        land.inflate(mapSize / 256f).setSize(mapSize / 4).grow(.5f, SymmetryType.TERRAIN, 4).setSize(mapSize + 1).smooth(6);
    }

    private void pathPlateauInit() {
        float maxStepSize = mapSize / 128f;
        int maxMiddlePoints = 16;
        int numPaths = (int) (12 * plateauDensity) / symmetrySettings.getSpawnSymmetry().getNumSymPoints();
        int bound = 0;
        plateaus = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "plateaus");

        pathInBounds(plateaus, maxStepSize, numPaths, maxMiddlePoints, bound);
        plateaus.inflate(mapSize / 256f).setSize(mapSize / 4).grow(.5f, SymmetryType.TERRAIN, 4).smooth(4).setSize(mapSize + 1).smooth(12);
    }

    private void walkMountainInit() {
        float scaledMountainDensity = mountainDensity * MOUNTAIN_DENSITY_RANGE + MOUNTAIN_DENSITY_MIN;
        if (mapSize < 512) {
            scaledMountainDensity = StrictMath.max(scaledMountainDensity - .25f, 0);
        }

        mountains = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "mountains");

        if (random.nextBoolean()) {
            mountains.progressiveWalk((int) (scaledMountainDensity * 100 / terrainSymmetry.getNumSymPoints()), mapSize / 16);
        } else {
            mountains.randomWalk((int) (scaledMountainDensity * 100 / terrainSymmetry.getNumSymPoints()), mapSize / 16);
        }
        mountains.setSize(mapSize / 4).erode(.5f, SymmetryType.TERRAIN, 4).grow(.5f, SymmetryType.TERRAIN, 6);
        mountains.setSize(mapSize + 1);


        if (mountainDensity > .5f) {
            float maxStepSize = mapSize / 256f;
            int maxMiddlePoints = 8;
            ConcurrentBinaryMask connections = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "connections");

            connectSpawns(connections, maxMiddlePoints, 1, maxStepSize);

            connections.grow(.5f, SymmetryType.SPAWN, 12).smooth(6);

            mountains.minus(connections);
        }
    }

    private void inversePathMountainInit() {
        float maxStepSize = mapSize / 128f;
        int maxMiddlePoints = 8;
        int numPaths = (int) (8 + 8 * (1 - mountainDensity) / symmetrySettings.getTerrainSymmetry().getNumSymPoints());
        int bound = (int) (mapSize / 16 * (random.nextFloat() + mountainDensity) / 2f);
        mountains = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "mountains");
        ConcurrentBinaryMask connections = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "connections");

        connectSpawns(connections, maxMiddlePoints, 2, maxStepSize);
        pathInBounds(connections, maxStepSize, numPaths, maxMiddlePoints, bound);
        connections.grow(.5f, SymmetryType.TERRAIN, 24);

        mountains.invert().minus(connections);
    }

    private void initRamps() {
        float maxStepSize = mapSize / 128f;
        float distanceThreshold = maxStepSize / 2f;
        int maxMiddlePoints = 4;
        int numPathsPerPlayer = (int) (rampDensity * 4 + 4) / symmetrySettings.getSpawnSymmetry().getNumSymPoints() + 1;
        int numPaths = (int) (rampDensity * 20 + 4 + 8 * plateauDensity) / symmetrySettings.getTerrainSymmetry().getNumSymPoints() + spawnCount;
        int bound = mapSize / 4;
        ramps = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "ramps");
        map.getSpawns().forEach(spawn -> {
            for (int i = 0; i < numPathsPerPlayer; i++) {
                Vector2f start = new Vector2f(spawn.getPosition());
                Vector2f end = new Vector2f(random.nextInt(mapSize / 2) + start.getX() - mapSize / 4f,
                        random.nextInt(mapSize / 2) + start.getY() - mapSize / 4f);
                int numMiddlePoints = random.nextInt(maxMiddlePoints) + 2;
                ramps.path(start, end, maxStepSize, numMiddlePoints, SymmetryType.SPAWN);
            }
        });
        for (int i = 0; i < numPaths; i++) {
            Vector2f start = new Vector2f(random.nextInt(mapSize + 1), random.nextInt(mapSize + 1));
            Vector2f end = new Vector2f(random.nextInt(bound * 2) - bound + start.getX(), random.nextInt(bound * 2) - bound + start.getY());
            int numMiddlePoints = random.nextInt(maxMiddlePoints);
            ramps.path(start, end, maxStepSize, numMiddlePoints, SymmetryType.TERRAIN);
        }
        ramps.inflate(distanceThreshold).intersect(plateaus.copy().outline())
                .minus(mountains.copy().inflate(16)).inflate(12).smooth(8, .5f);
    }

    private void connectSpawns(ConcurrentBinaryMask maskToUse, int maxMiddlePoints, int numConnections, float maxStepSize) {
        map.getSpawns().forEach(startSpawn -> {
            for (int i = 0; i < numConnections; ++i) {
                ArrayList<Spawn> otherSpawns = new ArrayList<>(map.getSpawns());
                otherSpawns.remove(startSpawn);
                Spawn endSpawn = otherSpawns.get(random.nextInt(otherSpawns.size()));
                Vector2f start = new Vector2f(startSpawn.getPosition());
                Vector2f end = new Vector2f(endSpawn.getPosition());
                int numMiddlePoints = random.nextInt(maxMiddlePoints);
                maskToUse.path(start, end, maxStepSize, numMiddlePoints, SymmetryType.TERRAIN);
            }
        });
    }

    private void pathInBounds(ConcurrentBinaryMask maskToUse, float maxStepSize, int numPaths, int maxMiddlePoints, int bound) {
        for (int i = 0; i < numPaths; i++) {
            Vector2f start = new Vector2f(random.nextInt(mapSize + 1 - bound * 2) + bound, random.nextInt(mapSize + 1 - bound * 2) + bound);
            Vector2f end = new Vector2f(random.nextInt(mapSize + 1 - bound * 2) + bound, random.nextInt(mapSize + 1 - bound * 2) + bound);
            int numMiddlePoints = random.nextInt(maxMiddlePoints);
            maskToUse.path(start, end, maxStepSize, numMiddlePoints, SymmetryType.TERRAIN);
        }
    }

    private void setupHeightmapPipeline() {
        int numSymPoints = symmetrySettings.getSpawnSymmetry().getNumSymPoints();
        int numBrushes = Brushes.GENERATOR_BRUSHES.size();

        if (generateBrushMaps) {
            brush1 = Brushes.GENERATOR_BRUSHES.get(mapNumber);
            brush2 = brush1;
            brush3 = brush1;
            brush4 = brush1;
            brush5 = brush1;
        } else {
            if (!Brushes.GENERATOR_BRUSHES.contains(brush1)) {
                brush1 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));
            }
            if (!Brushes.GENERATOR_BRUSHES.contains(brush2)) {
                brush2 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));
            }
            if (!Brushes.GENERATOR_BRUSHES.contains(brush3)) {
                brush3 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));
            }
            if (!Brushes.GENERATOR_BRUSHES.contains(brush4)) {
                brush4 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));
            }
            if (!Brushes.GENERATOR_BRUSHES.contains(brush5)) {
                brush5 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));
            }
        }

        heightmapBase = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapBase");
        ConcurrentFloatMask heightmapValleys = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapValleys");
        ConcurrentFloatMask heightmapHills = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapHills");
        ConcurrentFloatMask heightmapPlateaus = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapPlateaus");
        ConcurrentFloatMask heightmapMountains = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapMountains");
        ConcurrentFloatMask heightmapLand = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapLand");
        ConcurrentFloatMask heightmapOcean = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "heightmapOcean");
        ConcurrentFloatMask noise = new ConcurrentFloatMask(mapSize / 128, random.nextLong(), symmetrySettings, "noise");

        heightmapMountains.useBrushWithinAreaWithDensity(mountains, brush3, 64, .05f, 14f);

        ConcurrentBinaryMask paintedMountains = new ConcurrentBinaryMask(heightmapMountains, PLATEAU_HEIGHT / 2, random.nextLong(), "paintedMountains");

        mountains.replace(paintedMountains);
        land.combine(paintedMountains);

        heightmapMountains.smooth(4, mountains.copy().inflate(32).minus(mountains.copy().inflate(4)));

        heightmapPlateaus.useBrushWithinAreaWithDensity(plateaus, brush1, 32, .64f, 8f).clampMax(PLATEAU_HEIGHT);

        ConcurrentBinaryMask paintedPlateaus = new ConcurrentBinaryMask(heightmapPlateaus, PLATEAU_HEIGHT - 1f, random.nextLong(), "paintedPlateaus");

        land.combine(paintedPlateaus);
        plateaus.replace(paintedPlateaus);

        heightmapPlateaus.smooth(1, plateaus);

        plateaus.combine(spawnLandMask).combine(spawnPlateauMask);

        hills = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "hills");
        valleys = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "valleys");

        hills.randomWalk(random.nextInt(4) + 1, random.nextInt(mapSize / 2) / numSymPoints).grow(.5f, SymmetryType.SPAWN, 2)
                .setSize(mapSize + 1).intersect(land.copy().deflate(8)).minus(plateaus.copy().outline().inflate(8)).minus(spawnLandMask);
        valleys.randomWalk(random.nextInt(4), random.nextInt(mapSize / 2) / numSymPoints).grow(.5f, SymmetryType.SPAWN, 4)
                .setSize(mapSize + 1).intersect(plateaus.copy().deflate(8)).minus(spawnPlateauMask);

        heightmapValleys.useBrushWithinAreaWithDensity(valleys, brush2, 24, .72f, -0.35f)
                .clampMin(VALLEY_FLOOR);
        heightmapHills.useBrushWithinAreaWithDensity(hills.combine(mountains.copy().outline().inflate(4).acid(.01f, 4)), brush4, 24, .72f, 0.5f);

        initRamps();

        ConcurrentBinaryMask water = land.copy().invert();
        ConcurrentBinaryMask deepWater = water.copy().deflate(32);

        heightmapOcean.addDistance(land, -.45f).clampMin(OCEAN_FLOOR).useBrushWithinAreaWithDensity(water.minus(deepWater), brush5, 16, 1f, .5f)
                .useBrushWithinAreaWithDensity(deepWater, brush5, 64, .065f, 1f).clampMax(0).smooth(4, deepWater);

        heightmapLand.add(heightmapHills).add(heightmapValleys).add(heightmapMountains).add(LAND_HEIGHT)
                .setValueInArea(LAND_HEIGHT, spawnLandMask).add(heightmapPlateaus).setValueInArea(PLATEAU_HEIGHT + LAND_HEIGHT, spawnPlateauMask)
                .smooth(1, spawnPlateauMask.copy().inflate(4)).smooth(18, ramps.copy().acid(.001f, 4).erode(.25f, SymmetryType.SPAWN, 4)).smooth(12, ramps.copy().inflate(8).acid(.01f, 4).erode(.25f, SymmetryType.SPAWN, 4))
                .smooth(6, ramps.copy().inflate(12)).smooth(2, ramps.copy().inflate(16));

        heightmapBase.add(heightmapOcean).smooth(1).add(heightmapLand);

        noise.addWhiteNoise(PLATEAU_HEIGHT).resample(mapSize / 64).addWhiteNoise(PLATEAU_HEIGHT).resample(mapSize + 1)
                .subtractAvg().clampMin(0f).setValueInArea(0f, land.copy().invert()).smooth(8);

        heightmapBase.add(waterHeight).add(noise).clampMin(0f).clampMax(256f);

        ConcurrentBinaryMask paintedLand = new ConcurrentBinaryMask(heightmapBase, waterHeight, random.nextLong(), "paintedLand");

        land.replace(paintedLand);

        slope = heightmapBase.copy().supcomGradient();

        impassable = new ConcurrentBinaryMask(slope, .75f, random.nextLong(), "impassable");
        unbuildable = new ConcurrentBinaryMask(slope, .2f, random.nextLong(), "unbuildable");
        notFlat = new ConcurrentBinaryMask(slope, .05f, random.nextLong(), "notFlat");

        unbuildable.combine(ramps.copy().intersect(notFlat));
        impassable.inflate(2).combine(paintedMountains);

        passable = new ConcurrentBinaryMask(impassable, random.nextLong(), "passable").invert();
        passableLand = new ConcurrentBinaryMask(land, random.nextLong(), "passableLand");
        passableWater = new ConcurrentBinaryMask(land, random.nextLong(), "passableWater").invert();

        passable.fillEdge(8, false);
        passableLand.intersect(passable);
        passableWater.deflate(16).fillEdge(8, false);
    }

    private void setupDecalPipeline() {
        fieldDecal = new ConcurrentBinaryMask(land, random.nextLong(), "fieldDecal");
        slopeDecal = new ConcurrentBinaryMask(slope, .15f, random.nextLong(), "slopeDecal");
        mountainDecal = new ConcurrentBinaryMask(mountains, random.nextLong(), "mountainDecal");

        fieldDecal.minus(slopeDecal).minus(mountainDecal).deflate(24);
    }

    private void setupResourcePipeline() {
        resourceMask = new ConcurrentBinaryMask(land, random.nextLong(), "resource");
        waterResourceMask = new ConcurrentBinaryMask(land, random.nextLong(), "waterResource").invert();

        resourceMask.minus(unbuildable).deflate(4);
        resourceMask.fillEdge(16, false).fillCenter(24, false);
        waterResourceMask.minus(unbuildable).deflate(8).fillEdge(16, false).fillCenter(24, false);
    }

    private void setupTexturePipeline() {
        ConcurrentBinaryMask flat = new ConcurrentBinaryMask(slope, .05f, random.nextLong(), "flat").invert();
        ConcurrentBinaryMask highGround = new ConcurrentBinaryMask(heightmapBase, waterHeight + PLATEAU_HEIGHT * 3 / 4f, random.nextLong(), "highGround");
        ConcurrentBinaryMask accentGround = new ConcurrentBinaryMask(land, random.nextLong(), "accentGround");
        ConcurrentBinaryMask accentPlateau = new ConcurrentBinaryMask(plateaus, random.nextLong(), "accentPlateau");
        ConcurrentBinaryMask slopes = new ConcurrentBinaryMask(slope, .15f, random.nextLong(), "slopes");
        ConcurrentBinaryMask accentSlopes = new ConcurrentBinaryMask(slope, .55f, random.nextLong(), "accentSlopes").invert();
        ConcurrentBinaryMask steepHills = new ConcurrentBinaryMask(slope, .55f, random.nextLong(), "steepHills");
        ConcurrentBinaryMask rock = new ConcurrentBinaryMask(slope, .75f, random.nextLong(), "rock");
        ConcurrentBinaryMask accentRock = new ConcurrentBinaryMask(slope, .75f, random.nextLong(), "accentRock");
        waterBeachTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "waterBeachTexture");
        accentGroundTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "accentGroundTexture");
        accentPlateauTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "accentPlateauTexture");
        slopesTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "slopesTexture");
        accentSlopesTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "accentSlopesTexture");
        steepHillsTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "steepHillsTexture");
        rockTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "rockTexture");
        accentRockTexture = new ConcurrentFloatMask(mapSize + 1, random.nextLong(), symmetrySettings, "accentRockTexture");

        accentGround.minus(highGround).acid(.1f, 0).erode(.4f, SymmetryType.SPAWN).smooth(6, .75f);
        accentPlateau.acid(.1f, 0).erode(.4f, SymmetryType.SPAWN).smooth(6, .75f);
        slopes.intersect(land).flipValues(.95f).erode(.5f, SymmetryType.SPAWN).acid(.3f, 0).erode(.2f, SymmetryType.SPAWN);
        accentSlopes.minus(flat).intersect(land).acid(.1f, 0).erode(.5f, SymmetryType.SPAWN).smooth(4, .75f).acid(.55f, 0);
        steepHills.acid(.3f, 0).erode(.2f, SymmetryType.SPAWN);
        accentRock.acid(.2f, 0).erode(.3f, SymmetryType.SPAWN).acid(.2f, 0).smooth(2, .5f).intersect(rock);

        accentGroundTexture.init(accentGround, 0, .5f).smooth(12).add(accentGround, .325f).smooth(8).add(accentGround, .25f).clampMax(1f).smooth(2);
        accentPlateauTexture.init(accentPlateau, 0, .5f).smooth(12).add(accentPlateau, .325f).smooth(8).add(accentPlateau, .25f).clampMax(1f).smooth(2);
        slopesTexture.init(slopes, 0, 1).smooth(8).add(slopes, .75f).smooth(4).clampMax(1f);
        accentSlopesTexture.init(accentSlopes, 0, 1).smooth(8).add(accentSlopes, .65f).smooth(4).add(accentSlopes, .5f).smooth(1).clampMax(1f);
        steepHillsTexture.init(steepHills, 0, 1).smooth(8).clampMax(0.35f).add(steepHills, .65f).smooth(4).clampMax(0.65f).add(steepHills, .5f).smooth(1).clampMax(1f);
        waterBeachTexture.init(land.copy().invert().inflate(12).minus(plateaus.copy().minus(ramps)), 0, 1).smooth(12);
        rockTexture.init(rock, 0, 1f).smooth(4).add(rock, 1f).smooth(2).clampMax(1f);
        accentRockTexture.init(accentRock, 0, 1f).smooth(4).clampMax(1f);
    }

    private void setupPropPipeline() {
        baseMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "base");
        civReclaimMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "civReclaim");
        allBaseMask = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "allBase");
        treeMask = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetrySettings, "tree");
        cliffRockMask = new ConcurrentBinaryMask(mapSize / 16, random.nextLong(), symmetrySettings, "cliffRock");
        fieldStoneMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "fieldStone");
        largeRockFieldMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "largeRockField");
        smallRockFieldMask = new ConcurrentBinaryMask(mapSize / 4, random.nextLong(), symmetrySettings, "smallRockField");

        if (hasCivilians) {
            if (!enemyCivilians) {
                baseMask.setSize(mapSize + 1);
                civReclaimMask.randomize(.005f).setSize(mapSize + 1).intersect(land.copy().minus(unbuildable).minus(ramps).deflate(24)).fillCenter(32, false).fillEdge(64, false);
            } else {
                civReclaimMask.setSize(mapSize + 1);
                baseMask.randomize(.005f).setSize(mapSize + 1).intersect(land.copy().minus(unbuildable).minus(ramps).deflate(24)).fillCenter(32, false).fillEdge(32, false).minus(civReclaimMask.copy().inflate(16));
            }
        } else {
            civReclaimMask.setSize(mapSize + 1);
            baseMask.setSize(mapSize + 1);
        }
        allBaseMask.combine(baseMask.copy().inflate(24)).combine(civReclaimMask.copy().inflate(24));

        cliffRockMask.randomize(reclaimDensity * .5f + .1f).setSize(mapSize + 1).intersect(impassable).grow(.5f, SymmetryType.SPAWN, 6).minus(plateaus.copy().outline().inflate(2)).minus(impassable).intersect(land);
        fieldStoneMask.randomize(reclaimDensity * .001f).setSize(mapSize + 1).intersect(land).minus(impassable).fillEdge(10, false);
        treeMask.randomize(reclaimDensity * .2f + .1f).setSize(mapSize / 4).inflate(2).erode(.5f, SymmetryType.SPAWN).erode(.5f, SymmetryType.SPAWN);
        treeMask.setSize(mapSize + 1).intersect(land.copy().deflate(8)).minus(impassable.copy().inflate(2)).deflate(2).fillEdge(8, false).minus(notFlat);
        largeRockFieldMask.randomize(reclaimDensity * .001f).fillEdge(32, false).grow(.5f, SymmetryType.SPAWN, 8).setSize(mapSize + 1).minus(notFlat).intersect(land).minus(impassable);
        smallRockFieldMask.randomize(reclaimDensity * .0025f).fillEdge(16, false).grow(.5f, SymmetryType.SPAWN, 4).setSize(mapSize + 1).minus(notFlat).intersect(land).minus(impassable);
    }

    private void setupWreckPipeline() {
        t1LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetrySettings, "t1LandWreck");
        t2LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetrySettings, "t2LandWreck");
        t3LandWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetrySettings, "t3LandWreck");
        t2NavyWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetrySettings, "t2NavyWreck");
        navyFactoryWreckMask = new ConcurrentBinaryMask(mapSize / 8, random.nextLong(), symmetrySettings, "navyFactoryWreck");
        allWreckMask = new ConcurrentBinaryMask(mapSize + 1, random.nextLong(), symmetrySettings, "allWreck");

        t1LandWreckMask.randomize(reclaimDensity * .0025f).setSize(mapSize + 1).intersect(land).inflate(1).minus(impassable).fillEdge(20, false);
        t2LandWreckMask.randomize(reclaimDensity * .002f).setSize(mapSize + 1).intersect(land).minus(impassable).minus(t1LandWreckMask).fillEdge(64, false);
        t3LandWreckMask.randomize(reclaimDensity * .0004f).setSize(mapSize + 1).intersect(land).minus(impassable).minus(t1LandWreckMask).minus(t2LandWreckMask).fillEdge(mapSize / 8, false);
        navyFactoryWreckMask.randomize(reclaimDensity * .005f).setSize(mapSize + 1).minus(land.copy().inflate(16)).fillEdge(20, false).fillCenter(32, false);
        t2NavyWreckMask.randomize(reclaimDensity * .005f).setSize(mapSize + 1).intersect(land.copy().inflate(4).outline()).fillEdge(20, false);
        allWreckMask.combine(t1LandWreckMask).combine(t2LandWreckMask).combine(t3LandWreckMask).combine(t2NavyWreckMask).inflate(2);
    }

    private void generateExclusionMasks() {
        noProps = new BinaryMask(unbuildable.getFinalMask(), null);
        noBases = new BinaryMask(unbuildable.getFinalMask(), null);
        noCivs = new BinaryMask(unbuildable.getFinalMask(), null);
        noWrecks = new BinaryMask(unbuildable.getFinalMask(), null);

        noProps.combine(allBaseMask.getFinalMask());
        noWrecks.combine(allBaseMask.getFinalMask()).fillCenter(16, true);

        map.getSpawns().forEach(spawn -> {
            noProps.fillCircle(spawn.getPosition(), 30, true);
            noBases.fillCircle(spawn.getPosition(), 128, true);
            noCivs.fillCircle(spawn.getPosition(), 96, true);
            noWrecks.fillCircle(spawn.getPosition(), 128, true);
        });
        map.getMexes().forEach(mex -> {
            noProps.fillCircle(mex.getPosition(), 1, true);
            noBases.fillCircle(mex.getPosition(), 32, true);
            noCivs.fillCircle(mex.getPosition(), 32, true);
            noWrecks.fillCircle(mex.getPosition(), 8, true);
        });
        map.getHydros().forEach(hydro -> {
            noProps.fillCircle(hydro.getPosition(), 8, true);
            noBases.fillCircle(hydro.getPosition(), 32, true);
            noCivs.fillCircle(hydro.getPosition(), 32, true);
            noWrecks.fillCircle(hydro.getPosition(), 32, true);
        });
    }

    public void toFile(Path path) throws IOException {

        Files.deleteIfExists(path);
        File outFile = path.toFile();
        boolean status = outFile.createNewFile();
        if (status) {
            FileOutputStream out = new FileOutputStream(outFile);
            String summaryString = "Seed: " + seed +
                    "\nBiome: " + biome.getName() +
                    "\nLand Density: " + landDensity +
                    "\nPlateau Density: " + plateauDensity +
                    "\nMountain Density: " + mountainDensity +
                    "\nRamp Density: " + rampDensity +
                    "\nReclaim Density: " + reclaimDensity +
                    "\nMex Count: " + mexCount +
                    "\nTerrain Symmetry: " + terrainSymmetry +
                    "\nTeam Symmetry: " + symmetrySettings.getTeamSymmetry() +
                    "\nSpawn Symmetry: " + symmetrySettings.getSpawnSymmetry();
            out.write(summaryString.getBytes());
            out.flush();
            out.close();
        }
    }
}
