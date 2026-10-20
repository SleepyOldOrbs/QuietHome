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
        SettingsBackup.validate(base().put("favourites",new JSONArray().put("example.unavailable")));
        SettingsBackup.validate(base().put("widgets",widgets()).put("gallery",gallery())); // 1.4 fields only.
        SettingsBackup.validate(base().put("widgets",widgets().put("fahrenheit",true).put("clockMode",2))
            .put("gallery",gallery().put("interval",0).put("fit",true)));
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
