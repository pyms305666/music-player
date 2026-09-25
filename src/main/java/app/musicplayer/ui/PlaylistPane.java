package app.musicplayer.ui;

import app.musicplayer.model.Track;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;
import javafx.collections.transformation.FilteredList;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.prefs.Preferences;

/** 播放列表的视图组件，只处理控件和用户交互，不访问数据库或播放器。 */
public final class PlaylistPane extends VBox {
    private static final String PREF_SORT_TYPE = "playlist.sort.type";
    private static final String PREF_SORT_ORDER = "playlist.sort.order";

    private final TextField searchField = new TextField();
    private final ComboBox<PlaylistSort> sortTypeBox = new ComboBox<>();
    private final ComboBox<SortDirection> sortOrderBox = new ComboBox<>();
    private final ListView<Track> playlistView;
    private final boolean desktopMode;
    private Track currentTrack;

    public PlaylistPane(
            FilteredList<Track> tracks,
            Preferences preferences,
            Consumer<String> filterAction,
            Runnable sortAction,
            Consumer<Track> playAction,
            Runnable removeAction,
            boolean desktopMode
    ) {
        super(10);
        this.desktopMode = desktopMode;
        Label header = new Label(desktopMode ? "我的音乐" : "播放列表");
        header.getStyleClass().add("section-title");
        HBox headerRow = null;
        if (desktopMode) {
            Label countLabel = new Label();
            countLabel.getStyleClass().add("playlist-count");
            countLabel.textProperty().bind(Bindings.size(tracks).asString("%d 首"));
            headerRow = new HBox(8, header, countLabel);
            headerRow.getStyleClass().add("playlist-header");
            headerRow.setAlignment(Pos.CENTER_LEFT);
        }

        searchField.setPromptText("搜索歌曲、歌手或文件名");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterAction.accept(newValue));

        sortTypeBox.getItems().setAll(PlaylistSort.values());
        sortTypeBox.getSelectionModel().select(PlaylistSort.fromLabel(
                preferences.get(PREF_SORT_TYPE, PlaylistSort.TITLE.toString())));
        if (desktopMode) {
            sortTypeBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(PlaylistSort sort, boolean empty) {
                    super.updateItem(sort, empty);
                    setText(empty || sort == null ? null : switch (sort) {
                        case TITLE -> "歌名";
                        case ARTIST -> "歌手";
                        case FILE_NAME -> "文件名";
                        case CREATED_AT -> "创建日期";
                    });
                }
            });
        }
        sortTypeBox.setMaxWidth(Double.MAX_VALUE);
        sortTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                preferences.put(PREF_SORT_TYPE, newValue.toString());
                sortAction.run();
            }
        });

        sortOrderBox.getItems().setAll(SortDirection.values());
        sortOrderBox.getSelectionModel().select(SortDirection.fromLabel(
                preferences.get(PREF_SORT_ORDER, SortDirection.ASCENDING.toString())));
        sortOrderBox.setMaxWidth(Double.MAX_VALUE);
        sortOrderBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                preferences.put(PREF_SORT_ORDER, newValue.toString());
                sortAction.run();
            }
        });

        HBox sortRow;
        if (desktopMode) {
            Button directionButton = new Button(sortOrderBox.getValue() == SortDirection.ASCENDING ? "↑" : "↓");
            directionButton.getStyleClass().add("sort-direction-button");
            directionButton.setTooltip(new Tooltip("切换排序方向"));
            directionButton.setAccessibleText("排序方向：" + sortOrderBox.getValue());
            directionButton.setOnAction(event -> sortOrderBox.getSelectionModel().select(
                    sortOrderBox.getValue() == SortDirection.ASCENDING
                            ? SortDirection.DESCENDING : SortDirection.ASCENDING));
            sortOrderBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                directionButton.setText(newValue == SortDirection.DESCENDING ? "↓" : "↑");
                directionButton.setAccessibleText("排序方向：" + newValue);
            });
            sortRow = new HBox(8, sortTypeBox, directionButton);
        } else {
            sortRow = new HBox(8, sortTypeBox, sortOrderBox);
            HBox.setHgrow(sortOrderBox, Priority.ALWAYS);
        }
        sortRow.getStyleClass().add("sort-row");
        HBox.setHgrow(sortTypeBox, Priority.ALWAYS);

        playlistView = new ListView<>(tracks);
        if (desktopMode) playlistView.getStyleClass().add("playlist-view");
        playlistView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        if (desktopMode) playlistView.setCellFactory(ignored -> new ListCell<>() {
            private final Label playingMarker = new Label("▶");
            private final Label title = new Label();
            private final Label artist = new Label();
            private final VBox text = new VBox(3, title, artist);
            private final HBox row = new HBox(10, playingMarker, text);
            private final MenuItem removeItem = new MenuItem("从曲库移除");
            private final ContextMenu contextMenu = new ContextMenu(removeItem);

            {
                playingMarker.getStyleClass().add("playlist-playing-marker");
                title.getStyleClass().add("playlist-track-title");
                artist.getStyleClass().add("playlist-track-artist");
                title.setMaxWidth(Double.MAX_VALUE);
                artist.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(text, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
                removeItem.setOnAction(event -> {
                    if (getItem() != null) {
                        playlistView.getSelectionModel().select(getItem());
                        removeAction.run();
                    }
                });
            }

            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                getStyleClass().remove("currently-playing");
                if (empty || track == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }
                title.setText(track.title());
                artist.setText(track.artist());
                playingMarker.setVisible(track == currentTrack);
                if (track == currentTrack) getStyleClass().add("currently-playing");
                setText(null);
                setGraphic(row);
                setContextMenu(contextMenu);
            }
        });
        else playlistView.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                setText(empty || track == null ? null : track.toString());
            }
        });
        playlistView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Track selected = playlistView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    playAction.accept(selected);
                }
            }
        });
        if (desktopMode) playlistView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Track selected = playlistView.getSelectionModel().getSelectedItem();
                if (selected != null) playAction.accept(selected);
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                if (playlistView.getSelectionModel().getSelectedItem() != null) removeAction.run();
                event.consume();
            }
        });

        if (desktopMode) getChildren().setAll(headerRow, searchField, sortRow, playlistView);
        else getChildren().setAll(header, searchField, sortRow, playlistView);
        getStyleClass().add("sidebar");
        setPadding(new Insets(8, 0, 18, 22));
        setMinWidth(240);
        setPrefWidth(320);
        VBox.setVgrow(playlistView, Priority.ALWAYS);
    }

    public TextField searchField() {
        return searchField;
    }

    public ComboBox<PlaylistSort> sortTypeBox() {
        return sortTypeBox;
    }

    public ComboBox<SortDirection> sortOrderBox() {
        return sortOrderBox;
    }

    public ListView<Track> playlistView() {
        return playlistView;
    }

    public void setCurrentTrack(Track track) {
        if (!desktopMode) return;
        currentTrack = track;
        playlistView.refresh();
    }
}
