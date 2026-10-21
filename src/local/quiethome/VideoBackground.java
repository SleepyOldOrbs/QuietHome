package local.quiethome;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.*;

/** One muted local decoder; never owns audio focus or a wake lock. Stale work cannot restart it. */
final class VideoBackground implements TextureView.SurfaceTextureListener {
    interface Listener {void changed(boolean showing,String status);}
    final TextureView view;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final File directory;
    private final Listener listener;
    private MediaPlayer player;
    private Surface surface;
    private volatile long generation;
    private boolean wanted,closed,failed;
    private String name="";
    private int videoWidth,videoHeight;
    private Runnable timeout;
    VideoBackground(Context context,File directory,Listener listener){
        this.directory=directory;this.listener=listener;view=new TextureView(context);
        view.setOpaque(false);view.setAlpha(0);view.setFocusable(false);view.setClickable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setSurfaceTextureListener(this);
    }
    void update(String next,boolean enabled){
        if(closed)return;
        if(name.equals(next)&&wanted==enabled)return;
        name=next;wanted=enabled;failed=false;stop();start();
    }
    private void start(){
        if(!wanted||closed||failed||!view.isAvailable()||player!=null)return;
        final long ticket=++generation;final String selected=name;
        listener.changed(false,"Preparing video…");
        worker.execute(()->{
            String error=null;File file=null;
            try{
                if(!VideoPolicy.validName(selected))throw new IOException("Choose a local video first.");
                file=new File(directory,selected).getCanonicalFile();
                if(!file.getParentFile().equals(directory.getCanonicalFile())||!file.isFile())throw new IOException("Video missing. Still wallpaper is available.");
                try{inspect(file);}catch(Exception e){throw new IOException("Video unsupported or damaged. Use H.264 MP4 up to 1080p, 30 fps and 80 MiB.");}
            }catch(Exception e){error=e.getMessage()==null?"Video unavailable. Using still wallpaper.":e.getMessage();}
            final File ready=file;final String problem=error;
            main.post(()->{if(closed||generation!=ticket||!wanted)return;if(problem!=null){fail(problem);return;}prepare(ready,ticket);});
        });
    }
    private static void inspect(File file) throws IOException {
        if(file.length()==0||file.length()>VideoPolicy.MAX_BYTES)throw new IOException("Use an MP4 smaller than 80 MiB.");
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(file.getPath());boolean found=false;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);
                if(mime==null||!mime.startsWith("video/"))continue;
                double fps=0;
                if(format.containsKey(MediaFormat.KEY_FRAME_RATE)){
                    try{fps=format.getInteger(MediaFormat.KEY_FRAME_RATE);}catch(ClassCastException e){fps=format.getFloat(MediaFormat.KEY_FRAME_RATE);}
                }
                if(!"video/avc".equals(mime)||!VideoPolicy.supported(format.getInteger(MediaFormat.KEY_WIDTH),format.getInteger(MediaFormat.KEY_HEIGHT),fps,file.length()))
                    throw new IOException("Use H.264 MP4, up to 1080p and 30 fps.");
                found=true;
            }
            if(!found)throw new IOException("No video track. Using still wallpaper.");
        }finally{extractor.release();}
    }
    private void prepare(File file,long ticket){
        try{
            if(!view.isAvailable())return;
            MediaPlayer p=new MediaPlayer();player=p;surface=new Surface(view.getSurfaceTexture());
            p.setSurface(surface);p.setVolume(0,0);p.setScreenOnWhilePlaying(false);p.setLooping(true);
            p.setOnPreparedListener(mp->{
                if(player!=mp||generation!=ticket||!wanted)return;
                videoWidth=mp.getVideoWidth();videoHeight=mp.getVideoHeight();transform();
                try{mp.setVolume(0,0);mp.start();android.util.Log.i("QuietHomeVideo","started muted=true");}
                catch(RuntimeException e){fail("Video could not start. Using still wallpaper.");}
            });
            p.setOnVideoSizeChangedListener((mp,w,h)->{if(player==mp){videoWidth=w;videoHeight=h;transform();}});
            p.setOnInfoListener((mp,what,extra)->{
                if(player==mp&&generation==ticket&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
                    if(timeout!=null)main.removeCallbacks(timeout);view.setAlpha(1);listener.changed(true,"Playing silently");
                }return false;
            });
            p.setOnErrorListener((mp,what,extra)->{if(player==mp&&generation==ticket)fail("Video could not play. Using still wallpaper.");return true;});
            p.setDataSource(file.getPath());p.prepareAsync();
            timeout=()->{if(player==p&&generation==ticket)fail("Video timed out. Using still wallpaper.");};main.postDelayed(timeout,15000);
        }catch(Exception e){fail("Video could not open. Using still wallpaper.");}
    }
    private void transform(){
        if(videoWidth<=0||videoHeight<=0||view.getWidth()==0||view.getHeight()==0)return;
        float w=view.getWidth(),h=view.getHeight(),scale=Math.max(w/videoWidth,h/videoHeight);
        Matrix matrix=new Matrix();matrix.setScale(videoWidth*scale/w,videoHeight*scale/h,w/2,h/2);view.setTransform(matrix);
    }
    private void fail(String message){failed=true;stop();listener.changed(false,message);android.util.Log.w("QuietHomeVideo",message);}
    private void stop(){
        generation++;if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        if(player!=null){player.release();player=null;android.util.Log.i("QuietHomeVideo","released");}
        if(surface!=null){surface.release();surface=null;}
        view.setAlpha(0);listener.changed(false,"");
    }
    boolean hasFailed(){return failed;}
    void close(){if(closed)return;closed=true;wanted=false;stop();worker.shutdownNow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){start();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){stop();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
}
