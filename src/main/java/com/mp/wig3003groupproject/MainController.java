package com.mp.wig3003groupproject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public class MainController {

    private static MainController instance;

    public MainController() {
        if (instance == null) {
            instance = this;
        }
    }

    public static MainController getInstance() {
        return instance;
    }

    // --- FXML UI Fields ---
    @FXML private BorderPane rootPane;
    @FXML private HBox toolbarHBox, statusBarHBox;
    @FXML private VBox sidebarVBox, dashboardPane, galleryPane, dipWorkspaceBg, uploadPlaceholder, annotationBox, extractionWorkspaceBg, mosaicWorkspaceBg;
    @FXML private HBox objectExtractionPane, mosaicPane;
    @FXML private VBox settingsPane, userProfilePane;
    @FXML private VBox shareGalleryContainer, selectedFileIndicator;
    @FXML private MainController shareContentPaneController;
    @FXML private Node shareContentPane, videoCreatorPaneContent;
    @FXML private HBox dipEditorPane;
    @FXML private StackPane mainContentStackPane;
    @FXML private ScrollPane imageScrollPane;
    @FXML private ImageView mainImageView;
    @FXML private ScrollPane extractionImageScrollPane, mosaicImageScrollPane;
    @FXML private ImageView extractionImageView, mosaicImageView;
    @FXML private VBox extractionUploadPlaceholder, mosaicUploadPlaceholder;
    @FXML private FlowPane galleryGrid;
    
    @FXML private Label brandLabel, heartIcon, profileInitialsLabel, profileDisplayName, profileSaveStatus;
    @FXML private Label statusFileName, statusResolution, statusZoom;
    @FXML private Label lblDashboard, lblGallery, lblDip, lblExtraction, lblMosaic, lblVideo, lblShare;
    @FXML private Label selectedFileNameLabel, emailStatusLabel;
    @FXML private Button btnBack, btnUndo, btnRedo, btnZoomIn, btnZoomOut, btnSettings, btnSendEmail;
    @FXML private TextField searchBar, annotationField, profileNameField, profileEmailField;
    @FXML private TextField emailRecipientField, emailSubjectField;
    @FXML private TextArea profileBioField, emailMessageField;
    @FXML private ToggleButton tabDashboard, tabGallery, tabDipEditor, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare;

    // --- State Fields ---
    private java.util.Stack<String> navHistory = new java.util.Stack<>();
    private File selectedFileToShare;
    private String currentPane = "dashboard";
    private String currentFileName = "No file open";
    private String currentImagePath;
    private String currentFileHash;
    private double zoomLevel = 1.0;
    private boolean darkMode = false;
    private boolean sidebarExpanded = true;
    private Image currentDisplayedImage;
    private List<String> editedFiles = new ArrayList<>();
    private Properties annotationsDB = new Properties();
    private static final String DB_FILE = "photo_editor_db.properties";

    public void setCurrentImagePath(String path) { this.currentImagePath = path; }
    public void setCurrentFileName(String name) { this.currentFileName = name; }
    public void setCurrentDisplayedImage(Image image) { this.currentDisplayedImage = image; }
    public String getCurrentImagePath() { return this.currentImagePath; }

    @FXML
    public void initialize() {
        ensureGalleryStorage();
        loadDatabase();
        // applyTheme() should be called after FXML injection is guaranteed.
        // In most cases, it's safer to use Platform.runLater if rootPane logic is sensitive.
        javafx.application.Platform.runLater(this::applyTheme);
    }

    @FXML public void showDashboard()        { switchPane("dashboard"); }
    @FXML public void showGallery()          { switchPane("gallery"); refreshGallery(""); }
    @FXML public void showDipEditor()        { switchPane("dipEditor"); }
    @FXML public void showObjectExtraction() { switchPane("objectExtraction"); }
    @FXML public void showMosaic()           { switchPane("mosaic"); }
    @FXML public void showVideoCreator()     { switchPane("videoCreator"); }
    @FXML public void showShareTab()         { switchPane("share"); syncSharePane(); }

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
            // If currently in Mosaic or Extraction tab, stay in that tab
            if (!currentPane.equals("mosaic") && !currentPane.equals("objectExtraction")) {
                switchPane("dipEditor");
                tabDipEditor.setSelected(true);
            }
        }
    }

    // UPDATED: Now forcefully clears the right-side properties panel
    @FXML public void handleDelete() {
        mainImageView.setImage(null);
        currentDisplayedImage = null;
        uploadPlaceholder.setVisible(true);
        imageScrollPane.setVisible(false);
        
        // Show upload placeholders for all tabs
        if (mosaicUploadPlaceholder != null) mosaicUploadPlaceholder.setVisible(true);
        if (extractionUploadPlaceholder != null) extractionUploadPlaceholder.setVisible(true);
        
        currentFileName = "No file open";
        currentImagePath = null;
        currentFileHash = null;
        if (heartIcon != null) heartIcon.setVisible(false);
        if (annotationBox != null) annotationBox.setVisible(false);
        if (annotationField != null) annotationField.clear();
        
        // Clear all controller states
        if (DIPController.getInstance() != null) DIPController.getInstance().clearUI();
        if (MosaicController.getInstance() != null) MosaicController.getInstance().clearUI();
        if (ObjectExtractionController.getInstance() != null) ObjectExtractionController.getInstance().clearUI();
        
        updateStatusBar();
    }

    @FXML public void handleUndo() { if (DIPController.getInstance() != null) DIPController.getInstance().undo(); }
    @FXML public void handleRedo() { if (DIPController.getInstance() != null) DIPController.getInstance().redo(); }

    @FXML public void handleZoomIn() {
        if ("dipEditor".equals(currentPane)) {
            zoomLevel = Math.min(zoomLevel + 0.25, 5.0);
            mainImageView.setScaleX(zoomLevel);
            mainImageView.setScaleY(zoomLevel);
        } else if ("mosaic".equals(currentPane) && MosaicController.getInstance() != null) {
            double newZoom = Math.min(MosaicController.getInstance().getZoomLevel() + 0.25, 5.0);
            MosaicController.getInstance().setZoomLevel(newZoom);
            zoomLevel = newZoom;
        } else if ("objectExtraction".equals(currentPane) && ObjectExtractionController.getInstance() != null) {
            double newZoom = Math.min(ObjectExtractionController.getInstance().getZoomLevel() + 0.25, 5.0);
            ObjectExtractionController.getInstance().setZoomLevel(newZoom);
            zoomLevel = newZoom;
        }
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    @FXML public void handleZoomOut() {
        if ("dipEditor".equals(currentPane)) {
            zoomLevel = Math.max(zoomLevel - 0.25, 0.25);
            mainImageView.setScaleX(zoomLevel);
            mainImageView.setScaleY(zoomLevel);
        } else if ("mosaic".equals(currentPane) && MosaicController.getInstance() != null) {
            double newZoom = Math.max(MosaicController.getInstance().getZoomLevel() - 0.25, 0.25);
            MosaicController.getInstance().setZoomLevel(newZoom);
            zoomLevel = newZoom;
        } else if ("objectExtraction".equals(currentPane) && ObjectExtractionController.getInstance() != null) {
            double newZoom = Math.max(ObjectExtractionController.getInstance().getZoomLevel() - 0.25, 0.25);
            ObjectExtractionController.getInstance().setZoomLevel(newZoom);
            zoomLevel = newZoom;
        }
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    @FXML public void handleShareEmail() { showShareTab(); }
    @FXML public void handleShareWhatsApp() { showShareTab(); }

    private void refreshShareGallery() {
        if (shareGalleryContainer == null) return;
        shareGalleryContainer.getChildren().clear();

        if (editedFiles.isEmpty()) {
            shareGalleryContainer.getChildren().add(new Label("No saved files yet."));
            return;
        }

        for (String path : editedFiles) {
            File file = new File(path);
            if (!file.exists()) continue;

            HBox item = new HBox(12);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setStyle("-fx-padding: 10; -fx-background-color: #F8FAFC; -fx-background-radius: 8; -fx-cursor: hand;");

            Node preview = buildSharePreview(file);
            VBox info = new VBox(2);
            Label name = new Label(file.getName());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");
            Label pathLabel = new Label(file.getParent());
            pathLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #94A3B8;");
            info.getChildren().addAll(name, pathLabel);

            item.getChildren().addAll(preview, info);
            item.setOnMouseClicked(e -> selectFileForShare(file, item));
            shareGalleryContainer.getChildren().add(item);
        }
    }

    public void syncShareState(List<String> files, Properties annotations) {
        if (files != null) {
            editedFiles = new ArrayList<>(files);
        }
        if (annotations != null) {
            annotationsDB = annotations;
        }
        refreshShareGallery();
    }

    private void syncSharePane() {
        if (shareContentPaneController != null && shareContentPaneController != this) {
            shareContentPaneController.syncShareState(editedFiles, annotationsDB);
        } else {
            refreshShareGallery();
        }
    }

    private Node buildSharePreview(File file) {
        if (isVideoFile(file)) {
            StackPane box = new StackPane();
            box.setPrefSize(40, 40);
            box.setMinSize(40, 40);
            box.setMaxSize(40, 40);
            box.setStyle("-fx-background-color: #E0E7FF; -fx-background-radius: 8;");
            Label icon = new Label("▶");
            icon.setStyle("-fx-text-fill: #4F5BD5; -fx-font-size: 18; -fx-font-weight: bold;");
            box.getChildren().add(icon);
            return box;
        }

        ImageView thumb = new ImageView();
        thumb.setFitHeight(40);
        thumb.setFitWidth(40);
        thumb.setPreserveRatio(true);
        try {
            thumb.setImage(new Image(file.toURI().toString()));
        } catch (Exception ex) {
            // keep placeholder style below
        }

        StackPane wrapper = new StackPane(thumb);
        wrapper.setPrefSize(40, 40);
        wrapper.setMinSize(40, 40);
        wrapper.setMaxSize(40, 40);
        wrapper.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 8;");
        return wrapper;
    }

    private void selectFileForShare(File file, HBox item) {
        selectedFileToShare = file;
        if (shareGalleryContainer != null) {
            for (Node node : shareGalleryContainer.getChildren()) {
                node.setStyle("-fx-padding: 10; -fx-background-color: #F8FAFC; -fx-background-radius: 8; -fx-cursor: hand;");
            }
        }
        item.setStyle("-fx-padding: 10; -fx-background-color: #E0E7FF; -fx-border-color: #4F5BD5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;");
        if (selectedFileIndicator != null) selectedFileIndicator.setVisible(true);
        if (selectedFileNameLabel != null) selectedFileNameLabel.setText(file.getName());
    }

    @FXML public void handleActualEmailSend() {
        if (selectedFileToShare == null) {
            showSimpleWarning("No selection", "Please select a file from the gallery first.");
            return;
        }

        String recipient = emailRecipientField.getText().trim();
        if (recipient.isEmpty() || !recipient.contains("@")) {
            showSimpleWarning("Invalid Email", "Please enter a valid recipient email address.");
            return;
        }

        if (btnSendEmail != null) {
            btnSendEmail.setDisable(true);
            btnSendEmail.setText("⏳ Sending...");
        }
        emailStatusLabel.setText("");

        new Thread(() -> {
            try {
                String subject = emailSubjectField.getText().isEmpty() ? "Check out my creation!" : emailSubjectField.getText();
                String body = emailMessageField.getText();

                EmailService.sendEmailWithAttachment(recipient, subject, body, selectedFileToShare);

                javafx.application.Platform.runLater(() -> {
                    if (btnSendEmail != null) {
                        btnSendEmail.setDisable(false);
                        btnSendEmail.setText("🚀 Send with Attachment");
                    }
                    emailStatusLabel.setText("✅ Sent successfully!");
                    emailStatusLabel.setStyle("-fx-text-fill: #10B981;");
                });
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    if (btnSendEmail != null) {
                        btnSendEmail.setDisable(false);
                        btnSendEmail.setText("🚀 Send again");
                    }
                    emailStatusLabel.setText("❌ Failed: " + e.getMessage());
                    emailStatusLabel.setStyle("-fx-text-fill: #EF4444;");
                });
            }
        }).start();
    }

    private void ensureGalleryStorage() {
        File galleryDir = getGalleryDirectory();
        if (!galleryDir.exists() && !galleryDir.mkdirs()) {
            throw new IllegalStateException("Unable to create gallery folder at " + galleryDir.getAbsolutePath());
        }
    }

    private void showSimpleInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSimpleWarning(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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
        if (rootPane == null) return; // Prevent NPE if called prematurely
        String sidebarW = sidebarExpanded ? "220" : "60";
        
        if (darkMode) {
            rootPane.setStyle("-fx-background-color: #090A0F; -fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif;");
            if (toolbarHBox != null) toolbarHBox.setStyle("-fx-background-color: #12141D; -fx-padding: 10 20; -fx-border-color: #1F2332; -fx-border-width: 0 0 1 0;");
            if (sidebarVBox != null) sidebarVBox.setStyle("-fx-background-color: #12141D; -fx-padding: 16 10; -fx-min-width: " + sidebarW + "; -fx-pref-width: " + sidebarW + "; -fx-border-color: #1F2332; -fx-border-width: 0 1 0 0;");
            if (statusBarHBox != null) statusBarHBox.setStyle("-fx-background-color: #12141D; -fx-padding: 6 20; -fx-border-color: #1F2332; -fx-border-width: 1 0 0 0;");
            
            // Plunge the central workspaces into darkness
            if(mainContentStackPane != null) mainContentStackPane.setStyle("-fx-background-color: #090A0F;");
            if(dipWorkspaceBg != null) dipWorkspaceBg.setStyle("-fx-background-color: #090A0F;");
            if(extractionWorkspaceBg != null) extractionWorkspaceBg.setStyle("-fx-background-color: #090A0F;");
            if(mosaicWorkspaceBg != null) mosaicWorkspaceBg.setStyle("-fx-background-color: #090A0F;");
            if(uploadPlaceholder != null) uploadPlaceholder.setStyle("-fx-background-color: #090A0F;");
            if(extractionUploadPlaceholder != null) extractionUploadPlaceholder.setStyle("-fx-background-color: #090A0F;");
            if(mosaicUploadPlaceholder != null) mosaicUploadPlaceholder.setStyle("-fx-background-color: #090A0F;");
            if(imageScrollPane != null) imageScrollPane.setStyle("-fx-background: #090A0F; -fx-background-color: #090A0F; -fx-border-color: transparent;");
            if(extractionImageScrollPane != null) extractionImageScrollPane.setStyle("-fx-background: #090A0F; -fx-background-color: #090A0F; -fx-border-color: transparent;");
            if(mosaicImageScrollPane != null) mosaicImageScrollPane.setStyle("-fx-background: #090A0F; -fx-background-color: #090A0F; -fx-border-color: transparent;");
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
            if (toolbarHBox != null) toolbarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 10 20; -fx-border-color: #E8EAF0; -fx-border-width: 0 0 1 0;");
            if (sidebarVBox != null) sidebarVBox.setStyle("-fx-background-color: #E8EAF0; -fx-padding: 16 10; -fx-min-width: " + sidebarW + "; -fx-pref-width: " + sidebarW + "; -fx-border-color: #D1D5DB; -fx-border-width: 0 1 0 0;");
            if (statusBarHBox != null) statusBarHBox.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 6 20; -fx-border-color: #E8EAF0; -fx-border-width: 1 0 0 0;");
            
            if(mainContentStackPane != null) mainContentStackPane.setStyle("-fx-background-color: #F7F8FA;");
            if(dipWorkspaceBg != null) dipWorkspaceBg.setStyle("-fx-background-color: #F0F2F8;");
            if(extractionWorkspaceBg != null) extractionWorkspaceBg.setStyle("-fx-background-color: #F0F2F8;");
            if(mosaicWorkspaceBg != null) mosaicWorkspaceBg.setStyle("-fx-background-color: #F0F2F8;");
            if(uploadPlaceholder != null) uploadPlaceholder.setStyle("-fx-background-color: #F0F2F8;");
            if(extractionUploadPlaceholder != null) extractionUploadPlaceholder.setStyle("-fx-background-color: #F0F2F8;");
            if(mosaicUploadPlaceholder != null) mosaicUploadPlaceholder.setStyle("-fx-background-color: #F0F2F8;");
            if(imageScrollPane != null) imageScrollPane.setStyle("-fx-background: #F0F2F8; -fx-background-color: #F0F2F8; -fx-border-color: transparent;");
            if(extractionImageScrollPane != null) extractionImageScrollPane.setStyle("-fx-background: #F0F2F8; -fx-background-color: #F0F2F8; -fx-border-color: transparent;");
            if(mosaicImageScrollPane != null) mosaicImageScrollPane.setStyle("-fx-background: #F0F2F8; -fx-background-color: #F0F2F8; -fx-border-color: transparent;");
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
                lblDashboard.setVisible(sidebarExpanded); 
                lblDashboard.setManaged(sidebarExpanded); 
            } 
            if (lblGallery != null) { 
                lblGallery.setVisible(sidebarExpanded); 
                lblGallery.setManaged(sidebarExpanded); 
            } 
            if (lblDip != null) { 
                lblDip.setVisible(sidebarExpanded); 
                lblDip.setManaged(sidebarExpanded); 
            } 
            if (lblExtraction != null) { 
                lblExtraction.setVisible(sidebarExpanded); 
                lblExtraction.setManaged(sidebarExpanded); 
            } 
            if (lblMosaic != null) { 
                lblMosaic.setVisible(sidebarExpanded); 
                lblMosaic.setManaged(sidebarExpanded); 
            } 
            if (lblShare != null) { 
                lblShare.setVisible(sidebarExpanded); 
                lblShare.setManaged(sidebarExpanded); 
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
        if (profileDisplayName != null) profileDisplayName.setText(name);
        String[] parts = name.split("\\s+");
        if (profileInitialsLabel != null) profileInitialsLabel.setText(parts.length >= 2 ? String.valueOf(parts[0].charAt(0)).toUpperCase() + String.valueOf(parts[1].charAt(0)).toUpperCase() : String.valueOf(parts[0].charAt(0)).toUpperCase());
        if (profileSaveStatus != null) profileSaveStatus.setText("✓ Profile saved!");
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
        pause.setOnFinished(e -> { if (profileSaveStatus != null) profileSaveStatus.setText(""); });
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
        
        File localGallery = getGalleryDirectory();
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
        currentDisplayedImage = image;
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
        
        // Hide upload placeholders for all tabs
        if (mosaicUploadPlaceholder != null) mosaicUploadPlaceholder.setVisible(false);
        if (extractionUploadPlaceholder != null) extractionUploadPlaceholder.setVisible(false);
        
        currentFileName = file.getName();

        // Notify all controllers about the loaded image
        if (DIPController.getInstance() != null) DIPController.getInstance().onImageLoaded(image);
        if (MosaicController.getInstance() != null) MosaicController.getInstance().onImageLoaded(image);
        if (ObjectExtractionController.getInstance() != null) ObjectExtractionController.getInstance().onImageLoaded(image);
        updateStatusBar();

        if (annotationBox != null) { annotationBox.setVisible(true); annotationBox.setManaged(true); }
        if (heartIcon != null) heartIcon.setVisible(true);
        if (annotationField != null) annotationField.setText(annotationsDB.getProperty(currentFileHash, ""));
        checkAnnotationAndHeart();
    }

    private void updateStatusBar() {
        if (statusFileName != null) statusFileName.setText(currentFileName);
        if (statusResolution != null) {
            statusResolution.setText((mainImageView.getImage() != null) ? (int) mainImageView.getImage().getWidth() + " × " + (int) mainImageView.getImage().getHeight() + " px" : "—");
        }
        if (statusZoom != null) statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
    }

    @FXML public void handleSaveAnnotation() {
        if (currentFileHash == null || annotationField == null) return;
        String note = annotationField.getText().trim();
        if (note.isEmpty()) annotationsDB.remove(currentFileHash);
        else annotationsDB.setProperty(currentFileHash, note);
        saveDatabase();
        checkAnnotationAndHeart();
    }

    @FXML public void handleSaveToGallery() {
        try {
            File saved = saveCurrentMediaToGallery();
            System.out.println("Saved successfully to: " + saved.getAbsolutePath());
            showSimpleInfo("Saved", "Saved to gallery: " + saved.getName());
        } catch (Exception e) {
            e.printStackTrace();
            showSimpleWarning("Save Error", "Could not write the edited file: " + e.getMessage());
        }

        // Keep the current media visible after saving to gallery.
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

    private File saveCurrentMediaToGallery() throws IOException {
        ensureGalleryStorage();
        File galleryDir = getGalleryDirectory();
        File target = buildGalleryTargetFile(galleryDir);
        File saved = saveDisplayedImageToTarget(target, true);
        try {
            handleSaveAnnotation();
            if (currentImagePath != null && !editedFiles.contains(currentImagePath)) editedFiles.add(0, currentImagePath);
            saveDatabase();
        } catch (Exception e) {
            System.err.println("Post-save refresh warning: " + e.getMessage());
        }
        syncSharePane();
        return saved;
    }

    private File getGalleryDirectory() {
        return new File(System.getProperty("user.dir"), "Edited_Gallery");
    }

    private File buildGalleryTargetFile(File galleryDir) {
        String extension = isImageInEditor() ? ".png" : getCurrentMediaExtension();
        return new File(galleryDir, "edited_" + System.currentTimeMillis() + extension);
    }

    public File exportCurrentMedia(File requestedTarget) throws IOException {
        return saveCurrentMediaToTarget(requestedTarget, false);
    }

    private boolean hasCurrentMedia() {
        return isImageInEditor() || getCurrentMediaSourceFile() != null;
    }

    private boolean isImageInEditor() {
        return mainImageView != null && mainImageView.getImage() != null;
    }

    private File getCurrentMediaSourceFile() {
        if (currentImagePath == null || currentImagePath.isBlank()) return null;
        File source = new File(currentImagePath);
        return source.exists() ? source : null;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov");
    }

    private String getCurrentMediaExtension() {
        File source = getCurrentMediaSourceFile();
        if (source == null) return ".png";
        String name = source.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex < 0 ? ".png" : name.substring(dotIndex);
    }

    private File saveCurrentMediaToTarget(File requestedTarget, boolean recordInGallery) throws IOException {
        if (requestedTarget == null) throw new IOException("No save path selected.");

        if (isImageInEditor()) {
            return saveDisplayedImageToTarget(requestedTarget, recordInGallery);
        }

        File source = getCurrentMediaSourceFile();
        if (source == null) throw new IOException("No current media is available to save.");

        File target = ensureExtension(requestedTarget, getCurrentMediaExtension());
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        updateSavedMediaState(target, recordInGallery);
        return target;
    }

    private File saveDisplayedImageToTarget(File requestedTarget, boolean recordInGallery) throws IOException {
        Image imageToSave = currentDisplayedImage != null ? currentDisplayedImage : (mainImageView != null ? mainImageView.getImage() : null);
        if (imageToSave == null) {
            throw new IOException("No edited image is currently displayed.");
        }

        File target = ensureExtension(requestedTarget, ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(imageToSave, null), "png", target);
        updateSavedMediaState(target, recordInGallery);
        return target;
    }

    private void updateSavedMediaState(File savedFile, boolean recordInGallery) throws IOException {
        currentImagePath = savedFile.getAbsolutePath();
        currentFileName = savedFile.getName();
        currentFileHash = getFileHash(savedFile);
        if (recordInGallery && !editedFiles.contains(currentImagePath)) editedFiles.add(0, currentImagePath);
        if (recordInGallery) saveDatabase();
        updateStatusBar();
    }

    private File ensureExtension(File file, String extension) {
        String name = file.getName();
        if (name.toLowerCase().endsWith(extension.toLowerCase())) return file;
        String parent = file.getParent();
        return parent == null ? new File(name + extension) : new File(parent, name + extension);
    }

    // Helper method for MosaicController and ObjectExtractionController to save images
    public void saveImageToGallery(Image image, String prefix) {
        try {
            ensureGalleryStorage();
            File galleryDir = getGalleryDirectory();
            File target = new File(galleryDir, prefix + "_" + System.currentTimeMillis() + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target);
            
            // Add to gallery tracking
            String targetPath = target.getAbsolutePath();
            if (!editedFiles.contains(targetPath)) {
                editedFiles.add(0, targetPath);
            }
            saveDatabase();
            refreshGallery(""); // Refresh gallery display
            syncSharePane(); // Refresh share pane
            
            showSimpleInfo("Saved", "✅ Saved to gallery: " + target.getName());
            System.out.println("Saved successfully to: " + target.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showSimpleWarning("Save Error", "Could not save to gallery: " + e.getMessage());
        }
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
            Node preview = buildGalleryPreview(f);
            if (annotationsDB.containsKey(hash)) {
                Label h = new Label("♥");
                h.setStyle("-fx-text-fill: #F38BA8; -fx-font-size: 18;");
                StackPane.setAlignment(h, Pos.TOP_RIGHT);
                imgStack.getChildren().addAll(preview, h);
            } else imgStack.getChildren().add(preview);

            Label name = new Label(f.getName());
            name.setStyle("-fx-font-size: 11; -fx-text-fill: #1A1D2E; -fx-font-weight: bold;");
            name.setMaxWidth(140);
            
            card.getChildren().addAll(imgStack, name);
            card.setCursor(javafx.scene.Cursor.HAND);
            card.setOnMouseClicked(e -> {
                if (isVideoFile(f)) {
                    setCurrentImagePath(f.getAbsolutePath());
                    setCurrentFileName(f.getName());
                    showVideoCreator();
                    return;
                }
                loadImage(f);
                switchPane("dipEditor");
                tabDipEditor.setSelected(true);
            });
            galleryGrid.getChildren().add(card);
        }
    }

    private Node buildGalleryPreview(File file) {
        if (isVideoFile(file)) {
            StackPane box = new StackPane();
            box.setPrefSize(140, 110);
            box.setStyle("-fx-background-color: #E0E7FF; -fx-background-radius: 10;");
            Label icon = new Label("▶");
            icon.setStyle("-fx-font-size: 28; -fx-text-fill: #4F5BD5; -fx-font-weight: bold;");
            box.getChildren().add(icon);
            return box;
        }

        ImageView iv = new ImageView(new Image(file.toURI().toString()));
        iv.setFitWidth(140);
        iv.setFitHeight(110);
        iv.setPreserveRatio(true);
        return iv;
    }

    // --- Data Accessors for Sub-Controllers ---
    public List<String> getEditedFiles() { return editedFiles; }
    public Properties getAnnotationsDB() { return annotationsDB; }
    public ImageView getImageView() { return mainImageView; }
    public ImageView getExtractionImageView() { return extractionImageView; }
    public ImageView getMosaicImageView() { return mosaicImageView; }
}
