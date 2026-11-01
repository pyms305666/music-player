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
    /** 枚举 JavaFX 窄屏导航页：本地曲库、播放和在线。 */
    public enum Section {
        PLAYLIST,
        NOW_PLAYING,
        ONLINE
    }

    private final Map<Section, Node> views = new EnumMap<>(Section.class);
    private final Map<Section, ToggleButton> navigationButtons = new EnumMap<>(Section.class);
    private final HBox navigationBar = new HBox(6);
    private Section selectedSection;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
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

    /** 返回手机比例布局的页面导航栏。 */
    public HBox navigationBar() {
        return navigationBar;
    }

    /** 返回当前选中的手机比例页面。 */
    public Section selectedSection() {
        return selectedSection;
    }

    /** 切换选中项或页面，并更新导航反馈。 */
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

    /** 创建对应页面的导航按钮及选择处理。 */
    private ToggleButton navigationButton(String text, Section section, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        navigationButtons.put(section, button);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("mobile-nav-button");
        button.setOnAction(
                event -> {
                    select(section);
                    button.setSelected(true);
                });
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }
}
