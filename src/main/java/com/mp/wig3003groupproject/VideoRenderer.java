package com.mp.wig3003groupproject;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Utility to "burn" overlays into a video file using JavaCV (FFmpeg).
 * Now using a Frame-to-Frame approach for higher quality and correct colors.
 */
public class VideoRenderer {

    public interface RenderProgressCallback {
        void onProgress(double progress);
        void onComplete(File outFile);
        void onError(String message);
    }

    public static void renderVideoWithOverlays(
            File sourceFile,
            File outputFile,
            StackPane previewStack,
            MediaPlayer mediaPlayer,
            double startTimeSec,
            double durationSec,
            RenderProgressCallback callback) {

        CompletableFuture.runAsync(() -> {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(sourceFile);
            Java2DFrameConverter converter = new Java2DFrameConverter();
            FFmpegFrameRecorder recorder = null;

            try {
                grabber.start();

                // Get original dimensions and frame rate
                int width = grabber.getImageWidth();
                int height = grabber.getImageHeight();
                double frameRate = grabber.getFrameRate();
                if (frameRate <= 0) frameRate = 30;

                // Ensure even dimensions
                if (width % 2 != 0) width--;
                if (height % 2 != 0) height--;

                recorder = new FFmpegFrameRecorder(outputFile, width, height);
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setFormat("mp4");
                recorder.setFrameRate(frameRate);
                recorder.setVideoBitrate(grabber.getVideoBitrate() > 0 ? grabber.getVideoBitrate() : 2000000);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // Standards-compliant pixel format
                
                recorder.start();

                // Calculate frame ranges
                long startFrame = (long) (startTimeSec * frameRate);
                long totalFramesToProcess = (long) (durationSec * frameRate);
                grabber.setFrameNumber((int) startFrame);

                // Get overlay data from UI (captured once)
                final String overlayText;
                final String textColor;
                final double textX;
                final double textY;
                final double textOpacity;
                
                // We need to capture the UI settings from the JavaFX thread safely
                CompletableFuture<OverlayData> uiDataFuture = new CompletableFuture<>();
                Platform.runLater(() -> {
                    String text = "";
                    String colorStr = "#FFFFFF";
                    double tx = 0, ty = 0, op = 1.0;
                    
                    // Look for the overlay label
                    for (javafx.scene.Node node : previewStack.getChildren()) {
                        if (node instanceof javafx.scene.control.Label && !"▶".equals(((javafx.scene.control.Label)node).getText())) {
                            javafx.scene.control.Label lbl = (javafx.scene.control.Label)node;
                            text = lbl.getText();
                            op = lbl.getOpacity();
                            tx = lbl.getTranslateX();
                            ty = lbl.getTranslateY();
                            
                            // Try to extract color from style or text fill
                            if (lbl.getTextFill() instanceof javafx.scene.paint.Color) {
                                javafx.scene.paint.Color c = (javafx.scene.paint.Color)lbl.getTextFill();
                                colorStr = String.format("#%02X%02X%02X", 
                                    (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
                            }
                        }
                    }
                    uiDataFuture.complete(new OverlayData(text, colorStr, tx, ty, op));
                });

                OverlayData ui = uiDataFuture.get();

                for (long i = 0; i < totalFramesToProcess; i++) {
                    Frame frame = grabber.grabImage();
                    if (frame == null) break;

                    // Convert to BufferedImage to draw overlays
                    BufferedImage bi = converter.convert(frame);
                    if (bi != null) {
                        Graphics2D G = bi.createGraphics();
                        G.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // Apply Text Overlay if it exists
                        if (ui.text != null && !ui.text.isEmpty()) {
                            G.setFont(new Font("Arial", Font.ITALIC, 40)); // Upscaled font for video
                            Color c = Color.decode(ui.textColor);
                            G.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(ui.opacity * 255)));
                            
                            // Map JavaFX translate coordinates to video coordinates
                            // (Simplistic mapping: assuming center alignment in UI)
                            double x = (width / 2.0) + ui.x * (width / 800.0);
                            double y = (height / 2.0) + ui.y * (height / 400.0);
                            
                            G.drawString(ui.text, (int)x, (int)y);
                        }
                        
                        G.dispose();
                        recorder.record(converter.convert(bi));
                    } else {
                        // If conversion failed, at least record the raw frame so the video isn't black
                        recorder.record(frame);
                    }

                    if (callback != null && i % 10 == 0) {
                        final double progress = (double) i / totalFramesToProcess;
                        Platform.runLater(() -> callback.onProgress(progress));
                    }
                }

                recorder.stop();
                grabber.stop();
                
                if (callback != null) {
                    Platform.runLater(() -> callback.onComplete(outputFile));
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    Platform.runLater(() -> callback.onError(e.getMessage()));
                }
            } finally {
                try {
                    if (recorder != null) recorder.release();
                    grabber.release();
                } catch (Exception ignored) {}
            }
        });
    }

    private static class OverlayData {
        String text, textColor;
        double x, y, opacity;
        OverlayData(String text, String textColor, double x, double y, double opacity) {
            this.text = text; this.textColor = textColor; this.x = x; this.y = y; this.opacity = opacity;
        }
    }
}
