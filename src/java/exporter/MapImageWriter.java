package exporter;

import importer.SCMapImporter;
import importer.SaveImporter;
import map.*;
import util.ArgumentParser;
import util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public strictfp class MapImageWriter {

    public static boolean DEBUG = false;

    private Path inMapPath;
    private SCMap map;
    private boolean writeOnlySelectImages;
    private String writeImagesPath;
    private boolean writeLayer0;
    private boolean writeLayer1;
    private boolean writeLayer2;
    private boolean writeLayer3;
    private boolean writeLayer4;
    private boolean writeLayer5;
    private boolean writeLayer6;
    private boolean writeLayer7;
    private boolean writeLayer8;
    private boolean writeLayerh;

    private SymmetrySettings symmetrySettings;

    public static void main(String[] args) throws IOException {

        Locale.setDefault(Locale.US);
        if (DEBUG) {
            Path debugDir = Paths.get(".", "debug");
            FileUtils.deleteRecursiveIfExists(debugDir);
            Files.createDirectory(debugDir);
        }

        MapImageWriter mapImageWriter = new MapImageWriter();

        mapImageWriter.interpretArguments(args);

        System.out.println("Creating map image files at " + mapImageWriter.inMapPath);
        mapImageWriter.importMap();
        mapImageWriter.writeMapImages();
    }

    public void interpretArguments(String[] args) {
        interpretArguments(ArgumentParser.parse(args));
    }

    private void interpretArguments(Map<String, String> arguments) {
        if (arguments.containsKey("help")) {
            System.out.println("map-image-writer usage:\n" +
                    "--help                 produce help message\n" +
                    "--in-folder-path arg   required, set the input folder for the map - the images will appear in this folder\n" +
                    "--create-only arg      optional, create only arg limits which layers have PNG images created from them (0, 1, 2, 3, 4, 5, 6, 7, 8, h)\n" +
                    " - ie: to create all available PNG images except the one for layer 7, use: --create-only 01234568h\n" +
                    " - options 0 - 8 will create PNG's of the corresponding texture layers, and option h will create a PNG of the heightmap" +
                    "--debug                optional, turn on debugging options\n");
            System.exit(0);
        }

        if (arguments.containsKey("debug")) {
            DEBUG = true;
        }

        if (!arguments.containsKey("in-folder-path")) {
            System.out.println("Input Folder not Specified");
            System.exit(1);
        }

        inMapPath = Paths.get(arguments.get("in-folder-path"));
        writeImagesPath = arguments.get("in-folder-path");
        symmetrySettings = new SymmetrySettings(Symmetry.NONE, Symmetry.NONE, Symmetry.NONE);
        writeOnlySelectImages = arguments.containsKey("create-only");
        if (writeOnlySelectImages) {
            String whichImages = arguments.get("create-only");
            if (whichImages != null) {
                writeLayer0 = whichImages.contains("0");
                writeLayer1 = whichImages.contains("1");
                writeLayer2 = whichImages.contains("2");
                writeLayer3 = whichImages.contains("3");
                writeLayer4 = whichImages.contains("4");
                writeLayer5 = whichImages.contains("5");
                writeLayer6 = whichImages.contains("6");
                writeLayer7 = whichImages.contains("7");
                writeLayer8 = whichImages.contains("8");
                writeLayerh = whichImages.contains("h");
            }
        } else {
            writeLayer0 = true;
            writeLayer1 = true;
            writeLayer2 = true;
            writeLayer3 = true;
            writeLayer4 = true;
            writeLayer5 = true;
            writeLayer6 = true;
            writeLayer7 = true;
            writeLayer8 = true;
            writeLayerh = true;
        }
    }

    public void importMap() {
        try {
            File dir = inMapPath.toFile();

            File[] mapFiles = dir.listFiles((dir1, filename) -> filename.endsWith(".scmap"));
            if (mapFiles == null || mapFiles.length == 0) {
                System.out.println("No scmap file in map folder");
                return;
            }
            map = SCMapImporter.loadSCMAP(inMapPath);
            SaveImporter.importSave(inMapPath, map);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while importing the map.");
        }
    }

    public void writeMapImages() throws IOException {

        Random random = new Random();
        FloatMask heightmapBase = map.getHeightMask(symmetrySettings);
        heightmapBase = new FloatMask(heightmapBase, random.nextLong());
        heightmapBase.applySymmetry(SymmetryType.SPAWN);
        map.setHeightImage(heightmapBase);

        FloatMask[] texturesMasks = map.getTextureMasksScaled(symmetrySettings);
        FloatMask oldLayer1 = texturesMasks[0];
        FloatMask oldLayer2 = texturesMasks[1];
        FloatMask oldLayer3 = texturesMasks[2];
        FloatMask oldLayer4 = texturesMasks[3];
        FloatMask oldLayer5 = texturesMasks[4];
        FloatMask oldLayer6 = texturesMasks[5];
        FloatMask oldLayer7 = texturesMasks[6];
        FloatMask oldLayer8 = texturesMasks[7];
        if(writeLayerh) {
            util.ImageUtils.writePNGFromMask(heightmapBase, 1, Paths.get(writeImagesPath + "\\Heightmap.png"));
        }
        if(writeLayer1) {
            util.ImageUtils.writePNGFromMask(oldLayer1, 255, Paths.get(writeImagesPath + "\\Layer 1.png"));
        }
        if(writeLayer2) {
            util.ImageUtils.writePNGFromMask(oldLayer2, 255, Paths.get(writeImagesPath + "\\Layer 2.png"));
        }
        if(writeLayer3) {
            util.ImageUtils.writePNGFromMask(oldLayer3, 255, Paths.get(writeImagesPath + "\\Layer 3.png"));
        }
        if(writeLayer4) {
            util.ImageUtils.writePNGFromMask(oldLayer4, 255, Paths.get(writeImagesPath + "\\Layer 4.png"));
        }
        if(writeLayer5) {
            util.ImageUtils.writePNGFromMask(oldLayer5, 255, Paths.get(writeImagesPath + "\\Layer 5.png"));
        }
        if(writeLayer6) {
            util.ImageUtils.writePNGFromMask(oldLayer6, 255, Paths.get(writeImagesPath + "\\Layer 6.png"));
        }
        if(writeLayer7) {
            util.ImageUtils.writePNGFromMask(oldLayer7, 255, Paths.get(writeImagesPath + "\\Layer 7.png"));
        }
        if(writeLayer8) {
            util.ImageUtils.writePNGFromMask(oldLayer8, 255, Paths.get(writeImagesPath + "\\Layer 8.png"));
        }
        if(writeLayer0) {
            int mapImageSize = oldLayer1.getSize();
            oldLayer1.min(0f).max(1f).setSize(mapImageSize);
            oldLayer2.min(0f).max(1f).setSize(mapImageSize);
            oldLayer3.min(0f).max(1f).setSize(mapImageSize);
            oldLayer4.min(0f).max(1f).setSize(mapImageSize);
            oldLayer5.min(0f).max(1f).setSize(mapImageSize);
            oldLayer6.min(0f).max(1f).setSize(mapImageSize);
            oldLayer7.min(0f).max(1f).setSize(mapImageSize);
            oldLayer8.min(0f).max(1f).setSize(mapImageSize);
            FloatMask oldLayer0 = new FloatMask(mapImageSize, random.nextLong(), symmetrySettings);
            oldLayer0.init(new BinaryMask(mapImageSize, random.nextLong(), symmetrySettings).invert(), 0f, 1f).subtract(oldLayer8).subtract(oldLayer7).subtract(oldLayer6).subtract(oldLayer5).subtract(oldLayer4).subtract(oldLayer3).subtract(oldLayer2).subtract(oldLayer1).min(0f);
            util.ImageUtils.writePNGFromMask(oldLayer0, 255, Paths.get(writeImagesPath + "\\Layer 0.png"));
        }
    }
}