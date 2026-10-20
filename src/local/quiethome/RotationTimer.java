package local.quiethome;

/** Foreground-only wallpaper time, supplied in monotonic milliseconds. */
final class RotationTimer {
    private long interval,remaining,deadline;
    private boolean active;
    RotationTimer(long interval){setInterval(interval,0);}
    static boolean supported(long value){return value==0||value==60000||value==300000||value==900000;}
    long interval(){return interval;}
    void setInterval(long value,long now){
        if(!supported(value))throw new IllegalArgumentException("Invalid wallpaper interval");
        interval=value;restart(now);
    }
    void restart(long now){remaining=interval;deadline=now+remaining;}
    void resume(long now){if(!active){active=true;deadline=now+remaining;}}
    void pause(long now){if(active){remaining=Math.max(0,deadline-now);active=false;}}
    boolean due(long now){return active&&interval>0&&now>=deadline;}
    long delay(long now){return active&&interval>0?Math.max(0,deadline-now):Long.MAX_VALUE;}
}
