package local.quiethome;

/** Pure validation for external-input identifiers and one-shot timers. */
final class ControlPolicy {
    static int hdmiPort(String uri){
        if(uri==null||!uri.matches("extInput:hdmi\\?port=[1-9][0-9]?"))return -1;
        int port=Integer.parseInt(uri.substring(uri.indexOf('=')+1));return port<=32?port:-1;
    }
    static String inputKey(int port){if(port<1||port>32)throw new IllegalArgumentException();return "local.quiethome.input.hdmi"+port;}
    static String inputUri(String key){
        if(key==null||!key.matches("local\\.quiethome\\.input\\.hdmi[1-9][0-9]?"))return null;
        int port=Integer.parseInt(key.substring(key.lastIndexOf('i')+1));return port<=32?"extInput:hdmi?port="+port:null;
    }
    static boolean artDim(int value){return value==0||value==15||value==30||value==50;}
    static boolean liveTimer(long due,long now,int savedBoot,int currentBoot){
        return savedBoot>=0&&savedBoot==currentBoot&&due>0&&now>=0&&due-now<=90*60000L&&now-due<=300000;
    }
    static boolean due(long due,long now,int savedBoot,int currentBoot){return liveTimer(due,now,savedBoot,currentBoot)&&now>=due;}
}
