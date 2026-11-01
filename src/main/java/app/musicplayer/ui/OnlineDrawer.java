package app.musicplayer.ui;

import app.musicplayer.model.OnlineTrackInfo;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** 可折叠在线搜索抽屉，并负责保存三栏分隔位置。 */
public final class OnlineDrawer extends HBox {
    private static final String PREF_EXPANDED = "ui.online.panel.expanded";
    private static final String PREF_DIVIDER_LEFT = "ui.split.divider.left";
    private static final String PREF_DIVIDER_RIGHT = "ui.split.divider.right";
    private static final double DEFAULT_LEFT_DIVIDER = 0.23;
    private static final double DEFAULT_RIGHT_DIVIDER = 0.72;
    private static final double COLLAPSED_WIDTH = 0;
    private static final double EXPANDED_MIN_WIDTH = 320;
    private static final double EXPANDED_PREF_WIDTH = 360;
    private static final double EXPANDED_MAX_WIDTH = 440;

    private final Preferences preferences;
    private final TextField searchField = new TextField();
    private final ListView<OnlineTrackInfo> resultsView;
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();
    private final VBox content;
    private final Button closeButton = new Button("关闭");
    private final Button searchButton = new Button("搜索");
    private final Button mobileDownloadButton = new Button("下载并播放");
    private final Label hintLabel = new Label("单击预览 · 双击下载到本地播放");
    private final Label placeholderLabel = new Label("搜索 酷狗 / 酷我 / 咪咕 / QQ / 网易云，双击下载到本地");

    private SplitPane splitPane;
    private boolean expanded;
    private boolean syncingDivider;
    private Consumer<Boolean> expandedChanged;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineDrawer(
            ObservableList<OnlineTrackInfo> results,
            Preferences preferences,
            Runnable searchAction,
            Consumer<OnlineTrackInfo> previewAction,
            Consumer<OnlineTrackInfo> downloadAction) {
        this.preferences = preferences;
        this.expanded = preferences.getBoolean(PREF_EXPANDED, false);

        Label header = new Label("在线下载");
        header.getStyleClass().add("section-title");
        closeButton.getStyleClass().add("online-close-button");
        closeButton.setOnAction(event -> toggle());
        HBox headerRow = new HBox(header);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        headerRow.getChildren().addAll(headerSpacer, closeButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("搜索歌曲名 / 歌手名");
        searchField.getStyleClass().add("search-field");
        searchField.setOnAction(event -> searchAction.run());
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
        resultsView.setPlaceholder(placeholderLabel);
        resultsView.setCellFactory(ignored -> new OnlineResultCell(false));
        resultsView
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (newValue != null) {
                                previewAction.accept(newValue);
                            }
                        });
        resultsView.setOnMouseClicked(
                event -> {
                    if (event.getClickCount() == 2) {
                        OnlineTrackInfo selected =
                                resultsView.getSelectionModel().getSelectedItem();
                        if (selected != null) {
                            downloadAction.accept(selected);
                        }
                    }
                });

