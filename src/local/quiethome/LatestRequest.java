package local.quiethome;

/** A bounded last-request-wins mailbox; navigation never queues unbounded decode jobs. */
final class LatestRequest<T> {
    static final class Item<T> {
        final long generation; final T value;
        Item(long generation,T value){this.generation=generation;this.value=value;}
    }
    private long generation;
    private Item<T> latest;
    synchronized Item<T> offer(T value){latest=new Item<>(++generation,value);return latest;}
    synchronized Item<T> latest(){return latest;}
    synchronized boolean current(Item<T> item){return latest==item;}
    synchronized void clear(){generation++;latest=null;}
}
