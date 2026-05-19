package com.mp.wig3003groupproject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.MenuButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainController {

    // Sidebar toggle buttons
    @FXML private ToggleButton tabDashboard, tabGallery, tabDipEditor, tabAnnotation;
    @FXML private ToggleButton tabFavorites, tabObjectExtraction, tabMosaic, tabVideoCreator;
    @FXML private ToggleButton tabShare, tabSettings;
    @FXML private VBox sidebarVBox;

    private boolean sidebarExpanded = true;
    private static final String[][] SIDEBAR_LABELS = {
        {"\uD83C\uDFE0", "Dashboard"}, {"🖼", "Gallery"}, {"🎨", "DIP Editor"},
        {"\u270F", "Annotation"}, {"\u2B50", "Favorites"}, {"\u2702", "Object\nExtraction"},
        {"🔲", "Mosaic"}, {"🎬", "Video Creator"}, {"📤", "Share"}
    };

    // Content panes
    @FXML private VBox dashboardPane, galleryPane, annotationPane, favoritesPane;
    @FXML private VBox objectExtractionPane, mosaicPane, videoCreatorPane, shareContentPane, settingsPane;
    @FXML private VBox userProfilePane;
    @FXML private HBox dipEditorPane;

    // User profile fields
    @FXML private TextField profileNameField, profileEmailField;
    @FXML private TextArea profileBioField;
    @FXML private Label profileInitialsLabel, profileDisplayName, profileSaveStatus;

    // DIP Editor image area
    @FXML private ImageView mainImageView;
    @FXML private ScrollPane imageScrollPane;
    @FXML private VBox uploadPlaceholder;

    // Toolbar
    @FXML private Button backBtn;
    @FXML private MenuButton darkModeBtn;
    @FXML private TextField searchBar;
    @FXML private BorderPane rootPane;
    @FXML private HBox toolbarHBox;
    @FXML private HBox statusBarHBox;

    // Status bar
    @FXML private Label statusFileName, statusResolution, statusZoom, statusProcessing;

    private boolean darkMode = false;
    private double zoomLevel = 1.0;
    private String currentFileName = "No file";
    private String currentPane = "dashboard";
    private final Deque<String> navHistory = new ArrayDeque<>();
    private boolean skipHistoryPush = false;

    private static MainController instance;
    public MainController() { instance = this; }
    public static MainController getInstance() { return instance; }

    @FXML
    public void initialize() {
        mainImageView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            DIPController dip = DIPController.getInstance();
            if (dip != null && dip.isSelectionMode() && mainImageView.getImage() != null) {
                double ratioX = mainImageView.getImage().getWidth() / mainImageView.getBoundsInLocal().getWidth();
                double ratioY = mainImageView.getImage().getHeight() / mainImageView.getBoundsInLocal().getHeight();
                dip.selectSimilarColors(event.getX() * ratioX, event.getY() * ratioY);
            }
        });
        updateStatusBar();
    }

    // ── Sidebar navigation ────────────────────────────────────────────────────

    @FXML public void showDashboard()        { switchPane("dashboard"); }
    @FXML public void showGallery()          { switchPane("gallery"); }
    @FXML public void showDipEditor()        { switchPane("dipEditor"); }
    @FXML public void showAnnotation()       { switchPane("annotation"); }
    @FXML public void showFavorites()        { switchPane("favorites"); }
    @FXML public void showObjectExtraction() { switchPane("objectExtraction"); }
    @FXML public void showMosaic()           { switchPane("mosaic"); }
    @FXML public void showVideoCreator()     { switchPane("videoCreator"); }
    @FXML public void showShare()            { switchPane("share"); }
    @FXML public void showSettings()         { switchPane("settings"); }

    @FXML public void handleSidebarToggle() {
        sidebarExpanded = !sidebarExpanded;
        ToggleButton[] allTabs = { tabDashboard, tabGallery, tabDipEditor, tabAnnotation,
                tabFavorites, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare };
        if (sidebarExpanded) {
            sidebarVBox.setPrefWidth(130); sidebarVBox.setMinWidth(130);
            for (int i = 0; i < allTabs.length; i++)
                allTabs[i].setText(SIDEBAR_LABELS[i][0] + "\n" + SIDEBAR_LABELS[i][1]);
        } else {
            sidebarVBox.setPrefWidth(52); sidebarVBox.setMinWidth(52);
            for (int i = 0; i < allTabs.length; i++)
                allTabs[i].setText(SIDEBAR_LABELS[i][0]);
        }
        // Re-apply active style for current selection
        for (ToggleButton t : allTabs) if (t.isSelected()) t.setStyle(buildActiveStyle());
    }

    private String buildActiveStyle() {
        String fs = sidebarExpanded ? "11" : "20";
        String bg = darkMode ? "#2A5080" : "#6FBAFF";
        String fg = darkMode ? "#E8E8FF" : "#4A4A4A";
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-background-radius: 8; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif; -fx-font-size: " + fs + "; -fx-padding: 8 4; -fx-alignment: CENTER;";
    }

    private String buildInactiveStyle() {
        String fs = sidebarExpanded ? "11" : "20";
        String fg = darkMode ? "#C0C8E8" : "#4A4A4A";
        return "-fx-background-color: transparent; -fx-text-fill: " + fg + "; -fx-background-radius: 8; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif; -fx-font-size: " + fs + "; -fx-padding: 8 4; -fx-alignment: CENTER;";
    }

    private void switchPane(String name) {
        if (!skipHistoryPush && !name.equals(currentPane)) {
            navHistory.push(currentPane);
        }
        currentPane = name;
        if (backBtn != null) backBtn.setDisable(navHistory.isEmpty());

        VBox[] allVBoxPanes = { dashboardPane, galleryPane, annotationPane, favoritesPane,
                objectExtractionPane, mosaicPane, videoCreatorPane, shareContentPane, settingsPane, userProfilePane };
        for (VBox p : allVBoxPanes) { p.setVisible(false); p.setManaged(false); }
        dipEditorPane.setVisible(false); dipEditorPane.setManaged(false);

        ToggleButton[] allTabs = { tabDashboard, tabGallery, tabDipEditor, tabAnnotation,
                tabFavorites, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare };
        String activeStyle   = buildActiveStyle();
        String inactiveStyle = buildInactiveStyle();
        for (ToggleButton t : allTabs) t.setStyle(inactiveStyle);

        switch (name) {
            case "dashboard"       -> { dashboardPane.setVisible(true);       dashboardPane.setManaged(true);       tabDashboard.setStyle(activeStyle); }
            case "gallery"         -> { galleryPane.setVisible(true);         galleryPane.setManaged(true);         tabGallery.setStyle(activeStyle); }
            case "dipEditor"       -> { dipEditorPane.setVisible(true);       dipEditorPane.setManaged(true);       tabDipEditor.setStyle(activeStyle); }
            case "annotation"      -> { annotationPane.setVisible(true);      annotationPane.setManaged(true);      tabAnnotation.setStyle(activeStyle); }
            case "favorites"       -> { favoritesPane.setVisible(true);       favoritesPane.setManaged(true);       tabFavorites.setStyle(activeStyle); }
            case "objectExtraction"-> { objectExtractionPane.setVisible(true);objectExtractionPane.setManaged(true);tabObjectExtraction.setStyle(activeStyle); }
            case "mosaic"          -> { mosaicPane.setVisible(true);          mosaicPane.setManaged(true);          tabMosaic.setStyle(activeStyle); }
            case "videoCreator"    -> { videoCreatorPane.setVisible(true);    videoCreatorPane.setManaged(true);    tabVideoCreator.setStyle(activeStyle); }
            case "share"           -> { shareContentPane.setVisible(true);    shareContentPane.setManaged(true);    tabShare.setStyle(activeStyle); }
            case "settings"        -> { settingsPane.setVisible(true);        settingsPane.setManaged(true); }
            case "userProfile"     -> { userProfilePane.setVisible(true);     userProfilePane.setManaged(true); }
        }
    }

    // ── Toolbar: File ─────────────────────────────────────────────────────────

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

    @FXML public void handleUploadImage() { handleOpenImage(); }

    @FXML public void handleSave() {
        if (DIPController.getInstance() != null) DIPController.getInstance().handleSaveAction();
    }

    @FXML public void handleExport() { handleSave(); }
    @FXML public void handleImport()  { handleOpenImage(); }

    // ── Toolbar: Edit ─────────────────────────────────────────────────────────

    @FXML public void handleUndo()   { /* placeholder */ }
    @FXML public void handleRedo()   { /* placeholder */ }

    @FXML public void handleDelete() {
        mainImageView.setImage(null);
        uploadPlaceholder.setVisible(true);
        imageScrollPane.setVisible(false);
        currentFileName = "No file";
        updateStatusBar();
    }

    // ── Toolbar: DIP Tools ────────────────────────────────────────────────────

    @FXML public void handleToolBrightness() { goToDip(); }
    @FXML public void handleToolContrast()   { goToDip(); }
    @FXML public void handleToolResize()     { goToDip(); }

    @FXML public void handleToolGrayscale() {
        goToDip();
        if (DIPController.getInstance() != null) DIPController.getInstance().handleGrayscale();
    }

    @FXML public void handleToolBorder() {
        goToDip();
        if (DIPController.getInstance() != null) DIPController.getInstance().handleBorderToggle();
    }

    @FXML public void handleToolRotate() {
        goToDip();
        if (DIPController.getInstance() != null) DIPController.getInstance().handleRotate();
    }

    @FXML public void handleToolTranslate() {
        goToDip();
        if (mainImageView.getImage() != null) {
            mainImageView.setTranslateX(mainImageView.getTranslateX() + 20);
        }
    }

    private void goToDip() {
        switchPane("dipEditor");
        tabDipEditor.setSelected(true);
    }

    // ── Toolbar: Quick Tools ──────────────────────────────────────────────────

    @FXML public void handleSearch() { /* placeholder */ }

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

    @FXML public void handleFullscreen() {
        if (mainImageView.getScene() != null) {
            Stage stage = (Stage) mainImageView.getScene().getWindow();
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    // ── Toolbar: Share ────────────────────────────────────────────────────────

    @FXML public void handleShareEmail()    { switchPane("share"); tabShare.setSelected(true); }
    @FXML public void handleShareWhatsApp() { switchPane("share"); tabShare.setSelected(true); }

    // ── Toolbar: Settings ─────────────────────────────────────────────────────

    @FXML public void handleToggleDarkMode() {
        darkMode = !darkMode;
        applyTheme();
    }

    private void applyTheme() {
        if (darkMode) {
            // Dark mode
            rootPane.setStyle("-fx-background-color: #1E1E2E; -fx-font-family: 'Poppins Regular', 'Poppins', 'Segoe UI', sans-serif;");
            toolbarHBox.setStyle("-fx-background-color: #2A2A3E; -fx-padding: 6 16; -fx-border-color: #3A3A5E; -fx-border-width: 0 0 1 0;");
            sidebarVBox.setStyle("-fx-background-color: #1A2A40; -fx-padding: 10 6; -fx-min-width: " + (sidebarExpanded ? 130 : 52) + "; -fx-pref-width: " + (sidebarExpanded ? 130 : 52) + "; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif;");
            statusBarHBox.setStyle("-fx-background-color: #2A2A3E; -fx-padding: 5 15; -fx-border-color: #3A3A5E; -fx-border-width: 1 0 0 0;");
            darkModeBtn.setText("\u2600  Settings");
            if (backBtn != null) backBtn.setStyle("-fx-background-color: #3A3A5E; -fx-text-fill: #C0C8E8; -fx-background-radius: 6; -fx-border-color: #3A3A5E; -fx-border-radius: 6; -fx-font-size: 13; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-padding: 6 14;");
        } else {
            // Light mode
            rootPane.setStyle("-fx-background-color: #FFFFFF; -fx-font-family: 'Poppins Regular', 'Poppins', 'Segoe UI', sans-serif;");
            toolbarHBox.setStyle("-fx-background-color: #BFDFFF; -fx-padding: 6 16; -fx-border-color: #D0E8FF; -fx-border-width: 0 0 1 0;");
            sidebarVBox.setStyle("-fx-background-color: #BFDFFF; -fx-padding: 10 6; -fx-min-width: " + (sidebarExpanded ? 130 : 52) + "; -fx-pref-width: " + (sidebarExpanded ? 130 : 52) + "; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif;");
            statusBarHBox.setStyle("-fx-background-color: #BFDFFF; -fx-padding: 5 15; -fx-border-color: #D0E8FF; -fx-border-width: 1 0 0 0;");
            darkModeBtn.setText("\u2699  Settings");
            if (backBtn != null) backBtn.setStyle("-fx-background-color: #A9D6FF; -fx-text-fill: #4A4A4A; -fx-background-radius: 6; -fx-border-color: #D0E8FF; -fx-border-radius: 6; -fx-font-size: 13; -fx-font-family: 'Poppins Medium', 'Poppins', 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-padding: 6 14;");
        }
        // Re-apply sidebar button styles with correct colours
        ToggleButton[] allTabs = { tabDashboard, tabGallery, tabDipEditor, tabAnnotation,
                tabFavorites, tabObjectExtraction, tabMosaic, tabVideoCreator, tabShare };
        String activeStyle   = buildActiveStyle();
        String inactiveStyle = buildInactiveStyle();
        for (ToggleButton t : allTabs) t.setStyle(t.isSelected() ? activeStyle : inactiveStyle);
    }

    @FXML public void handleUserProfile() { switchPane("userProfile"); }
    @FXML public void handleProfileBack()  { handleBack(); }
    @FXML public void handleSettings()    { switchPane("settings"); }

    @FXML public void handleBack() {
        if (!navHistory.isEmpty()) {
            skipHistoryPush = true;
            switchPane(navHistory.pop());
            skipHistoryPush = false;
        }
    }

    @FXML public void handleSaveProfile() {
        String name = profileNameField.getText().trim();
        if (name.isEmpty()) name = "User";
        profileDisplayName.setText(name);
        // Build initials from first letters of words (up to 2)
        String[] parts = name.split("\\s+");
        String initials = parts.length >= 2
                ? String.valueOf(parts[0].charAt(0)).toUpperCase() + String.valueOf(parts[1].charAt(0)).toUpperCase()
                : String.valueOf(parts[0].charAt(0)).toUpperCase();
        profileInitialsLabel.setText(initials);
        profileSaveStatus.setText("✓  Profile saved!");
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
        pause.setOnFinished(e -> profileSaveStatus.setText(""));
        pause.play();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadImage(File file) {
        Image image = new Image(file.toURI().toString());
        mainImageView.setImage(image);
        mainImageView.setPreserveRatio(true);
        mainImageView.fitWidthProperty().bind(imageScrollPane.widthProperty().subtract(20));
        mainImageView.fitHeightProperty().bind(imageScrollPane.heightProperty().subtract(20));
        uploadPlaceholder.setVisible(false);
        imageScrollPane.setVisible(true);
        currentFileName = file.getName();
        if (DIPController.getInstance() != null) DIPController.getInstance().onImageLoaded(image);
        updateStatusBar();
    }

    public void updateStatus(boolean isModified) {
        statusProcessing.setText(isModified ? "Processing…" : "Ready");
        statusProcessing.setStyle(isModified
                ? "-fx-text-fill: #FFB7C5; -fx-font-size: 11; -fx-font-weight: bold;"
                : "-fx-text-fill: #7A7A7A; -fx-font-size: 11; -fx-font-weight: bold;");
    }

    private void updateStatusBar() {
        statusFileName.setText("File: " + currentFileName);
        String res = (mainImageView.getImage() != null)
                ? (int) mainImageView.getImage().getWidth() + " × " + (int) mainImageView.getImage().getHeight() + " px"
                : "—";
        statusResolution.setText("Resolution: " + res);
        statusZoom.setText(String.format("Zoom: %.0f%%", zoomLevel * 100));
        statusProcessing.setText("Ready");
    }

    public ImageView getImageView() { return mainImageView; }
}
