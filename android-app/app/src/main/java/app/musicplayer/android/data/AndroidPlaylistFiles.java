package app.musicplayer.android.data;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.media.MediaMetadataRetriever;
import android.webkit.MimeTypeMap;
import app.musicplayer.model.Track;
import app.musicplayer.playlist.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Persistent document grants allow reuse without copying the user's existing audio. */
public final class AndroidPlaylistFiles implements PlaylistFiles,PlaylistDownloads.Publisher {
    public static final String DEFAULT="MEDIA_STORE";
    private final Context context;
    private final AndroidMusicDatabase database;
    public AndroidPlaylistFiles(Context context,AndroidMusicDatabase database){this.context=context.getApplicationContext();this.database=database;}
    @Override public boolean readable(String location){
        if(location==null||location.isBlank())return false;
        try{
            if(!location.startsWith("content:"))return Files.isRegularFile(Paths.get(location))&&Files.isReadable(Paths.get(location));
            try(var descriptor=context.getContentResolver().openFileDescriptor(Uri.parse(location),"r")){return descriptor!=null;}
        }catch(IOException|RuntimeException e){return false;}
    }
    @Override public List<PlaylistDuplicates.Local> scan(String destination,boolean recursive){
        Map<String,TrackEntry> files=new LinkedHashMap<>();for(var e:database.loadTracks())files.put(e.location(),e);
        if(destination.startsWith("content:"))scanTree(Uri.parse(destination),recursive,files);
        else if(DEFAULT.equals(destination)&&Build.VERSION.SDK_INT>=29){
            String relative="Music/music/";
            try(var cursor=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{"_id","_display_name","title","artist"},recursive?"relative_path like ?":"relative_path=?",
                    new String[]{recursive?relative+"%":relative},null)){
                while(cursor!=null&&cursor.moveToNext()){
                    String uri=ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)).toString();
                    Track track=new Track(Paths.get(cursor.getString(1)));track.updateMetadata(cursor.getString(2),cursor.getString(3));
                    files.putIfAbsent(uri,TrackEntry.mediaStore(track,System.currentTimeMillis(),uri,cursor.getString(1)));
                }
            }
        }else if(DEFAULT.equals(destination)){
            File folder=legacyFolder();try{Files.createDirectories(folder.toPath());try(var paths=Files.walk(folder.toPath(),recursive?Integer.MAX_VALUE:1)){
                paths.filter(Files::isRegularFile).filter(p -> audio(p.getFileName().toString())).forEach(path -> files.putIfAbsent(path.toString(),AndroidTrackFiles.entry(path.toFile(),System.currentTimeMillis())));
            }}catch(IOException e){throw new IllegalStateException("无法扫描下载目录",e);}
        }
        List<PlaylistDuplicates.Local> result=new ArrayList<>();
        files.forEach((location,e) -> {if(readable(location))result.add(new PlaylistDuplicates.Local(location,e.track().title(),e.track().artist(),""));});
        for(var p:database.loadPlaylists())for(var e:p.entries())if(readable(e.location()))result.addAll(PlaylistDuplicates.associations(e));
        return result;
    }
    private void scanTree(Uri tree,boolean recursive,Map<String,TrackEntry> result){
        ArrayDeque<String> dirs=new ArrayDeque<>();Set<String> seen=new HashSet<>();dirs.add(DocumentsContract.getTreeDocumentId(tree));
        int visited=0;while(!dirs.isEmpty()){
            String parent=dirs.remove();if(!seen.add(parent))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
            try(var rows=context.getContentResolver().query(children,new String[]{"document_id","_display_name","mime_type"},null,null,null)){
                while(rows!=null&&rows.moveToNext()){
                    if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                    if(++visited>100_000)throw new IllegalStateException("目录超过 100000 项，请选择更小的目录");
                    String id=rows.getString(0),name=rows.getString(1),mime=rows.getString(2);
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){if(recursive)dirs.add(id);continue;}
                    if(!audio(name))continue;Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                    result.putIfAbsent(uri.toString(),metadata(uri.toString(),name));
                }
            }
        }
    }
    private TrackEntry metadata(String location,String name){
        if(!location.startsWith("content:"))return AndroidTrackFiles.entry(new File(location),System.currentTimeMillis());
        Track track=new Track(Paths.get(name));MediaMetadataRetriever reader=new MediaMetadataRetriever();
        try{reader.setDataSource(context,Uri.parse(location));track.updateMetadata(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));}
        catch(RuntimeException ignored){ }finally{try{reader.release();}catch(IOException ignored){ }}
        return TrackEntry.mediaStore(track,System.currentTimeMillis(),location,name);
    }
    @Override public synchronized String publish(Path downloaded,NamedPlaylist.Entry entry,String destination) throws Exception{
        String requested=AndroidTrackFiles.safeName(downloaded.getFileName().toString());TrackEntry saved;Uri created=null;File createdFile=null;
        try{
            if(destination.startsWith("content:")){
                Uri tree=Uri.parse(destination),parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
                Set<String> names=new HashSet<>();try(var c=context.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),new String[]{"_display_name"},null,null,null)){
                    while(c!=null&&c.moveToNext())names.add(c.getString(0));}
                String name=unique(requested,names);created=DocumentsContract.createDocument(context.getContentResolver(),parent,mime(name),name);
                if(created==null)throw new IOException("所选目录无法创建文件");copy(downloaded,created);
                saved=TrackEntry.mediaStore(new Track(Paths.get(name)),System.currentTimeMillis(),created.toString(),name);
            }else if(Build.VERSION.SDK_INT>=29){
                Set<String> names=new HashSet<>();try(var c=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_display_name"},"relative_path=?",new String[]{"Music/music/"},null)){
                    while(c!=null&&c.moveToNext())names.add(c.getString(0));}
                String name=unique(requested,names);ContentValues value=new ContentValues();value.put("_display_name",name);value.put("mime_type",mime(name));value.put("relative_path","Music/music/");value.put("is_pending",1);
                created=context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,value);if(created==null)throw new IOException("媒体库无法创建文件");
                copy(downloaded,created);ContentValues complete=new ContentValues();complete.put("is_pending",0);context.getContentResolver().update(created,complete,null,null);
                saved=TrackEntry.mediaStore(new Track(Paths.get(name)),System.currentTimeMillis(),created.toString(),name);
            }else{
                createdFile=AndroidTrackFiles.reserve(legacyFolder(),requested);Files.copy(downloaded,createdFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
                saved=new TrackEntry(new Track(createdFile.toPath()),System.currentTimeMillis());
            }
            var metadata=entry.downloadTrack()==null?entry.track():entry.downloadTrack();
            saved.track().updateMetadata(metadata.title(),metadata.artist());database.saveTrack(saved);Files.deleteIfExists(downloaded);return saved.location();
        }catch(Exception error){
            if(created!=null)try{if(destination.startsWith("content:"))DocumentsContract.deleteDocument(context.getContentResolver(),created);else context.getContentResolver().delete(created,null,null);}catch(Exception ignored){ }
            if(createdFile!=null)try{Files.deleteIfExists(createdFile.toPath());}catch(Exception ignored){ }throw error;
        }
    }
    private void copy(Path file,Uri uri) throws IOException{
        try(var input=Files.newInputStream(file);var output=context.getContentResolver().openOutputStream(uri,"w")){
            if(output==null)throw new IOException("文件无法写入");byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
        }
    }
    public List<TrackEntry> register(List<String> locations){
        Map<String,TrackEntry> saved=new HashMap<>();for(var e:database.loadTracks())saved.put(e.location(),e);List<TrackEntry> result=new ArrayList<>();
        for(String location:locations)if(readable(location)){
            TrackEntry entry=saved.get(location);
            if(entry==null){String name=location.startsWith("content:")?"audio.mp3":Paths.get(location).getFileName().toString();
                if(location.startsWith("content:"))try(var c=context.getContentResolver().query(Uri.parse(location),new String[]{"_display_name"},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                entry=metadata(location,name);database.saveTrack(entry);
            }result.add(entry);
        }return List.copyOf(result);
    }
    private File legacyFolder(){File external=context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);return new File(external==null?context.getFilesDir():external,"downloads");}
    private static boolean audio(String name){return name!=null&&name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a|aac|wav|aif|aiff|ogg|flac)");}
    private static String mime(String name){int dot=name.lastIndexOf('.');String type=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(Locale.ROOT));return type==null?"audio/mpeg":type;}
    private static String unique(String requested,Set<String> names){int dot=requested.lastIndexOf('.');String base=dot>0?requested.substring(0,dot):requested,extension=dot>0?requested.substring(dot):"";String name=requested;int suffix=2;while(names.contains(name))name=base+" ("+suffix+++")"+extension;return name;}
}
