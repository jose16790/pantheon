package dev.pantheon.adultspace.agent;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import dev.pantheon.adultspace.MainActivity;
import dev.pantheon.adultspace.voice.Actions;

/**
 * Pantheon's agent, running on the TV itself: the agentic remote ("what should I watch?") and the smarter voice.
 *
 *   TMDB         the catalogue, and which of your services has what, in the TV's country
 *   Jev          (OpenRouter, TypeSafe's decision model) routes each voice request and scores candidates: fast, cheap
 *   DeepSeek     (OpenRouter, a small fast chat model) understands free text and writes the short reasons and replies
 *
 * Needs both keys (scripts/agent-keys.sh). Every network call runs off the UI thread; results reach the page as
 * pantheonCouncil.* calls and the voice banner as replies.
 */
public final class Agent {
    public static final String TAG = "Pantheon/Agent";

    /** Voice replies: the same states the voice banner shows (heard, reply, error, council). */
    public interface Say { void state(String state, String text); }

    private final MainActivity act;
    private final Consumer<String> js;                         // runs a line of script in the page
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Tmdb tmdb;
    private final OpenRouter or;
    private final Council council;
    private int request;                                       // the newest council request; older answers are dropped

    public Agent(MainActivity act, Consumer<String> js) {
        this.act = act; this.js = js;
        tmdb = new Tmdb(act); or = new OpenRouter(act);
        council = new Council(act, tmdb, or, pool);
    }

    public boolean ready() { return Keys.ready(act); }

    public void shutdown() { pool.shutdownNow(); }

    // ---------------------------------------------------------------- the agentic remote
    /** A request from the remote's panel: {minutes, episode, genres, like, kind}. Answers come back to the page. */
    public void recommend(String json) {
        final int id = ++request;
        pool.execute(() -> {
            JSONObject req;
            try { req = new JSONObject(json); } catch (Exception e) { req = new JSONObject(); }
            if (!ready()) { toPage(id, "onError", JSONObject.quote("The agent has no keys yet. Run scripts/agent-keys.sh on your computer.")); return; }
            council.recommend(req, new Council.Listener() {
                @Override public void picks(JSONObject r) { toPage(id, "onPicks", r.toString()); }
                @Override public void reasons(JSONObject r) { toPage(id, "onReasons", r.toString()); }
                @Override public void error(String m) { toPage(id, "onError", JSONObject.quote(m)); }
            });
        });
    }

    private void toPage(int id, String fn, String arg) {
        if (id != request) return;                              // a newer request has replaced this one
        main.post(() -> js.accept("window.pantheonCouncil && pantheonCouncil." + fn + "(" + arg + ")"));
    }

    // ---------------------------------------------------------------- voice
    private static final JSONObject ROUTES = new JSONObject();
    static {
        try {
            ROUTES.put("recommend", "Wants ideas for what to watch: a mood, a genre, a time, 'something like…', 'what should I watch'.")
                  .put("play_title", "Names one specific film or series to watch now, or continue: 'put on Dune', 'play the new Bond film'.")
                  .put("control", "Wants the TV or Pantheon to do something: open an app, volume, playback, switch theme, go home.")
                  .put("question", "Asks a question to be answered in words: about a film, an actor, the weather, anything.");
        } catch (Exception ignored) { }
    }

    /** What you said (after the simple commands the TV does by itself) -> Jev decides what kind of request it is. */
    public void voice(String text, Say say) {
        pool.execute(() -> {
            String route = "control";
            try {
                JSONObject q = new JSONObject().put("route", new JSONObject().put("type", "choice")
                        .put("instructions", "What kind of request did the viewer say to their TV?").put("criteria", ROUTES));
                JSONObject a = or.decide(new JSONObject().put("said", text), q, 4000).getJSONObject("route");
                route = a.optString("choice", "control");
                Log.i(TAG, "voice \"" + text + "\" -> " + route + " (" + a.optDouble("confidence") + ")");
                if (a.optDouble("confidence", 0) < 0.25) route = "control";      // unsure: the chat model with every action
            } catch (Exception e) { Log.w(TAG, "Jev route failed: " + e.getMessage()); }
            try {
                switch (route) {
                    case "recommend":  ui(() -> { js.accept("window.pantheonCouncil && pantheonCouncil.fromVoice(" + JSONObject.quote(text) + ")");
                                                  say.state("reply", reply("Asking the council…")); }); break;
                    case "play_title": playTitle(text, say); break;
                    case "question":   answer(text, say); break;
                    default:           control(text, say);
                }
            } catch (Exception e) {
                Log.w(TAG, "voice failed: " + e);
                ui(() -> say.state("error", "The Ravens didn't return. Check the TV's internet."));
            }
        });
    }

