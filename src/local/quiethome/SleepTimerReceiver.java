package local.quiethome;
import android.content.*;
/** Explicit, non-exported, one-shot receiver; no permanent background service. */
public final class SleepTimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!"local.quiethome.SLEEP".equals(intent.getAction()))return;
        final PendingResult pending=goAsync();
        final Context app=context.getApplicationContext();final String token=intent.getStringExtra("token");
        new Thread(()->{try{SleepTimer.fire(app,token);}finally{pending.finish();}},"QuietHome-sleep").start();
    }
}
