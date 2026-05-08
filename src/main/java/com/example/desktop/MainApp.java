package com.example.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.awt.Taskbar;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public class MainApp extends Application {
    private BackendServer server;

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("EMR Workspace");
        BufferedImage iconImage = createAppIconBuffered();
        setTaskbarIcon(iconImage);
        Image appIcon = toFxImage(iconImage);
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }
        stage.setOnCloseRequest(event -> {
            stopServer();
            Platform.exit();
        });

        maximizeStage(stage);
        Label loadingLabel = new Label("Starting application...");
        stage.setScene(new Scene(new StackPane(loadingLabel), 1400, 850));
        stage.show();
        Platform.runLater(() -> maximizeStage(stage));

        try {
            server = new BackendServer();
            server.start();

            String appUrl = "http://localhost:" + server.port();
            System.out.println("Javalin + JavaFX WebView app started at " + appUrl);

            WebView webView = new WebView();
            webView.getEngine().locationProperty().addListener((obs, oldLocation, newLocation) ->
                    System.out.println("WebView location: " + newLocation));
            webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                System.out.println("WebView load state: " + newState);
                if (newState == Worker.State.FAILED) {
                    Throwable error = webView.getEngine().getLoadWorker().getException();
                    if (error != null) {
                        error.printStackTrace(System.err);
                    }
                }
            });
            webView.getEngine().load(appUrl);

            stage.setScene(new Scene(new StackPane(webView), 1400, 850));
            Platform.runLater(() -> maximizeStage(stage));
        } catch (Throwable ex) {
            System.err.println("Fatal startup error");
            ex.printStackTrace(System.err);
            Label error = new Label("Startup failed: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            error.setWrapText(true);
            stage.setScene(new Scene(new StackPane(error), 900, 400));
        }
    }

    @Override
    public void stop() {
        stopServer();
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private void maximizeStage(Stage stage) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setMaximized(true);
    }

    private BufferedImage createAppIconBuffered() {
        try {
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gradient = new GradientPaint(0, 0, new Color(24, 94, 184), 256, 256, new Color(43, 128, 255));
            g.setPaint(gradient);
            g.fill(new RoundRectangle2D.Double(16, 16, 224, 224, 52, 52));

            g.setColor(new Color(255, 255, 255, 230));
            g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(70, 84, 70, 172);
            g.drawLine(70, 84, 168, 84);
            g.drawLine(70, 128, 148, 128);
            g.drawLine(70, 172, 186, 172);
            g.dispose();
            return image;
        } catch (Exception ex) {
            System.err.println("Unable to create app icon: " + ex.getMessage());
            return null;
        }
    }

    private Image toFxImage(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", out);
            return new Image(new ByteArrayInputStream(out.toByteArray()));
        } catch (Exception ex) {
            System.err.println("Unable to convert app icon to JavaFX image: " + ex.getMessage());
            return null;
        }
    }

    private void setTaskbarIcon(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
            return;
        }
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(bufferedImage);
                }
            }
        } catch (UnsupportedOperationException ex) {
            System.err.println("Taskbar icon not supported: " + ex.getMessage());
        } catch (SecurityException ex) {
            System.err.println("Taskbar icon blocked by security manager: " + ex.getMessage());
        }
    }
}
