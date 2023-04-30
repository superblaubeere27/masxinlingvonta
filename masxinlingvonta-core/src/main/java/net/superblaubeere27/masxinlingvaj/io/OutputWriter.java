package net.superblaubeere27.masxinlingvaj.io;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class OutputWriter {

    public static HashMap<String, byte[]> encodeChangedClasses(MLVCompiler compiler, HashMap<String, byte[]> rawData) {
        var outputMap = new HashMap<String, byte[]>();

        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
            if (!aClass.getModifiedFlag()) {
                continue;
            }

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

            aClass.getClassNode().accept(classWriter);

            outputMap.put(aClass.getName() + ".class", classWriter.toByteArray());
        }

        for (Map.Entry<String, byte[]> entry : rawData.entrySet()) {
            outputMap.computeIfAbsent(entry.getKey(), e -> entry.getValue());
        }

        return outputMap;
    }

    public static void writeZipFile(OutputStream outputStream, HashMap<String, byte[]> data) throws IOException {
        var zipOutputStream = new ZipOutputStream(outputStream);

        for (Map.Entry<String, byte[]> stringEntry : data.entrySet()) {
            zipOutputStream.putNextEntry(new ZipEntry(stringEntry.getKey()));

            zipOutputStream.write(stringEntry.getValue());
            zipOutputStream.closeEntry();
        }

        zipOutputStream.close();
    }

}
