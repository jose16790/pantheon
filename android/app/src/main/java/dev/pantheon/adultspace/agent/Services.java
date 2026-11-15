package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.pantheon.adultspace.weather.Weather;

/**
 * Your services: the streaming apps installed on this TV, matched to TMDB's providers for the TV's country (from its
 * location), so the agent only suggests what you can actually watch, and knows which app opens it.
 */
final class Services {
    final String region;                                  // e.g. "CA", "IN"
    final Map<Integer, String> pkgFor = new LinkedHashMap<>();   // TMDB provider id -> the app's package
    final Map<Integer, String> nameFor = new HashMap<>();        // TMDB provider id -> its name ("Netflix")
    private final long at = System.currentTimeMillis();

    private static Services cached;

    /** The services, looked up at most every 6 hours (or when the TV's country changes). */
    static synchronized Services get(Context c, Tmdb tmdb) throws Exception {
        String region = Weather.country(c).toUpperCase(Locale.ROOT);
        if (region.length() != 2) region = "US";
        if (cached != null && cached.region.equals(region) && System.currentTimeMillis() - cached.at < 6 * 3_600_000L) return cached;
        cached = new Services(c, tmdb, region);
        return cached;
    }

    private Services(Context c, Tmdb tmdb, String region) throws Exception {
        this.region = region;
        Map<String, String> apps = installed(c);                         // normalised label -> package
        for (String kind : new String[] { "movie", "tv" }) {
            JSONArray ps = tmdb.get("/watch/providers/" + kind, Tmdb.params("watch_region", region)).optJSONArray("results");
            if (ps == null) continue;
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.getJSONObject(i);
                int id = p.getInt("provider_id");
                if (pkgFor.containsKey(id)) continue;
                String name = p.optString("provider_name"), n = norm(name);
                if (n.length() < 3 || n.contains("channel") || n.contains("withads") && !n.startsWith("netflix")) continue;   // add-on channels (e.g. "Starz Amazon Channel")
                for (Map.Entry<String, String> a : apps.entrySet()) {
                    String l = a.getKey();
                    if (l.length() >= 4 && (n.equals(l) || n.startsWith(l) || l.startsWith(n) || (n.contains(l) && l.length() >= 5))) {
                        pkgFor.put(id, a.getValue()); nameFor.put(id, name); break;
                    }
                }
            }
        }
        Log.i(Agent.TAG, "services in " + region + ": " + nameFor.values());
    }

    String providerIds() {                                        // for TMDB's with_watch_providers (any of them)
        StringBuilder b = new StringBuilder();
        for (int id : pkgFor.keySet()) b.append(b.length() == 0 ? "" : "|").append(id);
        return b.toString();
    }

    private static String norm(String s) {
        String n = s.toLowerCase(Locale.ROOT).replace("+", "plus").replaceAll("[^a-z0-9]", "");
        if (n.startsWith("amazonprimevideo")) n = "primevideo" + n.substring(16);         // the app is labelled "Prime Video"
        return n.replaceAll("plus$", "");
    }

    /** The TV's apps you'd watch something in: normalised label -> package. */
    private static Map<String, String> installed(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        PackageManager pm = c.getPackageManager();
        for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                if (ai.packageName.equals(c.getPackageName())) continue;
                out.putIfAbsent(norm(String.valueOf(ai.loadLabel(pm))), ai.packageName);
            }
        }
        return out;
    }
}
