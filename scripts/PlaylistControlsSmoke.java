import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.online.PlaylistActionFixtures;
import app.musicplayer.playlist.*;
import app.musicplayer.ui.PlaylistWindow;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.robot.Robot;
import javafx.stage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Exercises real button handlers and native mouse picking, in a disposable library. */
public class PlaylistControlsSmoke extends PlaylistDesktopSmoke {
    static PlaylistWindow manager;
    static Stage owner,main;
    static final List<List<String>> played=new ArrayList<>();
    static final List<Boolean> appended=new ArrayList<>();
    static Button control(String title)throws Exception{return ((Map<String,Button>)read(manager,"controls")).get(title);}
    static void press(String title)throws Exception{fx(() -> {Button b=control(title);if(b.isDisabled())throw new AssertionError("Disabled action: "+title);Platform.runLater(b::fire);return null;});}
    static NamedPlaylist current()throws Exception{return (NamedPlaylist)read(manager,"current");}
    static void click(javafx.scene.Node node)throws Exception{
        Thread.sleep(200);fx(() -> {var box=node.localToScreen(node.getBoundsInLocal());if(box==null)throw new AssertionError("Missing screen bounds");
            var robot=new Robot();robot.mouseMove(box.getCenterX(),box.getCenterY());robot.mousePress(MouseButton.PRIMARY);robot.mouseRelease(MouseButton.PRIMARY);return null;});
    }
    static void dialogButton(String title,ButtonType type)throws Exception{click(fx(() -> (Button)((DialogPane)window(title).getScene().getRoot()).lookupButton(type)));}
    static void checked(String entryId)throws Exception{
        fx(() -> {ListView<NamedPlaylist.Entry> list=(ListView<NamedPlaylist.Entry>)read(manager,"entries");
            int row=0;while(!list.getItems().get(row).id().equals(entryId))row++;list.scrollTo(row);main.getScene().getRoot().applyCss();main.getScene().getRoot().layout();return null;});
        String label=fx(() -> current().entries().stream().filter(e -> e.id().equals(entryId)).findFirst().orElseThrow().label());
        await(() -> main.getScene().getRoot().lookupAll(".check-box").stream().anyMatch(n -> n instanceof CheckBox c&&c.getText().startsWith(label)),"Checkbox missing");
        var check=fx(() -> main.getScene().getRoot().lookupAll(".check-box").stream().filter(n -> n instanceof CheckBox c&&c.getText().startsWith(label)).map(n -> (CheckBox)n).findFirst().orElseThrow());
        boolean was=fx(() -> ((Set<?>)read(manager,"selected")).contains(entryId));click(check);
        await(() -> ((Set<?>)read(manager,"selected")).contains(entryId)!=was,"Native checkbox click was not delivered");
    }
    static void snapshot(Path output)throws Exception{fx(() -> {var shot=main.getScene().getRoot().snapshot(null,null);var raster=new java.awt.image.BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<raster.getHeight();y++)for(int x=0;x<raster.getWidth();x++)raster.setRGB(x,y,shot.getPixelReader().getArgb(x,y));javax.imageio.ImageIO.write(raster,"png",output.toFile());return null;});}
    public static void main(String[]args)throws Exception{
        Path data=Path.of(System.getProperty("musicplayer.data-dir"));Files.createDirectories(data);
        Platform.startup(() -> {});MusicDatabase db=new MusicDatabase(data.resolve("controls.db"));
        var files=new DesktopPlaylistFiles(db);var workspace=new PlaylistWorkspace(db,data.resolve("scratch-original"),files,Platform::runLater);
        workspace.downloads.shutdown().get(10,TimeUnit.SECONDS);
        var fixture=new PlaylistActionFixtures(db,data.resolve("scratch"),Platform::runLater);
        var field=PlaylistWorkspace.class.getDeclaredField("downloads");field.setAccessible(true);field.set(workspace,fixture.downloads);
        try{
            fx(() -> {owner=new Stage();owner.setScene(new Scene(new Pane(),300,150));owner.show();manager=new PlaylistWindow(owner,workspace,files,data.toString(),(locations,append) -> {played.add(locations);appended.add(append);});manager.show();main=(Stage)read(manager,"window");return null;});
            await(() -> current()==null,"Initial playlist must be empty");
            fx(() -> {for(var b:((Map<String,Button>)read(manager,"controls")).entrySet())if(!b.getKey().equals("导入歌单")&&!b.getValue().isDisabled())throw new AssertionError("Empty playlist action enabled: "+b.getKey());return null;});
            press("导入歌单");await(() -> window("导入歌单")!=null,"Import input missing");
            fx(() -> {DialogPane pane=(DialogPane)window("导入歌单").getScene().getRoot();if(!pane.lookupButton(ButtonType.OK).isDisabled())throw new AssertionError("Blank link accepted");((TextField)pane.lookup(".text-field")).setText("test invalid share link");return null;});
            dialogButton("导入歌单",ButtonType.CANCEL);await(() -> window("导入歌单")==null,"Native cancel click was intercepted");
            press("导入歌单");await(() -> window("导入歌单")!=null,"Import input reopen missing");
            fx(() -> {((TextField)window("导入歌单").getScene().getRoot().lookup(".text-field")).setText("test invalid share link");return null;});
            dialogButton("导入歌单",ButtonType.OK);await(() -> window("导入歌单")==null,"Native confirm click was intercepted");
            await(() -> window("操作未完成")!=null,"Invalid link did not report an error");dialogButton("操作未完成",ButtonType.OK);
            await(() -> window("操作未完成")==null&&!control("导入歌单").isDisabled(),"Import did not recover after error");
            Path local=Files.write(data.resolve("歌手 - 本地曲.wav"),new byte[2048]);var first=NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","本地曲","歌手","","","local",""),0).local(local.toString());
            var rows=new ArrayList<NamedPlaylist.Entry>();rows.add(first);for(int i=1;i<=3;i++)rows.add(NamedPlaylist.Entry.create(new OnlineTrackInfo("QQ音乐","测试曲"+i,"歌手","","","song"+i,""),0));
            var p=new NamedPlaylist("buttons","按钮验证歌单","QQ音乐","fixture","","","测试",4,data.toString(),false,rows);workspace.work(() -> {db.savePlaylist(p);return null;}).get();
            fx(() -> {invoke(manager,"reload",new Class<?>[0]);return null;});await(() -> current()!=null&&current().entries().size()==4,"Seed playlist did not load");
            press("全选");await(() -> ((Set<?>)read(manager,"selected")).size()==4,"Select all failed");press("取消选择");await(() -> ((Set<?>)read(manager,"selected")).isEmpty(),"Unselect failed");
            fx(() -> {((TextField)read(manager,"search")).setText("测试曲2");return null;});press("全选");await(() -> ((Set<?>)read(manager,"selected")).size()==1,"Filtered select all failed");
            press("取消选择");fx(() -> {((TextField)read(manager,"search")).clear();return null;});
            checked(rows.get(1).id());await(() -> !control("上移").isDisabled()&&!control("选择下载版本").isDisabled(),"Checkbox did not focus its row");
            press("上移");await(() -> current().entries().get(0).id().equals(rows.get(1).id()),"Move up failed");
            fx(() -> {if(!control("上移").isDisabled())throw new AssertionError("Move beyond first row allowed");return null;});
            press("下移");await(() -> current().entries().get(1).id().equals(rows.get(1).id()),"Focused row was lost after moving");
            press("重命名");await(() -> window("重命名歌单")!=null,"Rename dialog missing");fx(() -> {((TextField)window("重命名歌单").getScene().getRoot().lookup(".text-field")).setText("新的歌单名称");return null;});
            dialogButton("重命名歌单",ButtonType.OK);await(() -> current().name().equals("新的歌单名称"),"Rename did not persist");
            press("下载目录");await(() -> window("歌单下载目录")!=null,"Directory settings missing");
            fx(() -> {((TextField)window("歌单下载目录").getScene().getRoot().lookup(".text-field")).setText("relative-path");return null;});dialogButton("歌单下载目录",ButtonType.OK);
            await(() -> window("歌单下载目录")!=null&&window("歌单下载目录").getScene().getRoot().lookupAll(".playlist-error").stream().anyMatch(n -> n instanceof Label l&&!l.getText().isBlank()),"Relative directory was accepted without feedback");
            Path destination=data.resolve("downloads");fx(() -> {var root=window("歌单下载目录").getScene().getRoot();((TextField)root.lookup(".text-field")).setText(destination.toString());((CheckBox)root.lookup(".check-box")).setSelected(true);return null;});
            dialogButton("歌单下载目录",ButtonType.OK);await(() -> current().directory().equals(destination.toString())&&current().recursive(),"Directory/recursive settings did not persist");
            // Cancelling duplicate review must not enqueue anything.
            press("全选");press("下载选中");await(() -> window("发现重复歌曲")!=null,"Duplicate review missing");dialogButton("发现重复歌曲",ButtonType.CANCEL);
            await(() -> window("发现重复歌曲")==null,"Duplicate cancellation failed");if(workspace.downloads.active(p.id()))throw new AssertionError("Duplicate cancellation started downloads");
            press("取消选择");checked(rows.get(1).id());fixture.fail=true;
            press("选择下载版本");await(() -> window("选择下载版本")!=null,"Version picker missing");click(fx(() -> findButton(window("选择下载版本"),"确认版本并下载")));
            await(() -> current().entries().get(1).state()==NamedPlaylist.State.FAILED&&!workspace.downloads.active(p.id()),"Version download did not run/report failure");
            fx(() -> {var e=current().entries().get(1);if(!e.userSelectedVersion()||!e.downloadTrack().source().equals("酷我音乐"))throw new AssertionError("Manual version did not persist");return null;});
            fixture.hold=true;fixture.fail=false;press("下载未完成");await(() -> workspace.downloads.active(p.id())&&fixture.attempts.get()>=3,"Unfinished download did not enqueue");
            await(() -> !control("暂停 / 继续").isDisabled()&&control("删除歌单").isDisabled(),"Active task control state wrong");
            press("暂停 / 继续");await(() -> workspace.downloads.paused(p.id())&&control("暂停 / 继续").getText().equals("继续下载"),"Pause failed");
            press("暂停 / 继续");await(() -> !workspace.downloads.paused(p.id()),"Resume failed");
            press("取消下载");await(() -> !workspace.downloads.active(p.id())&&current().entries().stream().skip(1).allMatch(e -> e.state()==NamedPlaylist.State.CANCELLED),"Cancel failed");
            fixture.hold=false;press("取消选择");checked(rows.get(1).id());press("下载选中");
            await(() -> current().entries().get(1).state()==NamedPlaylist.State.READY&&!workspace.downloads.active(p.id()),"Selected download did not finish");
            int before=fixture.attempts.get();press("下载未完成");await(() -> current().entries().stream().allMatch(e -> e.state()==NamedPlaylist.State.READY)&&!workspace.downloads.active(p.id()),"Unfinished retry did not complete");
            if(fixture.attempts.get()-before!=2)throw new AssertionError("Unfinished download fetched completed songs");
            press("播放本地歌曲");await(() -> played.size()==1,"Play button was not wired");press("加入播放队列");await(() -> played.size()==2,"Queue button was not wired");
            fx(() -> {if(!appended.equals(List.of(false,true))||!played.get(0).equals(current().entries().stream().map(NamedPlaylist.Entry::location).toList()))throw new AssertionError("Local play/queue order changed");return null;});
            press("删除歌单");await(() -> window("删除歌单")!=null,"Delete confirmation missing");dialogButton("删除歌单",ButtonType.CANCEL);await(() -> window("删除歌单")==null,"Delete cancel failed");
            if(db.loadPlaylists().isEmpty())throw new AssertionError("Cancelling delete removed playlist");
            snapshot(data.resolve("playlist-controls.png"));press("删除歌单");await(() -> window("删除歌单")!=null,"Delete confirmation missing");dialogButton("删除歌单",ButtonType.OK);
            await(() -> current()==null,"Deletion did not clear page");if(!Files.exists(local))throw new AssertionError("Deletion removed audio");
            // Optional live probe uses the user's public link through the real input, preview and save path.
            String live=System.getProperty("musicplayer.qa.playlist-link","");if(!live.isBlank()){
                press("导入歌单");await(() -> window("导入歌单")!=null,"Live input missing");fx(() -> {((TextField)window("导入歌单").getScene().getRoot().lookup(".text-field")).setText(live);return null;});dialogButton("导入歌单",ButtonType.OK);
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);while(fx(() -> window("导入预览")==null&&window("操作未完成")==null)&&System.nanoTime()<until)Thread.sleep(200);
                if(fx(() -> window("导入预览")==null))throw new AssertionError("Live playlist did not reach preview");click(fx(() -> findButton(window("导入预览"),"确认导入")));
                await(() -> current()!=null&&current().source().equals("酷狗音乐"),"Live playlist did not save");snapshot(data.resolve("playlist-live.png"));System.out.println("LIVE IMPORT PASSED: "+fx(() -> current().name()+" · "+current().entries().size()+" songs"));
            }
            System.out.println("PLAYLIST CONTROLS PASSED: native confirm/cancel, invalid/blank input, all 15 actions, filtered selection, reorder focus, rename, directory, duplicates, version, pause/resume/cancel, retries, play/append, safe deletion");
            System.out.println("QA evidence: "+data);
        }finally{fx(() -> {for(var w:new ArrayList<>(Window.getWindows()))w.hide();return null;},15);workspace.shutdown().get(20,TimeUnit.SECONDS);db.close();Platform.exit();}
    }
}
