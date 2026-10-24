package com.aryston.helion.render.water;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Footprints {
    static final int MAX_PRINTS = 32;
    static final double LIFETIME_SECONDS = 45.0;

    private final Deque<Footprint> prints = new ArrayDeque<>();

    void add(List<Footprint> steps) {
        for (Footprint step : steps) {
            prints.addLast(step);
            if (prints.size() > MAX_PRINTS) {
                prints.removeFirst();
            }
        }
    }

    List<Footprint> visible(double seconds) {
        while (!prints.isEmpty() && (seconds - prints.peekFirst().seconds() > LIFETIME_SECONDS || seconds < prints.peekFirst().seconds())) {
            prints.removeFirst();
        }
        return new ArrayList<>(prints);
    }

    void clear() {
        prints.clear();
    }
}
