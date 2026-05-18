package com.mp.wig3003groupproject;

import java.io.File;
import java.util.Stack;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

public class ObjectExtractionController {
    @FXML private Slider colorToleranceSlider, alphaSlider;
    @FXML private Label toleranceLabel, alphaLabel;
    @FXML private Button loadImageBtn, extractBtn, downloadBtn, resetBtn;
    @FXML private ComboBox<String> outputModeCombo;
    @FXML private VBox downloadContainer;
    @FXML private Label statusLabel;

    private Image originalImage;
    private Image currentImage;
    private Color selectedColor;
    private boolean colorSelected = false;
    private Stack<Image> undoStack = new Stack<>();
    private Stack<Image> redoStack = new Stack<>();
    private double zoomLevel = 1.0;
    
    private MainController mainController;

    private static ObjectExtractionController instance;

    public ObjectExtractionController() {
        instance = this;
    }

    public static ObjectExtractionController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        mainController = MainController.getInstance();
        
        colorToleranceSlider.valueProperty().addListener((o, old, v) -> {
            if (toleranceLabel != null) {
                toleranceLabel.setText(String.format("%.0f%%", v.doubleValue() * 100));
            }
        });

        alphaSlider.valueProperty().addListener((o, old, v) -> {
            if (alphaLabel != null) {
                alphaLabel.setText(String.format("%.0f%%", v.doubleValue() * 100));
            }
        });

        colorToleranceSlider.setOnMousePressed(e -> saveState());
        alphaSlider.setOnMousePressed(e -> saveState());

        outputModeCombo.getItems().addAll("Extract Only", "Extract with Background", "Color Mask");
        outputModeCombo.setValue("Extract Only");

        downloadContainer.setVisible(true);
        statusLabel.setText("Load an image to start");
        
