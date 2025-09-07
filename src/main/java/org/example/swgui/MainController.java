package org.example.swgui;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.geometry.Bounds;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Optional;

import java.util.prefs.Preferences;

public class MainController {

    @FXML private MediaView mediaView;
    @FXML private Slider timeSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label startLabel;
    @FXML private Label endLabel;
    @FXML private StackPane videoStack;

    @FXML private TextField inputDirField;
    @FXML private TextField outputDirField;
    // 移除目标目录与时间精确输入
    @FXML private TextField cropXField;
    @FXML private TextField cropYField;
    @FXML private TextField cropWField;
    @FXML private TextField cropHField;
    // 移除核心数设置
    @FXML private Spinner<Integer> widthSpinner;
    @FXML private Spinner<Integer> qualitySpinner;
    @FXML private Spinner<Integer> compressSpinner;
    @FXML private Spinner<Integer> fpsSpinner;
    @FXML private CheckBox fadeCheck;
    @FXML private Spinner<Integer> fadeFramesSpinner;
    @FXML private Pane timeOverlay;
    @FXML private javafx.scene.layout.Region startMarker;
    @FXML private javafx.scene.layout.Region endMarker;

    private static final String PREF_OUTPUT_DIR = "output_directory";
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainController.class);
    private static final String PREF_MP4_INPUT_DIR = "mp4_input_directory";

    private MediaPlayer mediaPlayer;
    private Duration markStart = Duration.ZERO;
    private Duration markEnd = Duration.ZERO;

    private final DoubleProperty cropX = new SimpleDoubleProperty(0);
    private final DoubleProperty cropY = new SimpleDoubleProperty(0);
    private final DoubleProperty cropW = new SimpleDoubleProperty(0);
    private final DoubleProperty cropH = new SimpleDoubleProperty(0);

    private final DecimalFormat timeFmt = new DecimalFormat("0"); // reserved for future formatting
    private static final java.util.List<Process> CHILD_PROCESSES = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void killAllProcesses() {
        for (Process p : CHILD_PROCESSES) {
            try { p.destroyForcibly(); } catch (Throwable ignore) { try { p.destroy(); } catch (Throwable ignored) {} }
        }
        CHILD_PROCESSES.clear();
    }
    // 固定裁剪宽高比 5:3（以宽为基准）
    private static final int CROP_RATIO_W = 5;
    private static final int CROP_RATIO_H = 3;
    private static final double CROP_RATIO = (double) CROP_RATIO_H / (double) CROP_RATIO_W;

    @FXML
    public void initialize() {
        // 恢复上次使用的MP4输入目录
        String lastMp4InputDir = PREFS.get(PREF_MP4_INPUT_DIR, "");
        if (!lastMp4InputDir.isEmpty() && new File(lastMp4InputDir).isDirectory()) {
            inputDirField.setText(lastMp4InputDir);
        }
// 添加MP4输入目录变化的监听器
        inputDirField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty() && new File(newVal).isDirectory()) {
                PREFS.put(PREF_MP4_INPUT_DIR, newVal);
            }
        });
        // 恢复上次使用的输出目录
        String lastOutputDir = PREFS.get(PREF_OUTPUT_DIR, "");
        if (!lastOutputDir.isEmpty() && new File(lastOutputDir).isDirectory()) {
            outputDirField.setText(lastOutputDir);
        }
