package dev.pantheon.adultspace.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dev.pantheon.adultspace.MainActivity;
import dev.pantheon.adultspace.agent.Keys;
import dev.pantheon.adultspace.home.HomeGuard;
import dev.pantheon.adultspace.voice.AgentConfig;

/**
 * The only way to configure or drive Pantheon from outside: commands from a computer over adb.
 *
 * Guarded by android.permission.DUMP (see AndroidManifest.xml): adb's shell holds it, but no ordinary app can be
 * granted it, so no other app on the TV can swap the agent's keys, spend its credits, or make it act. The main screen
 * (which must stay open to other apps, as the home screen) accepts only harmless requests: a theme, go home.
 *
 *   adb shell am broadcast -n dev.pantheon.adultspace/.control.AdbControl --es <name> <value> ...
 *
 *   openrouter_key, tmdb_key, elevenlabs_key   the agent's keys (scripts/agent-keys.sh)       never logged
 *   sleep_minutes N        the sleep timer (scripts/sleep-timer.sh; 0: off)
 *   pantheon_home_mode b   the Home takeover on/off (scripts/home.sh)
 *   agent_url URL          an external agent for voice, instead of the one on the TV
 *   ask "…"                testing: as if said to the mic          council '{json}'   testing: a Council request
 *   wx kind [wx_at ISO]    testing: a weather and time             sleep_test N        testing: N s before sleep
 *   fresh_conversation b   testing: a new conversation (the facts it knows stay)
 */
public final class AdbControl extends BroadcastReceiver {
    private static final String TAG = "Pantheon/Control";

    @Override public void onReceive(Context c, Intent i) {
        String or = i.getStringExtra("openrouter_key"), tm = i.getStringExtra("tmdb_key"), el = i.getStringExtra("elevenlabs_key");
        if (or != null || tm != null) Keys.set(c, or, tm);
        if (el != null) Keys.setElevenLabs(c, el);
        if (or != null || tm != null || el != null) Log.i(TAG, "keys updated; agent ready: " + Keys.ready(c));
        if (i.hasExtra("sleep_minutes"))
            c.getSharedPreferences("castle", Context.MODE_PRIVATE).edit().putInt("sleep_minutes", intExtra(i, "sleep_minutes", 30)).apply();
        if (i.hasExtra("pantheon_home_mode")) HomeGuard.setHomeMode(c, boolExtra(i, "pantheon_home_mode"));
        if (i.getStringExtra("agent_url") != null) AgentConfig.set(c, i.getStringExtra("agent_url"));
        // the rest act on the screen: handed to Pantheon (opened first if it isn't showing)
        if (i.hasExtra("ask") || i.hasExtra("council") || i.hasExtra("wx") || i.hasExtra("sleep_test") || i.hasExtra("fresh_conversation"))
            MainActivity.control(c, new Intent(i));
    }

    // adb's --es passes strings; accept --ei / --ez too
    public static int intExtra(Intent i, String k, int def) {
        Object v = i.getExtras() == null ? null : i.getExtras().get(k);
        try { return v instanceof Integer ? (Integer) v : Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return def; }
    }
    public static boolean boolExtra(Intent i, String k) {
        Object v = i.getExtras() == null ? null : i.getExtras().get(k);
        return v instanceof Boolean ? (Boolean) v : "true".equalsIgnoreCase(String.valueOf(v).trim());
    }
}
