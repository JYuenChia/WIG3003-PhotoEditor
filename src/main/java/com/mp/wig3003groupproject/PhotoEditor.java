package com.mp.wig3003groupproject;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PhotoEditor extends Application {
    private static PhotoEditor instance;

    @Override
    public void start(Stage stage) throws Exception {
        instance = this;
        FXMLLoader fxmlLoader = new FXMLLoader(PhotoEditor.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("Photo Editor");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    public static PhotoEditor getInstance() {
        return instance;
    }
}