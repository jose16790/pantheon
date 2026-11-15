package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * "What should I watch?": the agentic remote's recommender.
 *
 *   request   {minutes: 0 (any) | 20..180, episode: bool, genres: ["thriller", ...], like: "free text", kind: any|movie|tv}
 *   1. understand  the free text, if any (the chat model): titles it's like, genres, time, movie or series
 *   2. find        candidates on TMDB that are on your services in this country (what the referenced titles' fans
 *                  watch, and the best-liked titles in those genres), with runtimes that fit the time you have
 *   3. decide      Jev scores every candidate against the request in one call; the best 8 are kept (movies and series)
 *   4. explain     the chat model writes one line on why each fits (sent after the picks, so the cards show at once)
 */
final class Council {
    interface Listener {
        void picks(JSONObject result);        // {picks: [...], summary}
        void reasons(JSONObject reasons);     // {key: reason}
        void error(String message);
    }

    private final Context ctx;
    private final Tmdb tmdb;
    private final OpenRouter or;
    private final ExecutorService pool;
    private final Memory memory;

    Council(Context c, Tmdb tmdb, OpenRouter or, ExecutorService pool, Memory memory) {
        this.ctx = c.getApplicationContext(); this.tmdb = tmdb; this.or = or; this.pool = pool; this.memory = memory;
    }

