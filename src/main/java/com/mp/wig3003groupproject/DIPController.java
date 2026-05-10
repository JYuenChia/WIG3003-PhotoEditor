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

public class DIPController {
    @FXML private Slider brightnessSlider, contrastSlider, borderThicknessSlider, borderRoundSlider;
    @FXML private Label brightnessLabel, contrastLabel;
    @FXML private Button grayscaleBtn, borderToggleBtn;
    @FXML private ToggleButton selectionToggle;
    @FXML private VBox borderControlsBox, downloadContainer;
    @FXML private ColorPicker customColorPicker;
    @FXML private ComboBox<String> patternCombo;
    @FXML private TextField widthField, heightField;

    private Image originalImage, currentBaseImage;
    private Color activeBorderColor = Color.WHITE;
    private boolean isGrayscale = false, isBorderActive = false;

    private static DIPController instance;
    public DIPController() { instance = this; }
    public static DIPController getInstance() { return instance; }

    @FXML
    public void initialize() {
        brightnessSlider.valueProperty().addListener((o, old, v) -> {
            brightnessLabel.setText(Math.round(v.doubleValue() * 100) + "%");
            applyDIP();
        });
        contrastSlider.valueProperty().addListener((o, old, v) -> {
            contrastLabel.setText(Math.round(v.doubleValue() * 100) + "%");
            applyDIP();
        });
        borderThicknessSlider.valueProperty().addListener((o, old, v) -> { if(isBorderActive) applyDIP(); });
        borderRoundSlider.valueProperty().addListener((o, old, v) -> { if(isBorderActive) applyDIP(); });
        patternCombo.valueProperty().addListener((o, old, v) -> { if(isBorderActive) applyDIP(); });
        customColorPicker.valueProperty().addListener((o, old, v) -> { activeBorderColor = v; if(isBorderActive) applyDIP(); });

        selectionToggle.selectedProperty().addListener((obs, old, val) -> {
            if (val) {
                selectionToggle.setText("Selection Mode: ON");
                selectionToggle.setStyle("-fx-background-color: #B8E1FF; -fx-background-radius: 10; -fx-border-color: #0984e3; -fx-border-radius: 10;");
            } else {
                selectionToggle.setText("Magic Wand");
                selectionToggle.setStyle("-fx-background-color: #F0E68C; -fx-background-radius: 10;");
                applyDIP();
            }
        });
    }

    public void onImageLoaded(Image img) {
        this.originalImage = img;
        this.currentBaseImage = img;
        widthField.setText(String.valueOf((int)img.getWidth()));
        heightField.setText(String.valueOf((int)img.getHeight()));
        downloadContainer.setVisible(true);
    }

    @FXML
    public void handleBorderToggle() {
        isBorderActive = !isBorderActive;
        borderControlsBox.setDisable(!isBorderActive);
        borderControlsBox.setOpacity(isBorderActive ? 1.0 : 0.4);
        borderToggleBtn.setText(isBorderActive ? "❌ Remove Border" : "Enable Border");
        applyDIP();
    }

    @FXML
    public void handleGrayscale() {
        isGrayscale = !isGrayscale;
        if (isGrayscale) {
            grayscaleBtn.setText("Undo Grayscale");
            grayscaleBtn.setStyle("-fx-background-color: #B8E1FF; -fx-background-radius: 10; -fx-border-color: #0984e3; -fx-border-radius: 10;");
        } else {
            grayscaleBtn.setText("Apply Grayscale");
            grayscaleBtn.setStyle("-fx-background-color: #FFD1DC; -fx-background-radius: 10;");
        }
        applyDIP();
    }

    @FXML
    public void handleResetAll() {
        brightnessSlider.setValue(0); contrastSlider.setValue(0);
        isGrayscale = false; isBorderActive = false;
        grayscaleBtn.setText("🌑 Apply Gray");
        grayscaleBtn.setStyle("-fx-background-color: #FFD1DC; -fx-background-radius: 10;");
        selectionToggle.setSelected(false);
        currentBaseImage = originalImage;
        MainController.getInstance().updateStatus(false);
        applyDIP();
    }

    private void applyDIP() {
        ImageView view = MainController.getInstance().getImageView();
        if (view == null || currentBaseImage == null) return;
        MainController.getInstance().updateStatus(true);

        double b = brightnessSlider.getValue(), c = contrastSlider.getValue() + 1.0;
        double t = borderThicknessSlider.getValue(), r = borderRoundSlider.getValue();
        String pattern = patternCombo.getValue();

        int w = (int)currentBaseImage.getWidth(), h = (int)currentBaseImage.getHeight();
        PixelReader pr = currentBaseImage.getPixelReader();
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
                        if ((x + shift) % space < size && y % space < size) pw.setColor(x, y, Color.WHITE);
                        else pw.setColor(x, y, activeBorderColor);
                    } else if (pattern.equals("Stripes")) {
                        if ((x + y) % 20 < 10) pw.setColor(x, y, activeBorderColor);
                        else pw.setColor(x, y, activeBorderColor.deriveColor(0, 0.7, 1.2, 1));
                    } else if (pattern.equals("Gradient")) {
                        pw.setColor(x, y, activeBorderColor.interpolate(Color.BLACK, (double)y/h * 0.5));
                    } else pw.setColor(x, y, activeBorderColor);
                } else {
                    Color col = pr.getColor(x, y);
                    double rv = col.getRed(), gv = col.getGreen(), bv = col.getBlue();
                    if (isGrayscale) { double gray = (rv + gv + bv) / 3.0; rv = gv = bv = gray; }
                    pw.setColor(x, y, new Color(Math.min(1.0, Math.max(0.0, rv * c + b)),
                            Math.min(1.0, Math.max(0.0, gv * c + b)),
                            Math.min(1.0, Math.max(0.0, bv * c + b)), col.getOpacity()));
                }
            }
        }
        view.setImage(wImg);
    }

    public void selectSimilarColors(double x, double y) {
        Image source = MainController.getInstance().getImageView().getImage();
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
    }

    @FXML public void handleResize() { try { currentBaseImage = new WritableImage(originalImage.getPixelReader(), Integer.parseInt(widthField.getText()), Integer.parseInt(heightField.getText())); applyDIP(); } catch (Exception e) {} }
    @FXML public void handleRotate() { MainController.getInstance().getImageView().setRotate(MainController.getInstance().getImageView().getRotate() + 90); }
    public boolean isSelectionMode() { return selectionToggle.isSelected(); }
    @FXML public void handleSaveAction() {
        ImageView v = MainController.getInstance().getImageView();
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File f = fc.showSaveDialog(null);
        if (f != null) { try { ImageIO.write(SwingFXUtils.fromFXImage(v.getImage(), null), "png", f); } catch (Exception e) {} }
    }
}