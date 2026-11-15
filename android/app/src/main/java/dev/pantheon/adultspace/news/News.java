package dev.pantheon.adultspace.news;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import dev.pantheon.adultspace.weather.Weather;

/**
 * The home's headlines: the top ten national (the TV's own country, from its location: weather/Weather.java), world
 * and business stories, from Google News' public RSS feeds (free, no key). Only each story's headline, source and
 * time are kept. Refreshed every 15 minutes while Pantheon is showing; the last set is kept for a quick start.
 *
 *   {"cc": "CA", "at": ms, "national": [{"t": headline, "src": source, "ts": ms}, ...], "world": [...], "business": [...]}
 */
public final class News {
    private static final String TAG = "Pantheon/News";
    private static final long EVERY = 15 * 60_000L;
    private static final int TOP = 10;
    // Google News has English editions for these; anywhere else gets the US edition's (international) top stories
    private static final String EDITIONS = "AU BW CA ET GB GH IE IL IN KE LV MY NA NG NZ PH PK SG US ZA ZW";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<String> onUpdate;
    private boolean running;

    public News(Context c, Consumer<String> onUpdate) {
        this.ctx = c.getApplicationContext();
        this.prefs = c.getSharedPreferences("pantheon_news", Context.MODE_PRIVATE);
        this.onUpdate = onUpdate;
    }

    /** The last headlines ({} until the first fetch). */
    public String latest() { return prefs.getString("json", "{}"); }

    public void start() {
        if (running) return;
        running = true;
        main.postDelayed(tick, 3_000);                          // after the weather has found where the TV is
    }

    public void stop() { running = false; main.removeCallbacks(tick); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            io.execute(News.this::refresh);
            main.postDelayed(this, EVERY);
        }
    };

    private void refresh() {
        try {
            String cc = Weather.country(ctx).toUpperCase(Locale.ROOT);
            if (!EDITIONS.contains(cc) || cc.length() != 2) cc = "US";
            String ed = "?hl=en-" + cc + "&gl=" + cc + "&ceid=" + cc + ":en";
            JSONObject out = new JSONObject().put("cc", cc).put("at", System.currentTimeMillis());
            out.put("national", feed("https://news.google.com/rss" + ed));
            out.put("world", feed("https://news.google.com/rss/headlines/section/topic/WORLD" + ed));
            out.put("business", feed("https://news.google.com/rss/headlines/section/topic/BUSINESS" + ed));
            String json = out.toString();
            prefs.edit().putString("json", json).apply();
            Log.i(TAG, cc + ": " + out.getJSONArray("national").length() + " national, " + out.getJSONArray("world").length()
                    + " world, " + out.getJSONArray("business").length() + " business");
            main.post(() -> onUpdate.accept(json));
        } catch (Exception e) {
            Log.w(TAG, "news not updated: " + e);
        }
    }

    /** The first ten items of an RSS feed: headline (without the " - Source" Google adds), source, time. */
    private static JSONArray feed(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        JSONArray list = new JSONArray();
        SimpleDateFormat rfc = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        try {
            c.setConnectTimeout(8_000); c.setReadTimeout(12_000);
            c.setRequestProperty("User-Agent", "Pantheon-news/1.0");
            if (c.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                XmlPullParser x = Xml.newPullParser();
                x.setInput(in, "UTF-8");
                String title = null, src = null, date = null;
                boolean inItem = false;
                for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && list.length() < TOP; ev = x.next()) {
                    if (ev == XmlPullParser.START_TAG) {
                        String n = x.getName();
                        if ("item".equals(n)) { inItem = true; title = src = date = null; }
                        else if (inItem && "title".equals(n)) title = x.nextText();
                        else if (inItem && "source".equals(n)) src = x.nextText();
                        else if (inItem && "pubDate".equals(n)) date = x.nextText();
                    } else if (ev == XmlPullParser.END_TAG && "item".equals(x.getName()) && inItem) {
                        inItem = false;
                        if (title == null) continue;
                        String t = title.trim();
                        if (src != null && t.endsWith(" - " + src)) t = t.substring(0, t.length() - src.length() - 3);
                        else { int k = t.lastIndexOf(" - "); if (k > 20) t = t.substring(0, k); }
                        long ts = 0;
                        try { ts = rfc.parse(date).getTime(); } catch (Exception ignored) { }
                        list.put(new JSONObject().put("t", t).put("src", src == null ? "" : src).put("ts", ts));
                    }
                }
            }
        } finally { c.disconnect(); }
        return list;
    }
}