    void recommend(JSONObject req, Listener out) {
        long t0 = System.currentTimeMillis();
        try {
            Services svc = Services.get(ctx, tmdb);
            if (svc.pkgFor.isEmpty()) { out.error("None of this TV's apps are streaming services TMDB knows in " + svc.region + "."); return; }

            // 1. understand
            int minutes = req.optInt("minutes", 0);
            String kind = req.optString("kind", "any");
            Set<String> genres = new HashSet<>();
            JSONArray g = req.optJSONArray("genres");
            if (g != null) for (int i = 0; i < g.length(); i++) genres.add(g.optString(i).toLowerCase(Locale.ROOT));
            String like = req.optString("like", "").trim();
            List<String> refs = new ArrayList<>(), ideas = new ArrayList<>();
            String mood = "";
            boolean pickedGenres = !genres.isEmpty();
            if (!like.isEmpty()) {
                JSONObject u = understand(like);
                JSONArray ts = u.optJSONArray("titles");
                if (ts != null) for (int i = 0; i < ts.length() && refs.size() < 2; i++) refs.add(ts.optString(i));
                JSONArray ug = u.optJSONArray("genres");
                if (ug != null && !pickedGenres) for (int i = 0; i < ug.length(); i++) { String x = ug.optString(i).toLowerCase(Locale.ROOT); if (Tmdb.GENRES.containsKey(x)) genres.add(x); }
                JSONArray sg = u.optJSONArray("suggest");
                if (sg != null) for (int i = 0; i < sg.length() && ideas.size() < 20; i++) ideas.add(sg.optString(i));
                if (minutes == 0) minutes = u.optInt("minutes", 0);
                if ("any".equals(kind)) kind = u.optString("kind", "any");
                mood = u.optString("mood", "");
            }
            final int mins = minutes; final String knd = kind;
            Log.i(Agent.TAG, "council: " + mins + " min, " + knd + ", genres " + genres + ", like " + refs + ", ideas " + ideas.size() + (mood.isEmpty() ? "" : ", mood " + mood));

            // 2. find
            Map<String, JSONObject> cands = new LinkedHashMap<>();       // "movie:603" -> {id, type, ref}
            List<Future<List<JSONObject>>> found = new ArrayList<>();
            for (String r : ideas) found.add(pool.submit(() -> lookUp(r, knd)));        // the chat model's ideas, checked on TMDB
            for (String r : refs) found.add(pool.submit(() -> similarTo(r)));
            for (String type : new String[] { "movie", "tv" }) {
                if (!"any".equals(knd) && !knd.equals(type)) continue;
                for (int page = 1; page <= 2; page++) { final int p = page; found.add(pool.submit(() -> discover(type, genres, mins, svc, p))); }
            }
            Set<String> refKeys = new HashSet<>();
            for (Future<List<JSONObject>> f : found) {
                try {
                    for (JSONObject o : f.get(8, TimeUnit.SECONDS)) {
                        String key = o.getString("type") + ":" + o.getInt("id");
                        if (o.optBoolean("isRef")) { refKeys.add(key); continue; }
                        if (!"any".equals(knd) && !knd.equals(o.getString("type"))) continue;
                        cands.putIfAbsent(key, o);
                    }
                } catch (Exception e) { Log.w(Agent.TAG, "a search failed: " + e.getMessage()); }
            }
            for (String k : refKeys) cands.remove(k);                    // not the titles you named
            List<JSONObject> list = new ArrayList<>(cands.values());
            if (list.size() > 40) list = list.subList(0, 40);

            // details: runtime, genres, where it streams for you
            List<Future<JSONObject>> det = new ArrayList<>();
            for (JSONObject c : list) det.add(pool.submit(() -> details(c, svc)));
            List<JSONObject> fit = new ArrayList<>();
            for (Future<JSONObject> f : det) {
                try {
                    JSONObject d = f.get(8, TimeUnit.SECONDS);
                    if (d == null) continue;
                    int rt = d.optInt("runtime", 0);
                    boolean series = "tv".equals(d.getString("type"));
                    if (mins > 0 && rt > 0 && rt > (series ? mins + 5 : mins + 10)) continue;   // doesn't fit the time you have
                    fit.add(d);
                } catch (Exception e) { Log.w(Agent.TAG, "details failed: " + e.getMessage()); }
            }
            if (fit.isEmpty()) { out.error("Nothing on your services fits that. Try more time or other genres."); return; }
            if (fit.size() > 30) fit = fit.subList(0, 30);
            Log.i(Agent.TAG, "council: " + fit.size() + " candidates fit, after " + (System.currentTimeMillis() - t0) + " ms");

            // 3. decide
            String ask = describe(mins, req.optBoolean("episode"), knd, genres, like);
            List<JSONObject> picks = decide(ask, fit, knd);
            JSONArray arr = new JSONArray();
            for (JSONObject p : picks) arr.put(p);
            memory.picks(arr);                                      // so "play the second one" works
            out.picks(new JSONObject().put("picks", arr).put("summary", ask).put("region", svc.region));
            StringBuilder names = new StringBuilder();
            for (JSONObject p : picks) names.append(names.length() == 0 ? "" : "; ").append(p.optString("title")).append(" (").append(p.optString("type"))
                    .append(", ").append(p.optString("service")).append(", fit ").append(String.format(Locale.ROOT, "%.2f", p.optDouble("fit"))).append(")");
            Log.i(Agent.TAG, "council: " + picks.size() + " picks after " + (System.currentTimeMillis() - t0) + " ms: " + names);

            // 4. explain
            try { out.reasons(explain(ask, picks)); Log.i(Agent.TAG, "council: reasons after " + (System.currentTimeMillis() - t0) + " ms"); }
            catch (Exception e) { Log.w(Agent.TAG, "reasons failed: " + e.getMessage()); }
        } catch (Exception e) {
            Log.w(Agent.TAG, "council failed: " + e);
            out.error(e instanceof Http.Error && ((Http.Error) e).status == 401 ? "A key was refused. Run scripts/agent-keys.sh again."
                    : "The Ravens didn't return. Check the TV's internet and try again.");
        }
    }

