package com.mp.wig3003groupproject;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Stack;

public class DIPController {
    @FXML private Slider brightnessSlider, contrastSlider, transparencySlider, borderThicknessSlider, borderRoundSlider;
    @FXML private Label brightnessLabel, contrastLabel, transparencyLabel;
    @FXML private Button grayscaleBtn, borderToggleBtn;
    @FXML private ToggleButton selectionToggle;
    @FXML private VBox borderControlsBox, downloadContainer;
    @FXML private ColorPicker customColorPicker;
    @FXML private ComboBox<String> patternCombo;
    @FXML private TextField widthField, heightField;

    private Image originalImage, currentBaseImage;
    private Color activeBorderColor = Color.WHITE;
    private boolean isGrayscale = false, isBorderActive = false;

    private Stack<Image> undoStack = new Stack<>();
    private Stack<Image> redoStack = new Stack<>();

    private static DIPController instance;
    public DIPController() { instance = this; }
    public static DIPController getInstance() { return instance; }

    @FXML
    public void initialize() {
        brightnessSlider.valueProperty().addListener((o, old, v) -> {
            if (brightnessLabel != null) brightnessLabel.setText(Math.round(v.doubleValue() * 100) + "%");
            applyDIP();
        });
        contrastSlider.valueProperty().addListener((o, old, v) -> {
            if (contrastLabel != null) contrastLabel.setText(Math.round(v.doubleValue() * 100) + "%");
            applyDIP();
        });
        transparencySlider.valueProperty().addListener((o, old, v) -> {
            if (transparencyLabel != null) transparencyLabel.setText(Math.round(v.doubleValue() * 100) + "%");
            applyDIP();
        });

        brightnessSlider.setOnMousePressed(e -> saveState());
        contrastSlider.setOnMousePressed(e -> saveState());
        transparencySlider.setOnMousePressed(e -> saveState());
        borderThicknessSlider.setOnMousePressed(e -> saveState());
        borderRoundSlider.setOnMousePressed(e -> saveState());

        borderThicknessSlider.valueProperty().addListener((o, old, v) -> { if(isBorderActive) applyDIP(); });
        borderRoundSlider.valueProperty().addListener((o, old, v) -> { if(isBorderActive) applyDIP(); });
        patternCombo.valueProperty().addListener((o, old, v) -> { saveState(); if(isBorderActive) applyDIP(); });
        customColorPicker.valueProperty().addListener((o, old, v) -> { saveState(); activeBorderColor = v; if(isBorderActive) applyDIP(); });

        selectionToggle.selectedProperty().addListener((obs, old, val) -> {
            if (val) {
                selectionToggle.setText("Selection Mode: ON");
                selectionToggle.setStyle("-fx-background-color: #4F5BD5; -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: bold;");
            } else {
                selectionToggle.setText("Magic Wand");
                selectionToggle.setStyle("-fx-background-color: #EEF2FF; -fx-text-fill: #4F5BD5; -fx-background-radius: 10; -fx-font-weight: bold;");
                applyDIP();
            }
        });
    }

    @FXML public void showShareTab() {
        MainController.getInstance().showShareTab();
    }

    public void onImageLoaded(Image img) {
        this.originalImage = img;
        this.currentBaseImage = img;
        MainController.getInstance().setCurrentDisplayedImage(img);
        widthField.setText(String.valueOf((int)img.getWidth()));
        heightField.setText(String.valueOf((int)img.getHeight()));
        downloadContainer.setVisible(true);
        undoStack.clear();
        redoStack.clear();
    }

    private Image resolveSourceImage() {
        if (currentBaseImage != null) return currentBaseImage;
        if (originalImage != null) return originalImage;
        return null;
    }

