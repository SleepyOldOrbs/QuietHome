package local.quiethome;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector botanicals, recorded once per size/style. No timer, bitmap or network. */
final class NatureBorder extends View {
    private final Paint paint=new Paint(3);
    private final Path path=new Path();
    private Picture picture;
    private int mode,density;
    NatureBorder(Context context,ThemeStyle style){
        super(context);mode=style.mode;density=style.density;
        setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void style(ThemeStyle style){if(mode!=style.mode||density!=style.density){mode=style.mode;density=style.density;record();invalidate();}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){record();}
    private void record(){
        picture=null;if(getWidth()==0||getHeight()==0||mode<3||density==0)return;
        picture=new Picture();Canvas c=picture.beginRecording(getWidth(),getHeight());
        c.scale(getWidth()/960f,getHeight()/540f);
        if(mode==3){ivy(c,false);ivy(c,true);}
        if(mode==4){meadow(c);}
        if(mode==5){sakura(c,false);sakura(c,true);}
        if(mode==6){autumn(c);}
        if(mode==7){halloween(c);}
        picture.endRecording();
    }
    @Override protected void onDraw(Canvas canvas){if(picture!=null)canvas.drawPicture(picture);}
    private void line(Canvas c,int colour,float width){paint.setColor(colour);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
    private void leaf(Canvas c,float x,float y,float size,float angle,int colour,boolean ivy){
        c.save();c.translate(x,y);c.rotate(angle);path.reset();path.moveTo(0,0);
        if(ivy){path.cubicTo(-size*.9f,-size*.15f,-size*.6f,-size*.6f,-size*.35f,-size*.55f);path.lineTo(0,-size);path.lineTo(size*.35f,-size*.55f);path.cubicTo(size*.8f,-size*.7f,size*.85f,-size*.1f,0,0);}
        else {path.cubicTo(-size*.65f,-size*.5f,-size*.3f,-size*.9f,0,-size);path.cubicTo(size*.6f,-size*.6f,size*.65f,-size*.15f,0,0);}
        paint.setColor(colour);c.drawPath(path,paint);path.reset();path.moveTo(0,-1);path.lineTo(0,-size*.8f);line(c,0x7065A268,.7f);c.restore();
    }
    private void ivy(Canvas c,boolean right){
        c.save();if(right){c.translate(960,0);c.scale(-1,1);}
        path.reset();path.moveTo(135,-6);path.cubicTo(83,18,9,2,20,75);path.cubicTo(36,105,7,147,17,density==2?286:214);line(c,0xDD71955E,2);
        int count=density==2?15:10;
        for(int i=0;i<count;i++){float y=27+i*16;float x=18+(float)Math.sin(i*.85)*5;leaf(c,x,y,15+i%3*3,i%2==0?-58:52,i%3==0?0xEF83A76D:0xE747744E,true);}
        for(int i=0;i<6;i++)leaf(c,35+i*17,10,17,i%2==0?125:70,0xE7658D53,true);
        c.restore();
    }
    private void flower(Canvas c,float x,float y,float radius,int colour,int petals){
        c.save();c.translate(x,y);paint.setColor(colour);
        for(int i=0;i<petals;i++){c.drawOval(-radius*.42f,-radius*1.1f,radius*.42f,1,paint);c.rotate(360f/petals);}
        paint.setColor(0xFFF2C564);c.drawCircle(0,0,radius*.28f,paint);c.restore();
    }
    private void meadow(Canvas c){
        int count=density==2?32:21;
        for(int i=0;i<count;i++){
            float x=12+i*936f/(count-1),height=9+(i*13%17),y=540-height;
            path.reset();path.moveTo(x-3,542);path.quadTo(x+5,530,x,y);line(c,0xE287A46A,1.1f);
            leaf(c,x,535,7,-50,0xD866945D,false);
            flower(c,x,y,4+i%3, i%3==0?0xFFF3E6B7:i%3==1?0xFFB9ABDD:0xFFF1BEBC,7);
        }
        for(int side=0;side<2;side++)for(int i=0;i<(density==2?7:4);i++){
            float x=side==0?18:942,y=510-i*26;
            leaf(c,x, y+9,12,side==0?40:-40,0xD88BA478,false);flower(c,x,y,6,0xFFFFEAC1,7);
        }
    }
    private void sakura(Canvas c,boolean right){
        c.save();if(right){c.translate(960,0);c.scale(-1,1);}
        path.reset();path.moveTo(-8,152);path.cubicTo(20,100,5,45,20,24);path.cubicTo(45,8,88,10,135,-4);line(c,0xE28C6874,3);
        path.reset();path.moveTo(20,24);path.quadTo(50,10,105,10);line(c,0xCEAA7F8E,1.5f);
        int count=density==2?16:10;
        for(int i=0;i<count;i++){
            float x=i<6?16+i*20:8+(i%3)*10,y=i<6?8+(i%3)*5:40+(i-6)*16;
            flower(c,x,y,7+i%3,i%2==0?0xFFF4C5D7:0xFCECDCE4,5);
            if(i%3==0)leaf(c,x+8,y+5,9,60,0xC595AE83,false);
        }
        c.restore();
    }
    private void autumnLeaf(Canvas c,float x,float y,float size,float angle,int colour){
        c.save();c.translate(x,y);c.rotate(angle);path.reset();path.moveTo(0,size*.7f);
        path.lineTo(-size*.08f,size*.24f);path.lineTo(-size*.72f,size*.16f);path.lineTo(-size*.5f,-size*.16f);
        path.lineTo(-size*.85f,-size*.53f);path.lineTo(-size*.36f,-size*.48f);path.lineTo(-size*.35f,-size*.85f);
        path.lineTo(-size*.12f,-size*.65f);path.lineTo(0,-size*1.12f);path.lineTo(size*.2f,-size*.65f);
        path.lineTo(size*.48f,-size*.82f);path.lineTo(size*.4f,-size*.38f);path.lineTo(size*.86f,-size*.5f);
        path.lineTo(size*.55f,-size*.14f);path.lineTo(size*.7f,size*.12f);path.lineTo(size*.08f,size*.24f);path.close();
        paint.setColor(colour);c.drawPath(path,paint);path.reset();path.moveTo(0,size*.6f);path.lineTo(0,-size*.85f);
        path.moveTo(0,-size*.1f);path.lineTo(-size*.5f,-size*.42f);path.moveTo(0,-size*.12f);path.lineTo(size*.5f,-size*.42f);line(c,0x88714629,.8f);c.restore();
    }
    private void autumn(Canvas c){
        int[] colours={0xF5DA943E,0xF5BB6035,0xF5D7B45B,0xF59F4A38};
        for(int side=0;side<2;side++){
            c.save();if(side==1){c.translate(960,0);c.scale(-1,1);}
            path.reset();path.moveTo(-2,205);path.cubicTo(23,140,8,55,20,30);path.cubicTo(38,10,100,10,153,0);line(c,0xBB96704C,1.5f);
            for(int i=0;i<(density==2?12:8);i++){
                float x=i<4?22+i*31:17+(i%2)*8,y=i<4?8+(i%2)*4:54+(i-4)*24;
                autumnLeaf(c,x,y,11+i%4*2,25+i*37,colours[i%4]);
            }
            c.restore();
        }
        for(int i=0;i<(density==2?22:12);i++)autumnLeaf(c,18+i*(924f/(density==2?21:11)),533-(i%3)*3,8+i%3,40+i*41,colours[i%4]);
    }
    private void bat(Canvas c,float x,float y,float scale){
        c.save();c.translate(x,y);c.scale(scale,scale);path.reset();path.moveTo(-2,-1);
        path.lineTo(-7,-8);path.quadTo(-14,-8,-20,-13);path.quadTo(-16,0,-18,6);path.quadTo(-10,0,-8,8);
        path.quadTo(-4,2,0,7);path.quadTo(4,2,8,8);path.quadTo(10,0,18,6);path.quadTo(16,0,20,-13);
        path.quadTo(14,-8,7,-8);path.lineTo(2,-1);path.lineTo(2,-7);path.lineTo(0,-4);path.lineTo(-2,-7);path.close();
        paint.setColor(0xF27D6495);c.drawPath(path,paint);c.restore();
    }
    private void pumpkin(Canvas c,float x,float y,float size){
        c.save();c.translate(x,y);c.scale(size,size);
        paint.setColor(0xFF527044);c.drawRoundRect(-2,-14,2,-7,2,2,paint);
        paint.setColor(0xFFE88E35);c.drawOval(-15,-10,15,12,paint);
        paint.setColor(0xFFF3AA49);c.drawOval(-8,-10,8,12,paint);
        paint.setColor(0xFF593229);path.reset();path.moveTo(-9,0);path.lineTo(-5,-5);path.lineTo(-2,0);path.close();
        path.moveTo(2,0);path.lineTo(5,-5);path.lineTo(9,0);path.close();c.drawPath(path,paint);
        path.reset();path.moveTo(-7,4);path.quadTo(0,12,7,4);path.quadTo(0,7,-7,4);c.drawPath(path,paint);c.restore();
    }
    private void halloween(Canvas c){
        for(int side=0;side<2;side++){
            c.save();if(side==1){c.translate(960,0);c.scale(-1,1);}
            paint.setColor(0xDDE3D3B0);c.drawCircle(27,26,14,paint);
            bat(c,76,19,.65f);bat(c,18,82,.7f);
            if(density==2){bat(c,126,14,.55f);bat(c,20,142,.55f);}
            pumpkin(c,22,516,1);pumpkin(c,58,528,.65f);
            if(density==2)pumpkin(c,89,530,.55f);
            c.restore();
        }
    }
}
