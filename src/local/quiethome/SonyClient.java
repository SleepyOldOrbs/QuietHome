package local.quiethome;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.*;

/** A bounded on-TV connection. No caller can supply a host or URL. */
final class SonyClient {
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    static String key(Context context) {
        try {
            JSONObject saved=new JSONObject(new String(new AtomicFile(new File(context.getNoBackupFilesDir(),"sony-power.json")).readFully(),"UTF-8"));
            String key=saved.optString("secret");
            if("psk".equals(saved.optString("type"))&&PowerRequest.validKey(key))return key;
        }catch(Exception ignored){}
        return "";
    }
    static boolean supported(){return "Sony".equalsIgnoreCase(android.os.Build.MANUFACTURER)&&android.os.Build.VERSION.SDK_INT>=24;}
    static boolean allowed(String service,String method) {
        String pair=service+"/"+method;
        for(String valid:new String[]{"system/getPowerSavingMode","system/setPowerSavingMode","system/getPowerStatus","system/setPowerStatus",
            "audio/getVolumeInformation","audio/setAudioVolume","audio/setAudioMute","audio/getSoundSettings",
            "video/getPictureQualitySettings","video/setPictureQualitySettings","avContent/getCurrentExternalInputsStatus","avContent/setPlayContent"})
            if(valid.equals(pair))return true;
        return false;
    }
    static JSONArray call(Context context,String service,String method,JSONArray params,String version) throws Exception {
        return request(service,method,params,version,key(context));
    }
    static JSONArray request(String service,String method,JSONArray params,String version,String key) throws Exception {
        if(!allowed(service,method)||!("1.0".equals(version)||"1.1".equals(version)))throw new IOException("Unsupported TV request");
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1/sony/"+service).openConnection();
        try {
            c.setConnectTimeout(2000);c.setReadTimeout(2500);c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            if(PowerRequest.validKey(key))c.setRequestProperty("X-Auth-PSK",key);
            byte[] body=new JSONObject().put("method",method).put("id",1).put("params",params).put("version",version).toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            int status=c.getResponseCode();
            if(status!=200)throw new IOException(status==401||status==403?"TV connection needs its control password":"TV did not accept the request");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){if(bytes.size()+n>131072)throw new IOException("TV response too large");bytes.write(buffer,0,n);}
            }
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            if(response.has("error"))throw new IOException("TV does not support this operation in its current state");
            return response.getJSONArray("result");
        }finally{c.disconnect();}
    }
    static JSONArray params(String key,Object value) throws JSONException{return new JSONArray().put(new JSONObject().put(key,value));}
}
