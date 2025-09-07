package org.example.swgui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("绳网情报站工具箱(动图工具) V0.0");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void stop() throws Exception {
        // 应用退出时强制终止仍在运行的外部进程（ffmpeg/bat）
        try {
            MainController.killAllProcesses();
        } catch (Throwable ignored) {}
        super.stop();
    }
}