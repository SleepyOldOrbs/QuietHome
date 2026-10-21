package local.quiethome;

public final class LatestRequestTest {
    public static void main(String[] args) throws Exception {
        LatestRequest<Integer> mailbox=new LatestRequest<>();
        LatestRequest.Item<Integer> first=mailbox.offer(1);
        Thread sender=new Thread(()->{for(int i=2;i<=10000;i++)mailbox.offer(i);});sender.start();sender.join();
        if(mailbox.current(first)||mailbox.latest().value!=10000)throw new AssertionError("Latest navigation was lost");
        LatestRequest.Item<Integer> last=mailbox.latest();mailbox.clear();
        if(mailbox.latest()!=null||mailbox.current(last))throw new AssertionError("Paused work still accepted");
        LatestRequest.Item<Integer> resumed=mailbox.offer(1);
        if(!mailbox.current(resumed)||resumed.generation<=last.generation)throw new AssertionError("Resume accepted stale work");
        System.out.println("PASS: 10000 requests coalesce to latest; cancellation and resume reject stale work");
    }
}
