package org.example.swgui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
       Scene scene = new Scene(fxmlLoader.load(), 1200, 700); // 增加高度以容纳所有控件
        stage.setTitle("绳网情报站工具箱(动图工具) V0.0");
        
        
        // 从类路径加载图标
        try {
            // 直接从类路径加载resources目录下的图标
            Image icon = new Image(HelloApplication.class.getResourceAsStream("icon.png"));
            if (icon.isError()) {
                System.out.println("图标加载错误");
            } else {
                stage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.out.println("无法加载图标: " + e.getMessage());
        }
        
        stage.setResizable(true); // 允许调整窗口大小
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