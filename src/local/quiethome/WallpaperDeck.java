package local.quiethome;

import java.util.*;

/** Persistable shuffle bag: visit every asset once before reshuffling. */
final class WallpaperDeck {
    private final ArrayList<String> order = new ArrayList<>();
    private final Random random;
    private int position;

    WallpaperDeck(String[] catalog, List<String> savedOrder, int savedPosition,
                  String lastImage, Random random) {
        this.random = random;
        Set<String> available = new HashSet<>(Arrays.asList(catalog));
        if (savedOrder != null && savedOrder.size() == catalog.length &&
                new HashSet<>(savedOrder).equals(available) &&
                savedPosition >= 0 && savedPosition < savedOrder.size()) {
            order.addAll(savedOrder);
            position = savedPosition;
        } else {
            order.addAll(Arrays.asList(catalog));
            Collections.shuffle(order, random);
            // Preserve the visible image if a future update changes the collection.
            if (lastImage != null && order.remove(lastImage)) order.add(0, lastImage);
        }
    }

    String current() { return order.isEmpty() ? null : order.get(position); }
    int position() { return position; }
    List<String> order() { return new ArrayList<>(order); }

    /** Manual browsing wraps within the saved shuffle, so Left then Right is reversible. */
    String browse(int direction) {
        if(order.isEmpty())return null;
        position=(position+(direction<0?-1:1)+order.size())%order.size();
        return current();
    }

    String next() {
        if (order.isEmpty()) return null;
        String previous = current();
        if (++position >= order.size()) {
            Collections.shuffle(order, random);
            if (order.size() > 1 && order.get(0).equals(previous))
                Collections.swap(order, 0, 1 + random.nextInt(order.size() - 1));
            position = 0;
        }
        return current();
    }
}
