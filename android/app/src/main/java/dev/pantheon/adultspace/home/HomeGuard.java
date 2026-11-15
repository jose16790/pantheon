package dev.pantheon.adultspace.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Keeps the Home watch switched on while Home mode is on (scripts/home.sh apply).
 *
 * Android silently removes an app's accessibility service from the enabled list when the app is force-stopped
 * (Settings > Apps > Force stop, or `am force-stop`), and the TV then wakes to Google's home. So Pantheon puts its
 * watch back itself: at boot, after an update, whenever the screen turns on (PantheonVoiceInteractionService listens
 * while Pantheon is the assistant), and whenever Pantheon opens. That needs WRITE_SECURE_SETTINGS, which only adb can
 * grant (home.sh does), and it touches nothing but Pantheon's own entry in that list.
 */
public final class HomeGuard {
    private static final String TAG = "Pantheon/HomeGuard";
    private HomeGuard() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("pantheon", Context.MODE_PRIVATE); }

    /** Home mode on/off: set by scripts/home.sh (am start ... --ez pantheon_home_mode true|false). */
    public static void setHomeMode(Context c, boolean on) {
        prefs(c).edit().putBoolean("home_mode", on).apply();
        Log.i(TAG, "home mode " + (on ? "on" : "off"));
    }

    public static boolean homeMode(Context c) { return prefs(c).getBoolean("home_mode", false); }

    /** If Home mode is on and the watch has been switched off, switch it back on. True if it had to. */
    public static boolean ensure(Context c, String why) {
        if (!homeMode(c)) return false;
        String me = new ComponentName(c, HomeWatch.class).flattenToShortString();
        String full = new ComponentName(c, HomeWatch.class).flattenToString();
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean listed = false;
        if (list != null) for (String s : list.split(":")) listed |= s.equalsIgnoreCase(me) || s.equalsIgnoreCase(full);
        boolean on = Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
        if (listed && on) return false;
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "the Home watch is off (" + why + ") and Pantheon may not switch it back on: run scripts/home.sh apply");
            return false;
        }
        try {
            String next = listed ? list : (list == null || list.isEmpty() || "null".equals(list) ? me : list + ":" + me);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            prefs(c).edit().putLong("kick_at", System.currentTimeMillis()).apply();   // the watch brings Pantheon forward when it starts
            Log.i(TAG, "the Home watch was off (" + why + "): switched it back on");
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "couldn't switch the Home watch back on: " + e.getMessage());
            return false;
        }
    }

    /** Whether the watch, just (re)started, should bring Pantheon forward (it was put back moments ago). */
    static boolean consumeKick(Context c) {
        long at = prefs(c).getLong("kick_at", 0);
        prefs(c).edit().remove("kick_at").apply();
        return System.currentTimeMillis() - at < 30_000;
    }
}
