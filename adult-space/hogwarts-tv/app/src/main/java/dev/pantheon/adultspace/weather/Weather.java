package dev.pantheon.adultspace.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The weather wizard's data: where the TV is (from its internet connection, so it follows the TV to another
 * country), and the weather there now (Open-Meteo: free, no key). The sun's and moon's positions and the moon's
 * phase are worked out in the page (themes/weather.js), offline.
 *
 *   location   ipapi.co, else ipwho.is: approximate coordinates of the TV's internet connection. Refreshed every 6 h.
 *   weather    api.open-meteo.com: conditions, cloud, rain/snow, wind, visibility, sunrise/sunset. Every 15 min.
 *
 * Only the approximate coordinates go to Open-Meteo. The last result is kept, so the scene is right at once on start.
 */
public final class Weather {
    private static final String TAG = "Pantheon/Weather";
    private static final long WEATHER_EVERY = 15 * 60_000L, PLACE_EVERY = 6 * 3_600_000L;
    private static final String UA = "Pantheon-weather/1.0";

    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Consumer<String> onUpdate;
    private boolean running;

    public Weather(Context c, Consumer<String> onUpdate) {
        this.prefs = c.getSharedPreferences("pantheon_weather", Context.MODE_PRIVATE);
        this.onUpdate = onUpdate;
    }

    /** The last weather, as the page wants it ({} until the first fetch). */
    public String latest() { return prefs.getString("json", "{}"); }

    public void start() {
        if (running) return;
        running = true;
        tick.run();
    }

    public void stop() { running = false; main.removeCallbacks(tick); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            io.execute(Weather.this::refresh);
            main.postDelayed(this, WEATHER_EVERY);
        }
    };

    private void refresh() {
        try {
            JSONObject place = place();
            if (place == null) return;
            String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,is_day,weather_code,cloud_cover,precipitation,rain,showers,snowfall,wind_speed_10m,wind_gusts_10m,visibility"
                    + "&daily=sunrise,sunset&timezone=auto&forecast_days=1", place.getDouble("lat"), place.getDouble("lon"));
            JSONObject w = new JSONObject(get(url));
            JSONObject out = new JSONObject();
            out.put("place", place);
            out.put("current", w.getJSONObject("current"));
            out.put("daily", w.getJSONObject("daily"));
            out.put("utcOffset", w.optInt("utc_offset_seconds", 0));
            out.put("fetched", System.currentTimeMillis());
            String json = out.toString();
            prefs.edit().putString("json", json).apply();
            JSONObject cur = w.getJSONObject("current");
            Log.i(TAG, place.optString("city") + ": code " + cur.optInt("weather_code") + ", cloud " + cur.optInt("cloud_cover")
                    + "%, " + cur.optDouble("temperature_2m") + "°C, day " + cur.optInt("is_day"));
            main.post(() -> onUpdate.accept(json));
        } catch (Exception e) {
            Log.w(TAG, "weather not updated: " + e);
        }
    }

    /** Approximate coordinates of the TV's internet connection: cached for 6 h, then looked up again. */
    private JSONObject place() {
        try {
            JSONObject cached = new JSONObject(prefs.getString("place", "{}"));
            if (System.currentTimeMillis() - cached.optLong("at", 0) < PLACE_EVERY && cached.has("lat")) return cached;
        } catch (Exception ignored) { }
        JSONObject p = null;
        try {
            JSONObject j = new JSONObject(get("https://ipapi.co/json/"));
            if (j.has("latitude")) p = new JSONObject().put("lat", j.getDouble("latitude")).put("lon", j.getDouble("longitude"))
                    .put("city", j.optString("city")).put("country", j.optString("country_name")).put("tz", j.optString("timezone"));
        } catch (Exception e) { Log.w(TAG, "ipapi.co: " + e.getMessage()); }
        if (p == null) try {
            JSONObject j = new JSONObject(get("https://ipwho.is/"));
            if (j.optBoolean("success")) p = new JSONObject().put("lat", j.getDouble("latitude")).put("lon", j.getDouble("longitude"))
                    .put("city", j.optString("city")).put("country", j.optString("country"))
                    .put("tz", j.optJSONObject("timezone") != null ? j.getJSONObject("timezone").optString("id") : "");
        } catch (Exception e) { Log.w(TAG, "ipwho.is: " + e.getMessage()); }
        if (p == null) {                                        // offline: keep the last place, however old
            try { JSONObject c = new JSONObject(prefs.getString("place", "{}")); return c.has("lat") ? c : null; }
            catch (Exception e) { return null; }
        }
        try { p.put("at", System.currentTimeMillis()); } catch (Exception ignored) { }
        prefs.edit().putString("place", p.toString()).apply();
        Log.i(TAG, "the TV is near " + p.optString("city") + ", " + p.optString("country"));
        return p;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        try {
            c.setConnectTimeout(8_000); c.setReadTimeout(12_000);
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() >= 400) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                return b.toString(StandardCharsets.UTF_8.name());
            }
        } finally { c.disconnect(); }
    }
}
