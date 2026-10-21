package local.quiethome;

public final class ThemeStyleTest {
    public static void main(String[] args){
        // Arbitrary picker colours must keep a readable selection label, including mid tones.
        for(int colour=0;colour<=0xFFFFFF;colour+=997){
            double a=ThemeStyle.luminance(colour),b=ThemeStyle.luminance(ThemeStyle.textColour(colour));
            double ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
            if(ratio<4.5)throw new AssertionError("Low selection contrast: "+Integer.toHexString(colour)+" "+ratio);
        }
        if(ThemeStyle.parseColour("#aBC123")!=0xABC123)throw new AssertionError("Hex case");
        for(String invalid:new String[]{"ABC123","#abc","#12345678","#zzzzzz","#12345",null}){
            try{ThemeStyle.parseColour(invalid);throw new AssertionError("Invalid hex accepted");}catch(IllegalArgumentException expected){}
        }
        for(int i=0;i<ThemeStyle.NAMES.length;i++){
            ThemeStyle a=ThemeStyle.defaults(i),b=a.colour(true,0x123456).transparency(100).density(0);
            if(b.accent!=0x123456||b.tint!=a.tint||b.mode!=i||b.transparency!=100||a.transparency==100||b.density!=0)throw new AssertionError("Preview mutated original");
        }
        System.out.println("PASS: colour validation, immutable previews and selection contrast over 16,000 colours");
    }
}
