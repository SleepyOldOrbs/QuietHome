package local.quiethome;

/** Local appearance values, independent of Android rendering. Colours are opaque RGB. */
final class ThemeStyle {
    static final String[] NAMES={"Frosted Glass","Midnight","Gallery","Woodland","Meadow","Sakura","Autumn","Halloween","Summertime"};
    final int mode,accent,tint,transparency,density;
    ThemeStyle(int mode,int accent,int tint,int transparency,int density){
        if(mode<0||mode>=NAMES.length||accent<0||accent>0xFFFFFF||tint<0||tint>0xFFFFFF
            ||transparency<0||transparency>100||density<0||density>2)throw new IllegalArgumentException("Invalid appearance");
        this.mode=mode;this.accent=accent;this.tint=tint;this.transparency=transparency;this.density=density;
    }
    static ThemeStyle defaults(int mode){
        if(mode<0||mode>=NAMES.length)mode=1;
        int[] accents={0xD7F5ED,0xBDCCFF,0xFFE6BC,0xC6E2AD,0xFFE6AD,0xF5C6DA,0xF2BE7D,0xEAB4FF,0xFFE6A0};
        int[] tints={0x182933,0x101728,0x2C241F,0x192C24,0x302C20,0x332332,0x372519,0x24152F,0x21342D};
        int[] transparency={65,6,12,65,65,65,60,55,65};
        return new ThemeStyle(mode,accents[mode],tints[mode],transparency[mode],1);
    }
    ThemeStyle colour(boolean highlight,int rgb){return new ThemeStyle(mode,highlight?rgb:accent,highlight?tint:rgb,transparency,density);}
    ThemeStyle transparency(int value){return new ThemeStyle(mode,accent,tint,value,density);}
    ThemeStyle density(int value){return new ThemeStyle(mode,accent,tint,transparency,value);}
    static int parseColour(String value){
        if(value==null||!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use #RRGGBB");
        return Integer.parseInt(value.substring(1),16);
    }
    static double luminance(int rgb){
        double sum=0;double[] weights={0.2126,0.7152,0.0722};
        for(int i=0;i<3;i++){double c=((rgb>>(16-8*i))&255)/255.0;sum+=weights[i]*(c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4));}
        return sum;
    }
    static int textColour(int rgb){return luminance(rgb)>0.179?0xFF000000:0xFFFFFFFF;}
}

