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
    private final Set<String> selected=new HashSet<>();
    private final Consumer<String> downloadListener=id -> reload();
    private NamedPlaylist current;
    private Stage importPreview;
    private boolean refreshing;
    private long request, importRequest;
    private final String defaultDirectory;
    public PlaylistWindow(Stage owner,PlaylistWorkspace workspace,PlaylistFiles files,String defaultDirectory,
                          BiConsumer<List<String>,Boolean> play){
        this.workspace=workspace;this.files=files;this.defaultDirectory=defaultDirectory;this.play=play;window.initOwner(owner);window.setTitle("我的歌单");
        playlists.setMaxWidth(Double.MAX_VALUE);playlists.setOnAction(e -> {if(!refreshing){current=playlists.getValue();selected.clear();showEntries();}});
        search.setPromptText("搜索歌单中的歌名或歌手");search.textProperty().addListener((o,a,b) -> showEntries());
        entries.setCellFactory(v -> checkCell(selected));
        FlowPane actions=new FlowPane(8,8,button("导入歌单",this::importLink),button("重命名",this::rename),button("下载目录",this::directory),button("删除歌单",this::delete),
                button("全选",() -> {if(current!=null)current.entries().forEach(e -> selected.add(e.id()));entries.refresh();}),
                button("取消选择",() -> {selected.clear();entries.refresh();}),button("上移",() -> move(-1)),button("下移",() -> move(1)));
        FlowPane downloads=new FlowPane(8,8,button("下载选中",this::download),button("下载未完成",this::downloadMissing),
                button("选择下载版本",this::chooseDownload),
                button("暂停 / 继续",this::pause),button("取消下载",this::cancel),button("播放本地歌曲",() -> playback(false)),button("加入播放队列",() -> playback(true)));
        VBox root=new VBox(10,playlists,actions,search,summary,entries,downloads);root.setPadding(new Insets(18));VBox.setVgrow(entries,Priority.ALWAYS);
        Scene scene=new Scene(root,880,650);scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());window.setScene(scene);
        window.setOnShown(e -> {workspace.downloads.listen(downloadListener);workspace.refreshFiles(files).whenComplete((v,error) -> Platform.runLater(() -> {if(error!=null)fail(error);else reload();}));});window.setOnHidden(e -> {request++;importRequest++;workspace.downloads.unlisten(downloadListener);});
    }
    public void show(){window.show();window.toFront();}
    private static Button button(String title,Runnable action){Button b=new Button(title);b.setOnAction(e -> action.run());return b;}
    private ListCell<NamedPlaylist.Entry> checkCell(Set<String> selection){return new ListCell<>(){
        private final CheckBox check=new CheckBox();
        {check.setOnAction(e -> {if(getItem()!=null){if(check.isSelected())selection.add(getItem().id());else selection.remove(getItem().id());}});}
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
        if(current==null){entries.getItems().clear();summary.setText("尚无歌单，点击导入歌单开始");return;}
        String query=search.getText().trim().toLowerCase(Locale.ROOT);
        entries.getItems().setAll(current.entries().stream().filter(e -> e.label().toLowerCase(Locale.ROOT).contains(query)).toList());
        summary.setText(current.progressLabel()+(workspace.downloads.paused(current.id())?" · 已暂停":"")+"\n下载目录："+current.directory());
    }
    private void importLink(){var dialog=new TextInputDialog();dialog.initOwner(window);dialog.setTitle("导入歌单");dialog.setHeaderText("粘贴酷狗、网易云或 QQ 音乐的歌单分享文字 / 链接");
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(link -> {
            summary.setText("正在读取歌单…");long version=++importRequest;
            workspace.read(link).whenComplete((playlist,error) -> Platform.runLater(() -> {if(version!=importRequest||!window.isShowing())return;if(error!=null)fail(error);else preview(playlist);}));
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
        ImageView cover=new ImageView();cover.setFitWidth(64);cover.setFitHeight(64);cover.setPreserveRatio(true);
        if(!playlist.artworkUrl().isBlank())cover.setImage(new Image(playlist.artworkUrl(),64,64,true,true,true));
        FlowPane selection=new FlowPane(8,8,button("全选",() -> {playlist.entries().forEach(e -> chosen.add(e.id()));list.refresh();}),button("取消选择",() -> {chosen.clear();list.refresh();}));
        Button save=new Button("确认导入");save.setOnAction(event -> {
            if(name.getText().isBlank()||chosen.isEmpty()){fail(new IllegalArgumentException("请输入歌单名称并至少选择一首歌曲"));return;}
            if(directory.getText().isBlank()){fail(new IllegalArgumentException("请选择下载目录"));return;}
            try{Path.of(directory.getText().trim());}catch(RuntimeException e){fail(e);return;}
            var value=playlist.configured(playlist.id(),name.getText().trim(),directory.getText().trim(),recursive.isSelected())
                    .withEntries(playlist.entries().stream().filter(e -> chosen.contains(e.id())).toList());
            save.setDisable(true);
            review(value,result -> {stage.close();saveImported(result,download.isSelected());},() -> save.setDisable(false));
        });
        VBox root=new VBox(10,new HBox(10,cover,counts),name,new HBox(8,directory,pick),recursive,selection,list,download,save);root.setPadding(new Insets(16));VBox.setVgrow(list,Priority.ALWAYS);
        stage.setScene(new Scene(root,700,620));stage.show();
    }
    private void review(NamedPlaylist playlist,Consumer<NamedPlaylist> accept,Runnable aborted){
        workspace.work(() -> new PlaylistDuplicates(files.scan(playlist.directory(),playlist.recursive())).match(playlist.entries()))
            .whenComplete((matches,error) -> Platform.runLater(() -> {
                if(!window.isShowing()){aborted.run();return;}if(error!=null){fail(error);aborted.run();return;}
                if(matches.isEmpty()){accept.accept(playlist);return;}
                Dialog<Boolean> dialog=new Dialog<>();dialog.initOwner(dialogOwner());dialog.setTitle("发现重复歌曲");
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
        if(error!=null){fail(error);return;}var same=values.stream().filter(p -> p.source().equals(playlist.source())&&p.sourceId().equals(playlist.sourceId())).findFirst();
        String existing=null;if(same.isPresent()){ButtonType update=new ButtonType("更新已有歌单"),copy=new ButtonType("另存一份");
            var alert=new Alert(Alert.AlertType.CONFIRMATION,"已导入过此歌单。更新将采用这次选择的歌曲和原平台顺序，音频文件保留。",update,copy,ButtonType.CANCEL);alert.initOwner(window);
            var choice=alert.showAndWait().orElse(ButtonType.CANCEL);if(choice==ButtonType.CANCEL)return;if(choice==update)existing=same.get().id();}
        workspace.save(playlist,existing).whenComplete((saved,failure) -> Platform.runLater(() -> {
            if(failure!=null){fail(failure);return;}current=saved;selected.clear();reload();
            if(download)workspace.work(() -> {workspace.downloads.start(saved,missingIds(saved));return null;}).exceptionally(e -> {Platform.runLater(() -> fail(e));return null;});
        }));
    }));}
    private static Set<String> missingIds(NamedPlaylist p){Set<String> ids=new HashSet<>();for(var e:p.entries())if(e.state()!=NamedPlaylist.State.READY)ids.add(e.id());return ids;}
    private void downloadMissing(){if(current==null)return;selected.clear();selected.addAll(missingIds(current));download();}
    private void download(){if(current==null||selected.isEmpty())return;NamedPlaylist p=current;
        reviewAndStart(p,p.withEntries(p.entries().stream().filter(e -> selected.contains(e.id())).map(NamedPlaylist.Entry::prepareDownload).toList()));
    }
    private void chooseDownload(){
        if(current==null||selected.size()!=1){fail(new IllegalArgumentException("请只勾选一首歌曲，再选择下载版本"));return;}
        NamedPlaylist p=current;var entry=p.entries().stream().filter(e -> selected.contains(e.id())).findFirst().orElse(null);
        if(entry==null){selected.clear();showEntries();fail(new IllegalArgumentException("所选歌曲已变化，请重新选择"));return;}
        summary.setText("正在搜索软件中的下载渠道…");long version=++importRequest;
        workspace.candidates(entry).whenComplete((candidates,error) -> Platform.runLater(() -> {
            if(version!=importRequest||!window.isShowing())return;showEntries();if(error!=null){fail(error);return;}
            if(candidates.isEmpty()){fail(new IllegalStateException("未找到可选择的歌曲，请更换关键词在在线搜索中检查"));return;}
            Dialog<app.musicplayer.model.OnlineTrackInfo> dialog=new Dialog<>();dialog.initOwner(window);dialog.setTitle("选择下载版本");dialog.setHeaderText("原歌单："+entry.label()+"\n请核对歌手、歌曲和版本后确认下载");
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
    private void cancel(){if(current!=null)workspace.work(() -> {workspace.downloads.cancel(current.id());return null;}).exceptionally(e -> {Platform.runLater(() -> fail(e));return null;});}
    private void rename(){if(current==null)return;var dialog=new TextInputDialog(current.name());dialog.initOwner(window);dialog.setHeaderText("歌单名称");String id=current.id();
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(name -> done(workspace.edit(id,p -> p.configured(p.id(),name.trim(),p.directory(),p.recursive()))));}
    private void directory(){if(current==null)return;var chooser=new DirectoryChooser();chooser.setTitle("选择歌单下载目录");var folder=chooser.showDialog(window);String id=current.id();
        if(folder!=null)done(workspace.edit(id,p -> p.configured(p.id(),p.name(),folder.getAbsolutePath(),p.recursive())));}
    private void delete(){if(current==null)return;var alert=new Alert(Alert.AlertType.CONFIRMATION,"删除歌单记录，保留所有音频文件？",ButtonType.OK,ButtonType.CANCEL);alert.initOwner(window);
        if(alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK)done(workspace.delete(current.id()));}
    private void move(int direction){if(current==null||entries.getSelectionModel().getSelectedItem()==null)return;String item=entries.getSelectionModel().getSelectedItem().id();done(workspace.edit(current.id(),p -> {
        var list=new ArrayList<>(p.entries());int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).id().equals(item))index=i;
        if(index>=0&&index+direction>=0&&index+direction<list.size())Collections.swap(list,index,index+direction);return p.withEntries(list);
    }));}
    private void playback(boolean append){if(current==null)return;var p=current;workspace.work(() -> p.entries().stream().map(NamedPlaylist.Entry::location).filter(files::readable).toList())
        .whenComplete((locations,error) -> Platform.runLater(() -> {if(error!=null)fail(error);else if(locations.isEmpty())fail(new IllegalStateException("歌单中尚无可读取的本地歌曲"));else play.accept(locations,append);}));}
    private void done(CompletableFuture<?> future){future.whenComplete((v,error) -> Platform.runLater(() -> {if(error!=null)fail(error);else reload();}));}
    private Window dialogOwner(){return importPreview!=null&&importPreview.isShowing()?importPreview:window;}
    private void fail(Throwable error){while(error.getCause()!=null)error=error.getCause();var alert=new Alert(Alert.AlertType.ERROR,Objects.toString(error.getMessage(),"操作失败"),ButtonType.OK);alert.initOwner(dialogOwner());alert.show();}
}
