package dev.pantheon.adultspace.voice;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * The agent thinks; the TV acts. An agent reply may carry actions, which Pantheon carries out here:
 *
 *   {"reply": "Opening Disney+", "actions": [{"type": "launch", "app": "Disney+"}]}
 *
 *   launch  {app}      open an installed app by its name ("disney plus", "Crave") or package
 *   key     {key}      home, play_pause, play, pause, next, previous, volume_up, volume_down, mute
 *   search  {query}    the TV's own search for a title
 *   volume  {percent}  set the volume (0-100)
 *   theme   {id}       switch Pantheon's theme: got, hogwarts, marvel
 *
 * Anything else is logged and skipped. Nothing here pays, signs in or types anything.
 */
public final class Actions {
    private static final String TAG = "Pantheon/Actions";
    private Actions() { }

    /** Run every action in the reply (if it has any). Returns how many ran. Call on the UI thread. */
    public static int run(Activity a, String replyBody) {
        JSONArray list;
        try { list = new JSONObject(replyBody).optJSONArray("actions"); } catch (Exception e) { return 0; }
        if (list == null) return 0;
        int done = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String type = o.optString("type");
            boolean ok;
            switch (type) {
                case "launch": ok = launch(a, o.optString("app")); break;
                case "key":    ok = key(a, o.optString("key")); break;
                case "search": ok = search(a, o.optString("query")); break;
                case "volume": ok = o.has("percent") && setVolume(a, o.optInt("percent")); break;
                case "theme":  ok = a instanceof dev.pantheon.adultspace.MainActivity && ((dev.pantheon.adultspace.MainActivity) a).switchTheme(o.optString("id")); break;
                default:       ok = false; Log.w(TAG, "unknown action " + o);
            }
            Log.i(TAG, (ok ? "✓ " : "✗ ") + o);
            if (ok) done++;
        }
        return done;
    }

    // ---------------------------------------------------------------- launch
    static boolean launch(Context c, String name) {
        if (name == null || name.isEmpty()) return false;
        PackageManager pm = c.getPackageManager();
        String pkg = installed(pm, name) ? name : findByLabel(pm, name);
        if (pkg == null) { Log.w(TAG, "no app called " + name); return false; }
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i != null && start(c, i);
    }

    /** The installed app whose name best matches: exact (ignoring case, spaces, "+" = "plus"), then contained. */
    private static String findByLabel(PackageManager pm, String name) {
        String want = norm(name);
        String partial = null;
        for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String label = norm(String.valueOf(r.loadLabel(pm)));
                if (label.isEmpty()) continue;
                if (label.equals(want)) return r.activityInfo.packageName;
                if (partial == null && (label.contains(want) || want.contains(label))) partial = r.activityInfo.packageName;
            }
        }
        return partial;
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("+", "plus").replaceAll("[^a-z0-9]", "");
    }

    private static boolean installed(PackageManager pm, String pkg) {
        if (!pkg.contains(".")) return false;
        try { pm.getApplicationInfo(pkg, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    // ---------------------------------------------------------------- keys
    static boolean key(Context c, String key) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (key) {
            case "home":                                       // Pantheon's home, never the TV's own (Google's)
                return start(c, new Intent(c, dev.pantheon.adultspace.MainActivity.class)
                        .setAction(Intent.ACTION_MAIN).putExtra(dev.pantheon.adultspace.MainActivity.EXTRA_GO_HOME, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            case "volume_up":   am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return true;
            case "volume_down": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return true;
            case "mute":        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return true;
            case "play_pause":  return media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            case "play":        return media(am, KeyEvent.KEYCODE_MEDIA_PLAY);
            case "pause":       return media(am, KeyEvent.KEYCODE_MEDIA_PAUSE);
            case "next":        return media(am, KeyEvent.KEYCODE_MEDIA_NEXT);
            case "previous":    return media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            default: Log.w(TAG, "no such key " + key); return false;
        }
    }

    /** Volume as a percentage of the TV's range (on a 0-100 TV, "30" is exactly 30). */
    public static boolean setVolume(Context c, int percent) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int index = Math.round(percent * max / 100f);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI);
        Log.i(TAG, "volume " + percent + "% -> " + index + "/" + max);
        return true;
    }

    /** n steps up or down (one step = one press of the remote's volume key). */
    public static boolean stepVolume(Context c, boolean up, int n) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        for (int i = 0; i < n; i++)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, i == n - 1 ? AudioManager.FLAG_SHOW_UI : 0);
        return true;
    }

    /** Media keys go to whatever is playing (no special permission needed). */
    private static boolean media(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        return true;
    }

    // ---------------------------------------------------------------- search
    static boolean search(Context c, String query) {
        if (query == null || query.isEmpty()) return false;
        return start(c, new Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, query))
                || start(c, new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query));
    }

    private static boolean start(Context c, Intent i) {
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (ActivityNotFoundException | SecurityException e) { Log.w(TAG, "couldn't start " + i + ": " + e.getMessage()); return false; }
    }
}
