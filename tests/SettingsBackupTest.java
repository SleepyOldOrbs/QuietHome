package local.quiethome;
import org.json.*;

public final class SettingsBackupTest {
    private static JSONObject base() throws JSONException {
        return new JSONObject().put("schema",1).put("favourites",new JSONArray()).put("compact",true);
    }
    private static JSONObject widgets() throws JSONException {
        return new JSONObject().put("clock",true).put("weather",false).put("news",false)
            .put("source",2).put("town","").put("latitude",0).put("longitude",0);
    }
    private static JSONObject gallery() throws JSONException {
        return new JSONObject().put("order",new JSONArray()).put("position",0).put("artDelay",30000);
    }
    private static void reject(JSONObject state) throws JSONException {
        String before=state.toString();
        try{SettingsBackup.validate(state);throw new AssertionError("Invalid backup accepted: "+state);}
        catch(JSONException expected){}
        if(!before.equals(state.toString()))throw new AssertionError("Validation mutated input");
    }
    public static void main(String[] args) throws Exception {
        SettingsBackup.validate(base()); // A blank dock is a valid portable backup.
        for(int dim:new int[]{0,15,30,50})SettingsBackup.validate(base().put("artDim",dim));
        for(Object dim:new Object[]{-1,10,51,100,0.5,"15",true,JSONObject.NULL})reject(base().put("artDim",dim));
        SettingsBackup.validate(base().put("favourites",new JSONArray().put("local.quiethome.input.hdmi1").put("example.app")));
        reject(base().put("favourites",new JSONArray().put("local.quiethome.input.hdmi33")));
        for(int mode=0;mode<4;mode++)SettingsBackup.validate(base().put("video",new JSONObject().put("mode",mode).put("file","forest.mp4")));
        SettingsBackup.validate(base().put("video",new JSONObject().put("mode",0).put("file","")));
        reject(base().put("video",JSONObject.NULL));reject(base().put("video",new JSONObject()));
        for(Object mode:new Object[]{-1,4,0.5,"1",true})reject(base().put("video",new JSONObject().put("mode",mode).put("file","forest.mp4")));
        for(Object file:new Object[]{"../forest.mp4","https://example.com/a.mp4",7,JSONObject.NULL})reject(base().put("video",new JSONObject().put("mode",1).put("file",file)));
        SettingsBackup.validate(base().put("favourites",new JSONArray().put("example.unavailable")));
        SettingsBackup.validate(base().put("widgets",widgets()).put("gallery",gallery())); // 1.4 fields only.
        SettingsBackup.validate(base().put("widgets",widgets().put("fahrenheit",true).put("clockMode",2))
            .put("gallery",gallery().put("interval",0).put("fit",true)));
        for(int theme=0;theme<ThemeStyle.NAMES.length;theme++)for(int layout=0;layout<3;layout++)
            SettingsBackup.validate(base().put("theme",theme).put("widgets",widgets().put("layout",layout).put("weatherLeft",true)));
        reject(base().put("theme",ThemeStyle.NAMES.length));reject(base().put("theme","0"));reject(base().put("theme",1.5));
        JSONObject appearance=new JSONObject().put("accent",0xC6E2AD).put("tint",0x192C24).put("transparency",65).put("density",1);
        SettingsBackup.validate(base().put("theme",3).put("appearance",appearance));
        reject(base().put("appearance",JSONObject.NULL));
        reject(base().put("appearance",new JSONObject()));
        for(String key:new String[]{"accent","tint","transparency","density"}){
            reject(base().put("appearance",new JSONObject(appearance.toString()).put(key,"1")));
            reject(base().put("appearance",new JSONObject(appearance.toString()).put(key,-1)));
            reject(base().put("appearance",new JSONObject(appearance.toString()).put(key,0.5)));
            reject(base().put("appearance",new JSONObject(appearance.toString()).put(key,0x1000000)));
        }
        reject(base().put("appearance",new JSONObject(appearance.toString()).put("transparency",101)));
        reject(base().put("appearance",new JSONObject(appearance.toString()).put("density",3)));
        reject(base().put("widgets",widgets().put("layout",3)));
        reject(base().put("widgets",widgets().put("layout","1")));
        reject(base().put("widgets",widgets().put("weatherLeft",1)));
        reject(base().put("schema","1"));reject(base().put("schema",1.5));reject(base().put("schema",2));
        reject(base().put("compact","false"));reject(base().put("favourites",new JSONArray().put("")));
        reject(base().put("widgets",JSONObject.NULL));reject(base().put("gallery","ignored before"));
        reject(base().put("widgets",widgets().put("weather","true")));
        reject(base().put("widgets",widgets().put("source",1.5)));
        reject(base().put("widgets",widgets().put("latitude","0")));
        reject(base().put("widgets",widgets().put("longitude",181)));
        reject(base().put("widgets",widgets().put("fahrenheit",0)));
        reject(base().put("widgets",widgets().put("clockMode",3)));
        reject(base().put("gallery",gallery().put("interval","60000")));
        reject(base().put("gallery",gallery().put("interval",60000.5)));
        reject(base().put("gallery",gallery().put("fit","false")));
        reject(base().put("gallery",gallery().put("order",new JSONArray().put(7))));
        reject(base().put("gallery",gallery().put("position",-1)));
        JSONArray many=new JSONArray();for(int i=0;i<101;i++)many.put("example.app");
        reject(base().put("favourites",many));
        System.out.println("PASS: old/new/empty/unavailable backups; strict types, bounds, malformed optional sections; validation has no mutations");
    }
}
