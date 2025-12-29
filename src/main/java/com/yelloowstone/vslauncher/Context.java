package com.yelloowstone.vslauncher;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;

public class Context {
    private final Stage stage;
    private final ObservableList<VintageStoryInstance> instances;
    private final ObservableList<File> dataDirs;
    private final ObservableList<File> runtimeDirs;
    private final VBox rootNode;

    public Context(final Stage stage, final VBox rootNode) {
        this.stage = stage;
        this.rootNode = rootNode;
        this.instances = FXCollections.observableArrayList();
        this.dataDirs = FXCollections.observableArrayList();
        this.runtimeDirs = FXCollections.observableArrayList();

        final File userHomePath = new File(System.getProperty("user.home"));
        final File libraryPath = new File(userHomePath, "/Library/Application Support/VintagestoryData");
        if(libraryPath.exists()) {
            this.dataDirs.add(libraryPath);
        }

        final File vsInstancePath = new File(userHomePath, "/Documents/VSInstances");
        final File[] vsInstancePaths = vsInstancePath.listFiles();
        if(vsInstancePaths != null) {
            for(final File file: vsInstancePaths) {
                if(new File(file, "clientsettings.json").isFile()) {
                    this.dataDirs.add(file);
                }
            }
        }

        final File libraryApp = new File("/Applications/Vintage Story.app");
        if(libraryApp.exists()) {
            this.runtimeDirs.add(libraryApp);
        }
    }

    public VBox getRootNode() {
        return rootNode;
    }

    public ObservableList<File> getRuntimeDirs() {
        return this.runtimeDirs;
    }

    public ObservableList<File> getDataDirs() {
        return this.dataDirs;
    }

    public ObservableList<VintageStoryInstance> getInstances() {
        return instances;
    }

    public Stage getStage() {
        return stage;
    }
}
