package net.superblaubeere27.masxinlingvonta.test.runner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class TestCompiler {
    private final File inputDir;
    private final File outputJar;
    private final List<File> classPath;

    public TestCompiler(File inputDir, File outputJar, List<File> classPath) {
        this.inputDir = inputDir;
        this.outputJar = outputJar;
        this.classPath = classPath;
    }

    public void compile(List<String> classesToCompile) throws IOException, InterruptedException {
        var outputTempFile = Files.createTempDirectory("mlv_compile_").toFile();

        outputTempFile.deleteOnExit();

        var javacProcess =
                new ProcessBuilder(generateJavacCommandline(classesToCompile, outputTempFile)).inheritIO().start();

        var exitCode = javacProcess.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("Failed to compile tests");
        }

        var jarProcess =
                new ProcessBuilder(generateJarCommandline(outputTempFile, this.outputJar)).inheritIO().start();

        exitCode = jarProcess.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException("Failed to create jar");
        }
    }

    private String[] generateJavacCommandline(List<String> classesToCompile, File outputTempFile) {
        var classPathString = this.classPath.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));

        var targetClassString = classesToCompile.stream()
                .map(x -> this.inputDir.getAbsolutePath() + File.separator + x.replace('.', File.separatorChar) + ".java")
                .collect(Collectors.joining(File.pathSeparator));

        return new String[]{
                "javac",
                "-source", "8",
                "-target", "8",
                "-cp", classPathString,
                "-d", outputTempFile.getAbsolutePath(),
                targetClassString
        };
    }

    private String[] generateJarCommandline(File inputDir, File outputJar) {
        return new String[]{
                "jar",
                "--create",
                "--file", outputJar.getAbsolutePath(),
                "-C", inputDir.getAbsolutePath(), "."
        };
    }
}
