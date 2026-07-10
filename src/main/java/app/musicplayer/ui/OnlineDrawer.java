package app.musicplayer.ui;

import app.musicplayer.model.OnlineTrackInfo;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** 可折叠在线搜索抽屉，并负责保存三栏分隔位置。 */
public final class OnlineDrawer extends HBox {
    private static final String PREF_EXPANDED = "ui.online.panel.expanded";
    private static final String PREF_DIVIDER_LEFT = "ui.split.divider.left";
    private static final String PREF_DIVIDER_RIGHT = "ui.split.divider.right";
    private static final double DEFAULT_LEFT_DIVIDER = 0.24;
    private static final double DEFAULT_RIGHT_DIVIDER = 0.78;
    private static final double COLLAPSED_WIDTH = 66;
    private static final double EXPANDED_MIN_WIDTH = 320;
    private static final double EXPANDED_PREF_WIDTH = 360;
    private static final double EXPANDED_MAX_WIDTH = 520;

    private final Preferences preferences;
    private final TextField searchField = new TextField();
    private final ListView<OnlineTrackInfo> resultsView;
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();
    private final VBox content;
    private final Button toggleButton = new Button();

    private SplitPane splitPane;
    private boolean expanded;
    private boolean syncingDivider;

    public OnlineDrawer(
            ObservableList<OnlineTrackInfo> results,
            Preferences preferences,
            Runnable searchAction,
            Consumer<OnlineTrackInfo> previewAction,
            Consumer<OnlineTrackInfo> downloadAction
    ) {
        this.preferences = preferences;
        this.expanded = preferences.getBoolean(PREF_EXPANDED, false);

        Label header = new Label("在线下载");
        header.getStyleClass().add("section-title");

        searchField.setPromptText("搜索歌曲名 / 歌手名");
        searchField.getStyleClass().add("search-field");
        searchField.setOnAction(event -> searchAction.run());
        Button searchButton = new Button("搜索");
        searchButton.getStyleClass().add("primary-button");
        searchButton.setOnAction(event -> searchAction.run());

        loadingIndicator.setMaxSize(18, 18);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);
        HBox searchRow = new HBox(8, searchField, searchButton, loadingIndicator);
        searchRow.getStyleClass().add("search-row");
        searchRow.setMinWidth(0);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        resultsView = new ListView<>(results);
        resultsView.getStyleClass().add("online-results-view");
        resultsView.setPlaceholder(new Label("搜索 QQMP3 / 网易云 / QQ / 酷狗，双击下载到本地"));
        resultsView.setCellFactory(ignored -> new OnlineResultCell());
        resultsView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        previewAction.accept(newValue);
                    }
                });
        resultsView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                OnlineTrackInfo selected = resultsView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    downloadAction.accept(selected);
                }
            }
        });

        Label hint = new Label("单击预览 · 双击下载到本地播放");
        hint.getStyleClass().add("muted-label");
        content = new VBox(10, header, searchRow, hint, resultsView);
        content.getStyleClass().add("online-panel-content");
        content.setPadding(new Insets(12, 16, 16, 12));
        content.setFillWidth(true);
        HBox.setHgrow(content, Priority.ALWAYS);
        VBox.setVgrow(resultsView, Priority.ALWAYS);

        toggleButton.getStyleClass().add("drawer-toggle-button");
        toggleButton.setWrapText(true);
        toggleButton.setFocusTraversable(false);
        toggleButton.setOnAction(event -> toggle());

        getChildren().setAll(toggleButton, content);
        getStyleClass().add("online-panel");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(COLLAPSED_WIDTH);
        setPrefWidth(EXPANDED_PREF_WIDTH);
        setMaxWidth(EXPANDED_MAX_WIDTH);
        applyState(false);
    }

    public void attach(SplitPane splitPane) {
        this.splitPane = splitPane;
        if (splitPane.getDividers().size() >= 2) {
            splitPane.getDividers().get(0).positionProperty().addListener(
                    (observable, oldValue, newValue) -> persistDividers());
            splitPane.getDividers().get(1).positionProperty().addListener(
                    (observable, oldValue, newValue) -> persistDividers());
        }
    }

    public void restore(double windowWidth) {
        if (!hasDividers()) {
            return;
        }
        double left = clamp(preferences.getDouble(PREF_DIVIDER_LEFT, DEFAULT_LEFT_DIVIDER), 0.16, 0.52);
        double right = expanded
                ? expandedDividerPosition(windowWidth, left)
                : collapsedDividerPosition(windowWidth, left);
        setDividerPositions(left, right);
        applyState(false);
    }

    public void applyResponsiveLayout(double windowWidth) {
        if (!expanded) {
            syncDivider(windowWidth);
        }
    }

    public TextField searchField() {
        return searchField;
    }

    public ListView<OnlineTrackInfo> resultsView() {
        return resultsView;
    }

    public ProgressIndicator loadingIndicator() {
        return loadingIndicator;
    }

    private void toggle() {
        if (expanded && hasDividers()) {
            double[] positions = splitPane.getDividerPositions();
            preferences.putDouble(PREF_DIVIDER_LEFT, positions[0]);
            preferences.putDouble(PREF_DIVIDER_RIGHT, positions[1]);
        }
        expanded = !expanded;
        animateState();
    }

    private void applyState(boolean persist) {
        if (persist) {
            preferences.putBoolean(PREF_EXPANDED, expanded);
        }
        content.setVisible(expanded);
        content.setManaged(expanded);
        content.setPrefWidth(expanded ? EXPANDED_PREF_WIDTH - COLLAPSED_WIDTH : 0);
        content.setMinWidth(expanded ? 240 : 0);
        content.setMaxWidth(expanded ? Double.MAX_VALUE : 0);
        content.setOpacity(expanded ? 1.0 : 0.0);

        setMinWidth(expanded ? EXPANDED_MIN_WIDTH : COLLAPSED_WIDTH);
        setPrefWidth(expanded ? EXPANDED_PREF_WIDTH : COLLAPSED_WIDTH);
        setMaxWidth(expanded ? Double.MAX_VALUE : COLLAPSED_WIDTH);
        getStyleClass().removeAll("online-panel-expanded", "online-panel-collapsed");
        getStyleClass().add(expanded ? "online-panel-expanded" : "online-panel-collapsed");
        updateToggleButton();
        syncDivider(currentWindowWidth());
    }

    private void animateState() {
        preferences.putBoolean(PREF_EXPANDED, expanded);
        ScaleTransition pulse = new ScaleTransition(Duration.millis(180), toggleButton);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);

        if (expanded) {
            content.setManaged(true);
            content.setVisible(true);
            content.setOpacity(0.0);
            content.setTranslateX(24);
            applyState(false);
            FadeTransition fade = new FadeTransition(Duration.millis(180), content);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), content);
            slide.setFromX(24);
            slide.setToX(0);
            new ParallelTransition(fade, slide, pulse).play();
        } else {
            updateToggleButton();
            FadeTransition fade = new FadeTransition(Duration.millis(150), content);
            fade.setFromValue(content.getOpacity());
            fade.setToValue(0.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(180), content);
            slide.setFromX(0);
            slide.setToX(24);
            ParallelTransition animation = new ParallelTransition(fade, slide, pulse);
            animation.setOnFinished(event -> applyState(false));
            animation.play();
        }
    }

    private void updateToggleButton() {
        toggleButton.setText(expanded ? "‹\n收起\n搜索" : "☰\n在线\n搜索\n›");
        toggleButton.setTooltip(new Tooltip(expanded ? "收起在线搜索抽屉" : "展开在线搜索抽屉"));
        toggleButton.getStyleClass().removeAll("drawer-expanded", "drawer-collapsed");
        toggleButton.getStyleClass().add(expanded ? "drawer-expanded" : "drawer-collapsed");
    }

    private void persistDividers() {
        if (syncingDivider || !hasDividers()) {
            return;
        }
        double[] positions = splitPane.getDividerPositions();
        double left = clamp(positions[0], 0.16, 0.52);
        preferences.putDouble(PREF_DIVIDER_LEFT, left);
        if (expanded) {
            preferences.putDouble(PREF_DIVIDER_RIGHT, clamp(positions[1], left + 0.18, 0.90));
        }
    }

    private void syncDivider(double windowWidth) {
        if (!hasDividers()) {
            return;
        }
        double left = clamp(splitPane.getDividerPositions()[0], 0.16, 0.52);
        double right = expanded
                ? expandedDividerPosition(windowWidth, left)
                : collapsedDividerPosition(windowWidth, left);
        if (Math.abs(splitPane.getDividerPositions()[1] - right) > 0.002) {
            setDividerPositions(left, right);
        }
    }

    private void setDividerPositions(double left, double right) {
        syncingDivider = true;
        splitPane.setDividerPositions(left, right);
        Platform.runLater(() -> {
            if (splitPane != null) {
                splitPane.setDividerPositions(left, right);
            }
            syncingDivider = false;
        });
    }

    private double expandedDividerPosition(double windowWidth, double left) {
        double width = Math.max(1120, windowWidth);
        double preferred = preferences.getDouble(PREF_DIVIDER_RIGHT, DEFAULT_RIGHT_DIVIDER);
        double maximum = 1.0 - EXPANDED_PREF_WIDTH / width;
        return clamp(Math.min(preferred, maximum), left + 0.18, maximum);
    }

    private double collapsedDividerPosition(double windowWidth, double left) {
        double width = Math.max(1120, windowWidth);
        return clamp(1.0 - COLLAPSED_WIDTH / width, left + 0.18, 0.97);
    }

    private double currentWindowWidth() {
        return getScene() == null ? 1320 : getScene().getWidth();
    }

    private boolean hasDividers() {
        return splitPane != null && splitPane.getDividers().size() >= 2;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class OnlineResultCell extends ListCell<OnlineTrackInfo> {
        @Override
        protected void updateItem(OnlineTrackInfo item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("result-downloadable", "result-tryable", "result-unavailable");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item.title() + "\n" + item.subtitle());
            setWrapText(true);
            if (item.downloadable()) {
                getStyleClass().add("result-downloadable");
            } else if ("可尝试下载".equals(item.availabilityText())) {
                getStyleClass().add("result-tryable");
            } else {
                getStyleClass().add("result-unavailable");
            }
        }
    }
}
