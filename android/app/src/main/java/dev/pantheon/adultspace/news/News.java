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
    private static final int TOP = 10, FETCH = 25;
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
            JSONArray national = feed("https://news.google.com/rss" + ed, FETCH);
            JSONArray world = feed("https://news.google.com/rss/headlines/section/topic/WORLD" + ed, FETCH);
            JSONArray business = feed("https://news.google.com/rss/headlines/section/topic/BUSINESS" + ed, FETCH);
            JSONArray markets = feed("https://news.google.com/rss/search?q=" + android.net.Uri.encode(
                    "(stocks OR markets OR \"interest rates\" OR earnings OR inflation OR \"central bank\" OR economy) when:1d")
                    + ed.replace("?", "&"), FETCH);
            sortInto(out, cc, national, world, business, markets);
            String json = out.toString();
            prefs.edit().putString("json", json).apply();
            Log.i(TAG, cc + ": " + out.getJSONArray("national").length() + " national, " + out.getJSONArray("world").length()
                    + " world, " + out.getJSONArray("business").length() + " business");
            main.post(() -> onUpdate.accept(json));
        } catch (Exception e) {
            Log.w(TAG, "news not updated: " + e);
        }
    }

    // ---------------------------------------------------------------- each story to its row
    // Google's topic feeds are loose (the "business" feed carries travel and labour stories). Jev reads every headline
    // once and says which row it belongs to; each row then takes only its own kind, in the feeds' order. Without the
    // agent's key: the feeds as they are, and a finance word list for the Iron Bank.
    private static final String[] ROWS = { "national", "world", "finance", "other" };
    private static final java.util.regex.Pattern MONEY = java.util.regex.Pattern.compile("(?i)\\b(stocks?|shares|markets?|"
            + "tsx|s&p|nasdaq|dow|investors?|earnings|profits?|revenue|inflation|interest rates?|rate cut|central bank|"
            + "bank of|economy|economic|gdp|recession|bonds?|yields?|dollar|loonie|rupee|sensex|nifty|crypto|bitcoin|ipo|merger|"
            + "acquisition|deal|tariffs?|trade|budget|deficit|debt|oil prices?|prices)\\b");

    private void sortInto(JSONObject out, String cc, JSONArray... feeds) throws Exception {
        java.util.List<JSONObject> all = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int f = 0; f < feeds.length; f++)
            for (int i = 0; i < feeds[f].length(); i++) {
                JSONObject o = feeds[f].getJSONObject(i);
                if (!seen.add(o.optString("t").toLowerCase(Locale.ROOT))) continue;
                all.add(o.put("feed", f));                              // 0 national, 1 world, 2 business, 3 markets search
            }
        boolean sorted = false;
        if (dev.pantheon.adultspace.agent.Decide.ready(ctx)) {
            try { classify(all, cc); sorted = true; }
            catch (Exception e) { Log.w(TAG, "Jev couldn't sort the headlines: " + e.getMessage()); }
        }
        JSONArray national = new JSONArray(), world = new JSONArray(), finance = new JSONArray();
        for (JSONObject o : all) {
            String row = sorted ? o.optString("row", "other")
                    : o.optInt("feed") == 0 ? "national" : o.optInt("feed") == 1 ? "world"
                    : MONEY.matcher(o.optString("t")).find() ? "finance" : "other";
            JSONArray to = "national".equals(row) ? national : "world".equals(row) ? world : "finance".equals(row) ? finance : null;
            if (to == null || to.length() >= TOP) continue;
            if (!sorted && o.optInt("feed") == 3 && finance.length() >= 6) continue;   // keep the unsorted search feed a minority
            o.remove("feed"); o.remove("row");
            to.put(o);
        }
        out.put("national", national).put("world", world).put("business", finance).put("sorted", sorted);
        Log.i(TAG, "rows" + (sorted ? " (sorted by Jev)" : "") + ": " + national.length() + " national, " + world.length() + " world, " + finance.length() + " finance");
    }

    private void classify(java.util.List<JSONObject> all, String cc) throws Exception {
        String country = new Locale("", cc).getDisplayCountry(Locale.ENGLISH);
        JSONObject criteria = new JSONObject()
                .put("national", "Mainly about " + country + ": its politics, people, places, events or institutions.")
                .put("world", "Mainly about other countries or international affairs (not " + country + ").")
                .put("finance", "Finance news: markets, stocks, the economy, inflation, interest rates, central banks, banking, "
                        + "company earnings, deals, trade or prices. A real news story, not a stock-quote page, a listicle or investing tips.")
                .put("other", "Anything else: sport, entertainment, lifestyle, local human interest, tech gadgets; stock-quote "
                        + "pages, listicles ('3 stocks to buy'), investing advice.");
        for (int from = 0; from < all.size(); from += 40) {                 // ~40 headlines a call
            JSONObject q = new JSONObject();
            StringBuilder list = new StringBuilder();
            int to = Math.min(all.size(), from + 40);
            for (int i = from; i < to; i++) {
                JSONObject o = all.get(i);
                list.append("h").append(i).append(": ").append(o.optString("t")).append(" (").append(o.optString("src")).append(")\n");
                q.put("h" + i, new JSONObject().put("type", "choice").put("instructions", "Which row does headline h" + i + " belong in?").put("criteria", criteria));
            }
            JSONObject a = dev.pantheon.adultspace.agent.Decide.decide(ctx,
                    new JSONObject().put("tv_country", country).put("headlines", list.toString()), q, 15000);
            for (int i = from; i < to; i++) {
                JSONObject x = a.optJSONObject("h" + i);
                String row = x == null ? "other" : x.optString("choice", "other");
                if ("finance".equals(row) && x.optJSONObject("probabilities") != null && x.getJSONObject("probabilities").optDouble("finance", 1) < 0.75) row = "other";   // clearly finance, or not at all
                all.get(i).put("row", row);
            }
        }
    }

    /** The first items of an RSS feed: headline (without the " - Source" Google adds), source, time. */
    private static JSONArray feed(String u, int top) throws Exception {
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
                for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && list.length() < top; ev = x.next()) {
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
