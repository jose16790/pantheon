package dev.pantheon.adultspace.voice;

import android.content.Context;
import android.util.Log;

import dev.pantheon.adultspace.R;

/**
 * Where the agent is. The built-in default is agent_url in strings.xml; a saved address overrides it, so moving
 * the agent (this Mac now, the Raspberry Pi later) needs no rebuild:
 *
 *   adb shell am start -n dev.pantheon.adultspace/.MainActivity --es agent_url http://192.168.0.22:8080/agent
 *   adb shell am start -n dev.pantheon.adultspace/.MainActivity --es agent_url default      (back to the built-in one)
 */
public final class AgentConfig {
    private AgentConfig() { }

    public static String url(Context c) {
        String saved = prefs(c).getString("agent_url", null);
        return saved != null ? saved : c.getString(R.string.agent_url);
    }

    /** Save a new address ("default" clears it). False if it isn't an http(s) URL. */
    public static boolean set(Context c, String url) {
        if (url == null) return false;
        url = url.trim();
        if (url.equalsIgnoreCase("default")) {
            prefs(c).edit().remove("agent_url").apply();
            Log.i(AgentClient.TAG, "agent endpoint -> built-in default " + c.getString(R.string.agent_url));
            return true;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) { Log.w(AgentClient.TAG, "not an http(s) URL: " + url); return false; }
        prefs(c).edit().putString("agent_url", url).apply();
        Log.i(AgentClient.TAG, "agent endpoint -> " + url);
        return true;
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pantheon", Context.MODE_PRIVATE);
    }
}
