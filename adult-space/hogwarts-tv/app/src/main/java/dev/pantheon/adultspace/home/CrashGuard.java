package dev.pantheon.adultspace.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * If Pantheon crashes twice within 5 minutes, the Home watch stands aside for 10 minutes and leaves Google's home
 * on screen, so a broken Pantheon can never trap the TV. Installed by PantheonApp for every Pantheon process.
 */
public final class CrashGuard {
    private static final String TAG = "Pantheon/CrashGuard";
    private static final long WINDOW = 5 * 60_000L, ASIDE = 10 * 60_000L;
    private CrashGuard() { }

    public static void install(Context c) {
        Context app = c.getApplicationContext();
        Thread.UncaughtExceptionHandler next = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            try {
                SharedPreferences p = prefs(app);
                long now = System.currentTimeMillis(), prev = p.getLong("crash_last", 0);
                SharedPreferences.Editor e = p.edit().putLong("crash_last", now);
                if (now - prev < WINDOW) { e.putLong("aside_until", now + ASIDE); Log.e(TAG, "second crash in 5 min: standing aside for 10 min"); }
                e.commit();                                     // commit, not apply: the process is about to die
            } catch (Throwable ignored) { }
            if (next != null) next.uncaughtException(t, ex);
        });
    }

    public static boolean standingAside(Context c) { return System.currentTimeMillis() < prefs(c).getLong("aside_until", 0); }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("pantheon_crash", Context.MODE_PRIVATE); }
}
