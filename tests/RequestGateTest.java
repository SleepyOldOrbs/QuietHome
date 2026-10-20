package local.quiethome;

public final class RequestGateTest {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        RequestGate gate=new RequestGate();
        long firstTown=gate.ticket();
        check(gate.accepts(firstTown));
        gate.invalidate(); // Same label, different coordinates.
        long secondTown=gate.ticket();
        check(!gate.accepts(firstTown));check(gate.accepts(secondTown));
        gate.invalidate(); // Switch back to the original town: old result stays obsolete.
        check(!gate.accepts(firstTown));check(!gate.accepts(secondTown));
        long beforePause=gate.ticket();gate.invalidate(); // Pause then resume.
        check(!gate.accepts(beforePause));check(gate.accepts(gate.ticket()));
        long beforeClear=gate.ticket();gate.invalidate(); // Clearing cannot be undone by a pending result.
        check(!gate.accepts(beforeClear));
        System.out.println("Request generation regression tests passed");
    }
}
