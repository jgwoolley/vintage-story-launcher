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

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Context {
    final ObjectMapper mapper;
    private final Stage stage;
    private final ObservableList<VintageStoryInstance> instances;
    private final VBox rootNode;

    public Context(final Stage stage, final VBox rootNode) {
         this.mapper = JsonMapper.builder() // format-specific builders
                .build();

        this.stage = stage;
        this.rootNode = rootNode;
        this.instances = FXCollections.observableArrayList();

        // TODO: Save somewhere else...
        final File saveFile = new File("test.json");

        this.instances.addListener(new ListChangeListener<VintageStoryInstance>() {
            @Override
            public void onChanged(Change<? extends VintageStoryInstance> c) {
                System.out.println(saveFile.getAbsolutePath());
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

        for(final File file: new File[] {
                new File("/Applications/Vintage Story.app"),
                new File(userHomePath, "/AppData/Roaming/Vintagestory"),
                new File(userHomePath, "/.local/share/vintagestory"),
        }) {
            if(file.exists()) {
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
}
