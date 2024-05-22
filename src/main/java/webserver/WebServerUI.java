package webserver;

import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.application.Platform;

public class WebServerUI extends Application {
    private WebServer webServer;
    private TextField filePathField;
    private TextField logsPathField;
    private TextField portField;
    private TextArea logArea;
    private final Preferences preferences = Preferences.userNodeForPackage(WebServerUI.class);
    private Button startButton;
    private Button stopButton;
    private Timeline logUpdater;
    private Set<String> displayedLogs = new HashSet<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Web Server");

        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(10));
        borderPane.setStyle("-fx-background-color: #e0f7fa;");

        Label titleLabel = new Label("Web Server");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #0277bd;");
        titleLabel.setAlignment(Pos.CENTER);
        borderPane.setTop(titleLabel);
        BorderPane.setAlignment(titleLabel, Pos.CENTER);

        VBox controlPanel = new VBox(10);
        controlPanel.setPadding(new Insets(10));
        controlPanel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #81d4fa; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        controlPanel.setAlignment(Pos.TOP_CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label pathLabel = new Label("File Path :");
        filePathField = new TextField(preferences.get("filePath", "D:\\Web\\Files"));
        filePathField.setPrefWidth(200);
        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> browseFilePath(primaryStage));
        grid.addRow(0, pathLabel, filePathField, browseButton);

        Label logsPathLabel = new Label("Logs Path :");
        logsPathField = new TextField(preferences.get("logsPath", "D:\\Web\\logs"));
        logsPathField.setPrefWidth(200);
        Button logsBrowseButton = new Button("Browse");
        logsBrowseButton.setOnAction(e -> browseLogsPath(primaryStage));
        grid.addRow(1, logsPathLabel, logsPathField, logsBrowseButton);

        Label portLabel = new Label("Port :");
        portField = new TextField(preferences.get("port", "8000"));
        portField.setPrefWidth(200);
        grid.addRow(2, portLabel, portField);

        controlPanel.getChildren().add(grid);

        startButton = new Button("Start");
        startButton.setStyle("-fx-background-color: #0277bd; -fx-text-fill: white; -fx-font-weight: bold;");
        startButton.setOnAction(e -> startWebServer());

        stopButton = new Button("Stop");
        stopButton.setStyle("-fx-background-color: #b71c1c; -fx-text-fill: white; -fx-font-weight: bold;");
        stopButton.setOnAction(e -> stopWebServer());
        stopButton.setDisable(true);

        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(startButton, stopButton);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setAlignment(Pos.CENTER);

        controlPanel.getChildren().add(buttonBox);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(300);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-background-color: #e3f2fd; -fx-text-fill: #01579b;");
        logArea.setWrapText(true);

        VBox logBox = new VBox(10, new Label("Server Logs:"), logArea);
        logBox.setPadding(new Insets(10));
        logBox.setStyle("-fx-background-color: #ffffff; -fx-border-color: #81d4fa; -fx-border-width: 1px; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        logBox.setPrefHeight(200);

        VBox mainBox = new VBox(10, logBox);
        mainBox.setPadding(new Insets(10));
        mainBox.setAlignment(Pos.TOP_CENTER);

        borderPane.setLeft(controlPanel);
        borderPane.setCenter(mainBox);

        Scene scene = new Scene(borderPane, 700, 500);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private void browseFilePath(Stage primaryStage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select File Path");
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            filePathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    private void browseLogsPath(Stage primaryStage) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Logs Path");
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            logsPathField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    private void startWebServer() {
        String filePath = filePathField.getText();
        String logsPath = logsPathField.getText();
        int port = Integer.parseInt(portField.getText());

        preferences.put("filePath", filePath);
        preferences.put("logsPath", logsPath);
        preferences.put("port", String.valueOf(port));

        if (webServer == null || !webServer.isAlive()) {
            if (!logArea.getText().isEmpty()) {
                appendToLog("\n");
            }

            webServer = new WebServer(filePath, logsPath, port);
            new Thread(() -> webServer.start()).start();
            appendToLog(String.format("[%s] Server started on port %d\n", new Date(), port));
            startButton.setDisable(true);
            stopButton.setDisable(false);

            // Start log updater
            startLogUpdater();
        } else {
            System.out.println("Server already running.");
        }
    }

    private void stopWebServer() {
        if (webServer != null && webServer.isAlive()) {
            webServer.stopServer();
            appendToLog(String.format("[%s] Server stopped\n", new Date()));
            stopButton.setDisable(true);
            startButton.setDisable(false);

            // Stop log updater
            stopLogUpdater();
        }
    }

    private void startLogUpdater() {
        logUpdater = new Timeline(new KeyFrame(Duration.seconds(2), e -> readLogs()));
        logUpdater.setCycleCount(Timeline.INDEFINITE);
        logUpdater.play();
    }

    private void stopLogUpdater() {
        if (logUpdater != null) {
            logUpdater.stop();
        }
    }

    private void readLogs() {
        if (webServer != null) {
            List<String> logs = webServer.loadAccessLogs();
            if (!logs.isEmpty()) {
                for (String log : logs) {
                    if (!displayedLogs.contains(log)) {
                        appendToLog(log + "\n");
                        displayedLogs.add(log);
                    }
                }
            }
        }
    }

    private void appendToLog(String message) {
        Platform.runLater(() -> logArea.appendText(message));
    }
}
