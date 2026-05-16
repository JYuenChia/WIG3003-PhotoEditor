package com.mp.wig3003groupproject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class MosaicController {
    @FXML private Slider tileWidthSlider, tileHeightSlider, blendingSlider;
    @FXML private Label tileWidthLabel, tileHeightLabel, blendingLabel;
    @FXML private Button loadImageBtn, loadTilesBtn, generateBtn, downloadBtn, resetBtn;
    @FXML private ComboBox<String> mosaicModeCombo;
    @FXML private VBox downloadContainer;
    @FXML private Label statusLabel;

    private Image originalImage;
    private Image currentMosaicImage;
    private List<Image> tileImages = new ArrayList<>();
    private boolean tileLibraryLoaded = false;
    private double zoomLevel = 1.0;
    private Thread generationThread;
    
    private MainController mainController;

    private static MosaicController instance;

    public MosaicController() {
        instance = this;
    }

    public static MosaicController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        mainController = MainController.getInstance();
        
        tileWidthSlider.valueProperty().addListener((o, old, v) -> {
            if (tileWidthLabel != null) {
                tileWidthLabel.setText(String.format("%.0f px", v.doubleValue()));
            }
        });

        tileHeightSlider.valueProperty().addListener((o, old, v) -> {
            if (tileHeightLabel != null) {
                tileHeightLabel.setText(String.format("%.0f px", v.doubleValue()));
            }
        });

        blendingSlider.valueProperty().addListener((o, old, v) -> {
            if (blendingLabel != null) {
                blendingLabel.setText(String.format("%.0f%%", v.doubleValue() * 100));
            }
        });

        downloadContainer.setVisible(false);
        statusLabel.setText("Load an image and select mosaic mode");
        
        tileWidthSlider.setValue(32);
        tileHeightSlider.setValue(32);
        blendingSlider.setValue(0.5);
        
        if (mosaicModeCombo != null) {
            mosaicModeCombo.setValue("Plain Color (Fast)");
        }
    }

    @FXML
    public void handleLoadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image for Mosaic");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                Image img = new Image(file.toURI().toString());
                onImageLoaded(img);
                statusLabel.setText("Image loaded. Now load tile images.");
            } catch (Exception e) {
                statusLabel.setText("Error loading image: " + e.getMessage());
            }
        }
    }

    public void onImageLoaded(Image img) {
        this.originalImage = img;
        this.currentMosaicImage = img; // Reset to original when new image loads
        mainController.getMosaicImageView().setImage(img);
        tileLibraryLoaded = false; // Reset tile library flag
        tileImages.clear(); // Clear previous tiles
        statusLabel.setText("Image loaded. Load tile images and generate mosaic.");
        downloadContainer.setVisible(false); // Hide download button until mosaic is generated
    }

    @FXML
    public void handleLoadTiles() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder with Tile Images");
        File dir = dirChooser.showDialog(null);

        if (dir != null) {
            tileImages.clear();
            File[] files = dir.listFiles((d, name) -> 
                name.toLowerCase().endsWith(".png") || 
                name.toLowerCase().endsWith(".jpg") ||
                name.toLowerCase().endsWith(".jpeg") ||
                name.toLowerCase().endsWith(".bmp") ||
                name.toLowerCase().endsWith(".gif"));

            if (files != null && files.length > 0) {
                for (File f : files) {
                    try {
                        tileImages.add(new Image(f.toURI().toString()));
                    } catch (Exception e) {
                        // Skip invalid images
                    }
                }
                tileLibraryLoaded = true;
                statusLabel.setText(String.format("Loaded %d tiles. Ready to generate mosaic!", tileImages.size()));
                downloadContainer.setVisible(true);
            } else {
                statusLabel.setText("No image files found in selected folder.");
            }
        }
    }

    @FXML
    public void handleGenerateMosaic() {
        if (originalImage == null) {
            statusLabel.setText("Please load an image first.");
            return;
        }

        String mode = mosaicModeCombo != null ? mosaicModeCombo.getValue() : "Tile Library (Quality)";
        
        if (mode.contains("Tile Library") && (!tileLibraryLoaded || tileImages.isEmpty())) {
            statusLabel.setText("Please load tile images first for Tile Library mode.");
            return;
        }

        // Disable generate button while processing
        generateBtn.setDisable(true);
        
        // Create progress dialog
        javafx.scene.control.Dialog<Boolean> progressDialog = new javafx.scene.control.Dialog<>();
        progressDialog.setTitle("Processing");
        progressDialog.setHeaderText("⏳ Generating mosaic, please wait...");
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));
        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
        progressBar.setProgress(-1); // Indeterminate
        javafx.scene.control.Label msgLabel = new javafx.scene.control.Label("Processing in progress...");
        content.getChildren().addAll(msgLabel, progressBar);
        progressDialog.getDialogPane().setContent(content);
        javafx.scene.control.ButtonType cancelBtn = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().setAll(cancelBtn);
        
        // Run mosaic generation on background thread
        generationThread = new Thread(() -> {
            try {
                int tileWidth = (int) tileWidthSlider.getValue();
                int tileHeight = (int) tileHeightSlider.getValue();
                double blending = blendingSlider.getValue();

                Image mosaicResult = null;
                if (mode.contains("Plain Color")) {
                    mosaicResult = createPlainColorMosaic(originalImage, tileWidth, tileHeight);
                } else {
                    mosaicResult = createMosaic(originalImage, tileImages, tileWidth, tileHeight, blending);
                }
                
                currentMosaicImage = mosaicResult;
                javafx.application.Platform.runLater(() -> {
                    mainController.getMosaicImageView().setImage(currentMosaicImage);
                    statusLabel.setText("✅ Mosaic generated successfully!");
                    downloadContainer.setVisible(true);
                    progressDialog.close();
                    generateBtn.setDisable(false);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error generating mosaic: " + e.getMessage());
                    progressDialog.close();
                    generateBtn.setDisable(false);
                });
            }
        });
        generationThread.setDaemon(true);
        
        // Handle cancel button click
        progressDialog.setOnCloseRequest(event -> {
            if (generationThread != null && generationThread.isAlive()) {
                generationThread.interrupt();
                statusLabel.setText("Mosaic generation cancelled.");
                generateBtn.setDisable(false);
            }
        });
        
        generationThread.start();
        progressDialog.showAndWait();
    }

    private Image createMosaic(Image sourceImage, List<Image> tiles, int tileWidth, int tileHeight, double blending) {
        int sourceWidth = (int) sourceImage.getWidth();
        int sourceHeight = (int) sourceImage.getHeight();

        // Create result image
        WritableImage mosaicImage = new WritableImage(sourceWidth, sourceHeight);
        PixelWriter pw = mosaicImage.getPixelWriter();
        PixelReader sourcePR = sourceImage.getPixelReader();

        // Process each tile position
        for (int tileY = 0; tileY < sourceHeight; tileY += tileHeight) {
            for (int tileX = 0; tileX < sourceWidth; tileX += tileWidth) {
                // Calculate average color of this region
                javafx.scene.paint.Color avgColor = getAverageColor(sourceImage, tileX, tileY, tileWidth, tileHeight);

                // Find best matching tile
                Image bestTile = findBestMatchingTile(tiles, avgColor);

                // Scale and blend the tile
                Image scaledTile = scaleImage(bestTile, tileWidth, tileHeight);
                blendTileIntoMosaic(mosaicImage, scaledTile, tileX, tileY, blending);
            }
        }

        return mosaicImage;
    }

    private Image createPlainColorMosaic(Image sourceImage, int tileWidth, int tileHeight) {
        int sourceWidth = (int) sourceImage.getWidth();
        int sourceHeight = (int) sourceImage.getHeight();

        WritableImage mosaicImage = new WritableImage(sourceWidth, sourceHeight);
        PixelWriter pw = mosaicImage.getPixelWriter();

        // Create mosaic by filling each tile region with the average color of that region
        for (int tileY = 0; tileY < sourceHeight; tileY += tileHeight) {
            for (int tileX = 0; tileX < sourceWidth; tileX += tileWidth) {
                // Get average color of this region
                javafx.scene.paint.Color avgColor = getAverageColor(sourceImage, tileX, tileY, tileWidth, tileHeight);

                // Fill the tile region with solid color
                int endX = Math.min(tileX + tileWidth, sourceWidth);
                int endY = Math.min(tileY + tileHeight, sourceHeight);
                for (int y = tileY; y < endY; y++) {
                    for (int x = tileX; x < endX; x++) {
                        pw.setColor(x, y, avgColor);
                    }
                }
            }
        }

        return mosaicImage;
    }

    private javafx.scene.paint.Color getAverageColor(Image image, int startX, int startY, int width, int height) {
        PixelReader pr = image.getPixelReader();
        double r = 0, g = 0, b = 0;
        int count = 0;

        int endX = Math.min(startX + width, (int) image.getWidth());
        int endY = Math.min(startY + height, (int) image.getHeight());

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                javafx.scene.paint.Color c = pr.getColor(x, y);
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                count++;
            }
        }

        if (count > 0) {
            r /= count;
            g /= count;
            b /= count;
        }

        return new javafx.scene.paint.Color(r, g, b, 1.0);
    }

    private Image findBestMatchingTile(List<Image> tiles, javafx.scene.paint.Color targetColor) {
        if (tiles.isEmpty()) return null;

        Image bestTile = tiles.get(0);
        double bestDistance = Double.MAX_VALUE;

        for (Image tile : tiles) {
            javafx.scene.paint.Color tileColor = getAverageColor(tile, 0, 0, (int) tile.getWidth(), (int) tile.getHeight());
            double distance = colorDistance(tileColor, targetColor);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestTile = tile;
            }
        }

        return bestTile;
    }

    private double colorDistance(javafx.scene.paint.Color c1, javafx.scene.paint.Color c2) {
        double dr = c1.getRed() - c2.getRed();
        double dg = c1.getGreen() - c2.getGreen();
        double db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private Image scaleImage(Image source, int newWidth, int newHeight) {
        if (source == null) return null;
        
        ImageView view = new ImageView(source);
        view.setFitWidth(newWidth);
        view.setFitHeight(newHeight);
        view.setPreserveRatio(false);
        
        WritableImage scaled = new WritableImage(newWidth, newHeight);
        PixelWriter pw = scaled.getPixelWriter();
        PixelReader pr = source.getPixelReader();

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                double sourceX = (x / (double) newWidth) * source.getWidth();
                double sourceY = (y / (double) newHeight) * source.getHeight();
                
                int sx = (int) sourceX;
                int sy = (int) sourceY;
                
                if (sx >= 0 && sx < source.getWidth() && sy >= 0 && sy < source.getHeight()) {
                    pw.setColor(x, y, pr.getColor(sx, sy));
                }
            }
        }

        return scaled;
    }

    private void blendTileIntoMosaic(WritableImage mosaic, Image tile, int startX, int startY, double blending) {
        if (tile == null) return;

        PixelReader tPR = tile.getPixelReader();
        PixelWriter mpw = mosaic.getPixelWriter();
        PixelReader mPR = mosaic.getPixelReader();

        int tileWidth = (int) tile.getWidth();
        int tileHeight = (int) tile.getHeight();

        for (int y = 0; y < tileHeight && startY + y < mosaic.getHeight(); y++) {
            for (int x = 0; x < tileWidth && startX + x < mosaic.getWidth(); x++) {
                javafx.scene.paint.Color tileColor = tPR.getColor(x, y);
                javafx.scene.paint.Color mosaicColor = mPR.getColor(startX + x, startY + y);

                // Blend colors
                javafx.scene.paint.Color blended = tileColor.interpolate(mosaicColor, blending);
                mpw.setColor(startX + x, startY + y, blended);
            }
        }
    }

    @FXML
    public void handleDownloadMosaic() {
        if (currentMosaicImage == null) {
            statusLabel.setText("No mosaic to save. Please generate first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Mosaic Image");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG Image", "*.png"),
            new FileChooser.ExtensionFilter("JPEG Image", "*.jpg"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                String fileName = file.getAbsolutePath();
                if (!fileName.toLowerCase().endsWith(".png") && 
                    !fileName.toLowerCase().endsWith(".jpg") && 
                    !fileName.toLowerCase().endsWith(".jpeg")) {
                    fileName += ".png";
                }
                
                ImageIO.write(SwingFXUtils.fromFXImage(currentMosaicImage, null), 
                    fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg") ? "jpg" : "png", 
                    new File(fileName));
                
                statusLabel.setText("Mosaic saved successfully!");
            } catch (Exception e) {
                statusLabel.setText("Error saving: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleSaveToGallery() {
        if (currentMosaicImage == null) {
            statusLabel.setText("No mosaic to save. Please generate first.");
            return;
        }
        mainController.saveImageToGallery(currentMosaicImage, "mosaic");
        statusLabel.setText("✅ Saved to gallery successfully!");
    }

    @FXML
    public void handleSaveAction() {
        if (currentMosaicImage == null) {
            statusLabel.setText("No mosaic to save. Please generate first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Mosaic to Local");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG Image", "*.png"),
            new FileChooser.ExtensionFilter("JPEG Image", "*.jpg")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                String fileName = file.getAbsolutePath();
                String format = fileName.toLowerCase().endsWith(".jpg") ? "jpg" : "png";
                ImageIO.write(SwingFXUtils.fromFXImage(currentMosaicImage, null), format, new File(fileName));
                statusLabel.setText("✅ Saved successfully to: " + file.getName());
            } catch (Exception e) {
                statusLabel.setText("Error saving: " + e.getMessage());
            }
        }
    }

    public double getZoomLevel() {
        return zoomLevel;
    }

    public void setZoomLevel(double level) {
        this.zoomLevel = level;
        if (mainController.getMosaicImageView() != null) {
            mainController.getMosaicImageView().setScaleX(zoomLevel);
            mainController.getMosaicImageView().setScaleY(zoomLevel);
        }
    }

    @FXML
    public void handleReset() {
        originalImage = null;
        currentMosaicImage = null;
        tileImages.clear();
        tileLibraryLoaded = false;
        mainController.getMosaicImageView().setImage(null);
        statusLabel.setText("Reset. Load an image and tiles to start.");
        downloadContainer.setVisible(false);
    }

    public void clearUI() {
        originalImage = null;
        currentMosaicImage = null;
        tileImages.clear();
        tileLibraryLoaded = false;
        mainController.getMosaicImageView().setImage(null);
        tileWidthSlider.setValue(32);
        tileHeightSlider.setValue(32);
        blendingSlider.setValue(0.5);
        statusLabel.setText("Load an image and tiles to start");
        downloadContainer.setVisible(false);
    }
}