    private void playTitle(String text, Say say) throws Exception {
        JSONObject t = or.json("Which film or series does the viewer want to watch? Reply with JSON only: "
                + "{\"title\": \"its exact official title\", \"kind\": \"movie\", \"tv\" or \"any\", \"year\": the year if known or 0}", text, 80, 6000);
        String title = t.optString("title", "").trim();
        if (title.isEmpty()) { control(text, say); return; }
        Services svc = Services.get(act, tmdb);
        JSONArray rs = tmdb.get("/search/multi", Tmdb.params("query", title, "include_adult", "false")).optJSONArray("results");
        String kind = t.optString("kind", "any");
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONObject r = rs.getJSONObject(i);
            String type = r.optString("media_type");
            if (!"movie".equals(type) && !"tv".equals(type) || !"any".equals(kind) && !kind.equals(type)) continue;
            JSONObject p = tmdb.get("/" + type + "/" + r.getInt("id") + "/watch/providers", null).optJSONObject("results");
            JSONObject here = p == null ? null : p.optJSONObject(svc.region);
            String name = r.optString("movie".equals(type) ? "title" : "name", title);
            for (String m : new String[] { "flatrate", "free", "ads" }) {
                JSONArray ps = here == null ? null : here.optJSONArray(m);
                for (int j = 0; ps != null && j < ps.length(); j++) {
                    int id = ps.getJSONObject(j).getInt("provider_id");
                    if (!svc.pkgFor.containsKey(id)) continue;
                    String pkg = svc.pkgFor.get(id), service = svc.nameFor.get(id);
                    ui(() -> { act.openTitle(pkg, name); say.state("reply", reply("Opening " + name + " on " + service)); });
                    return;
                }
            }
            JSONArray elsewhere = here == null ? null : here.optJSONArray("flatrate");
            String where = elsewhere != null && elsewhere.length() > 0 ? " It streams on " + elsewhere.getJSONObject(0).optString("provider_name") + "." : "";
            ui(() -> { act.openTitle(null, name); say.state("reply", reply(name + " isn't on your services." + where + " Here's what the TV found.")); });
            return;
        }
        ui(() -> { act.openTitle(null, title); say.state("reply", reply("Searching for " + title)); });
    }

    private void answer(String text, Say say) throws Exception {
        JSONObject a = or.json("You are the voice of a TV home screen. Answer the viewer's question in at most two short "
                + "sentences, plainly. If you don't know, say so. Reply with JSON only: {\"reply\": \"…\"}", text, 160, 8000);
        ui(() -> say.state("reply", a.toString()));
    }

    private void control(String text, Say say) throws Exception {
        JSONObject a = or.json("You control a TV home screen called Pantheon. Turn the viewer's words into actions. Reply with "
                + "JSON only: {\"reply\": \"a few words on what you did\", \"actions\": [...]}. Actions: "
                + "{\"type\":\"launch\",\"app\":\"app name\"}, {\"type\":\"key\",\"key\":\"home|play_pause|play|pause|next|previous|volume_up|volume_down|mute\"}, "
                + "{\"type\":\"volume\",\"percent\":0-100}, {\"type\":\"search\",\"query\":\"title\"}, {\"type\":\"theme\",\"id\":\"got|hogwarts|marvel\"}. "
                + "Use no actions if none fit, and say so briefly in reply.", text, 200, 8000);
        ui(() -> { Actions.run(act, a.toString()); say.state("reply", a.toString()); });
    }

    private static String reply(String s) {
        try { return new JSONObject().put("reply", s).toString(); } catch (Exception e) { return "{}"; }
    }

    private void ui(Runnable r) { main.post(r); }

    static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
