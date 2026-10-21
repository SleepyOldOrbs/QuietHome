package local.quiethome;

import android.content.res.AssetManager;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** One process-wide, sampled alpha bitmap. Decode off the UI thread; never retain an Activity. */
final class SummerArtwork {
    private static Bitmap cached;
    private static boolean loading;
    private static final ArrayList<WeakReference<NatureBorder>> waiting=new ArrayList<>();
    static synchronized Bitmap get(){return cached;}
    static synchronized void request(AssetManager assets,NatureBorder target){
        if(cached!=null)return;
        for(WeakReference<NatureBorder> ref:waiting)if(ref.get()==target)return;
        waiting.add(new WeakReference<>(target));
        if(loading)return;
        loading=true;
        new Thread(()->{
            Bitmap result=null;
            try(InputStream input=assets.open("themes/summertime.png")){
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=2;options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                result=BitmapFactory.decodeStream(input,null,options);
                if(result!=null)android.util.Log.i("QuietHomeTheme","summer_bitmap_bytes="+result.getAllocationByteCount());
            }catch(IOException e){android.util.Log.w("QuietHomeTheme","Summer artwork unavailable");}
            final Bitmap ready=result;
            new Handler(Looper.getMainLooper()).post(()->{
                ArrayList<WeakReference<NatureBorder>> targets;
                synchronized(SummerArtwork.class){cached=ready;loading=false;targets=new ArrayList<>(waiting);waiting.clear();}
                for(WeakReference<NatureBorder> ref:targets){NatureBorder border=ref.get();if(border!=null)border.artworkReady();}
            });
        },"QuietHome-summer-art").start();
    }
}
