package local.quiethome;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;

/** Recovery record is durable before changing Sony's panel state. */
final class ScreenOff {
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"screen-off.json"));}
    private static String previous(Context c){
        try{return new JSONObject(new String(file(c).readFully(),"UTF-8")).getString("previous");}catch(Exception e){return "";}
    }
    static boolean pending(Context c){return !previous(c).isEmpty();}
    static void restore(Context c) throws Exception {
        String previous=previous(c);if(previous.isEmpty())return;
        if(!previous.equals("off")&&!previous.equals("low")&&!previous.equals("high"))throw new IOException("Invalid picture recovery record");
        String current=SonyClient.call(c,"system","getPowerSavingMode",new JSONArray(),"1.0").getJSONObject(0).getString("mode");
        if(current.equals("pictureOff"))SonyClient.call(c,"system","setPowerSavingMode",SonyClient.params("mode",previous),"1.0");
        // Respect a newer manual setting rather than overwriting it.
        file(c).delete();
    }
    static void start(Context c) throws Exception {
        if(pending(c))restore(c);
        String mode=SonyClient.call(c,"system","getPowerSavingMode",new JSONArray(),"1.0").getJSONObject(0).getString("mode");
        if(!mode.equals("off")&&!mode.equals("low")&&!mode.equals("high"))throw new IOException("Picture is already off or unavailable");
        AtomicFile file=file(c);FileOutputStream out=null;
        try{out=file.startWrite();out.write(new JSONObject().put("previous",mode).toString().getBytes("UTF-8"));file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        // Retain the record even if the response is lost: the command may have acted.
        SonyClient.call(c,"system","setPowerSavingMode",SonyClient.params("mode","pictureOff"),"1.0");
    }
}
