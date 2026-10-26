package local.quiethome;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.util.UUID;
import org.json.*;

final class SleepTimer {
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"sleep-timer.json"));}
    private static AlarmManager manager(Context c){return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);}
    private static JSONObject read(Context c){try{return new JSONObject(new String(file(c).readFully(),"UTF-8"));}catch(Exception e){return null;}}
    private static PendingIntent pending(Context c,String token){
        Intent intent=new Intent(c,SleepTimerReceiver.class).setAction("local.quiethome.SLEEP").putExtra("token",token);
        return PendingIntent.getBroadcast(c,302,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static boolean permitted(Context c){return Build.VERSION.SDK_INT<31||manager(c).canScheduleExactAlarms();}
    static synchronized long remaining(Context c){
        JSONObject state=read(c);if(state==null)return 0;
        long now=SystemClock.elapsedRealtime(),due=state.optLong("due",-1);
        if(!permitted(c)||!ControlPolicy.liveTimer(due,now,state.optInt("boot",-1),boot(c))){cancel(c);return 0;}
        return Math.max(1,due-now);
    }
    static synchronized void cancel(Context c){manager(c).cancel(pending(c,""));file(c).delete();}
    static synchronized void arm(Context c,int minutes) throws Exception {
        if(minutes!=30&&minutes!=60&&minutes!=90)throw new IllegalArgumentException("Unknown timer duration");
        if(!permitted(c))throw new IOException("Allow timers in Android settings first");
        if(boot(c)<0)throw new IOException("This TV cannot safely identify a restart");
        cancel(c);
        long due=SystemClock.elapsedRealtime()+minutes*60000L;String token=UUID.randomUUID().toString();
        AtomicFile file=file(c);FileOutputStream out=null;
        try{
            out=file.startWrite();out.write(new JSONObject().put("due",due).put("boot",boot(c)).put("token",token).toString().getBytes("UTF-8"));file.finishWrite(out);
            manager(c).setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,due,pending(c,token));
        }catch(Exception e){if(out!=null)file.failWrite(out);cancel(c);throw e;}
    }
    static void fire(Context c,String token){
        // Consume once before I/O. A lost reply never triggers another standby request.
        synchronized(SleepTimer.class){
            JSONObject state=read(c);
            if(state==null||token==null||!token.equals(state.optString("token")))return;
            if(!ControlPolicy.due(state.optLong("due",-1),SystemClock.elapsedRealtime(),state.optInt("boot",-1),boot(c))){cancel(c);return;}
            file(c).delete();
        }
        try{
            String power=SonyClient.call(c,"system","getPowerStatus",new JSONArray(),"1.0").getJSONObject(0).getString("status");
            if("active".equals(power))SonyClient.call(c,"system","setPowerStatus",SonyClient.params("status",false),"1.0");
        }catch(Exception ignored){/* Never retry an uncertain power action or log credentials. */}
    }
}
