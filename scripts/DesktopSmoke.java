import app.musicplayer.MusicPlayerApp;
import app.musicplayer.model.Track;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.media.MediaPlayer;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class DesktopSmoke {
    static MusicPlayerApp app;
    static Stage stage;
    static Object field(String name) throws Exception { var f=MusicPlayerApp.class.getDeclaredField(name);f.setAccessible(true);return f.get(app); }
    static Object call(String name,Class<?> type,Object argument) throws Exception {
        var method=type==null ? MusicPlayerApp.class.getDeclaredMethod(name) : MusicPlayerApp.class.getDeclaredMethod(name,type);
        method.setAccessible(true);return type==null? method.invoke(app):method.invoke(app,argument);
    }
    interface Work<T>{T get() throws Exception;}
    static <T>T fx(Work<T> work) throws Exception { return fx(work,2); }
    static <T>T fx(Work<T> work,int timeout) throws Exception {
        var result=new CompletableFuture<T>();
        Platform.runLater(()->{try{result.complete(work.get());}catch(Throwable error){result.completeExceptionally(error);}});
        return result.get(timeout,TimeUnit.SECONDS);
    }
    static void await(Work<Boolean> check,String reason)throws Exception{
        for(int i=0;i<120;i++){if(fx(check))return;Thread.sleep(100);}
        throw new AssertionError(reason);
    }
    static void checkArtworkPresentation(Path data) throws Exception {
        Path source = data.resolve("cover.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(576, 576,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        byte[] bytes = Files.readAllBytes(source);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet(); started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        var view = fx(() -> new javafx.scene.image.ImageView());
        try (var service = new app.musicplayer.artwork.ArtworkService(data.resolve("artwork"))) {
            var presenter = fx(() -> new app.musicplayer.artwork.ArtworkPresenter(service, view));
            try {
                String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png";
                fx(() -> { view.setFitWidth(144); view.setFitHeight(144); presenter.show(url); return null; });
                if (!started.await(5, TimeUnit.SECONDS)) throw new AssertionError("Artwork request never started");
                fx(() -> { view.setFitWidth(288); view.setFitHeight(288); return null; });
                release.countDown();
                await(() -> view.getImage() != null && view.getImage().getWidth() == 288,
                        "Pending artwork did not use the latest view dimensions");
                if (hits.get() != 1) throw new AssertionError("Resizing duplicated the artwork download");
                fx(() -> { presenter.show(null); presenter.show(url); return null; });
                if (!fx(() -> view.getImage() != null)) throw new AssertionError("Decoded artwork cache was not reused");
                fx(() -> {
                    presenter.close();
                    var container = new javafx.scene.layout.StackPane(view);
                    Stage attached = new Stage(); attached.setScene(new javafx.scene.Scene(container));
                    try {
                        view.setFitWidth(512); view.setFitHeight(512); presenter.show(url);
                        var window = app.musicplayer.artwork.ArtworkPresenter.class.getDeclaredField("window");
                        window.setAccessible(true);
                        if (window.get(presenter) != null || view.getImage() != null)
                            throw new AssertionError("Closed artwork presenter resumed after attachment or resize");
                    } finally { attached.close(); }
                    return null;
                });
                if (hits.get() != 1) throw new AssertionError("Closed artwork presenter started a request");
            } finally { fx(() -> { presenter.close(); return null; }); }
        } finally { release.countDown(); server.stop(0); }
    }
    static void checkOnlinePresentation(Path data) throws Exception {
        try (var fixture = new app.musicplayer.online.OnlineSmokeFixtures()) {
            var results = fx(() -> javafx.collections.FXCollections.<app.musicplayer.model.OnlineTrackInfo>observableArrayList());
            var status = new java.util.concurrent.atomic.AtomicReference<String>("");
            var published = new java.util.concurrent.atomic.AtomicInteger();
            var prefs = java.util.prefs.Preferences.userRoot().node(System.getProperty("musicplayer.preferences-node"));
            var drawer = fx(() -> new app.musicplayer.ui.OnlineDrawer(results, prefs, () -> { }, ignored -> { }, ignored -> { }));
            var controller = fx(() -> new app.musicplayer.ui.DesktopOnlineTasks(fixture.service, drawer, results,
                    new app.musicplayer.ui.DesktopOnlineTasks.Callbacks(status::set, () -> { }, ignored -> { })));
            try {
                fx(() -> { drawer.searchField().setText("song"); controller.search(); return null; });
                await(() -> results.size() == 2, "Fast online source was not published before the slow source");
                fx(() -> { drawer.resultsView().getSelectionModel().select(1); return null; });
                var selected = fx(() -> drawer.resultsView().getSelectionModel().getSelectedItem());
                fixture.releaseSource.countDown();
                await(() -> results.size() == 3 && status.get().contains("搜索完成"), "Online search did not finish");
                if (fx(() -> drawer.resultsView().getSelectionModel().getSelectedItem()) != selected)
                    throw new AssertionError("Source append changed the selected online song");
                fx(() -> {
                    controller.download(selected, data.resolve("online-fixture"), path -> {
                        published.incrementAndGet(); return CompletableFuture.completedFuture(path);
                    }, ignored -> { }); return null;
                });
                if (!fixture.transferStarted.await(3, TimeUnit.SECONDS)) throw new AssertionError("UI transfer did not start");
                await(() -> status.get().contains("2.0 KiB"), "Unknown-size transfer byte count was not shown");
                if (status.get().contains("%")) throw new AssertionError("Unknown-size transfer displayed a fake percentage");
                fx(() -> { if (!controller.cancelDownload(selected)) throw new AssertionError("Selected transfer was not cancellable"); return null; });
                await(() -> status.get().contains("已取消下载"), "Cancellation was not shown");
                if (published.get() != 0) throw new AssertionError("Cancelled transfer entered the library");
                fx(() -> {
                    controller.download(selected, data.resolve("online-fixture"), path -> {
                        published.incrementAndGet(); return CompletableFuture.completedFuture(path);
                    }, ignored -> { }); return null;
                });
                await(() -> status.get().contains("下载完成"), "Retry after cancellation did not complete");
                if (published.get() != 1) throw new AssertionError("Retry published more than once");
                int calls = fixture.sourceCalls.get();
                fx(() -> { controller.search(); return null; });
                await(() -> status.get().contains("缓存"), "Successful query cache was not shown");
                if (fixture.sourceCalls.get() != calls) throw new AssertionError("Cached query contacted sources again");
                try (var files = Files.list(data.resolve("online-fixture"))) {
                    if (files.anyMatch(path -> path.toString().endsWith(".part"))) throw new AssertionError("Cancelled UI transfer left a temporary file");
                }
            } finally { fx(() -> { controller.close(); return null; }); }
        }
    }
    public static void main(String[] args)throws Exception {
        Path data=Path.of(System.getProperty("musicplayer.data-dir"));Files.createDirectories(data);
        List<Track> initial=new ArrayList<>();
        for(String name:List.of("QA-A.wav","QA-B.wav")){
            int size=44100*2*2;
            ByteBuffer wav=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
            wav.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '});
            wav.putInt(16).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16);
            wav.put(new byte[]{'d','a','t','a'}).putInt(size);
            initial.add(new Track(Files.write(data.resolve(name),wav.array())));
        }
        try(var db=new MusicDatabase(data.resolve("music-player.db"))){db.saveTracks(initial);for(var track:initial)db.saveLyrics(track,LrcParser.parse("fixture","[00:00]silent smoke test"));}
        Platform.startup(()->{});
        try{
            fx(()->{
                app=new MusicPlayerApp();
                Class<?> parameters=Class.forName("com.sun.javafx.application.ParametersImpl");
                Object value=parameters.getConstructor(List.class).newInstance(List.of());
                parameters.getMethod("registerParameters",javafx.application.Application.class,javafx.application.Application.Parameters.class).invoke(null,app,value);
                stage=new Stage();app.start(stage);stage.setIconified(true);return null;
            },15);
            await(()->((List<?>)field("tracks")).size()==2,"Library was not restored");
            List<?> tracks=fx(()->new ArrayList<>((List<?>)field("tracks")));
            Track first=(Track)tracks.get(0),last=(Track)tracks.get(1);
            // Artificial slow disk work must not block the FX thread or apply an obsolete selection.
            fx(()->{((ExecutorService)field("libraryExecutor")).submit(()->{try{Thread.sleep(1500);}catch(InterruptedException error){Thread.currentThread().interrupt();}});call("playTrack",Track.class,first);call("playTrack",Track.class,last);return null;});
            for(int i=0;i<8;i++){fx(()->true);Thread.sleep(100);}
            await(()->field("mediaPlayer") instanceof MediaPlayer p && p.getStatus()==MediaPlayer.Status.PLAYING,"Selected track did not play");
            if(fx(()->field("currentTrack"))!=last)throw new AssertionError("Obsolete selection applied");
            await(()->field("currentTrack")==first,"Last track did not loop to first");
            await(()->field("mediaPlayer") instanceof MediaPlayer p && p.getStatus()==MediaPlayer.Status.PLAYING,"Looped track did not play");
            fx(()->{call("togglePlayPause",null,null);return null;});
            await(()->((MediaPlayer)field("mediaPlayer")).getStatus()==MediaPlayer.Status.PAUSED,"Pause failed");
            fx(()->{call("togglePlayPause",null,null);return null;});
            await(()->((MediaPlayer)field("mediaPlayer")).getStatus()==MediaPlayer.Status.PLAYING,"Resume failed");
            MediaPlayer playing = fx(() -> (MediaPlayer)field("mediaPlayer"));
            List<?> queue = fx(() -> new ArrayList<>((List<?>)field("tracks")));
            fx(() -> {
                var search = (javafx.scene.control.TextField)field("searchField");
                search.setText("QA-A"); search.setText("absent"); search.setText(last.path().getFileName().toString());
                return null;
            });
            await(() -> ((List<?>)field("filteredTracks")).size()==1
                    && ((List<?>)field("filteredTracks")).get(0)==last, "Latest local filter was not displayed");
            fx(() -> {
                if (!queue.equals(field("tracks")) || playing != field("mediaPlayer"))
                    throw new AssertionError("Filtering changed the playing queue/player");
                ((javafx.scene.control.TextField)field("searchField")).setText(""); return null;
            });
            await(() -> ((List<?>)field("filteredTracks")).size()==2, "Clearing filter did not restore library");
            checkArtworkPresentation(data);
            checkOnlinePresentation(data);
        System.out.println("DESKTOP SMOKE PASSED: async restore, responsive slow I/O, latest selection, natural queue loop while minimized, pause/resume, local filter without changing playback, incremental online selection, download progress/cancel/retry and search cache");
        }finally{
            fx(()->{if(app!=null)app.stop();if(stage!=null)stage.close();return null;});Platform.exit();
            String preferences = System.getProperty("musicplayer.preferences-node");
            if (preferences != null && preferences.startsWith("/app/musicplayer/qa/"))
                java.util.prefs.Preferences.userRoot().node(preferences).removeNode();
        }
    }
}
