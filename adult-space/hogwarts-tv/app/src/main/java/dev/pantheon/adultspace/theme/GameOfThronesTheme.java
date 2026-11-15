package dev.pantheon.adultspace.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STUB: a cold steel-and-ice palette. No pictures yet, so the castle backdrops stay.
 * To finish it, add assets/www/themes/got/{castle,shelf,greenhouse}.jpg (+ windows/mist if wanted)
 * and return them from images(), e.g. i.put("castle", "themes/got/castle.jpg").
 */
public final class GameOfThronesTheme implements ThemeData {
    @Override public String id() { return "got"; }
    @Override public String displayName() { return "Game of Thrones"; }
    @Override public int windowBackground() { return 0xFF05080B; }

    @Override public Map<String, String> colors() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("gold", "#9fb3c2");          // steel
        c.put("gold-hi", "#dfe9f0");       // ice
        c.put("ink", "#e9e4dc");           // bone
        c.put("accent", "#c9b28a");        // old brass
        c.put("accent-soft", "#d8d0c2");
        c.put("spine", "#b9c6cf");
        c.put("panel-1", "#0f1418");
        c.put("panel-2", "#040506");
        return c;
    }

    @Override public Map<String, String> images() { return Collections.emptyMap(); }
    @Override public boolean complete() { return false; }
}