    private JSONObject understand(String text) throws Exception {
        String sys = "You turn a TV viewer's request into search hints. Reply with JSON only: "
                + "{\"titles\": [the film or series titles the viewer NAMES as a reference (\"like Severance\" -> \"Severance\"), up to 2,"
                + " exact official titles; never suggest titles of your own; [] if none],"
                + " \"genres\": [any of: " + String.join(", ", Tmdb.GENRES.keySet()) + "],"
                + " \"minutes\": number of minutes they have, or 0 if not said,"
                + " \"kind\": \"movie\" or \"tv\" only if they explicitly ask for a film or a series, else \"any\","
                + " \"suggest\": [20 well-known, well-liked films and series that fit the request closely, exact official titles, a mix of both"
                + " unless they asked for one kind; not the titles they named],"
                + " \"mood\": a few words on the feel they want}";
        String known = memory.factsText(), opened = memory.openedTitles();
        String user = text + (known.isEmpty() ? "" : "\n\nWhat we know about this viewer:\n" + known)
                + (opened.isEmpty() ? "" : "\nRecently watched (don't suggest these): " + opened);
        try { return or.json(sys, user, 700, 9000); }
        catch (Exception e) { Log.w(Agent.TAG, "understand failed: " + e.getMessage()); return new JSONObject(); }
    }

