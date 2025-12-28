package com.yelloowstone.vslauncher;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LibraryPage {
	
	public static VBox create(final Application application, final Stage stage, final ObjectProperty<VintageStoryVersion> versionProperty, final ObservableMap<VintageStoryVersion, File> versionsProperty) {
		final Button viewUploadButton = new Button("Upload Clients");
		viewUploadButton.setOnAction(e -> {
	        VBox libraryPage = UploadPage.create(application, stage, versionProperty, versionsProperty);
			final Scene scene = new Scene(libraryPage, 400, 300);
	        scene.setRoot(libraryPage);
			stage.setScene(scene);
	    });
	
		final ObservableList<Map.Entry<VintageStoryVersion, File>> tableItems = FXCollections.observableArrayList();
		for(Entry<VintageStoryVersion, File> val: versionsProperty.entrySet()) {
			tableItems.add(val);
		}
		
		versionsProperty.addListener((MapChangeListener.Change<? extends VintageStoryVersion, ? extends File> change) -> {
		    if (change.wasAdded()) {
		        // If it was an update to an existing key, we should remove the old entry first
		        tableItems.removeIf(entry -> entry.getKey().equals(change.getKey()));
		        tableItems.add(Map.entry(change.getKey(), change.getValueAdded()));
		    } else if (change.wasRemoved()) {
		        tableItems.removeIf(entry -> entry.getKey().equals(change.getKey()));
		    }
		});
		
		TableView<Map.Entry<VintageStoryVersion, File>> table = new TableView<>(tableItems);
		
		// Column for the Version (Key)
	    TableColumn<Map.Entry<VintageStoryVersion, File>, String> versionCol = new TableColumn<>("Version");
	    versionCol.setCellValueFactory(data -> 
	        new SimpleStringProperty(data.getValue().getKey().getVersionString()));
	    table.getColumns().add(versionCol);
	    
	    // Column for the OS (Key)
	    TableColumn<Map.Entry<VintageStoryVersion, File>, String> osCol = new TableColumn<>("OS");
	    osCol.setCellValueFactory(data -> 
	        new SimpleStringProperty(data.getValue().getKey().getOperatingSystem().toString()));
	    table.getColumns().add(osCol);

	    // Column for the File Path (Value)
	    TableColumn<Map.Entry<VintageStoryVersion, File>, String> pathCol = new TableColumn<>("File Location");
	    pathCol.setCellValueFactory(data -> 
	        new SimpleStringProperty(data.getValue().getValue().getAbsolutePath()));
	    table.getColumns().add(pathCol);

	    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		
		final VBox root = new VBox(10); // 10px spacing
		root.setAlignment(Pos.CENTER);
		root.getChildren().addAll(table, viewUploadButton);
		
		return root;
	}
	
}
