package local.quiethome;

/** Invalidates worker results when the screen or its configuration changes. */
final class RequestGate {
    private volatile long generation;
    long ticket(){return generation;}
    void invalidate(){generation++;} // Called only from the UI thread.
    boolean accepts(long ticket){return ticket==generation;}
}
