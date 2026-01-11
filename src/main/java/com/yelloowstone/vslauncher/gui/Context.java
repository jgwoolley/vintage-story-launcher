package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Desktop;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Context {
    final ObjectMapper mapper;
    private final Stage stage;
    private final ObservableList<VintageStoryInstance> instances;
    private final VBox rootNode;
    private final File configHome;
    private final AudioClipProperty clickAudioClip;

    
    public Context(final Stage stage, final VBox rootNode) {
         this.mapper = JsonMapper.builder() // format-specific builders
                .build();

        this.stage = stage;
        this.rootNode = rootNode;
        this.instances = FXCollections.observableArrayList();

        // https://specifications.freedesktop.org/basedir/latest/
        this.configHome = new File(System.getProperty("user.home"), ".config/VSLauncher");
        configHome.mkdirs();

        final File saveFile = new File(configHome, "config.json");

        this.instances.addListener(new ListChangeListener<VintageStoryInstance>() {
            @Override
            public void onChanged(Change<? extends VintageStoryInstance> c) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(saveFile, instances);
            }
        });

        try {
            if (saveFile.exists()) {
                // Use TypeReference to handle Generics (List<VintageStoryInstance>)
                List<VintageStoryInstance> loadedInstances = mapper.readValue(
                        saveFile,
                        new TypeReference<List<VintageStoryInstance>>() {}
                );

                // Add the loaded data back to your observable list
                this.instances.setAll(loadedInstances);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        this.clickAudioClip = new AudioClipProperty(Context.class.getResource("/click.wav"));
    }

    public VBox getRootNode() {
        return rootNode;
    }

    public ObservableList<VintageStoryInstance> getInstances() {
        return instances;
    }

    public ObservableList<File> getRuntimeDirs() {
        final Set<File> runtimeDirs = new HashSet<>();

        for(final var instance: this.getInstances()) {
            runtimeDirs.add(instance.getRuntimePath());
        }

        final File userHomePath = new File(System.getProperty("user.home"));

        // TODO: https://wiki.vintagestory.at/Vintagestory_folder

        final File localShareFile = new File(userHomePath, "/.local/share/");
        
        for(final File file: new File[] {
                new File("/Applications/Vintage Story.app"),
                new File(userHomePath, "/AppData/Roaming/Vintagestory"),
                new File(localShareFile, "/vintagestory"),
        }) {
        	if(file.exists()) {
                runtimeDirs.add(file);
        	}
        }
        
        final File[] backupPaths = localShareFile.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String filename) {
				return filename.startsWith("vintagestory.bak");
			}
        	
        });
        
        if(backupPaths != null) {
            for(File file: backupPaths) {
                runtimeDirs.add(file);
            }
        }

        return FXCollections.observableArrayList(runtimeDirs);
    }

    public ObservableList<File> getDataDirs() {
        final Set<File> dataDirs = new HashSet<>();

        for(final var instance: this.getInstances()) {
            dataDirs.add(instance.getDataPath());
        }

        final File userHomePath = new File(System.getProperty("user.home"));

        for(final File file: new File[] {
                new File(userHomePath, "/Library/Application Support/VintagestoryData"),
                new File(userHomePath, "/.config/VintagestoryData/"),
                new File(userHomePath, "/.config/VintagestoryData2/"), // TODO: Remove
                new File(userHomePath, "/.var/app/at.vintagestory.VintageStory/config/VintagestoryData/"),
        }) {
            if(file.exists()) {
                dataDirs.add(file);
            }
        }

        final File vsInstancePath = new File(userHomePath, "/Documents/VSInstances");
        final File[] vsInstancePaths = vsInstancePath.listFiles();
        if(vsInstancePaths != null) {
            for(final File file: vsInstancePaths) {
                if(new File(file, "clientsettings.json").isFile()) {
                    dataDirs.add(file);
                }
            }
        }

        return FXCollections.observableArrayList(dataDirs);
    }

    public Stage getStage() {
        return stage;
    }

    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    public File getConfigHome() {
        return configHome;
    }
    
    public AudioClipProperty getVolumeProperty() {
    	return this.clickAudioClip;
    }
    
    //TODO: Shouldn't be at both Context / Instance
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
    
    public static void openFile(File file) {
    	if(Context.isUnix()) {
    		try {
                // Use xdg-open for Linux
                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
    	} else {
    		try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
    	}
    }
}