        hintLabel.setText("选择结果可预览，点击按钮下载并播放");
        hintLabel.getStyleClass().add("muted-label");
        hintLabel.setWrapText(true);
        mobileDownloadButton.getStyleClass().add("primary-button");
        mobileDownloadButton.setMaxWidth(Double.MAX_VALUE);
        mobileDownloadButton
                .disableProperty()
                .bind(resultsView.getSelectionModel().selectedItemProperty().isNull());
        mobileDownloadButton.setOnAction(
                event -> {
                    OnlineTrackInfo selected = resultsView.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        downloadAction.accept(selected);
                    }
                });

        content = new VBox(10, headerRow, searchRow, hintLabel, resultsView, mobileDownloadButton);
        content.getStyleClass().add("online-panel-content");
        content.setPadding(new Insets(12, 16, 16, 12));
        content.setFillWidth(true);
        HBox.setHgrow(content, Priority.ALWAYS);
        VBox.setVgrow(resultsView, Priority.ALWAYS);

        getChildren().setAll(content);
        getStyleClass().add("online-panel");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(COLLAPSED_WIDTH);
        setPrefWidth(EXPANDED_PREF_WIDTH);
        setMaxWidth(EXPANDED_MAX_WIDTH);
        applyState(false);
    }

    /** 把抽屉或组件接到指定布局和窗口。 */
    public void attach(SplitPane splitPane) {
        this.splitPane = splitPane;
        if (splitPane.getDividers().size() >= 2) {
            splitPane
                    .getDividers()
                    .get(0)
                    .positionProperty()
                    .addListener((observable, oldValue, newValue) -> persistDividers());
            splitPane
                    .getDividers()
                    .get(1)
                    .positionProperty()
                    .addListener((observable, oldValue, newValue) -> persistDividers());
        }
    }

    /** 恢复已保存的数据或界面状态，并应用当前可用性检查。 */
    public void restore(double windowWidth) {
        if (!hasDividers()) {
            return;
        }
        double left =
                clamp(preferences.getDouble(PREF_DIVIDER_LEFT, DEFAULT_LEFT_DIVIDER), 0.19, 0.36);
        double right =
                expanded
                        ? expandedDividerPosition(windowWidth, left)
                        : collapsedDividerPosition(windowWidth, left);
        setDividerPositions(left, right);
        applyState(false);
    }

    /** 根据窗口或设备的可用尺寸调整布局或封面大小。 */
    public void applyResponsiveLayout(double windowWidth) {
        if (!expanded) {
            syncDivider(windowWidth);
        }
    }

    /** 返回该面板的对应控件，让外层连接业务事件。 */
    public TextField searchField() {
        return searchField;
    }

    /** 返回该面板的对应控件，让外层连接业务事件。 */
    public ListView<OnlineTrackInfo> resultsView() {
        return resultsView;
    }

    /** 返回该面板的对应控件，让外层连接业务事件。 */
    public ProgressIndicator loadingIndicator() {
        return loadingIndicator;
    }

    /** 注册搜索和取消操作，保持按钮与键盘入口一致。 */
    public void setSearchActions(Runnable submit, Runnable toggle) {
        searchField.setOnAction(event -> submit.run());
        searchButton.setOnAction(event -> toggle.run());
    }

    /** 将搜索、下载或任务提示状态同步到在线面板。 */
    public void setSearchState(boolean searching, boolean retry) {
        searchButton.setText(searching ? "取消搜索" : retry ? "重试搜索" : "搜索");
        loadingIndicator.setVisible(searching);
        loadingIndicator.setManaged(searching);
    }

    /** 将搜索、下载或任务提示状态同步到在线面板。 */
    public void setDownloadState(String label, boolean disabled) {
        mobileDownloadButton.disableProperty().unbind();
        mobileDownloadButton.setText(label);
        mobileDownloadButton.setDisable(disabled);
    }

    /** 将搜索、下载或任务提示状态同步到在线面板。 */
    public void setTaskHint(String message) {
        hintLabel.setText(message);
    }

    /** 返回在线抽屉当前是否展开。 */
    public boolean isExpanded() {
        return expanded;
    }

    /** 从主界面标题栏切换在线抽屉的展开状态。 */
    public void toggleFromHeader() {
        toggle();
    }

    /** 注册在线抽屉展开状态变化的回调。 */
    public void setOnExpandedChanged(Consumer<Boolean> callback) {
        expandedChanged = callback;
    }

    /** 启用在线面板的手机比例显示方式。 */
    public void enableMobileMode() {
        expanded = true;
        Label mobileHeader = new Label("在线下载");
        mobileHeader.getStyleClass().add("section-title");
        content.getChildren().set(0, mobileHeader);
        resultsView.setCellFactory(ignored -> new OnlineResultCell(true));
        mobileDownloadButton.disableProperty().unbind();
        closeButton.setVisible(false);
        closeButton.setManaged(false);
        content.setVisible(true);
        content.setManaged(true);
        content.setOpacity(1.0);
        content.setTranslateX(0);
        content.setMinWidth(0);
        content.setPrefWidth(USE_COMPUTED_SIZE);
        content.setMaxWidth(Double.MAX_VALUE);
        mobileDownloadButton.setVisible(true);
        mobileDownloadButton.setManaged(true);
        hintLabel.setText("选择结果后点击“下载并播放”");
        placeholderLabel.setText("搜索后选择要下载的歌曲");
        setMinWidth(0);
        setPrefWidth(USE_COMPUTED_SIZE);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().removeAll("online-panel-expanded", "online-panel-collapsed");
        getStyleClass().add("mobile-online-panel");
    }

    /** 切换组件的打开或选中状态。 */
    private void toggle() {
        if (expanded && hasDividers()) {
            double[] positions = splitPane.getDividerPositions();
            preferences.putDouble(PREF_DIVIDER_LEFT, positions[0]);
            preferences.putDouble(PREF_DIVIDER_RIGHT, positions[1]);
        }
        expanded = !expanded;
        applyState(true);
        if (expandedChanged != null) expandedChanged.accept(expanded);
    }

    /** 将保存或计算出的状态应用到实际控件。 */
    private void applyState(boolean persist) {
        if (persist) {
            preferences.putBoolean(PREF_EXPANDED, expanded);
        }
        content.setVisible(expanded);
        content.setManaged(expanded);
        content.setPrefWidth(expanded ? EXPANDED_PREF_WIDTH : 0);
        content.setMinWidth(expanded ? 240 : 0);
        content.setMaxWidth(expanded ? Double.MAX_VALUE : 0);
        content.setOpacity(expanded ? 1.0 : 0.0);

        setMinWidth(expanded ? EXPANDED_MIN_WIDTH : COLLAPSED_WIDTH);
        setPrefWidth(expanded ? EXPANDED_PREF_WIDTH : COLLAPSED_WIDTH);
        setMaxWidth(expanded ? EXPANDED_MAX_WIDTH : COLLAPSED_WIDTH);
        getStyleClass().removeAll("online-panel-expanded", "online-panel-collapsed");
        getStyleClass().add(expanded ? "online-panel-expanded" : "online-panel-collapsed");
        syncDivider(currentWindowWidth());
    }

    /** 记录分隔条位置，供下次恢复布局。 */
    private void persistDividers() {
        if (syncingDivider || !hasDividers()) {
            return;
        }
        double[] positions = splitPane.getDividerPositions();
        double left = clamp(positions[0], 0.19, 0.36);
        preferences.putDouble(PREF_DIVIDER_LEFT, left);
        if (expanded) {
            preferences.putDouble(PREF_DIVIDER_RIGHT, clamp(positions[1], left + 0.18, 0.90));
        }
    }

    /** 将在线抽屉展开状态与分隔条位置同步。 */
    private void syncDivider(double windowWidth) {
        if (!hasDividers()) {
            return;
        }
        double left = clamp(splitPane.getDividerPositions()[0], 0.19, 0.36);
        double right =
                expanded
                        ? expandedDividerPosition(windowWidth, left)
                        : collapsedDividerPosition(windowWidth, left);
        if (Math.abs(splitPane.getDividerPositions()[1] - right) > 0.002) {
            setDividerPositions(left, right);
        }
    }

    /** 将在线抽屉展开状态与分隔条位置同步。 */
    private void setDividerPositions(double left, double right) {
        syncingDivider = true;
        splitPane.setDividerPositions(left, right);
        Platform.runLater(
                () -> {
                    if (splitPane != null) {
                        splitPane.setDividerPositions(left, right);
                    }
                    syncingDivider = false;
                });
    }

    /** 计算在线抽屉展开或收起时应使用的分隔位置。 */
    private double expandedDividerPosition(double windowWidth, double left) {
        double width = Math.max(1120, windowWidth);
        double preferred = preferences.getDouble(PREF_DIVIDER_RIGHT, DEFAULT_RIGHT_DIVIDER);
        double maximum = 1.0 - EXPANDED_PREF_WIDTH / width;
        return clamp(Math.min(preferred, maximum), left + 0.18, maximum);
    }

    /** 计算在线抽屉展开或收起时应使用的分隔位置。 */
    private double collapsedDividerPosition(double windowWidth, double left) {
        double width = Math.max(1120, windowWidth);
        return 1.0;
    }

    /** 取得当前窗口可用宽度，供响应式布局选择。 */
    private double currentWindowWidth() {
        return getScene() == null ? 1320 : getScene().getWidth();
    }

    /** 检查当前布局是否具备可调整的分隔条。 */
    private boolean hasDividers() {
        return splitPane != null && splitPane.getDividers().size() >= 2;
    }

    /** 将数值限制在指定上下界之间。 */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 复用 JavaFX 在线结果行控件，显示来源、标题及下载可用状态。 */
    private static final class OnlineResultCell extends ListCell<OnlineTrackInfo> {
        private final boolean mobileMode;
        private final Label title = new Label();
        private final Label details = new Label();
        private final Label availability = new Label();
        private final VBox text = new VBox(4, title, details);
        private final HBox row = new HBox(8, text, availability);

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        private OnlineResultCell(boolean mobileMode) {
            this.mobileMode = mobileMode;
            title.getStyleClass().add("online-track-title");
            details.getStyleClass().add("online-track-details");
            availability.getStyleClass().add("online-availability");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        /** 在 JavaFX 复用列表行时绑定当前对象或清空旧内容。 */
        @Override
        protected void updateItem(OnlineTrackInfo item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass()
                    .removeAll("result-downloadable", "result-tryable", "result-unavailable");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (mobileMode) {
                setText(item.title() + "\n" + item.subtitle());
                setWrapText(true);
                setGraphic(null);
            } else {
                title.setText(item.title());
                details.setText(
                        (item.artist() == null || item.artist().isBlank() ? "未知歌手" : item.artist())
                                + " · "
                                + item.source());
                availability.setText(item.availabilityText());
                setText(null);
                setGraphic(row);
            }
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
