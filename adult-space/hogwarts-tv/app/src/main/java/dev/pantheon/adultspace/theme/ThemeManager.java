package dev.pantheon.adultspace.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Which theme is active, kept across restarts. The page asks for it through the TV bridge
 * (TV.theme()) and gets {@link #toJson} of the current theme.
 *
 * Switch from a computer:  adb shell am start -n dev.pantheon.adultspace/.MainActivity --es theme got
 */
public final class ThemeManager {
    private static final String TAG = "Pantheon/Theme";
    public static final String DEFAULT = "hogwarts";

    /** Every theme, in the order they're offered. */
    public static final List<ThemeData> ALL = Collections.unmodifiableList(Arrays.asList(
            new HogwartsTheme(), new GameOfThronesTheme(), new MarvelTheme()));

    private final SharedPreferences prefs;

    public ThemeManager(Context c) { prefs = c.getSharedPreferences("pantheon", Context.MODE_PRIVATE); }

    public ThemeData current() {
        ThemeData t = find(prefs.getString("theme", DEFAULT));
        return t != null ? t : find(DEFAULT);
    }

    /** Make a theme the active one. False (and nothing changes) for an unknown id. */
    public boolean set(String id) {
        ThemeData t = find(id);
        if (t == null) { Log.w(TAG, "unknown theme: " + id); return false; }
        prefs.edit().putString("theme", t.id()).apply();
        Log.i(TAG, "theme -> " + t.id() + (t.complete() ? "" : " (stub: palette only)"));
        return true;
    }

    public static ThemeData find(String id) {
        if (id == null) return null;
        for (ThemeData t : ALL) if (t.id().equalsIgnoreCase(id.trim())) return t;
        return null;
    }

    public static JSONObject toJson(ThemeData t) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", t.id());
            o.put("name", t.displayName());
            o.put("mascot", t.mascot());
            o.put("complete", t.complete());
            o.put("colors", map(t.colors()));
            o.put("images", map(t.images()));
        } catch (JSONException ignored) { }
        return o;
    }

    /** [{id, name, complete}] for a picker. */
    public static String listJson() {
        JSONArray a = new JSONArray();
        for (ThemeData t : ALL) {
            JSONObject o = new JSONObject();
            try { o.put("id", t.id()); o.put("name", t.displayName()); o.put("complete", t.complete()); } catch (JSONException ignored) { }
            a.put(o);
        }
        return a.toString();
    }

    private static JSONObject map(Map<String, String> m) throws JSONException {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, String> e : m.entrySet()) o.put(e.getKey(), e.getValue());
        return o;
    }
}