        // Add mouse click handler to extraction image view
        if (mainController.getExtractionImageView() != null) {
            mainController.getExtractionImageView().setOnMouseClicked(this::handleImageClick);
        }
    }

    @FXML
    public void handleLoadImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image for Object Extraction");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                Image img = new Image(file.toURI().toString());
                onImageLoaded(img);
                statusLabel.setText("Image loaded. Click on an object to select.");
                downloadContainer.setVisible(true);
                colorSelected = false;
                undoStack.clear();
                redoStack.clear();
            } catch (Exception e) {
                statusLabel.setText("Error loading image: " + e.getMessage());
            }
        }
    }

    public void onImageLoaded(Image img) {
        this.originalImage = img;
        this.currentImage = img;
        mainController.getExtractionImageView().setImage(img);
        colorToleranceSlider.setValue(0.1);
        alphaSlider.setValue(1.0);
        colorSelected = false; // Reset color selection
        undoStack.clear();
        redoStack.clear();
        downloadContainer.setVisible(true);
        statusLabel.setText("Image loaded. Click on an object to select.");
    }

    private void handleImageClick(MouseEvent event) {
        if (originalImage == null) return;
        selectColor(event.getX(), event.getY());
    }

    public void selectColor(double x, double y) {
        if (originalImage == null) return;
        
        PixelReader reader = originalImage.getPixelReader();
        int pixelX = (int) x;
        int pixelY = (int) y;
        
        if (pixelX >= 0 && pixelX < originalImage.getWidth() && pixelY >= 0 && pixelY < originalImage.getHeight()) {
            selectedColor = reader.getColor(pixelX, pixelY);
            colorSelected = true;
            statusLabel.setText(String.format("Color selected: RGB(%.0f, %.0f, %.0f)", 
                selectedColor.getRed() * 255, selectedColor.getGreen() * 255, selectedColor.getBlue() * 255));
        }
    }

    @FXML
    public void handleExtract() {
        if (!colorSelected || originalImage == null) {
            statusLabel.setText("Please select a color first by clicking on an object.");
            return;
        }

        saveState();
        double tolerance = colorToleranceSlider.getValue();
        double alpha = alphaSlider.getValue();
        String outputMode = outputModeCombo.getValue();

        int w = (int) originalImage.getWidth();
        int h = (int) originalImage.getHeight();
        PixelReader pr = originalImage.getPixelReader();
        WritableImage resultImage = new WritableImage(w, h);
        PixelWriter pw = resultImage.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color pixelColor = pr.getColor(x, y);
                double distance = colorDistance(pixelColor, selectedColor);

                if (distance <= tolerance) {
                    // Object pixel
                    if (outputMode.equals("Color Mask")) {
                        pw.setColor(x, y, selectedColor.deriveColor(0, 1, 1, alpha));
                    } else {
                        pw.setColor(x, y, pixelColor.deriveColor(0, 1, 1, alpha));
                    }
                } else {
                    // Background pixel
                    if (outputMode.equals("Extract Only")) {
                        pw.setColor(x, y, new Color(0, 0, 0, 0)); // Transparent
                    } else if (outputMode.equals("Extract with Background")) {
                        pw.setColor(x, y, new Color(1.0, 1.0, 1.0, alpha)); // White background
                    } else { // Color Mask
                        pw.setColor(x, y, new Color(0, 0, 0, 0)); // Transparent
                    }
                }
            }
        }

        currentImage = resultImage;
        mainController.getExtractionImageView().setImage(resultImage);
        statusLabel.setText("Object extracted successfully!");
    }

    private double colorDistance(Color c1, Color c2) {
        double dr = c1.getRed() - c2.getRed();
        double dg = c1.getGreen() - c2.getGreen();
        double db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    @FXML
    public void handleDownloadExtraction() {
        if (currentImage == null) {
            statusLabel.setText("No image to save. Please extract first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Extracted Object");
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
                
                ImageIO.write(SwingFXUtils.fromFXImage(currentImage, null), 
                    fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg") ? "jpg" : "png", 
                    new File(fileName));
                
                statusLabel.setText("✅ Extraction saved successfully!");
            } catch (Exception e) {
                statusLabel.setText("Error saving: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleSaveToGallery() {
        if (currentImage == null) {
            statusLabel.setText("No image to save. Please extract first.");
            return;
        }
        mainController.saveImageToGallery(currentImage, "extraction");
        statusLabel.setText("✅ Saved to gallery successfully!");
    }

    @FXML
    public void handleSaveAction() {
        if (currentImage == null) {
            statusLabel.setText("No image to save. Please extract first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Extraction to Local");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG Image", "*.png"),
            new FileChooser.ExtensionFilter("JPEG Image", "*.jpg")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                String fileName = file.getAbsolutePath();
                String format = fileName.toLowerCase().endsWith(".jpg") ? "jpg" : "png";
                ImageIO.write(SwingFXUtils.fromFXImage(currentImage, null), format, new File(fileName));
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
        if (mainController.getExtractionImageView() != null) {
            mainController.getExtractionImageView().setScaleX(zoomLevel);
            mainController.getExtractionImageView().setScaleY(zoomLevel);
        }
    }

    @FXML
    public void handleReset() {
        if (originalImage != null) {
            currentImage = originalImage;
            colorSelected = false;
            mainController.getExtractionImageView().setImage(originalImage);
            statusLabel.setText("Reset. Click on an object to select.");
            undoStack.clear();
            redoStack.clear();
        }
    }

    public void saveState() {
        if (mainController.getExtractionImageView().getImage() != null) {
            undoStack.push(mainController.getExtractionImageView().getImage());
            redoStack.clear();
        }
    }

    @FXML
    public void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(mainController.getExtractionImageView().getImage());
            Image prev = undoStack.pop();
            mainController.getExtractionImageView().setImage(prev);
            this.currentImage = prev;
        }
    }

    @FXML
    public void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(mainController.getExtractionImageView().getImage());
            Image next = redoStack.pop();
            mainController.getExtractionImageView().setImage(next);
            this.currentImage = next;
        }
    }

    public void clearUI() {
        originalImage = null;
        currentImage = null;
        selectedColor = null;
        colorSelected = false;
        undoStack.clear();
        redoStack.clear();
        mainController.getExtractionImageView().setImage(null);
        colorToleranceSlider.setValue(0.1);
        alphaSlider.setValue(1.0);
        outputModeCombo.setValue("Extract Only");
        statusLabel.setText("Load an image to start");
        downloadContainer.setVisible(true);
    }
}
