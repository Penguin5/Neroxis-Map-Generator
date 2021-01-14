package neroxis.exporter;

import neroxis.map.SCMap;

import java.io.*;
import java.nio.file.Path;

public strictfp class ScriptExporter {

    public static void exportScript(Path folderPath, SCMap map) throws IOException {
        File file = folderPath.resolve(map.getFilePrefix() + "_script.lua").toFile();
        boolean status = file.createNewFile();
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

        out.writeBytes(map.getScript());

        out.flush();
        out.close();
    }
}
