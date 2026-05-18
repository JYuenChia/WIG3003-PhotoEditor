package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import java.io.File;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class VideoController {

    @FXML private VBox videoDropZone, videoPlayerContainer;
    @FXML private VBox synthesisControls, trimControls, photoDurationList;
    @FXML private ImageView videoPreviewImage;
    @FXML private Label videoOverlayLabel;
    @FXML private TextArea videoOverlayText;
    @FXML private Slider videoSeekBar;
    @FXML private TextField videoTrimInput;
    @FXML private Button btnTrimFront, btnTrimBack;
    @FXML private ComboBox<String> videoFontCombo;
    @FXML private Slider videoOpacitySlider, videoTextXSlider, videoTextYSlider;
    @FXML private ColorPicker videoTextColorPicker;
    @FXML private ComboBox<String> videoGraphicsCombo;
    @FXML private StackPane videoPreviewStack;
    @FXML private Button btnVideoPlay;
    @FXML private MediaView videoMediaView;

    private List<String> videoPhotos = new ArrayList<>();
    private List<Double> photoDurations = new ArrayList<>();
    private javafx.animation.Timeline videoTimeline;
    private MediaPlayer mediaPlayer;
    private int currentVideoIndex = 0;
    private boolean isVideoMode = false;
    private double trimFrontSec = 0;
    private double trimBackSec = 0;
    private double lastKnownMediaWidth = 600;
    private double lastKnownMediaHeight = 340;
    
    // Singleton for MainController to access
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

        if (videoOverlayText != null) {
            videoOverlayText.textProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null) videoOverlayLabel.setText(newVal);
            });
        }

        if (videoTextColorPicker != null) {
            videoTextColorPicker.setOnAction(e -> updateOverlayStyle());
        }

        if (videoGraphicsCombo != null) {
            videoGraphicsCombo.setOnAction(e -> applyGraphicsOverlay());
        }

        if (videoSeekBar != null) {
            videoSeekBar.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (videoSeekBar.isValueChanging()) {
                    seekByPercentage(newVal.doubleValue());
                }
            });
            // Allow clicking to seek
            videoSeekBar.setOnMouseClicked(event -> {
                double mouseX = event.getX();
                double width = videoSeekBar.getWidth();
                double percentage = (mouseX / width) * 100.0;
                videoSeekBar.setValue(percentage);
                seekByPercentage(percentage);
            });
        }
    }

    private void seekByPercentage(double percentage) {
        if (isVideoMode && mediaPlayer != null) {
            double total = mediaPlayer.getTotalDuration().toSeconds();
            double playableDuration = total - trimFrontSec - trimBackSec;
            
            // Map 0-100% to the range [trimFrontSec, total - trimBackSec]
            double targetSec = trimFrontSec + (percentage / 100.0) * playableDuration;
            mediaPlayer.seek(Duration.seconds(targetSec));
            return;
        }

        if (videoPhotos.isEmpty()) return;
        double total = getTotalDuration();
        double targetTime = (percentage / 100.0) * total;
        
        double cumulative = 0;
        int targetIndex = 0;
        for (int i = 0; i < videoPhotos.size(); i++) {
            cumulative += photoDurations.get(i);
            if (targetTime <= cumulative) {
                targetIndex = i;
                break;
            }
            if (i == videoPhotos.size() - 1) targetIndex = i;
        }
        
        if (targetIndex != currentVideoIndex) {
            currentVideoIndex = targetIndex;
            File f = new File(videoPhotos.get(currentVideoIndex));
            videoPreviewImage.setImage(new Image(f.toURI().toString()));
        }
        
        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            videoTimeline.jumpTo(Duration.seconds(targetTime));
        }
    }

    private void applyGraphicsOverlay() {
        if (videoPreviewStack == null || videoGraphicsCombo == null) return;
        
        // Remove existing graphical overlays (keep only target view and overlay label)
        videoPreviewStack.getChildren().removeIf(node -> 
            node != videoPreviewImage && node != videoOverlayLabel && node != videoMediaView);
        
        String selection = videoGraphicsCombo.getValue();
        if ("None".equals(selection)) return;

        double currentWidth = videoMediaView.getFitWidth() > 0 ? videoMediaView.getFitWidth() : videoPreviewStack.getWidth();
        double currentHeight = videoMediaView.getFitHeight() > 0 ? videoMediaView.getFitHeight() : videoPreviewStack.getHeight();
        
        // Fallback to media/image dimensions if width/height not laid out yet
        if (currentWidth <= 0) {
            if (isVideoMode && mediaPlayer != null) {
                currentWidth = lastKnownMediaWidth;
                currentHeight = lastKnownMediaHeight;
            } else if (videoPreviewImage.getImage() != null) {
                currentWidth = videoPreviewImage.getImage().getWidth();
                currentHeight = videoPreviewImage.getImage().getHeight();
            }
        }
        
        if (currentWidth <= 0) currentWidth = 800; // Final safe fallback
        if (currentHeight <= 0) currentHeight = 500;
        
        if ("Golden Borders".equals(selection)) {
            Region border = new Region();
            border.setStyle("-fx-border-color: radial-gradient(center 50% 50%, radius 100%, #FFD700, #B8860B); " +
                          "-fx-border-width: 12; -fx-border-style: solid; -fx-mouse-transparent: true;");
            border.setPrefSize(currentWidth, currentHeight);
            videoPreviewStack.getChildren().add(border);
        } else if ("White Borders".equals(selection)) {
            Region border = new Region();
            border.setStyle("-fx-border-color: white; -fx-border-width: 12; -fx-border-style: solid; -fx-mouse-transparent: true;");
            border.setPrefSize(currentWidth, currentHeight);
            videoPreviewStack.getChildren().add(border);
        } else if ("Classic Vignette".equals(selection)) {
            Region vignette = new Region();
            vignette.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 80%, transparent, rgba(0,0,0,0.8)); -fx-mouse-transparent: true;");
            vignette.setPrefSize(currentWidth, currentHeight);
            videoPreviewStack.getChildren().add(vignette);
        } else if ("Retro Film".equals(selection)) {
            Region film = new Region();
            film.setPrefSize(currentWidth, currentHeight);
            film.setStyle("-fx-background-color: rgba(60, 40, 0, 0.15); -fx-mouse-transparent: true;");
            
            VBox grain = new VBox();
            grain.setPrefSize(currentWidth, currentHeight);
            grain.setStyle("-fx-background-color: repeating-linear-gradient(from 0px 0px to 2px 2px, rgba(255,255,255,0.05), transparent 1px); " +
                          "-fx-opacity: 0.3; -fx-mouse-transparent: true;");
            
            Region line = new Region();
            line.setPrefWidth(1);
            line.setPrefHeight(currentHeight);
            line.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-mouse-transparent: true;");
            line.setTranslateX(currentWidth * 0.25);

            videoPreviewStack.getChildren().addAll(film, grain, line);
        } else if ("Heart Stickers".equals(selection) || "Star Explosion".equals(selection)) {
            javafx.scene.layout.Pane stickerPane = new javafx.scene.layout.Pane();
            stickerPane.setPrefSize(currentWidth, currentHeight);
            stickerPane.setMouseTransparent(true);
            String symbol = "Heart Stickers".equals(selection) ? "❤" : "✨";
            String color = "Heart Stickers".equals(selection) ? "#FF6B6B" : "#FFD93D";
            
            for (int i = 0; i < 7; i++) {
                Label sticker = new Label(symbol);
                double size = 18 + Math.random() * 20;
                sticker.setStyle("-fx-text-fill: " + color + "; -fx-font-size: " + size + "px; " +
                               "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 5, 0, 0, 2);");
                
                double x, y;
                double borderMargin = 50; 
                int side = (int)(Math.random() * 4);
                if (side == 0) { // Top
                    x = 20 + Math.random() * (currentWidth - 40);
                    y = Math.random() * borderMargin;
                } else if (side == 1) { // Bottom
                    x = 20 + Math.random() * (currentWidth - 40);
                    y = currentHeight - borderMargin - (Math.random() * borderMargin);
                } else if (side == 2) { // Left
                    x = Math.random() * borderMargin;
                    y = 20 + Math.random() * (currentHeight - 40);
                } else { // Right
                    x = currentWidth - borderMargin - (Math.random() * borderMargin);
                    y = 20 + Math.random() * (currentHeight - 40);
                }

                sticker.setLayoutX(x);
                sticker.setLayoutY(y);
                sticker.setRotate(Math.random() * 360);
                stickerPane.getChildren().add(sticker);
            }
            videoPreviewStack.getChildren().add(stickerPane);
        } else if ("Birthday Party".equals(selection)) {
            javafx.scene.layout.Pane bdayPane = new javafx.scene.layout.Pane();
            bdayPane.setPrefSize(currentWidth, currentHeight);
            bdayPane.setMouseTransparent(true);
            
            String[] emojis = {"🎈", "🎉", "🎂", "🎁", "✨"};
            for (int i = 0; i < 7; i++) {
                Label emoji = new Label(emojis[(int)(Math.random() * emojis.length)]);
                emoji.setStyle("-fx-font-size: 24px;");
                
                double x, y;
                double borderMargin = 50;
                int side = (int)(Math.random() * 4);
                if (side == 0) { // Top
                    x = 20 + Math.random() * (currentWidth - 40);
                    y = Math.random() * borderMargin;
                } else if (side == 1) { // Bottom
                    x = 20 + Math.random() * (currentWidth - 40);
                    y = currentHeight - borderMargin - (Math.random() * borderMargin);
                } else if (side == 2) { // Left
                    x = Math.random() * borderMargin;
                    y = 20 + Math.random() * (currentHeight - 40);
                } else { // Right
                    x = currentWidth - borderMargin - (Math.random() * borderMargin);
                    y = 20 + Math.random() * (currentHeight - 40);
                }
                
                emoji.setLayoutX(x);
                emoji.setLayoutY(y);
                bdayPane.getChildren().add(emoji);
            }
            
            Region overlay = new Region();
            overlay.setPrefSize(currentWidth, currentHeight);
            overlay.setStyle("-fx-border-color: #FF69B4; -fx-border-width: 5; -fx-border-style: dashed; -fx-mouse-transparent: true;");
            videoPreviewStack.getChildren().addAll(overlay, bdayPane);
        }
    }

    private void updatePhotoDurationList() {
        if (photoDurationList == null) return;
        photoDurationList.getChildren().clear();
        for (int i = 0; i < videoPhotos.size(); i++) {
            final int index = i;
            HBox row = new HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            
            Label lbl = new Label("Image " + (i + 1) + ":");
            lbl.setMinWidth(60);
            lbl.setStyle("-fx-font-family: 'Poppins Regular', 'Poppins', 'Segoe UI', sans-serif; -fx-font-size: 11; -fx-text-fill: #4A4B57;");
            
            TextField tf = new TextField(String.valueOf(photoDurations.get(i)));
            tf.setPrefWidth(55);
            tf.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-padding: 3 8; -fx-font-size: 11;");
            tf.textProperty().addListener((obs, oldVal, newVal) -> {
                try {
                    if (newVal != null && !newVal.isBlank()) {
                        double val = Double.parseDouble(newVal);
                        photoDurations.set(index, Math.max(0.1, val));
                    }
                } catch (NumberFormatException e) {
                    // Keep previous or default
                }
            });
            
            Label sec = new Label("sec");
            sec.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
            
            row.getChildren().addAll(lbl, tf, sec);
            HBox.setHgrow(tf, javafx.scene.layout.Priority.NEVER);
            photoDurationList.getChildren().add(row);
        }
    }

    @FXML public void handleShareWhatsApp() { 
        if (isVideoMode || !videoPhotos.isEmpty()) {
            // In video mode, we trigger the MainController share directly
            MainController.getInstance().handleShareWhatsApp(); 
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Media");
            alert.setHeaderText(null);
            alert.setContentText("Please upload a video or photos first.");
            alert.showAndWait();
        }
    }
    
    @FXML public void handleShareEmail() { 
        if (isVideoMode || !videoPhotos.isEmpty()) {
            MainController.getInstance().handleShareEmail(); 
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Media");
            alert.setHeaderText(null);
            alert.setContentText("Please upload a video or photos first.");
            alert.showAndWait();
        }
    }

    @FXML public void handleUndo() {
        // Video specific undo if needed, currently resets trim
        handleTrimBackReset();
        handleTrimFrontReset();
    }

    @FXML public void handleRedo() {
        // Placeholder for video redo
    }

    @FXML public void handleDelete() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        videoPhotos.clear();
        photoDurations.clear();
        isVideoMode = false;
        
        videoMediaView.setVisible(false);
        videoMediaView.setManaged(false);
        videoPreviewImage.setVisible(false);
        videoPreviewImage.setManaged(false);
        
        videoPlayerContainer.setVisible(false);
        videoPlayerContainer.setManaged(false);
        videoDropZone.setVisible(true);
        videoDropZone.setManaged(true);
        
        MainController.getInstance().setCurrentImagePath(null);
        MainController.getInstance().setCurrentFileName("No file open");
    }

    @FXML public void handleZoomIn() {
        if (videoPreviewStack != null) {
            double currentScale = videoPreviewStack.getScaleX();
            videoPreviewStack.setScaleX(currentScale + 0.1);
            videoPreviewStack.setScaleY(currentScale + 0.1);
        }
    }

    @FXML public void handleZoomOut() {
        if (videoPreviewStack != null) {
            double currentScale = videoPreviewStack.getScaleX();
            videoPreviewStack.setScaleX(Math.max(0.1, currentScale - 0.1));
            videoPreviewStack.setScaleY(Math.max(0.1, currentScale - 0.1));
        }
    }

    private void handleTrimFrontReset() {
        trimFrontSec = 0;
        if (mediaPlayer != null) mediaPlayer.seek(Duration.ZERO);
    }

    private void handleTrimBackReset() {
        trimBackSec = 0;
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

    public void updateScaling() {
        if (lastKnownMediaWidth <= 0 || lastKnownMediaHeight <= 0) return;

        // Ensure the MediaView and Image containers are visible if they have content
        if (isVideoMode) {
            videoMediaView.setVisible(true);
            videoMediaView.setManaged(true);
            videoPreviewImage.setVisible(false);
            videoPreviewImage.setManaged(false);
        } else if (!videoPhotos.isEmpty()) {
            videoPreviewImage.setVisible(true);
            videoPreviewImage.setManaged(true);
            videoMediaView.setVisible(false);
            videoMediaView.setManaged(false);
        }

        // Use even smaller dimensions to ensure it never hits the bounds
        boolean expanded = MainController.getInstance().isSidebarExpanded();
        double maxWidth = expanded ? 600 : 800;  
        double maxHeight = expanded ? 380 : 500;

        double scale = Math.min(maxWidth / lastKnownMediaWidth, maxHeight / lastKnownMediaHeight);
        double displayWidth = lastKnownMediaWidth * scale;
        double displayHeight = lastKnownMediaHeight * scale;

        videoMediaView.setFitWidth(displayWidth);
        videoMediaView.setFitHeight(displayHeight);
        videoPreviewImage.setFitWidth(displayWidth);
        videoPreviewImage.setFitHeight(displayHeight);
        
        videoPreviewStack.setPrefSize(displayWidth, displayHeight);
        videoPreviewStack.setMaxSize(displayWidth, displayHeight);
        videoPreviewStack.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // Refresh overlays if graphic is active
        if (videoGraphicsCombo != null && videoGraphicsCombo.getValue() != null && !"None".equals(videoGraphicsCombo.getValue())) {
            applyGraphicsOverlay();
        }
    }

    @FXML
    public void handleUploadVideo() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv", "*.avi"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            MainController.getInstance().setCurrentImagePath(file.getAbsolutePath());
            MainController.getInstance().setCurrentFileName(file.getName());
            isVideoMode = true;
            videoPhotos.clear();
            photoDurations.clear();
            
            if (videoTimeline != null) videoTimeline.stop();
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }

            try {
                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                videoMediaView.setMediaPlayer(mediaPlayer);
                
                mediaPlayer.setOnReady(() -> {
                    double duration = media.getDuration().toSeconds();
                    photoDurations.add(duration);
                    videoSeekBar.setMax(100);

                    lastKnownMediaWidth = media.getWidth();
                    lastKnownMediaHeight = media.getHeight();
                    
                    // Force visibility for MediaView when video is ready
                    videoMediaView.setVisible(true);
                    videoMediaView.setManaged(true);
                    videoPreviewImage.setVisible(false);
                    videoPreviewImage.setManaged(false);
                    
                    updateScaling();
                });

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!videoSeekBar.isValueChanging()) {
                        double total = mediaPlayer.getTotalDuration().toSeconds();
                        if (total > 0) {
                            double current = newTime.toSeconds();
                            
                            // Define the playable range
                            double playableDuration = total - trimFrontSec - trimBackSec;
                            
                            // If we exceed the 'back' trim point, stop or loop back to front
                            if (trimBackSec > 0 && current >= (total - trimBackSec)) {
                                mediaPlayer.seek(Duration.seconds(trimFrontSec));
                                if (mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                                    mediaPlayer.pause();
                                    btnVideoPlay.setText("▶");
                                }
                            }
                            
                            // Calculate progress relative to the TRIMMED range
                            // 0% is now trimFrontSec, 100% is (total - trimBackSec)
                            double relativeCurrent = Math.max(0, current - trimFrontSec);
                            double progress = (relativeCurrent / playableDuration) * 100;
                            videoSeekBar.setValue(Math.min(100, Math.max(0, progress)));
                        }
                    }
                });

                mediaPlayer.setOnEndOfMedia(() -> {
                    btnVideoPlay.setText("▶");
                    mediaPlayer.seek(Duration.seconds(trimFrontSec));
                    mediaPlayer.pause();
                });

                switchToPlayerMode();
                videoPreviewImage.setVisible(false);
                videoPreviewImage.setManaged(false);
                videoMediaView.setVisible(true);
                videoMediaView.setManaged(true);
                
                synthesisControls.setVisible(false);
                synthesisControls.setManaged(false);
                trimControls.setVisible(true);
                trimControls.setManaged(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }

            for (File f : files) {
                videoPhotos.add(f.getAbsolutePath());
                photoDurations.add(3.0);
            }
            
            updatePhotoDurationList();
            switchToPlayerMode();
            videoMediaView.setVisible(false);
            videoMediaView.setManaged(false);
            videoPreviewImage.setVisible(true);
            videoPreviewImage.setManaged(true);
            
            showVideoFrame(0);

            // Resize based on first photo within a limited box
            if (!videoPhotos.isEmpty()) {
                File firstFile = new File(videoPhotos.get(0));
                Image firstImg = new Image(firstFile.toURI().toString());
                double w = firstImg.getWidth();
                double h = firstImg.getHeight();
                if (w > 0 && h > 0) {
                    double maxWidth = 800;
                    double maxHeight = 500;
                    double scale = Math.min(maxWidth / w, maxHeight / h);
                    
                    double displayWidth = w * scale;
                    double displayHeight = h * scale;

                    videoPreviewImage.setFitWidth(displayWidth);
                    videoPreviewImage.setFitHeight(displayHeight);
                    videoPreviewStack.setPrefSize(displayWidth, displayHeight);
                    videoPreviewStack.setMaxSize(displayWidth, displayHeight);
                    videoPreviewStack.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                }
            }

            synthesisControls.setVisible(true);
            synthesisControls.setManaged(true);
            trimControls.setVisible(false);
            trimControls.setManaged(false);
        }
    }

    @FXML
    public void handleTrimFront() {
        if (!isVideoMode || mediaPlayer == null || videoTrimInput.getText().isEmpty()) return;
        try {
            double trimSec = Double.parseDouble(videoTrimInput.getText());
            double currentTotal = mediaPlayer.getTotalDuration().toSeconds();
            if (trimSec >= (currentTotal - trimBackSec)) return;

            trimFrontSec = trimSec;
            mediaPlayer.seek(Duration.seconds(trimFrontSec));
            videoSeekBar.setValue(0); // Immediately reset slider to the new start point
            
            // Visual feedback for buttons
            btnTrimFront.setStyle("-fx-background-color: #A04000; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            btnTrimBack.setStyle("-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText("Video will now start from " + trimSec + "s");
            alert.show();
        } catch (Exception e) {
            videoTrimInput.setText("");
        }
    }

    @FXML
    public void handleTrimBack() {
        if (!isVideoMode || mediaPlayer == null || videoTrimInput.getText().isEmpty()) return;
        try {
            double trimSec = Double.parseDouble(videoTrimInput.getText());
            double currentTotal = mediaPlayer.getTotalDuration().toSeconds();
            if (trimSec >= (currentTotal - trimFrontSec)) return;

            trimBackSec = trimSec;
            
            // Visual feedback for buttons
            btnTrimBack.setStyle("-fx-background-color: #A04000; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            btnTrimFront.setStyle("-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText("Video will now cut " + trimSec + "s from the back.");
            alert.show();
        } catch (Exception e) {
            videoTrimInput.setText("");
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

        List<String> editedFiles = main.getEditedFiles();
        Properties annotationsDB = main.getAnnotationsDB();
        List<String> favouritedPaths = new ArrayList<>();

        for (String path : editedFiles) {
            File f = new File(path);
            String hash = getFileHash(f);
            if (annotationsDB.containsKey(hash)) {
                favouritedPaths.add(path);
            }
        }

        if (favouritedPaths.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Favourites");
            alert.setHeaderText(null);
            alert.setContentText("You haven't marked any photos as favourites in the gallery yet!");
            alert.showAndWait();
            return;
        }

        // Pop up selection modal
        showFavouritesSelectionModal(favouritedPaths);
    }

    private void showFavouritesSelectionModal(List<String> favouritedPaths) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Select Favourites to Synthesize");
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new javafx.geometry.Insets(20));
        root.setStyle("-fx-background-color: #F8F9FA;");

        Label title = new Label("Select Photos for Video");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1A1D2E;");

        ScrollPane scrollPane = new ScrollPane();
        FlowPane gridContainer = new FlowPane();
        gridContainer.setHgap(15);
        gridContainer.setVgap(15);
        gridContainer.setPadding(new javafx.geometry.Insets(10));
        gridContainer.setStyle("-fx-background-color: white;");
        
        scrollPane.setContent(gridContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);

        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String path : favouritedPaths) {
            VBox itemBox = new VBox(5);
            itemBox.setAlignment(javafx.geometry.Pos.CENTER);
            itemBox.setStyle("-fx-border-color: #E0E0E0; -fx-border-radius: 8; -fx-padding: 8; -fx-background-color: #FFFFFF;");

            File f = new File(path);
            ImageView iv = new ImageView(new Image(f.toURI().toString()));
            iv.setFitWidth(100);
            iv.setFitHeight(100);
            iv.setPreserveRatio(true);

            CheckBox cb = new CheckBox();
            cb.setUserData(path);
            cb.setSelected(true);
            checkBoxes.add(cb);
            
            itemBox.getChildren().addAll(iv, cb);
            itemBox.setOnMouseClicked(e -> {
                cb.setSelected(!cb.isSelected());
                if (cb.isSelected()) {
                    itemBox.setStyle("-fx-border-color: #4F5BD5; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 7; -fx-background-color: #EEF2FF;");
                } else {
                    itemBox.setStyle("-fx-border-color: #E0E0E0; -fx-border-width: 1; -fx-border-radius: 8; -fx-padding: 8; -fx-background-color: #FFFFFF;");
                }
                // Preview logic
                videoPreviewImage.setImage(iv.getImage());
            });
            // Set initial state
            itemBox.setStyle("-fx-border-color: #4F5BD5; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 7; -fx-background-color: #EEF2FF;");
            
            gridContainer.getChildren().add(itemBox);
        }

        Button btnSelectAll = new Button("Select All");
        btnSelectAll.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #4F5BD5; -fx-text-fill: #4F5BD5; -fx-border-radius: 5;");
        btnSelectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(true)));
        
        Button btnDeselectAll = new Button("Deselect All");
        btnDeselectAll.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #8892B0; -fx-text-fill: #8892B0; -fx-border-radius: 5;");
        btnDeselectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(false)));

        HBox topActions = new HBox(10, btnSelectAll, btnDeselectAll);

        Button btnSynthesize = new Button("Create Video");
        btnSynthesize.setStyle("-fx-background-color: #4F5BD5; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
        btnSynthesize.setOnAction(e -> {
            videoPhotos.clear();
            photoDurations.clear();
            for (CheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    videoPhotos.add((String) cb.getUserData());
                    photoDurations.add(3.0);
                }
            }
            if (!videoPhotos.isEmpty()) {
                isVideoMode = false;
                switchToPlayerMode();
                updatePhotoDurationList();
                showVideoFrame(0);
                synthesisControls.setVisible(true);
                synthesisControls.setManaged(true);
                trimControls.setVisible(false);
                trimControls.setManaged(false);
                stage.close();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setContentText("Please select at least one photo.");
                alert.show();
            }
        });

        HBox bottomActions = new HBox(10, btnSynthesize);
        bottomActions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, topActions, scrollPane, bottomActions);
        stage.setScene(new javafx.scene.Scene(root, 520, 600));
        stage.show();
    }

    private void showVideoFrame(int index) {
        if (videoPhotos.isEmpty()) return;
        currentVideoIndex = index;
        File f = new File(videoPhotos.get(index));
        Image img = new Image(f.toURI().toString());
        videoPreviewImage.setImage(img);

        lastKnownMediaWidth = img.getWidth();
        lastKnownMediaHeight = img.getHeight();
        updateScaling();
        
        double timeBefore = 0;
        for (int i = 0; i < index; i++) timeBefore += photoDurations.get(i);
        double total = getTotalDuration();
        if (!videoSeekBar.isValueChanging()) {
            videoSeekBar.setValue(total > 0 ? (timeBefore / total) * 100 : 0);
        }
    }

    @FXML
    public void handleVideoPlayPause() {
        if (isVideoMode && mediaPlayer != null) {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                btnVideoPlay.setText("▶");
            } else {
                mediaPlayer.play();
                btnVideoPlay.setText("⏸");
            }
            return;
        }

        if (videoPhotos.isEmpty()) return;

        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            videoTimeline.pause();
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null); // Clear SVG shape if any
            return;
        } 
        
        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.PAUSED) {
            videoTimeline.play();
            btnVideoPlay.setText("⏸");
            btnVideoPlay.setShape(null);
            return;
        }

        if (videoTimeline != null) videoTimeline.stop();
        
        videoTimeline = new javafx.animation.Timeline();
        final int totalFrames = videoPhotos.size();
        final double totalDuration = getTotalDuration();
        double currentTime = 0;

        // Shared playback logic for both Synthesis and Video modes
        for (int i = 0; i < totalFrames; i++) {
            final int frameIndex = i;
            double duration = photoDurations.get(i);
            
            // Show the frame/image at the start of its duration
            videoTimeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                Duration.seconds(currentTime),
                e -> {
                    showVideoFrame(frameIndex);
                    double timeBefore = 0;
                    for(int k=0; k<frameIndex; k++) timeBefore += photoDurations.get(k);
                    videoSeekBar.setValue((timeBefore / totalDuration) * 100);
                }
            ));

            // Smooth seekbar updating during this part's duration
            double step = 0.1;
            for (double t = step; t < duration; t += step) {
                final double timeAtStep = currentTime + t;
                videoTimeline.getKeyFrames().add(new javafx.animation.KeyFrame(
                    Duration.seconds(timeAtStep),
                    e -> {
                        if (!videoSeekBar.isValueChanging()) {
                            videoSeekBar.setValue((timeAtStep / totalDuration) * 100);
                        }
                    }
                ));
            }
            currentTime += duration;
        }

        videoTimeline.setCycleCount(1);
        videoTimeline.setOnFinished(e -> {
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null);
            currentVideoIndex = 0;
            videoSeekBar.setValue(100);
        });

        videoTimeline.play();
        btnVideoPlay.setText("⏸");
        btnVideoPlay.setShape(null);
    }

    @FXML
    public void handleSkipPrevious() {
        if (isVideoMode && mediaPlayer != null) {
            mediaPlayer.seek(Duration.seconds(trimFrontSec));
            mediaPlayer.play();
            btnVideoPlay.setText("⏸");
            return;
        }
        if (videoPhotos.isEmpty()) return;
        boolean wasPlaying = videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING;
        
        if (videoTimeline != null) videoTimeline.stop();
        
        currentVideoIndex = Math.max(0, currentVideoIndex - 1);
        showVideoFrame(currentVideoIndex);
        
        if (wasPlaying) {
            handleVideoPlayPause();
            double timeBefore = 0;
            for (int i = 0; i < currentVideoIndex; i++) timeBefore += photoDurations.get(i);
            videoTimeline.jumpTo(Duration.seconds(timeBefore));
        } else {
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null);
        }
    }

    @FXML
    public void handleSkipNext() {
        if (isVideoMode && mediaPlayer != null) {
            double total = mediaPlayer.getTotalDuration().toSeconds();
            mediaPlayer.seek(Duration.seconds(total - trimBackSec));
            mediaPlayer.pause();
            btnVideoPlay.setText("▶");
            return;
        }
        if (videoPhotos.isEmpty()) return;
        boolean wasPlaying = videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING;
        
        if (videoTimeline != null) videoTimeline.stop();
        
        currentVideoIndex = Math.min(videoPhotos.size() - 1, currentVideoIndex + 1);
        showVideoFrame(currentVideoIndex);

        if (wasPlaying) {
            handleVideoPlayPause();
            double timeBefore = 0;
            for (int i = 0; i < currentVideoIndex; i++) timeBefore += photoDurations.get(i);
            videoTimeline.jumpTo(Duration.seconds(timeBefore));
        } else {
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null);
        }
    }

    private double getTotalDuration() {
        double total = 0;
        for (Double d : photoDurations) total += d;
        return total;
    }

    @FXML public void showShareTab() {
        MainController.getInstance().showShareTab();
    }

    @FXML public void handleRenderSave() {
        if (videoPhotos.isEmpty() && !isVideoMode) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("No media to save. Please upload a video or synthesize photos first.");
            alert.show();
            return;
        }

        if (mediaPlayer == null && isVideoMode) return;

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Render Edited Video");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
        fc.setInitialFileName("edited_video.mp4");
        File file = fc.showSaveDialog(null);

        if (file != null) {
            performFullRender(file);
        }
    }

    @FXML public void handleSaveToGallery() {
        if (!isVideoMode && videoPhotos.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Please upload a video or synthesize photos first.");
            alert.show();
            return;
        }

        // Create a temporary file in common gallery location
        File galleryDir = new File("Edited_Gallery");
        if (!galleryDir.exists()) galleryDir.mkdirs();
        
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String ext = isVideoMode ? ".mp4" : ".mp4"; // Always save as video for synthesis
        File target = new File(galleryDir, "video_" + timestamp + ext);
        
        performFullRender(target);
    }

    private File resolveCurrentVideoSource() {
        if (isVideoMode) {
            String currentPath = MainController.getInstance().getCurrentImagePath();
            if (currentPath == null || currentPath.isBlank()) return null;
            File file = new File(currentPath);
            return file.exists() ? file : null;
        } else if (!videoPhotos.isEmpty()) {
            // Return one of the photos just to satisfy the grabber, 
            // but the renderer logic should know how to handle the photo-to-video mode
            return new File(videoPhotos.get(0));
        }
        return null;
    }

    private File ensureExtension(File file, String extension) {
        if (file.getName().toLowerCase().endsWith(extension.toLowerCase())) return file;
        String parent = file.getParent();
        return parent == null ? new File(file.getName() + extension) : new File(parent, file.getName() + extension);
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

    private void performFullRender(File outFile) {
        if (outFile == null) return;
        outFile = ensureExtension(outFile, ".mp4");
        final File renderTarget = outFile;

        if (isVideoMode) {
            File source = resolveCurrentVideoSource();
            if (source == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText("No video source available to render.");
                alert.show();
                return;
            }

            double total = 0;
            if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                total = mediaPlayer.getTotalDuration().toSeconds() - trimFrontSec - trimBackSec;
                if (total < 0) total = 0;
            }

            final double startSec = trimFrontSec;
            final double durationSec = total;

            ProgressIndicator pi = new ProgressIndicator(0);
            Alert progressAlert = new Alert(Alert.AlertType.NONE);
            progressAlert.setTitle("Rendering");
            progressAlert.getDialogPane().setContent(pi);
            progressAlert.show();

            VideoRenderer.renderVideoWithOverlays(source, outFile, videoPreviewStack, mediaPlayer, startSec, durationSec,
                new VideoRenderer.RenderProgressCallback() {
                    @Override
                    public void onProgress(double progress) {
                        javafx.application.Platform.runLater(() -> pi.setProgress(progress));
                    }

                    @Override
                    public void onComplete(File out) {
                        javafx.application.Platform.runLater(() -> {
                            progressAlert.close();
                            try {
                                // copy to Edited_Gallery and refresh main list via MainController helper
                                File galleryDir = new File("Edited_Gallery");
                                if (!galleryDir.exists()) galleryDir.mkdirs();
                                File dest = new File(galleryDir, out.getName());
                                Files.copy(out.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                try {
                                    MainController.getInstance().updateSavedMediaStateExternal(dest);
                                } catch (Exception ignored) {}
                            } catch (Exception ignored) {}
                            Alert done = new Alert(Alert.AlertType.INFORMATION);
                            done.setContentText("Render complete: " + out.getAbsolutePath());
                            done.show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        javafx.application.Platform.runLater(() -> {
                            progressAlert.close();
                            Alert err = new Alert(Alert.AlertType.ERROR);
                            err.setContentText("Render failed: " + message);
                            err.show();
                        });
                    }
                }
            );
            return;
        }

        // Photo-synthesis mode: build a slideshow-style MP4 using JavaCV directly
        new Thread(() -> {
            try {
                if (videoPhotos.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.WARNING);
                        a.setContentText("No photos to synthesize.");
                        a.show();
                    });
                    return;
                }

                File first = new File(videoPhotos.get(0));
                BufferedImage sample = ImageIO.read(first);
                int width = sample.getWidth();
                int height = sample.getHeight();
                if (width % 2 != 0) width--;
                if (height % 2 != 0) height--;

                double frameRate = 30.0;
                Java2DFrameConverter converter = new Java2DFrameConverter();
                FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(renderTarget, width, height);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setFormat("mp4");
                recorder.setFrameRate(frameRate);
                recorder.setVideoBitrate(2000000);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.start();

                // capture overlay settings from JavaFX thread
                final String overlayText = videoOverlayText != null ? videoOverlayText.getText() : "";
                final double tx = videoOverlayLabel != null ? videoOverlayLabel.getTranslateX() : 0;
                final double ty = videoOverlayLabel != null ? videoOverlayLabel.getTranslateY() : 0;
                final double op = videoOverlayLabel != null ? videoOverlayLabel.getOpacity() : 1.0;
                final javafx.scene.paint.Color fxColor = videoOverlayLabel != null && videoOverlayLabel.getTextFill() instanceof javafx.scene.paint.Color
                        ? (javafx.scene.paint.Color) videoOverlayLabel.getTextFill() : javafx.scene.paint.Color.WHITE;
                final java.awt.Color awtColor = new java.awt.Color((float) fxColor.getRed(), (float) fxColor.getGreen(), (float) fxColor.getBlue(), (float) fxColor.getOpacity());

                for (int idx = 0; idx < videoPhotos.size(); idx++) {
                    File f = new File(videoPhotos.get(idx));
                    BufferedImage img = ImageIO.read(f);
                    if (img.getWidth() != width || img.getHeight() != height) {
                        BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics2D g = tmp.createGraphics();
                        g.drawImage(img, 0, 0, width, height, null);
                        g.dispose();
                        img = tmp;
                    }

                    // apply overlay onto a copy
                    BufferedImage framed = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = framed.createGraphics();
                    g.drawImage(img, 0, 0, null);
                    g.setColor(awtColor);
                    g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) op));
                    g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 48));
                    int x = (int) ((width / 2.0) + tx * (width / 800.0));
                    int y = (int) ((height / 2.0) + ty * (height / 400.0));
                    if (overlayText != null && !overlayText.isBlank()) {
                        g.drawString(overlayText, x, y);
                    }
                    g.dispose();

                    int frames = (int) Math.max(1, Math.round(photoDurations.get(idx) * frameRate));
                    for (int fidx = 0; fidx < frames; fidx++) {
                        recorder.record(converter.convert(framed));
                    }
                }

                recorder.stop();
                recorder.release();

                // copy to gallery and refresh
                File galleryDir = new File("Edited_Gallery");
                if (!galleryDir.exists()) galleryDir.mkdirs();
                File dest = new File(galleryDir, renderTarget.getName());
                Files.copy(renderTarget.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                javafx.application.Platform.runLater(() -> {
                    try {
                        MainController.getInstance().updateSavedMediaStateExternal(dest);
                    } catch (Exception ignored) {}
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setContentText("Synthesis complete: " + dest.getAbsolutePath());
                    done.show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setContentText("Synthesis failed: " + e.getMessage());
                    err.show();
                });
            }
        }).start();
    }
}
