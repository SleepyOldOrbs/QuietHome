package local.quiethome;

import android.content.res.AssetManager;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** One cached theme, one decoder, latest requested theme wins. No Activity retained. */
final class ThemeArtwork {
    private static final String[] FILES={"woodland","meadow","sakura","autumn","halloween","summertime"};
    static final class Artwork {
        final Bitmap frame,sprig;
        final Rect sprigBounds;
        Artwork(Bitmap frame,Bitmap sprig){this.frame=frame;this.sprig=sprig;this.sprigBounds=alphaBounds(sprig);}
        int bytes(){return (frame==null?0:frame.getAllocationByteCount())+(sprig==null?0:sprig.getAllocationByteCount());}
    }
    private static Rect alphaBounds(Bitmap image){
        if(image==null)return new Rect();
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];
        image.getPixels(pixels,0,w,0,0,w,h);
        int left=w,top=h,right=0,bottom=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((pixels[y*w+x]>>>24)>16){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        return right>left&&bottom>top?new Rect(Math.max(0,left-1),Math.max(0,top-1),Math.min(w,right+1),Math.min(h,bottom+1)):new Rect();
    }
    private static Bitmap decode(AssetManager assets,String file,int sample){
        try(InputStream input=assets.open("themes/"+file+".png")){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=sample;options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(input,null,options);
        }catch(IOException e){android.util.Log.w("QuietHomeTheme","Theme artwork unavailable");return null;}
    }
    private static Artwork cached;
    private static int cachedMode=-1,wanted=-1;
    private static boolean loading;
    private static final ArrayList<WeakReference<NatureBorder>> waiting=new ArrayList<>();
    static synchronized Artwork get(int mode){return mode==cachedMode?cached:null;}
    static synchronized void request(AssetManager assets,int mode,NatureBorder target){
        if(mode<3||mode>8)return;
        // Also change the desired mode on a cache hit, cancelling an obsolete pending switch.
        wanted=mode;
        if(get(mode)!=null)return;
        boolean found=false;
        for(WeakReference<NatureBorder> ref:waiting)if(ref.get()==target)found=true;
        if(!found)waiting.add(new WeakReference<>(target));
        if(loading)return;
        loading=true;
        new Thread(()->{
            for(;;){
                int requested;
                synchronized(ThemeArtwork.class){requested=wanted;}
                String name=FILES[requested-3];
                Artwork result=new Artwork(decode(assets,name,2),decode(assets,name+"-sprig",4));
                ArrayList<WeakReference<NatureBorder>> targets;
                synchronized(ThemeArtwork.class){
                    if(requested!=wanted)continue;
                    cached=result;cachedMode=requested;loading=false;
                    targets=new ArrayList<>(waiting);waiting.clear();
                }
                if(result!=null)android.util.Log.i("QuietHomeTheme","theme="+requested+" bitmap_bytes="+result.bytes());
                new Handler(Looper.getMainLooper()).post(()->{
                    for(WeakReference<NatureBorder> ref:targets){NatureBorder border=ref.get();if(border!=null)border.artworkReady();}
                });
                return;
            }
        },"QuietHome-theme-art").start();
    }
}
