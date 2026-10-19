package com.yelloowstone.vslauncher.gui;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.yelloowstone.vslauncher.VintageStoryInstance;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

/** System tray icon whose menu opens the most recently used instances. */
public class TrayMenu {
    private static final int RECENT = 5;

    private final Context context;
    private final Stage stage;
    private final TrayIcon trayIcon;
    private final Timeline refresh;

    private TrayMenu(final Context context, final Stage stage, final TrayIcon trayIcon) {
        this.context = context;
        this.stage = stage;
        this.trayIcon = trayIcon;
        // Last-opened times change while the game runs, outside the instance list
        this.refresh = new Timeline(new KeyFrame(Duration.seconds(30), e -> rebuild()));
        refresh.setCycleCount(Timeline.INDEFINITE);
    }

    /** False where the desktop has no tray (e.g. some Linux desktops, or a headless session). */
    public static boolean isSupported() {
        return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    }

    /** Adds the tray icon, or returns null if there is no tray. */
    public static TrayMenu install(final Context context, final Stage stage) {
        if (!isSupported()) {
            return null;
        }
        try (InputStream in = TrayMenu.class.getResourceAsStream("/icon-64.png")) {
            if (in == null) {
                return null;
            }
            final java.awt.Image image = javax.imageio.ImageIO.read(in);
            final PopupMenu popup = new PopupMenu();
            final TrayIcon icon = new TrayIcon(image, stage.getTitle(), popup);
            icon.setImageAutoSize(true);
            final TrayMenu menu = new TrayMenu(context, stage, icon);
            // TrayIcon keeps the PopupMenu given at construction: reuse it
            menu.attach(popup);
            SystemTray.getSystemTray().add(icon);
            return menu;
        } catch (IOException | AWTException | RuntimeException e) {
            System.err.println("Could not add a system tray icon: " + e.getMessage());
            return null;
        }
    }

    private PopupMenu menu;

    private void attach(final PopupMenu popupMenu) {
        this.menu = popupMenu;
        trayIcon.addActionListener(e -> showLauncher());
        context.getInstances().addListener((javafx.collections.ListChangeListener<VintageStoryInstance>) c -> rebuild());
        rebuild();
        refresh.play();
    }

    public void remove() {
        refresh.stop();
        SystemTray.getSystemTray().remove(trayIcon);
    }

    private void showLauncher() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
        });
    }

    /** Rebuilds the menu from the five most recently opened instances. */
    public void rebuild() {
        // Read on the JavaFX thread (the list belongs to it), then update the AWT menu
        final List<VintageStoryInstance> recent = new ArrayList<>(context.getInstances());
        recent.sort(Comparator.comparing(VintageStoryInstance::getLastOpen).reversed());
        final List<VintageStoryInstance> top = recent.subList(0, Math.min(RECENT, recent.size()));
        final List<String> labels = new ArrayList<>();
        for (final VintageStoryInstance instance : top) {
            labels.add(instance.getName() + "  (" + HumanTime.format(instance.getLastOpen()) + ")");
        }

        EventQueue.invokeLater(() -> {
            menu.removeAll();
            if (top.isEmpty()) {
                final MenuItem none = new MenuItem("No instances yet");
                none.setEnabled(false);
                menu.add(none);
            }
            for (int i = 0; i < top.size(); i++) {
                final VintageStoryInstance instance = top.get(i);
                final MenuItem item = new MenuItem("Open " + labels.get(i));
                item.addActionListener(e -> Platform.runLater(() -> {
                    instance.open(context.getObjectMapper(), context.getMapRegistry());
                    rebuild();
                }));
                menu.add(item);
            }
            menu.addSeparator();
            final MenuItem show = new MenuItem("Show Launcher");
            show.addActionListener(e -> showLauncher());
            menu.add(show);
            final MenuItem exit = new MenuItem("Exit");
            exit.addActionListener(e -> Platform.runLater(Platform::exit));
            menu.add(exit);
        });
    }
}
