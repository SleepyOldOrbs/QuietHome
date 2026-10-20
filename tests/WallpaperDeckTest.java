package local.quiethome;

import java.util.*;

public final class WallpaperDeckTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int size : new int[]{1, 2, 122}) {
            String[] catalog = new String[size];
            for (int i=0; i<size; i++) catalog[i] = "image-" + i;
            WallpaperDeck deck = new WallpaperDeck(catalog, null, 0, null, new Random(17));
            String previous = null;
            for (int cycle=0; cycle<20; cycle++) {
                Set<String> seen = new HashSet<>();
                for (int i=0; i<size; i++) {
                    String current = deck.current();
                    check(seen.add(current), "Duplicate inside cycle");
                    if (size>1) check(!current.equals(previous), "Adjacent repeat");
                    // Simulate process recreation at every position.
                    WallpaperDeck restored = new WallpaperDeck(
                        catalog, deck.order(), deck.position(), current, new Random(23 + i));
                    check(restored.current().equals(current), "Current image lost on restart");
                    check(restored.order().equals(deck.order()), "Cycle order lost on restart");
                    deck = restored;
                    previous = current;
                    deck.next();
                }
                check(seen.equals(new HashSet<>(Arrays.asList(catalog))), "Incomplete cycle");
            }
        }
        WallpaperDeck repaired = new WallpaperDeck(new String[]{"a","b","c"},
            Arrays.asList("a","a","b"), 1, "b", new Random(5));
        check(repaired.current().equals("b"), "Repair failed to preserve current image");
        check(new HashSet<>(repaired.order()).size()==3, "Corrupt state not repaired");
        WallpaperDeck empty = new WallpaperDeck(new String[0], null, 0, null, new Random(1));
        check(empty.current()==null && empty.next()==null, "Empty collection failed");
        System.out.println("PASS: 20 cycles at sizes 1, 2, 122; restart at every position; no repeats; corrupt-state repair; empty collection.");
    }
}
