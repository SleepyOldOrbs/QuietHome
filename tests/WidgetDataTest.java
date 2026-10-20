package local.quiethome;
import org.json.*;

public final class WidgetDataTest {
    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    private static void rejected(String xml) throws Exception{
        try{WidgetData.news(xml,0);throw new AssertionError("Unsafe XML accepted");}
        catch(java.io.IOException expected){}
    }
    public static void main(String[] args) throws Exception{
        String good="{\"current\":{\"temperature_2m\":12.6,\"weather_code\":63,\"time\":\"2026-10-20T16:15\"},\"daily\":{\"temperature_2m_max\":[15.2],\"temperature_2m_min\":[7.8]}}";
        JSONObject weather=WidgetData.weather(good);
        check(weather.getInt("temperature")==13,"temperature rounding");
        check(weather.getString("condition").equals("Rain"),"WMO code");
        check(weather.getInt("high")==15&&weather.getInt("low")==8,"daily range");
        try{WidgetData.weather(good.replace("12.6","null"));throw new AssertionError("null weather accepted");}
        catch(JSONException expected){}
        try{WidgetData.weather(good.replace("12.6","500"));throw new AssertionError("invalid temperature accepted");}
        catch(JSONException expected){}
        try{WidgetData.weather("{}");throw new AssertionError("missing weather accepted");}
        catch(JSONException expected){}
        String item="<item><title><![CDATA[Test & title]]></title><description>A published summary.</description><link>https://www.bbc.co.uk/news/articles/test</link><pubDate>Tue, 20 Oct 2026</pubDate></item>";
        String xml="<rss><channel><title>Channel</title>"+item+item+"</channel></rss>";
        JSONObject news=WidgetData.news(xml,1);
        check(news.getInt("source")==1,"source retained");
        check(news.getJSONArray("items").length()==2,"items parsed");
        check(news.getJSONArray("items").getJSONObject(0).getString("title").equals("Test & title"),"CDATA text");
        check(news.getJSONArray("items").getJSONObject(0).getString("description").equals("A published summary."),"summary retained");
        rejected("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+xml);
        rejected(xml.replace("https://www.bbc.co.uk","https://evil.example"));
        check(!WidgetData.validArticle("https://www.bbc.co.uk@evil.example/a"),"userinfo URL rejected");
        check(!WidgetData.validArticle("javascript:alert(1)"),"script URL rejected");
        check(!WidgetData.validArticle("http://www.bbc.co.uk/news"),"cleartext rejected");
        check(WidgetData.age(1000,31000).equals("Updated just now"),"fresh age");
        check(WidgetData.age(1000,121000).equals("Updated 2 min ago"),"cached age");
        check(WidgetData.condition(999).equals("Conditions unavailable"),"unknown WMO");
        try{WidgetData.fetch("https://evil.example/");throw new AssertionError("host allowed");}
        catch(java.io.IOException expected){}
        JSONObject settings=new JSONObject().put("clock",true).put("weather",true).put("news",false)
            .put("source",2).put("town","").put("latitude",0).put("longitude",0);
        HomeWidgets.validate(settings);
        try{HomeWidgets.validate(new JSONObject(settings.toString()).put("latitude",95));throw new AssertionError("bad coordinates accepted");}
        catch(JSONException expected){}
        try{HomeWidgets.validate(new JSONObject(settings.toString()).put("source",3));throw new AssertionError("bad source accepted");}
        catch(JSONException expected){}
        JSONObject live=WidgetData.news(WidgetData.fetch(WidgetData.NEWS_URLS[0]),0);
        check(live.getJSONArray("items").length()>0,"live BBC feed");
        JSONObject sample=WidgetData.weather(WidgetData.fetch("https://api.open-meteo.com/v1/forecast?latitude=51.5074&longitude=-0.1278&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"));
        check(sample.has("temperature"),"live weather sample");
        JSONObject towns=new JSONObject(WidgetData.fetch("https://geocoding-api.open-meteo.com/v1/search?name=London&count=5&language=en&format=json"));
        check(towns.getJSONArray("results").length()>0,"live town search");
        System.out.println("PASS: weather, RSS/CDATA, summaries, missing/invalid data, XML entity rejection, URL allowlist, age, unknown codes, settings validation, live BBC/weather/geocoding from TV");
    }
}
