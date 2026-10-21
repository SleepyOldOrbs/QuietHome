package local.quiethome;

final class VideoPolicy {
    static final long MAX_BYTES=80L*1024*1024;
    static final String[] MODES={"Off — still wallpapers","Home and Art mode","Art mode only","Home screen only"};
    static boolean validName(String name){return name!=null&&name.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,115}\\.mp4");}
    static boolean shouldPlay(int mode,boolean active,boolean artwork,boolean suppressed){
        return active&&!suppressed&&(mode==1||(mode==2&&artwork)||(mode==3&&!artwork));
    }
    static boolean supported(int width,int height,double fps,long bytes){
        return width>0&&height>0&&width<=1920&&height<=1080&&Double.isFinite(fps)&&fps>=0&&fps<=30&&bytes>0&&bytes<=MAX_BYTES;
    }
}
