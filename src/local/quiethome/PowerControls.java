package local.quiethome;

import android.app.*;
import android.os.*;
import android.text.InputType;
import android.util.AtomicFile;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.*;

/** Password-authenticated Sony control, on this TV's loopback interface only. */
final class PowerControls {
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicFile credentials;
    private volatile HttpURLConnection connection;
    private volatile boolean closed;
    private boolean busy,active;
    private volatile String secret="";

    PowerControls(Activity activity) {
        this.activity=activity;
        credentials=new AtomicFile(new File(activity.getNoBackupFilesDir(),"sony-power.json"));
        try {
            JSONObject saved=new JSONObject(new String(credentials.readFully(),"UTF-8"));
            String value=saved.optString("secret");
            if("psk".equals(saved.optString("type"))&&PowerRequest.validKey(value))secret=value;
        }catch(Exception ignored){/* Missing/invalid setup requires explicit user action. */}
    }
    void setActive(boolean value){active=value;}
    void show(boolean restart) {
        if(busy){toast("Please wait for the TV to respond.");return;}
        if(!"Sony".equalsIgnoreCase(Build.MANUFACTURER)||Build.VERSION.SDK_INT<24) {
            new AlertDialog.Builder(activity).setTitle("Power controls")
                .setMessage("These buttons need a compatible Sony BRAVIA. Use your TV remote on this device.")
                .setPositiveButton("OK",null).show();return;
        }
        if(secret.isEmpty()){setup();return;}
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle(restart?"Restart the TV?":"Turn the TV off?")
            .setMessage(restart?"The TV will restart and return to Home. This interrupts anything playing."
                :"Put the TV into standby, just like the remote's power button. Use the remote to turn it on again.")
            .setPositiveButton(restart?"Restart":"Turn off",(d,w)->execute(restart))
            .setNeutralButton("Connection",(d,w)->connectionOptions())
            .setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->{
            Button cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            cancel.setFocusableInTouchMode(true);cancel.requestFocus();
        });dialog.show();
    }
    private void connectionOptions() {
        new AlertDialog.Builder(activity).setTitle("TV power connection")
            .setMessage("The TV control password stays on this TV and is excluded from backups. QuietHome does not change Sony's control settings.")
            .setPositiveButton("Update password",(d,w)->setup())
            .setNeutralButton("Forget connection",(d,w)->{secret="";credentials.delete();toast("Power connection forgotten.");})
            .setNegativeButton("Close",null).show();
    }
    private void setup() {
        EditText input=new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);input.setHint("TV control password");
        if(Build.VERSION.SDK_INT>=26)input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Connect the power buttons")
            .setMessage("Enter the password from Sony's IP control settings (Pre-Shared Key). Authentication must allow Normal and Pre-Shared Key. Keep an existing control password if other devices use it. This is not your Wi-Fi or Google password.")
            .setView(input).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final String value=input.getText().toString();
            if(!PowerRequest.validKey(value)){input.setError("Enter the TV control password (up to 128 visible characters)");return;}
            input.setText("");dialog.dismiss();
            run(()->{
                // This read requires authentication but does not change the TV.
                Response response=request("{\"method\":\"getSystemInformation\",\"id\":1,\"params\":[],\"version\":\"1.0\"}",value);
                if(response.ok()) {
                    FileOutputStream out=null;
                    try {
                        out=credentials.startWrite();
                        out.write(new JSONObject().put("type","psk").put("secret",value).toString().getBytes("UTF-8"));
                        credentials.finishWrite(out);secret=value;
                    }catch(Exception e){if(out!=null)credentials.failWrite(out);throw e;}
                }
                return response;
            },response->{
                if(response.ok())toast("Connected. Choose Power or Restart when you are ready.");
                else problem("The TV did not accept that password. Check Sony's IP control authentication and password, then try again.");
            },"Could not verify the TV control password. Check the TV's IP control settings and try again.");
        }));dialog.show();input.requestFocus();
    }
    private void execute(boolean restart) {
        // Never retry: a missing response may mean the TV already acted.
        run(()->request(PowerRequest.command(restart),secret),response->{
            if(response.ok())toast(restart?"Restart requested.":"Standby requested.");
            else problem(response.authError()?"The TV control password was rejected. Open Connection to update it."
                :"The TV did not accept that command. Use the remote, or check the power connection.");
        },"No reply from the TV. Check its screen before trying again; the command may already have arrived.");
    }
    private interface Work {Response get() throws Exception;}
    private interface Done {void accept(Response response);}
    private void run(Work work,Done done,String failure) {
        if(busy||closed||!active)return;busy=true;
        worker.execute(()->{
            Response result=null;
            try{result=work.get();}catch(Exception ignored){/* Never log passwords or response bodies. */}
            final Response response=result;
            main.post(()->{
                busy=false;if(closed||!active||activity.isFinishing()||activity.isDestroyed())return;
                if(response==null)problem(failure);else done.accept(response);
            });
        });
    }
    private Response request(String body,String key) throws Exception {
        if(closed)throw new IOException();
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1/sony/system").openConnection();
        connection=c;
        try {
            c.setConnectTimeout(4000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            c.setRequestProperty("X-Auth-PSK",key);
            byte[] bytes=body.getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[1024];int n;
                while((n=in.read(buffer))!=-1){if(result.size()+n>16384)throw new IOException();result.write(buffer,0,n);}}
            JSONObject json=result.size()==0?new JSONObject():new JSONObject(result.toString("UTF-8"));
            return new Response(status,json);
        }finally{c.disconnect();connection=null;}
    }
    private static final class Response {
        final int status;final JSONObject json;
        Response(int status,JSONObject json){this.status=status;this.json=json;}
        boolean ok(){return status==200&&!json.has("error")&&json.has("result");}
        boolean authError(){JSONArray e=json.optJSONArray("error");return status==401||status==403||(e!=null&&(e.optInt(0)==401||e.optInt(0)==403));}
    }
    private void problem(String message){new AlertDialog.Builder(activity).setTitle("TV power controls").setMessage(message).setPositiveButton("OK",null).show();}
    private void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    void close(){closed=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
