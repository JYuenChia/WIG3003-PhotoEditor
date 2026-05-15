package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

    private static MainController instance;

    public MainController() {
        instance = this;
    }

    public static MainController getInstance() {
        return instance;
    }

    // --- FXML UI Fields ---
    @FXML private BorderPane rootPane;
    @FXML private HBox toolbarHBox, statusBarHBox;
    @FXML private VBox sidebarVBox, dashboardPane, galleryPane, dipWorkspaceBg, uploadPlaceholder, annotationBox;
    @FXML private VBox objectExtractionPane, mosaicPane, shareContentPane, settingsPane, userProfilePane;
    @FXML private Node videoCreatorPaneContent;
    @FXML private HBox dipEditorPane;
    @FXML private StackPane mainContentStackPane;
    @FXML private ScrollPane imageScrollPane;
    @FXML private ImageView mainImageView;
    @FXML private FlowPane galleryGrid;
    
    @FXML private Label brandLabel, heartIcon, profileInitialsLabel, profileDisplayName, profileSaveStatus;
    @FXML private Label statusFileName, statusResolution, statusZoom;
    @FXML private Label lblDashboard, lblGallery, lblDip, lblExtraction, lblMosaic, lblVideo, lblShare;
    @FXML private Button btnBack, btnUndo, btnRedo, btnZoomIn, btnZoomOut, btnSettings;
    @FXML private TextField searchBar, annotationField, profileNameField, profileEmailField;
    @FXML private TextArea profileBioField;
    @FXML private ToggleButton tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare;

    // --- State Fields ---
    private java.util.Stack<String> navHistory = new java.util.Stack<>();
    private String currentPane = "dashboard";
    private String currentFileName = "No file open";
    private String currentImagePath;
    private String currentFileHash;
    private double zoomLevel = 1.0;
    private boolean darkMode = false;
    private boolean sidebarExpanded = true;
    private List<String> editedFiles = new ArrayList<>();
    private Properties annotationsDB = new Properties();
    private static final String DB_FILE = "photo_editor_db.properties";

    @FXML
    public void initialize() {
        loadDatabase();
        applyTheme();
    }

    @FXML public void showDashboard()        { switchPane("dashboard"); }
    @FXML public void showGallery()          { switchPane("gallery"); refreshGallery(""); }
    @FXML public void showDipEditor()        { switchPane("dipEditor"); }
    @FXML public void showObjectExtraction() { switchPane("objectExtraction"); }
    @FXML public void showMosaic()           { switchPane("mosaic"); }
    @FXML public void showVideoCreator()     { switchPane("videoCreator"); }
    @FXML public void showShare()            { switchPane("share"); }

    @FXML public void handleBack() {
        if (!navHistory.isEmpty()) {
            String lastPane = navHistory.pop();
            currentPane = lastPane;
            // Update UI without pushing to history
            updatePaneVisibility(lastPane);
            
            // Sync toggle group selection
            syncSidebarSelection(lastPane);
            
            applyTheme();
        }
    }

    private void syncSidebarSelection(String paneName) {
        if ("dashboard".equals(paneName)) tabDashboard.setSelected(true);
        else if ("gallery".equals(paneName)) tabGallery.setSelected(true);
        else if ("dipEditor".equals(paneName)) tabDipEditor.setSelected(true);
        else if ("objectExtraction".equals(paneName)) tabObjectExtraction.setSelected(true);
        else if ("mosaic".equals(paneName)) tabMosaic.setSelected(true);
        else if ("videoCreator".equals(paneName)) tabVideoCreator.setSelected(true);
        else if ("share".equals(paneName)) tabShare.setSelected(true);
    }

    private void switchPane(String paneName) {
        if (!currentPane.equals(paneName)) {
            navHistory.push(currentPane);
            currentPane = paneName;
        }
        updatePaneVisibility(paneName);
        applyTheme();
    }

    private void updatePaneVisibility(String paneName) {
        dashboardPane.setVisible("dashboard".equals(paneName));
        dashboardPane.setManaged("dashboard".equals(paneName));
        galleryPane.setVisible("gallery".equals(paneName));
        galleryPane.setManaged("gallery".equals(paneName));
        dipEditorPane.setVisible("dipEditor".equals(paneName));
        dipEditorPane.setManaged("dipEditor".equals(paneName));
        objectExtractionPane.setVisible("objectExtraction".equals(paneName));
        objectExtractionPane.setManaged("objectExtraction".equals(paneName));
        mosaicPane.setVisible("mosaic".equals(paneName));
        mosaicPane.setManaged("mosaic".equals(paneName));
        if (videoCreatorPaneContent != null) {
            videoCreatorPaneContent.setVisible("videoCreator".equals(paneName));
            videoCreatorPaneContent.setManaged("videoCreator".equals(paneName));
        }
        shareContentPane.setVisible("share".equals(paneName));
        shareContentPane.setManaged("share".equals(paneName));
        settingsPane.setVisible("settings".equals(paneName));
        settingsPane.setManaged("settings".equals(paneName));
        userProfilePane.setVisible("userProfile".equals(paneName));
        userProfilePane.setManaged("userProfile".equals(paneName));
    }

    private String buildActiveStyle() {
        if (darkMode) {
            return "-fx-background-color: #2E3250; -fx-text-fill: #CDD6F4; -fx-background-radius: 12; -fx-padding: 0; -fx-cursor: hand; -fx-min-height: 50;";
        } else {
            return "-fx-background-color: #C7D2FE; -fx-text-fill: #3730A3; -fx-background-radius: 12; -fx-padding: 0; -fx-cursor: hand; -fx-min-height: 50;";
        }
    }

    private String buildInactiveStyle() {
        if (darkMode) {
            return "-fx-background-color: transparent; -fx-text-fill: #8892B0; -fx-background-radius: 12; -fx-padding: 0; -fx-cursor: hand; -fx-min-height: 50;";
        } else {
            return "-fx-background-color: transparent; -fx-text-fill: #6B7280; -fx-background-radius: 12; -fx-padding: 0; -fx-cursor: hand; -fx-min-height: 50;";
        }
    }

    @FXML
    public void handleSidebarToggle() {
        sidebarExpanded = !sidebarExpanded;
        applyTheme();
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
            if(btnBack != null) btnBack.setStyle(darkBtn);
            if(btnUndo != null) btnUndo.setStyle(darkBtn);
            if(btnRedo != null) btnRedo.setStyle(darkBtn);
            if(btnZoomIn != null) btnZoomIn.setStyle(darkBtn);
            if(btnZoomOut != null) btnZoomOut.setStyle(darkBtn);
            if(btnSettings != null) btnSettings.setStyle("-fx-background-color: #1F2332; -fx-text-fill: #E2E8F0; -fx-background-radius: 8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            
        } else {
            rootPane.setStyle("-fx-background-color: #F7F8FA; -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif;");
            toolbarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 10 20; -fx-border-color: #E8EAF0; -fx-border-width: 0 0 1 0;");
            sidebarVBox.setStyle("-fx-background-color: #E8EAF0; -fx-padding: 16 10; -fx-min-width: " + sidebarW + "; -fx-pref-width: " + sidebarW + "; -fx-border-color: #D1D5DB; -fx-border-width: 0 1 0 0;");
            statusBarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 6 20; -fx-border-color: #E8EAF0; -fx-border-width: 1 0 0 0;");
            
            if(mainContentStackPane != null) mainContentStackPane.setStyle("-fx-background-color: #F7F8FA;");
            if(dipWorkspaceBg != null) dipWorkspaceBg.setStyle("-fx-background-color: #F0F2F8;");
            if(uploadPlaceholder != null) uploadPlaceholder.setStyle("-fx-background-color: #F0F2F8;");
            if(imageScrollPane != null) imageScrollPane.setStyle("-fx-background: #F0F2F8; -fx-background-color: #F0F2F8; -fx-border-color: transparent;");
            if(annotationBox != null) annotationBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 14 20; -fx-border-color: #E8EAF0; -fx-border-width: 1 0 0 0;");

            if(brandLabel != null) brandLabel.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1F2937;");
            
            String lightBtn = "-fx-background-color: transparent; -fx-text-fill: #374151; -fx-font-size: 13; -fx-font-weight: bold; -fx-cursor: hand;";
            if(btnBack != null) btnBack.setStyle(lightBtn);
            if(btnUndo != null) btnUndo.setStyle(lightBtn);
            if(btnRedo != null) btnRedo.setStyle(lightBtn);
            if(btnZoomIn != null) btnZoomIn.setStyle(lightBtn);
            if(btnZoomOut != null) btnZoomOut.setStyle(lightBtn);
            if(btnSettings != null) btnSettings.setStyle("-fx-background-color: #E0E7FF; -fx-text-fill: #4338CA; -fx-background-radius: 8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
        }
        
        // Update Sidebar Labels
        if (lblDashboard != null) {
            lblDashboard.setVisible(sidebarExpanded); lblDashboard.setManaged(sidebarExpanded);
            lblGallery.setVisible(sidebarExpanded);   lblGallery.setManaged(sidebarExpanded);
            lblDip.setVisible(sidebarExpanded);       lblDip.setManaged(sidebarExpanded);
            lblExtraction.setVisible(sidebarExpanded); lblExtraction.setManaged(sidebarExpanded);
            lblMosaic.setVisible(sidebarExpanded);    lblMosaic.setManaged(sidebarExpanded);
            lblVideo.setVisible(sidebarExpanded);     lblVideo.setManaged(sidebarExpanded);
            lblShare.setVisible(sidebarExpanded);     lblShare.setManaged(sidebarExpanded);
        }

        ToggleButton[] allTabs = {tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare};
        for (ToggleButton t : allTabs) {
            if (t != null) {
                t.setStyle(t.isSelected() ? buildActiveStyle() : buildInactiveStyle());
                t.setAlignment(sidebarExpanded ? javafx.geometry.Pos.CENTER_LEFT : javafx.geometry.Pos.CENTER);
            }
        }
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
                for (String key : annotationsDB.stringPropertyNames()) {
                    if (key.startsWith("path_")) {
                        String path = annotationsDB.getProperty(key);
                        if (new File(path).exists() && !editedFiles.contains(path)) editedFiles.add(path);
                    }
                }
            } catch (Exception e) { System.err.println("DB Load Error: " + e.getMessage()); }
        }
    }

    private void saveDatabase() {
        for (int i = 0; i < editedFiles.size(); i++) annotationsDB.setProperty("path_" + i, editedFiles.get(i));
        try (FileOutputStream fos = new FileOutputStream(DB_FILE)) {
            annotationsDB.store(fos, "PhotoEditor Database");
        } catch (Exception e) { System.err.println("DB Save Error: " + e.getMessage()); }
    }

    // --- Gallery Logic ---
    private void refreshGallery(String filter) {
        galleryGrid.getChildren().clear();
        for (String path : editedFiles) {
            File f = new File(path);
            String hash = getFileHash(f);
            String note = annotationsDB.getProperty(hash, "").toLowerCase();
            
            if (!filter.isEmpty() && !f.getName().toLowerCase().contains(filter.toLowerCase()) && !note.contains(filter.toLowerCase())) continue;

            VBox card = new VBox(8);
            card.setAlignment(Pos.CENTER);
            card.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 10; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");

            StackPane imgStack = new StackPane();
            ImageView iv = new ImageView(new Image(f.toURI().toString()));
            iv.setFitWidth(140); iv.setFitHeight(110); iv.setPreserveRatio(true);
            
            if (annotationsDB.containsKey(hash)) {
                Label h = new Label("♥");
                h.setStyle("-fx-text-fill: #F38BA8; -fx-font-size: 18;");
                StackPane.setAlignment(h, Pos.TOP_RIGHT);
                imgStack.getChildren().addAll(iv, h);
            } else imgStack.getChildren().add(iv);

            Label name = new Label(f.getName());
            name.setStyle("-fx-font-size: 11; -fx-text-fill: #1A1D2E; -fx-font-weight: bold;");
            name.setMaxWidth(140);
            
            card.getChildren().addAll(imgStack, name);
            card.setCursor(javafx.scene.Cursor.HAND);
            card.setOnMouseClicked(e -> { loadImage(f); switchPane("dipEditor"); tabDipEditor.setSelected(true); });
            galleryGrid.getChildren().add(card);
        }
    }

    // --- Data Accessors for Sub-Controllers ---
    public List<String> getEditedFiles() { return editedFiles; }
    public Properties getAnnotationsDB() { return annotationsDB; }
    public ImageView getImageView() { return mainImageView; }
}
