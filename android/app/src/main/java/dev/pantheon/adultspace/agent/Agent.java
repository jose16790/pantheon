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
    private final Speaker speaker;
    private final Memory memory;
    private int request;                                       // the newest council request; older answers are dropped
    private long voiceCouncilAt;                               // when a Council request last came by voice (its picks are announced)
    private String voiceCouncilAsk = "";

    public Agent(MainActivity act, Consumer<String> js) {
        this.act = act; this.js = js;
        tmdb = new Tmdb(act); or = new OpenRouter(act);
        memory = new Memory(act);
        council = new Council(act, tmdb, or, pool, memory);
        speaker = new Speaker(act, pool, js);
    }

    private String theme() { return act.themeId(); }

    /** Testing: start a fresh conversation (the facts it knows about you stay). */
    public void forgetConversation() { pool.execute(memory::clearConversation); }

    /** A title was opened from Pantheon (the Council's cards, or by voice): remembered, so it isn't suggested again. */
    public void opened(String title) { pool.execute(() -> memory.opened(title)); }

    private String[] names() { return Persona.names(theme()); }

    /** The exchange is over: kept for follow-ups, and anything worth knowing about you is learnt (in the background). */
    private void done(String said, String answered) {
        if (answered == null || answered.trim().isEmpty()) return;
        memory.exchange(said, answered);
        pool.execute(() -> memory.learn(or, said, answered));
    }

    /** A quick spoken "at once" for the commands the TV runs by itself (a recorded line: instant, free). */
    public void ack() { speaker.stock(theme(), "ack"); }

    public boolean ready() { return Keys.ready(act); }

    public void shutdown() { speaker.shutdown(); pool.shutdownNow(); }

    // ---------------------------------------------------------------- the agentic remote
    /** A request from the remote's panel: {minutes, episode, genres, like, kind}. Answers come back to the page. */
    public void recommend(String json) {
        final int id = ++request;
        pool.execute(() -> {
            JSONObject req;
            try { req = new JSONObject(json); } catch (Exception e) { req = new JSONObject(); }
            if (!ready()) { toPage(id, "onError", JSONObject.quote("The agent has no keys yet. Run scripts/agent-keys.sh on your computer.")); return; }
            council.recommend(req, new Council.Listener() {
                @Override public void picks(JSONObject r) {
                    toPage(id, "onPicks", r.toString());
                    if (System.currentTimeMillis() - voiceCouncilAt < 30_000) {      // you asked by voice: the Hand announces them
                        voiceCouncilAt = 0;
                        JSONArray p = r.optJSONArray("picks");
                        if (p != null && p.length() > 0) {
                            String line = Persona.picks(theme(), p.optJSONObject(0).optString("title"),
                                    p.length() > 1 ? p.optJSONObject(1).optString("title") : null, p.length() > 2 ? p.optJSONObject(2).optString("title") : null);
                            speaker.say(theme(), line);
                            done(voiceCouncilAsk, line);
                        }
                    }
                }
                @Override public void reasons(JSONObject r) { toPage(id, "onReasons", r.toString()); }
                @Override public void error(String m) {
                    toPage(id, "onError", JSONObject.quote(m));
                    if (System.currentTimeMillis() - voiceCouncilAt < 30_000) { voiceCouncilAt = 0; speaker.stock(theme(), "sorry"); }
                }
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
                  .put("play_title", "Names one specific film or series to watch now, or picks one of the Council's suggestions: "
                          + "'put on Dune', 'play the second one', 'let's watch Upload'.")
                  .put("control", "Wants the TV or Pantheon to do something: open an app, volume, playback, switch theme, go home.")
                  .put("question", "Asks a question to be answered in words: about a film, an actor, the weather, anything; "
                          + "or a follow-up to what was just said ('tell me more', 'and Austin?').")
                  .put("memory", "Asks the TV to remember something about them, to forget something, or what it knows about them.");
        } catch (Exception ignored) { }
    }

    /** What you said (after the simple commands the TV does by itself) -> Jev decides what kind of request it is. */
    public void voice(String text, Say say) {
        pool.execute(() -> {
            String route = "control";
            try {
                JSONObject q = new JSONObject().put("route", new JSONObject().put("type", "choice")
                        .put("instructions", "What kind of request did the viewer say to their TV?").put("criteria", ROUTES));
                JSONObject st = new JSONObject().put("said", text);
                String recent = memory.conversation("viewer", "tv"), picks = memory.lastPicks();
                if (!recent.isEmpty()) st.put("just_before", recent.length() > 600 ? recent.substring(recent.length() - 600) : recent);
                if (!picks.isEmpty()) st.put("suggestions_on_screen", picks);
                JSONObject a = or.decide(st, q, 4000).getJSONObject("route");
                route = a.optString("choice", "control");
                Log.i(TAG, "voice \"" + text + "\" -> " + route + " (" + a.optDouble("confidence") + ")");
                if (a.optDouble("confidence", 0) < 0.25) route = "control";      // unsure: the chat model with every action
            } catch (Exception e) { Log.w(TAG, "Jev route failed: " + e.getMessage()); }
            try {
                switch (route) {
                    case "memory":     remember(text, say); break;
                    case "recommend":  voiceCouncilAt = System.currentTimeMillis(); voiceCouncilAsk = text;
                                       String said = speaker.stock(theme(), "council");
                                       ui(() -> { js.accept("window.pantheonCouncil && pantheonCouncil.fromVoice(" + JSONObject.quote(text) + ")");
                                                  say.state("reply", reply(said != null ? Speaker.plain(said) : "Asking the council…")); }); break;
                    case "play_title": playTitle(text, say); break;
                    case "question":   answer(text, say); break;
                    default:           control(text, say);
                }
            } catch (Exception e) {
                Log.w(TAG, "voice failed: " + e);
                speaker.stock(theme(), "sorry");
                ui(() -> say.state("error", "The Ravens didn't return. Check the TV's internet."));
            }
        });
    }

    private void playTitle(String text, Say say) throws Exception {
        JSONObject t = or.json("Which film or series does the viewer want to watch? They may refer to the suggestions on "
                + "screen ('the second one'). Reply with JSON only: "
                + "{\"title\": \"its exact official title\", \"kind\": \"movie\", \"tv\" or \"any\", \"year\": the year if known or 0}",
                memory.context("viewer", "tv") + "\nThe viewer says: " + text, 80, 6000);
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
                    speaker.stock(theme(), "ack");
                    done(text, "Opening " + name + " on " + service + ".");
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

    private static final java.util.regex.Pattern NEWSY = java.util.regex.Pattern.compile(
            "(?i)\\b(news|headlines?|happening|going on|world|today'?s|latest|markets?|stocks?)\\b");
    private static final java.util.regex.Pattern DETAIL = java.util.regex.Pattern.compile(
            "(?i)\\b(in detail|detailed|tell me more|more about|explain|elaborate|everything|in depth|the full)\\b");
    private static final java.util.regex.Pattern FLIGHTS = java.util.regex.Pattern.compile(
            "(?i)\\b(flights?|delays?|delayed|airports?|ground stop|faa)\\b");

    /** A question: a recorded "one moment" at once, then the persona's answer from live sources, spoken and shown. */
    private void answer(String text, Say say) throws Exception {
        speaker.stock(theme(), "think");
        // the question goes first and alone: OpenRouter's web search searches with it (memory goes in the instructions)
        StringBuilder ctx = new StringBuilder(text).append("\n\n(Local time: ")
                .append(new java.text.SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", Locale.UK).format(new java.util.Date()))
                .append(". The TV is in ").append(where()).append(".)\n");
        if (NEWSY.matcher(text).find()) ctx.append("\nToday's headlines (from Google News, for the TV's country):\n").append(headlines());
        if (FLIGHTS.matcher(text).find()) ctx.append("\nLive US airport status from the FAA (only airports with delays are listed; "
                + "an airport not listed has no reported delay):\n").append(faa());
        boolean detailed = DETAIL.matcher(text).find();
        String mem = memory.context(names()[0], names()[1]);
        String out = or.talk(Persona.of(theme(), detailed) + (mem.isEmpty() ? "" : "\n\n" + mem), ctx.toString(), true, detailed ? 420 : 180, 20000);
        Log.i(TAG, "answer: " + out);
        speaker.say(theme(), out);
        ui(() -> say.state("reply", reply(Speaker.plain(out))));
        done(text, out);
    }

    /** "Remember that…", "forget that…", "forget everything", "what do you know about me?" */
    private void remember(String text, Say say) throws Exception {
        JSONArray f = memory.facts();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < f.length(); i++) list.append(i).append(": ").append(f.optJSONObject(i).optString("t")).append("\n");
        JSONObject r = or.json(Persona.of(theme()) + " Now you are handling a request about your memory of the viewer. Reply "
                + "with JSON only: {\"action\": \"remember\" | \"forget\" | \"forget_all\" | \"recall\", \"fact\": \"a short fact to "
                + "keep, for remember\", \"remove\": [indexes of facts to forget], \"reply\": \"what you say back, in character (for "
                + "recall: sum up what you know, briefly)\"}",
                "What you remember now:\n" + (list.length() == 0 ? "(nothing yet)\n" : list) + "\nThe viewer says: " + text, 260, 9000);
        String action = r.optString("action");
        if ("remember".equals(action)) memory.addFact(r.optString("fact"));
        else if ("forget".equals(action)) memory.removeFacts(r.optJSONArray("remove"));
        else if ("forget_all".equals(action)) memory.forgetAll();
        Log.i(TAG, "memory: " + action + (r.optString("fact").isEmpty() ? "" : " \"" + r.optString("fact") + "\"") + "; says: " + Speaker.plain(r.optString("reply")));
        String out = r.optString("reply", "");
        speaker.say(theme(), out);
        ui(() -> say.state("reply", reply(Speaker.plain(out))));
        if (!"forget_all".equals(action)) memory.exchange(text, out);
    }

    /** Where the TV is, in words ("Kitchener, Canada"): a bare "CA" reads as California. */
    private String where() {
        String cc = dev.pantheon.adultspace.weather.Weather.country(act), city = "";
        try { city = new JSONObject(act.getSharedPreferences("pantheon_weather", android.content.Context.MODE_PRIVATE).getString("place", "{}")).optString("city"); }
        catch (Exception ignored) { }
        String country = cc.isEmpty() ? "" : new Locale("", cc).getDisplayCountry(Locale.ENGLISH);
        return (city.isEmpty() ? "" : city + ", ") + (country.isEmpty() ? "an unknown country" : country);
    }

    private String headlines() {
        try {
            JSONObject n = new JSONObject(act.getSharedPreferences("pantheon_news", android.content.Context.MODE_PRIVATE).getString("json", "{}"));
            StringBuilder b = new StringBuilder();
            for (String k : new String[] { "national", "world", "business" }) {
                JSONArray a = n.optJSONArray(k);
                for (int i = 0; a != null && i < Math.min(6, a.length()); i++)
                    b.append("- [").append(k).append("] ").append(a.getJSONObject(i).optString("t")).append(" (").append(a.getJSONObject(i).optString("src")).append(")\n");
            }
            return b.length() == 0 ? "(none fetched yet)\n" : b.toString();
        } catch (Exception e) { return "(unavailable)\n"; }
    }

    private String faa() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://nasstatus.faa.gov/api/airport-status-information").openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(6000);
            String x;
            try (java.io.InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                x = b.toString("UTF-8");
            }
            finally { c.disconnect(); }
            return x.replaceAll("<(Name|ARPT)>", "\n$0").replaceAll("<[^>]+>", " ").replaceAll("[ \t]+", " ").trim() + "\n";
        } catch (Exception e) { return "(the FAA status feed didn't answer)\n"; }
    }

    private void control(String text, Say say) throws Exception {
        JSONObject a = or.json("You control a TV home screen called Pantheon. Turn the viewer's words into actions. Reply with "
                + "JSON only: {\"reply\": \"a few words on what you did\", \"actions\": [...]}. Actions: "
                + "{\"type\":\"launch\",\"app\":\"app name\"}, {\"type\":\"key\",\"key\":\"home|play_pause|play|pause|next|previous|volume_up|volume_down|mute\"}, "
                + "{\"type\":\"volume\",\"percent\":0-100}, {\"type\":\"search\",\"query\":\"title\"}, {\"type\":\"theme\",\"id\":\"got|hogwarts|marvel\"}, {\"type\":\"sleep\"} (turn the TV off). "
                + "Use no actions if none fit, and say so briefly in reply.", memory.context("viewer", "tv") + "\nThe viewer says: " + text, 200, 8000);
        ui(() -> {
            int done = Actions.run(act, a.toString());
            if (done > 0) speaker.stock(theme(), "ack"); else speaker.say(theme(), a.optString("reply"));
            memory.exchange(text, a.optString("reply"));
            say.state("reply", a.toString());
        });
    }

    private static String reply(String s) {
        try { return new JSONObject().put("reply", s).toString(); } catch (Exception e) { return "{}"; }
    }

    private void ui(Runnable r) { main.post(r); }

    static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
