package dev.pantheon.adultspace.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STUB: a bold red-and-gold palette. No pictures yet, so the castle backdrops stay.
 * To finish it, add assets/www/themes/marvel/{castle,shelf,greenhouse}.jpg and return them from images().
 */
public final class MarvelTheme implements ThemeData {
    @Override public String id() { return "marvel"; }
    @Override public String displayName() { return "Marvel"; }
    @Override public int windowBackground() { return 0xFF0A0203; }

    @Override public Map<String, String> colors() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("gold", "#e62429");          // red
        c.put("gold-hi", "#ffcf3f");       // gold
        c.put("ink", "#ffffff");
        c.put("accent", "#f2c14e");
        c.put("accent-soft", "#ffe3a3");
        c.put("spine", "#ffcf3f");
        c.put("panel-1", "#1a0b0d");
        c.put("panel-2", "#060203");
        return c;
    }

    @Override public Map<String, String> images() { return Collections.emptyMap(); }
    @Override public boolean complete() { return false; }
}
