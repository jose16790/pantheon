package dev.pantheon.adultspace.agent;

import android.content.Context;

import org.json.JSONObject;

/** Jev's typed decisions for the rest of the app (e.g. sorting the headlines), with the agent's OpenRouter key. */
public final class Decide {
    private Decide() { }

    public static boolean ready(Context c) { return !Keys.openRouter(c).isEmpty(); }

    /** questions: {name: {type: choice|noul|score, instructions, criteria}} -> answers {name: {...}}. */
    public static JSONObject decide(Context c, JSONObject state, JSONObject questions, int timeoutMs) throws Exception {
        return new OpenRouter(c).decide(state, questions, timeoutMs);
    }
}
