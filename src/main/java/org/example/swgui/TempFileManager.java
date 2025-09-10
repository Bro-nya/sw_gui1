package org.example.swgui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TempFileManager {
    private static final TempFileManager INSTANCE = new TempFileManager();
    private final List<Path> tempFiles = new CopyOnWriteArrayList<>();

    private TempFileManager() {
        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupAllTempFiles));
    }

    public static TempFileManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册临时文件
     */
    public void registerTempFile(Path path) {
        tempFiles.add(path);
        // 设置JVM退出时删除
        path.toFile().deleteOnExit();
    }

    /**
     * 清理所有临时文件
     */
    public void cleanupAllTempFiles() {
        for (Path path : tempFiles) {
            try {
                if (Files.exists(path)) {
                    Files.delete(path);
                    System.out.println("已删除临时文件: " + path);
                }
            } catch (IOException e) {
                System.err.println("无法删除临时文件: " + path + ", 错误: " + e.getMessage());
            }
        }
        tempFiles.clear();
    }
}