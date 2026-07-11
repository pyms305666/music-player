package app.musicplayer.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

/** 9:16 移动布局的歌单、歌词和在线搜索页面切换器。 */
public final class MobileViewSwitcher extends StackPane {
    public enum Section {
        PLAYLIST,
        NOW_PLAYING,
        ONLINE
    }

    private final Map<Section, Node> views = new EnumMap<>(Section.class);
    private final Map<Section, ToggleButton> navigationButtons = new EnumMap<>(Section.class);
    private final HBox navigationBar = new HBox(6);
    private Section selectedSection;

    public MobileViewSwitcher(Node playlist, Node nowPlaying, Node online) {
        views.put(Section.PLAYLIST, playlist);
        views.put(Section.NOW_PLAYING, nowPlaying);
        views.put(Section.ONLINE, online);

        ToggleGroup group = new ToggleGroup();
        ToggleButton playlistButton = navigationButton("歌单", Section.PLAYLIST, group);
        ToggleButton nowPlayingButton = navigationButton("歌词", Section.NOW_PLAYING, group);
        ToggleButton onlineButton = navigationButton("在线", Section.ONLINE, group);
        navigationBar.getChildren().setAll(playlistButton, nowPlayingButton, onlineButton);
        navigationBar.getStyleClass().add("mobile-navigation");
        navigationBar.setAlignment(Pos.CENTER);

        getStyleClass().add("mobile-view-switcher");
        setMinSize(0, 0);
        select(Section.NOW_PLAYING);
    }

    public HBox navigationBar() {
        return navigationBar;
    }

    public Section selectedSection() {
        return selectedSection;
    }

    public void select(Section section) {
        Node view = views.get(section);
        if (view == null || section == selectedSection) {
            return;
        }
        selectedSection = section;
        getChildren().setAll(view);
        ToggleButton navigationButton = navigationButtons.get(section);
        if (navigationButton != null) {
            navigationButton.setSelected(true);
        }
    }

    private ToggleButton navigationButton(String text, Section section, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        navigationButtons.put(section, button);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("mobile-nav-button");
        button.setOnAction(event -> {
            select(section);
            button.setSelected(true);
        });
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }
}
