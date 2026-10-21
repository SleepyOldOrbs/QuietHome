package local.quiethome;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Directional-remote editor. Preview is transient until Save; Back/cancel always restores it. */
final class AppearanceEditor {
    private final Activity activity;
    private final HomeTheme theme;
    private final NatureBorder border;
    private final Runnable rebuild;
    AppearanceEditor(Activity activity,HomeTheme theme,NatureBorder border,Runnable rebuild){this.activity=activity;this.theme=theme;this.border=border;this.rebuild=rebuild;}
    void show(){
        new AlertDialog.Builder(activity).setTitle("Appearance").setItems(new String[]{"Theme: "+ThemeStyle.NAMES[theme.mode],
            "Highlight colour","Panel colour","Glass transparency","Nature border density","Reset this theme"},(d,i)->{
                if(i==0)themes();if(i==1)colours(true);if(i==2)colours(false);if(i==3)transparency();if(i==4)density();
                if(i==5)new AlertDialog.Builder(activity).setTitle("Reset this theme?").setMessage("Restore this theme's colours, transparency and border density.")
                    .setPositiveButton("Reset",(x,y)->save(ThemeStyle.defaults(theme.mode))).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Close",null).show();
    }
    private void save(ThemeStyle s){HomeTheme.save(activity.getPreferences(0),s);rebuild.run();}
    private void preview(ThemeStyle s){theme.preview(s);border.style(s);}
    private void themes(){
        new AlertDialog.Builder(activity).setTitle("Choose a theme").setSingleChoiceItems(ThemeStyle.NAMES,theme.mode,(d,i)->{
            d.dismiss();save(ThemeStyle.defaults(i));
        }).setNegativeButton("Cancel",null).show();
    }
    private LinearLayout panel(){LinearLayout p=new LinearLayout(activity);p.setOrientation(1);p.setPadding(28,12,28,12);return p;}
    private TextView label(LinearLayout p,String value){TextView t=new TextView(activity);t.setText(value);t.setTextSize(18);t.setTextColor(Color.WHITE);t.setPadding(8,8,8,8);p.addView(t);return t;}
    private void transparentWindow(AlertDialog dialog){
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels*.48),-2);
        dialog.getWindow().setGravity(Gravity.CENTER);
    }
    private void colours(boolean highlight){
        ThemeStyle original=theme.style;
        int[] colours={0xC6E2AD,0xFFE6AD,0xF5C6DA,0xBDCCFF,0x7DD9D0,0xFFFFFF,0x192C24,0x101728,0x332332,0x121212};
        String[] names={"Sage","Honey","Blossom","Periwinkle","Sea glass","Snow","Forest","Midnight","Plum","Charcoal"};
        LinearLayout content=panel();label(content,"Choose a swatch, or enter #RRGGBB");
        TextView sample=label(content,"Preview");sample.setGravity(Gravity.CENTER);sample.setMinHeight(66);
        final int[] selected={highlight?original.accent:original.tint};
        EditText hex=new EditText(activity);hex.setSingleLine(true);hex.setText(String.format(Locale.ROOT,"#%06X",selected[0]));hex.setSelectAllOnFocus(true);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});hex.setContentDescription("Custom colour hex code");
        Runnable update=()->{sample.setBackgroundColor(0xFF000000|selected[0]);sample.setTextColor(ThemeStyle.textColour(selected[0]));sample.setText(String.format(Locale.ROOT,"Preview  #%06X",selected[0]));preview(original.colour(highlight,selected[0]));};
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(activity);content.addView(line);
            for(int col=0;col<5;col++){final int index=row*5+col;
                Button button=new Button(activity);button.setText(names[index]);button.setTextSize(12);button.setAllCaps(false);button.setPadding(4,0,4,0);button.setSingleLine(true);button.setFocusableInTouchMode(true);
                GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF000000|colours[index]);bg.setCornerRadius(9);bg.setStroke(2,0xFF899798);
                button.setBackground(bg);button.setTextColor(ThemeStyle.textColour(colours[index]));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,66,1);lp.setMargins(3,4,3,4);line.addView(button,lp);
                button.setOnFocusChangeListener((v,f)->{bg.setStroke(f?5:2,f?0xFFFFFFFF:0xFF899798);v.setScaleX(f?1.04f:1);v.setScaleY(f?1.04f:1);});
                button.setOnClickListener(v->{selected[0]=colours[index];hex.setText(String.format(Locale.ROOT,"#%06X",selected[0]));update.run();});
            }
        }
        content.addView(hex);final boolean[] accepted={false};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(highlight?"Highlight colour":"Panel colour").setView(content)
            .setPositiveButton("Save",null).setNeutralButton("Preview hex",null).setNegativeButton("Cancel",null).create();
        Runnable parse=()->{selected[0]=ThemeStyle.parseColour(hex.getText().toString().trim());update.run();};
        dialog.setOnDismissListener(d->{preview(original);if(accepted[0])save(original.colour(highlight,selected[0]));});
        dialog.setOnShowListener(d->{transparentWindow(dialog);update.run();((LinearLayout)content.getChildAt(2)).getChildAt(0).requestFocus();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{try{parse.run();}catch(IllegalArgumentException e){hex.setError("Use six hexadecimal digits, for example #C6E2AD");}});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{parse.run();accepted[0]=true;dialog.dismiss();}catch(IllegalArgumentException e){hex.setError("Use #RRGGBB");}});
        });dialog.show();
    }
    private void transparency(){
        ThemeStyle original=theme.style;LinearLayout content=panel();TextView value=label(content,"");
        label(content,"Left / Right adjusts in 5% steps.\n0% = solid panels; 100% = clear panels.\nSelection highlights stay solid for readability.");
        SeekBar slider=new SeekBar(activity);slider.setFocusableInTouchMode(true);slider.setMax(100);slider.setProgress(original.transparency);slider.setKeyProgressIncrement(5);
        slider.setContentDescription("Glass transparency");content.addView(slider,new LinearLayout.LayoutParams(-1,64));
        Runnable update=()->{value.setText("Transparency: "+slider.getProgress()+"%");preview(original.transparency(slider.getProgress()));};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){update.run();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        final boolean[] accepted={false};AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Glass transparency").setView(content)
            .setPositiveButton("Save",(d,i)->accepted[0]=true).setNegativeButton("Cancel",null).create();
        dialog.setOnDismissListener(d->{preview(original);if(accepted[0])save(original.transparency(slider.getProgress()));});
        dialog.setOnShowListener(d->{transparentWindow(dialog);update.run();slider.requestFocus();});dialog.show();
    }
    private void density(){
        if(theme.mode<3){new AlertDialog.Builder(activity).setTitle("Nature borders").setMessage("Choose a nature or seasonal theme to add a border.").setPositiveButton("OK",null).show();return;}
        ThemeStyle original=theme.style;int[] selected={original.density};boolean[] accepted={false};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Nature border density").setSingleChoiceItems(new String[]{"Off","Light","Lush"},selected[0],(d,i)->{selected[0]=i;preview(original.density(i));})
            .setPositiveButton("Save",(d,i)->accepted[0]=true).setNegativeButton("Cancel",null).create();
        dialog.setOnDismissListener(d->{preview(original);if(accepted[0])save(original.density(selected[0]));});
        dialog.setOnShowListener(d->transparentWindow(dialog));dialog.show();
    }
}
