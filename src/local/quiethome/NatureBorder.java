package local.quiethome;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** Generated alpha cutouts anchored to the screen and stationary panels; no animation timer. */
final class NatureBorder extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Picture picture;
    private int mode,density;
    private FrameLayout root;
    private View[] anchors=new View[0],protectedViews=new View[0];
    private Rect[] panels=new Rect[0],protectedRects=new Rect[0];
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener=this::updateBounds;
    NatureBorder(Context context,ThemeStyle style){
        super(context);mode=style.mode;density=style.density;
        setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void bind(FrameLayout root,View dock,View news,View weather,View header,View caption,View tools){
        this.root=root;anchors=new View[]{dock,news,weather};protectedViews=new View[]{header,caption,tools};
        panels=new Rect[]{new Rect(),new Rect(),new Rect()};
        protectedRects=new Rect[]{new Rect(),new Rect(),new Rect()};
    }
    void style(ThemeStyle style){if(mode!=style.mode||density!=style.density){mode=style.mode;density=style.density;record();invalidate();}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){record();}
    private boolean bounds(View view,Rect previous){
        Rect next=new Rect();
        if(root!=null&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0){
            view.getDrawingRect(next);
            // Padding below the caption is usable decoration space; protect the actual text.
            if(protectedViews.length>1&&view==protectedViews[1])next.bottom-=view.getPaddingBottom();
            if(protectedViews.length>2&&view==protectedViews[2])next.top+=view.getPaddingTop();
            root.offsetDescendantRectToMyCoords(view,next);
        }
        if(previous.equals(next))return false;previous.set(next);return true;
    }
    private void updateBounds(){
        boolean changed=false;
        for(int i=0;i<anchors.length;i++)changed|=bounds(anchors[i],panels[i]);
        for(int i=0;i<protectedViews.length;i++)changed|=bounds(protectedViews[i],protectedRects[i]);
        if(changed){record();invalidate();}
    }
    private RectF logical(Rect r){return new RectF(r.left*960f/getWidth(),r.top*540f/getHeight(),r.right*960f/getWidth(),r.bottom*540f/getHeight());}
    private void record(){
        picture=null;if(getWidth()==0||getHeight()==0||mode<3||density==0)return;
        ThemeArtwork.request(getContext().getApplicationContext().getAssets(),mode,this);
        ThemeArtwork.Artwork artwork=ThemeArtwork.get(mode);if(artwork==null||artwork.frame==null)return;
        Bitmap image=artwork.frame;
        picture=new Picture();Canvas c=picture.beginRecording(getWidth(),getHeight());
        c.scale(getWidth()/960f,getHeight()/540f);
        // Keep text, focus highlights and buttons unobscured, including after widget reflow.
        for(Rect r:protectedRects)if(!r.isEmpty()){RectF clear=logical(r);clear.inset(-3,-3);c.clipRect(clear,Region.Op.DIFFERENCE);}
        for(int i=0;i<panels.length;i++)if(!panels[i].isEmpty()){
            RectF clear=logical(panels[i]);clear.inset(i==0?8:10,i==0?8:10);
            Path interior=new Path();interior.addRoundRect(clear,i==0?14:8,i==0?14:8,Path.Direction.CW);
            c.clipPath(interior,Region.Op.DIFFERENCE);
        }
        float topScale=mode==8?(density==2?.65f:.5f):(density==2?.45f:.34f);
        float bottomScale=density==2?.55f:.42f;
        for(int row=0;row<2;row++)for(int col=0;col<2;col++){
            float factor=960f/image.getWidth()*(row==0?topScale:bottomScale);
            float width=image.getWidth()/2f*factor,height=image.getHeight()/2f*factor;
            cutout(c,image,row,col,col==0?0:960-width,row==0?0:540-height,factor);
        }
        if(artwork.sprig!=null&&!artwork.sprigBounds.isEmpty())for(int i=0;i<panels.length;i++)if(!panels[i].isEmpty()){
            RectF p=logical(panels[i]);
            // Continuous corner garlands: dock at both ends, news at its right edge,
            // weather at both corners. Size follows each panel, never the scrolled tiles.
            float width=i==0?(density==2?280:245):i==1?(density==2?220:190):p.width()*(density==2?.66f:.58f);
            width=Math.min(width,p.width()*.72f);
            float height=width*artwork.sprigBounds.height()/artwork.sprigBounds.width();
            float rise=density==2?.18f:.14f;
            float top=p.top-height*rise;
            // Leave the controls below the dock clear without squashing the artwork.
            if(i==0&&!protectedRects[2].isEmpty()){
                float limit=logical(protectedRects[2]).top-5;
                if(top+height>limit){height=(limit-p.top)/(1-rise);width=height*artwork.sprigBounds.width()/artwork.sprigBounds.height();top=p.top-height*rise;}
            }
            if(i!=1)garland(c,artwork,p.left-width*.13f,top,width,height,false);
            garland(c,artwork,p.right-width*.87f,top,width,height,true);
        }
        picture.endRecording();
    }
    private void garland(Canvas c,ThemeArtwork.Artwork artwork,float left,float top,float width,float height,boolean mirror){
        c.save();c.translate(left,top);
        if(mirror){c.translate(width,0);c.scale(-1,1);}
        c.drawBitmap(artwork.sprig,artwork.sprigBounds,new RectF(0,0,width,height),paint);
        c.restore();
    }
    private void cutout(Canvas c,Bitmap image,int row,int col,float left,float top,float factor){
        int w=image.getWidth(),h=image.getHeight();
        Rect source=new Rect(col*w/2,row*h/2,(col+1)*w/2,(row+1)*h/2);
        c.drawBitmap(image,source,new RectF(left,top,left+source.width()*factor,top+source.height()*factor),paint);
    }
    void artworkReady(){if(isAttachedToWindow()&&ThemeArtwork.get(mode)!=null){record();invalidate();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);record();}
    @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);picture=null;super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){if(picture!=null)canvas.drawPicture(picture);}
}
