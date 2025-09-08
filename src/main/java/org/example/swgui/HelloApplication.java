package org.example.swgui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 显示密码验证对话框
        if (!showPasswordDialog()) {
            // 如果用户取消或密码错误，退出应用
            System.exit(0);
            return;
        }
        
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

    // 密码验证对话框
    private boolean showPasswordDialog() {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("请输入暗号");
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new javafx.geometry.Insets(20));
        
        Label label = new Label("我们是谁:");
        // 创建密码输入对话框
        Dialog<String> passwordDialog = new Dialog<>();
        passwordDialog.setTitle("请输入密码");
        passwordDialog.setHeaderText("欢迎使用绳网工具工具");
        passwordDialog.setResizable(false);

        // 设置对话框的按钮类型
        ButtonType loginButtonType = new ButtonType("确认", ButtonBar.ButtonData.OK_DONE);
        passwordDialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // 创建密码输入框
        // 将PasswordField改为TextField以明文显示
        TextField passwordField = new TextField();
        passwordField.setPromptText("输入密码");
        // 移除密码相关的特殊设置
        
        Button loginButton = new Button("确认");
        Button cancelButton = new Button("取消");
        
        loginButton.setOnAction(e -> {
            if ("绳网大酒店".equals(passwordField.getText())) {
                dialogStage.close();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("密码错误");
                alert.setHeaderText(null);
                alert.setContentText("请输入正确的密码");
                alert.showAndWait();
            }
        });
        
        cancelButton.setOnAction(e -> dialogStage.close());
        
        // 设置回车键也能登录
        passwordField.setOnAction(e -> loginButton.fire());
        
        // 设置布局
        vbox.getChildren().addAll(label, passwordField);
        
        // 创建按钮区域
        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().addAll(loginButton, cancelButton);
        
        vbox.getChildren().add(buttonBox);
        
        Scene dialogScene = new Scene(vbox, 300, 150);
        dialogStage.setScene(dialogScene);
        
        // 显示对话框并等待用户响应
        dialogStage.showAndWait();
        
        // 验证密码是否正确
        return "绳网大酒店".equals(passwordField.getText());
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