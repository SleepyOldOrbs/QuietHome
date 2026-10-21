package local.quiethome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import java.util.*;

/** Cached wallpaper glass; no runtime blur, network work or per-frame bitmap allocation. */
final class HomeTheme {
    static final String[] NAMES=ThemeStyle.NAMES;
    final int mode;
    ThemeStyle style;
    private Bitmap frost;
    private boolean fit,video;
    void video(boolean showing){if(video!=showing){video=showing;preview(style);}}
    private final ArrayList<WeakReference<Drawable>> surfaces=new ArrayList<>();
    HomeTheme(ThemeStyle style){this.style=style;mode=style.mode;}
    static ThemeStyle read(SharedPreferences prefs){
        ThemeStyle d=ThemeStyle.defaults(prefs.getInt("theme",1));
        try{return new ThemeStyle(d.mode,prefs.getInt("appearance_accent",d.accent),prefs.getInt("appearance_tint",d.tint),
            prefs.getInt("appearance_transparency",d.transparency),prefs.getInt("appearance_density",d.density));}
        catch(IllegalArgumentException e){return d;}
    }
    static void save(SharedPreferences prefs,ThemeStyle s){
        prefs.edit().putInt("theme",s.mode).putInt("appearance_accent",s.accent).putInt("appearance_tint",s.tint)
            .putInt("appearance_transparency",s.transparency).putInt("appearance_density",s.density).apply();
    }
    int focus(){return 0xFF000000|style.accent;}
    int focusText(){return ThemeStyle.textColour(style.accent);}
    int panelText(){return style.transparency<=35?ThemeStyle.textColour(style.tint):Color.WHITE;}
    int panel(){return (Math.round((100-style.transparency)*2.55f)<<24)|style.tint;}
    void preview(ThemeStyle next){
        style=next;
        for(WeakReference<Drawable> ref:surfaces){Drawable d=ref.get();if(d instanceof Surface){Surface s=(Surface)d;
            if(s.owner.getBackground()==s){
                int colour=s.focused?focusText():panelText();
                if(s.owner instanceof TextView)((TextView)s.owner).setTextColor(colour);
                else if(s.owner instanceof ViewGroup){ViewGroup group=(ViewGroup)s.owner;
                    for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof TextView)((TextView)group.getChildAt(i)).setTextColor(colour);
                }
                d.invalidateSelf();
            }
        }}
    }
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
            if(!video&&(mode==0||mode>=3)&&frost!=null){
                View root=owner.getRootView();owner.getLocationInWindow(location);root.getLocationInWindow(origin);
                float w=root.getWidth(),h=root.getHeight();
                float scale=fit?Math.min(w/frost.getWidth(),h/frost.getHeight()):Math.max(w/frost.getWidth(),h/frost.getHeight());
                float iw=frost.getWidth()*scale,ih=frost.getHeight()*scale;
                float left=(w-iw)/2-location[0]+origin[0],top=(h-ih)/2-location[1]+origin[1];
                imageArea.set(left,top,left+iw,top+ih);paint.setColor((Math.min(255,Math.round((100-style.transparency)*4.57f))<<24)|0xFFFFFF);canvas.drawBitmap(frost,null,imageArea,paint);
            }
            paint.setColor(focused?focus():panel());canvas.drawRect(area,paint);
            if((mode==0||mode>=3)&&!focused&&sheen!=null&&style.transparency<100){paint.setShader(sheen);canvas.drawRect(area,paint);paint.setShader(null);}
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(focused?3f:1.5f);
            paint.setColor(focused?0xFFFFFFFF:0x60000000|style.accent);
            area.inset(1.5f,1.5f);canvas.drawRoundRect(area,radius,radius,paint);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
