package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class VideoController {

    @FXML private VBox videoDropZone, videoPlayerContainer;
    @FXML private ImageView videoPreviewImage;
    @FXML private Label videoOverlayLabel;
    @FXML private TextArea videoOverlayText;
    @FXML private Slider videoSeekBar, videoDurationSlider;
    @FXML private ColorPicker videoTextColorPicker;
    @FXML private ComboBox<String> videoGraphicsCombo;
    @FXML private Button btnVideoPlay;

    private List<String> videoPhotos = new ArrayList<>();
    private javafx.animation.Timeline videoTimeline;
    private int currentVideoIndex = 0;

    private static VideoController instance;
    public VideoController() { instance = this; }
    public static VideoController getInstance() { return instance; }

    @FXML
    public void initialize() {
        if (videoOverlayText != null) {
            videoOverlayText.textProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null) videoOverlayLabel.setText(newVal);
            });
        }
        if (videoTextColorPicker != null) {
            videoTextColorPicker.setOnAction(e -> {
                if (videoOverlayLabel != null) {
                    videoOverlayLabel.setStyle("-fx-text-fill: #" + 
                        videoTextColorPicker.getValue().toString().substring(2, 8) + 
                        "; -fx-font-size: 20; -fx-padding: 15; -fx-font-family: 'Georgia'; -fx-font-style: italic;");
                }
            });
        }
    }

    @FXML
    public void handleSyncFavourites() {
        MainController main = MainController.getInstance();
        if (main == null) return;

        videoPhotos.clear();
        List<String> editedFiles = main.getEditedFiles();
        Properties annotationsDB = main.getAnnotationsDB();

        for (String path : editedFiles) {
            File f = new File(path);
            String hash = getFileHash(f);
            if (annotationsDB.containsKey(hash)) {
                videoPhotos.add(path);
            }
        }
        
        if (!videoPhotos.isEmpty()) {
            videoDropZone.setVisible(false);
            videoDropZone.setManaged(false);
            videoPlayerContainer.setVisible(true);
            videoPlayerContainer.setManaged(true);
            showVideoFrame(0);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Favourites");
            alert.setHeaderText(null);
            alert.setContentText("You haven't marked any photos as favourites in the gallery yet!");
            alert.showAndWait();
        }
    }

    private void showVideoFrame(int index) {
        if (videoPhotos.isEmpty()) return;
        currentVideoIndex = index;
        File f = new File(videoPhotos.get(index));
        videoPreviewImage.setImage(new Image(f.toURI().toString()));
    }

    @FXML
    public void handleVideoPlayPause() {
        if (videoPhotos.isEmpty()) return;

        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            videoTimeline.pause();
            btnVideoPlay.setText("▶");
        } else {
            if (videoTimeline != null) videoTimeline.stop();
            
            videoTimeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                Duration.seconds(videoDurationSlider.getValue()),
                e -> {
                    currentVideoIndex = (currentVideoIndex + 1) % videoPhotos.size();
                    showVideoFrame(currentVideoIndex);
                    videoSeekBar.setValue((double) currentVideoIndex / (videoPhotos.size() - 1) * 100);
                }
            ));
            videoTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
            videoTimeline.play();
            btnVideoPlay.setText("⏸");
        }
    }

    private String getFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(Files.readAllBytes(file.toPath()));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return file.getAbsolutePath(); }
    }
}
