package local.quiethome;
public final class ControlPolicyTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        for(int p=1;p<=32;p++){String uri="extInput:hdmi?port="+p;check(ControlPolicy.hdmiPort(uri)==p);check(uri.equals(ControlPolicy.inputUri(ControlPolicy.inputKey(p))));}
        for(String uri:new String[]{null,"","http://host/","extInput:hdmi?port=0","extInput:hdmi?port=01","extInput:hdmi?port=33","extInput:hdmi?port=1&other=2"})check(ControlPolicy.hdmiPort(uri)==-1);
        for(String key:new String[]{null,"example.app","local.quiethome.input.hdmi0","local.quiethome.input.hdmi33","local.quiethome.input.hdmi1/path"})check(ControlPolicy.inputUri(key)==null);
        check(!ControlPolicy.due(10000,9999,4,4));check(ControlPolicy.due(10000,10000,4,4));
        check(!ControlPolicy.due(10000,10000,4,5));check(!ControlPolicy.due(10000,310001,4,4));
        check(!ControlPolicy.liveTimer(10000,0,-1,-1));check(!ControlPolicy.liveTimer(5400001,0,1,1));
        check(ControlPolicy.liveTimer(5400000,0,1,1));
        for(int i=-1;i<=100;i++)check(ControlPolicy.artDim(i)==(i==0||i==15||i==30||i==50));
        System.out.println("PASS: input identifier round trips and injection bounds; timer boot, expiry and early-fire guards; dim presets");
    }
}
