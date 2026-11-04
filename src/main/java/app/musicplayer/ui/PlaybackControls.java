package app.musicplayer.ui;

import app.musicplayer.model.PlayMode;
import app.musicplayer.model.Track;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.DoubleConsumer;

/** 底部播放控制栏，统一管理进度条、音量条和传输按钮的稳定尺寸。 */
public final class PlaybackControls extends VBox {
    private final Button playPauseButton = new Button("▶");
    private final Slider progressSlider = new Slider(0, 1, 0);
    private final Slider volumeSlider = new Slider(0, 1, 0.75);
    private final Label timeLabel = new Label("00:00 / 00:00");
    private final Label trackTitleLabel = new Label("未播放歌曲");
    private final Label trackArtistLabel = new Label("选择歌曲开始播放");
    private final ImageView trackThumbView = new ImageView();
    private final Label trackThumbPlaceholder = new Label("♫");
    private final StackPane trackThumbBox = new StackPane(trackThumbPlaceholder, trackThumbView);
    private boolean seeking;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaybackControls(
            ObservableList<Track> tracks,
            Runnable previousAction,
            Runnable playPauseAction,
            Runnable nextAction,
            Runnable seekAction,
            DoubleConsumer volumeAction,
            boolean mobileMode,
            ComboBox<PlayMode> playModeBox) {
        super(8);

        Button previousButton = controlButton("⏮", previousAction, "上一首");
        playPauseButton.getStyleClass().addAll("primary-button", "control-button", "play-button");
        playPauseButton.setTooltip(new Tooltip("播放 / 暂停"));
        playPauseButton.setOnAction(event -> playPauseAction.run());
        Button nextButton = controlButton("⏭", nextAction, "下一首");

        trackThumbView.setFitWidth(38);
        trackThumbView.setFitHeight(38);
        trackThumbView.setPreserveRatio(true);
        trackThumbView.setVisible(false);
        javafx.scene.shape.Rectangle thumbClip = new javafx.scene.shape.Rectangle(38, 38);
        thumbClip.setArcWidth(12);
        thumbClip.setArcHeight(12);
        trackThumbView.setClip(thumbClip);
        trackThumbPlaceholder.getStyleClass().add("player-track-thumb-icon");
        trackThumbBox.getStyleClass().add("player-track-thumb");
        trackThumbBox.setMinSize(38, 38);
        trackThumbBox.setPrefSize(38, 38);
        trackThumbBox.setMaxSize(38, 38);

        progressSlider.getStyleClass().add("song-progress-slider");
        progressSlider.setMinWidth(240);
        progressSlider.setPrefWidth(620);
        progressSlider.setMaxWidth(mobileMode ? 760 : Double.MAX_VALUE);
        progressSlider.setMinHeight(28);
        progressSlider.setPrefHeight(28);
        progressSlider.setMaxHeight(28);
        enableDirectPointerInput(progressSlider, true, seekAction);

        timeLabel.getStyleClass().add("time-label");
        timeLabel.setMinWidth(112);
        timeLabel.setPrefWidth(112);
        timeLabel.setMaxWidth(112);
        timeLabel.setMinHeight(28);
        timeLabel.setPrefHeight(28);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        Region progressSpacer = new Region();
        HBox.setHgrow(progressSpacer, Priority.ALWAYS);
        HBox progressRow = new HBox(12, progressSlider, timeLabel, progressSpacer);
        progressRow.getStyleClass().add("progress-row");
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        HBox transportGroup = new HBox(12, previousButton, playPauseButton, nextButton);
        transportGroup.getStyleClass().add("transport-group");
        transportGroup.setAlignment(Pos.CENTER);

        Label volumeLabel = new Label("🔊");
        volumeLabel.getStyleClass().add("volume-icon");
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setMinWidth(72);
        volumeSlider.setPrefWidth(96);
        volumeSlider.setMaxWidth(96);
        volumeSlider
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                volumeAction.accept(newValue.doubleValue()));
        enableDirectPointerInput(volumeSlider, false, null);

