package local.quiethome;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Bounded public feeds. No cookies, identifiers, redirects, or embedded web content. */
final class WidgetData {
    static final String[] NEWS_NAMES={"BBC UK News","BBC World News","BBC Technology"};
    static final String[] NEWS_URLS={
        "https://feeds.bbci.co.uk/news/uk/rss.xml",
        "https://feeds.bbci.co.uk/news/world/rss.xml",
        "https://feeds.bbci.co.uk/news/technology/rss.xml"};
    static final int MAX_BYTES=262144;
    static String fetch(String address) throws IOException {
        URL url=new URL(address);
        if(!url.getProtocol().equals("https") ||
            !(url.getHost().equals("api.open-meteo.com") || url.getHost().equals("feeds.bbci.co.uk") ||
                url.getHost().equals("geocoding-api.open-meteo.com")))
            throw new IOException("Unsupported feed");
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
        connection.setConnectTimeout(7000);connection.setReadTimeout(7000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent","QuietHome/1.4 (personal TV feed reader)");
        final long deadline=System.nanoTime()+20000000000L;
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Feed unavailable");
            if(connection.getContentLength()>MAX_BYTES)throw new IOException("Feed too large");
            try(InputStream in=connection.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] bytes=new byte[4096];int count;
                while((count=in.read(bytes))!=-1){
                    if(Thread.currentThread().isInterrupted() || System.nanoTime()>deadline)
                        throw new IOException("Feed timed out");
                    if(out.size()+count>MAX_BYTES)throw new IOException("Feed too large");
                    out.write(bytes,0,count);
                }
                return out.toString("UTF-8");
            }
        }finally{connection.disconnect();}
    }
    static JSONObject weather(String body) throws JSONException {
        JSONObject root=new JSONObject(body),current=root.getJSONObject("current"),daily=root.getJSONObject("daily");
        double temp=current.getDouble("temperature_2m");
        double high=daily.getJSONArray("temperature_2m_max").getDouble(0);
        double low=daily.getJSONArray("temperature_2m_min").getDouble(0);
        if(!Double.isFinite(temp)||!Double.isFinite(high)||!Double.isFinite(low) ||
            temp< -100 || temp>70 || high< -100 || high>70 || low< -100 || low>70)
            throw new JSONException("Invalid temperature");
        String time=current.getString("time");
        if(!time.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"))throw new JSONException("Invalid weather time");
        return new JSONObject().put("temperature",temp).put("high",high)
            .put("low",low).put("condition",condition(current.getInt("weather_code")))
            .put("time",time).put("at",System.currentTimeMillis());
    }
    static String condition(int code){
        switch(code){
            case 0:return "Clear";
            case 1:return "Mostly clear";
            case 2:return "Partly cloudy";
            case 3:return "Overcast";
            case 45:case 48:return "Fog";
            case 51:case 53:case 55:return "Drizzle";
            case 56:case 57:return "Freezing drizzle";
            case 61:case 63:case 65:return "Rain";
            case 66:case 67:return "Freezing rain";
            case 71:case 73:case 75:case 77:return "Snow";
            case 80:case 81:case 82:return "Rain showers";
            case 85:case 86:return "Snow showers";
            case 95:case 96:case 99:return "Thunderstorms";
            default:return "Conditions unavailable";
        }
    }
    static JSONObject news(String body,int source) throws Exception {
        if(body.length()>MAX_BYTES || body.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") ||
            body.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("Unsupported XML");
        XmlPullParser parser=Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        parser.setInput(new StringReader(body));
        JSONArray stories=new JSONArray();JSONObject item=null;
        int itemDepth=0;
        for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
            if(event==XmlPullParser.START_TAG){
                String name=parser.getName();
                if(name.equals("item")){item=new JSONObject();itemDepth=parser.getDepth();}
                else if(item!=null && parser.getDepth()==itemDepth+1 &&
                    (name.equals("title")||name.equals("description")||name.equals("link")||name.equals("pubDate"))){
                    String value=parser.nextText().trim();
                    if(value.length()>4000)throw new IOException("Feed text too large");
                    item.put(name,value);
                }
            }else if(event==XmlPullParser.END_TAG && parser.getName().equals("item") && item!=null){
                if(!item.optString("title").isEmpty() && validArticle(item.optString("link")))stories.put(item);
                item=null;
                if(stories.length()==5)break;
            }
        }
        if(stories.length()==0)throw new IOException("No headlines available");
        return new JSONObject().put("items",stories).put("source",source).put("at",System.currentTimeMillis());
    }
    static boolean validArticle(String link){
        try{
            URI uri=new URI(link);
            return "https".equals(uri.getScheme()) && uri.getUserInfo()==null &&
                ("www.bbc.co.uk".equals(uri.getHost())||"www.bbc.com".equals(uri.getHost()));
        }catch(Exception e){return false;}
    }
    static String age(long at,long now){
        if(at<=0)return "Not updated yet";
        long minutes=Math.max(0,(now-at)/60000);
        if(minutes<1)return "Updated just now";
        if(minutes<60)return "Updated "+minutes+" min ago";
        if(minutes<1440)return "Updated "+minutes/60+" hr ago";
        return "Updated "+minutes/1440+" days ago";
    }
}
