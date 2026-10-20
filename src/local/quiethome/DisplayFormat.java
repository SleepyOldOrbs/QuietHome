package local.quiethome;

/** Pure display conversions: caches remain in the provider's Celsius units. */
final class DisplayFormat {
    static String degrees(double celsius,boolean fahrenheit){
        if(!Double.isFinite(celsius))return "\u2014";
        return Math.round(fahrenheit?celsius*9.0/5.0+32:celsius)+(fahrenheit?"\u00b0F":"\u00b0C");
    }
}
