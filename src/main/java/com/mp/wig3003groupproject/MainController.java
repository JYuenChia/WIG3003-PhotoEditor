package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;

public class MainController {
    @FXML private ImageView mainImageView;
    @FXML private ScrollPane imageScrollPane;
    @FXML private VBox uploadPlaceholder;
    @FXML private Label heartIcon;

    private static MainController instance;
    public MainController() { instance = this; }
    public static MainController getInstance() { return instance; }

    @FXML
    public void initialize() {
        mainImageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (DIPController.getInstance().isSelectionMode()) {
                double ratioX = mainImageView.getImage().getWidth() / mainImageView.getBoundsInLocal().getWidth();
                double ratioY = mainImageView.getImage().getHeight() / mainImageView.getBoundsInLocal().getHeight();
                DIPController.getInstance().selectSimilarColors(event.getX() * ratioX, event.getY() * ratioY);
            }
        });
    }

    public void updateStatus(boolean isModified) {
        if (isModified) {
            heartIcon.setStyle("-fx-text-fill: #ff7675; -fx-font-size: 80;");
        } else {
            heartIcon.setStyle("-fx-text-fill: #dfe6e9; -fx-font-size: 80;");
        }
    }

    @FXML
    public void handleOpenImage() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            Image image = new Image(file.toURI().toString());
            mainImageView.setImage(image);
            mainImageView.setPreserveRatio(true);
            mainImageView.fitWidthProperty().bind(imageScrollPane.widthProperty().subtract(20));
            mainImageView.fitHeightProperty().bind(imageScrollPane.heightProperty().subtract(20));
            uploadPlaceholder.setVisible(false);
            imageScrollPane.setVisible(true);

            heartIcon.setVisible(true);
            heartIcon.setStyle("-fx-text-fill: #dfe6e9; -fx-font-size: 80;");

            DIPController.getInstance().onImageLoaded(image);
        }
    }

    public ImageView getImageView() { return mainImageView; }
}