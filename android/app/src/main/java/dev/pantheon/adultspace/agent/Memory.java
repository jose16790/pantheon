package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * What the agent remembers, kept only on this TV (the app's private storage; backups are off for this app).
 *
 *   the conversation   the last few exchanges (15 minutes): follow-ups like "tell me more", "what about Austin?"
 *   the Council's picks the last cards it showed, so "play the second one" works
 *   what's been opened the titles you started from Pantheon (so the Council doesn't keep suggesting them)
 *   facts              up to 40 short things about you ("doesn't like horror", "follows NVIDIA"), learnt from what you
 *                      say (the chat model decides what's worth keeping) or told directly ("remember that…"); you can
 *                      ask what it knows, and have it forget one thing or everything
 * Each request carries these few hundred words at most, so memory never makes the agent slow or costly.
 */
final class Memory {
    private static final long CONVERSATION = 15 * 60_000L;
    private static final int TURNS = 6, FACTS = 40, OPENED = 15;

    private final SharedPreferences prefs;
    Memory(Context c) { prefs = c.getSharedPreferences("pantheon_agent_memory", Context.MODE_PRIVATE); }

    private synchronized JSONArray list(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private synchronized void save(String key, JSONArray a) { prefs.edit().putString(key, a.toString()).apply(); }

    private static JSONArray tail(JSONArray a, int n) {
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, a.length() - n); i < a.length(); i++) out.put(a.opt(i));
        return out;
    }

    // ---------------------------------------------------------------- the conversation
    synchronized void exchange(String said, String answered) {
        JSONArray t = list("turns");
        try { t.put(new JSONObject().put("at", System.currentTimeMillis()).put("you", said).put("hand", Speaker.plain(answered))); }
        catch (Exception ignored) { }
        save("turns", tail(t, TURNS));
    }

    synchronized void clearConversation() { prefs.edit().remove("turns").apply(); }

    /** The recent exchanges (the last 15 minutes) as plain lines, or "" if there are none. */
    synchronized String conversation(String you, String hand) {
        JSONArray t = list("turns");
        StringBuilder b = new StringBuilder();
        long now = System.currentTimeMillis();
        for (int i = 0; i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o == null || now - o.optLong("at") > CONVERSATION) continue;
            b.append(you).append(": ").append(o.optString("you")).append("\n").append(hand).append(": ").append(o.optString("hand")).append("\n");
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- the Council's last picks, and what's been opened
    synchronized void picks(JSONArray picks) {
        JSONArray p = new JSONArray();
        for (int i = 0; picks != null && i < picks.length(); i++) {
            JSONObject o = picks.optJSONObject(i);
            if (o == null) continue;
            try { p.put(new JSONObject().put("title", o.optString("title")).put("pkg", o.optString("pkg")).put("service", o.optString("service"))); }
            catch (Exception ignored) { }
        }
        prefs.edit().putString("picks", p.toString()).putLong("picksAt", System.currentTimeMillis()).apply();
    }

    /** The last picks, numbered, if shown in the last hour: "1. Upload (Prime Video)\n2. …". */
    synchronized String lastPicks() {
        if (System.currentTimeMillis() - prefs.getLong("picksAt", 0) > 3_600_000L) return "";
        JSONArray p = list("picks");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.length(); i++)
            b.append(i + 1).append(". ").append(p.optJSONObject(i).optString("title")).append(" (").append(p.optJSONObject(i).optString("service")).append(")\n");
        return b.toString();
    }

    synchronized JSONObject pick(int n) {
        JSONArray p = list("picks");
        return n >= 1 && n <= p.length() ? p.optJSONObject(n - 1) : null;
    }

    synchronized void opened(String title) {
        if (title == null || title.isEmpty()) return;
        JSONArray o = list("opened"), out = new JSONArray();
        for (int i = 0; i < o.length(); i++) if (!title.equalsIgnoreCase(o.optJSONObject(i).optString("title"))) out.put(o.opt(i));
        try { out.put(new JSONObject().put("title", title).put("at", System.currentTimeMillis())); } catch (Exception ignored) { }
        save("opened", tail(out, OPENED));
    }

    synchronized String openedTitles() {
        JSONArray o = list("opened");
        StringBuilder b = new StringBuilder();
        for (int i = o.length() - 1; i >= 0; i--) b.append(b.length() == 0 ? "" : ", ").append(o.optJSONObject(i).optString("title"));
        return b.toString();
    }

    // ---------------------------------------------------------------- facts
    synchronized JSONArray facts() { return list("facts"); }

    synchronized String factsText() {
        JSONArray f = facts();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < f.length(); i++) b.append("- ").append(f.optJSONObject(i).optString("t")).append("\n");
        return b.toString();
    }

    synchronized void addFact(String t) {
        if (t == null || t.trim().isEmpty()) return;
        JSONArray f = facts(), out = new JSONArray();
        for (int i = 0; i < f.length(); i++) if (!t.trim().equalsIgnoreCase(f.optJSONObject(i).optString("t"))) out.put(f.opt(i));
        try { out.put(new JSONObject().put("t", t.trim()).put("at", new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()))); }
        catch (Exception ignored) { }
        save("facts", tail(out, FACTS));                           // oldest drop off past 40
    }

    synchronized void removeFacts(JSONArray indexes) {
        JSONArray f = facts(), out = new JSONArray();
        java.util.Set<Integer> drop = new java.util.HashSet<>();
        for (int i = 0; indexes != null && i < indexes.length(); i++) drop.add(indexes.optInt(i, -1));
        for (int i = 0; i < f.length(); i++) if (!drop.contains(i)) out.put(f.opt(i));
        save("facts", out);
    }

    synchronized void forgetAll() {
        prefs.edit().clear().apply();
        Log.i(Agent.TAG, "memory cleared");
    }

    /** Everything the agent should keep in mind, for a prompt: who you are, what you've opened, the recent talk. */
    String context(String you, String hand) {
        StringBuilder b = new StringBuilder();
        String f = factsText(), o = openedTitles(), c = conversation(you, hand), p = lastPicks();
        if (!f.isEmpty()) b.append("What you know about the viewer (from earlier conversations):\n").append(f);
        if (!o.isEmpty()) b.append("Recently opened from the TV: ").append(o).append("\n");
        if (!p.isEmpty()) b.append("The Small Council's last picks, as shown on screen:\n").append(p);
        if (!c.isEmpty()) b.append("The conversation so far (most recent last):\n").append(c);
        return b.toString();
    }

    /**
     * After an exchange: the chat model decides whether it taught anything worth keeping about the viewer (tastes,
     * dislikes, interests, people, routines), and whether any old fact is now wrong. Small and cheap; off the UI thread.
     */
    void learn(OpenRouter or, String said, String answered) {
        try {
            JSONArray f = facts();
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < f.length(); i++) list.append(i).append(": ").append(f.optJSONObject(i).optString("t")).append("\n");
            JSONObject r = or.json("You keep a TV owner's long-term memory: short facts about the person (tastes in films and "
                    + "series, dislikes, interests they follow, people in the house, routines, how they like answers). From the "
                    + "latest exchange, add only what is clearly about the person and useful later; most exchanges add nothing. "
                    + "Remove facts the exchange shows are wrong. Never store passwords, money details, health or anything private. "
                    + "Reply with JSON only: {\"add\": [\"short fact\", ...], \"remove\": [indexes]}",
                    "Current facts:\n" + (list.length() == 0 ? "(none)\n" : list) + "\nThe viewer said: " + said
                            + "\nThe TV answered: " + Speaker.plain(answered), 200, 9000);
            removeFacts(r.optJSONArray("remove"));
            JSONArray add = r.optJSONArray("add");
            for (int i = 0; add != null && i < add.length(); i++) { addFact(add.optString(i)); Log.i(Agent.TAG, "remembered: " + add.optString(i)); }
        } catch (Exception e) { Log.w(Agent.TAG, "memory not updated: " + e.getMessage()); }
    }
}
