package dev.pantheon.adultspace.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Keeps Google's home out of sight without switching it off.
 *
 * Google's home (launcherx) must stay installed and on: on some Google TVs, switching it off leaves the TV stuck
 * in a restart loop (learnt by couch-tv, docs/TROUBLESHOOTING.md there). So it stays on, Pantheon is the chosen
 * home, and this watch puts Pantheon straight back whenever Google's home comes forward: at power on, and from the
 * remote's Home button. Google's home can flash up for a moment first.
 *
 * Only Google's actual home screen is replaced. Its other screens are left alone: the dashboard you get by holding
 * Home (with the Settings gear), a show's details page, the programmable-button setup. A home screen window is
 * replaced when it is the home activity itself, or when Home was just pressed (some home windows only report a
 * generic class name). Home presses are seen, never blocked: holding Home still opens the dashboard.
 *
 * It reads nothing on screen (res/xml/home_watch.xml), and stands aside if Pantheon keeps crashing
 * ({@link CrashGuard}), so the TV can never be stuck with a broken Pantheon.
 */
public final class HomeWatch extends AccessibilityService {
    private static final String TAG = "Pantheon/HomeWatch";
    static final String[] GOOGLE_HOMES = { "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher" };
    /** The home screens themselves. Anything else from these apps (dashboard, details pages...) is left alone. */
    private static final String[] HOME_SCREENS = {
            "com.google.android.apps.tv.launcherx.home.HomeActivity", "com.google.android.tvlauncher.MainActivity" };
    private long last, homePressed;

    @Override protected void onServiceConnected() {
        Log.i(TAG, "on: Google's home will hand over to Pantheon");
        if (HomeGuard.consumeKick(this) && !CrashGuard.standingAside(this)) bringPantheon();   // just put back: Google's home is likely showing
    }

    private void bringPantheon() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(getPackageName(), getPackageName() + ".HomeAlias"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException ex) {
            Log.e(TAG, "couldn't bring Pantheon back: " + ex);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        boolean google = false;
        for (String h : GOOGLE_HOMES) google |= h.equals(pkg);
        if (!google) return;
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        boolean homeScreen = false;
        for (String h : HOME_SCREENS) homeScreen |= h.equals(cls);
        boolean afterHomeKey = System.currentTimeMillis() - homePressed < 2000 && cls.startsWith("android.widget.");
        if (!homeScreen && !afterHomeKey) { Log.d(TAG, "left alone: " + cls); return; }
        if (CrashGuard.standingAside(this)) { Log.w(TAG, "Pantheon crashed recently: leaving Google's home on screen for now"); return; }
        long now = System.currentTimeMillis();
        if (now - last < 1500) return;                       // one hand-over per Home press
        last = now;
        Log.i(TAG, "Google's home came forward (" + e.getClassName() + "): back to Pantheon");
        bringPantheon();
    }

    /** Notes a short press of Home (not a hold, which opens the dashboard). Never blocks the key. */
    @Override protected boolean onKeyEvent(KeyEvent k) {
        if (k.getKeyCode() == KeyEvent.KEYCODE_HOME && k.getAction() == KeyEvent.ACTION_UP && !k.isCanceled()
                && k.getEventTime() - k.getDownTime() < 500)
            homePressed = System.currentTimeMillis();
        return false;
    }

    @Override public void onInterrupt() { }
}
