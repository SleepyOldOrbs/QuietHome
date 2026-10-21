package local.quiethome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.*;

/** Cached wallpaper glass; no runtime blur, network work or per-frame bitmap allocation. */
final class HomeTheme {
    static final String[] NAMES={"Frosted Glass","Midnight","Gallery"};
    final int mode;
    private Bitmap frost;
    private boolean fit;
    private final ArrayList<WeakReference<Drawable>> surfaces=new ArrayList<>();
    HomeTheme(int mode){this.mode=mode>=0&&mode<NAMES.length?mode:1;}
    int focus(){return mode==2?0xFFFFE6BC:mode==1?0xFFBDCCFF:0xD8D7F5ED;}
    int panel(){return mode==2?0xE02C241F:mode==1?0xF0101728:0x58182933;}
    void wallpaper(Bitmap bitmap,boolean fit){
        this.frost=bitmap;this.fit=fit;
        Iterator<WeakReference<Drawable>> it=surfaces.iterator();
        while(it.hasNext()){Drawable d=it.next().get();if(d==null)it.remove();else d.invalidateSelf();}
    }
    Drawable surface(View owner,boolean focused,float radius){
        Drawable d=new Surface(owner,focused,radius);surfaces.add(new WeakReference<>(d));return d;
    }
    private final class Surface extends Drawable {
        final View owner;final boolean focused;final float radius;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF area=new RectF(),imageArea=new RectF();final Path clip=new Path();
        final int[] location=new int[2],origin=new int[2];
        private LinearGradient sheen;
        Surface(View owner,boolean focused,float radius){this.owner=owner;this.focused=focused;this.radius=radius;}
        @Override protected void onBoundsChange(Rect bounds){
            sheen=new LinearGradient(0,bounds.top,0,bounds.bottom,0x16FFFFFF,0x08000000,Shader.TileMode.CLAMP);
        }
        @Override public void draw(Canvas canvas){
            area.set(getBounds());clip.reset();clip.addRoundRect(area,radius,radius,Path.Direction.CW);
            int saved=canvas.save();canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            // Glass must not start with an opaque undercoat: retain the real background below.
            if(mode!=0||frost==null){paint.setColor(0xFF101820);canvas.drawRect(area,paint);}
            if(mode==0&&frost!=null){
                View root=owner.getRootView();owner.getLocationInWindow(location);root.getLocationInWindow(origin);
                float w=root.getWidth(),h=root.getHeight();
                float scale=fit?Math.min(w/frost.getWidth(),h/frost.getHeight()):Math.max(w/frost.getWidth(),h/frost.getHeight());
                float iw=frost.getWidth()*scale,ih=frost.getHeight()*scale;
                float left=(w-iw)/2-location[0]+origin[0],top=(h-ih)/2-location[1]+origin[1];
                imageArea.set(left,top,left+iw,top+ih);paint.setColor(0xA0FFFFFF);canvas.drawBitmap(frost,null,imageArea,paint);
            }
            paint.setColor(focused?focus():panel());canvas.drawRect(area,paint);
            if(mode==0&&!focused&&sheen!=null){paint.setShader(sheen);canvas.drawRect(area,paint);paint.setShader(null);}
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(focused?3f:1.5f);
            paint.setColor(focused?0xFFFFFFFF:mode==2?0x706D5B43:0x608EAEB9);
            area.inset(1.5f,1.5f);canvas.drawRoundRect(area,radius,radius,paint);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
