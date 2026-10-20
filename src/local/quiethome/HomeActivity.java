package local.quiethome;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvContract;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Remote-first home. Optional public-feed widgets; no services or analytics. */
public final class HomeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private ImageView background;
    private TextView clock, scene;
    private HomeWidgets widgets;
    private LinearLayout dock;
    private View allAppsButton;
    private View headerView, lowerView, shadeView;
    private String focusedPackage;
    private boolean compact;
    private boolean artworkVisible;
    private int swallowedKey = -1;
    private boolean swallowedTouch;
    private long artworkDelay = 30000;
    private final RotationTimer rotation=new RotationTimer(60000);
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<String> favourites = new ArrayList<>();
    private String[] wallpapers;
    private WallpaperDeck wallpaperDeck;
    private boolean active, loading;
    private static final String DEFAULTS = "com.google.android.youtube.tv,com.sony.dtv.tvx,org.jellyfin.androidtv,com.netflix.ninja";
    private final Runnable enterArtwork = () -> {
        if(active && hasWindowFocus() && artworkDelay>0) setArtworkVisible(true);
    };
    private static final class App {
        String name, pkg;
        Intent intent;
        Drawable icon;
    }
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!active) return;
            SimpleDateFormat clockFormat=new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(),DisplayFormat.clockSkeleton(widgets.clockMode(),android.text.format.DateFormat.is24HourFormat(HomeActivity.this))),Locale.getDefault());
            clockFormat.setTimeZone(TimeZone.getDefault());
            String time = clockFormat.format(new Date())+"\n"+
                new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date());
            if (!time.contentEquals(clock.getText())) {
                android.text.SpannableString display=new android.text.SpannableString(time);
                display.setSpan(new android.text.style.RelativeSizeSpan(0.72f),time.indexOf('\n')+1,time.length(),0);
                clock.setText(display);
            }
            widgets.render();
            if (wallpapers.length>1 && rotation.due(SystemClock.elapsedRealtime()) && !loading) nextScene();
            long clockDelay = 60000 - (System.currentTimeMillis() % 60000);
            long sceneDelay = wallpapers.length<2?clockDelay:Math.max(1000,rotation.delay(SystemClock.elapsedRealtime()));
            handler.postDelayed(this, Math.min(clockDelay, sceneDelay));
        }
    };
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        return t;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        long interval=getPreferences(0).getLong("wallpaper_interval",60000);
        rotation.setInterval(RotationTimer.supported(interval)?interval:60000,SystemClock.elapsedRealtime());
        scanApps();
        String stored = getPreferences(0).getString("favourites", DEFAULTS);
        favourites.addAll(Arrays.asList(stored.split(",")));
        compact = getPreferences(0).getBoolean("compact", false);
        focusedPackage = getPreferences(0).getString("last_dock_package", null);
        artworkDelay = getPreferences(0).getLong("artwork_delay", 30000);
        if(artworkDelay!=0 && artworkDelay!=30000 && artworkDelay!=60000 && artworkDelay!=120000) artworkDelay=30000;
        // Save the existing preferences before the first migration; never replace this snapshot.
        if (!getPreferences(0).getBoolean("refresh_1_1", false)) {
            try {
                saveBackup("before-update-1.1.json", false);
                compact = true;
                if (favourites.remove("org.smarttube.stable")) favourites.add(0, "org.smarttube.stable");
                persist();
                getPreferences(0).edit().putBoolean("refresh_1_1", true).apply();
            } catch (Exception e) {
                android.util.Log.e("QuietHome", "Migration backup failed; original layout preserved", e);
            }
        }
        try { wallpapers = getAssets().list("wallpapers"); } catch(IOException e) { wallpapers = new String[0]; }
        Arrays.sort(wallpapers);
        ArrayList<String> savedOrder=null;
        try{savedOrder=jsonStrings(new JSONArray(getPreferences(0).getString("wallpaper_order","[]")));}
        catch(JSONException ignored){}
        wallpaperDeck=new WallpaperDeck(wallpapers,savedOrder,
            getPreferences(0).getInt("wallpaper_position",0),
            getPreferences(0).getString("wallpaper_current",null),new Random());
        if(!getPreferences(0).getBoolean("gallery_1_3_backup",false)){
            try{saveBackup("before-gallery-1.3.json",false);getPreferences(0).edit().putBoolean("gallery_1_3_backup",true).apply();}
            catch(Exception e){android.util.Log.w("QuietHome","Gallery settings backup unavailable",e);}
        }
        build();
        loadScene();
    }
    private void scanApps() {
        apps.clear();
        PackageManager pm=getPackageManager();
        List<ResolveInfo> results=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        HashSet<String> seen=new HashSet<>();
        for(ResolveInfo r:results) {
            String pkg=r.activityInfo.packageName;
            if(pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            App a=new App(); a.pkg=pkg; a.name=r.loadLabel(pm).toString();
            if(pkg.equals("com.sony.dtv.tvx")) a.name="TV";
            if(pkg.equals("org.jellyfin.androidtv")) a.name="Jellyfin";
            a.intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(new ComponentName(pkg,r.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            a.icon=r.loadIcon(pm); apps.add(a);
        }
        Collections.sort(apps,(a,b)->a.name.compareToIgnoreCase(b.name));
    }
    private App find(String pkg) { for(App a:apps) if(a.pkg.equals(pkg)) return a; return null; }
    private void build() {
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,26,32));
        background=new ImageView(this); background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        shadeView=shade;
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x50000000,0x08000000,0xE80A1018}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        headerView=header;
        TextView title=text("Home",28,Color.WHITE); title.setTypeface(Typeface.create("sans-serif-medium",0));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        clock=text("",22,Color.WHITE); clock.setGravity(Gravity.RIGHT); header.addView(clock);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        hp.setMargins(dp(48),dp(30),dp(48),0); root.addView(header,hp);
        widgets=new HomeWidgets(this,handler);
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        wp.setMargins(dp(48),dp(96),dp(48),0);root.addView(widgets.view,wp);
        clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);

        LinearLayout lower=new LinearLayout(this); lower.setOrientation(LinearLayout.VERTICAL);
        lowerView=lower;
        scene=text(wallpapers.length==0?"Quiet Home":"A moment outside",15,0xFFE2ECE6);
        scene.setPadding(dp(4),0,0,dp(18)); lower.addView(scene);
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false); scroll.setClipChildren(false);
        scroll.setClipToPadding(false); scroll.setPadding(dp(10),dp(10),dp(10),dp(10));
        scroll.setBackground(rounded(0x90212B32,22));
        dock=new LinearLayout(this); dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false); dock.setClipToPadding(false);
        scroll.addView(dock); lower.addView(scroll,new LinearLayout.LayoutParams(-1,dp(132)));
        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(0,dp(14),0,0); tools.setGravity(Gravity.CENTER_VERTICAL);
        allAppsButton=addTool(tools,"All apps",()->showApps(false));
        addTool(tools,"Edit dock",()->editDock());
        addTool(tools,"Inputs",()->showInputs());
        addTool(tools,"Wallpaper",()->nextScene());
        addTool(tools,"Home options",()->homeOptions());
        addTool(tools,"Settings",()->launchSettings());
        TextView hint=text("Hold OK to arrange",11,0xFFC0C8CE);
        hint.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tools.addView(hint,new LinearLayout.LayoutParams(0,dp(40),1));
        lower.addView(tools);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        lp.setMargins(dp(48),0,dp(48),dp(34)); root.addView(lower,lp);
        setContentView(root);
        rebuildDock();
    }
    private View addTool(LinearLayout parent,String label,Runnable action) {
        TextView b=text(label,14,0xFFE2E7EB); b.setGravity(Gravity.CENTER);
        b.setId(View.generateViewId());
        b.setPadding(dp(12),0,dp(12),0); b.setFocusable(true); b.setClickable(true);
        b.setBackground(rounded(0x60232C34,12));
        b.setOnFocusChangeListener((v,f)->{
            b.setBackground(rounded(f?0xFFF0F5F1:0x60232C34,12));
            b.setTextColor(f?0xFF13201E:0xFFE2E7EB);
        });
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));
        p.rightMargin=dp(8); parent.addView(b,p);
        return b;
    }
    private void rebuildDock() {
        String wantedFocus=focusedPackage;
        dock.removeAllViews();
        for(String pkg:favourites) {
            App a=find(pkg); if(a==null) continue;
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER); card.setPadding(dp(12),dp(10),dp(12),dp(8));
            card.setFocusable(true); card.setFocusableInTouchMode(true); card.setClickable(true);
            card.setTag(pkg); card.setId(View.generateViewId());
            card.setNextFocusDownId(allAppsButton.getId());
            widgets.linkDock(card);
            card.setBackground(rounded(0xD9212933,15));
            ImageView icon=new ImageView(this); icon.setImageDrawable(a.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon,new LinearLayout.LayoutParams(dp(compact?48:58),dp(compact?48:58)));
            TextView label=text(a.name,compact?14:15,Color.WHITE); label.setGravity(Gravity.CENTER);
            label.setSingleLine(true); label.setPadding(0,dp(5),0,0);
            card.addView(label,new LinearLayout.LayoutParams(-1,-2));
            card.setOnFocusChangeListener((v,f)->{
                if(f) {
                    focusedPackage=pkg; allAppsButton.setNextFocusUpId(card.getId());
                    widgets.linkDock(card);
                    getPreferences(0).edit().putString("last_dock_package",pkg).apply();
                }
                GradientDrawable bg=rounded(f?0xFFF1F5F2:0xD9212933,15);
                if(f) bg.setStroke(dp(2),0xFFFFFFFF);
                card.setBackground(bg); label.setTextColor(f?0xFF14201F:Color.WHITE);
                card.animate().scaleX(f?1.035f:1).scaleY(f?1.035f:1).setDuration(120).start();
            });
            card.setOnClickListener(v->open(a));
            card.setOnLongClickListener(v->{arrangeApp(pkg);return true;});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(compact?118:148),dp(108));
            cp.setMargins(dp(4),0,dp(4),0); dock.addView(card,cp);
        }
        View focus=wantedFocus==null?null:dock.findViewWithTag(wantedFocus);
        if(focus!=null)focus.requestFocus();
        else if(dock.getChildCount()>0) dock.getChildAt(0).requestFocus();
    }
    private void open(App a) {
        try{startActivity(a.intent);}catch(RuntimeException e){
            Toast.makeText(this,"This app is unavailable. Check Settings.",Toast.LENGTH_LONG).show();
        }
    }
    private void launchSettings(){
        try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
        catch(RuntimeException e){Toast.makeText(this,"Use the Settings button on your remote.",Toast.LENGTH_LONG).show();}
    }
    private void showApps(boolean unused) {
        scanApps();
        String[] names=new String[apps.size()];
        for(int i=0;i<apps.size();i++) names[i]=apps.get(i).name;
        new AlertDialog.Builder(this).setTitle("All apps")
            .setItems(names,(d,i)->open(apps.get(i))).setNegativeButton("Close",null).show();
    }
    private void editDock() {
        scanApps();
        String[] names=new String[apps.size()];
        boolean[] selected=new boolean[apps.size()];
        final ArrayList<String> chosen=new ArrayList<>(favourites);
        for(int i=0;i<apps.size();i++){names[i]=apps.get(i).name;selected[i]=chosen.contains(apps.get(i).pkg);}
        new AlertDialog.Builder(this).setTitle("Choose your dock apps")
            .setMultiChoiceItems(names,selected,(d,i,on)->{
                String p=apps.get(i).pkg;
                if(on&&!chosen.contains(p))chosen.add(p); else if(!on)chosen.remove(p);
            })
            .setPositiveButton("Save",(d,i)->{
                if(chosen.isEmpty()){Toast.makeText(this,"Keep at least one app in the dock.",Toast.LENGTH_LONG).show();return;}
                favourites.clear(); favourites.addAll(chosen);
                persist();
                rebuildDock();
            }).setNegativeButton("Cancel",null).show();
    }
    private void persist() {
        getPreferences(0).edit().putString("favourites",android.text.TextUtils.join(",",favourites))
            .putBoolean("compact",compact).apply();
    }
    private void homeOptions() {
        new AlertDialog.Builder(this).setTitle("Home options").setItems(
            new String[]{"Arrange dock","Tile size","Save backup","Restore backup","Artwork mode","Widgets","Clock format","Wallpaper settings"},(d,i)->{
                if(i==0)arrangeDock();
                if(i==1)new AlertDialog.Builder(this).setTitle("Tile size")
                    .setSingleChoiceItems(new String[]{"Compact","Standard"},compact?0:1,(dialog,index)->{
                        compact=index==0;persist();rebuildDock();dialog.dismiss();
                    }).setNegativeButton("Cancel",null).show();
                if(i==2) {
                    try { saveBackup("saved-"+System.currentTimeMillis()+".json",true); }
                    catch(Exception e){toast("Could not save backup. Your settings are unchanged.");}
                }
                if(i==3)chooseBackup();
                if(i==4)artworkOptions();
                if(i==7)wallpaperOptions();
                if(i==6)widgets.clockOptions(()->{handler.removeCallbacks(tick);if(active)handler.post(tick);});
                if(i==5)widgets.options(()->{
                    clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);
                    rebuildDock();
                });
            }).setNegativeButton("Close",null).show();
    }
    private void arrangeDock() {
        String[] labels=new String[favourites.size()];
        for(int i=0;i<labels.length;i++){App app=find(favourites.get(i));labels[i]=(i+1)+". "+(app==null?"Unavailable app":app.name);}
        new AlertDialog.Builder(this).setTitle("Arrange dock").setItems(labels,(d,i)->arrangeApp(favourites.get(i)))
            .setNegativeButton("Done",null).show();
    }
    private void arrangeApp(String pkg) {
        App app=find(pkg);
        new AlertDialog.Builder(this).setTitle(app==null?"Dock app":app.name).setItems(
            new String[]{"Move left","Move right","Move to start","Move to end","Remove from dock"},(d,choice)->{
                int from=favourites.indexOf(pkg); if(from<0)return;
                int to=from;
                if(choice==0)to=Math.max(0,from-1);
                if(choice==1)to=Math.min(favourites.size()-1,from+1);
                if(choice==2)to=0;
                if(choice==3)to=favourites.size()-1;
                if(choice==4) {
                    int visible=0; for(String p:favourites)if(find(p)!=null)visible++;
                    if(visible<=1 && find(pkg)!=null){toast("Keep at least one available app.");return;}
                    favourites.remove(from);focusedPackage=null;
                } else {favourites.remove(from);favourites.add(to,pkg);focusedPackage=pkg;}
                persist();rebuildDock();
            }).setNegativeButton("Cancel",null).show();
    }
    private void showInputs() {
        try {
            TvInputManager manager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);
            ArrayList<TvInputInfo> inputs=new ArrayList<>();
            for(TvInputInfo input:manager.getTvInputList())if(input.isPassthroughInput()&&!input.isHidden(this))inputs.add(input);
            Collections.sort(inputs,(a,b)->a.loadLabel(this).toString().compareToIgnoreCase(b.loadLabel(this).toString()));
            if(inputs.isEmpty()){toast("Use the Input button on your Sony remote.");return;}
            String[] labels=new String[inputs.size()];
            for(int i=0;i<labels.length;i++){
                CharSequence custom=inputs.get(i).loadCustomLabel(this);
                labels[i]=(custom==null?inputs.get(i).loadLabel(this):custom).toString();
            }
            new AlertDialog.Builder(this).setTitle("TV inputs").setItems(labels,(d,i)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(inputs.get(i).getId())));}
                catch(RuntimeException e){toast("Use the Input button on your Sony remote.");}
            }).setNegativeButton("Cancel",null).show();
        }catch(RuntimeException e){toast("Use the Input button on your Sony remote.");}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private JSONObject stateJson() throws JSONException {
        JSONObject state=new JSONObject();
        state.put("schema",1);state.put("favourites",new JSONArray(favourites));state.put("compact",compact);
        if(widgets!=null)state.put("widgets",widgets.state());
        if(wallpaperDeck!=null){
            JSONObject gallery=new JSONObject();
            gallery.put("order",new JSONArray(wallpaperDeck.order()));
            gallery.put("position",wallpaperDeck.position());
            gallery.put("image",wallpaperDeck.current());
            gallery.put("focusedPackage",focusedPackage==null?"":focusedPackage);
            gallery.put("artDelay",artworkDelay);gallery.put("interval",rotation.interval());
            state.put("gallery",gallery);
        }
        return state;
    }
    private File backupDir() throws IOException {
        File root=getExternalFilesDir(null);
        if(root==null)root=getFilesDir();
        File dir=new File(root,"backups");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Backup directory unavailable");
        return dir;
    }
    private void saveBackup(String name,boolean announce) throws IOException,JSONException {
        File target=new File(backupDir(),name);
        if(name.equals("before-update-1.1.json")&&target.exists())return;
        android.util.AtomicFile atomic=new android.util.AtomicFile(target);
        FileOutputStream out=null;
        try {
            out=atomic.startWrite();out.write(stateJson().toString(2).getBytes("UTF-8"));atomic.finishWrite(out);
        }catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}
        if(announce)toast("Home settings backed up on this TV.");
    }
    private void chooseBackup(){
        try{
            File[] files=backupDir().listFiles((dir,name)->name.endsWith(".json"));
            if(files==null||files.length==0){toast("No saved backups yet.");return;}
            Arrays.sort(files,(a,b)->{
                if(a.getName().equals("recovery-kit.json"))return b.getName().equals("recovery-kit.json")?0:-1;
                if(b.getName().equals("recovery-kit.json"))return 1;
                return Long.compare(b.lastModified(),a.lastModified());
            });
            String[] labels=new String[files.length];
            SimpleDateFormat date=new SimpleDateFormat("dd MMM yyyy, HH:mm:ss",Locale.getDefault());
            for(int i=0;i<files.length;i++)labels[i]=files[i].getName().equals("before-update-1.1.json")?
                "Before QuietHome 1.1 update":
                files[i].getName().equals("recovery-kit.json")?"Recovery kit settings":
                (files[i].getName().startsWith("before-restore-")?"Before restore · ":"Saved · ")+date.format(new Date(files[i].lastModified()));
            new AlertDialog.Builder(this).setTitle("Restore home settings").setItems(labels,(d,i)->
                new AlertDialog.Builder(this).setTitle("Restore this backup?")
                    .setMessage("Restores the saved home settings. Your current settings will be backed up first.")
                    .setPositiveButton("Restore",(dialog,which)->restoreBackup(files[i]))
                    .setNegativeButton("Cancel",null).show()).setNegativeButton("Cancel",null).show();
        }catch(IOException e){toast("Backup storage is unavailable.");}
    }
    private void restoreBackup(File file){
        try{
            if(file.length()>65536||file.length()==0)throw new IOException("Invalid backup size");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=new FileInputStream(file)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>65536)throw new IOException("Too large");}}
            JSONObject state=new JSONObject(bytes.toString("UTF-8"));
            if(state.getInt("schema")!=1)throw new JSONException("Unknown schema");
            JSONArray list=state.getJSONArray("favourites");
            if(list.length()==0||list.length()>100)throw new JSONException("Invalid dock");
            ArrayList<String> restored=new ArrayList<>();
            for(int i=0;i<list.length();i++){
                String pkg=list.getString(i);
                if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))throw new JSONException("Invalid package");
                if(!restored.contains(pkg))restored.add(pkg);
            }
            boolean restoredCompact=state.getBoolean("compact");
            JSONObject restoredWidgets=state.optJSONObject("widgets");
            if(restoredWidgets!=null)HomeWidgets.validate(restoredWidgets);
            JSONObject gallery=state.optJSONObject("gallery");
            WallpaperDeck restoredDeck=null;
            long restoredDelay=artworkDelay;
            long restoredInterval=rotation.interval();
            String restoredFocus=null;
            if(gallery!=null){
                restoredDelay=gallery.getLong("artDelay");
                restoredInterval=gallery.optLong("interval",60000);
                if(!RotationTimer.supported(restoredInterval))throw new JSONException("Invalid wallpaper interval");
                if(restoredDelay!=0 && restoredDelay!=30000 && restoredDelay!=60000 && restoredDelay!=120000)
                    throw new JSONException("Invalid artwork delay");
                restoredFocus=gallery.optString("focusedPackage","");
                if(!restored.contains(restoredFocus))restoredFocus=null;
                restoredDeck=new WallpaperDeck(wallpapers,jsonStrings(gallery.getJSONArray("order")),
                    gallery.getInt("position"),gallery.optString("image",null),new Random());
            }
            int visible=0;for(String pkg:restored)if(find(pkg)!=null)visible++;
            if(visible==0)throw new JSONException("No available apps");
            saveBackup("before-restore-"+System.currentTimeMillis()+".json",false);
            favourites.clear();favourites.addAll(restored);compact=restoredCompact;focusedPackage=restoredFocus;
            if(restoredDeck!=null){
                wallpaperDeck=restoredDeck;artworkDelay=restoredDelay;
                rotation.setInterval(restoredInterval,SystemClock.elapsedRealtime());
                getPreferences(0).edit().putLong("wallpaper_interval",restoredInterval).apply();
                getPreferences(0).edit().putLong("artwork_delay",artworkDelay).apply();
                persistGallery();loadScene();
            }
            if(restoredWidgets!=null){
                widgets.restore(restoredWidgets);
                clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);
            }
            persist();rebuildDock();armArtwork();toast("Home settings restored.");
        }catch(Exception e){toast("This backup could not be restored. Your settings are unchanged.");}
    }
    private void nextScene() {
        if(wallpapers.length==0||loading)return;
        wallpaperDeck.next(); loadScene();
    }
    private void loadScene() {
        rotation.restart(SystemClock.elapsedRealtime());
        if(wallpapers.length==0||loading)return;
        loading=true;
        final String name=wallpaperDeck.current();
        images.execute(()->{
            Bitmap decoded=null;
            try{
                BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;
                try(InputStream in=getAssets().open("wallpapers/"+name)){BitmapFactory.decodeStream(in,null,opt);}
                opt.inSampleSize=1;
                while(opt.outWidth/opt.inSampleSize>2560||opt.outHeight/opt.inSampleSize>1920)opt.inSampleSize*=2;
                opt.inJustDecodeBounds=false;opt.inPreferredConfig=Bitmap.Config.RGB_565;
                try(InputStream in=getAssets().open("wallpapers/"+name)){decoded=BitmapFactory.decodeStream(in,null,opt);}
            }catch(IOException|RuntimeException e){android.util.Log.w("QuietHome","Wallpaper unavailable",e);}
            final Bitmap result=decoded;
            handler.post(()->{
                loading=false;
                if(isDestroyed()){if(result!=null)result.recycle();return;}
                if(result!=null){
                    background.setImageBitmap(result);
                    String caption=name.startsWith("lake")?"Still waters  ·  Bonnie Moreland":
                        name.startsWith("forest")?"Forest light  ·  SpessiG":
                        "Your collection  ·  "+name.replaceFirst("^user-\\d+-","").replaceFirst("\\.[^.]+$","").replace('_',' ');
                    scene.setText(caption);
                    persistGallery();
                    android.util.Log.i("QuietHome","Wallpaper: "+name);
                }
            });
        });
    }
    private ArrayList<String> jsonStrings(JSONArray array) throws JSONException {
        if(array.length()>1000)throw new JSONException("Too many wallpapers");
        ArrayList<String> result=new ArrayList<>();
        for(int i=0;i<array.length();i++)result.add(array.getString(i));
        return result;
    }
    private void persistGallery(){
        if(wallpaperDeck==null)return;
        getPreferences(0).edit()
            .putString("wallpaper_order",new JSONArray(wallpaperDeck.order()).toString())
            .putInt("wallpaper_position",wallpaperDeck.position())
            .putString("wallpaper_current",wallpaperDeck.current()).apply();
    }
    private void wallpaperOptions(){
        final long[] intervals={0,60000,300000,900000};
        int selected=0;for(int i=0;i<intervals.length;i++)if(intervals[i]==rotation.interval())selected=i;
        new AlertDialog.Builder(this).setTitle("Wallpaper timing")
            .setSingleChoiceItems(new String[]{"Paused (manual changes only)","Every minute","Every 5 minutes","Every 15 minutes"},selected,(d,i)->{
                rotation.setInterval(intervals[i],SystemClock.elapsedRealtime());
                getPreferences(0).edit().putLong("wallpaper_interval",intervals[i]).apply();
                d.dismiss();handler.removeCallbacks(tick);if(active)handler.post(tick);
            }).setNegativeButton("Cancel",null).show();
    }
    private void artworkOptions(){
        final long[] delays={0,30000,60000,120000};
        int selected=0;for(int i=0;i<delays.length;i++)if(delays[i]==artworkDelay)selected=i;
        new AlertDialog.Builder(this).setTitle("Artwork mode")
            .setSingleChoiceItems(new String[]{"Off","After 30 seconds","After 1 minute","After 2 minutes"},
                selected,(dialog,index)->{
                    artworkDelay=delays[index];
                    getPreferences(0).edit().putLong("artwork_delay",artworkDelay).apply();
                    dialog.dismiss();armArtwork();
                }).setNegativeButton("Cancel",null).show();
    }
    private void armArtwork(){
        handler.removeCallbacks(enterArtwork);
        if(active && hasWindowFocus() && !artworkVisible && artworkDelay>0)
            handler.postDelayed(enterArtwork,artworkDelay);
    }
    private void setArtworkVisible(boolean visible){
        artworkVisible=visible;
        for(View view:new View[]{headerView,lowerView,shadeView,widgets==null?null:widgets.view}){
            if(view==null)continue;
            view.animate().cancel();
            if(visible){
                view.animate().alpha(0).setDuration(500).withEndAction(()->{
                    if(artworkVisible)view.setVisibility(View.INVISIBLE);
                }).start();
            }else{
                view.setVisibility(View.VISIBLE);
                view.animate().alpha(1).setDuration(180).start();
            }
        }
        if(!visible)armArtwork();
        android.util.Log.i("QuietHome","Artwork mode: "+(visible?"on":"off"));
    }
    private boolean navigationKey(int key){
        return key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN ||
            key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT ||
            key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER ||
            key==KeyEvent.KEYCODE_BACK || key==KeyEvent.KEYCODE_MENU ||
            key==KeyEvent.KEYCODE_BUTTON_A || key==KeyEvent.KEYCODE_BUTTON_B;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key==swallowedKey){
            if(event.getAction()==KeyEvent.ACTION_UP)swallowedKey=-1;
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(artworkVisible){
                setArtworkVisible(false);
                if(navigationKey(key)){swallowedKey=key;return true;}
            }
            armArtwork();
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            swallowedTouch=artworkVisible;
            if(artworkVisible)setArtworkVisible(false);
            armArtwork();
        }
        if(swallowedTouch){
            if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                swallowedTouch=false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused)armArtwork();else handler.removeCallbacks(enterArtwork);
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);setArtworkVisible(false);
    }
    @Override protected void onResume(){
        super.onResume();active=true;swallowedKey=-1;swallowedTouch=false;
        rotation.resume(SystemClock.elapsedRealtime());
        setArtworkVisible(false);handler.removeCallbacks(tick);handler.post(tick);
        widgets.start();
    }
    @Override protected void onPause(){
        rotation.pause(SystemClock.elapsedRealtime());
        active=false;handler.removeCallbacks(tick);handler.removeCallbacks(enterArtwork);
        widgets.stop();
        persistGallery();super.onPause();
    }
    @Override protected void onDestroy(){
        active=false;handler.removeCallbacks(tick);handler.removeCallbacks(enterArtwork);
        widgets.destroy();
        images.shutdownNow();super.onDestroy();
    }
    @Override public void onBackPressed(){if(dock.getChildCount()>0)dock.getChildAt(0).requestFocus();}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_MENU){editDock();return true;}return super.onKeyDown(key,event);
    }
}
