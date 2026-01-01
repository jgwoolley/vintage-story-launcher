package com.yelloowstone.vslauncher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class VintageStoryInstance {
    private final String name;
    private final File runtimePath;
    private final File dataPath;

    @JsonCreator
    public VintageStoryInstance(
            @JsonProperty("name") final String name,
            @JsonProperty("runtimePath") final File runtimePath,
            @JsonProperty("dataPath") final File dataPath) {
        this.name = name;
        this.runtimePath = runtimePath;
        this.dataPath = dataPath;
    }

    public String getName() {
        return this.name;
    }

    public LocalDateTime getLastOpen() {
        final File src = new File(dataPath, "clientsettings.json");
        final Instant instant = Instant.ofEpochMilli(src.lastModified());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
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

    public static boolean isWindows() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("win");
    }

    public static boolean isMac() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("mac");
    }

    public static boolean isUnix() {
        final String OS = System.getProperty("os.name").toLowerCase();
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix");
    }

    public void open() {
        if (isWindows()) {
            System.out.println("Detected: Windows");
        } else if (isMac()) {
            openMac();
        } else if (isUnix()) {
            openLinux();
        } else {
            System.out.println("Detected: Unknown OS");
        }
    }

    public void openLinux() {
        String[] args = new String[]{
                new File(this.getRuntimePath(), "run.sh").toString(),
                "--dataPath",
                this.getDataPath().toString(),
        };
        final ProcessBuilder builder = new ProcessBuilder(args);
        try {
            final Process process = builder.start();
            final int exitCode = process.waitFor();
            System.out.println("Process Exit Code: " +  exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void openMac() {
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
            System.out.println("Process Exit Code: " + exitCode);
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

        final File[] sourceModFiles = sourceMods.listFiles((file, name) -> name.endsWith(".zip"));
        if(sourceModFiles == null) {
            System.err.println("No mods?!?!");
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

    public ClientSettingsFile getClientSettingsFile(ObjectMapper objectMapper) {
        final File src = new File(dataPath, "clientsettings.json");
        return objectMapper.readValue(src, ClientSettingsFile.class);
    }

    public String getPlayer(ObjectMapper objectMapper) {
        return getClientSettingsFile(objectMapper).getStringSettings().get("playername");
    }
}
