package com.yelloowstone.vslauncher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class VintageStoryInstance {
    private final String name;
    private final File runtimePath;
    private final File dataPath;

    public VintageStoryInstance(final String name, final File runtimePath, final File dataPath) {
        this.name = name;
        this.runtimePath = runtimePath;
        this.dataPath = dataPath;
    }

    public String getName() {
        return this.name;
    }

    public File getDataPath() {
        return this.dataPath;
    }

    public String getVersion() {
        final File assetsPath = new File(this.runtimePath,"assets");
        if(!assetsPath.isDirectory()) {
            return null;
        }

        final File[] files = assetsPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("version-") && name.endsWith(".txt");
            }
        });
        System.out.println();
        if(files == null || files.length == 0) {
            return null;
        }

        return files[0].getName().substring("version-".length(), files[0].getName().length() - ".txt".length());
    }

    public File getRuntimePath() {
        return runtimePath;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public String getLastLoginUser() {
        return null;
    }

    public void open() {
        String[] args = new String[]{
                "open",
                "-n",
                this.getRuntimePath().toString(),
                "--args",
                "--dataPath",
                this.getDataPath().toString(),
        };
        final ProcessBuilder builder = new ProcessBuilder(args);
        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            System.out.println(exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void copy(VintageStoryInstance other) {
        final File sourceMods = new File(this.getDataPath(), "Mods");
        sourceMods.mkdirs();
        final File destinationMods = new File(other.getDataPath(), "Mods");
        destinationMods.mkdirs();

        final File[] sourceModFiles = sourceMods.listFiles();
        if(sourceModFiles == null) {
            System.out.println("No mods?!?!");
            return;
        }

        System.out.println(sourceModFiles.length);


        for(final File file: sourceModFiles) {
            final Path destMod = new File(destinationMods, file.getName()).toPath();
            try {
                Files.copy(file.toPath(), destMod, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Copied " + destMod.toString());
        }
    }

}
