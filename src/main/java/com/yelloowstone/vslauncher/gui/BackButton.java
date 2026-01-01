package com.yelloowstone.vslauncher.gui;

import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class BackButton {
    public static Button create(Context context) {
        final Button button = new Button("Back");
        button.setOnAction(x -> {
            InstanceSelectionPage.create(context);
        });
        button.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(button, Priority.ALWAYS);

        return button;
    }
}