    public void saveState() {
        if (MainController.getInstance().getImageView().getImage() != null) {
            undoStack.push(MainController.getInstance().getImageView().getImage());
            redoStack.clear();
        }
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(MainController.getInstance().getImageView().getImage());
            Image prev = undoStack.pop();
            MainController.getInstance().getImageView().setImage(prev);
            MainController.getInstance().setCurrentDisplayedImage(prev);
            this.currentBaseImage = prev;
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(MainController.getInstance().getImageView().getImage());
            Image next = redoStack.pop();
            MainController.getInstance().getImageView().setImage(next);
            MainController.getInstance().setCurrentDisplayedImage(next);
            this.currentBaseImage = next;
        }
    }

    public void reset() {
        if (originalImage != null) {
            MainController.getInstance().getImageView().setImage(originalImage);
            MainController.getInstance().setCurrentDisplayedImage(originalImage);
            this.currentBaseImage = originalImage;
            undoStack.clear();
            redoStack.clear();
            clearUI();
        }
    }

    @FXML public void handleShareWhatsApp() { MainController.getInstance().handleShareWhatsApp(); }
    @FXML public void handleShareEmail() { MainController.getInstance().handleShareEmail(); }

    public void clearUI() {
        brightnessSlider.setValue(0);
        contrastSlider.setValue(0);
        transparencySlider.setValue(1.0);
        isGrayscale = false;
        isBorderActive = false;

        grayscaleBtn.setText("Apply Grayscale");
        grayscaleBtn.setStyle("-fx-background-color: #EEF2FF; -fx-text-fill: #4F5BD5; -fx-font-weight: bold; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif; -fx-background-radius: 10; -fx-cursor: hand;");

        borderToggleBtn.setText("Enable Border");
        borderControlsBox.setDisable(true);
        borderControlsBox.setOpacity(0.4);
        selectionToggle.setSelected(false);
        downloadContainer.setVisible(false);

        customColorPicker.setValue(Color.WHITE);
        patternCombo.setValue("Solid");
        borderThicknessSlider.setValue(25);
        borderRoundSlider.setValue(0);
        activeBorderColor = Color.WHITE;
        if (transparencyLabel != null) transparencyLabel.setText("100%");
    }

    @FXML
    public void handleBorderToggle() {
        saveState();
        isBorderActive = !isBorderActive;
        borderControlsBox.setDisable(!isBorderActive);
        borderControlsBox.setOpacity(isBorderActive ? 1.0 : 0.4);
        borderToggleBtn.setText(isBorderActive ? "❌ Remove Border" : "Enable Border");
        applyDIP();
    }

    @FXML
    public void handleGrayscale() {
        saveState();
        isGrayscale = !isGrayscale;
        if (isGrayscale) {
            grayscaleBtn.setText("Undo Grayscale");
            grayscaleBtn.setStyle("-fx-background-color: #4F5BD5; -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: bold;");
        } else {
            grayscaleBtn.setText("Apply Grayscale");
            grayscaleBtn.setStyle("-fx-background-color: #EEF2FF; -fx-text-fill: #4F5BD5; -fx-background-radius: 10; -fx-font-weight: bold;");
        }
        applyDIP();
    }

    @FXML
    public void handleResetAll() {
        if (originalImage == null) return;
        saveState();
        clearUI();
        downloadContainer.setVisible(true);
        currentBaseImage = originalImage;
        MainController.getInstance().getImageView().setImage(originalImage);
        MainController.getInstance().setCurrentDisplayedImage(originalImage);
        applyDIP();
    }

    private void applyDIP() {
        ImageView view = MainController.getInstance().getImageView();
        Image sourceImage = resolveSourceImage();
        if (view == null || sourceImage == null) return;

        double b = brightnessSlider.getValue(), c = contrastSlider.getValue() + 1.0;
        double alpha = transparencySlider.getValue();
        double t = borderThicknessSlider.getValue(), r = borderRoundSlider.getValue();
        String pattern = patternCombo.getValue();

        int w = (int)sourceImage.getWidth(), h = (int)sourceImage.getHeight();
        PixelReader pr = sourceImage.getPixelReader();
        WritableImage wImg = new WritableImage(w, h);
        PixelWriter pw = wImg.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean isBorder = false;
                if (isBorderActive) {
                    if (r <= 0) isBorder = (x < t || x > w - t || y < t || y > h - t);
                    else {
                        if (x < r && y < r) isBorder = Math.sqrt(Math.pow(r-x,2)+Math.pow(r-y,2)) > r - t;
                        else if (x > w-r && y < r) isBorder = Math.sqrt(Math.pow(x-(w-r),2)+Math.pow(r-y,2)) > r - t;
                        else if (x < r && y > h-r) isBorder = Math.sqrt(Math.pow(r-x,2)+Math.pow(y-(h-r),2)) > r - t;
                        else if (x > w-r && y > h-r) isBorder = Math.sqrt(Math.pow(x-(w-r),2)+Math.pow(y-(h-r),2)) > r - t;
                        else isBorder = (x < t || x > w - t || y < t || y > h - t);
                    }
                }

                if (isBorder) {
                    if (pattern.equals("Polka Dots")) {
                        int space = 20, size = 6;
                        int shift = (y / space % 2 == 0) ? 0 : space / 2;
                        if ((x + shift) % space < size && y % space < size) pw.setColor(x, y, Color.WHITE.deriveColor(0, 1, 1, alpha));
                        else pw.setColor(x, y, activeBorderColor.deriveColor(0, 1, 1, alpha));
                    } else if (pattern.equals("Stripes")) {
                        if ((x + y) % 20 < 10) pw.setColor(x, y, activeBorderColor.deriveColor(0, 1, 1, alpha));
                        else pw.setColor(x, y, activeBorderColor.deriveColor(0, 0.7, 1.2, alpha));
                    } else if (pattern.equals("Gradient")) {
                        pw.setColor(x, y, activeBorderColor.interpolate(Color.BLACK, (double)y/h * 0.5).deriveColor(0, 1, 1, alpha));
                    } else pw.setColor(x, y, activeBorderColor.deriveColor(0, 1, 1, alpha));
                } else {
                    Color col = pr.getColor(x, y);
                    double rv = col.getRed(), gv = col.getGreen(), bv = col.getBlue();
                    if (isGrayscale) { double gray = (rv + gv + bv) / 3.0; rv = gv = bv = gray; }
                    pw.setColor(x, y, new Color(Math.min(1.0, Math.max(0.0, rv * c + b)), Math.min(1.0, Math.max(0.0, gv * c + b)), Math.min(1.0, Math.max(0.0, bv * c + b)), Math.min(1.0, Math.max(0.0, col.getOpacity() * alpha))));
                }
            }
        }
        view.setImage(wImg);
        MainController.getInstance().setCurrentDisplayedImage(wImg);
    }

    public void selectSimilarColors(double x, double y) {
        saveState();
        Image source = resolveSourceImage();
        if (source == null) return;

        PixelReader reader = source.getPixelReader();
        Color target = reader.getColor((int)x, (int)y);
        WritableImage wImg = new WritableImage((int)source.getWidth(), (int)source.getHeight());
        PixelWriter writer = wImg.getPixelWriter();

        for (int row = 0; row < source.getHeight(); row++) {
            for (int col = 0; col < source.getWidth(); col++) {
                Color pix = reader.getColor(col, row);
                double dist = Math.sqrt(Math.pow(pix.getRed()-target.getRed(),2)+Math.pow(pix.getGreen()-target.getGreen(),2)+Math.pow(pix.getBlue()-target.getBlue(),2));
                if (dist < 0.22) writer.setColor(col, row, pix);
                else writer.setColor(col, row, pix.grayscale().deriveColor(0, 1, 0.4, 1));
            }
        }
        MainController.getInstance().getImageView().setImage(wImg);
        MainController.getInstance().setCurrentDisplayedImage(wImg);
    }

    @FXML public void handleResize() {
        saveState();
        try {
            Image source = resolveSourceImage();
            if (source == null) return;
            currentBaseImage = new WritableImage(source.getPixelReader(), Integer.parseInt(widthField.getText()), Integer.parseInt(heightField.getText()));
            applyDIP();
        } catch (Exception e) {}
    }

    @FXML public void handleRotate() {
        saveState();
        Image source = resolveSourceImage();
        if (source == null) return;

        int w = (int) source.getWidth();
        int h = (int) source.getHeight();

        WritableImage rotatedImage = new WritableImage(h, w);
        PixelReader pr = source.getPixelReader();
        PixelWriter pw = rotatedImage.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setColor(h - 1 - y, x, pr.getColor(x, y));
            }
        }

        currentBaseImage = rotatedImage;
        if (originalImage == source) originalImage = rotatedImage;

        widthField.setText(String.valueOf(h));
        heightField.setText(String.valueOf(w));

        applyDIP();
    }

    public boolean isSelectionMode() { return selectionToggle.isSelected(); }

    @FXML public void handleSaveAction() {
        ImageView v = MainController.getInstance().getImageView();
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File f = fc.showSaveDialog(null);
        if (f != null) { try { ImageIO.write(SwingFXUtils.fromFXImage(v.getImage(), null), "png", f); } catch (Exception e) {} }
    }

    @FXML public void handleSaveToGallery() {
        MainController.getInstance().handleSaveToGallery();
    }
}