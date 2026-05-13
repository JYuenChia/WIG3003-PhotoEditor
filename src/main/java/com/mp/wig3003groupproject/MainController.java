package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MainController {

    @FXML private ToggleButton tabDashboard, tabGallery, tabDipEditor;
    @FXML private ToggleButton tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare;
    @FXML private VBox sidebarVBox;

    private boolean sidebarExpanded = true;

    private static final String[][] SIDEBAR_DATA = {
        {"⌂", "⌂  Dashboard"},
        {"⊞", "⊞  Gallery"},
        {"✦", "✦  DIP Editor"},
        {"◧", "◧  Extraction"},
        {"▦", "▦  Mosaic"},
        {"►", "►  Video"},
        {"↗", "↗  Share"}
    };

    // Structural elements required for Deep Dark Mode swaps
    @FXML private StackPane mainContentStackPane;
    @FXML private VBox dipWorkspaceBg;
    @FXML private Label brandLabel;
    @FXML private Button btnOpen, btnClear, btnUndo, btnRedo, btnZoomIn, btnZoomOut, btnSettings;

    @FXML private VBox dashboardPane, galleryPane;
    @FXML private VBox objectExtractionPane, mosaicPane, videoCreatorPane, shareContentPane, settingsPane;
    @FXML private VBox userProfilePane;
    @FXML private HBox dipEditorPane;

    @FXML private TextField profileNameField, profileEmailField;
    @FXML private TextArea profileBioField;
    @FXML private Label profileInitialsLabel, profileDisplayName, profileSaveStatus;

    @FXML private ImageView mainImageView;
    @FXML private ScrollPane imageScrollPane;
    @FXML private VBox uploadPlaceholder;

    @FXML private TextField searchBar;
    @FXML private BorderPane rootPane;
    @FXML private HBox toolbarHBox;
    @FXML private HBox statusBarHBox;
    @FXML private Label statusFileName, statusResolution, statusZoom;

    @FXML private VBox annotationBox;
    @FXML private TextField annotationField;
    @FXML private Label heartIcon;
    @FXML private FlowPane galleryGrid;

    private String currentImagePath = null;
    private String currentFileHash = null; 
    private Properties annotationsDB = new Properties();
    private final String DB_FILE = "annotations_database.properties";
    private List<String> editedFiles = new ArrayList<>();

    private boolean darkMode = false;
    private double zoomLevel = 1.0;
    private String currentFileName = "No file open";

    private static MainController instance;
    public MainController() { instance = this; }
    public static MainController getInstance() { return instance; }

    @FXML
    public void initialize() {
        loadDatabase();
        mainImageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            DIPController dip = DIPController.getInstance();
            if (dip != null && dip.isSelectionMode() && mainImageView.getImage() != null) {
                dip.saveState();
                double ratioX = mainImageView.getImage().getWidth() / mainImageView.getBoundsInLocal().getWidth();
                double ratioY = mainImageView.getImage().getHeight() / mainImageView.getBoundsInLocal().getHeight();
                dip.selectSimilarColors(event.getX() * ratioX, event.getY() * ratioY);
            }
        });
        updateStatusBar();
    }

    @FXML public void showDashboard()        { switchPane("dashboard"); }
    @FXML public void showGallery()          { switchPane("gallery"); refreshGallery(""); }
    @FXML public void showDipEditor()        { switchPane("dipEditor"); }
    @FXML public void showObjectExtraction() { switchPane("objectExtraction"); }
    @FXML public void showMosaic()           { switchPane("mosaic"); }
    @FXML public void showVideoCreator()     { switchPane("videoCreator"); }
    @FXML public void showShare()            { switchPane("share"); }
    @FXML public void showSettings()         { switchPane("settings"); }

    @FXML public void handleSidebarToggle() {
        sidebarExpanded = !sidebarExpanded;
        ToggleButton[] allTabs = {tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare};

        if (sidebarExpanded) {
            sidebarVBox.setPrefWidth(220); sidebarVBox.setMinWidth(220);
            for (int i = 0; i < allTabs.length; i++) allTabs[i].setText(SIDEBAR_DATA[i][1]);
        } else {
            sidebarVBox.setPrefWidth(60); sidebarVBox.setMinWidth(60);
            for (int i = 0; i < allTabs.length; i++) allTabs[i].setText(SIDEBAR_DATA[i][0]);
        }
        for (ToggleButton t : allTabs) t.setStyle(t.isSelected() ? buildActiveStyle() : buildInactiveStyle());
    }

    private String buildActiveStyle() {
        if (darkMode) return "-fx-background-color: #1E2333; -fx-text-fill: #E2E8F0; -fx-background-radius: 12; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14 16; -fx-alignment: CENTER_LEFT; -fx-cursor: hand;";
        return "-fx-background-color: #2E3250; -fx-text-fill: #CDD6F4; -fx-background-radius: 12; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14 16; -fx-alignment: CENTER_LEFT; -fx-cursor: hand;";
    }

    private String buildInactiveStyle() {
        if (darkMode) return "-fx-background-color: transparent; -fx-text-fill: #64748B; -fx-background-radius: 12; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14 16; -fx-alignment: CENTER_LEFT; -fx-cursor: hand;";
        return "-fx-background-color: transparent; -fx-text-fill: #8892B0; -fx-background-radius: 12; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 14 16; -fx-alignment: CENTER_LEFT; -fx-cursor: hand;";
    }

    private void switchPane(String name) {
        VBox[] allVBoxPanes = {dashboardPane, galleryPane, objectExtractionPane, mosaicPane, videoCreatorPane, shareContentPane, settingsPane, userProfilePane};
        for (VBox p : allVBoxPanes) { p.setVisible(false); p.setManaged(false); }
        dipEditorPane.setVisible(false); dipEditorPane.setManaged(false);

        ToggleButton[] allTabs = {tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare};
        for (ToggleButton t : allTabs) t.setStyle(buildInactiveStyle());

        switch (name) {
            case "dashboard"        -> { dashboardPane.setVisible(true);        dashboardPane.setManaged(true);        tabDashboard.setStyle(buildActiveStyle()); }
            case "gallery"          -> { galleryPane.setVisible(true);          galleryPane.setManaged(true);          tabGallery.setStyle(buildActiveStyle()); }
            case "dipEditor"        -> { dipEditorPane.setVisible(true);        dipEditorPane.setManaged(true);        tabDipEditor.setStyle(buildActiveStyle()); }
            case "objectExtraction" -> { objectExtractionPane.setVisible(true); objectExtractionPane.setManaged(true); tabObjectExtraction.setStyle(buildActiveStyle()); }
            case "mosaic"           -> { mosaicPane.setVisible(true);           mosaicPane.setManaged(true);           tabMosaic.setStyle(buildActiveStyle()); }
            case "videoCreator"     -> { videoCreatorPane.setVisible(true);     videoCreatorPane.setManaged(true);     tabVideoCreator.setStyle(buildActiveStyle()); }
            case "share"            -> { shareContentPane.setVisible(true);     shareContentPane.setManaged(true);     tabShare.setStyle(buildActiveStyle()); }
            case "settings"         -> { settingsPane.setVisible(true);         settingsPane.setManaged(true); }
            case "userProfile"      -> { userProfilePane.setVisible(true);      userProfilePane.setManaged(true); }
        }
    }

    @FXML public void handleOpenImage() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            loadImage(file);
            switchPane("dipEditor");
            tabDipEditor.setSelected(true);
        }
    }

    // UPDATED: Now forcefully clears the right-side properties panel
    @FXML public void handleDelete() {
        mainImageView.setImage(null);
        uploadPlaceholder.setVisible(true);
        imageScrollPane.setVisible(false);
        currentFileName = "No file open";
        currentImagePath = null;
        currentFileHash = null;
        if (heartIcon != null) heartIcon.setVisible(false);
        if (annotationBox != null) annotationBox.setVisible(false);
        if (annotationField != null) annotationField.clear();
        
        // Forces the parameters to reset
        if (DIPController.getInstance() != null) DIPController.getInstance().clearUI();
        
        updateStatusBar();
    }

    @FXML public void handleUndo() { if (DIPController.getInstance() != null) DIPController.getInstance().undo(); }
    @FXML public void handleRedo() { if (DIPController.getInstance() != null) DIPController.getInstance().redo(); }

    @FXML public void handleZoomIn() {
        zoomLevel = Math.min(zoomLevel + 0.25, 5.0);
        mainImageView.setScaleX(zoomLevel);
        mainImageView.setScaleY(zoomLevel);
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    @FXML public void handleZoomOut() {
        zoomLevel = Math.max(zoomLevel - 0.25, 0.25);
        mainImageView.setScaleX(zoomLevel);
        mainImageView.setScaleY(zoomLevel);
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    @FXML public void handleShareEmail()    { switchPane("share"); tabShare.setSelected(true); }
    @FXML public void handleShareWhatsApp() { switchPane("share"); tabShare.setSelected(true); }
    @FXML public void handleSearch() { refreshGallery(searchBar.getText()); }
    @FXML public void handleUserProfile() { switchPane("userProfile"); }
    @FXML public void handleProfileBack() { switchPane("settings"); }
    @FXML public void handleSettings()    { switchPane("settings"); }

    @FXML public void handleToggleDarkMode() {
        darkMode = !darkMode;
        applyTheme();
    }

    // UPDATED: Extremely Deep Dark Mode for much better visual contrast
    private void applyTheme() {
        String sidebarW = sidebarExpanded ? "220" : "60";
        
        if (darkMode) {
            rootPane.setStyle("-fx-background-color: #090A0F; -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif;");
            toolbarHBox.setStyle("-fx-background-color: #12141D; -fx-padding: 10 20; -fx-border-color: #1F2332; -fx-border-width: 0 0 1 0;");
            sidebarVBox.setStyle("-fx-background-color: #12141D; -fx-padding: 16 10; -fx-min-width: " + sidebarW + "; -fx-pref-width: " + sidebarW + "; -fx-border-color: #1F2332; -fx-border-width: 0 1 0 0;");
            statusBarHBox.setStyle("-fx-background-color: #12141D; -fx-padding: 6 20; -fx-border-color: #1F2332; -fx-border-width: 1 0 0 0;");
            
            // Plunge the central workspaces into darkness
            if(mainContentStackPane != null) mainContentStackPane.setStyle("-fx-background-color: #090A0F;");
            if(dipWorkspaceBg != null) dipWorkspaceBg.setStyle("-fx-background-color: #090A0F;");
            if(uploadPlaceholder != null) uploadPlaceholder.setStyle("-fx-background-color: #090A0F;");
            if(imageScrollPane != null) imageScrollPane.setStyle("-fx-background: #090A0F; -fx-background-color: #090A0F; -fx-border-color: transparent;");
            if(annotationBox != null) annotationBox.setStyle("-fx-background-color: #12141D; -fx-padding: 14 20; -fx-border-color: #1F2332; -fx-border-width: 1 0 0 0;");

            // Pop the text color for readability
            if(brandLabel != null) brandLabel.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
            
            String darkBtn = "-fx-background-color: transparent; -fx-text-fill: #E2E8F0; -fx-font-size: 13; -fx-font-weight: bold; -fx-cursor: hand;";
            if(btnOpen != null) btnOpen.setStyle(darkBtn);
            if(btnClear != null) btnClear.setStyle(darkBtn);
            if(btnUndo != null) btnUndo.setStyle(darkBtn);
            if(btnRedo != null) btnRedo.setStyle(darkBtn);
            if(btnZoomIn != null) btnZoomIn.setStyle(darkBtn);
            if(btnZoomOut != null) btnZoomOut.setStyle(darkBtn);
            if(btnSettings != null) btnSettings.setStyle("-fx-background-color: #1F2332; -fx-text-fill: #E2E8F0; -fx-background-radius: 8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            
        } else {
            rootPane.setStyle("-fx-background-color: #F7F8FA; -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif;");
            toolbarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 10 20; -fx-border-color: #E8EAF0; -fx-border-width: 0 0 1 0;");
            sidebarVBox.setStyle("-fx-background-color: #1A1D2E; -fx-padding: 16 10; -fx-min-width: " + sidebarW + "; -fx-pref-width: " + sidebarW + ";");
            statusBarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 6 20; -fx-border-color: #E8EAF0; -fx-border-width: 1 0 0 0;");
            
            if(mainContentStackPane != null) mainContentStackPane.setStyle("-fx-background-color: #F7F8FA;");
            if(dipWorkspaceBg != null) dipWorkspaceBg.setStyle("-fx-background-color: #F0F2F8;");
            if(uploadPlaceholder != null) uploadPlaceholder.setStyle("-fx-background-color: #F0F2F8;");
            if(imageScrollPane != null) imageScrollPane.setStyle("-fx-background: #F0F2F8; -fx-background-color: #F0F2F8; -fx-border-color: transparent;");
            if(annotationBox != null) annotationBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 14 20; -fx-border-color: #E8EAF0; -fx-border-width: 1 0 0 0;");

            if(brandLabel != null) brandLabel.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1A1D2E;");
            
            String lightBtn = "-fx-background-color: transparent; -fx-text-fill: #3D4155; -fx-font-size: 13; -fx-font-weight: bold; -fx-cursor: hand;";
            if(btnOpen != null) btnOpen.setStyle(lightBtn);
            if(btnClear != null) btnClear.setStyle(lightBtn);
            if(btnUndo != null) btnUndo.setStyle(lightBtn);
            if(btnRedo != null) btnRedo.setStyle(lightBtn);
            if(btnZoomIn != null) btnZoomIn.setStyle(lightBtn);
            if(btnZoomOut != null) btnZoomOut.setStyle(lightBtn);
            if(btnSettings != null) btnSettings.setStyle("-fx-background-color: #F0F2F8; -fx-text-fill: #3D4155; -fx-background-radius: 8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
        }
        
        ToggleButton[] allTabs = {tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare};
        for (ToggleButton t : allTabs) t.setStyle(t.isSelected() ? buildActiveStyle() : buildInactiveStyle());
    }

    @FXML public void handleSaveProfile() {
        String name = profileNameField.getText().trim();
        if (name.isEmpty()) name = "User";
        profileDisplayName.setText(name);
        String[] parts = name.split("\\s+");
        profileInitialsLabel.setText(parts.length >= 2 ? String.valueOf(parts[0].charAt(0)).toUpperCase() + String.valueOf(parts[1].charAt(0)).toUpperCase() : String.valueOf(parts[0].charAt(0)).toUpperCase());
        profileSaveStatus.setText("✓ Profile saved!");
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
        pause.setOnFinished(e -> profileSaveStatus.setText(""));
        pause.play();
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

    @FXML public void handleBrowseFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Image Repository");
        
        File localGallery = new File("Edited_Gallery");
        if (localGallery.exists()) {
            dc.setInitialDirectory(localGallery);
        } else {
            dc.setInitialDirectory(new File(System.getProperty("user.dir")));
        }
        
        File dir = dc.showDialog(rootPane.getScene().getWindow());
        if (dir != null) {
            File[] files = dir.listFiles((d, name) -> name.matches("(?i).*\\.(png|jpg|jpeg|gif|bmp)"));
            if (files != null) {
                for (File f : files) if (!editedFiles.contains(f.getAbsolutePath())) editedFiles.add(f.getAbsolutePath());
                saveDatabase();
                refreshGallery("");
            }
        }
    }

    private void loadImage(File file) {
        currentImagePath = file.getAbsolutePath();
        currentFileHash = getFileHash(file);

        Image image = new Image(file.toURI().toString());
        mainImageView.setImage(image);
        mainImageView.setPreserveRatio(true);
        mainImageView.fitWidthProperty().bind(imageScrollPane.widthProperty().subtract(40));
        mainImageView.fitHeightProperty().bind(imageScrollPane.heightProperty().subtract(40));

        mainImageView.boundsInParentProperty().addListener((obs, oldVal, newVal) -> {
            if (heartIcon != null && mainImageView.getImage() != null) {
                heartIcon.setTranslateX(newVal.getWidth() / 2 - 25); 
                heartIcon.setTranslateY(-newVal.getHeight() / 2 + 25);
            }
        });

        uploadPlaceholder.setVisible(false);
        imageScrollPane.setVisible(true);
        currentFileName = file.getName();

        if (DIPController.getInstance() != null) DIPController.getInstance().onImageLoaded(image);
        updateStatusBar();

        if (annotationBox != null) { annotationBox.setVisible(true); annotationBox.setManaged(true); }
        if (heartIcon != null) heartIcon.setVisible(true);
        if (annotationField != null) annotationField.setText(annotationsDB.getProperty(currentFileHash, ""));
        checkAnnotationAndHeart();
    }

    private void updateStatusBar() {
        statusFileName.setText(currentFileName);
        statusResolution.setText((mainImageView.getImage() != null) ? (int) mainImageView.getImage().getWidth() + " × " + (int) mainImageView.getImage().getHeight() + " px" : "—");
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    @FXML public void handleSaveAnnotation() {
        if (currentFileHash == null) return;
        String note = annotationField.getText().trim();
        if (note.isEmpty()) annotationsDB.remove(currentFileHash);
        else annotationsDB.setProperty(currentFileHash, note);
        saveDatabase();
        checkAnnotationAndHeart();
    }

    @FXML public void handleSaveToGallery() {
        if (currentImagePath == null || mainImageView.getImage() == null) return;
        
        try {
            File outFile;
            if (currentImagePath.contains("Edited_Gallery")) {
                outFile = new File(currentImagePath);
            } else {
                File galleryDir = new File("Edited_Gallery");
                if (!galleryDir.exists()) galleryDir.mkdirs();
                outFile = new File(galleryDir, "edited_" + System.currentTimeMillis() + ".png");
            }
            
            ImageIO.write(SwingFXUtils.fromFXImage(mainImageView.getImage(), null), "png", outFile);
            
            currentImagePath = outFile.getAbsolutePath();
            currentFileHash = getFileHash(outFile);
            
        } catch (Exception e) {
            System.err.println("Error saving edited image: " + e.getMessage());
        }

        handleSaveAnnotation();
        
        if (!editedFiles.contains(currentImagePath)) {
            editedFiles.add(0, currentImagePath);
        }
        
        saveDatabase();
        handleDelete(); 
    }

    public void checkAnnotationAndHeart() {
        if (heartIcon == null) return;
        if (currentFileHash != null && annotationsDB.containsKey(currentFileHash)) {
            heartIcon.setStyle("-fx-text-fill: #F38BA8; -fx-font-size: 38; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0.5, 0, 2);");
            heartIcon.setVisible(true);
        } else {
            heartIcon.setVisible(false);
        }
    }

    private void loadDatabase() {
        File file = new File(DB_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                annotationsDB.load(fis);
                String history = annotationsDB.getProperty("SYS_EDITED_FILES", "");
                if (!history.isEmpty()) editedFiles.addAll(Arrays.asList(history.split(";;")));
            } catch (IOException e) { System.err.println(e.getMessage()); }
        }
    }

    private void saveDatabase() {
        annotationsDB.setProperty("SYS_EDITED_FILES", String.join(";;", editedFiles));
        try (FileOutputStream fos = new FileOutputStream(DB_FILE)) { annotationsDB.store(fos, "PixelForge Annotations Database"); } 
        catch (IOException e) { System.err.println(e.getMessage()); }
    }

    private void refreshGallery(String searchQuery) {
        if (galleryGrid == null) return;
        galleryGrid.getChildren().clear();
        String query = searchQuery.toLowerCase().trim();

        for (String path : editedFiles) {
            File f = new File(path);
            if (!f.exists()) continue;

            String fileHash = getFileHash(f);
            String annotation = annotationsDB.getProperty(fileHash, "");

            if (!query.isEmpty() && !f.getName().toLowerCase().contains(query) && !annotation.toLowerCase().contains(query)) continue;

            Image thumbImg = new Image(f.toURI().toString(), 220, 140, true, true);
            ImageView thumbView = new ImageView(thumbImg);
            thumbView.setPreserveRatio(true);
            thumbView.setFitWidth(200);
            thumbView.setFitHeight(120);

            StackPane imageContainer = new StackPane(thumbView);
            imageContainer.setMinSize(220, 140);
            imageContainer.setMaxSize(220, 140);
            imageContainer.setStyle("-fx-background-color: #F0F2F8; -fx-background-radius: 8;");

            StackPane overlay = new StackPane(imageContainer);
            if (annotationsDB.containsKey(fileHash)) {
                Label galleryHeart = new Label("♥");
                galleryHeart.setStyle("-fx-text-fill: #F38BA8; -fx-font-size: 24; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0.5, 0, 0);");
                StackPane.setAlignment(galleryHeart, Pos.TOP_RIGHT);
                StackPane.setMargin(galleryHeart, new Insets(5, 5, 0, 0));
                overlay.getChildren().add(galleryHeart);
            }

            Label nameLabel = new Label(f.getName().length() > 25 ? f.getName().substring(0, 22) + "…" : f.getName());
            nameLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #8892B0; -fx-padding: 8 0 0 0;");

            VBox card = new VBox(0, overlay, nameLabel);
            card.setAlignment(Pos.CENTER);
            card.setStyle("-fx-background-color: white; -fx-padding: 12; -fx-background-radius: 14; -fx-cursor: hand; -fx-border-color: #E8EAF0; -fx-border-width: 1; -fx-border-radius: 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 3);");

            card.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) showPreviewPopup(path, annotationsDB.getProperty(fileHash, "No annotation provided."));
            });

            galleryGrid.getChildren().add(card);
        }
    }

    private void showPreviewPopup(String path, String note) {
        Stage previewStage = new Stage();
        previewStage.setTitle("Preview — " + new File(path).getName());
        ImageView previewView = new ImageView(new Image(new File(path).toURI().toString()));
        previewView.setPreserveRatio(true);
        previewView.setFitWidth(720);
        previewView.setFitHeight(520);
        previewView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 24, 0, 0, 6);");

        Label noteTitle = new Label("Annotation");
        noteTitle.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #8892B0;");
        Label noteLabel = new Label(note);
        noteLabel.setWrapText(true);
        noteLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #1A1D2E;");
        
        Button editBtn = new Button("✎ Edit Image");
        editBtn.setStyle("-fx-background-color: #4F5BD5; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        editBtn.setOnAction(e -> {
            previewStage.close();
            loadImage(new File(path));
            switchPane("dipEditor");
            tabDipEditor.setSelected(true);
        });

        HBox titleBox = new HBox(noteTitle, new Region(), editBtn);
        HBox.setHgrow(titleBox.getChildren().get(1), Priority.ALWAYS);

        VBox textContainer = new VBox(10, titleBox, noteLabel);
        textContainer.setStyle("-fx-background-color: #F7F8FA; -fx-padding: 16 20; -fx-background-radius: 12; -fx-border-color: #E8EAF0; -fx-border-width: 1; -fx-border-radius: 12;");
        
        VBox layout = new VBox(20, previewView, textContainer);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 32;");

        previewStage.setScene(new Scene(layout));
        previewStage.show();
    }
    
    public ImageView getImageView() { return mainImageView; }
}