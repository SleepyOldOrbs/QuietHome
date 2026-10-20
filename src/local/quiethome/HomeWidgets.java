package local.quiethome;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Small foreground-only widgets; network work never shares the wallpaper worker. */
final class HomeWidgets {
    private final Activity activity;
    private final Handler handler;
    private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private boolean busy,destroyed,weatherFailed,newsFailed;
    private long nextAttempt;
    private final RequestGate feeds=new RequestGate(), searches=new RequestGate();
    private int source;
    private String town="";
    private double latitude,longitude;
    private JSONObject weather,news;
    private TextView weatherTitle,temperature,conditions,weatherStatus,newsTitle,headlines,newsStatus;
    private LinearLayout weatherCard,newsCard;
    final LinearLayout view;
    private final Runnable refreshTask=()->refresh(false);
    private static final long INTERVAL=30*60000L;

    HomeWidgets(Activity context,Handler main){
        activity=context;handler=main;prefs=context.getPreferences(0);
        try(InputStream in=context.getAssets().open("widgets.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;
            while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>4096)throw new IOException();}
            JSONObject config=new JSONObject(out.toString("UTF-8"));
            town=config.optString("town","");
            latitude=config.getDouble("latitude");longitude=config.getDouble("longitude");
            source=config.optInt("newsSource",0);
        }catch(Exception ignored){}
        town=prefs.getString("widget_town",town);
        try{
            latitude=Double.parseDouble(prefs.getString("widget_lat",Double.toString(latitude)));
            longitude=Double.parseDouble(prefs.getString("widget_lon",Double.toString(longitude)));
        }catch(NumberFormatException e){town="";}
        source=prefs.getInt("widget_news_source",source);if(source<0||source>2)source=0;
        weather=readCache("widget_weather_cache");
        if(weather!=null&&!town.equals(weather.optString("town")))weather=null;
        news=readCache("widget_news_cache");
        if(news!=null&&news.optInt("source",-1)!=source)news=null;
        view=new LinearLayout(activity);view.setGravity(Gravity.TOP);
        newsCard=card();newsTitle=line(newsCard,"",13,0xFFD8E6E4);
        headlines=line(newsCard,"",18,Color.WHITE);headlines.setMaxLines(4);
        headlines.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headlines.setPadding(0,dp(8),0,dp(8));headlines.setLineSpacing(dp(3),1);
        newsStatus=line(newsCard,"",11,0xFFCBD5D8);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(dp(510),-2);
        view.addView(newsCard,np);
        view.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        weatherCard=card();weatherTitle=line(weatherCard,"",13,0xFFD8E6E4);
        temperature=line(weatherCard,"",29,Color.WHITE);
        conditions=line(weatherCard,"",14,Color.WHITE);
        weatherStatus=line(weatherCard,"",11,0xFFCBD5D8);
        weatherStatus.setPadding(0,dp(7),0,0);
        view.addView(weatherCard,new LinearLayout.LayoutParams(dp(270),-2));
        newsCard.setOnClickListener(v->readNews());
        weatherCard.setOnClickListener(v->weatherDetails());
        render();
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable background(boolean focus){
        GradientDrawable d=new GradientDrawable();d.setColor(focus?0xF02A4146:0xC018242D);d.setCornerRadius(dp(16));
        if(focus)d.setStroke(dp(2),0xFFE1F4EF);return d;
    }
    private LinearLayout card(){
        LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18),dp(14),dp(18),dp(14));v.setBackground(background(false));
        v.setId(View.generateViewId());v.setFocusable(true);v.setClickable(true);
        v.setOnFocusChangeListener((view,focus)->view.setBackground(background(focus)));return v;
    }
    private TextView line(LinearLayout parent,String value,int size,int color){
        TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);
        parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    private JSONObject readCache(String key){
        try{return new JSONObject(prefs.getString(key,""));}catch(Exception e){return null;}
    }
    private boolean enabled(String key){return prefs.getBoolean("widget_"+key,"clock".equals(key));}
    private boolean fahrenheit(){return prefs.getBoolean("widget_fahrenheit",false);}
    private String degrees(String field){return DisplayFormat.degrees(weather.optDouble(field,Double.NaN),fahrenheit());}
    int clockMode(){return prefs.getInt("widget_clock_mode",0);}
    void clockOptions(Runnable changed){
        new AlertDialog.Builder(activity).setTitle("Clock format")
            .setSingleChoiceItems(new String[]{"Follow TV settings","12-hour","24-hour"},clockMode(),(d,i)->{
                prefs.edit().putInt("widget_clock_mode",i).apply();d.dismiss();changed.run();
            }).setNegativeButton("Cancel",null).show();
    }
    boolean clockEnabled(){return enabled("clock");}
    void linkDock(View dock){
        if(dock==null)return;
        newsCard.setNextFocusDownId(dock.getId());weatherCard.setNextFocusDownId(dock.getId());
        int up=enabled("news")?newsCard.getId():enabled("weather")?weatherCard.getId():View.NO_ID;
        dock.setNextFocusUpId(up);
    }
    void start(){active=true;render();refresh(false);}
    void stop(){active=false;invalidateRequests();handler.removeCallbacks(refreshTask);}
    private void invalidateRequests(){feeds.invalidate();searches.invalidate();nextAttempt=0;}
    void destroy(){destroyed=true;stop();worker.shutdownNow();}
    void render(){
        newsCard.setVisibility(enabled("news")?View.VISIBLE:View.GONE);
        weatherCard.setVisibility(enabled("weather")?View.VISIBLE:View.GONE);
        newsTitle.setText(WidgetData.NEWS_NAMES[source]+"  ·  bbc.co.uk/news");
        if(news==null)headlines.setText(newsFailed?"Headlines unavailable":"Loading headlines…");
        else{
            JSONArray list=news.optJSONArray("items");StringBuilder text=new StringBuilder();
            if(list!=null)for(int i=0;i<Math.min(2,list.length());i++){
                if(i>0)text.append("\n");text.append("• ").append(list.optJSONObject(i).optString("title"));
            }
            headlines.setText(text);
        }
        newsStatus.setText(status(news,newsFailed));
        weatherTitle.setText(town.isEmpty()?"Weather":town+"  ·  Weather");
        if(weather==null){
            temperature.setText("—");
            conditions.setText(town.isEmpty()?"Choose a town to get started":weatherFailed?"Weather unavailable":"Loading weather…");
        }else{
            temperature.setText(degrees("temperature")+"  ·  "+weather.optString("condition"));
            temperature.setTextSize(24);
            conditions.setText("High "+degrees("high")+"  /  Low "+degrees("low")+"");
        }
        weatherStatus.setText("Open-Meteo  ·  "+status(weather,weatherFailed));
    }
    private String status(JSONObject value,boolean failed){
        if(value==null)return failed?"Will retry when Home is open":"Waiting for update";
        long at=value.optLong("at",0);
        boolean stale=System.currentTimeMillis()-at>INTERVAL*2;
        return (failed||stale?"Cached · ":"")+WidgetData.age(at,System.currentTimeMillis());
    }
    private void refresh(boolean force){
        handler.removeCallbacks(refreshTask);
        if(!active||destroyed||busy||(!enabled("weather")&&!enabled("news")))return;
        long now=SystemClock.elapsedRealtime();
        if(!force && now<nextAttempt){handler.postDelayed(refreshTask,nextAttempt-now);return;}
        boolean fetchWeather=enabled("weather")&&!town.isEmpty() &&
            (force||weather==null||System.currentTimeMillis()-weather.optLong("at")>=INTERVAL);
        boolean fetchNews=enabled("news") &&
            (force||news==null||System.currentTimeMillis()-news.optLong("at")>=INTERVAL);
        if(!fetchWeather&&!fetchNews){nextAttempt=now+60000;handler.postDelayed(refreshTask,60000);return;}
        busy=true;final long request=feeds.ticket();final int requestedSource=source;final String requestedTown=town;
        final double requestedLat=latitude,requestedLon=longitude;
        worker.execute(()->{
            JSONObject newWeather=null,newNews=null;boolean wf=false,nf=false;
            if(active&&feeds.accepts(request)&&fetchWeather)try{
                String url=String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto",
                    requestedLat,requestedLon);
                newWeather=WidgetData.weather(WidgetData.fetch(url)).put("town",requestedTown);
            }catch(Exception e){wf=true;}
            if(active&&feeds.accepts(request)&&fetchNews)try{newNews=WidgetData.news(WidgetData.fetch(WidgetData.NEWS_URLS[requestedSource]),requestedSource);}
            catch(Exception e){nf=true;}
            final JSONObject w=newWeather,n=newNews;final boolean weatherError=wf,newsError=nf;
            handler.post(()->{
                busy=false;if(destroyed)return;
                if(!active||!feeds.accepts(request)){if(active)refresh(false);return;}
                SharedPreferences.Editor edit=prefs.edit();
                if(w!=null&&town.equals(requestedTown)){weather=w;edit.putString("widget_weather_cache",w.toString());}
                if(n!=null&&source==requestedSource){news=n;edit.putString("widget_news_cache",n.toString());}
                if(fetchWeather&&town.equals(requestedTown))weatherFailed=weatherError;
                if(fetchNews&&source==requestedSource)newsFailed=newsError;
                edit.apply();
                nextAttempt=SystemClock.elapsedRealtime()+(weatherError||newsError?300000:60000);
                if(source!=requestedSource||!town.equals(requestedTown))nextAttempt=SystemClock.elapsedRealtime();
                if(active){render();handler.postDelayed(refreshTask,nextAttempt-SystemClock.elapsedRealtime());}
                android.util.Log.i("QuietHome","Widget refresh: weather="+(w!=null?"ok":weatherError?"unavailable":"unchanged")+
                    ", news="+(n!=null?"ok":newsError?"unavailable":"unchanged"));
            });
        });
    }
    void options(Runnable changed){
        new AlertDialog.Builder(activity).setTitle("Widgets").setItems(
            new String[]{"Show or hide widgets","News edition","Read news summaries","Refresh weather and news","About these widgets","Weather location","Clear feed data and turn feeds off","Forget weather town","Temperature units"},
            (dialog,index)->{
                if(index==0){
                    boolean[] selected={enabled("clock"),enabled("weather"),enabled("news")};
                    new AlertDialog.Builder(activity).setTitle("Show on Home")
                        .setMultiChoiceItems(new String[]{"Clock and date (offline)","Weather (contacts Open-Meteo)","News (contacts BBC)"},selected,(d,i,value)->selected[i]=value)
                        .setPositiveButton("Save",(d,i)->{
                            invalidateRequests();
                            prefs.edit().putBoolean("widget_clock",selected[0]).putBoolean("widget_weather",selected[1])
                                .putBoolean("widget_news",selected[2]).apply();
                            render();changed.run();refresh(false);
                        }).setNegativeButton("Cancel",null).show();
                }
                if(index==1)new AlertDialog.Builder(activity).setTitle("News edition")
                    .setSingleChoiceItems(WidgetData.NEWS_NAMES,source,(d,i)->{
                        invalidateRequests();source=i;news=null;newsFailed=false;nextAttempt=0;
                        prefs.edit().putInt("widget_news_source",source).remove("widget_news_cache").apply();
                        d.dismiss();render();refresh(true);
                    }).setNegativeButton("Cancel",null).show();
                if(index==2)readNews();
                if(index==3){refresh(true);Toast.makeText(activity,busy?"Refreshing widgets…":"Enable weather or news first.",Toast.LENGTH_SHORT).show();}
                if(index==4)new AlertDialog.Builder(activity).setTitle("About these widgets")
                    .setMessage("Clock: this TV's time and time zone.\n\nWeather: "+town+", "+(fahrenheit()?"Fahrenheit":"Celsius")+". Modelled conditions from Open-Meteo (CC BY 4.0), not a local sensor. Location lookup uses GeoNames via Open-Meteo.\nhttps://open-meteo.com/\n\nNews: BBC's published headlines and summaries. No AI rewriting.\nhttps://www.bbc.co.uk/news\n\nFeeds refresh about every 30 minutes while Home is open. Last successful results stay on this TV. Providers receive your IP address; weather requests include the chosen town's coordinates. No location access, account, analytics or advertising SDKs.")
                    .setPositiveButton("Close",null).show();
                if(index==5)chooseTown();
                if(index==8)new AlertDialog.Builder(activity).setTitle("Temperature units")
                    .setSingleChoiceItems(new String[]{"Celsius","Fahrenheit"},fahrenheit()?1:0,(d,i)->{
                        prefs.edit().putBoolean("widget_fahrenheit",i==1).apply();d.dismiss();render();
                    }).setNegativeButton("Cancel",null).show();
                if(index==6)new AlertDialog.Builder(activity).setTitle("Clear feed data?")
                    .setMessage("Remove cached weather and news from this app and turn both feeds off. Your town and edition stay selected. Previously exported or OS backups are unaffected.")
                    .setPositiveButton("Clear and turn off",(d,i)->{
                        invalidateRequests();weather=null;news=null;weatherFailed=false;newsFailed=false;
                        prefs.edit().remove("widget_weather_cache").remove("widget_news_cache")
                            .putBoolean("widget_weather",false).putBoolean("widget_news",false).apply();
                        handler.removeCallbacks(refreshTask);render();changed.run();
                    }).setNegativeButton("Cancel",null).show();
                if(index==7)new AlertDialog.Builder(activity).setTitle("Forget weather town?")
                    .setMessage("Remove the selected town, coordinates and cached weather, and turn weather off. Previously exported or OS backups are unaffected.")
                    .setPositiveButton("Forget town",(d,i)->{
                        invalidateRequests();town="";latitude=0;longitude=0;weather=null;weatherFailed=false;
                        prefs.edit().putString("widget_town","").putString("widget_lat","0").putString("widget_lon","0")
                            .remove("widget_weather_cache").putBoolean("widget_weather",false).apply();
                        render();changed.run();refresh(false);
                    }).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Close",null).show();
    }
    private void weatherDetails(){
        if(town.isEmpty()){chooseTown();return;}
        String detail=weather==null?"Weather has not loaded yet.":degrees("temperature")+" · "+weather.optString("condition")+
            "\nHigh "+degrees("high")+" / Low "+degrees("low")+"\nModel time: "+weather.optString("time").replace('T',' ')+"\n"+status(weather,weatherFailed);
        new AlertDialog.Builder(activity).setTitle(town+" weather").setMessage(detail+"\n\nModelled weather by Open-Meteo (CC BY 4.0).\nhttps://open-meteo.com/")
            .setPositiveButton("Close",null).show();
    }
    private void chooseTown(){
        EditText input=new EditText(activity);input.setSingleLine(true);input.setText(town);
        input.setHint("Town or city, country");input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new AlertDialog.Builder(activity).setTitle("Weather location").setView(input)
            .setMessage("Enter a town or city. Search is sent to Open-Meteo; no GPS or device location is used.")
            .setPositiveButton("Search",(d,i)->{
                String query=input.getText().toString().trim();
                if(query.length()<2){Toast.makeText(activity,"Enter at least two letters.",Toast.LENGTH_LONG).show();return;}
                Toast.makeText(activity,"Finding matching towns…",Toast.LENGTH_SHORT).show();
                searches.invalidate();final long search=searches.ticket();
                worker.execute(()->{
                    JSONArray results=null;
                    if(!active||!searches.accepts(search))return;
                    try{
                        results=new JSONObject(WidgetData.fetch("https://geocoding-api.open-meteo.com/v1/search?name="+
                            java.net.URLEncoder.encode(query,"UTF-8")+"&count=5&language=en&format=json")).optJSONArray("results");
                    }catch(Exception ignored){}
                    final JSONArray matches=results;
                    handler.post(()->{
                        if(destroyed||!active||!searches.accepts(search))return;
                        if(matches==null||matches.length()==0){
                            Toast.makeText(activity,"No towns found. Check the connection and try again.",Toast.LENGTH_LONG).show();return;
                        }
                        String[] names=new String[matches.length()];
                        for(int n=0;n<names.length;n++){
                            JSONObject place=matches.optJSONObject(n);
                            names[n]=place.optString("name")+" · "+place.optString("admin1")+" · "+place.optString("country");
                        }
                        new AlertDialog.Builder(activity).setTitle("Choose your town").setItems(names,(dialog,index)->{
                            if(!active||!searches.accepts(search))return;
                            JSONObject place=matches.optJSONObject(index);
                            try{
                                double lat=place.getDouble("latitude"),lon=place.getDouble("longitude");
                                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("Invalid place");
                                invalidateRequests();town=place.getString("name");latitude=lat;longitude=lon;
                                weather=null;weatherFailed=false;nextAttempt=0;
                                prefs.edit().putString("widget_town",town).putString("widget_lat",Double.toString(lat))
                                    .putString("widget_lon",Double.toString(lon)).remove("widget_weather_cache").apply();
                                render();refresh(true);
                            }catch(JSONException e){Toast.makeText(activity,"That location is unavailable.",Toast.LENGTH_LONG).show();}
                        }).setNegativeButton("Cancel",null).show();
                    });
                });
            }).setNegativeButton("Cancel",null).show();
    }
    private void readNews(){
        JSONArray list=news==null?null:news.optJSONArray("items");
        if(list==null||list.length()==0){Toast.makeText(activity,"Headlines are not available yet.",Toast.LENGTH_LONG).show();return;}
        String[] titles=new String[list.length()];for(int i=0;i<titles.length;i++)titles[i]=list.optJSONObject(i).optString("title");
        new AlertDialog.Builder(activity).setTitle(WidgetData.NEWS_NAMES[source]+" · "+status(news,newsFailed))
            .setItems(titles,(d,i)->{
                JSONObject story=list.optJSONObject(i);
                new AlertDialog.Builder(activity).setTitle(story.optString("title"))
                    .setMessage(story.optString("description")+"\n\nBBC News · "+story.optString("pubDate")+"\n"+story.optString("link"))
                    .setPositiveButton("Close",null).setNeutralButton("Open source",(dialog,which)->{
                        String link=story.optString("link");
                        if(WidgetData.validArticle(link))try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}
                        catch(RuntimeException e){Toast.makeText(activity,"No web browser is available on this TV.",Toast.LENGTH_LONG).show();}
                    }).show();
            }).setNegativeButton("Close",null).show();
    }
    JSONObject state() throws JSONException{
        return new JSONObject().put("clock",enabled("clock")).put("weather",enabled("weather"))
            .put("news",enabled("news")).put("source",source).put("fahrenheit",fahrenheit()).put("clockMode",clockMode())
            .put("town",town).put("latitude",latitude).put("longitude",longitude);
    }
    static void validate(JSONObject state) throws JSONException {SettingsBackup.validateWidgets(state);}
    void restore(JSONObject state) throws JSONException {
        validate(state);invalidateRequests();
        source=state.getInt("source");news=null;weather=null;nextAttempt=0;
        town=state.getString("town");latitude=state.getDouble("latitude");longitude=state.getDouble("longitude");
        prefs.edit().putBoolean("widget_clock",state.getBoolean("clock")).putBoolean("widget_weather",state.getBoolean("weather"))
            .putBoolean("widget_news",state.getBoolean("news")).putBoolean("widget_fahrenheit",state.optBoolean("fahrenheit",false)).putInt("widget_clock_mode",state.optInt("clockMode",0)).putInt("widget_news_source",source)
            .putString("widget_town",town).putString("widget_lat",Double.toString(latitude)).putString("widget_lon",Double.toString(longitude))
            .remove("widget_news_cache").remove("widget_weather_cache").apply();
        render();refresh(false);
    }
}
