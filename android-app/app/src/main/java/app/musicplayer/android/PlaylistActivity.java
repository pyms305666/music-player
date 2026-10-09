package app.musicplayer.android;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import app.musicplayer.android.data.*;
import app.musicplayer.playlist.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PlaylistActivity extends AppCompatActivity {
    private PlaylistRuntime runtime;
    private Spinner playlists;
    private TextView status;
    private EditText search;
    private ListView list;
    private EntryAdapter adapter;
    private NamedPlaylist current;
    private List<NamedPlaylist> values=List.of();
    private final Set<String> selected=new HashSet<>();
    private boolean refreshing;
    private long request, importRequest;
    private EditText previewDestination;
    private String directoryPlaylistId;
    private String destination=AndroidPlaylistFiles.DEFAULT;
    private MediaController player;
    private com.google.common.util.concurrent.ListenableFuture<MediaController> playerFuture;
    private Runnable permissionGranted,permissionAborted;
    private final ActivityResultLauncher<String> audioPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted -> {
        Runnable resume=permissionGranted,abort=permissionAborted;permissionGranted=null;permissionAborted=null;
        if(isDestroyed())return;
        if(granted){if(resume!=null)resume.run();}else{
            fail(new IllegalStateException("需要读取音乐权限才能检查默认目录中的重复歌曲；也可以选择其他目录并授权访问。"));
            if(abort!=null)abort.run();
        }
    });
    private final Consumer<String> listener=id -> reload();
    private final ActivityResultLauncher<Uri> directoryPicker=registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),uri -> {
        if(uri==null)return;
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            destination=uri.toString();if(previewDestination!=null)previewDestination.setText(directoryLabel(destination));
            if(directoryPlaylistId!=null){String id=directoryPlaylistId;directoryPlaylistId=null;done(runtime.workspace.edit(id,p -> p.configured(p.id(),p.name(),uri.toString(),p.recursive())));}
        }catch(RuntimeException error){fail(error);}
    });
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);runtime=PlaylistRuntime.get(this);setTitle("我的歌单");
        LinearLayout root=column();root.setPadding(dp(14),dp(8),dp(14),dp(8));
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets) -> {
            var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(14)+bars.left,dp(8)+bars.top,dp(14)+bars.right,dp(8)+bars.bottom);return insets;
        });
        root.addView(row(button("返回播放器",this::finish),button("导入歌单",this::importLink)));
        playlists=new Spinner(this);playlists.setId(R.id.namedPlaylistSelector);root.addView(playlists);
        playlists.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!refreshing&&position<values.size()){
                var next=values.get(position);if(current==null||!current.id().equals(next.id()))selected.clear();current=next;showEntries();}}
            @Override public void onNothingSelected(AdapterView<?> p){ }
        });
        root.addView(scrollActions(button("重命名",this::rename),button("下载目录",this::directory),button("删除歌单",this::delete),button("全选",() -> {if(current!=null)current.entries().forEach(e -> selected.add(e.id()));adapter.notifyDataSetChanged();}),
                button("取消选择",() -> {selected.clear();adapter.notifyDataSetChanged();}),button("上移",() -> move(-1)),button("下移",() -> move(1))));
        search=input();search.setSingleLine();search.setHint("搜索歌名或歌手");root.addView(search);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){ }public void onTextChanged(CharSequence s,int a,int b,int c){showEntries();}public void afterTextChanged(android.text.Editable s){ }});
        status=label();status.setId(R.id.namedPlaylistStatus);root.addView(status);
        list=new ListView(this);list.setId(R.id.namedPlaylistEntries);adapter=new EntryAdapter(List.of(),selected);list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(scrollActions(button("下载选中",this::download),button("下载未完成",this::downloadMissing),button("选择下载版本",this::chooseDownload),button("暂停 / 继续",this::pause),button("取消下载",this::cancel)));
        root.addView(row(button("播放本地歌曲",() -> playback(false)),button("加入播放队列",() -> playback(true))));setContentView(root);
        playerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        playerFuture.addListener(() -> {try{if(!isDestroyed())player=playerFuture.get();}catch(Exception error){fail(error);}},this::runOnUiThread);
    }
    @Override protected void onStart(){super.onStart();runtime.workspace.downloads.listen(listener);runtime.workspace.refreshFiles(runtime.files).whenComplete((v,error) -> runOnUiThread(() -> {if(error!=null)fail(error);else reload();}));}
    @Override protected void onStop(){request++;runtime.workspace.downloads.unlisten(listener);super.onStop();}
    @Override protected void onDestroy(){importRequest++;player=null;permissionGranted=null;permissionAborted=null;if(playerFuture!=null)MediaController.releaseFuture(playerFuture);super.onDestroy();}
    private void reload(){long version=++request;runtime.workspace.list().whenComplete((loaded,error) -> runOnUiThread(() -> {
        if(version!=request||isDestroyed())return;if(error!=null){fail(error);return;}String id=current==null?"":current.id();values=loaded;refreshing=true;
        playlists.setAdapter(spinnerAdapter(values));int index=0;
        for(int i=0;i<values.size();i++)if(values.get(i).id().equals(id))index=i;current=values.isEmpty()?null:values.get(index);playlists.setSelection(index);
        selected.retainAll(current==null?Set.of():current.entries().stream().map(NamedPlaylist.Entry::id).collect(java.util.stream.Collectors.toSet()));
        playlists.post(() -> refreshing=false);showEntries();
    }));}
    private void showEntries(){if(adapter==null)return;if(current==null){adapter.replace(List.of());status.setText("尚无歌单，点击导入歌单开始");return;}
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);adapter.replace(current.entries().stream().filter(e -> e.label().toLowerCase(Locale.ROOT).contains(query)).toList());
        status.setText(current.progressLabel()+(runtime.workspace.downloads.paused(current.id())?" · 已暂停":"")+"\n目录："+directoryLabel(current.directory()));
    }
    private void importLink(){EditText input=input();input.setId(R.id.playlistShareLink);input.setHint("粘贴三家平台的歌单分享文字或链接");input.setMaxLines(5);
        var dialog=new MaterialAlertDialogBuilder(this).setTitle("导入歌单").setView(input).setNegativeButton("取消",null).setPositiveButton("读取歌单",null).create();dialog.show();
        dialog.getButton(-1).setOnClickListener(view -> {
            dialog.getButton(-1).setEnabled(false);long version=++importRequest;runtime.workspace.read(input.getText().toString()).whenComplete((playlist,error) -> runOnUiThread(() -> {
                if(version!=importRequest||isDestroyed()||!dialog.isShowing())return;dialog.getButton(-1).setEnabled(true);if(error!=null)fail(error);else{dialog.dismiss();preview(playlist);}
            }));
        });
    }
    private void preview(NamedPlaylist playlist){
        directoryPlaylistId=null;
        LinearLayout content=column();content.setPadding(dp(14),0,dp(14),0);TextView info=label();
        info.setText(playlist.source()+" · "+playlist.creator()+"\n已读取 "+playlist.entries().size()+(playlist.expectedCount()<0?" 首，平台未提供总数":" / "+playlist.expectedCount()+" 首")
                +(!playlist.complete()?"\n请核对清单，平台可能未提供完整内容":"")+"\n下载使用本软件渠道，优先匹配酷我、咪咕");content.addView(info);
        android.widget.ImageView cover=new android.widget.ImageView(this);cover.setContentDescription("歌单封面");content.addView(cover,new LinearLayout.LayoutParams(dp(56),dp(56)));
        if(!playlist.artworkUrl().isBlank())com.bumptech.glide.Glide.with(this).load(playlist.artworkUrl()).override(dp(56),dp(56)).into(cover);
        EditText name=input();name.setSingleLine();name.setText(playlist.name());name.setHint("歌单名称");content.addView(name);
        destination=AndroidPlaylistFiles.DEFAULT;previewDestination=input();previewDestination.setText(directoryLabel(destination));previewDestination.setEnabled(false);content.addView(previewDestination);
        content.addView(row(button("选择目录",() -> directoryPicker.launch(null)),button("默认音乐目录",() -> {destination=AndroidPlaylistFiles.DEFAULT;previewDestination.setText(directoryLabel(destination));})));
        CheckBox recursive=check();recursive.setText("检查子目录中的重复歌曲");content.addView(recursive);
        Set<String> chosen=new HashSet<>();playlist.entries().forEach(e -> chosen.add(e.id()));EntryAdapter previewAdapter=new EntryAdapter(playlist.entries(),chosen);
        content.addView(row(button("全选",() -> {playlist.entries().forEach(e -> chosen.add(e.id()));previewAdapter.notifyDataSetChanged();}),button("取消选择",() -> {chosen.clear();previewAdapter.notifyDataSetChanged();})));
        ListView previewList=new ListView(this);previewList.setId(R.id.playlistPreviewEntries);previewList.setAdapter(previewAdapter);content.addView(previewList,new LinearLayout.LayoutParams(-1,dp(220)));
        CheckBox download=check();download.setId(R.id.playlistDownloadOption);download.setText("导入后下载选中的歌曲");content.addView(download);
        ScrollView previewScroll=new ScrollView(this);previewScroll.addView(content);
        var dialog=new MaterialAlertDialogBuilder(this).setTitle("导入预览").setView(previewScroll).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();
        dialog.setOnDismissListener(d -> previewDestination=null);dialog.show();
        dialog.getButton(-1).setOnClickListener(v -> {
            if(name.getText().toString().isBlank()||chosen.isEmpty()){fail(new IllegalArgumentException("请输入歌单名称并至少选择一首歌曲"));return;}
            dialog.getButton(-1).setEnabled(false);var value=playlist.configured(playlist.id(),name.getText().toString().trim(),destination,recursive.isChecked())
                    .withEntries(playlist.entries().stream().filter(e -> chosen.contains(e.id())).toList());
            review(value,result -> {dialog.dismiss();saveImported(result,download.isChecked());},() -> dialog.getButton(-1).setEnabled(true));
        });
    }
    private void review(NamedPlaylist playlist,Consumer<NamedPlaylist> accept,Runnable aborted){
        int sdk=android.os.Build.VERSION.SDK_INT;
        if(AndroidPlaylistFiles.DEFAULT.equals(playlist.directory())&&sdk>=29
                &&!(sdk>=30&&android.os.Environment.isExternalStorageManager())){
            String permission=sdk>=33?android.Manifest.permission.READ_MEDIA_AUDIO:android.Manifest.permission.READ_EXTERNAL_STORAGE;
            if(androidx.core.content.ContextCompat.checkSelfPermission(this,permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                permissionGranted=() -> review(playlist,accept,aborted);permissionAborted=aborted;audioPermission.launch(permission);return;
            }
        }
        runtime.workspace.work(() -> new PlaylistDuplicates(runtime.files.scan(playlist.directory(),playlist.recursive())).match(playlist.entries()))
                .whenComplete((matches,error) -> runOnUiThread(() -> {
            if(isDestroyed()){aborted.run();return;}if(error!=null){fail(error);aborted.run();return;}if(matches.isEmpty()){accept.accept(playlist);return;}
            Map<String,Spinner> choices=new HashMap<>();Map<String,PlaylistDuplicates.Match> byId=new HashMap<>();LinearLayout rows=column();
            rows.addView(button("全部重新下载",() -> choices.values().forEach(s -> s.setSelection(0))));
            for(var match:matches){TextView label=label();label.setText(match.entry()+" · "+(match.confirmed()?"已存在":"疑似重复，请核对版本"));rows.addView(label);
                List<String> options=new ArrayList<>();options.add("仍然下载，保留两份");match.candidates().forEach(c -> options.add("使用本地："+directoryLabel(c.location())));
                Spinner choice=new Spinner(this);choice.setAdapter(spinnerAdapter(options));choice.setSelection(match.confirmed()?1:0);rows.addView(choice);choices.put(match.entry().id(),choice);byId.put(match.entry().id(),match);}
            ScrollView scroll=new ScrollView(this);scroll.addView(rows);
            var dialog=new MaterialAlertDialogBuilder(this).setTitle("发现重复歌曲").setView(scroll).setNegativeButton("取消",(d,w) -> aborted.run()).setPositiveButton("确认处理",(d,w) -> {
                accept.accept(playlist.withEntries(playlist.entries().stream().map(e -> {Spinner c=choices.get(e.id());int i=c==null?0:c.getSelectedItemPosition();
                    return i>0?e.reuseLocal(byId.get(e.id()).candidates().get(i-1).location()):e.pendingCopy(c==null?NamedPlaylist.State.MISSING:NamedPlaylist.State.REDOWNLOAD);}).toList()));
            }).create();dialog.setOnCancelListener(d -> aborted.run());dialog.show();
        }));
    }
    private void saveImported(NamedPlaylist playlist,boolean download){
        runtime.workspace.list().whenComplete((loaded,error) -> runOnUiThread(() -> {
            if(error!=null){fail(error);return;}var same=loaded.stream().filter(p -> p.source().equals(playlist.source())&&p.sourceId().equals(playlist.sourceId())).findFirst();
            if(same.isPresent())new MaterialAlertDialogBuilder(this).setTitle("已导入过此歌单").setMessage("更新采用本次选择的歌曲和平台顺序，音频文件保留。")
                    .setPositiveButton("更新已有",(d,w) -> save(playlist,same.get().id(),download)).setNeutralButton("另存一份",(d,w) -> save(playlist,null,download)).setNegativeButton("取消",null).show();
            else save(playlist,null,download);
        }));
    }
    private void save(NamedPlaylist p,String existing,boolean download){runtime.workspace.save(p,existing).whenComplete((saved,error) -> runOnUiThread(() -> {
        if(error!=null){fail(error);return;}current=saved;selected.clear();reload();if(download)start(saved,missingIds(saved));
    }));}
    private static Set<String> missingIds(NamedPlaylist p){Set<String> ids=new HashSet<>();for(var e:p.entries())if(e.state()!=NamedPlaylist.State.READY)ids.add(e.id());return ids;}
    private void start(NamedPlaylist p,Set<String> ids){runtime.workspace.work(() -> {runtime.workspace.downloads.start(p,ids);return null;}).exceptionally(e -> {runOnUiThread(() -> fail(e));return null;});}
    private void downloadMissing(){if(current==null)return;selected.clear();selected.addAll(missingIds(current));download();}
    private void download(){if(current==null||selected.isEmpty())return;NamedPlaylist p=current;
        reviewAndStart(p,p.withEntries(p.entries().stream().filter(e -> selected.contains(e.id())).map(NamedPlaylist.Entry::prepareDownload).toList()));
    }
    private void chooseDownload(){
        if(current==null||selected.size()!=1){fail(new IllegalArgumentException("请只勾选一首歌曲，再选择下载版本"));return;}
        NamedPlaylist p=current;var entry=p.entries().stream().filter(e -> selected.contains(e.id())).findFirst().orElse(null);
        if(entry==null){selected.clear();showEntries();fail(new IllegalArgumentException("所选歌曲已变化，请重新选择"));return;}
        status.setText("正在搜索软件中的下载渠道…");long version=++importRequest;
        runtime.workspace.candidates(entry).whenComplete((candidates,error) -> runOnUiThread(() -> {
            if(version!=importRequest||isDestroyed())return;showEntries();if(error!=null){fail(error);return;}if(candidates.isEmpty()){fail(new IllegalStateException("未找到可选择的歌曲，请在在线搜索中检查"));return;}
            String[] labels=candidates.stream().map(PlaylistSongMatcher::label).toArray(String[]::new);int[] chosen={0};
            TextView header=label();header.setPadding(dp(20),dp(20),dp(20),dp(10));header.setText("选择下载版本\n原歌单："+entry.label()+"\n请核对歌手、歌曲和版本后确认下载");
            new MaterialAlertDialogBuilder(this).setCustomTitle(header)
                .setSingleChoiceItems(spinnerAdapter(Arrays.asList(labels)),0,(dialog,index) -> chosen[0]=index).setNegativeButton("取消",null)
                .setPositiveButton("确认版本并下载",(dialog,which) -> reviewAndStart(p,p.withEntries(List.of(entry.selectVersion(candidates.get(chosen[0])))))).show();
        }));
    }
    private void reviewAndStart(NamedPlaylist p,NamedPlaylist picked){
        review(picked,result -> {Map<String,NamedPlaylist.Entry> chosen=new HashMap<>();result.entries().forEach(e -> chosen.put(e.id(),e));
            runtime.workspace.edit(p.id(),latest -> latest.withEntries(latest.entries().stream().map(e -> chosen.getOrDefault(e.id(),e)).toList()))
                    .whenComplete((v,error) -> runOnUiThread(() -> {if(error!=null){fail(error);return;}reload();start(p,missingIds(result));}));
        },() -> { });
    }
    private void pause(){if(current!=null){if(runtime.workspace.downloads.paused(current.id()))runtime.workspace.downloads.resume(current.id());else runtime.workspace.downloads.pause(current.id());}}
    private void cancel(){if(current!=null){String id=current.id();done(runtime.workspace.work(() -> {runtime.workspace.downloads.cancel(id);return null;}));}}
    private void rename(){if(current==null)return;String id=current.id();EditText name=input();name.setText(current.name());
        new MaterialAlertDialogBuilder(this).setTitle("歌单名称").setView(name).setNegativeButton("取消",null).setPositiveButton("保存",(d,w) -> {
            if(!name.getText().toString().isBlank())done(runtime.workspace.edit(id,p -> p.configured(p.id(),name.getText().toString().trim(),p.directory(),p.recursive())));
        }).show();
    }
    private void directory(){if(current==null)return;String id=current.id();new MaterialAlertDialogBuilder(this).setTitle("下载目录")
            .setItems(new String[]{"默认音乐目录","选择其他目录"},(d,which) -> {
                if(which==0)done(runtime.workspace.edit(id,p -> p.configured(p.id(),p.name(),AndroidPlaylistFiles.DEFAULT,p.recursive())));
                else{directoryPlaylistId=id;directoryPicker.launch(null);}
            }).show();}
    private void delete(){if(current==null)return;String id=current.id();new MaterialAlertDialogBuilder(this).setTitle("删除歌单").setMessage("只删除歌单记录，保留所有音频文件。")
            .setNegativeButton("取消",null).setPositiveButton("删除",(d,w) -> done(runtime.workspace.delete(id))).show();}
    private void move(int direction){if(current==null||adapter.focused==null)return;String id=adapter.focused;done(runtime.workspace.edit(current.id(),p -> {
        var rows=new ArrayList<>(p.entries());int position=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id))position=i;
        if(position>=0&&position+direction>=0&&position+direction<rows.size())Collections.swap(rows,position,position+direction);return p.withEntries(rows);
    }));}
    private void playback(boolean append){if(current==null)return;if(player==null){fail(new IllegalStateException("播放服务连接中"));return;}NamedPlaylist p=current;
        runtime.workspace.work(() -> runtime.files.register(p.entries().stream().map(NamedPlaylist.Entry::location).filter(runtime.files::readable).toList()))
                .whenComplete((tracks,error) -> runOnUiThread(() -> {
            if(isDestroyed()||player==null)return;
            if(error!=null){fail(error);return;}if(tracks.isEmpty()){fail(new IllegalStateException("歌单中尚无可读取的本地歌曲"));return;}
            List<MediaItem> items=tracks.stream().map(entry -> {
                Bundle extras=new Bundle();extras.putBoolean("namedPlaylistQueue",true);
                return new MediaItem.Builder().setMediaId(entry.location()).setUri(entry.storageType()==TrackEntry.StorageType.FILE?Uri.fromFile(entry.track().path().toFile()):Uri.parse(entry.location()))
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle(entry.track().title()).setArtist(entry.track().artist()).setExtras(extras).build()).build();
            }).toList();
            if(append)player.addMediaItems(items);else{player.setMediaItems(items,0,0);player.prepare();player.play();}
            status.setText(append?"已加入播放队列":"正在播放歌单中的本地歌曲");
        }));
    }
    private void done(CompletableFuture<?> future){future.whenComplete((v,error) -> runOnUiThread(() -> {if(error!=null)fail(error);else reload();}));}
    private void fail(Throwable error){if(isDestroyed())return;while(error.getCause()!=null)error=error.getCause();new MaterialAlertDialogBuilder(this).setTitle("操作失败").setMessage(Objects.toString(error.getMessage(),"请重试")).setPositiveButton("知道了",null).show();}
    private String directoryLabel(String value){
        if(AndroidPlaylistFiles.DEFAULT.equals(value))return android.os.Build.VERSION.SDK_INT<=28?"应用音乐目录 / downloads":"Music / music";
        if(value.startsWith("content:"))try{
            Uri uri=Uri.parse(value);String id=android.provider.DocumentsContract.isDocumentUri(this,uri)?android.provider.DocumentsContract.getDocumentId(uri):android.provider.DocumentsContract.getTreeDocumentId(uri);
            return id.replaceFirst("^primary:","内部存储 / ");
        }catch(RuntimeException ignored){return "媒体库 / "+Uri.parse(value).getLastPathSegment();}
        return value;
    }
    private TextView label(){TextView view=new androidx.appcompat.widget.AppCompatTextView(this);view.setTextColor(getColor(R.color.text_primary));return view;}
    private EditText input(){EditText view=new androidx.appcompat.widget.AppCompatEditText(this);view.setTextColor(getColor(R.color.text_primary));view.setHintTextColor(getColor(R.color.text_secondary));return view;}
    private CheckBox check(){CheckBox view=new androidx.appcompat.widget.AppCompatCheckBox(this);view.setTextColor(getColor(R.color.text_primary));
        view.setButtonTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{getColor(R.color.accent),getColor(R.color.text_secondary)}));return view;}
    private <T> ArrayAdapter<T> spinnerAdapter(List<T> items){return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items){
        private View color(View view){if(view instanceof TextView text)text.setTextColor(getColor(R.color.text_primary));view.setBackgroundColor(getColor(R.color.surface));return view;}
        @Override public View getView(int p,View recycled,ViewGroup parent){return color(super.getView(p,recycled,parent));}
        @Override public View getDropDownView(int p,View recycled,ViewGroup parent){return color(super.getDropDownView(p,recycled,parent));}
    };}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private LinearLayout row(View...views){LinearLayout box=new LinearLayout(this);for(var view:views)box.addView(view);return box;}
    private Button button(String text,Runnable action){var button=new com.google.android.material.button.MaterialButton(this);
        button.setText(text);button.setTextSize(12);button.setTextColor(getColor(R.color.text_primary));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.surface_alt)));
        button.setCornerRadius(dp(8));button.setOnClickListener(v -> action.run());return button;}
    private View scrollActions(View...views){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.addView(row(views));return scroll;}
    private final class EntryAdapter extends BaseAdapter {
        private List<NamedPlaylist.Entry> rows;private final Set<String> checked;private String focused;
        EntryAdapter(List<NamedPlaylist.Entry> rows,Set<String> checked){this.rows=rows;this.checked=checked;}
        void replace(List<NamedPlaylist.Entry> values){rows=values;notifyDataSetChanged();}
        @Override public int getCount(){return rows.size();}@Override public Object getItem(int p){return rows.get(p);}@Override public long getItemId(int p){return p;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            CheckBox view=recycled instanceof CheckBox?(CheckBox)recycled:check();var entry=rows.get(position);view.setOnCheckedChangeListener(null);
            view.setText(entry.toString());view.setChecked(checked.contains(entry.id()));view.setPadding(dp(6),dp(8),dp(6),dp(8));
            view.setOnCheckedChangeListener((v,value) -> {focused=entry.id();if(value)checked.add(entry.id());else checked.remove(entry.id());});return view;
        }
    }
}
