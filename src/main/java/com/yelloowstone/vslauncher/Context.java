package com.yelloowstone.vslauncher;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;

public class Context {
    private final Stage stage;
    private final ObjectProperty<File> instancePathFormProperty;
    private final ObjectProperty<File> runtimePathFormProperty;
    private final ObservableList<VintageStoryInstance> instances;
    private final ObservableList<VintageStoryRuntime> runtimes;
    private final VBox rootNode;

    public Context(final Stage stage, final VBox rootNode) {
        this.stage = stage;
        this.rootNode = rootNode;
        this.instances = FXCollections.observableArrayList();
        this.runtimes = FXCollections.observableArrayList();
        this.instancePathFormProperty = new SimpleObjectProperty<>();
        this.runtimePathFormProperty = new SimpleObjectProperty<>();

        if(!this.instances.isEmpty()) {
            this.instancePathFormProperty.set(this.instances.get(0).getDataPath());
        }

        final File vsInstancePath = new File(System.getProperty("user.home"), "/Documents/VSInstances");
        final File[] vsInstancePaths = vsInstancePath.listFiles();
        if(vsInstancePaths != null) {
            for(final File file: vsInstancePaths)
            {
                this.runtimes.add(new VintageStoryRuntime(file));
            }
        }


        if(!this.runtimes.isEmpty()) {
            this.runtimePathFormProperty.set(this.runtimes.get(0).getPath());
        }
    }

    public VBox getRootNode() {
        return rootNode;
    }

    public ObservableList<VintageStoryRuntime> getRuntimes() {
        return runtimes;
    }

    public ObservableList<VintageStoryInstance> getInstances() {
        return instances;
    }

    public Stage getStage() {
        return stage;
    }

    public ObjectProperty<File> getInstancePathFormProperty() {
        return instancePathFormProperty;
    }

    public ObjectProperty<File> getRuntimePathFormProperty() {
        return runtimePathFormProperty;
    }
}
