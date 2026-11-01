package app.musicplayer.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

/** 在桌面预览和移动窗口调整时保持场景内容为 9:16 竖屏比例。 */
public final class MobileWindowSizer {
    public static final double WIDTH_TO_HEIGHT_RATIO = 9.0 / 16.0;
    private static final double MIN_CONTENT_WIDTH = 360;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private MobileWindowSizer() {
        // 只提供窗口绑定工具，不需要创建对象。
    }

    /** 将控件或窗口监听绑定到对应数据源。 */
    public static void bind(Stage stage, Scene scene) {
        Platform.runLater(
                () -> {
                    double frameWidth = Math.max(0, stage.getWidth() - scene.getWidth());
                    double frameHeight = Math.max(0, stage.getHeight() - scene.getHeight());
                    AtomicBoolean adjusting = new AtomicBoolean();

                    stage.setMinWidth(MIN_CONTENT_WIDTH + frameWidth);
                    stage.setMinHeight(heightForWidth(MIN_CONTENT_WIDTH) + frameHeight);

                    stage.widthProperty()
                            .addListener(
                                    (observable, oldWidth, newWidth) -> {
                                        if (adjusting.get()) {
                                            return;
                                        }
                                        adjusting.set(true);
                                        try {
                                            double contentWidth =
                                                    Math.max(
                                                            MIN_CONTENT_WIDTH,
                                                            newWidth.doubleValue() - frameWidth);
                                            stage.setHeight(
                                                    heightForWidth(contentWidth) + frameHeight);
                                        } finally {
                                            adjusting.set(false);
                                        }
                                    });

                    stage.heightProperty()
                            .addListener(
                                    (observable, oldHeight, newHeight) -> {
                                        if (adjusting.get()) {
                                            return;
                                        }
                                        adjusting.set(true);
                                        try {
                                            double contentHeight =
                                                    Math.max(
                                                            heightForWidth(MIN_CONTENT_WIDTH),
                                                            newHeight.doubleValue() - frameHeight);
                                            stage.setWidth(
                                                    widthForHeight(contentHeight) + frameWidth);
                                        } finally {
                                            adjusting.set(false);
                                        }
                                    });

                    adjusting.set(true);
                    try {
                        stage.setHeight(heightForWidth(scene.getWidth()) + frameHeight);
                    } finally {
                        adjusting.set(false);
                    }
                });
    }

    /** 按约定宽高比例计算另一边尺寸。 */
    static double heightForWidth(double width) {
        return width / WIDTH_TO_HEIGHT_RATIO;
    }

    /** 按约定宽高比例计算另一边尺寸。 */
    static double widthForHeight(double height) {
        return height * WIDTH_TO_HEIGHT_RATIO;
    }
}
