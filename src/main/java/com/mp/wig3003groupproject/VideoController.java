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

    @FXML
    private VBox videoDropZone, videoPlayerContainer;
    @FXML
    private VBox synthesisControls, trimControls, photoDurationList;
    @FXML
    private ImageView videoPreviewImage;
    @FXML
    private Label videoOverlayLabel;
    @FXML
    private TextArea videoOverlayText;
    @FXML
    private Slider videoSeekBar;
    @FXML
    private TextField videoTrimInput;
    @FXML
    private Button btnTrimFront, btnTrimBack;
    @FXML
    private ComboBox<String> videoFontCombo;
    @FXML
    private Slider videoOpacitySlider, videoTextXSlider, videoTextYSlider, videoFontSizeSlider;
    @FXML
    private ColorPicker videoTextColorPicker;
    @FXML
    private ComboBox<String> videoGraphicsCombo;
    @FXML
    private StackPane videoPreviewStack;
    @FXML
    private StackPane videoCenterPane;
    @FXML
    private Button btnVideoPlay;
    @FXML
    private Button btnBackToUpload;
    @FXML
    private MediaView videoMediaView;

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
    /** Fixed display size set once when a photo set is loaded; reused every frame to keep size stable. */
    private double storedFitWidth = 0;
    private double storedFitHeight = 0;
    /** Animation timeline for preview graphic overlay effects. */
    private javafx.animation.Timeline graphicsTimeline;
    /** Tracks all active overlay animations to play/pause with the video */
    private java.util.List<javafx.animation.Animation> activeGraphicAnimations = new java.util.ArrayList<>();

    private void setGraphicAnimationsPlaying(boolean play) {
        if (activeGraphicAnimations == null) return;
        for (javafx.animation.Animation a : activeGraphicAnimations) {
            if (play) a.play();
            else a.pause();
        }
    }

    // Singleton for MainController to access
    private static VideoController instance;

    public VideoController() {
        instance = this;
    }

    public static VideoController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        if (videoFontCombo != null) {
            videoFontCombo.setItems(
                    javafx.collections.FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
            videoFontCombo.setValue("System");
            videoFontCombo.setOnAction(e -> updateOverlayStyle());
        }
        if (videoFontSizeSlider != null) {
            videoFontSizeSlider.valueProperty().addListener((obs, oldV, newV) -> updateOverlayStyle());
        }

        if (videoOpacitySlider != null) {
            videoOpacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> updateOverlayStyle());
        }

        if (videoTextXSlider != null) {
            videoTextXSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null)
                    videoOverlayLabel.setTranslateX(newVal.doubleValue());
            });
        }

        if (videoTextYSlider != null) {
            videoTextYSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null)
                    videoOverlayLabel.setTranslateY(newVal.doubleValue());
            });
        }

        if (videoOverlayText != null) {
            videoOverlayText.textProperty().addListener((obs, oldVal, newVal) -> {
                if (videoOverlayLabel != null)
                    videoOverlayLabel.setText(newVal);
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

        // Clip the centre workspace so zoom never overflows the panel boundary
        if (videoCenterPane != null) {
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(videoCenterPane.widthProperty());
            clip.heightProperty().bind(videoCenterPane.heightProperty());
            videoCenterPane.setClip(clip);
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

        if (videoPhotos.isEmpty())
            return;
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
            if (i == videoPhotos.size() - 1)
                targetIndex = i;
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

        // Stop any running overlay animation
        if (graphicsTimeline != null) { graphicsTimeline.stop(); graphicsTimeline = null; }
        for (javafx.animation.Animation a : activeGraphicAnimations) { a.stop(); }
        activeGraphicAnimations.clear();

        // Remove previous overlay nodes (keep image, media, text label)
        videoPreviewStack.getChildren().removeIf(
                node -> node != videoPreviewImage && node != videoOverlayLabel && node != videoMediaView);

        String sel = videoGraphicsCombo.getValue();
        if ("None".equals(sel) || sel == null) return;

        double cw = storedFitWidth  > 0 ? storedFitWidth  : (videoPreviewStack.getWidth()  > 0 ? videoPreviewStack.getWidth()  : 800);
        double ch = storedFitHeight > 0 ? storedFitHeight : (videoPreviewStack.getHeight() > 0 ? videoPreviewStack.getHeight() : 500);

        switch (sel) {
            case "Classic Vignette": {
                javafx.scene.layout.Region v = new javafx.scene.layout.Region();
                v.setMouseTransparent(true);
                v.setPrefSize(cw, ch);
                v.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 75%, transparent, rgba(0,0,0,0.75));");
                videoPreviewStack.getChildren().add(v);
                break;
            }
            case "Golden Borders": {
                javafx.scene.layout.Region border = new javafx.scene.layout.Region();
                border.setMouseTransparent(true);
                border.setPrefSize(cw, ch);
                border.setMaxSize(cw, ch);
                // Static border, matching size exactly
                border.setStyle("-fx-border-color: linear-gradient(to bottom right, #FFD700, #FFA500, #FFD700); -fx-border-width: 12; -fx-border-style: solid inside;");
                videoPreviewStack.getChildren().add(border);
                break;
            }
            case "White Borders": {
                javafx.scene.layout.Region border = new javafx.scene.layout.Region();
                border.setMouseTransparent(true);
                border.setPrefSize(cw, ch);
                border.setMaxSize(cw, ch);
                border.setStyle("-fx-border-color: white; -fx-border-width: 12; -fx-border-style: solid inside;");
                videoPreviewStack.getChildren().add(border);
                break;
            }
            case "Retro Film": {
                javafx.scene.layout.Region tint = new javafx.scene.layout.Region();
                tint.setMouseTransparent(true);
                tint.setPrefSize(cw, ch);
                tint.setStyle("-fx-background-color: rgba(160,100,20,0.15);");
                // Animated scanline, clipped to container
                javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
                pane.setMouseTransparent(true);
                pane.setPrefSize(cw, ch);
                pane.setMaxSize(cw, ch);
                pane.setClip(new javafx.scene.shape.Rectangle(cw, ch));
                javafx.scene.shape.Line scanline = new javafx.scene.shape.Line(0, 0, cw, 0);
                scanline.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.35));
                scanline.setStrokeWidth(2);
                pane.getChildren().add(scanline);
                videoPreviewStack.getChildren().addAll(tint, pane);
                javafx.animation.Timeline tl = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(scanline.translateYProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1.5),
                        new javafx.animation.KeyValue(scanline.translateYProperty(), ch)));
                tl.setCycleCount(javafx.animation.Animation.INDEFINITE);
                activeGraphicAnimations.add(tl);
                break;
            }
            case "Floating Hearts": {
                javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
                pane.setMouseTransparent(true);
                pane.setPrefSize(cw, ch);
                pane.setMaxSize(cw, ch);
                pane.setClip(new javafx.scene.shape.Rectangle(cw, ch));
                videoPreviewStack.getChildren().add(pane);
                java.util.Random rnd = new java.util.Random(7);
                for (int i = 0; i < 10; i++) {
                    javafx.scene.text.Text heart = new javafx.scene.text.Text("♥");
                    double sz = 16 + rnd.nextDouble() * 18;
                    heart.setStyle("-fx-font-size: " + sz + ";");
                    heart.setFill(javafx.scene.paint.Color.rgb(255, 60 + (int)(rnd.nextDouble()*80), 120, 0.85));
                    heart.setEffect(new javafx.scene.effect.DropShadow(6, javafx.scene.paint.Color.rgb(255,0,80,0.4)));
                    double startX = 15 + rnd.nextDouble() * (cw - 30);
                    heart.setLayoutX(startX);
                    heart.setLayoutY(ch + 30);
                    pane.getChildren().add(heart);
                    double dur = 2.5 + rnd.nextDouble() * 2.0;
                    double delay = rnd.nextDouble() * dur;
                    javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(
                            javafx.util.Duration.seconds(dur), heart);
                    tt.setFromY(0); tt.setToY(-(ch + 60));
                    tt.setDelay(javafx.util.Duration.seconds(delay));
                    tt.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                            javafx.util.Duration.seconds(dur), heart);
                    ft.setFromValue(0); ft.setToValue(1);
                    ft.setAutoReverse(true);
                    ft.setDelay(javafx.util.Duration.seconds(delay));
                    ft.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    activeGraphicAnimations.add(tt);
                    activeGraphicAnimations.add(ft);
                }
                break;
            }
            case "Starfield": {
                javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
                pane.setMouseTransparent(true);
                pane.setPrefSize(cw, ch);
                pane.setMaxSize(cw, ch);
                pane.setClip(new javafx.scene.shape.Rectangle(cw, ch));
                videoPreviewStack.getChildren().add(pane);
                java.util.Random rnd = new java.util.Random(42);
                for (int i = 0; i < 22; i++) {
                    double r = 3 + rnd.nextDouble() * 5;
                    javafx.scene.shape.Circle star = new javafx.scene.shape.Circle(r);
                    star.setFill(javafx.scene.paint.Color.rgb(255, 240, 100, 0.9));
                    star.setEffect(new javafx.scene.effect.Glow(0.9));
                    star.setCenterX(rnd.nextDouble() * cw);
                    star.setCenterY(rnd.nextDouble() * ch);
                    pane.getChildren().add(star);
                    double dur = 0.4 + rnd.nextDouble() * 0.8;
                    javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
                            javafx.util.Duration.seconds(dur), star);
                    st.setFromX(0.3); st.setToX(1.4); st.setAutoReverse(true);
                    st.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    st.setDelay(javafx.util.Duration.seconds(rnd.nextDouble() * dur));
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                            javafx.util.Duration.seconds(dur), star);
                    ft.setFromValue(0.2); ft.setToValue(1.0); ft.setAutoReverse(true);
                    ft.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    ft.setDelay(javafx.util.Duration.seconds(rnd.nextDouble() * dur));
                    activeGraphicAnimations.add(st);
                    activeGraphicAnimations.add(ft);
                }
                break;
            }
            case "Confetti Rain": {
                javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
                pane.setMouseTransparent(true);
                pane.setPrefSize(cw, ch);
                pane.setMaxSize(cw, ch);
                pane.setClip(new javafx.scene.shape.Rectangle(cw, ch));
                javafx.scene.paint.Color[] colors = {
                    javafx.scene.paint.Color.rgb(255,80,80), javafx.scene.paint.Color.rgb(80,200,120),
                    javafx.scene.paint.Color.rgb(80,140,255), javafx.scene.paint.Color.rgb(255,200,0),
                    javafx.scene.paint.Color.rgb(200,80,255)
                };
                java.util.Random rnd = new java.util.Random(123);
                videoPreviewStack.getChildren().add(pane);
                for (int i = 0; i < 20; i++) {
                    double w2 = 8 + rnd.nextDouble() * 8, h2 = 5 + rnd.nextDouble() * 5;
                    javafx.scene.shape.Rectangle piece = new javafx.scene.shape.Rectangle(w2, h2);
                    piece.setFill(colors[i % colors.length]);
                    piece.setOpacity(0.85);
                    piece.setLayoutX(rnd.nextDouble() * cw);
                    piece.setLayoutY(-20);
                    pane.getChildren().add(piece);
                    double dur = 1.8 + rnd.nextDouble() * 1.5;
                    double delay = rnd.nextDouble() * dur;
                    javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(
                            javafx.util.Duration.seconds(dur), piece);
                    tt.setFromY(0); tt.setToY(ch + 40);
                    tt.setDelay(javafx.util.Duration.seconds(delay));
                    tt.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    javafx.animation.RotateTransition rt = new javafx.animation.RotateTransition(
                            javafx.util.Duration.seconds(dur / 2), piece);
                    rt.setFromAngle(0); rt.setToAngle(360); rt.setCycleCount(javafx.animation.Animation.INDEFINITE);
                    rt.setDelay(javafx.util.Duration.seconds(delay));
                    activeGraphicAnimations.add(tt);
                    activeGraphicAnimations.add(rt);
                }
                break;
            }
        }
        boolean isPlaying = btnVideoPlay != null && "⏸".equals(btnVideoPlay.getText());
        setGraphicAnimationsPlaying(isPlaying);
    }

    private void updatePhotoDurationList() {
        if (photoDurationList == null)
            return;
        photoDurationList.getChildren().clear();
        for (int i = 0; i < videoPhotos.size(); i++) {
            final int index = i;
            HBox row = new HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);

            Label lbl = new Label("Image " + (i + 1) + ":");
            lbl.setMinWidth(60);
            lbl.setStyle(
                    "-fx-font-family: 'Poppins Regular', 'Poppins', 'Segoe UI', sans-serif; -fx-font-size: 11; -fx-text-fill: #4A4B57;");

            TextField tf = new TextField(String.valueOf(photoDurations.get(i)));
            tf.setPrefWidth(55);
            tf.setStyle(
                    "-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-padding: 3 8; -fx-font-size: 11;");
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

    @FXML
    public void handleShareWhatsApp() {
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

    @FXML
    public void handleShareEmail() {
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

    @FXML
    public void handleUndo() {
        // Video specific undo if needed, currently resets trim
        handleTrimBackReset();
        handleTrimFrontReset();
    }

    @FXML
    public void handleRedo() {
        // Placeholder for video redo
    }

    @FXML
    public void handleDelete() {
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
        // Hide the back button when returning to the drop zone
        if (btnBackToUpload != null) {
            btnBackToUpload.setVisible(false);
            btnBackToUpload.setManaged(false);
        }

        MainController.getInstance().setCurrentImagePath(null);
        MainController.getInstance().setCurrentFileName("No file open");
    }

    /**
     * Returns the video pane to its initial "drop zone" state without navigating
     * away.
     */
    @FXML
    public void handleBackToUpload() {
        // Stop any active playback
        if (videoTimeline != null) {
            videoTimeline.stop();
            videoTimeline = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        // Clear all session data
        videoPhotos.clear();
        photoDurations.clear();
        isVideoMode = false;
        currentVideoIndex = 0;
        trimFrontSec = 0;
        trimBackSec = 0;

        // Reset preview
        videoPreviewImage.setImage(null);
        videoMediaView.setMediaPlayer(null);
        videoMediaView.setVisible(false);
        videoMediaView.setManaged(false);
        videoPreviewImage.setVisible(false);
        videoPreviewImage.setManaged(false);

        // Reset zoom
        if (videoPreviewStack != null) {
            videoPreviewStack.setScaleX(1.0);
            videoPreviewStack.setScaleY(1.0);
        }

        // Reset play button
        if (btnVideoPlay != null)
            btnVideoPlay.setText("▶");
        if (videoSeekBar != null)
            videoSeekBar.setValue(0);

        // Restore UI to the upload/drop-zone screen
        videoPlayerContainer.setVisible(false);
        videoPlayerContainer.setManaged(false);
        videoDropZone.setVisible(true);
        videoDropZone.setManaged(true);

        // Hide back button (we're back at the start)
        if (btnBackToUpload != null) {
            btnBackToUpload.setVisible(false);
            btnBackToUpload.setManaged(false);
        }

        // Restore default controls visibility
        if (synthesisControls != null) {
            synthesisControls.setVisible(true);
            synthesisControls.setManaged(true);
        }
        if (trimControls != null) {
            trimControls.setVisible(false);
            trimControls.setManaged(false);
        }
        if (photoDurationList != null)
            photoDurationList.getChildren().clear();

        MainController.getInstance().setCurrentImagePath(null);
        MainController.getInstance().setCurrentFileName("No file open");
    }

    @FXML
    public void handleZoomIn() {
        if (videoPreviewStack != null) {
            double currentScale = videoPreviewStack.getScaleX();
            double newScale = Math.min(5.0, currentScale + 0.1);
            videoPreviewStack.setScaleX(newScale);
            videoPreviewStack.setScaleY(newScale);
        }
    }

    @FXML
    public void handleZoomOut() {
        if (videoPreviewStack != null) {
            double currentScale = videoPreviewStack.getScaleX();
            double newScale = Math.max(0.1, currentScale - 0.1);
            videoPreviewStack.setScaleX(newScale);
            videoPreviewStack.setScaleY(newScale);
        }
    }

    @FXML
    public void handleResetAll() {
        // Reset zoom
        if (videoPreviewStack != null) {
            videoPreviewStack.setScaleX(1.0);
            videoPreviewStack.setScaleY(1.0);
        }
        // Reset overlay text
        if (videoOverlayText != null)
            videoOverlayText.clear();
        if (videoOverlayLabel != null) {
            videoOverlayLabel.setText("");
            videoOverlayLabel.setTranslateX(0);
            videoOverlayLabel.setTranslateY(0);
            videoOverlayLabel.setOpacity(1.0);
            videoOverlayLabel.setStyle(
                    "-fx-text-fill: white; -fx-font-size: 20; -fx-padding: 15; -fx-font-family: 'Georgia'; -fx-font-style: italic;");
        }
        // Reset sliders
        if (videoOpacitySlider != null)
            videoOpacitySlider.setValue(1.0);
        if (videoTextXSlider != null)
            videoTextXSlider.setValue(0);
        if (videoTextYSlider != null)
            videoTextYSlider.setValue(0);
        // Reset colour picker
        if (videoTextColorPicker != null)
            videoTextColorPicker.setValue(javafx.scene.paint.Color.WHITE);
        // Reset font
        if (videoFontCombo != null)
            videoFontCombo.setValue("System");
        // Reset graphics overlay
        if (videoGraphicsCombo != null) {
            videoGraphicsCombo.setValue("None");
            applyGraphicsOverlay();
        }
        // Reset trim
        trimFrontSec = 0;
        trimBackSec = 0;
        if (videoTrimInput != null)
            videoTrimInput.clear();
        if (btnTrimFront != null)
            btnTrimFront.setStyle(
                    "-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
        if (btnTrimBack != null)
            btnTrimBack.setStyle(
                    "-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
    }

    private void handleTrimFrontReset() {
        trimFrontSec = 0;
        if (mediaPlayer != null)
            mediaPlayer.seek(Duration.ZERO);
    }

    private void handleTrimBackReset() {
        trimBackSec = 0;
    }
    private void updateTextSliderBounds(double displayWidth, double displayHeight) {
        if (videoTextXSlider != null) {
            videoTextXSlider.setMin(-displayWidth / 2);
            videoTextXSlider.setMax(displayWidth / 2);
        }
        if (videoTextYSlider != null) {
            videoTextYSlider.setMin(-displayHeight / 2);
            videoTextYSlider.setMax(displayHeight / 2);
        }
    }

    private void updateOverlayStyle() {
        if (videoOverlayLabel == null)
            return;

        String color = "#" + videoTextColorPicker.getValue().toString().substring(2, 8);
        String font = videoFontCombo.getValue();
        double opacity = videoOpacitySlider.getValue();
        double fontSize = videoFontSizeSlider != null ? videoFontSizeSlider.getValue() : 48;

        videoOverlayLabel.setStyle("-fx-text-fill: " + color + "; " +
                "-fx-font-family: '" + font + "'; " +
                "-fx-font-size: " + fontSize + "; " +
                "-fx-padding: 15; " +
                "-fx-font-style: italic;");
        videoOverlayLabel.setOpacity(opacity);
    }

    public void updateScaling() {
        if (lastKnownMediaWidth <= 0 || lastKnownMediaHeight <= 0)
            return;

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

        updateTextSliderBounds(displayWidth, displayHeight);

        // Refresh overlays if graphic is active
        if (videoGraphicsCombo != null && videoGraphicsCombo.getValue() != null
                && !"None".equals(videoGraphicsCombo.getValue())) {
            applyGraphicsOverlay();
        }
    }

    @FXML
    public void handleUploadVideo() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.getExtensionFilters()
                .add(new javafx.stage.FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mkv", "*.avi"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            MainController.getInstance().setCurrentImagePath(file.getAbsolutePath());
            MainController.getInstance().setCurrentFileName(file.getName());
            isVideoMode = true;
            videoPhotos.clear();
            photoDurations.clear();

            if (videoTimeline != null)
                videoTimeline.stop();
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
        fc.getExtensionFilters()
                .add(new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
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

            // Compute and store a fixed display size (max 800×500) so the view
            // never shrinks when the Play button is pressed.
            applyAndStorePhotoScaling();

            synthesisControls.setVisible(true);
            synthesisControls.setManaged(true);
            trimControls.setVisible(false);
            trimControls.setManaged(false);
        }
    }

    @FXML
    public void handleTrimFront() {
        if (!isVideoMode || mediaPlayer == null || videoTrimInput.getText().isEmpty())
            return;
        try {
            double trimSec = Double.parseDouble(videoTrimInput.getText());
            double currentTotal = mediaPlayer.getTotalDuration().toSeconds();
            if (trimSec >= (currentTotal - trimBackSec))
                return;

            trimFrontSec = trimSec;
            mediaPlayer.seek(Duration.seconds(trimFrontSec));
            videoSeekBar.setValue(0); // Immediately reset slider to the new start point

            // Visual feedback for buttons
            btnTrimFront.setStyle(
                    "-fx-background-color: #A04000; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            btnTrimBack.setStyle(
                    "-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setContentText("Video will now start from " + trimSec + "s");
            alert.show();
        } catch (Exception e) {
            videoTrimInput.setText("");
        }
    }

    @FXML
    public void handleTrimBack() {
        if (!isVideoMode || mediaPlayer == null || videoTrimInput.getText().isEmpty())
            return;
        try {
            double trimSec = Double.parseDouble(videoTrimInput.getText());
            double currentTotal = mediaPlayer.getTotalDuration().toSeconds();
            if (trimSec >= (currentTotal - trimFrontSec))
                return;

            trimBackSec = trimSec;

            // Visual feedback for buttons
            btnTrimBack.setStyle(
                    "-fx-background-color: #A04000; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");
            btnTrimFront.setStyle(
                    "-fx-background-color: #F39C12; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8;");

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
        // Show the back button once media is loaded
        if (btnBackToUpload != null) {
            btnBackToUpload.setVisible(true);
            btnBackToUpload.setManaged(true);
        }
    }

    @FXML
    public void handleSyncFavourites() {
        MainController main = MainController.getInstance();
        if (main == null)
            return;

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
            itemBox.setStyle(
                    "-fx-border-color: #E0E0E0; -fx-border-radius: 8; -fx-padding: 8; -fx-background-color: #FFFFFF;");

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
                    itemBox.setStyle(
                            "-fx-border-color: #4F5BD5; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 7; -fx-background-color: #EEF2FF;");
                } else {
                    itemBox.setStyle(
                            "-fx-border-color: #E0E0E0; -fx-border-width: 1; -fx-border-radius: 8; -fx-padding: 8; -fx-background-color: #FFFFFF;");
                }
                // Preview logic
                videoPreviewImage.setImage(iv.getImage());
            });
            // Set initial state
            itemBox.setStyle(
                    "-fx-border-color: #4F5BD5; -fx-border-width: 2; -fx-border-radius: 8; -fx-padding: 7; -fx-background-color: #EEF2FF;");

            gridContainer.getChildren().add(itemBox);
        }

        Button btnSelectAll = new Button("Select All");
        btnSelectAll.setStyle(
                "-fx-background-color: #FFFFFF; -fx-border-color: #4F5BD5; -fx-text-fill: #4F5BD5; -fx-border-radius: 5;");
        btnSelectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(true)));

        Button btnDeselectAll = new Button("Deselect All");
        btnDeselectAll.setStyle(
                "-fx-background-color: #FFFFFF; -fx-border-color: #8892B0; -fx-text-fill: #8892B0; -fx-border-radius: 5;");
        btnDeselectAll.setOnAction(e -> checkBoxes.forEach(cb -> cb.setSelected(false)));

        HBox topActions = new HBox(10, btnSelectAll, btnDeselectAll);

        Button btnSynthesize = new Button("Create Video");
        btnSynthesize.setStyle(
                "-fx-background-color: #4F5BD5; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
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
                applyAndStorePhotoScaling();
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
        if (videoPhotos.isEmpty())
            return;
        currentVideoIndex = index;
        File f = new File(videoPhotos.get(index));
        Image img = new Image(f.toURI().toString());
        videoPreviewImage.setImage(img);

        lastKnownMediaWidth = img.getWidth();
        lastKnownMediaHeight = img.getHeight();

        // Reapply the dimensions fixed at load time so the container
        // never resizes between frames or on the first play-button press.
        if (storedFitWidth > 0 && storedFitHeight > 0) {
            videoPreviewImage.setFitWidth(storedFitWidth);
            videoPreviewImage.setFitHeight(storedFitHeight);
            videoPreviewStack.setPrefSize(storedFitWidth, storedFitHeight);
            videoPreviewStack.setMaxSize(storedFitWidth, storedFitHeight);
            videoPreviewStack.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        } else {
            // First-time call before storage is set: fall back to updateScaling()
            updateScaling();
        }

        double timeBefore = 0;
        for (int i = 0; i < index; i++)
            timeBefore += photoDurations.get(i);
        double total = getTotalDuration();
        if (!videoSeekBar.isValueChanging()) {
            videoSeekBar.setValue(total > 0 ? (timeBefore / total) * 100 : 0);
        }
    }

    /**
     * Computes display dimensions from the first photo (max 800x500), stores them,
     * and applies them so every subsequent showVideoFrame reuses the exact same size.
     */
    private void applyAndStorePhotoScaling() {
        if (videoPhotos.isEmpty()) return;
        File firstFile = new File(videoPhotos.get(0));
        Image firstImg = new Image(firstFile.toURI().toString());
        double w = firstImg.getWidth();
        double h = firstImg.getHeight();
        if (w > 0 && h > 0) {
            double scale = Math.min(800.0 / w, 500.0 / h);
            storedFitWidth  = w * scale;
            storedFitHeight = h * scale;
            videoPreviewImage.setFitWidth(storedFitWidth);
            videoPreviewImage.setFitHeight(storedFitHeight);
            videoPreviewStack.setPrefSize(storedFitWidth, storedFitHeight);
            videoPreviewStack.setMaxSize(storedFitWidth, storedFitHeight);
            videoPreviewStack.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            updateTextSliderBounds(storedFitWidth, storedFitHeight);
        }
    }

    @FXML
    public void handleVideoPlayPause() {
        if (isVideoMode && mediaPlayer != null) {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                btnVideoPlay.setText("▶");
                setGraphicAnimationsPlaying(false);
            } else {
                mediaPlayer.play();
                btnVideoPlay.setText("⏸");
                setGraphicAnimationsPlaying(true);
            }
            return;
        }

        if (videoPhotos.isEmpty())
            return;

        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            videoTimeline.pause();
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null); // Clear SVG shape if any
            setGraphicAnimationsPlaying(false);
            return;
        }

        if (videoTimeline != null && videoTimeline.getStatus() == javafx.animation.Animation.Status.PAUSED) {
            videoTimeline.play();
            btnVideoPlay.setText("⏸");
            btnVideoPlay.setShape(null);
            setGraphicAnimationsPlaying(true);
            return;
        }

        if (videoTimeline != null)
            videoTimeline.stop();

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
                        for (int k = 0; k < frameIndex; k++)
                            timeBefore += photoDurations.get(k);
                        videoSeekBar.setValue((timeBefore / totalDuration) * 100);
                    }));

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
                        }));
            }
            currentTime += duration;
        }

        videoTimeline.setCycleCount(1);
        videoTimeline.setOnFinished(e -> {
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null);
            currentVideoIndex = 0;
            videoSeekBar.setValue(100);
            setGraphicAnimationsPlaying(false);
        });

        videoTimeline.play();
        btnVideoPlay.setText("⏸");
        btnVideoPlay.setShape(null);
        setGraphicAnimationsPlaying(true);
    }

    @FXML
    public void handleSkipPrevious() {
        if (isVideoMode && mediaPlayer != null) {
            mediaPlayer.seek(Duration.seconds(trimFrontSec));
            mediaPlayer.play();
            btnVideoPlay.setText("⏸");
            return;
        }
        if (videoPhotos.isEmpty())
            return;
        boolean wasPlaying = videoTimeline != null
                && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING;

        if (videoTimeline != null)
            videoTimeline.stop();

        currentVideoIndex = Math.max(0, currentVideoIndex - 1);
        showVideoFrame(currentVideoIndex);

        if (wasPlaying) {
            handleVideoPlayPause();
            double timeBefore = 0;
            for (int i = 0; i < currentVideoIndex; i++)
                timeBefore += photoDurations.get(i);
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
        if (videoPhotos.isEmpty())
            return;
        boolean wasPlaying = videoTimeline != null
                && videoTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING;

        if (videoTimeline != null)
            videoTimeline.stop();

        currentVideoIndex = Math.min(videoPhotos.size() - 1, currentVideoIndex + 1);
        showVideoFrame(currentVideoIndex);

        if (wasPlaying) {
            handleVideoPlayPause();
            double timeBefore = 0;
            for (int i = 0; i < currentVideoIndex; i++)
                timeBefore += photoDurations.get(i);
            videoTimeline.jumpTo(Duration.seconds(timeBefore));
        } else {
            btnVideoPlay.setText("▶");
            btnVideoPlay.setShape(null);
        }
    }

    private double getTotalDuration() {
        double total = 0;
        for (Double d : photoDurations)
            total += d;
        return total;
    }

    @FXML
    public void showShareTab() {
        MainController.getInstance().showShareTab();
    }

    @FXML
    public void handleRenderSave() {
        if (videoPhotos.isEmpty() && !isVideoMode) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("No media to save. Please upload a video or synthesize photos first.");
            alert.show();
            return;
        }

        if (mediaPlayer == null && isVideoMode)
            return;

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Render Edited Video");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
        fc.setInitialFileName("edited_video.mp4");
        File file = fc.showSaveDialog(null);

        if (file != null) {
            performFullRender(file);
        }
    }

    @FXML
    public void handleSaveToGallery() {
        if (!isVideoMode && videoPhotos.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Please upload a video or synthesize photos first.");
            alert.show();
            return;
        }

        // Create a temporary file in common gallery location
        File galleryDir = new File("Edited_Gallery");
        if (!galleryDir.exists())
            galleryDir.mkdirs();

        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String ext = isVideoMode ? ".mp4" : ".mp4"; // Always save as video for synthesis
        File target = new File(galleryDir, "video_" + timestamp + ext);

        performFullRender(target);
    }

    private File resolveCurrentVideoSource() {
        if (isVideoMode) {
            String currentPath = MainController.getInstance().getCurrentImagePath();
            if (currentPath == null || currentPath.isBlank())
                return null;
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
        if (file.getName().toLowerCase().endsWith(extension.toLowerCase()))
            return file;
        String parent = file.getParent();
        return parent == null ? new File(file.getName() + extension) : new File(parent, file.getName() + extension);
    }

    private String getFileHash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(Files.readAllBytes(file.toPath()));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    private void performFullRender(File outFile) {
        if (outFile == null)
            return;
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
                if (total < 0)
                    total = 0;
            }

            final double startSec = trimFrontSec;
            final double durationSec = total;

            ProgressIndicator pi = new ProgressIndicator(0);
            pi.setPrefSize(80, 80);
            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(15, new javafx.scene.control.Label("Exporting your video, please wait..."), pi);
            content.setAlignment(javafx.geometry.Pos.CENTER);
            content.setPadding(new javafx.geometry.Insets(20));

            Alert progressAlert = new Alert(Alert.AlertType.NONE);
            progressAlert.setTitle("Rendering Video");
            progressAlert.getDialogPane().setContent(content);
            progressAlert.getDialogPane().setPrefSize(350, 200);
            progressAlert.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.OK);
            progressAlert.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK).setVisible(false);
            progressAlert.show();

            VideoRenderer.renderVideoWithOverlays(source, outFile, videoPreviewStack, mediaPlayer, startSec,
                    durationSec,
                    new VideoRenderer.RenderProgressCallback() {
                        @Override
                        public void onProgress(double progress) {
                            javafx.application.Platform.runLater(() -> pi.setProgress(progress));
                        }

                        @Override
                        public void onComplete(File out) {
                            javafx.application.Platform.runLater(() -> {
                                progressAlert.setResult(javafx.scene.control.ButtonType.OK);
                                progressAlert.close();
                                try {
                                    // copy to Edited_Gallery and refresh main list via MainController helper
                                    File galleryDir = new File("Edited_Gallery");
                                    if (!galleryDir.exists())
                                        galleryDir.mkdirs();
                                    File dest = new File(galleryDir, out.getName());
                                    Files.copy(out.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    try {
                                        MainController.getInstance().updateSavedMediaStateExternal(dest);
                                    } catch (Exception ignored) {
                                    }
                                } catch (Exception ignored) {
                                }
                                Alert done = new Alert(Alert.AlertType.INFORMATION);
                                done.setContentText("Render complete: " + out.getAbsolutePath());
                                done.show();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            javafx.application.Platform.runLater(() -> {
                                progressAlert.setResult(javafx.scene.control.ButtonType.OK);
                                progressAlert.close();
                                Alert err = new Alert(Alert.AlertType.ERROR);
                                err.setContentText("Render failed: " + message);
                                err.show();
                            });
                        }
                    });
            return;
        }

        // Photo-synthesis mode: build a slideshow-style MP4 using JavaCV directly
        ProgressIndicator photoPi = new ProgressIndicator(0);
        photoPi.setPrefSize(80, 80);
        javafx.scene.layout.VBox photoContent = new javafx.scene.layout.VBox(15, new javafx.scene.control.Label("Exporting your photos, please wait..."), photoPi);
        photoContent.setAlignment(javafx.geometry.Pos.CENTER);
        photoContent.setPadding(new javafx.geometry.Insets(20));

        Alert photoProgressAlert = new Alert(Alert.AlertType.NONE);
        photoProgressAlert.setTitle("Rendering Photo Synthesis");
        photoProgressAlert.getDialogPane().setContent(photoContent);
        photoProgressAlert.getDialogPane().setPrefSize(350, 200);
        photoProgressAlert.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.OK);
        photoProgressAlert.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK).setVisible(false);
        photoProgressAlert.show();

        // Capture overlay settings on JavaFX thread before handing off to render thread
        final String overlayText = videoOverlayText != null ? videoOverlayText.getText() : "";
        final double tx = videoOverlayLabel != null ? videoOverlayLabel.getTranslateX() : 0;
        final double ty = videoOverlayLabel != null ? videoOverlayLabel.getTranslateY() : 0;
        final double op = videoOverlayLabel != null ? videoOverlayLabel.getOpacity() : 1.0;
        final javafx.scene.paint.Color fxColor = videoOverlayLabel != null
                && videoOverlayLabel.getTextFill() instanceof javafx.scene.paint.Color
                        ? (javafx.scene.paint.Color) videoOverlayLabel.getTextFill()
                        : javafx.scene.paint.Color.WHITE;
        final java.awt.Color awtColor = new java.awt.Color((float) fxColor.getRed(),
                (float) fxColor.getGreen(), (float) fxColor.getBlue(), (float) fxColor.getOpacity());
        // Display dimensions used for coordinate mapping (same as preview)
        final double dispW = storedFitWidth  > 0 ? storedFitWidth  : 800.0;
        final double dispH = storedFitHeight > 0 ? storedFitHeight : 500.0;
        final String graphicEffect = videoGraphicsCombo != null ? videoGraphicsCombo.getValue() : "None";
        final String fontName = videoFontCombo != null && videoFontCombo.getValue() != null ? videoFontCombo.getValue() : "SansSerif";

        new Thread(() -> {
            try {
                if (videoPhotos.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        photoProgressAlert.setResult(javafx.scene.control.ButtonType.OK);
                        photoProgressAlert.close();
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
                if (width % 2 != 0)
                    width--;
                if (height % 2 != 0)
                    height--;

                double frameRate = 30.0;
                Java2DFrameConverter converter = new Java2DFrameConverter();
                FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(renderTarget, width, height);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setFormat("mp4");
                recorder.setFrameRate(frameRate);
                recorder.setVideoBitrate(2000000);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.start();

                for (int idx = 0; idx < videoPhotos.size(); idx++) {
                    File f = new File(videoPhotos.get(idx));
                    BufferedImage img = ImageIO.read(f);
                    if (img.getWidth() != width || img.getHeight() != height) {
                        BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics2D g = tmp.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(img, 0, 0, width, height, null);
                        g.dispose();
                        img = tmp;
                    }

                    int frames = (int) Math.max(1, Math.round(photoDurations.get(idx) * frameRate));
                    for (int fidx = 0; fidx < frames; fidx++) {
                        // Build a fresh frame so per-frame effects can vary
                        BufferedImage framed = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                        Graphics2D g = framed.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g.drawImage(img, 0, 0, null);

                        double progress = (double) fidx / Math.max(1, frames - 1); // 0.0 → 1.0
                        double overallProgress = (idx + ((double) fidx / frames)) / videoPhotos.size();
                        if (fidx % 5 == 0) {
                            javafx.application.Platform.runLater(() -> photoPi.setProgress(overallProgress));
                        }

                        // ── Graphic overlay ──────────────────────────────────────────
                        if (graphicEffect != null) {
                            switch (graphicEffect) {
                                case "Classic Vignette": {
                                    java.awt.RadialGradientPaint vignette = new java.awt.RadialGradientPaint(
                                        width / 2f, height / 2f,
                                        Math.max(width, height) * 0.7f,
                                        new float[]{0f, 1f},
                                        new java.awt.Color[]{new java.awt.Color(0,0,0,0), new java.awt.Color(0,0,0,180)});
                                    g.setPaint(vignette);
                                    g.fillRect(0, 0, width, height);
                                    break;
                                }
                                case "Golden Borders": {
                                    int bw = Math.max(10, width / 60);
                                    // Shimmer: alpha pulses with sin wave
                                    float shimmer = (float)(0.7 + 0.3 * Math.sin(progress * Math.PI * 6));
                                    g.setColor(new java.awt.Color(1f, 0.84f, 0f, shimmer));
                                    g.setStroke(new java.awt.BasicStroke(bw));
                                    g.drawRect(bw/2, bw/2, width - bw, height - bw);
                                    break;
                                }
                                case "White Borders": {
                                    int bw = Math.max(10, width / 60);
                                    g.setColor(new java.awt.Color(255, 255, 255, 220));
                                    g.setStroke(new java.awt.BasicStroke(bw));
                                    g.drawRect(bw/2, bw/2, width - bw, height - bw);
                                    break;
                                }
                                case "Retro Film": {
                                    // Sepia tint
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 0.15f));
                                    g.setColor(new java.awt.Color(180, 120, 40));
                                    g.fillRect(0, 0, width, height);
                                    // Animated scanline
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 0.25f));
                                    int lineY = (int)((progress * height * 2) % height);
                                    g.setColor(java.awt.Color.WHITE);
                                    g.setStroke(new java.awt.BasicStroke(2));
                                    g.drawLine(0, lineY, width, lineY);
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 1f));
                                    break;
                                }
                                case "Floating Hearts": {
                                    // 8 hearts at deterministic positions that float upward over the frame
                                    for (int h = 0; h < 8; h++) {
                                        double seed = h * 137.508; // golden angle spread
                                        double baseX = (Math.sin(seed) * 0.5 + 0.5) * width;
                                        double baseY = (1.0 - ((progress + h * 0.12) % 1.0)) * (height + 40) - 20;
                                        float alpha = (float) Math.min(1.0, Math.sin(((progress + h * 0.12) % 1.0) * Math.PI));
                                        g.setComposite(java.awt.AlphaComposite.getInstance(
                                                java.awt.AlphaComposite.SRC_OVER, alpha));
                                        g.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN,
                                                (int)(height * 0.06)));
                                        g.setColor(new java.awt.Color(255, 80, 100));
                                        g.drawString("\u2665", (int) baseX, (int) baseY);
                                    }
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 1f));
                                    break;
                                }
                                case "Starfield": {
                                    // Twinkling stars: opacity alternates per star
                                    java.util.Random rnd = new java.util.Random(42);
                                    for (int s = 0; s < 20; s++) {
                                        double sx = rnd.nextDouble() * width;
                                        double sy = rnd.nextDouble() * height;
                                        double r  = 3 + rnd.nextDouble() * 5;
                                        float alpha = (float)(0.4 + 0.6 * Math.abs(
                                                Math.sin(progress * Math.PI * 4 + s)));
                                        g.setComposite(java.awt.AlphaComposite.getInstance(
                                                java.awt.AlphaComposite.SRC_OVER, alpha));
                                        g.setColor(new java.awt.Color(255, 240, 100));
                                        g.fillOval((int)(sx - r), (int)(sy - r), (int)(r*2), (int)(r*2));
                                        // Cross flare
                                        g.setStroke(new java.awt.BasicStroke(1.5f));
                                        g.drawLine((int)(sx - r*2), (int)sy, (int)(sx + r*2), (int)sy);
                                        g.drawLine((int)sx, (int)(sy - r*2), (int)sx, (int)(sy + r*2));
                                    }
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 1f));
                                    break;
                                }
                                case "Confetti Rain": {
                                    // Confetti pieces fall from top to bottom
                                    java.awt.Color[] confettiColors = {
                                        new java.awt.Color(255, 80, 80),  new java.awt.Color(80, 200, 120),
                                        new java.awt.Color(80, 140, 255), new java.awt.Color(255, 200, 0),
                                        new java.awt.Color(200, 80, 255)
                                    };
                                    java.util.Random rnd2 = new java.util.Random(123);
                                    for (int c = 0; c < 18; c++) {
                                        double cx = rnd2.nextDouble() * width;
                                        double fallY = ((progress + c * 0.055) % 1.0) * (height + 30) - 15;
                                        double rot   = progress * Math.PI * 6 + c * 0.9;
                                        java.awt.Color cc = confettiColors[c % confettiColors.length];
                                        g.setComposite(java.awt.AlphaComposite.getInstance(
                                                java.awt.AlphaComposite.SRC_OVER, 0.85f));
                                        g.setColor(cc);
                                        java.awt.geom.AffineTransform at = g.getTransform();
                                        g.translate(cx, fallY);
                                        g.rotate(rot);
                                        int pw = (int)(width * 0.018), ph = (int)(height * 0.012);
                                        g.fillRect(-pw/2, -ph/2, pw, ph);
                                        g.setTransform(at);
                                    }
                                    g.setComposite(java.awt.AlphaComposite.getInstance(
                                            java.awt.AlphaComposite.SRC_OVER, 1f));
                                    break;
                                }
                            }
                        }

                        // ── Text overlay (correctly centred) ─────────────────────────
                        if (overlayText != null && !overlayText.isBlank()) {
                            // Scale font relative to how the preview scales to full image
                            double userFontSize = videoFontSizeSlider != null ? videoFontSizeSlider.getValue() : 48;
                            int fontSize = (int) Math.max(12, userFontSize * (width / dispW));
                            g.setFont(new java.awt.Font(fontName, java.awt.Font.BOLD, fontSize));
                            g.setColor(awtColor);
                            g.setComposite(java.awt.AlphaComposite.getInstance(
                                    java.awt.AlphaComposite.SRC_OVER, (float) op));
                            java.awt.FontMetrics fm = g.getFontMetrics();
                            int textW = fm.stringWidth(overlayText);
                            int textH = fm.getAscent();
                            // Map slider offset (display-space pixels) to image-space pixels, then centre
                            int drawX = (int)((width  / 2.0) + tx * (width  / dispW) - textW / 2.0);
                            int drawY = (int)((height / 2.0) + ty * (height / dispH) + textH / 2.0);
                            // Drop-shadow for readability
                            g.setColor(new java.awt.Color(0, 0, 0, 140));
                            g.drawString(overlayText, drawX + 2, drawY + 2);
                            g.setColor(awtColor);
                            g.drawString(overlayText, drawX, drawY);
                        }

                        g.dispose();
                        recorder.record(converter.convert(framed));
                    }
                }

                recorder.stop();
                recorder.release();

                // copy to gallery and refresh
                File galleryDir = new File("Edited_Gallery");
                if (!galleryDir.exists())
                    galleryDir.mkdirs();
                File dest = new File(galleryDir, renderTarget.getName());
                Files.copy(renderTarget.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                javafx.application.Platform.runLater(() -> {
                    photoProgressAlert.setResult(javafx.scene.control.ButtonType.OK);
                    photoProgressAlert.close();
                    try {
                        MainController.getInstance().updateSavedMediaStateExternal(dest);
                    } catch (Exception ignored) {
                    }
                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setContentText("Synthesis complete: " + dest.getAbsolutePath());
                    done.show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    photoProgressAlert.setResult(javafx.scene.control.ButtonType.OK);
                    photoProgressAlert.close();
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setContentText("Synthesis failed: " + e.getMessage());
                    err.show();
                });
            }
        }).start();
    }
}