        HBox volumeGroup = new HBox(8, volumeLabel, volumeSlider);
        volumeGroup.getStyleClass().add("volume-group");
        volumeGroup.setAlignment(Pos.CENTER_LEFT);
        volumeGroup.setMinWidth(148);
        volumeGroup.setPrefWidth(156);
        volumeGroup.setMaxWidth(164);

        HBox controlRow =
                new HBox(14, transportGroup, new Separator(Orientation.VERTICAL), volumeGroup);
        controlRow.getStyleClass().add("control-row");
        controlRow.setAlignment(Pos.CENTER_LEFT);
        controlRow.setMinHeight(56);
        controlRow.setPrefHeight(56);
        controlRow.setMaxHeight(56);

        getChildren().setAll(progressRow, controlRow);
        getStyleClass().add("controls");
        setPadding(new Insets(10, 22, 14, 22));
        setMinHeight(118);
        setPrefHeight(124);
        setMaxHeight(132);

        if (mobileMode) {
            getStyleClass().add("mobile-controls");
            progressSlider.setMinWidth(0);
            progressSlider.setPrefWidth(260);
            progressSlider.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(progressSlider, Priority.ALWAYS);
            timeLabel.setMinWidth(92);
            timeLabel.setPrefWidth(92);
            timeLabel.setMaxWidth(92);
            progressSpacer.setVisible(false);
            progressSpacer.setManaged(false);
            volumeSlider.setMinWidth(58);
            volumeSlider.setPrefWidth(68);
            volumeSlider.setMaxWidth(68);
            volumeGroup.setMinWidth(104);
            volumeGroup.setPrefWidth(112);
            volumeGroup.setMaxWidth(120);
            Region controlSpacer = new Region();
            HBox.setHgrow(controlSpacer, Priority.ALWAYS);
            controlRow.getChildren().setAll(transportGroup, controlSpacer, volumeGroup);
            setPadding(new Insets(8, 12, 8, 12));
            setMinHeight(100);
            setPrefHeight(106);
            setMaxHeight(112);
        } else {
            progressSpacer.setVisible(false);
            progressSpacer.setManaged(false);
            trackTitleLabel.getStyleClass().add("player-track-title");
            trackArtistLabel.getStyleClass().add("player-track-artist");
            HBox trackInfo =
                    new HBox(10, trackThumbBox, new VBox(2, trackTitleLabel, trackArtistLabel));
            trackInfo.getStyleClass().add("player-track-info");
            trackInfo.setAlignment(Pos.CENTER_LEFT);
            trackInfo.setMinWidth(190);
            trackInfo.setPrefWidth(240);
            trackInfo.setMaxWidth(300);
            Region leadingSpacer = new Region();
            Region trailingSpacer = new Region();
            HBox.setHgrow(leadingSpacer, Priority.ALWAYS);
            HBox.setHgrow(trailingSpacer, Priority.ALWAYS);
            playModeBox.getStyleClass().add("desktop-play-mode");
            controlRow
                    .getChildren()
                    .setAll(
                            trackInfo,
                            leadingSpacer,
                            transportGroup,
                            trailingSpacer,
                            playModeBox,
                            volumeGroup);
            controlRow.setMinHeight(52);
            controlRow.setPrefHeight(52);
            controlRow.setMaxHeight(52);
            setPadding(new Insets(8, 24, 10, 24));
            setMinHeight(100);
            setPrefHeight(100);
            setMaxHeight(100);
        }

