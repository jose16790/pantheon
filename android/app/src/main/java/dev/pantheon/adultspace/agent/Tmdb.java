package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * TMDB (themoviedb.org), the catalogue: titles, runtimes, genres, ratings, posters, similar titles, and which
 * streaming services carry each one in a country (its watch-provider data comes from JustWatch). Takes either kind
 * of TMDB credential: the v3 API key or the v4 read access token.
 */
final class Tmdb {
    static final String IMG = "https://image.tmdb.org/t/p/";

    // TMDB's genre ids. Series have fewer genres, so a few movie genres map to their nearest series ones.
    static final Map<String, int[]> GENRES = new HashMap<>();     // pill -> {movie id, series id (0: none)}
    static {
        GENRES.put("action", new int[] { 28, 10759 });
        GENRES.put("adventure", new int[] { 12, 10759 });
        GENRES.put("animation", new int[] { 16, 16 });
        GENRES.put("comedy", new int[] { 35, 35 });
        GENRES.put("crime", new int[] { 80, 80 });
        GENRES.put("documentary", new int[] { 99, 99 });
        GENRES.put("drama", new int[] { 18, 18 });
        GENRES.put("family", new int[] { 10751, 10751 });
        GENRES.put("fantasy", new int[] { 14, 10765 });
        GENRES.put("history", new int[] { 36, 10768 });
        GENRES.put("horror", new int[] { 27, 9648 });
        GENRES.put("mystery", new int[] { 9648, 9648 });
        GENRES.put("romance", new int[] { 10749, 18 });
        GENRES.put("sci-fi", new int[] { 878, 10765 });
        GENRES.put("thriller", new int[] { 53, 9648 });
        GENRES.put("war", new int[] { 10752, 10768 });
    }

    private final Context ctx;
    Tmdb(Context c) { this.ctx = c.getApplicationContext(); }

    JSONObject get(String path, Map<String, String> params) throws Exception {
        String key = Keys.tmdb(ctx);
        boolean token = key.startsWith("eyJ");                    // a v4 read access token (a JWT)
        Uri.Builder u = Uri.parse("https://api.themoviedb.org/3" + path).buildUpon();
        if (params != null) for (Map.Entry<String, String> p : params.entrySet()) u.appendQueryParameter(p.getKey(), p.getValue());
        if (!token) u.appendQueryParameter("api_key", key);
        Map<String, String> h = new HashMap<>();
        if (token) h.put("Authorization", "Bearer " + key);
        return Http.get(u.build().toString(), h, 6000);
    }

    static Map<String, String> params(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
