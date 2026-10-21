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
    private final ExecutorService catalogWorker = Executors.newSingleThreadExecutor();
    private WallpaperLoader imageLoader;
    private WallpaperLoader.Frame currentFrame;
    private String currentImage;
    private HomeTheme theme;
    private NatureBorder natureBorder;
    private VideoBackground videoBackground;
    private int videoMode;
    private String videoFile="",videoStatus="";
    private boolean videoSuppressed,videoShowing;
    private boolean catalogBusy;
    private long catalogGeneration;
    private ImageView background;
    private TextView clock, scene;
    private HomeWidgets widgets;
    private LinearLayout dock;
    private HorizontalScrollView dockScroll;
    private View allAppsButton;
    private View headerView, lowerView, shadeView;
    private String focusedPackage;
    private boolean compact,fitWallpaper;
    private boolean artworkVisible;
    private int swallowedKey = -1;
    private boolean swallowedTouch;
    private long artworkDelay = 30000;
    private final RotationTimer rotation=new RotationTimer(60000);
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<String> favourites = new ArrayList<>();
    private String[] wallpapers;
    private WallpaperDeck wallpaperDeck;
    private boolean active, loading, catalogDirty;
    private final Runnable refreshCatalog=this::refreshApps;
    private void refreshApps(){
        if(!active||!catalogDirty||catalogBusy||!hasWindowFocus())return;
        catalogBusy=true;catalogDirty=false;
        final long ticket=catalogGeneration;
        final HashMap<String,App> previous=new HashMap<>();for(App app:apps)previous.put(app.pkg,app);
        catalogWorker.execute(()->{
            ArrayList<App> result=null;
            try{result=readApps(previous);}catch(RuntimeException e){android.util.Log.w("QuietHome","App refresh unavailable",e);}
            final ArrayList<App> found=result;
            handler.post(()->{
                catalogBusy=false;if(isDestroyed())return;
                if(ticket!=catalogGeneration||!active){catalogDirty=true;if(active)handler.post(refreshCatalog);return;}
                if(!hasWindowFocus()){catalogDirty=true;return;}
                if(found!=null){
                    boolean changed=found.size()!=apps.size();
                    if(!changed)for(int i=0;i<found.size();i++)if(found.get(i)!=apps.get(i)){changed=true;break;}
                    if(changed){
                        View focus=getCurrentFocus();boolean outside=focus!=null&&focus.getParent()!=dock;
                        apps.clear();apps.addAll(found);rebuildDock();
                        if(outside&&focus.isAttachedToWindow())focus.requestFocus();
                    }
                }
                if(catalogDirty)handler.post(refreshCatalog);
            });
        });
    }
    private final BroadcastReceiver packageChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())&&intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            catalogGeneration++;catalogDirty=true;handler.removeCallbacks(refreshCatalog);handler.postDelayed(refreshCatalog,250);
        }
    };
    private static final String DEFAULTS = "com.google.android.youtube.tv,com.sony.dtv.tvx,org.jellyfin.androidtv,com.netflix.ninja";
    private final Runnable enterArtwork = () -> {
        if(active && hasWindowFocus() && artworkDelay>0) setArtworkVisible(true);
    };
    private static final class App {
        String name, pkg;
        long stamp;
        ResolveInfo info;
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
            if (!videoHoldsRotation() && wallpapers.length>1 && rotation.due(SystemClock.elapsedRealtime()) && !loading) nextScene();
            long clockDelay = 60000 - (System.currentTimeMillis() % 60000);
            long sceneDelay = videoHoldsRotation()||wallpapers.length<2?clockDelay:Math.max(1000,rotation.delay(SystemClock.elapsedRealtime()));
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
        theme=new HomeTheme(HomeTheme.read(getPreferences(0)));
        videoMode=getPreferences(0).getInt("video_mode",0);
        videoFile=getPreferences(0).getString("video_file","");
        imageLoader=new WallpaperLoader(getAssets(),handler,(name,frame,elapsed,cached)->{
            loading=false;if(frame==null){toast("That picture could not be loaded.");return;}
            currentFrame=frame;currentImage=name;background.setImageBitmap(frame.image);
            theme.wallpaper(frame.frost,fitWallpaper);
            if(!videoShowing)scene.setText(imageCaption(name));
            persistGallery();
            android.util.Log.i("QuietHome","Wallpaper: "+name);
            android.util.Log.i("QuietHomePerf","image_ms="+elapsed+" cached="+cached);
        });
        scanApps();
        String stored = getPreferences(0).getString("favourites", DEFAULTS);
        if(!stored.isEmpty())favourites.addAll(Arrays.asList(stored.split(",")));
        compact = getPreferences(0).getBoolean("compact", false);
        fitWallpaper=getPreferences(0).getBoolean("wallpaper_fit",false);
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
        HashMap<String,App> prior=new HashMap<>();for(App app:apps)prior.put(app.pkg,app);
        ArrayList<App> found=readApps(prior);apps.clear();apps.addAll(found);
    }
    private ArrayList<App> readApps(Map<String,App> previous){
        PackageManager pm=getPackageManager();ArrayList<App> found=new ArrayList<>();
        List<ResolveInfo> results=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);
        HashSet<String> seen=new HashSet<>();
        for(ResolveInfo info:results){
            String pkg=info.activityInfo.packageName;if(pkg.equals(getPackageName())||!seen.add(pkg))continue;
            long stamp=0;try{stamp=pm.getPackageInfo(pkg,0).lastUpdateTime;}catch(PackageManager.NameNotFoundException ignored){}
            App old=previous.get(pkg);
            if(old!=null&&old.stamp==stamp&&old.info.activityInfo.name.equals(info.activityInfo.name)){found.add(old);continue;}
            App app=new App();app.pkg=pkg;app.stamp=stamp;app.info=info;app.name=info.loadLabel(pm).toString();
            if(pkg.equals("com.sony.dtv.tvx"))app.name="TV";
            if(pkg.equals("org.jellyfin.androidtv"))app.name="Jellyfin";
            app.intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(new ComponentName(pkg,info.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            found.add(app);
        }
        Collections.sort(found,(a,b)->a.name.compareToIgnoreCase(b.name));return found;
    }
    private App find(String pkg) { for(App a:apps) if(a.pkg.equals(pkg)) return a; return null; }
    private void build() {
        if(videoBackground!=null)videoBackground.close();
        if(widgets!=null)widgets.destroy();
        artworkVisible=false;videoSuppressed=false;
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,26,32));
        background=new ImageView(this);applyWallpaperScale();
        if(currentFrame!=null)background.setImageBitmap(currentFrame.image);
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        videoBackground=new VideoBackground(this,videoDirectory(),(showing,status)->{
            videoShowing=showing;videoStatus=status;theme.video(showing);
            if(active&&videoBackground!=null&&videoBackground.hasFailed())rotation.resume(SystemClock.elapsedRealtime());
            if(scene!=null)scene.setText(showing?"Video · "+videoFile.replaceFirst("\\.mp4$",""):status.isEmpty()?(currentImage==null?"Your collection":imageCaption(currentImage)):status);
        });
        root.addView(videoBackground.view,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        shadeView=shade;
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x50000000,0x08000000,0xE80A1018}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        natureBorder=new NatureBorder(this,theme.style);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        headerView=header;
        TextView title=text("Home",28,Color.WHITE); title.setTypeface(Typeface.create("sans-serif-medium",0));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        clock=text("",22,Color.WHITE); clock.setGravity(Gravity.RIGHT); header.addView(clock);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        hp.setMargins(dp(48),dp(30),dp(48),0); root.addView(header,hp);
        boolean decorated=theme.mode>=3&&theme.style.density>0;
        widgets=new HomeWidgets(this,handler,theme);
        FrameLayout.LayoutParams wp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        wp.setMargins(dp(48),dp(decorated?116:96),dp(48),0);root.addView(widgets.view,wp);
        clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);

        LinearLayout lower=new LinearLayout(this); lower.setOrientation(LinearLayout.VERTICAL);
        lowerView=lower;
        scene=text(currentImage!=null?imageCaption(currentImage):wallpapers.length==0?"Quiet Home":"A moment outside",15,0xFFE2ECE6);
        scene.setPadding(dp(4),0,0,dp(decorated?34:18)); lower.addView(scene);
        FrameLayout dockFrame=new FrameLayout(this);
        dockFrame.setPadding(dp(10),dp(10),dp(10),dp(10));
        dockFrame.setBackground(theme.surface(dockFrame,false,dp(22)));
        dockFrame.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(22));
            }
        });
        dockFrame.setClipToOutline(true);
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        dockScroll=scroll;
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(true);scroll.setClipToPadding(true);
        dock=new LinearLayout(this); dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(4),0,dp(4),0);
        dock.setClipChildren(false); dock.setClipToPadding(false);
        scroll.addView(dock,new FrameLayout.LayoutParams(-2,-1));
        dockFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        lower.addView(dockFrame,new LinearLayout.LayoutParams(-1,dp(132)));
        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(0,dp(14),0,0); tools.setGravity(Gravity.CENTER_VERTICAL);
        allAppsButton=addTool(tools,"All apps",()->showApps(false));
        addTool(tools,"Edit dock",()->editDock());
        addTool(tools,"Inputs",()->showInputs());
        addTool(tools,"Art mode",()->setArtworkVisible(true));
        addTool(tools,"Home options",()->homeOptions());
        addTool(tools,"Settings",()->launchSettings());
        TextView hint=text("Hold OK to arrange",11,0xFFC0C8CE);
        hint.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tools.addView(hint,new LinearLayout.LayoutParams(0,dp(40),1));
        lower.addView(tools);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        lp.setMargins(dp(48),0,dp(48),dp(34)); root.addView(lower,lp);
        natureBorder.bind(root,dockFrame,widgets.newsDecorationAnchor(),widgets.weatherDecorationAnchor(),header,scene,tools);
        root.addView(natureBorder,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        final TextView caption=scene;final HomeWidgets panelWidgets=widgets;
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->updateCaptionVisibility(root,caption,panelWidgets));
        rebuildDock();
        if(currentFrame!=null)theme.wallpaper(currentFrame.frost,fitWallpaper);
        if(active)widgets.start();syncVideo();
    }
    private void updateCaptionVisibility(FrameLayout root,TextView caption,HomeWidgets panels){
        if(artworkVisible||caption.getLayout()==null)return;
        android.text.Layout textLayout=caption.getLayout();
        float right=0;
        for(int i=0;i<textLayout.getLineCount();i++)right=Math.max(right,textLayout.getLineRight(i));
        Rect textBounds=new Rect(0,0,Math.min(caption.getWidth(),(int)Math.ceil(right)+caption.getPaddingLeft()),caption.getHeight()-caption.getPaddingBottom());
        root.offsetDescendantRectToMyCoords(caption,textBounds);
        boolean overlaps=false;
        for(View card:new View[]{panels.newsDecorationAnchor(),panels.weatherDecorationAnchor()})if(card.isShown()){
            Rect bounds=new Rect();card.getDrawingRect(bounds);root.offsetDescendantRectToMyCoords(card,bounds);
            if(Rect.intersects(textBounds,bounds))overlaps=true;
        }
        // The filename is secondary; keep panel content readable without moving the dock.
        int visibility=overlaps?View.INVISIBLE:View.VISIBLE;
        if(caption.getVisibility()!=visibility)caption.setVisibility(visibility);
    }
    private View addTool(LinearLayout parent,String label,Runnable action) {
        TextView b=text(label,14,theme.panelText()); b.setGravity(Gravity.CENTER);
        b.setId(View.generateViewId());
        b.setPadding(dp(12),0,dp(12),0); b.setFocusable(true); b.setClickable(true);
        b.setBackground(theme.surface(b,false,dp(12)));
        b.setOnFocusChangeListener((v,f)->{
            b.setBackground(theme.surface(b,f,dp(12)));
            b.setTextColor(f?theme.focusText():theme.panelText());
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
            card.setBackground(theme.surface(card,false,dp(15)));
            if(a.icon==null)a.icon=a.info.loadIcon(getPackageManager());
            ImageView icon=new ImageView(this); icon.setImageDrawable(a.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon,new LinearLayout.LayoutParams(dp(compact?48:58),dp(compact?48:58)));
            TextView label=text(a.name,compact?14:15,theme.panelText()); label.setGravity(Gravity.CENTER);
            label.setSingleLine(true); label.setPadding(0,dp(5),0,0);
            card.addView(label,new LinearLayout.LayoutParams(-1,-2));
            card.setOnFocusChangeListener((v,f)->{
                if(f) {
                    focusedPackage=pkg; allAppsButton.setNextFocusUpId(card.getId());
                    widgets.linkDock(card);
                    getPreferences(0).edit().putString("last_dock_package",pkg).apply();
                    // Android's default focus scroll omits the scaled highlight and can clip an end tile.
                    dockScroll.post(()->{
                        if(!card.hasFocus())return;
                        int left=card.getLeft()-dp(8),right=card.getRight()+dp(8);
                        int x=dockScroll.getScrollX(),width=dockScroll.getWidth();
                        if(left<x)dockScroll.smoothScrollTo(Math.max(0,left),0);
                        else if(right>x+width)dockScroll.smoothScrollTo(right-width,0);
                    });
                }
                card.setBackground(theme.surface(card,f,dp(15))); label.setTextColor(f?theme.focusText():theme.panelText());
                card.animate().scaleX(f?1.035f:1).scaleY(f?1.035f:1).setDuration(120).start();
            });
            card.setOnClickListener(v->open(a));
            card.setOnLongClickListener(v->{arrangeApp(pkg);return true;});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(compact?118:148),dp(108));
            cp.setMargins(dp(4),0,dp(4),0); dock.addView(card,cp);
        }
        if(dock.getChildCount()==0){
            TextView empty=text("Choose your favourites with Edit dock",16,0xFFD8E6E4);
            empty.setPadding(dp(18),0,dp(18),0);dock.addView(empty);
            allAppsButton.setNextFocusUpId(View.NO_ID);widgets.linkDock(allAppsButton);allAppsButton.requestFocus();return;
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
        final ArrayList<App> choices=new ArrayList<>(apps);
        String[] names=new String[choices.size()];
        for(int i=0;i<choices.size();i++) names[i]=choices.get(i).name;
        new AlertDialog.Builder(this).setTitle("All apps")
            .setItems(names,(d,i)->open(choices.get(i))).setNegativeButton("Close",null).show();
    }
    private void editDock() {
        scanApps();
        final ArrayList<App> choices=new ArrayList<>(apps);
        String[] names=new String[choices.size()];
        boolean[] selected=new boolean[choices.size()];
        final ArrayList<String> chosen=new ArrayList<>(favourites);
        for(int i=0;i<choices.size();i++){names[i]=choices.get(i).name;selected[i]=chosen.contains(choices.get(i).pkg);}
        new AlertDialog.Builder(this).setTitle("Choose your dock apps")
            .setMultiChoiceItems(names,selected,(d,i,on)->{
                String p=choices.get(i).pkg;
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
            new String[]{"Arrange dock","Tile size","Save backup","Restore backup","Artwork mode","Widgets","Clock format","Wallpaper settings","Appearance"},(d,i)->{
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
                if(i==8)appearanceOptions();
                if(i==6)widgets.clockOptions(()->{handler.removeCallbacks(tick);if(active)handler.post(tick);});
                if(i==5)widgets.options(()->{
                    clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);
                    rebuildDock();
                });
            }).setNegativeButton("Close",null).show();
    }
    private void appearanceOptions(){
        new AppearanceEditor(this,theme,natureBorder,()->{
            theme=new HomeTheme(HomeTheme.read(getPreferences(0)));
            build();handler.removeCallbacks(tick);if(active)handler.post(tick);armArtwork();
        }).show();
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
        state.put("video",new JSONObject().put("mode",videoMode).put("file",videoFile));
        state.put("schema",1);state.put("favourites",new JSONArray(favourites));state.put("compact",compact);
        if(theme!=null){
            state.put("theme",theme.mode);ThemeStyle style=HomeTheme.read(getPreferences(0));
            state.put("appearance",new JSONObject().put("accent",style.accent).put("tint",style.tint)
                .put("transparency",style.transparency).put("density",style.density));
        }
        if(widgets!=null)state.put("widgets",widgets.state());
        if(wallpaperDeck!=null){
            JSONObject gallery=new JSONObject();
            gallery.put("order",new JSONArray(wallpaperDeck.order()));
            gallery.put("position",wallpaperDeck.position());
            gallery.put("image",wallpaperDeck.current());
            gallery.put("focusedPackage",focusedPackage==null?"":focusedPackage);
            gallery.put("artDelay",artworkDelay);gallery.put("interval",rotation.interval());gallery.put("fit",fitWallpaper);
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
        boolean changesStarted=false;
        try{
            if(file.length()>65536||file.length()==0)throw new IOException("Invalid backup size");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=new FileInputStream(file)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>65536)throw new IOException("Too large");}}
            JSONObject state=new JSONObject(bytes.toString("UTF-8"));
            SettingsBackup.validate(state);
            JSONArray list=state.getJSONArray("favourites");
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
            boolean restoredFit=fitWallpaper;
            String restoredFocus=null;
            if(gallery!=null){
                restoredDelay=gallery.getLong("artDelay");
                restoredInterval=gallery.optLong("interval",60000);
                if(gallery.has("fit")&&!(gallery.get("fit") instanceof Boolean))throw new JSONException("Invalid wallpaper scale");
                restoredFit=gallery.optBoolean("fit",false);
                if(!RotationTimer.supported(restoredInterval))throw new JSONException("Invalid wallpaper interval");
                if(restoredDelay!=0 && restoredDelay!=30000 && restoredDelay!=60000 && restoredDelay!=120000)
                    throw new JSONException("Invalid artwork delay");
                restoredFocus=gallery.optString("focusedPackage","");
                if(!restored.contains(restoredFocus))restoredFocus=null;
                restoredDeck=new WallpaperDeck(wallpapers,jsonStrings(gallery.getJSONArray("order")),
                    gallery.getInt("position"),gallery.optString("image",null),new Random());
            }
            saveBackup("before-restore-"+System.currentTimeMillis()+".json",false);
            changesStarted=true;
            favourites.clear();favourites.addAll(restored);compact=restoredCompact;focusedPackage=restoredFocus;
            if(restoredDeck!=null){
                wallpaperDeck=restoredDeck;artworkDelay=restoredDelay;
                rotation.setInterval(restoredInterval,SystemClock.elapsedRealtime());
                fitWallpaper=restoredFit;getPreferences(0).edit().putBoolean("wallpaper_fit",fitWallpaper).apply();applyWallpaperScale();
                getPreferences(0).edit().putLong("wallpaper_interval",restoredInterval).apply();
                getPreferences(0).edit().putLong("artwork_delay",artworkDelay).apply();
                imageLoader.cancel();loading=false;persistGallery();loadScene();
            }
            if(restoredWidgets!=null){
                widgets.restore(restoredWidgets);
                clock.setVisibility(widgets.clockEnabled()?View.VISIBLE:View.GONE);
            }
            int restoredTheme=state.optInt("theme",1);
            ThemeStyle style=ThemeStyle.defaults(restoredTheme);JSONObject appearance=state.optJSONObject("appearance");
            if(appearance!=null)style=new ThemeStyle(restoredTheme,appearance.getInt("accent"),appearance.getInt("tint"),appearance.getInt("transparency"),appearance.getInt("density"));
            JSONObject video=state.optJSONObject("video");
            videoMode=video==null?0:video.getInt("mode");videoFile=video==null?"":video.getString("file");
            getPreferences(0).edit().putInt("video_mode",videoMode).putString("video_file",videoFile).apply();
            HomeTheme.save(getPreferences(0),style);theme=new HomeTheme(style);persist();build();armArtwork();handler.removeCallbacks(tick);if(active)handler.post(tick);toast("Home settings restored.");
        }catch(Exception e){toast(changesStarted?"Restore failed after saving a recovery backup. Use Restore backup to recover.":"This backup could not be restored. Your settings are unchanged.");}
    }
    private void nextScene() {
        if(wallpapers.length==0)return;
        wallpaperDeck.next();loadScene();
    }
    private String imageCaption(String name){return "Your collection  \u00b7  "+name.replaceFirst("^user-\\d+-","").replaceFirst("\\.[^.]+$","").replace('_',' ');}
    private void browseScene(int direction) {
        if(wallpapers.length==0)return;
        if(videoWanted()){videoSuppressed=true;syncVideo();}
        wallpaperDeck.browse(direction);loadScene();
    }
    private void loadScene() {
        rotation.restart(SystemClock.elapsedRealtime());
        if(wallpapers.length==0)return;
        loading=true;
        List<String> order=wallpaperDeck.order();int position=wallpaperDeck.position(),size=order.size();
        imageLoader.request(wallpaperDeck.current(),order.get((position+size-1)%size),order.get((position+1)%size));
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
    private void applyWallpaperScale(){background.setScaleType(fitWallpaper?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
        if(theme!=null&&currentFrame!=null)theme.wallpaper(currentFrame.frost,fitWallpaper);}
    private void wallpaperOptions(){
        new AlertDialog.Builder(this).setTitle("Wallpaper settings").setItems(new String[]{"Timing","Picture framing","Video backgrounds"},(d,i)->{
            if(i==0)wallpaperTiming();
            if(i==2)videoOptions();
            if(i==1)new AlertDialog.Builder(this).setTitle("Picture framing")
                .setSingleChoiceItems(new String[]{"Fill screen (crop edges)","Fit whole picture (may show borders)"},fitWallpaper?1:0,(dialog,index)->{
                    fitWallpaper=index==1;getPreferences(0).edit().putBoolean("wallpaper_fit",fitWallpaper).apply();
                    applyWallpaperScale();dialog.dismiss();
                }).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Close",null).show();
    }
    private File videoDirectory(){
        File root=getExternalFilesDir(null);if(root==null)root=getFilesDir();
        File dir=new File(root,"videos");if(!dir.exists())dir.mkdirs();return dir;
    }
    private boolean videoWanted(){return VideoPolicy.shouldPlay(videoMode,active,artworkVisible,videoSuppressed)&&!videoFile.isEmpty();}
    private boolean videoHoldsRotation(){return videoWanted()&&(videoBackground==null||!videoBackground.hasFailed());}
    private void syncVideo(){
        if(videoBackground!=null)videoBackground.update(videoFile,videoWanted());
        if(videoHoldsRotation())rotation.pause(SystemClock.elapsedRealtime());else if(active)rotation.resume(SystemClock.elapsedRealtime());
    }
    private void saveVideo(){
        getPreferences(0).edit().putInt("video_mode",videoMode).putString("video_file",videoFile).apply();
        videoSuppressed=false;syncVideo();handler.removeCallbacks(tick);if(active)handler.post(tick);
    }
    private void videoOptions(){
        new AlertDialog.Builder(this).setTitle("Video backgrounds").setMessage("Silent local MP4 loops. Playback stops when you leave Home.\nCurrent: "+(videoFile.isEmpty()?"No video selected":videoFile)+"\n"+videoStatus)
            .setPositiveButton("Playback mode",(d,i)->new AlertDialog.Builder(this).setTitle("Video playback")
                .setSingleChoiceItems(VideoPolicy.MODES,videoMode,(dialog,index)->{videoMode=index;saveVideo();dialog.dismiss();if(index!=0&&videoFile.isEmpty())chooseVideo();})
                .setNegativeButton("Cancel",null).show())
            .setNeutralButton("Choose video",(d,i)->chooseVideo())
            .setNegativeButton("Close",null).show();
    }
    private void chooseVideo(){
        File directory=videoDirectory();
        catalogWorker.execute(()->{
            String[] names=directory.list((dir,name)->VideoPolicy.validName(name)&&new File(dir,name).isFile());
            final String[] files=names==null?new String[0]:names;Arrays.sort(files);
            handler.post(()->{
                if(isDestroyed()||!active)return;
                if(files.length==0){new AlertDialog.Builder(this).setTitle("Add a local video")
                    .setMessage("Copy a silent H.264 MP4 with QuietHome's Copy-Video.ps1 helper, then return here. Use up to 1080p, 30 fps and 80 MiB per file. Your original video is kept.\n\nVideo files are stored separately from the app and survive updates.")
                    .setPositiveButton("OK",null).show();return;}
                new AlertDialog.Builder(this).setTitle("Choose a local video").setItems(files,(d,i)->{videoFile=files[i];saveVideo();if(videoMode==0)toast("Video selected. Choose a playback mode to enable it.");})
                    .setNegativeButton("Cancel",null).show();
            });
        });
    }
    private void wallpaperTiming(){
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
        if(artworkVisible!=visible)videoSuppressed=false;
        artworkVisible=visible;syncVideo();
        for(View view:new View[]{headerView,lowerView,shadeView,natureBorder,widgets==null?null:widgets.view}){
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
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){
                    browseScene(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);
                    swallowedKey=key;return true;
                }
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
        if(focused){armArtwork();if(catalogDirty)handler.post(refreshCatalog);}else handler.removeCallbacks(enterArtwork);
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);setArtworkVisible(false);
    }
    @Override protected void onStart(){
        super.onStart();
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packageChanges,filter,Context.RECEIVER_EXPORTED);
        else registerReceiver(packageChanges,filter);
    }
    @Override protected void onStop(){
        unregisterReceiver(packageChanges);handler.removeCallbacks(refreshCatalog);super.onStop();
    }
    @Override protected void onResume(){
        super.onResume();active=true;swallowedKey=-1;swallowedTouch=false;
        rotation.resume(SystemClock.elapsedRealtime());
        setArtworkVisible(false);handler.removeCallbacks(tick);handler.post(tick);
        widgets.start();catalogDirty=true;handler.post(refreshCatalog);
        if(wallpaperDeck.current()!=null&&!wallpaperDeck.current().equals(currentImage))loadScene();
    }
    @Override protected void onPause(){
        rotation.pause(SystemClock.elapsedRealtime());
        active=false;syncVideo();handler.removeCallbacks(tick);handler.removeCallbacks(enterArtwork);
        widgets.stop();handler.removeCallbacks(refreshCatalog);catalogGeneration++;
        imageLoader.cancel();loading=false;
        persistGallery();super.onPause();
    }
    @Override protected void onDestroy(){
        active=false;handler.removeCallbacks(tick);handler.removeCallbacks(enterArtwork);
        if(videoBackground!=null)videoBackground.close();
        widgets.destroy();
        imageLoader.destroy();catalogWorker.shutdownNow();super.onDestroy();
    }
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW&&imageLoader!=null)imageLoader.trim();
    }
    @Override public void onBackPressed(){if(dock.getChildCount()>0&&dock.getChildAt(0).isFocusable())dock.getChildAt(0).requestFocus();else allAppsButton.requestFocus();}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_MENU){editDock();return true;}return super.onKeyDown(key,event);
    }
}
