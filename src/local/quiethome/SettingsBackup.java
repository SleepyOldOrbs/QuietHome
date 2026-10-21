package local.quiethome;

import org.json.*;

/** Side-effect-free schema validation before any restore changes or backup writes. */
final class SettingsBackup {
    private static long integer(JSONObject object,String key) throws JSONException {
        Object value=object.get(key);
        if(!(value instanceof Number))throw new JSONException("Expected number: "+key);
        double number=((Number)value).doubleValue();long whole=((Number)value).longValue();
        if(!Double.isFinite(number)||number!=whole)throw new JSONException("Expected integer: "+key);
        return whole;
    }
    private static void bool(JSONObject object,String key) throws JSONException {
        if(!(object.get(key) instanceof Boolean))throw new JSONException("Expected boolean: "+key);
    }
    private static String string(Object value,int max) throws JSONException {
        if(!(value instanceof String)||((String)value).length()>max)throw new JSONException("Invalid text");
        return (String)value;
    }
    private static void coordinate(JSONObject object,String key,double max) throws JSONException {
        Object value=object.get(key);
        if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||Math.abs(((Number)value).doubleValue())>max)
            throw new JSONException("Invalid coordinate");
    }
    static void validateWidgets(JSONObject state) throws JSONException {
        bool(state,"clock");bool(state,"weather");bool(state,"news");
        long source=integer(state,"source");if(source<0||source>=WidgetData.NEWS_NAMES.length)throw new JSONException("Unknown news edition");
        string(state.get("town"),120);coordinate(state,"latitude",90);coordinate(state,"longitude",180);
        if(state.has("fahrenheit"))bool(state,"fahrenheit");
        if(state.has("clockMode")){
            long mode=integer(state,"clockMode");if(mode<0||mode>2)throw new JSONException("Invalid clock mode");
        }
    }
    static void validate(JSONObject state) throws JSONException {
        if(integer(state,"schema")!=1)throw new JSONException("Unknown schema");
        bool(state,"compact");JSONArray favourites=state.getJSONArray("favourites");
        if(favourites.length()>100)throw new JSONException("Too many favourites");
        for(int i=0;i<favourites.length();i++){
            String pkg=string(favourites.get(i),255);
            if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new JSONException("Invalid package");
        }
        if(state.has("widgets"))validateWidgets(state.getJSONObject("widgets"));
        if(state.has("gallery")){
            JSONObject gallery=state.getJSONObject("gallery");
            long delay=integer(gallery,"artDelay");
            if(delay!=0&&delay!=30000&&delay!=60000&&delay!=120000)throw new JSONException("Invalid artwork delay");
            if(gallery.has("interval")&&!RotationTimer.supported(integer(gallery,"interval")))throw new JSONException("Invalid interval");
            if(gallery.has("fit"))bool(gallery,"fit");
            long position=integer(gallery,"position");if(position<0||position>1000)throw new JSONException("Invalid gallery position");
            JSONArray order=gallery.getJSONArray("order");if(order.length()>1000)throw new JSONException("Too many wallpapers");
            for(int i=0;i<order.length();i++)string(order.get(i),1024);
            if(gallery.has("image")&&!gallery.isNull("image"))string(gallery.get("image"),1024);
            if(gallery.has("focusedPackage"))string(gallery.get("focusedPackage"),255);
        }
    }
}