    /** One suggested title, found on TMDB (the best match of the kind asked for). */
    private List<JSONObject> lookUp(String title, String kind) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        JSONArray rs = tmdb.get("/search/multi", Tmdb.params("query", title, "include_adult", "false")).optJSONArray("results");
        for (int i = 0; rs != null && i < Math.min(5, rs.length()); i++) {
            JSONObject r = rs.getJSONObject(i);
            String type = r.optString("media_type");
            if (!"movie".equals(type) && !"tv".equals(type) || !"any".equals(kind) && !kind.equals(type)) continue;
            out.add(new JSONObject().put("id", r.getInt("id")).put("type", type).put("idea", true));
            break;
        }
        return out;
    }

    /** What fans of a title watch: TMDB's recommendations and similar titles (the title itself is marked isRef). */
    private List<JSONObject> similarTo(String title) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        JSONArray rs = tmdb.get("/search/multi", Tmdb.params("query", title, "include_adult", "false")).optJSONArray("results");
        if (rs == null) return out;
        for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.getJSONObject(i);
            String type = r.optString("media_type");
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            int id = r.getInt("id");
            out.add(new JSONObject().put("id", id).put("type", type).put("isRef", true));
            for (String rel : new String[] { "recommendations", "similar" }) {
                JSONArray xs = tmdb.get("/" + type + "/" + id + "/" + rel, null).optJSONArray("results");
                if (xs == null) continue;
                for (int j = 0; j < Math.min(14, xs.length()); j++)
                    out.add(new JSONObject().put("id", xs.getJSONObject(j).getInt("id")).put("type", type).put("ref", title));
            }
            break;
        }
        return out;
    }

    /** The best-liked titles on your services in these genres, short enough for the time you have. */
    private List<JSONObject> discover(String type, Set<String> genres, int minutes, Services svc, int page) throws Exception {
        StringBuilder gs = new StringBuilder();
        for (String g : genres) {
            int[] ids = Tmdb.GENRES.get(g);
            int id = ids == null ? 0 : ids["movie".equals(type) ? 0 : 1];
            if (id != 0 && gs.indexOf(String.valueOf(id)) < 0) gs.append(gs.length() == 0 ? "" : "|").append(id);
        }
        Map<String, String> p = Tmdb.params("watch_region", svc.region, "with_watch_providers", svc.providerIds(),
                "with_watch_monetization_types", "flatrate|free|ads", "sort_by", "popularity.desc",
                "vote_count.gte", "movie".equals(type) ? "800" : "400", "vote_average.gte", "7.0", "page", String.valueOf(page),
                "include_adult", "false");
        if (gs.length() > 0) p.put("with_genres", gs.toString());
        if (minutes > 0) p.put("with_runtime.lte", String.valueOf("movie".equals(type) ? minutes + 10 : minutes + 5));
        List<JSONObject> out = new ArrayList<>();
        JSONArray rs = tmdb.get("/discover/" + type, p).optJSONArray("results");
        if (rs != null) for (int i = 0; i < rs.length(); i++) out.add(new JSONObject().put("id", rs.getJSONObject(i).getInt("id")).put("type", type));
        return out;
    }

    /** One title's card: what it is, how long, and which of your apps has it (null if none of them does). */
    private JSONObject details(JSONObject c, Services svc) throws Exception {
        String type = c.getString("type");
        JSONObject d = tmdb.get("/" + type + "/" + c.getInt("id"), Tmdb.params("append_to_response", "watch/providers"));
        JSONObject here = d.optJSONObject("watch/providers") == null ? null
                : d.getJSONObject("watch/providers").optJSONObject("results") == null ? null
                : d.getJSONObject("watch/providers").getJSONObject("results").optJSONObject(svc.region);
        if (here == null) return null;
        int provider = 0;
        for (String m : new String[] { "flatrate", "free", "ads" }) {
            JSONArray ps = here.optJSONArray(m);
            if (ps == null) continue;
            for (int i = 0; i < ps.length() && provider == 0; i++) { int id = ps.getJSONObject(i).getInt("provider_id"); if (svc.pkgFor.containsKey(id)) provider = id; }
            if (provider != 0) break;
        }
        if (provider == 0) return null;
        boolean series = "tv".equals(type);
        int runtime = series ? episodeRuntime(d) : d.optInt("runtime", 0);
        String date = d.optString(series ? "first_air_date" : "release_date", "");
        JSONArray gs = d.optJSONArray("genres"); StringBuilder gn = new StringBuilder();
        if (gs != null) for (int i = 0; i < gs.length(); i++) gn.append(i == 0 ? "" : ", ").append(gs.getJSONObject(i).optString("name"));
        String overview = d.optString("overview", "");
        return new JSONObject()
                .put("key", type + ":" + c.getInt("id")).put("id", c.getInt("id")).put("type", type)
                .put("title", d.optString(series ? "name" : "title"))
                .put("year", date.length() >= 4 ? date.substring(0, 4) : "")
                .put("runtime", runtime)
                .put("seasons", series ? d.optInt("number_of_seasons", 0) : 0)
                .put("genres", gn.toString())
                .put("rating", Math.round(d.optDouble("vote_average", 0) * 10) / 10.0)
                .put("votes", d.optInt("vote_count", 0))
                .put("overview", overview.length() > 320 ? overview.substring(0, 317) + "…" : overview)
                .put("poster", d.isNull("poster_path") ? "" : Tmdb.IMG + "w342" + d.optString("poster_path"))
                .put("backdrop", d.isNull("backdrop_path") ? "" : Tmdb.IMG + "w780" + d.optString("backdrop_path"))
                .put("service", svc.nameFor.get(provider)).put("pkg", svc.pkgFor.get(provider))
                .put("ref", c.optString("ref", ""));
    }

    private static int episodeRuntime(JSONObject d) {
        JSONArray e = d.optJSONArray("episode_run_time");
        if (e != null && e.length() > 0) return e.optInt(0);
        JSONObject last = d.optJSONObject("last_episode_to_air");
        return last == null ? 0 : last.optInt("runtime", 0);
    }

    private static String describe(int minutes, boolean episode, String kind, Set<String> genres, String like) {
        StringBuilder b = new StringBuilder("Something to watch");
        if ("movie".equals(kind)) b.append(", a movie"); else if ("tv".equals(kind)) b.append(", a series");
        if (episode) b.append(", an episode of a series");
        if (minutes > 0) b.append(", with about ").append(minutes).append(" minutes to spare");
        if (!genres.isEmpty()) b.append(", in the mood for ").append(String.join(" or ", genres));
        if (!like.isEmpty()) b.append(". In their words: \"").append(like).append("\"");
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        b.append(". It is ").append(h < 5 || h >= 22 ? "late at night" : h < 12 ? "morning" : h < 17 ? "afternoon" : "evening").append(".");
        return b.toString();
    }

    /** Jev scores every candidate in one call; the best 8 are kept, with a few series and movies each when either fits. */
    private List<JSONObject> decide(String ask, List<JSONObject> fit, String kind) {
        JSONObject questions = new JSONObject();
        StringBuilder list = new StringBuilder();
        try {
            for (int i = 0; i < fit.size(); i++) {
                JSONObject c = fit.get(i);
                String line = c.getString("title") + " (" + c.optString("year") + ", " + ("tv".equals(c.getString("type")) ? "series, "
                        + c.optInt("seasons") + " seasons, ~" + c.optInt("runtime") + " min episodes" : "movie, " + c.optInt("runtime") + " min")
                        + "; " + c.optString("genres") + "; rated " + c.optDouble("rating") + "): " + c.optString("overview");
                list.append("c").append(i).append(": ").append(line).append("\n");
                questions.put("c" + i, new JSONObject().put("type", "score")
                        .put("instructions", "How well does candidate c" + i + " (" + c.getString("title") + ") suit the viewer's request right now?")
                        .put("criteria", new JSONArray().put("Poor fit: wrong feel, length or kind").put("Decent fit").put("Great fit: exactly what they asked for")));
            }
            JSONObject state = new JSONObject().put("viewer_request", ask).put("candidates", list.toString());
            String known = memory.factsText(), opened = memory.openedTitles();
            if (!known.isEmpty()) state.put("what_we_know_about_the_viewer", known);
            if (!opened.isEmpty()) state.put("recently_watched_so_a_poor_fit_now", opened);
            JSONObject ans = or.decide(state, questions, 6000);
            for (int i = 0; i < fit.size(); i++) {
                JSONObject a = ans.optJSONObject("c" + i);
                fit.get(i).put("fit", a == null ? 0 : a.optDouble("score", 0));
            }
        } catch (Exception e) {
            Log.w(Agent.TAG, "Jev failed (" + e.getMessage() + "): ranking by rating instead");
            for (JSONObject c : fit) try { c.put("fit", c.optDouble("rating") / 5 + Math.log10(1 + c.optInt("votes")) / 10); } catch (Exception ignored) { }
        }
        List<JSONObject> sorted = new ArrayList<>(fit);
        sorted.sort((a, b) -> Double.compare(b.optDouble("fit") + b.optDouble("rating") / 100, a.optDouble("fit") + a.optDouble("rating") / 100));
        List<JSONObject> picks = new ArrayList<>();
        if ("any".equals(kind)) {                                    // a few of each, if any of either fits well
            for (String t : new String[] { "movie", "tv" }) {
                int n = 0;
                for (JSONObject c : sorted) if (n < 2 && t.equals(c.optString("type")) && c.optDouble("fit") >= 1) { picks.add(c); n++; }
            }
        }
        for (JSONObject c : sorted) { if (picks.size() >= 8) break; if (!picks.contains(c)) picks.add(c); }
        picks.sort((a, b) -> Double.compare(b.optDouble("fit"), a.optDouble("fit")));
        return picks;
    }

    private JSONObject explain(String ask, List<JSONObject> picks) throws Exception {
        StringBuilder b = new StringBuilder("Viewer: ").append(ask).append("\n\nPicks:\n");
        for (JSONObject p : picks)
            b.append(p.getString("key")).append(" = ").append(p.getString("title")).append(" (").append(p.optString("year")).append("; ")
             .append(p.optString("genres")).append(p.optString("ref").isEmpty() ? "" : "; fans of " + p.optString("ref") + " watch it")
             .append("): ").append(p.optString("overview")).append("\n");
        String sys = "For each pick, write one short line (at most 14 words) on why it suits this viewer right now: concrete, "
                + "vivid, no spoilers, no title repeated, no hype words like 'masterpiece'. Reply with JSON only: {\"<key>\": \"<line>\", ...} "
                + "using the keys given before the = signs.";
        return or.json(sys, b.toString(), 600, 12000);
    }
}
