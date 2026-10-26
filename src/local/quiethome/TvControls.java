package local.quiethome;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.*;

/** On-demand controls; the sleep alarm is the only work scheduled outside Home. */
final class TvControls {
    interface Dim {int get();void set(int value);}
    private final Activity activity;private final Context app;
    private final PowerControls power;private final Runnable editDock;private final Dim dim;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean active,busy,closed,recovering;
    TvControls(Activity activity,PowerControls power,Runnable editDock,Dim dim){
        this.activity=activity;this.app=activity.getApplicationContext();this.power=power;this.editDock=editDock;this.dim=dim;
    }
    void active(boolean value){active=value;}
    void close(){closed=true;main.removeCallbacksAndMessages(null);}
    private boolean visible(){return active&&!closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private interface Work {Object get() throws Exception;}
    private interface Done {void accept(Object value) throws Exception;}
    private void run(Work work,Done done){
        if(busy){toast("Please wait for the TV.");return;}busy=true;
        SonyClient.WORKER.execute(()->{
            Object value=null;boolean ok=false;try{value=work.get();ok=true;}catch(Exception ignored){}
            final Object result=value;final boolean success=ok;
            main.post(()->{busy=false;if(!visible())return;
                if(!success){problem("The TV could not complete that action. Check its current state and power connection before trying again.");return;}
                try{done.accept(result);}catch(Exception e){problem("The TV returned settings this version cannot use.");}
            });
        });
    }
    void show(){
        if(!SonyClient.supported()){problem("TV controls need a compatible Sony BRAVIA. Art brightness is available in Home options → Artwork mode.");return;}
        if(SonyClient.key(app).isEmpty()){power.show(false);return;}
        long left=SleepTimer.remaining(app);
        new AlertDialog.Builder(activity).setTitle("TV controls")
            .setItems(new String[]{"Screen off — keep listening","Sound","Sleep timer"+(left>0?" · "+((left+59999)/60000)+" min left":" · Off"),"Art brightness","Pin HDMI inputs"},(d,i)->{
                if(i==0)screenOff();if(i==1)sound();if(i==2)timer();if(i==3)artBrightness();if(i==4)editDock.run();
            }).setNegativeButton("Close",null).show();
    }
    private void screenOff(){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Switch off the picture?")
            .setMessage("Music can keep playing if its app supports background playback. Press Home on the remote to restore the picture. This keeps the TV running.")
            .setPositiveButton("Screen off",(d,w)->run(()->{ScreenOff.start(app);return null;},v->{}))
            .setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->{Button b=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);b.setFocusableInTouchMode(true);b.requestFocus();});dialog.show();
    }
    boolean picturePending(){return ScreenOff.pending(app);}
    void home(){
        if(!ScreenOff.pending(app)||recovering)return;recovering=true;
        SonyClient.WORKER.execute(()->{
            boolean ok=false;try{ScreenOff.restore(app);ok=true;}catch(Exception ignored){}
            final boolean success=ok;main.post(()->{recovering=false;if(!success&&visible())problem("Picture restoration needs another try. Press Home again, or use Sony's picture settings.");});
        });
    }
    void input(String uri){
        if(ControlPolicy.hdmiPort(uri)<0){problem("That input is unavailable.");return;}
        if(SonyClient.key(app).isEmpty()){power.show(false);return;}
        run(()->SonyClient.call(app,"avContent","setPlayContent",SonyClient.params("uri",uri),"1.0"),v->{});
    }
    private JSONObject volume() throws Exception {
        JSONArray entries=SonyClient.call(app,"audio","getVolumeInformation",new JSONArray(),"1.0").getJSONArray(0);
        for(int i=0;i<entries.length();i++){
            JSONObject v=entries.getJSONObject(i);
            if("speaker".equals(v.optString("target"))&&v.has("minVolume")&&v.has("maxVolume")){
                int min=v.getInt("minVolume"),max=v.getInt("maxVolume"),now=v.getInt("volume");
                if(min>=0&&max>=min&&max<=1000&&now>=min&&now<=max)return v;
            }
        }throw new Exception("No controllable speaker volume");
    }
    private void sound(){run(this::volume,v->{
        JSONObject state=(JSONObject)v;
        new AlertDialog.Builder(activity).setTitle("Sound · "+state.getInt("volume")+" / "+state.getInt("maxVolume")+(state.getBoolean("mute")?" · Muted":""))
            .setItems(new String[]{"Volume down","Volume up",state.getBoolean("mute")?"Unmute":"Mute"},(d,i)->adjustSound(i))
            .setNegativeButton("Close",null).show();
    });}
    private void adjustSound(int action){run(()->{
        JSONObject current=volume();
        if(action==2)return SonyClient.call(app,"audio","setAudioMute",SonyClient.params("status",!current.getBoolean("mute")),"1.0");
        int next=Math.max(current.getInt("minVolume"),Math.min(current.getInt("maxVolume"),current.getInt("volume")+(action==0?-1:1)));
        return SonyClient.call(app,"audio","setAudioVolume",new JSONArray().put(new JSONObject().put("target","speaker").put("volume",Integer.toString(next))),"1.0");
    },v->sound());}
    void artBrightness(){
        int[] values={0,15,30,50};int selected=0;for(int i=0;i<values.length;i++)if(dim.get()==values[i])selected=i;
        new AlertDialog.Builder(activity).setTitle("Art brightness")
            .setSingleChoiceItems(new String[]{"Original","Gentle · 15% dimmer","Soft · 30% dimmer","Low · 50% dimmer"},selected,(d,i)->{dim.set(values[i]);d.dismiss();toast("Art brightness saved. Applies only while artwork is shown.");})
            .setNegativeButton("Cancel",null).show();
    }
    private void timer(){
        long left=SleepTimer.remaining(app);
        new AlertDialog.Builder(activity).setTitle(left>0?"Sleep timer · "+((left+59999)/60000)+" min left":"Sleep timer")
            .setItems(new String[]{left>0?"Cancel timer":"Off","30 minutes","60 minutes","90 minutes"},(d,i)->{
                if(i==0){SleepTimer.cancel(app);toast("Sleep timer off.");return;}
                if(!SleepTimer.permitted(app)){
                    new AlertDialog.Builder(activity).setTitle("Allow timed switch-off")
                        .setMessage("Android needs its Alarms & reminders permission to switch the TV off on time while another app is open. Enable it for Quiet Home, return here and choose a duration. No timer is running yet.")
                        .setPositiveButton("Open settings",(a,w)->{
                            try{activity.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+app.getPackageName())));}
                            catch(Exception e){problem("Open Android Settings → Apps → Special app access → Alarms & reminders, then allow Quiet Home.");}
                        }).setNegativeButton("Cancel",null).show();return;
                }
                int minutes=i*30;
                AlertDialog confirm=new AlertDialog.Builder(activity).setTitle("Turn off in "+minutes+" minutes?")
                    .setMessage("The TV will enter standby even if another app is playing. Cancel it here at any time. Restarting the TV cancels the timer.")
                    .setPositiveButton("Start timer",(a,w)->{try{SleepTimer.arm(app,minutes);toast("Sleep timer set for "+minutes+" minutes.");}catch(Exception e){problem("The timer could not be scheduled. No timer was started.");}})
                    .setNegativeButton("Cancel",null).create();
                confirm.setOnShowListener(a->{Button b=confirm.getButton(AlertDialog.BUTTON_NEGATIVE);b.setFocusableInTouchMode(true);b.requestFocus();});confirm.show();
            }).setNegativeButton("Close",null).show();
    }
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    private void problem(String message){new AlertDialog.Builder(activity).setTitle("TV controls").setMessage(message).setPositiveButton("OK",null).show();}
}
