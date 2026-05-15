module com.mp.wig3003groupproject {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires javafx.swing;
    requires javafx.media;

    opens com.mp.wig3003groupproject to javafx.fxml;
    exports com.mp.wig3003groupproject;
}