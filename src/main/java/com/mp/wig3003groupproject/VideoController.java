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
    @FXML private VBox synthesisControls, trimControls;
    @FXML private ImageView videoPreviewImage;
    @FXML private Label videoOverlayLabel, videoDurationValueLabel;
    @FXML private TextArea videoOverlayText;
    @FXML private Slider videoSeekBar, videoDurationSlider;
    @FXML private Slider trimStartSlider, trimEndSlider;
    @FXML private ComboBox<String> videoFontCombo;
    @FXML private Slider videoOpacitySlider, videoTextXSlider, videoTextYSlider;
    @FXML private ColorPicker videoTextColorPicker;
    @FXML private ComboBox<String> videoGraphicsCombo;
    @FXML private Button btnVideoPlay;

    private List<String> videoPhotos = new ArrayList<>();
    private List<Double> photoDurations = new ArrayList<>();
    private javafx.animation.Timeline videoTimeline;
    private int currentVideoIndex = 0;
    private boolean isVideoMode = false;

    private static VideoController instance;
    public VideoController() { instance = this; }
    public static VideoController getInstance() { return instance; }

    @FXML
    public void initialize() {
        if (videoFontCombo != null) {
            videoFontCombo.setItems(javafx.collections.FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
            videoFontCombo.setValue("System");
            videoFontCombo.setOnAction(e -> updateOverlayStyle());
        }

        if (videoOpacitySlider != null) {
            videoOpacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> updateOverlayStyle());
        }

        if (videoTextXSlider != null) {
            videoTextXSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null) videoOverlayLabel.setTranslateX(newVal.doubleValue());
            });
        }

        if (videoTextYSlider != null) {
            videoTextYSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null) videoOverlayLabel.setTranslateY(newVal.doubleValue());
            });
        }

        if (videoDurationSlider != null) {
            videoDurationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!videoPhotos.isEmpty() && !isVideoMode) {
                    photoDurations.set(currentVideoIndex, newVal.doubleValue());
                }
                if (videoDurationValueLabel != null) {
                    videoDurationValueLabel.setText(String.format("%.1fs", newVal.doubleValue()));
                }
            });
        }

        if (videoOverlayText != null) {
            videoOverlayText.textProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null) videoOverlayLabel.setText(newVal);
            });
        }

        if (videoTextColorPicker != null) {
            videoTextColorPicker.setOnAction(e -> updateOverlayStyle());
        }
    }

    private void updateOverlayStyle() {
        if (videoOverlayLabel == null) return;
        
        String color = "#" + videoTextColorPicker.getValue().toString().substring(2, 8);
        String font = videoFontCombo.getValue();
        double opacity = videoOpacitySlider.getValue();
        
        videoOverlayLabel.setStyle("-fx-text-fill: " + color + "; " +
                                  "-fx-font-family: '" + font + "'; " +
                                  "-fx-font-size: 20; " +
                                  "-fx-padding: 15; " +
                                  "-fx-font-style: italic;");
        videoOverlayLabel.setOpacity(opacity);
    }

    @FXML
    public void handleUploadVideo() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv", "*.avi"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            isVideoMode = true;
            switchToPlayerMode();
            // In a real app we'd load into MediaView, for now we simulate with image thumb
            videoPreviewImage.setImage(new Image(file.toURI().toString()));
            synthesisControls.setVisible(false);
            synthesisControls.setManaged(false);
            trimControls.setVisible(true);
            trimControls.setManaged(true);
        }
    }

    @FXML
    public void handleSelectPhotos() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
        List<File> files = fc.showOpenMultipleDialog(null);
        if (files != null && !files.isEmpty()) {
            isVideoMode = false;
            videoPhotos.clear();
            photoDurations.clear();
            for (File f : files) {
                videoPhotos.add(f.getAbsolutePath());
                photoDurations.add(3.0); // Default 3s
            }
            switchToPlayerMode();
            showVideoFrame(0);
            synthesisControls.setVisible(true);
            synthesisControls.setManaged(true);
            trimControls.setVisible(false);
            trimControls.setManaged(false);
        }
    }

    private void switchToPlayerMode() {
        videoDropZone.setVisible(false);
        videoDropZone.setManaged(false);
        videoPlayerContainer.setVisible(true);
        videoPlayerContainer.setManaged(true);
    }

    @FXML
    public void handleSyncFavourites() {
        MainController main = MainController.getInstance();
        if (main == null) return;

        videoPhotos.clear();
        photoDurations.clear();
        List<String> editedFiles = main.getEditedFiles();
        Properties annotationsDB = main.getAnnotationsDB();

        for (String path : editedFiles) {
            File f = new File(path);
            String hash = getFileHash(f);
            if (annotationsDB.containsKey(hash)) {
                videoPhotos.add(path);
                photoDurations.add(3.0);
            }
        }
        
        if (!videoPhotos.isEmpty()) {
            isVideoMode = false;
            switchToPlayerMode();
            showVideoFrame(0);
            synthesisControls.setVisible(true);
            synthesisControls.setManaged(true);
            trimControls.setVisible(false);
            trimControls.setManaged(false);
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
        if (!isVideoMode) {
            videoDurationSlider.setValue(photoDurations.get(index));
        }
    }

    @FXML
    public void handleVideoPlayPause() {
        if (videoPhotos.isEmpty() && !isVideoMode) return;

        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            videoTimeline.pause();
            btnVideoPlay.setShape(new javafx.scene.shape.SVGPath() {{ setContent("M 0 0 L 20 10 L 0 20 Z"); }});
        } else {
            if (videoTimeline != null) videoTimeline.stop();
            
            videoTimeline = new javafx.animation.Timeline();
            final int totalFrames = videoPhotos.size();
            double currentTime = 0;

            if (isVideoMode) {
                // Simplified video playback simulation
                double start = trimStartSlider.getValue();
                double end = trimEndSlider.getValue();
                // ... logic to play segment
            } else {
                for (int i = 0; i < totalFrames; i++) {
                    final int frameIndex = i;
                    double duration = photoDurations.get(i);
                    
                    videoTimeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                        Duration.seconds(currentTime),
                        e -> {
                            showVideoFrame(frameIndex);
                            videoSeekBar.setValue((double) frameIndex / (totalFrames - 1) * 100);
                        }
                    ));

                    // Smooth transition frames
                    double step = 0.05;
                    for (double t = step; t < duration; t += step) {
                        final double progress = ((double) i + (t / duration)) / (totalFrames - 1) * 100;
                        videoTimeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                            Duration.seconds(currentTime + t),
                            e -> videoSeekBar.setValue(progress)
                        ));
                    }
                    currentTime += duration;
                }
            }

            videoTimeline.setCycleCount(1);
            videoTimeline.setOnFinished(e -> {
                btnVideoPlay.setShape(new javafx.scene.shape.SVGPath() {{ setContent("M 0 0 L 20 10 L 0 20 Z"); }});
                currentVideoIndex = 0;
            });

            videoTimeline.play();
            btnVideoPlay.setShape(new javafx.scene.shape.SVGPath() {{ setContent("M 0 0 L 6 0 L 6 20 L 0 20 Z M 12 0 L 18 0 L 18 20 L 12 20 Z"); }});
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
