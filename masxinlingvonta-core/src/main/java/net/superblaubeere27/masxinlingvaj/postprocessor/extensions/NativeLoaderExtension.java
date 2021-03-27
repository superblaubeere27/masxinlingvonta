package net.superblaubeere27.masxinlingvaj.postprocessor.extensions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class NativeLoaderExtension {

    /**
     * Handles native-loading for the finished jars
     */
    public static void loadNatives() throws IOException {
        boolean isX64 = System.getProperty("os.arch").contains("64");
        String lowerCase = System.getProperty("os.name").toLowerCase();

        String name = null;

        if (isX64) {
            if (lowerCase.contains("win")) {
                name = "mlv-win64.dll";
            }
            if (lowerCase.contains("linux")) {
                name = "mlv-linux64.dll";
            }
            if (lowerCase.contains("mac")) {
                name = "mlv-macosx.dll";
            }
        }

        if (name == null) {
            throw new Error("No natives found for " + lowerCase + (isX64 ? " x86-64" : "x86"));
        }

        var tmpFile = File.createTempFile("mlv_", ".tmp");

        try (
                InputStream inputStream = NativeLoaderExtension.class.getResourceAsStream("/META-INF/natives/" + name);
                FileOutputStream fos = new FileOutputStream(tmpFile)
        ) {
            byte[] buf = new byte[1024];
            int read;

            while ((read = inputStream.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
        } catch (IOException e) {
            throw new Error("Failed to load natives", e);
        }

        System.load(tmpFile.getAbsolutePath());
    }

}
