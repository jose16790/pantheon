package dev.pantheon.adultspace.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An unofficial Game of Thrones fan theme (see NOTICE.md): a firelit throne room with live torch flames, a dragon in
 * place of the owl that breathes fire on the focused app, and the eight seasons in the library. Its scene is
 * assets/www/themes/got.js; its pictures are themes/got/img/ and themes/got/dragon/ (original AI-generated art, themes/got/tools/dragon_sprites.py).
 */
public final class GameOfThronesTheme implements ThemeData {
    @Override public String id() { return "got"; }
    @Override public String displayName() { return "Game of Thrones"; }
    @Override public int windowBackground() { return 0xFF000000; }

    @Override public Map<String, String> colors() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("gold", "#c9a45c");          // old gold
        c.put("gold-hi", "#f2d49a");       // firelight
        c.put("ink", "#ede5d8");           // bone
        c.put("accent", "#e8a070");        // ember
        c.put("accent-soft", "#f0cfae");
        c.put("spine", "#d8b470");
        c.put("panel-1", "#16100d");
        c.put("panel-2", "#000000");
        return c;
    }

    @Override public Map<String, String> images() {
        Map<String, String> i = new LinkedHashMap<>();
        i.put("castle", "themes/got/img/throne.jpg");
        i.put("shelf", "themes/got/img/shelf.jpg");
        return i;
    }

    @Override public String mascot() { return "dragon"; }
}
