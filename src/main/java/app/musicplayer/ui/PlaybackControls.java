package app.musicplayer.ui;

import app.musicplayer.model.Track;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.DoubleConsumer;

/** 底部播放控制栏，统一管理进度条、音量条和传输按钮的稳定尺寸。 */
public final class PlaybackControls extends VBox {
    private final Button playPauseButton = new Button("播放");
    private final Slider progressSlider = new Slider(0, 1, 0);
    private final Slider volumeSlider = new Slider(0, 1, 0.75);
    private final Label timeLabel = new Label("00:00 / 00:00");
    private boolean seeking;

    public PlaybackControls(
            ObservableList<Track> tracks,
            Runnable previousAction,
            Runnable playPauseAction,
            Runnable nextAction,
            Runnable seekAction,
            DoubleConsumer volumeAction,
            boolean mobileMode
    ) {
        super(8);

        Button previousButton = controlButton("上一首", previousAction);
        playPauseButton.getStyleClass().addAll("primary-button", "control-button", "play-button");
        playPauseButton.setOnAction(event -> playPauseAction.run());
        Button nextButton = controlButton("下一首", nextAction);

        progressSlider.getStyleClass().add("song-progress-slider");
        progressSlider.setMinWidth(240);
        progressSlider.setPrefWidth(620);
        progressSlider.setMaxWidth(760);
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
        HBox.setHgrow(progressSlider, Priority.SOMETIMES);

        HBox transportGroup = new HBox(10, previousButton, playPauseButton, nextButton);
        transportGroup.getStyleClass().add("transport-group");
        transportGroup.setAlignment(Pos.CENTER_LEFT);

        Label volumeLabel = new Label("音量");
        volumeLabel.getStyleClass().add("muted-label");
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setMinWidth(72);
        volumeSlider.setPrefWidth(96);
        volumeSlider.setMaxWidth(96);
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) ->
                volumeAction.accept(newValue.doubleValue()));
        enableDirectPointerInput(volumeSlider, false, null);

        HBox volumeGroup = new HBox(8, volumeLabel, volumeSlider);
        volumeGroup.getStyleClass().add("volume-group");
        volumeGroup.setAlignment(Pos.CENTER_LEFT);
        volumeGroup.setMinWidth(148);
        volumeGroup.setPrefWidth(156);
        volumeGroup.setMaxWidth(164);

        HBox controlRow = new HBox(
                14, transportGroup, new Separator(Orientation.VERTICAL), volumeGroup);
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
        }

        playPauseButton.disableProperty().bind(Bindings.isEmpty(tracks));
        previousButton.disableProperty().bind(Bindings.isEmpty(tracks));
        nextButton.disableProperty().bind(Bindings.isEmpty(tracks));
    }

    public Button playPauseButton() {
        return playPauseButton;
    }

    public Slider progressSlider() {
        return progressSlider;
    }

    public Slider volumeSlider() {
        return volumeSlider;
    }

    public Label timeLabel() {
        return timeLabel;
    }

    public boolean isSeeking() {
        return seeking;
    }

    private void enableDirectPointerInput(Slider slider, boolean marksSeeking, Runnable commitAction) {
        slider.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (marksSeeking) {
                seeking = true;
            }
            updateSliderFromPointer(slider, event);
            event.consume();
        });

        slider.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            updateSliderFromPointer(slider, event);
            event.consume();
        });

        slider.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
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
        slider.setValue(valueAtPosition(
                event.getSceneX(),
                trackStart,
                trackLength,
                slider.getMin(),
                slider.getMax()));
    }

    static double valueAtPosition(
            double pointerPosition,
            double trackStart,
            double trackLength,
            double minimum,
            double maximum
    ) {
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

    private Button controlButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("control-button");
        button.setOnAction(event -> action.run());
        return button;
    }
}
