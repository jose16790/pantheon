package dev.pantheon.adultspace.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/** The original castle look from hogwarts-tv-home (ikevinadams, MIT). Pictures come from scripts/fetch-fanart.sh. */
public final class HogwartsTheme implements ThemeData {
    @Override public String id() { return "hogwarts"; }
    @Override public String displayName() { return "Hogwarts"; }
    @Override public int windowBackground() { return 0xFF000000; }

    @Override public Map<String, String> colors() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("gold", "#d9b45f");
        c.put("gold-hi", "#f3dc9a");
        c.put("ink", "#f4ead6");
        c.put("accent", "#f0c29c");
        c.put("accent-soft", "#f6d9bb");
        c.put("spine", "#e8c97e");
        c.put("panel-1", "#111a26");
        c.put("panel-2", "#05070b");
        return c;
    }

    @Override public Map<String, String> images() {
        Map<String, String> i = new LinkedHashMap<>();
        i.put("castle", "themes/hogwarts/img/castle.jpg");
        i.put("windowsA", "themes/hogwarts/img/windows_a_crop.png");
        i.put("windowsB", "themes/hogwarts/img/windows_b_crop.png");
        i.put("shelf", "themes/hogwarts/img/shelf.jpg");
        i.put("greenhouse", "themes/hogwarts/img/greenhouse_beasts.jpg");
        i.put("mist", "themes/hogwarts/img/mist.png");
        return i;
    }
}
