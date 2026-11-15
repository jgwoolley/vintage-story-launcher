package com.yelloowstone.vslauncher.gui;

import com.yelloowstone.vslauncher.VintageStoryInstance;
import com.yelloowstone.vslauncher.VintageStoryMap;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class MapCopyPage {
	
	public static Button createBackButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Back");
		button.setOnAction(x -> {
			context.getVolumeProperty().play();
			MapsInfoPage.create(context, instance);
		});
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}
	
	public static Button createMergeButton(final Context context, final VintageStoryInstance instance) {
		final Button button = Icons.button("Merge");
		button.setDisable(true);
		button.setMaxWidth(Double.MAX_VALUE);
		VBox.setVgrow(button, Priority.ALWAYS);
		return button;
	}
	
	private static class InstanceMap {
		private final VintageStoryInstance instance;
		private final VintageStoryMap map;
		
		
		private InstanceMap(VintageStoryInstance instance, VintageStoryMap map) {
			super();
			this.instance = instance;
			this.map = map;
		}
		public VintageStoryInstance getInstance() {
			return instance;
		}
		public VintageStoryMap getMap() {
			return map;
		}
		
		@Override
		public String toString() {
			return map.getFile().getName() + " (" + instance.getName() + ")";
		}
		
	}
	
	public static void create(final Context context, final VintageStoryInstance instance, final VintageStoryMap map) {
		context.getRootNode().getChildren().clear();
		
		final Label instanceLabel = new Label("Instance: " + instance.getName());
		final Label mapNamelabel = new Label("Map File: " + map.getFile().getName());
		
		final ObservableList<InstanceMap> maps = FXCollections.observableArrayList();
		for(final VintageStoryInstance otherInstnace: context.getClientInstances()) {
			if(otherInstnace == instance) {
				continue;
			}
			for(final VintageStoryMap otherMap: otherInstnace.getMaps()) {
				if(otherMap.getFile().getName().equals(map.getFile().getName())) {
					maps.add(new InstanceMap(otherInstnace, otherMap));
				}
			}
		}
		
		final Label explanation = new Label("A map file is named after the world it belongs to (its unique ID). "
				+ "Two files with the same name are two copies of the same world, for example the same server played from "
				+ "two instances, so they can be merged into one map. Maps with different names are different worlds "
				+ "and are never offered here.");
		explanation.setWrapText(true);
		final Label emptyLabel = new Label(maps.isEmpty()
				? "No other instance has a copy of this world, so there is nothing to merge it with."
				: "Pick the instance whose copy of this map you want to merge in.");
		emptyLabel.setWrapText(true);

		final ComboBox<InstanceMap> comboBox = new ComboBox<>(maps);
		comboBox.setConverter(new StringConverter<InstanceMap>() {
		    @Override
		    public String toString(InstanceMap map) {
		        return (map == null) ? "" : map.getInstance().getName();
		    }

		    @Override
		    public InstanceMap fromString(String string) {
		        return null; // Not needed unless the ComboBox is editable
		    }
		});
		
		context.getRootNode().getChildren().addAll(
				instanceLabel,
				mapNamelabel,
				explanation,
				emptyLabel,
				comboBox,
				createMergeButton(context, instance),
				createBackButton(context, instance));
	}
}
