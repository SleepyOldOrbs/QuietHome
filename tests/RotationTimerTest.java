package local.quiethome;

public final class RotationTimerTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        RotationTimer timer=new RotationTimer(60000);timer.resume(1000);
        check(!timer.due(60999));check(timer.due(61000));
        timer.restart(100000);timer.pause(110000);
        check(!timer.due(10000000));timer.pause(200000); // Repeated pause must not consume time.
        timer.resume(10000000);check(timer.delay(10000000)==50000);
        timer.resume(10001000);check(timer.due(10050000)); // Repeated resume must not reset.
        timer.setInterval(300000,11000000);check(timer.delay(11000000)==300000);
        timer.setInterval(0,12000000);check(!timer.due(Long.MAX_VALUE));check(timer.delay(12000000)==Long.MAX_VALUE);
        timer.pause(12000001);timer.resume(13000000);check(!timer.due(14000000));
        timer.setInterval(900000,15000000);check(timer.due(15900000));
        try{timer.setInterval(-1,0);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("Rotation timing regression tests passed");
    }
}
