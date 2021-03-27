package net.superblaubeere27.masxinlingvaj.io;

import net.superblaubeere27.masxinlingvaj.utils.ExecutorServiceFactory;
import net.superblaubeere27.masxinlingvaj.utils.Result;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class for handling reading of Java files
 */
public class InputLoader {

    /**
     * Reads a zip file
     *
     * @param input        The input location
     * @param dataConsumer A function that is called when an entry was read
     * @throws IOException When an IO error occurs
     */
    private static void loadFile(URL input, BiConsumer<String, byte[]> dataConsumer) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(input.openStream())) {
            ZipEntry zipEntry;

            // While there are zip entries..
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // Read the entry data and send it to the consumer
                dataConsumer.accept(zipEntry.getName(), zipInputStream.readAllBytes());
            }
        }
    }

    /**
     * Reads input files to an {@link ArrayList} of ClassNodes
     *
     * @param inputs          the locations of the input files
     * @param executorFactory the executor factory that creates executors for multithreaded reading
     * @return all read classes
     * @throws IOException when an IO exception occurs
     */
    public static ReadInput loadFiles(List<URL> inputs, ExecutorServiceFactory executorFactory) throws IOException {
        var executor = executorFactory.createExecutor();

        HashMap<String, byte[]> rawData = new HashMap<>();
        ArrayList<Future<Result<ClassNode, Exception>>> classNodeFutures = new ArrayList<>();

        for (URL input : inputs) {
            // Load the current file
            loadFile(input, (name, data) -> {
                rawData.put(name, data);

                // Check if the current entry is a class file
                if (!name.endsWith(".class")) {
                    return;
                }

                // Enqueue the reader task
                classNodeFutures.add(executor.submit(() -> {
                    // Read the class and convert possible exceptions into results
                    return Result.executeCatching(() -> {
                        ClassReader classReader = new ClassReader(data);

                        ClassNode node = new ClassNode();

                        // Read the class
                        classReader.accept(node, ClassReader.SKIP_DEBUG);

                        return node;
                    });
                }));
            });
        }

        // Stop the executor
        executor.shutdown();

        ArrayList<ClassNode> output = new ArrayList<>(classNodeFutures.size());

        for (Future<Result<ClassNode, Exception>> classNodeFuture : classNodeFutures) {
            try {
                output.add(classNodeFuture.get().unwrap());
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted", e);
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        return new ReadInput(output, rawData);
    }

    public static class ReadInput {
        private final ArrayList<ClassNode> classNodes;
        private final HashMap<String, byte[]> rawData;

        public ReadInput(ArrayList<ClassNode> classNodes, HashMap<String, byte[]> rawData) {
            this.classNodes = classNodes;
            this.rawData = rawData;
        }

        public ArrayList<ClassNode> getClassNodes() {
            return classNodes;
        }

        public HashMap<String, byte[]> getRawData() {
            return rawData;
        }
    }

}