        playPauseButton.disableProperty().bind(Bindings.isEmpty(tracks));
        previousButton.disableProperty().bind(Bindings.isEmpty(tracks));
        nextButton.disableProperty().bind(Bindings.isEmpty(tracks));
    }

    /** 返回播放控件的对应子控件，供主界面连接事件。 */
    public Button playPauseButton() {
        return playPauseButton;
    }

    /** 返回播放控件的对应子控件，供主界面连接事件。 */
    public Slider progressSlider() {
        return progressSlider;
    }

    /** 返回播放控件的对应子控件，供主界面连接事件。 */
    public Slider volumeSlider() {
        return volumeSlider;
    }

    /** 返回播放控件的对应子控件，供主界面连接事件。 */
    public Label timeLabel() {
        return timeLabel;
    }

    /** 更新控制栏显示的歌名与歌手。 */
    public void setTrackInfo(String title, String artist) {
        trackTitleLabel.setText(title == null || title.isBlank() ? "未播放歌曲" : title);
        trackArtistLabel.setText(artist == null || artist.isBlank() ? "选择歌曲开始播放" : artist);
        if (title == null || title.isBlank()) {
            setArtwork(null);
        }
    }

    /** 更新控制栏缩略图。 */
    public void setArtwork(Image image) {
        if (image != null) {
            trackThumbView.setImage(image);
            trackThumbView.setVisible(true);
            trackThumbPlaceholder.setVisible(false);
        } else {
            trackThumbView.setImage(null);
            trackThumbView.setVisible(false);
            trackThumbPlaceholder.setVisible(true);
        }
    }

    /** 根据实际播放状态切换播放或暂停按钮外观。 */
    public void setPlaying(boolean playing) {
        playPauseButton.setText(playing ? "⏸" : "▶");
    }

    /** 检查用户是否正在拖动进度，避免自动刷新覆盖手动操作。 */
    public boolean isSeeking() {
        return seeking;
    }

    /** 让点击轨道可直接定位滑块，不必先拖动滑块手柄。 */
    private void enableDirectPointerInput(
            Slider slider, boolean marksSeeking, Runnable commitAction) {
        slider.addEventFilter(
                MouseEvent.MOUSE_PRESSED,
                event -> {
                    if (event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    if (marksSeeking) {
                        seeking = true;
                    }
                    updateSliderFromPointer(slider, event);
                    event.consume();
                });

        slider.addEventFilter(
                MouseEvent.MOUSE_DRAGGED,
                event -> {
                    if (!event.isPrimaryButtonDown()) {
                        return;
                    }
                    updateSliderFromPointer(slider, event);
                    event.consume();
                });

        slider.addEventFilter(
                MouseEvent.MOUSE_RELEASED,
                event -> {
                    if (event.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    updateSliderFromPointer(slider, event);
                    if (marksSeeking) {
                        seeking = false;
                    }
                    if (commitAction != null) {
                        commitAction.run();
                    }
                    event.consume();
                });
    }

    /** 将指针位置映射到滑块范围并更新对应值。 */
    private static void updateSliderFromPointer(Slider slider, MouseEvent event) {
        Node visibleTrack = slider.lookup(".track");
        Bounds trackBounds;
        double trackStart;
        double trackLength;
        if (visibleTrack == null) {
            trackBounds = slider.localToScene(slider.getLayoutBounds());
            trackStart = trackBounds.getMinX();
            trackLength = trackBounds.getWidth();
        } else {
            trackBounds = visibleTrack.localToScene(visibleTrack.getLayoutBounds());
            double capRadius = trackBounds.getHeight() / 2.0;
            trackStart = trackBounds.getMinX() + capRadius;
            trackLength = Math.max(0, trackBounds.getWidth() - capRadius * 2.0);
        }
        slider.setValue(
                valueAtPosition(
                        event.getSceneX(),
                        trackStart,
                        trackLength,
                        slider.getMin(),
                        slider.getMax()));
    }

    /** 将指针位置映射到滑块范围并更新对应值。 */
    static double valueAtPosition(
            double pointerPosition,
            double trackStart,
            double trackLength,
            double minimum,
            double maximum) {
        if (!Double.isFinite(pointerPosition)
                || !Double.isFinite(trackStart)
                || !Double.isFinite(trackLength)
                || trackLength <= 0
                || maximum <= minimum) {
            return minimum;
        }
        double fraction = Math.max(0, Math.min(1, (pointerPosition - trackStart) / trackLength));
        return minimum + fraction * (maximum - minimum);
    }

    /** 创建统一外观及提示的播放控制按钮。 */
    private Button controlButton(String text, Runnable action, String tooltipText) {
        Button button = new Button(text);
        button.getStyleClass().add("control-button");
        if (tooltipText != null) {
            button.setTooltip(new Tooltip(tooltipText));
        }
        button.setOnAction(event -> action.run());
        return button;
    }
}
