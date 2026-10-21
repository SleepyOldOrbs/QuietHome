package local.quiethome;
public final class VideoPolicyTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(VideoPolicy.validName("Forest-123.mp4"));
        for(String bad:new String[]{null,"","../forest.mp4","/forest.mp4","https://example.com/a.mp4","a\\b.mp4",".hidden.mp4","a;touch.mp4","a.mp4\n"})check(!VideoPolicy.validName(bad));
        for(int mode=0;mode<4;mode++)for(boolean art:new boolean[]{false,true}){
            check(!VideoPolicy.shouldPlay(mode,false,art,false));
            check(!VideoPolicy.shouldPlay(mode,true,art,true));
            check(VideoPolicy.shouldPlay(mode,true,art,false)==(mode==1||mode==2&&art||mode==3&&!art));
        }
        check(VideoPolicy.supported(1920,1080,30,VideoPolicy.MAX_BYTES));
        check(VideoPolicy.supported(1280,720,0,1024)); // Extractor may omit frame rate.
        check(!VideoPolicy.supported(1921,1080,30,100));
        check(!VideoPolicy.supported(1920,1081,30,100));
        check(!VideoPolicy.supported(1920,1080,31,100));
        check(!VideoPolicy.supported(1920,1080,Double.NaN,100));
        check(!VideoPolicy.supported(1920,1080,Double.POSITIVE_INFINITY,100));
        check(!VideoPolicy.supported(0,1080,30,100));
        check(!VideoPolicy.supported(1920,1080,30,0));
        check(!VideoPolicy.supported(1920,1080,30,VideoPolicy.MAX_BYTES+1));
        System.out.println("PASS: local video paths, playback lifecycle policy and media bounds");
    }
}
