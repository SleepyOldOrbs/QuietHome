package local.quiethome;

public final class DisplayFormatTest {
    static void check(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        check(DisplayFormat.degrees(0,true),"32\u00b0F");
        check(DisplayFormat.degrees(100,true),"212\u00b0F");
        check(DisplayFormat.degrees(-40,true),"-40\u00b0F");
        check(DisplayFormat.degrees(12.6,true),"55\u00b0F"); // Convert before rounding.
        check(DisplayFormat.degrees(12.6,false),"13\u00b0C");
        check(DisplayFormat.degrees(-0.4,false),"0\u00b0C");
        check(DisplayFormat.degrees(Double.NaN,true),"\u2014");
        check(DisplayFormat.degrees(Double.POSITIVE_INFINITY,false),"\u2014");
        System.out.println("Display format regression tests passed");
    }
}
