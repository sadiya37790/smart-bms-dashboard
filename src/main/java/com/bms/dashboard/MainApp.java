package com.bms.dashboard;

import com.bms.dashboard.controller.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/bms/dashboard/view/MainView.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();

        Scene scene = new Scene(root, 1280, 850);
        scene.getStylesheets().add(getClass().getResource("/com/bms/dashboard/styles/dark-theme.css").toExternalForm());

        primaryStage.setTitle("Smart BMS Dashboard - Lithium-ion Pack Monitor (4S)");
        primaryStage.setMinWidth(1280);
        primaryStage.setMinHeight(800);
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(e -> {
            if (controller != null) {
                controller.shutdown();
            }
        });

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