// 添加监听器保存输出目录变化
        outputDirField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty() && new File(newVal).isDirectory()) {
                PREFS.put(PREF_OUTPUT_DIR, newVal);
            }
        });

        widthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(64, 4096, 500, 2));
        qualitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 80));
        compressSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 6, 4));
        fpsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 240, 30));
        fadeFramesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 120, 20));

        volumeSlider.setMin(0);
        volumeSlider.setMax(1);
        volumeSlider.setValue(0.8);

        timeSlider.setMin(0);
        timeSlider.setMax(100);
        timeSlider.setValue(0);

        currentTimeLabel.setText("0:00");

        setupCropOverlay();
        bindMediaViewToContainer();
        bindFieldsToState();
        setupTimeMarkers();
    }

    private void setupCropOverlay() {
        // Lightweight overlay using a resizable selection via mouse drag
        Pane overlay = new Pane();
        overlay.setMouseTransparent(false);
        overlay.setPickOnBounds(true);
        overlay.setStyle("-fx-border-color: transparent;");

        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle();
        rect.getStrokeDashArray().addAll(6.0, 6.0);
        rect.setStrokeWidth(2);
        rect.setStroke(javafx.scene.paint.Color.LIME);
        rect.setFill(javafx.scene.paint.Color.color(0, 1, 0, 0.1));

        rect.xProperty().bind(cropX);
        rect.yProperty().bind(cropY);
        rect.widthProperty().bind(cropW);
        rect.heightProperty().bind(cropH);

        overlay.getChildren().add(rect);

        final DoubleProperty dragStartX = new SimpleDoubleProperty();
        final DoubleProperty dragStartY = new SimpleDoubleProperty();

        overlay.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            dragStartX.set(e.getX());
            dragStartY.set(e.getY());
            cropX.set(e.getX());
            cropY.set(e.getY());
            cropW.set(0);
            cropH.set(0);
        });
        overlay.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            double startX = dragStartX.get();
            double startY = dragStartY.get();
            double dx = e.getX() - startX;
            double width = Math.abs(dx);
            double height = width * CROP_RATIO; // 以宽为基准按 5:3 求高

            // 根据上下方向确定 y（向上拖则 y 在上方）
            boolean draggingDown = e.getY() >= startY;
            double x = dx >= 0 ? startX : (startX - width);
            double y = draggingDown ? startY : (startY - height);

            cropX.set(x);
            cropY.set(y);
            cropW.set(width);
            cropH.set(height);
        });
        overlay.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            updateCropFieldsFromState();
            int[] box = computeCropBoxFromOverlay();
            if (box != null) {
                if (cropXField != null) cropXField.setText(String.valueOf(box[0]));
                if (cropYField != null) cropYField.setText(String.valueOf(box[1]));
                if (cropWField != null) cropWField.setText(String.valueOf(box[2]));
                if (cropHField != null) cropHField.setText(String.valueOf(box[3]));
            }
        });

        videoStack.getChildren().add(overlay);
        StackPane.setAlignment(overlay, javafx.geometry.Pos.CENTER);
        overlay.maxWidthProperty().bind(videoStack.widthProperty());
        overlay.maxHeightProperty().bind(videoStack.heightProperty());
        overlay.prefWidthProperty().bind(videoStack.widthProperty());
        overlay.prefHeightProperty().bind(videoStack.heightProperty());
    }

    private void bindFieldsToState() {
        // 初始化数值显示
        updateTimeFieldsFromState();
        updateCropFieldsFromState();
    }

    private void updateTimeFieldsFromState() { }

    private void updateCropFieldsFromState() {
        int[] box = computeCropBoxFromOverlay();
        if (box == null) return;
        if (cropXField != null) cropXField.setText(String.valueOf(box[0]));
        if (cropYField != null) cropYField.setText(String.valueOf(box[1]));
        if (cropWField != null) cropWField.setText(String.valueOf(box[2]));
        if (cropHField != null) cropHField.setText(String.valueOf(box[3]));
    }

    private int[] computeCropBoxFromOverlay() {
        if (mediaPlayer == null || mediaPlayer.getMedia() == null) return null;
        int srcW = mediaPlayer.getMedia().getWidth();
        int srcH = mediaPlayer.getMedia().getHeight();
        if (srcW <= 0 || srcH <= 0) return null;
        Bounds vb = mediaView.localToParent(mediaView.getBoundsInLocal());
        double offsetX = vb.getMinX();
        double offsetY = vb.getMinY();
        double displayedW = vb.getWidth();
        double displayedH = vb.getHeight();
        if (displayedW <= 0 || displayedH <= 0) return null;
        double scale = displayedW / srcW; // preserveRatio ensures same for H
        double selX = Math.max(0, cropX.get() - offsetX);
        double selY = Math.max(0, cropY.get() - offsetY);
        double selW = Math.max(0, Math.min(cropW.get(), displayedW - selX));
        double selH = Math.max(0, Math.min(cropH.get(), displayedH - selY));
        int px = (int)Math.round(selX / scale);
        int py = (int)Math.round(selY / scale);
        int pw = (int)Math.round(selW / scale);
        int ph = (int)Math.round(selH / scale);
        if (pw < 1) pw = 1;
        if (ph < 1) ph = 1;
        // clamp
        if (px + pw > srcW) pw = srcW - px;
        if (py + ph > srcH) ph = srcH - py;
        return new int[]{px, py, pw, ph};
    }

    @FXML
    public void onApplyTimeFields() { }

    @FXML
    public void onApplyCropFields() {
        if (mediaPlayer == null) return;
        try {
            int x = Integer.parseInt(cropXField.getText().trim());
            int y = Integer.parseInt(cropYField.getText().trim());
            int w = Integer.parseInt(cropWField.getText().trim());
            int h = (int)Math.round(w * CROP_RATIO); // 高度按 5:3 自适应

            double viewW = mediaView.getBoundsInParent().getWidth();
            double viewH = mediaView.getBoundsInParent().getHeight();
            int srcW = mediaPlayer.getMedia().getWidth();
            int srcH = mediaPlayer.getMedia().getHeight();
            if (srcW <= 0 || srcH <= 0 || viewW <= 0 || viewH <= 0) return;
            double scale = Math.min(viewW / srcW, viewH / srcH);
            double displayedW = srcW * scale;
            double displayedH = srcH * scale;
            double offsetX = (viewW - displayedW) / 2.0;
            double offsetY = (viewH - displayedH) / 2.0;

            // 将像素映射回视图坐标
            cropX.set(x * scale + offsetX);
            cropY.set(y * scale + offsetY);
            cropW.set(w * scale);
            cropH.set(h * scale);
        } catch (Exception ignore) { }
    }

    private void bindMediaViewToContainer() {
        // 让 MediaView 随容器缩放
        mediaView.fitWidthProperty().bind(videoStack.widthProperty());
        mediaView.fitHeightProperty().bind(videoStack.heightProperty());
        mediaView.setPreserveRatio(true);
    }

    @FXML
    public void onOpenVideo(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP4 Files", "*.mp4"));
        File f = fc.showOpenDialog(((Node)e.getSource()).getScene().getWindow());
        if (f == null) return;
        openMedia(f);
    }

    private void openMedia(File file) {
        disposePlayer();
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getMedia().getDuration();
            timeSlider.setMin(0);
            timeSlider.setMax(total.toMillis());
            markStart = Duration.ZERO;
            markEnd = total;
            startLabel.setText(formatTime(markStart));
            endLabel.setText(formatTime(markEnd));
            updateTimeFieldsFromState();
            updateCropFieldsFromState();
        });

        mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
            timeSlider.setValue(n.toMillis());
            currentTimeLabel.setText(formatTime(n));
        });
        timeSlider.valueChangingProperty().addListener((obs, was, is) -> {
            if (!is && mediaPlayer != null) {
                mediaPlayer.seek(Duration.millis(timeSlider.getValue()));
            }
        });
        timeSlider.valueProperty().addListener((obs, ov, nv) -> {
            if (!timeSlider.isValueChanging() && mediaPlayer != null) {
                Duration d = Duration.millis(nv.doubleValue());
                if (Math.abs(mediaPlayer.getCurrentTime().toMillis() - d.toMillis()) > 100) {
                    mediaPlayer.seek(d);
                }
            }
        });
        volumeSlider.valueProperty().addListener((obs, ov, nv) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(nv.doubleValue());
        });
        mediaPlayer.setVolume(volumeSlider.getValue());
        mediaPlayer.play();
        setupTimeMarkers();
    }

    private void setupTimeMarkers() {
        try {
            if (timeOverlay == null || startMarker == null || endMarker == null) return;
        } catch (Exception ignore) { return; }
        Runnable update = () -> {
            double min = timeSlider.getMin();
            double max = timeSlider.getMax();
            double w = timeOverlay.getWidth();
            if (max <= min || w <= 0) return;
            double sx = (markStart.toMillis() - min) / (max - min) * w;
            double ex = (markEnd.toMillis() - min) / (max - min) * w;
            startMarker.setLayoutX(Math.max(0, Math.min(w, sx)));
            endMarker.setLayoutX(Math.max(0, Math.min(w, ex)));
        };
        // 监听进度条范围与标记变化
        timeOverlay.widthProperty().addListener((o, a, b) -> update.run());
        timeSlider.minProperty().addListener((o, a, b) -> update.run());
        timeSlider.maxProperty().addListener((o, a, b) -> update.run());
        // 在设置开始/结束时更新
        // 已在 onMarkStart/onMarkEnd 中更新 label，这里也更新标记
        update.run();
    }

    private String formatTime(Duration d) {
        long ms = (long) d.toMillis();
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        long hr = m / 60;
        long min = m % 60;
        if (hr > 0) return String.format("%d:%02d:%02d", hr, min, sec);
        return String.format("%d:%02d", min, sec);
    }

    @FXML
    public void onTogglePlay() {
        if (mediaPlayer == null) return;
        MediaPlayer.Status st = mediaPlayer.getStatus();
        if (st == MediaPlayer.Status.PLAYING) mediaPlayer.pause(); else mediaPlayer.play();
    }

    @FXML
    public void onMarkStart() {
        if (mediaPlayer == null) return;
        markStart = mediaPlayer.getCurrentTime();
        startLabel.setText(formatTime(markStart));
        setupTimeMarkers();
    }

    @FXML
    public void onMarkEnd() {
        if (mediaPlayer == null) return;
        markEnd = mediaPlayer.getCurrentTime();
        endLabel.setText(formatTime(markEnd));
        setupTimeMarkers();
    }

    // 修改onBrowseInputDir方法以保存选择的MP4输入目录
    @FXML
    public void onBrowseInputDir(ActionEvent e) {
        browseDir(inputDirField, e);

        // 保存选择的MP4输入目录
        String selectedDir = inputDirField.getText();
        if (!selectedDir.isEmpty()) {
            PREFS.put(PREF_MP4_INPUT_DIR, selectedDir);
        }
    }
    @FXML
    public void onBrowseOutputDir(ActionEvent e) {
        browseDir(outputDirField, e);

        // 保存选择的输出目录
        String selectedDir = outputDirField.getText();
        if (!selectedDir.isEmpty()) {
            PREFS.put(PREF_OUTPUT_DIR, selectedDir);
        }
    }
    

    private void browseDir(TextField field, ActionEvent e) {
        DirectoryChooser dc = new DirectoryChooser();
        File dir = dc.showDialog(((Node)e.getSource()).getScene().getWindow());
        if (dir != null) field.setText(dir.getAbsolutePath());
    }

    @FXML
    public void onExportTrimCrop() {
        if (mediaPlayer == null) return;
        // 将导出结果直接保存到输入目录，便于 BAT 批处理
        String inputDir = Optional.ofNullable(inputDirField.getText()).orElse("");
        if (inputDir.isEmpty()) {
            showAlert("缺少输入目录", "请在设置中选择输入目录");
            return;
        }
        File out = new File(inputDir, "trimmed_" + System.currentTimeMillis() + ".mp4");

        // 将 Media 源 URI 转为本地文件路径，处理 %20 等转义与 file:/// 前缀
        String inputPath;
        String src = mediaPlayer.getMedia().getSource();
        try {
            URI uri = new URI(src);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                inputPath = Paths.get(uri).toFile().getAbsolutePath();
            } else {
                inputPath = URLDecoder.decode(src, StandardCharsets.UTF_8);
            }
        } catch (Exception ignore) {
            try {
                inputPath = Paths.get(new URL(src).toURI()).toFile().getAbsolutePath();
            } catch (Exception ignore2) {
                String s = src;
                if (s.startsWith("file:///")) s = s.substring(8);
                else if (s.startsWith("file:/")) s = s.substring(6);
                inputPath = URLDecoder.decode(s, StandardCharsets.UTF_8).replace('/', File.separatorChar);
            }
        }

        // 读取时间范围：优先使用输入框毫秒值，其次使用打点
        long totalMs = (long) mediaPlayer.getMedia().getDuration().toMillis();
        long startMs = (long) markStart.toMillis();
        long endMs = (long) markEnd.toMillis();
        // 精确输入已移除，仅用打点
        if (startMs < 0) startMs = 0;
        if (endMs > totalMs) endMs = totalMs;
        if (endMs <= startMs) {
            showAlert("时间范围无效", "结束时间必须大于开始时间");
            return;
        }
        // 导出前二次确认：范围超过10秒
        if ((endMs - startMs) > 10_000) {
            boolean proceed = confirmLongExport(endMs - startMs);
            if (!proceed) return;
        }
        long durationMs = endMs - startMs;
        // 同步显示
        markStart = Duration.millis(startMs);
        markEnd = Duration.millis(endMs);
        startLabel.setText(formatTime(markStart));
        endLabel.setText(formatTime(markEnd));

        // Compute crop area —— 仅以数值输入为准（拖拽在 Mouse Released 时已同步到数值）
        int srcW = mediaPlayer.getMedia().getWidth();
        int srcH = mediaPlayer.getMedia().getHeight();
        int cropXpx;
        int cropYpx;
        int cropWpx;
        int cropHpx;
        // 优先读字段；若字段无效则用拖拽换算
        try {
            int fx = Integer.parseInt(Optional.ofNullable(cropXField.getText()).orElse("0").trim());
            int fy = Integer.parseInt(Optional.ofNullable(cropYField.getText()).orElse("0").trim());
            int fw = Integer.parseInt(Optional.ofNullable(cropWField.getText()).orElse("0").trim());
            // 高度由宽度决定，维持 5:3
            int fh = (int)Math.round(fw * CROP_RATIO);
            cropXpx = Math.max(0, Math.min(fx, Math.max(0, srcW - 1)));
            cropYpx = Math.max(0, Math.min(fy, Math.max(0, srcH - 1)));
            cropWpx = Math.max(0, Math.min(fw, Math.max(0, srcW - cropXpx)));
            cropHpx = Math.max(0, Math.min(fh, Math.max(0, srcH - cropYpx)));
            if (cropWpx == 0 || cropHpx == 0) throw new IllegalArgumentException("empty");
        } catch (Exception ex) {
            int[] box = computeCropBoxFromOverlay();
            if (box == null) {
                cropXpx = 0; cropYpx = 0; cropWpx = srcW; cropHpx = srcH;
            } else {
                cropXpx = box[0]; cropYpx = box[1]; cropWpx = box[2]; cropHpx = box[3];
            }
        }

        // x264 要求宽高为偶数，做偶数化处理
        if ((cropWpx & 1) == 1) cropWpx -= 1;
        if ((cropHpx & 1) == 1) cropHpx -= 1;
        if (cropWpx <= 0 || cropHpx <= 0) {
            // 若未选择裁剪，默认不裁剪
            cropXpx = 0; cropYpx = 0; cropWpx = srcW; cropHpx = srcH;
            if ((cropWpx & 1) == 1) cropWpx -= 1;
            if ((cropHpx & 1) == 1) cropHpx -= 1;
        }

        String ffmpeg = "ffmpeg"; // 要求 ffmpeg 在 PATH
        String start = msToTimestamp(startMs);
        String duration = msToTimestamp(durationMs);
        String cropFilter = String.format("crop=%d:%d:%d:%d,setsar=1", cropWpx, cropHpx, cropXpx, cropYpx);
        // 计算渐入/渐出参数
        boolean enableFade = fadeCheck != null && fadeCheck.isSelected();
        String fadeFilters = "";
        if (enableFade) {
            int fps = Math.max(1, fpsSpinner.getValue());
            int fadeFrames = fadeFramesSpinner.getValue(); // 使用可配置的帧数
            // 渐入：从第0帧开始，持续fadeFrames；渐出：从末尾向前fadeFrames
            // 使用 t 表达式按时长近似：duration 秒 * fps ≈ 帧数
            double durSec = durationMs / 1000.0;
            fadeFilters = String.format(",fade=t=in:st=0:n=%d,fade=t=out:st=%f:n=%d",
                    fadeFrames,
                    Math.max(0.0, durSec - (fadeFrames / (double)fps)),
                    fadeFrames);
        }

        ProcessBuilder pb = new ProcessBuilder(ffmpeg,
                "-ss", start,
                "-i", inputPath,
                "-t", duration,
                "-vf", cropFilter + fadeFilters,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "18",
                "-y",
                out.getAbsolutePath());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            CHILD_PROCESSES.add(p);
            CHILD_PROCESSES.add(p);
            p.getInputStream().transferTo(System.out);
            int code = p.waitFor();
            CHILD_PROCESSES.remove(p);
            if (code != 0) {
                showAlert("FFmpeg 失败", "导出失败，代码: " + code);
            } else {
                if (oneClickAfterExport) {
                    oneClickAfterExport = false;
                    Platform.runLater(this::onRunBat);
                } else {
                    Platform.runLater(() -> {
                        Alert done = new Alert(Alert.AlertType.INFORMATION,
                                "已导出到:\n" + out.getAbsolutePath(), ButtonType.OK);
                        done.setTitle("导出完成");
                        done.showAndWait();
                    });
                }
            }
        } catch (Exception ex) {
            showAlert("FFmpeg 异常", ex.getMessage());
        }
    }

    private boolean confirmLongExport(long durationMs) {
        final boolean[] ret = {false};
        try {
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                        "选取视频较长！！！当前选取时长约 " + String.format("%.1f", durationMs/1000.0) + " 秒，不能导出！！！",
                        ButtonType.OK, ButtonType.CANCEL);
                a.setTitle("导出确认");
                a.showAndWait().ifPresent(bt -> ret[0] = (bt == ButtonType.OK));
            });
            // 简单等待 UI 线程处理
            int wait = 0;
            while (wait < 200 && !ret[0]) { Thread.sleep(10); wait++; }
        } catch (Exception ignore) {}
        return ret[0];
    }

    private String msToTimestamp(long ms) {
        long s = ms / 1000;
        long m = s / 60; long sec = s % 60;
        long h = m / 60; long min = m % 60;
        long msOnly = ms % 1000;
        return String.format("%02d:%02d:%02d.%03d", h, min, sec, msOnly);
    }

    @FXML
    public void onRunBat() {
        try {
            Path bat = generateBat();
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", bat.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            // 选择合适工作目录，避免相对路径或权限问题
            File workDir = null;
            if (outputDirField != null && outputDirField.getText() != null && !outputDirField.getText().trim().isEmpty()) {
                workDir = new File(outputDirField.getText().trim());
            } else if (inputDirField != null && inputDirField.getText() != null && !inputDirField.getText().trim().isEmpty()) {
                workDir = new File(inputDirField.getText().trim());
            }
            if (workDir != null && workDir.isDirectory()) {
                pb.directory(workDir);
            }
            Process p = pb.start();
            // 将输出显示到对话框
            TextArea ta = new TextArea();
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setPrefColumnCount(80);
            ta.setPrefRowCount(24);
            Alert out = new Alert(Alert.AlertType.INFORMATION);
            out.setTitle("执行转换中...");
            out.setHeaderText("正在执行，请等待完成...");
            out.getDialogPane().setContent(ta);

            Thread t = new Thread(() -> {
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        final String ln = line;
                        Platform.runLater(() -> {
                            ta.appendText(ln);
                            ta.appendText("\n");
                        });
                    }
                    int code = p.waitFor();
                    CHILD_PROCESSES.remove(p);
                    Platform.runLater(() -> out.setHeaderText(code == 0 ? "执行完成" : ("执行失败，代码 " + code)));
                } catch (Exception ignored) {}
            });
            t.setDaemon(true);
            t.start();

            out.show();
        } catch (IOException ex) {
            showAlert("运行失败", ex.getMessage());
        }
    }

    @FXML
    public void onOneClickConvert() {
        // 先导出裁剪MP4 → 完成后直接执行转换
        // 复用现有导出逻辑，但把导出完成后的询问改为自动启动
        if (mediaPlayer == null) return;
        // 设置一个标志，让导出成功后直接调用 onRunBat
        oneClickAfterExport = true;
        onExportTrimCrop();
    }

    private boolean oneClickAfterExport = false;

    private Path generateBat() throws IOException {
        String inputDir = Optional.ofNullable(inputDirField.getText()).orElse("");
        String outputDir = Optional.ofNullable(outputDirField.getText()).orElse("");
        int cores = 4;
        int width = widthSpinner.getValue();
        int quality = qualitySpinner.getValue();
        int compress = compressSpinner.getValue();
        int fps = fpsSpinner.getValue();

        String content = "@echo off\r\n" +
                "chcp 65001 >nul\r\n" +
                "setlocal enabledelayedexpansion\r\n" +
                "\r\n" +
                "set \"input_dir=" + inputDir + "\"\r\n" +
                "set \"output_dir=" + outputDir + "\"\r\n" +
                "set \"hx=" + cores + "\"\r\n" +
                "set \"input files suffix=mp4\"\r\n" +
                "set \"with=" + width + "\"\r\n" +
                "set \"quality=" + quality + "\"\r\n" +
                "set \"compress=" + compress + "\"\r\n" +
                "set \"FPS=" + fps + "\"\r\n" +
                "if not exist \"%output_dir%\" mkdir \"%output_dir%\"\r\n" +
                "for %%F in (\"%input_dir%\\*.%input files suffix%\") do (\r\n" +
                "echo 正在处理: %%~nxF\r\n" +
                "ffmpeg -hide_banner -threads %hx% -i \"%%F\" -c:v libwebp -loop 0 -vf \"scale=%with%:-1:flags=lanczos,fps=%FPS%\" -q:v %quality% -compression_level %compress% \"%output_dir%\\%%~nF.webp\" -y\r\n" +
                "rem 将webp重命名为gif\r\n" +
                "ren \"%output_dir%\\%%~nF.webp\" \"%%~nF.gif\"\r\n" +
                ")\r\n" +
                "echo GIF 生成完成。\r\n" +
                "explorer \"%output_dir%\"\r\n" +
                "exit /b 0\r\n";

        Path tmp = Files.createTempFile("swgif-", ".bat");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tmp.toFile()))) {
            bw.write(content);
        }
        return tmp;
    }



    private void disposePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle(title);
            a.showAndWait();
        });
    }
}


