package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The agent's API keys: OpenRouter (Jev and the chat model), TMDB (the catalogue) and, optionally, ElevenLabs (the voice). They live only in this app's
 * private storage on the TV (backups are off for this app), set from a computer by scripts/agent-keys.sh, and are
 * never logged or put in the repo.
 */
public final class Keys {
    private Keys() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pantheon_agent_keys", Context.MODE_PRIVATE);
    }

    public static String openRouter(Context c) { return prefs(c).getString("openrouter", ""); }
    public static String tmdb(Context c) { return prefs(c).getString("tmdb", ""); }
    public static String elevenLabs(Context c) { return prefs(c).getString("elevenlabs", ""); }

    public static void setElevenLabs(Context c, String key) { prefs(c).edit().putString("elevenlabs", key == null ? "" : key.trim()).apply(); }

    /** Both keys are set: the agent can run on the TV. */
    public static boolean ready(Context c) { return !openRouter(c).isEmpty() && !tmdb(c).isEmpty(); }

    /** Save whichever keys were given (null leaves one as it is; "" removes it). */
    public static void set(Context c, String openRouter, String tmdb) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (openRouter != null) e.putString("openrouter", openRouter.trim());
        if (tmdb != null) e.putString("tmdb", tmdb.trim());
        e.apply();
    }
}
