package com.yelloowstone.vslauncher.gui;

import java.net.URL;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.media.AudioClip;

public class AudioClipProperty {
	private final SimpleDoubleProperty volumeProperty;	
	private final AudioClip audioClip;

	public AudioClipProperty(final URL soundURL) {
		this.volumeProperty = new SimpleDoubleProperty(1.0); 
		this.audioClip = new AudioClip(soundURL.toExternalForm());
	}
	
	public SimpleDoubleProperty getProperty() {
		return this.volumeProperty;
	}
	
	public void play() {
		try {
			if (audioClip != null) {
				audioClip.setVolume(getProperty().doubleValue()); // 1.0 is max
				audioClip.play();
			    System.out.println("Playing at volume: " + audioClip.getVolume());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
