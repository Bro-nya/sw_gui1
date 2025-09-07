module org.example.swgui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.prefs;


    opens org.example.swgui to javafx.fxml;
    exports org.example.swgui;
}