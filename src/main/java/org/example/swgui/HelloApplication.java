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

// 在文件顶部添加必要的导入
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // 显示暗号验证对话框
        if (!showPasswordDialog()) {
            // 如果用户取消或暗号错误，退出应用
            System.exit(0);
            return;
        }
        
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 700); // 增加高度以容纳所有控件
        stage.setTitle("绳网情报站工具箱 Vᗜ˰ᗜ");
        
        
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

    // 暗号验证对话框
    private boolean showPasswordDialog() {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("请输入暗号");
        
        VBox vbox = new VBox(10);
        vbox.setPadding(new javafx.geometry.Insets(20));
        
        Label label = new Label("我们是谁:");
        
        // 创建暗号输入框
        TextField passwordField = new TextField();
        passwordField.setPromptText("输入暗号");
        
        Button loginButton = new Button("确认");
        Button cancelButton = new Button("取消");
        
        loginButton.setOnAction(e -> {
            if (verifyPassword(passwordField.getText(), "绳网大酒店")) {
                dialogStage.close();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("暗号错误");
                alert.setHeaderText(null);
                alert.setContentText("请输入正确的暗号");
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
        
        // 添加图标
        try {
            Image icon = new Image(HelloApplication.class.getResourceAsStream("icon.png"));
            if (!icon.isError()) {
                dialogStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.out.println("无法加载对话框图标: " + e.getMessage());
        }
        
        // 显示对话框并等待用户响应
        dialogStage.showAndWait();
        
        // 验证暗号是否正确
        return verifyPassword(passwordField.getText(), "绳网大酒店");
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
    
    // 添加暗号加密和验证方法
    private boolean verifyPassword(String inputPassword, String expectedPassword) {
        try {
            // 为了演示，我们直接将输入暗号与预期暗号都进行哈希处理后比对
            // 在实际应用中，预期暗号的哈希值应该预先计算并存储
            String inputHash = hashPassword(inputPassword);
            String expectedHash = hashPassword(expectedPassword);
            return inputHash.equals(expectedHash);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private String hashPassword(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 使用PBKDF2算法进行暗号哈希
        String salt = "swgui_salt_2025";
        int iterations = 10000;
        int keyLength = 256;
        
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iterations, keyLength);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        
        // 将哈希值转换为Base64字符串便于存储和比较
        return Base64.getEncoder().encodeToString(hash);
    }
}