package app.musicplayer.ui;

import app.musicplayer.model.Track;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
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

    public PlaylistPane(
            FilteredList<Track> tracks,
            Preferences preferences,
            Consumer<String> filterAction,
            Runnable sortAction,
            Consumer<Track> playAction
    ) {
        super(10);
        Label header = new Label("播放列表");
        header.getStyleClass().add("section-title");

        searchField.setPromptText("搜索歌曲、歌手或文件名");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filterAction.accept(newValue));

        sortTypeBox.getItems().setAll(PlaylistSort.values());
        sortTypeBox.getSelectionModel().select(PlaylistSort.fromLabel(
                preferences.get(PREF_SORT_TYPE, PlaylistSort.TITLE.toString())));
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

        HBox sortRow = new HBox(8, sortTypeBox, sortOrderBox);
        sortRow.getStyleClass().add("sort-row");
        HBox.setHgrow(sortTypeBox, Priority.ALWAYS);
        HBox.setHgrow(sortOrderBox, Priority.ALWAYS);

        playlistView = new ListView<>(tracks);
        playlistView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        playlistView.setCellFactory(ignored -> new ListCell<>() {
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

        getChildren().setAll(header, searchField, sortRow, playlistView);
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
}
