package local.quiethome;

import android.content.res.AssetManager;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Three-image, byte-bounded cache. Decode and frost preparation stay off the UI thread. */
final class WallpaperLoader {
    static final class Frame {
        final Bitmap image,frost;
        Frame(Bitmap image,Bitmap frost){this.image=image;this.frost=frost;}
        int bytes(){return image.getAllocationByteCount()+frost.getAllocationByteCount();}
    }
    interface Listener {void ready(String name,Frame frame,long elapsed,boolean cached);}
    private static final class Request {
        final String name,previous,next;final long started;
        Request(String name,String previous,String next){this.name=name;this.previous=previous;this.next=next;started=SystemClock.elapsedRealtime();}
    }
    private final AssetManager assets;
    private final Handler main;
    private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LatestRequest<Request> requests=new LatestRequest<>();
    private final LinkedHashMap<String,Frame> cache=new LinkedHashMap<>(4,.75f,true);
    private volatile boolean stopped;
    private boolean running;
    private static final int MAX_BYTES=14*1024*1024;
    WallpaperLoader(AssetManager assets,Handler main,Listener listener){this.assets=assets;this.main=main;this.listener=listener;}
    synchronized void request(String name,String previous,String next){
        if(stopped||name==null)return;
        requests.offer(new Request(name,previous,next));
        if(!running){running=true;worker.execute(this::pump);}
    }
    void cancel(){requests.clear();}
    synchronized void destroy(){stopped=true;cancel();worker.shutdownNow();}
    synchronized void trim(){if(!stopped)worker.execute(cache::clear);}
    private void pump(){
        LatestRequest.Item<Request> done=null;
        while(!stopped){
            LatestRequest.Item<Request> item=requests.latest();
            if(item==null||item==done){
                synchronized(this){if(requests.latest()==item){running=false;return;}}
                continue;
            }
            Request request=item.value;
            boolean hit=cache.containsKey(request.name);
            Frame frame=read(request.name);
            if(requests.current(item))main.post(()->{
                if(!stopped&&requests.current(item))listener.ready(request.name,frame,SystemClock.elapsedRealtime()-request.started,hit);
            });
            for(String neighbour:new String[]{request.next,request.previous}){
                if(stopped||!requests.current(item))break;
                if(neighbour!=null&&!neighbour.equals(request.name))read(neighbour);
            }
            done=item;
        }
        synchronized(this){running=false;}
    }
    private Frame read(String name){
        Frame found=cache.get(name);if(found!=null)return found;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream in=assets.open("wallpapers/"+name)){BitmapFactory.decodeStream(in,null,options);}
            options.inSampleSize=1;
            while(options.outWidth/options.inSampleSize>2560||options.outHeight/options.inSampleSize>1920)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap bitmap;
            try(InputStream in=assets.open("wallpapers/"+name)){bitmap=BitmapFactory.decodeStream(in,null,options);}
            if(bitmap==null)return null;
            float scale=Math.min(1f,Math.min(1920f/bitmap.getWidth(),1080f/bitmap.getHeight()));
            if(scale<1){Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);if(small!=bitmap)bitmap.recycle();bitmap=small;}
            Frame frame=new Frame(bitmap,frost(bitmap));cache.put(name,frame);
            int bytes=0;for(Frame value:cache.values())bytes+=value.bytes();
            Iterator<Map.Entry<String,Frame>> entries=cache.entrySet().iterator();
            while((cache.size()>3||bytes>MAX_BYTES)&&cache.size()>1&&entries.hasNext()){
                Map.Entry<String,Frame> entry=entries.next();bytes-=entry.getValue().bytes();entries.remove();
                // A displayed bitmap may still be referenced by an ImageView: let GC reclaim it safely.
            }
            return frame;
        }catch(IOException|RuntimeException e){android.util.Log.w("QuietHome","Wallpaper unavailable",e);return null;}
    }
    private static Bitmap frost(Bitmap image){
        float scale=Math.min(160f/image.getWidth(),160f/image.getHeight());
        int w=Math.max(1,Math.round(image.getWidth()*scale)),h=Math.max(1,Math.round(image.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(image,w,h,true);
        int[] pixels=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        int[] temp=new int[pixels.length];
        for(int pass=0;pass<3;pass++){
            blur(pixels,temp,w,h,true);blur(temp,pixels,w,h,false);
        }
        Bitmap result=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        if(small!=image)small.recycle();return result;
    }
    private static void blur(int[] input,int[] output,int w,int h,boolean horizontal){
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int r=0,g=0,b=0;
            for(int offset=-5;offset<=5;offset++){
                int xx=horizontal?Math.max(0,Math.min(w-1,x+offset)):x;
                int yy=horizontal?y:Math.max(0,Math.min(h-1,y+offset));int c=input[yy*w+xx];
                r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;
            }
            output[y*w+x]=0xff000000|((r/11)<<16)|((g/11)<<8)|(b/11);
        }
    }
}
