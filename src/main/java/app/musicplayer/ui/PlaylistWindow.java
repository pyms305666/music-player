package app.musicplayer.ui;

import app.musicplayer.playlist.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Named playlist management and an explicit import/duplicate review flow. */
public final class PlaylistWindow {
    private final PlaylistWorkspace workspace;
    private final PlaylistFiles files;
    private final BiConsumer<List<String>,Boolean> play;
    private final Stage window=new Stage();
    private final ComboBox<NamedPlaylist> playlists=new ComboBox<>();
    private final TextField search=new TextField();
    private final ListView<NamedPlaylist.Entry> entries=new ListView<>();
    private final Label summary=new Label("尚无歌单，点击导入歌单开始");
    private final Label selectionSummary=new Label();
    private final Label operationStatus=new Label();
    private final Label emptyTitle=new Label();
    private final Label emptyDetail=new Label();
    private final Map<String,Button> controls=new HashMap<>();
    private final Set<String> selected=new HashSet<>();
    private final Consumer<String> downloadListener=id -> reload();
    private NamedPlaylist current;
    private Stage importPreview;
    private boolean refreshing;
    private boolean resolving;
    private long request, importRequest;
    private final String defaultDirectory;
    public PlaylistWindow(Stage owner,PlaylistWorkspace workspace,PlaylistFiles files,String defaultDirectory,
                          BiConsumer<List<String>,Boolean> play){
        this.workspace=workspace;this.files=files;this.defaultDirectory=defaultDirectory;this.play=play;window.initOwner(owner);window.setTitle("我的歌单");
        playlists.setPromptText("先导入一个歌单");playlists.setMaxWidth(Double.MAX_VALUE);playlists.setPrefHeight(42);playlists.setMaxHeight(42);
        playlists.setOnAction(e -> {if(!refreshing){current=playlists.getValue();selected.clear();entries.getSelectionModel().clearSelection();showEntries();}});
        search.setPromptText("搜索歌单中的歌名或歌手");search.getStyleClass().add("search-field");search.textProperty().addListener((o,a,b) -> showEntries());
        entries.setCellFactory(v -> checkCell(selected));entries.getStyleClass().add("playlist-entries");
        entries.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> updateControls());
        emptyTitle.getStyleClass().add("empty-title");emptyDetail.getStyleClass().add("muted-label");
        VBox empty=new VBox(10,emptyTitle,emptyDetail);empty.getStyleClass().add("playlist-empty");entries.setPlaceholder(empty);
        FlowPane actions=new FlowPane(8,8,control("导入歌单","粘贴酷狗、网易云或 QQ 音乐的分享链接",this::importLink),control("重命名","修改当前歌单名称",this::rename),
                control("下载目录","设置保存位置及是否检查子目录",this::directory),control("删除歌单","只删除歌单记录，保留音频文件",this::delete),
                control("全选","勾选当前搜索结果；清空搜索可全选歌单",() -> {entries.getItems().forEach(e -> selected.add(e.id()));entries.refresh();updateControls();}),
                control("取消选择","清除所有勾选，包括搜索隐藏的歌曲",() -> {selected.clear();entries.refresh();updateControls();}),
                control("上移","点击歌曲行，再将它在原歌单中上移",() -> move(-1)),control("下移","点击歌曲行，再将它在原歌单中下移",() -> move(1)));
        controls.get("导入歌单").getStyleClass().add("primary-button");controls.get("删除歌单").getStyleClass().add("danger-button");
        FlowPane downloads=new FlowPane(8,8,control("下载选中","下载勾选歌曲；重复文件会先询问",this::download),control("下载未完成","继续下载所有未完成、失败或取消的歌曲",this::downloadMissing),
                control("选择下载版本","只勾选一首，手动选择本软件的下载渠道与版本",this::chooseDownload),
                control("暂停 / 继续","暂停后，正在下载的歌曲会完成，后续歌曲等待",this::pause),control("取消下载","取消当前歌单的排队与下载任务，已完成文件保留",this::cancel),
                control("播放本地歌曲","按歌单顺序播放已下载歌曲",() -> playback(false)),control("加入播放队列","将本地歌曲加入当前队列，不自动播放",() -> playback(true)));
        controls.get("下载选中").getStyleClass().add("primary-button");
        Label title=new Label("我的歌单");title.getStyleClass().add("playlist-heading");
        Label subtitle=new Label("导入酷狗 · 网易云 · QQ 音乐歌单，使用本软件渠道下载");subtitle.getStyleClass().add("muted-label");
        summary.getStyleClass().add("muted-label");summary.setWrapText(true);summary.setMinHeight(Region.USE_PREF_SIZE);summary.setMaxWidth(Double.MAX_VALUE);
        selectionSummary.getStyleClass().add("muted-label");operationStatus.getStyleClass().add("playlist-operation");
        VBox footer=new VBox(8,selectionSummary,downloads);footer.getStyleClass().add("playlist-footer");
        VBox root=new VBox(12,title,subtitle,playlists,actions,search,summary,operationStatus,entries,footer);root.setPadding(new Insets(20));VBox.setVgrow(entries,Priority.ALWAYS);
        window.setScene(scene(root,940,720));window.setMinWidth(800);window.setMinHeight(560);showEntries();
        window.setOnShown(e -> {workspace.downloads.listen(downloadListener);workspace.refreshFiles(files).whenComplete((v,error) -> Platform.runLater(() -> {if(!window.isShowing())return;if(error!=null)fail(error);else reload();}));});
        window.setOnHidden(e -> {request++;importRequest++;resolving=false;operationStatus.setText("");workspace.downloads.unlisten(downloadListener);if(importPreview!=null)importPreview.close();});
    }
    public void show(){window.show();window.toFront();}
    private static Button button(String title,Runnable action){Button b=new Button(title);b.setOnAction(e -> action.run());return b;}
    private Button control(String title,String hint,Runnable action){Button b=button(title,action);b.setTooltip(new Tooltip(hint));controls.put(title,b);return b;}
    private static Scene scene(Pane root,double width,double height){root.getStyleClass().addAll("desktop-root","playlist-root");Scene scene=new Scene(root,width,height);styles(scene.getStylesheets());return scene;}
    private static void styles(List<String> target){for(String path:List.of("/styles.css","/styles-desktop.css","/styles-playlists.css"))target.add(PlaylistWindow.class.getResource(path).toExternalForm());}
    private static void theme(Dialog<?> dialog){dialog.initModality(Modality.WINDOW_MODAL);dialog.getDialogPane().getStyleClass().addAll("desktop-root","playlist-root","playlist-dialog");styles(dialog.getDialogPane().getStylesheets());}
    private ListCell<NamedPlaylist.Entry> checkCell(Set<String> selection){return new ListCell<>(){
        private final CheckBox check=new CheckBox();
        {check.setWrapText(true);check.setMaxWidth(Double.MAX_VALUE);check.setOnAction(e -> {if(getItem()!=null){if(check.isSelected())selection.add(getItem().id());else selection.remove(getItem().id());
            if(selection==selected){entries.getSelectionModel().select(getItem());updateControls();}}});}
        @Override protected void updateItem(NamedPlaylist.Entry item,boolean empty){super.updateItem(item,empty);if(empty||item==null){setGraphic(null);return;}
            check.setText(item.toString());check.setSelected(selection.contains(item.id()));check.setTooltip(new Tooltip(item.toString()+"\n"+item.location()));setGraphic(check);}
    };}
    private void reload(){long version=++request;workspace.list().whenComplete((values,error) -> Platform.runLater(() -> {
        if(version!=request||!window.isShowing())return;if(error!=null){fail(error);return;}
        String id=current==null?"":current.id();refreshing=true;playlists.getItems().setAll(values);
        current=values.stream().filter(p -> p.id().equals(id)).findFirst().orElse(values.isEmpty()?null:values.get(0));
        selected.retainAll(current==null?Set.of():current.entries().stream().map(NamedPlaylist.Entry::id).collect(java.util.stream.Collectors.toSet()));
        playlists.setValue(current);refreshing=false;showEntries();
    }));}
    private void showEntries(){
        var focused=entries.getSelectionModel().getSelectedItem();
        if(current==null){entries.getItems().clear();summary.setText("尚无歌单，点击导入歌单开始");emptyTitle.setText("还没有歌单");emptyDetail.setText("点击「导入歌单」，粘贴分享文字或链接即可开始");updateControls();return;}
        String query=search.getText().trim().toLowerCase(Locale.ROOT);
        entries.getItems().setAll(current.entries().stream().filter(e -> e.label().toLowerCase(Locale.ROOT).contains(query)).toList());
        if(focused!=null)entries.getItems().stream().filter(e -> e.id().equals(focused.id())).findFirst().ifPresent(e -> entries.getSelectionModel().select(e));
        summary.setText(current.progressLabel()+(workspace.downloads.paused(current.id())?" · 已暂停":"")+"\n下载目录："+current.directory());
        emptyTitle.setText(query.isEmpty()?"歌单中没有歌曲":"没有找到匹配歌曲");emptyDetail.setText(query.isEmpty()?"重新导入歌单以添加歌曲":"试试其他歌名或歌手，或清空搜索");updateControls();
    }
    private void updateControls(){
        boolean exists=current!=null,active=exists&&workspace.downloads.active(current.id()),editable=exists&&!active&&!resolving;
        playlists.setDisable(!exists);search.setDisable(!exists);controls.get("导入歌单").setDisable(resolving);
        for(String title:List.of("重命名","下载目录","删除歌单"))controls.get(title).setDisable(!editable);
        controls.get("全选").setDisable(entries.getItems().isEmpty()||entries.getItems().stream().allMatch(e -> selected.contains(e.id())));
        controls.get("取消选择").setDisable(selected.isEmpty());
        var focused=entries.getSelectionModel().getSelectedItem();int index=-1;
        if(exists&&focused!=null)for(int i=0;i<current.entries().size();i++)if(current.entries().get(i).id().equals(focused.id()))index=i;
        controls.get("上移").setDisable(!editable||index<=0);controls.get("下移").setDisable(!editable||index<0||index>=current.entries().size()-1);
        controls.get("下载选中").setDisable(!editable||selected.isEmpty());controls.get("下载未完成").setDisable(!editable||missingIds(current).isEmpty());
        controls.get("选择下载版本").setDisable(!editable||selected.size()!=1);controls.get("暂停 / 继续").setDisable(!active);controls.get("取消下载").setDisable(!active);
        controls.get("暂停 / 继续").setText(exists&&workspace.downloads.paused(current.id())?"继续下载":"暂停下载");
        boolean local=exists&&current.entries().stream().anyMatch(e -> !e.location().isBlank());
        controls.get("播放本地歌曲").setDisable(!local);controls.get("加入播放队列").setDisable(!local);
        selectionSummary.setText("已勾选 "+selected.size()+" 首"+(active?" · 下载期间可暂停或取消，取消后可编辑歌单":" · 点击歌曲行可调整顺序；选择下载版本需只勾选一首"));
        operationStatus.setManaged(!operationStatus.getText().isBlank());operationStatus.setVisible(operationStatus.isManaged());
    }
    private void importLink(){if(resolving)return;if(importPreview!=null&&importPreview.isShowing()){importPreview.toFront();return;}
        var dialog=new TextInputDialog();dialog.initOwner(window);dialog.setTitle("导入歌单");dialog.setHeaderText("粘贴酷狗、网易云或 QQ 音乐的歌单分享文字 / 链接");theme(dialog);
        dialog.getEditor().setPromptText("支持完整分享文字或 https:// 开头的歌单链接");dialog.getEditor().setPrefColumnCount(40);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(dialog.getEditor().textProperty().map(s -> s.isBlank()));
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(link -> {
            resolving=true;operationStatus.setText("正在读取歌单，请稍候…");updateControls();long version=++importRequest;
            workspace.read(link).whenComplete((playlist,error) -> Platform.runLater(() -> {if(version!=importRequest||!window.isShowing())return;resolving=false;operationStatus.setText("");updateControls();if(error!=null)fail(error);else preview(playlist);}));
        });
    }
    private void preview(NamedPlaylist playlist){
        Stage stage=new Stage();stage.initOwner(window);stage.initModality(Modality.WINDOW_MODAL);stage.setTitle("导入预览");
        importPreview=stage;stage.setOnHidden(event -> {if(importPreview==stage)importPreview=null;});
        Set<String> chosen=new HashSet<>();playlist.entries().forEach(e -> chosen.add(e.id()));
        ListView<NamedPlaylist.Entry> list=new ListView<>(FXCollections.observableArrayList(playlist.entries()));list.setCellFactory(v -> checkCell(chosen));
        TextField name=new TextField(playlist.name()), directory=new TextField(defaultDirectory);
        Button pick=button("选择目录",() -> {var chooser=new DirectoryChooser();var file=chooser.showDialog(stage);if(file!=null)directory.setText(file.getAbsolutePath());});
        CheckBox recursive=new CheckBox("检查子目录中的重复歌曲");CheckBox download=new CheckBox("导入后下载选中的歌曲");
        Label counts=new Label(playlist.source()+" · "+playlist.creator()+" · 已读取 "+playlist.entries().size()
                +(playlist.expectedCount()<0?" 首（平台未提供总数）":" / "+playlist.expectedCount()+" 首")
                +(!playlist.complete()?"\n请核对歌曲清单，平台可能未提供完整内容":"")+"\n下载使用本软件渠道，优先匹配酷我、咪咕");
        counts.setWrapText(true);counts.getStyleClass().add("muted-label");
        name.setPromptText("歌单名称");directory.setPromptText("下载目录");HBox.setHgrow(directory,Priority.ALWAYS);list.getStyleClass().add("playlist-entries");
        ImageView cover=new ImageView();cover.setFitWidth(64);cover.setFitHeight(64);cover.setPreserveRatio(true);
        if(!playlist.artworkUrl().isBlank())cover.setImage(new Image(playlist.artworkUrl(),64,64,true,true,true));
        FlowPane selection=new FlowPane(8,8,button("全选",() -> {playlist.entries().forEach(e -> chosen.add(e.id()));list.refresh();}),button("取消选择",() -> {chosen.clear();list.refresh();}));
        Button save=new Button("确认导入");save.getStyleClass().add("primary-button");save.setOnAction(event -> {
            if(name.getText().isBlank()||chosen.isEmpty()){fail(new IllegalArgumentException("请输入歌单名称并至少选择一首歌曲"));return;}
            if(directory.getText().isBlank()){fail(new IllegalArgumentException("请选择下载目录"));return;}
            try{Path.of(directory.getText().trim());}catch(RuntimeException e){fail(e);return;}
            var value=playlist.configured(playlist.id(),name.getText().trim(),directory.getText().trim(),recursive.isSelected())
                    .withEntries(playlist.entries().stream().filter(e -> chosen.contains(e.id())).toList());
            save.setDisable(true);
            review(value,result -> {stage.close();saveImported(result,download.isSelected());},() -> save.setDisable(false));
        });
        Label nameLabel=new Label("歌单名称"),directoryLabel=new Label("下载目录");nameLabel.getStyleClass().add("muted-label");directoryLabel.getStyleClass().add("muted-label");
        VBox root=new VBox(10,new HBox(10,cover,counts),nameLabel,name,directoryLabel,new HBox(8,directory,pick),recursive,selection,list,download,new HBox(8,save,button("取消",stage::close)));root.setPadding(new Insets(20));VBox.setVgrow(list,Priority.ALWAYS);
        stage.setScene(scene(root,740,680));stage.setMinWidth(620);stage.setMinHeight(560);stage.show();
    }
    private void review(NamedPlaylist playlist,Consumer<NamedPlaylist> accept,Runnable aborted){
        Window owner=dialogOwner();long version=++importRequest;resolving=true;operationStatus.setText("正在检查本地重复歌曲，请稍候…");updateControls();
        workspace.work(() -> new PlaylistDuplicates(files.scan(playlist.directory(),playlist.recursive())).match(playlist.entries()))
            .whenComplete((matches,error) -> Platform.runLater(() -> {
                if(version!=importRequest||!window.isShowing()){aborted.run();return;}
                resolving=false;operationStatus.setText("");updateControls();
                if(!owner.isShowing()){aborted.run();return;}if(error!=null){fail(error);aborted.run();return;}
                if(matches.isEmpty()){accept.accept(playlist);return;}
                Dialog<Boolean> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("发现重复歌曲");theme(dialog);
                dialog.setHeaderText("请选择使用本地歌曲还是重复下载；疑似重复需要你确认版本");
                Map<String,ComboBox<String>> choices=new HashMap<>();Map<String,PlaylistDuplicates.Match> byId=new HashMap<>();
                VBox rows=new VBox(10);for(var match:matches){ComboBox<String> choice=new ComboBox<>();choice.getItems().add("仍然下载，保留两份");
                    match.candidates().forEach(c -> choice.getItems().add("使用本地："+c.location()));choice.getSelectionModel().select(match.confirmed()?1:0);choice.setMaxWidth(Double.MAX_VALUE);
                    rows.getChildren().add(new VBox(4,new Label(match.entry()+" · "+(match.confirmed()?"已存在":"疑似重复")),choice));choices.put(match.entry().id(),choice);byId.put(match.entry().id(),match);}
                ScrollPane scroll=new ScrollPane(rows);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(320);scroll.setPrefViewportWidth(680);
                Button all=button("全部重新下载",() -> choices.values().forEach(c -> c.getSelectionModel().select(0)));
                dialog.getDialogPane().setContent(new VBox(10,all,scroll));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
                dialog.setResultConverter(type -> type==ButtonType.OK);
                if(dialog.showAndWait().orElse(false))accept.accept(playlist.withEntries(playlist.entries().stream().map(e -> {
                    ComboBox<String> choice=choices.get(e.id());int index=choice==null?0:choice.getSelectionModel().getSelectedIndex();
                    return index>0?e.reuseLocal(byId.get(e.id()).candidates().get(index-1).location()):e.pendingCopy(choice==null?NamedPlaylist.State.MISSING:NamedPlaylist.State.REDOWNLOAD);
                }).toList()));else aborted.run();
            }));
    }
    private void saveImported(NamedPlaylist playlist,boolean download){workspace.list().whenComplete((values,error) -> Platform.runLater(() -> {
        if(!window.isShowing())return;
        if(error!=null){fail(error);return;}var same=values.stream().filter(p -> p.source().equals(playlist.source())&&p.sourceId().equals(playlist.sourceId())).findFirst();
        String existing=null;if(same.isPresent()){ButtonType update=new ButtonType("更新已有歌单"),copy=new ButtonType("另存一份");
            var alert=new Alert(Alert.AlertType.CONFIRMATION,"已导入过此歌单。更新将采用这次选择的歌曲和原平台顺序，音频文件保留。",update,copy,ButtonType.CANCEL);alert.initOwner(window);alert.setTitle("已导入过此歌单");theme(alert);
            var choice=alert.showAndWait().orElse(ButtonType.CANCEL);if(choice==ButtonType.CANCEL)return;if(choice==update)existing=same.get().id();}
        workspace.save(playlist,existing).whenComplete((saved,failure) -> Platform.runLater(() -> {
            if(failure!=null){fail(failure);return;}current=saved;selected.clear();reload();
            if(download)workspace.work(() -> {workspace.downloads.start(saved,missingIds(saved));return null;}).exceptionally(e -> {Platform.runLater(() -> fail(e));return null;});
        }));
    }));}
    private static Set<String> missingIds(NamedPlaylist p){Set<String> ids=new HashSet<>();for(var e:p.entries())if(e.state()!=NamedPlaylist.State.READY)ids.add(e.id());return ids;}
    private void downloadMissing(){if(current==null)return;selected.clear();selected.addAll(missingIds(current));entries.refresh();updateControls();download();}
    private void download(){if(current==null||selected.isEmpty())return;NamedPlaylist p=current;
        reviewAndStart(p,p.withEntries(p.entries().stream().filter(e -> selected.contains(e.id())).map(NamedPlaylist.Entry::prepareDownload).toList()));
    }
    private void chooseDownload(){
        if(current==null||selected.size()!=1){fail(new IllegalArgumentException("请只勾选一首歌曲，再选择下载版本"));return;}
        NamedPlaylist p=current;var entry=p.entries().stream().filter(e -> selected.contains(e.id())).findFirst().orElse(null);
        if(entry==null){selected.clear();showEntries();fail(new IllegalArgumentException("所选歌曲已变化，请重新选择"));return;}
        resolving=true;operationStatus.setText("正在搜索本软件的下载渠道，请稍候…");updateControls();long version=++importRequest;
        workspace.candidates(entry).whenComplete((candidates,error) -> Platform.runLater(() -> {
            if(version!=importRequest||!window.isShowing())return;resolving=false;operationStatus.setText("");showEntries();
            if(current==null||!current.id().equals(p.id()))return;if(error!=null){fail(error);return;}
            if(candidates.isEmpty()){fail(new IllegalStateException("未找到可选择的歌曲，请更换关键词在在线搜索中检查"));return;}
            Dialog<app.musicplayer.model.OnlineTrackInfo> dialog=new Dialog<>();dialog.initOwner(window);dialog.setTitle("选择下载版本");dialog.setHeaderText("原歌单："+entry.label()+"\n请核对歌手、歌曲和版本后确认下载");theme(dialog);
            ListView<app.musicplayer.model.OnlineTrackInfo> list=new ListView<>(FXCollections.observableArrayList(candidates));list.setPrefSize(660,330);
            list.setCellFactory(v -> new ListCell<>(){@Override protected void updateItem(app.musicplayer.model.OnlineTrackInfo info,boolean empty){super.updateItem(info,empty);setText(empty||info==null?null:PlaylistSongMatcher.label(info));}});
            list.getSelectionModel().select(0);var confirm=new ButtonType("确认版本并下载",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().setContent(list);dialog.getDialogPane().getButtonTypes().addAll(confirm,ButtonType.CANCEL);
            dialog.setResultConverter(button -> button==confirm?list.getSelectionModel().getSelectedItem():null);
            dialog.showAndWait().ifPresent(info -> reviewAndStart(p,p.withEntries(List.of(entry.selectVersion(info)))));
        }));
    }
    private void reviewAndStart(NamedPlaylist p,NamedPlaylist picked){
        review(picked,result -> {Map<String,NamedPlaylist.Entry> chosen=new HashMap<>();result.entries().forEach(e -> chosen.put(e.id(),e));
            workspace.edit(p.id(),latest -> latest.withEntries(latest.entries().stream().map(e -> chosen.getOrDefault(e.id(),e)).toList()))
                .whenComplete((v,error) -> Platform.runLater(() -> {if(error!=null){fail(error);return;}reload();workspace.work(() -> {
                    var latest=workspace.store.findPlaylist(p.id()).orElseThrow();Set<String> ids=missingIds(result);workspace.downloads.start(latest,ids);return null;
                }).exceptionally(e -> {Platform.runLater(() -> fail(e));return null;});}));},() -> { });
    }
    private void pause(){if(current!=null){if(workspace.downloads.paused(current.id()))workspace.downloads.resume(current.id());else workspace.downloads.pause(current.id());}}
    private void cancel(){if(current!=null){String id=current.id();done(workspace.work(() -> {workspace.downloads.cancel(id);return null;}));}}
    private void rename(){if(current==null)return;var dialog=new TextInputDialog(current.name());dialog.initOwner(window);dialog.setTitle("重命名歌单");dialog.setHeaderText("歌单名称");theme(dialog);String id=current.id();
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(dialog.getEditor().textProperty().map(s -> s.isBlank()));
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(name -> done(workspace.edit(id,p -> p.configured(p.id(),name.trim(),p.directory(),p.recursive()))));}
    private void directory(){if(current==null)return;var p=current;Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(window);dialog.setTitle("歌单下载目录");dialog.setHeaderText("选择此歌单的保存位置");theme(dialog);
        TextField path=new TextField(p.directory());path.setPrefColumnCount(38);HBox.setHgrow(path,Priority.ALWAYS);path.setPromptText("输入目录路径，或点击浏览");
        CheckBox recursive=new CheckBox("检查子目录中的重复歌曲");recursive.setSelected(p.recursive());Label error=new Label();error.getStyleClass().add("playlist-error");error.setWrapText(true);
        Button browse=button("浏览…",() -> {var chooser=new DirectoryChooser();chooser.setTitle("选择歌单下载目录");var folder=chooser.showDialog(dialog.getDialogPane().getScene().getWindow());if(folder!=null)path.setText(folder.getAbsolutePath());});
        dialog.getDialogPane().setContent(new VBox(10,new HBox(8,path,browse),recursive,error));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        var confirm=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);confirm.disableProperty().bind(path.textProperty().map(String::isBlank));
        confirm.addEventFilter(javafx.event.ActionEvent.ACTION,event -> {try{Path target=Path.of(path.getText().trim());if(!target.isAbsolute())throw new IllegalArgumentException("请输入完整的目录路径");}
            catch(RuntimeException invalid){error.setText(Objects.toString(invalid.getMessage(),"目录路径无效"));event.consume();}});
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK){String destination=path.getText().trim();boolean subdirectories=recursive.isSelected();
            done(workspace.edit(p.id(),latest -> {Path target=Path.of(destination);if(java.nio.file.Files.exists(target)&&!java.nio.file.Files.isDirectory(target))throw new IllegalArgumentException("所选路径不是文件夹");
                return latest.configured(latest.id(),latest.name(),destination,subdirectories);}));}}
    private void delete(){if(current==null)return;String id=current.id();var alert=new Alert(Alert.AlertType.CONFIRMATION,"删除歌单记录，保留所有音频文件？",ButtonType.OK,ButtonType.CANCEL);alert.initOwner(window);alert.setTitle("删除歌单");alert.setHeaderText(current.name());theme(alert);
        if(alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)done(workspace.delete(id));}
    private void move(int direction){if(current==null||entries.getSelectionModel().getSelectedItem()==null)return;String item=entries.getSelectionModel().getSelectedItem().id();done(workspace.edit(current.id(),p -> {
        var list=new ArrayList<>(p.entries());int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).id().equals(item))index=i;
        if(index>=0&&index+direction>=0&&index+direction<list.size())Collections.swap(list,index,index+direction);return p.withEntries(list);
    }));}
    private void playback(boolean append){if(current==null)return;var p=current;workspace.work(() -> p.entries().stream().map(NamedPlaylist.Entry::location).filter(files::readable).toList())
        .whenComplete((locations,error) -> Platform.runLater(() -> {if(error!=null)fail(error);else if(locations.isEmpty())fail(new IllegalStateException("歌单中尚无可读取的本地歌曲"));else play.accept(locations,append);}));}
    private void done(CompletableFuture<?> future){future.whenComplete((v,error) -> Platform.runLater(() -> {if(error!=null)fail(error);else reload();}));}
    private Window dialogOwner(){return importPreview!=null&&importPreview.isShowing()?importPreview:window;}
    private void fail(Throwable error){if(!window.isShowing())return;while(error.getCause()!=null)error=error.getCause();var alert=new Alert(Alert.AlertType.ERROR,Objects.toString(error.getMessage(),"操作失败"),ButtonType.OK);alert.initOwner(dialogOwner());alert.setTitle("操作未完成");alert.setHeaderText("请检查后重试");theme(alert);alert.show();}
}
