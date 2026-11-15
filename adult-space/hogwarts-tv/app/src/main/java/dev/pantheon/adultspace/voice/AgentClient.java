package dev.pantheon.adultspace.voice;

import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends a spoken prompt to the Pantheon agent (the Raspberry Pi backend) and logs what comes back.
 *
 *   POST {agent_url}     Content-Type: application/json
 *   {"prompt": "...", "source": "voice", "space": "adult", "theme": "hogwarts", "device": "...", "ts": 1731600000000}
 *
 * Whatever the agent answers is written to Logcat under the tag {@value #TAG}:
 *   adb logcat -s Pantheon/Agent
 *
 * The agent is on the home network and spoken to over plain http, so the address is checked the
 * way couch-tv's AskWire.isLocalHost does (MIT, credited in NOTICE.md): a public address is refused
 * rather than sending what was said in the living room across the internet unencrypted.
 */
public final class AgentClient {
    public static final String TAG = "Pantheon/Agent";

    public interface Callback {
        /** The agent answered (any HTTP status). Called on a background thread. */
        void onReply(int status, String body);
        /** It could not be reached. Called on a background thread. */
        void onError(String message);
    }

    private final String url;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public AgentClient(String url) { this.url = url; }

    public String url() { return url; }

    public void send(String prompt, String theme, Callback cb) {
        io.execute(() -> {
            String host = host(url);
            if (url.startsWith("http://") && !isLocalHost(host)) {
                String m = "Refusing plain http to a non-local host: " + host;
                Log.e(TAG, m);
                cb.onError(m);
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("prompt", prompt);
                body.put("source", "voice");
                body.put("space", "adult");
                body.put("theme", theme);
                body.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                body.put("ts", System.currentTimeMillis());
            } catch (JSONException ignored) { }

            HttpURLConnection c = null;
            long t0 = System.currentTimeMillis();
            try {
                Log.i(TAG, "→ POST " + url + " " + body);
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5_000);
                c.setReadTimeout(60_000);                       // an agent may think (and act) for a while
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("Accept", "application/json, text/plain");
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(out.length);
                try (OutputStream os = c.getOutputStream()) { os.write(out); }

                int status = c.getResponseCode();
                InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                String reply = in == null ? "" : read(in);
                Log.i(TAG, "← " + status + " in " + (System.currentTimeMillis() - t0) + " ms: " + reply);
                cb.onReply(status, reply);
            } catch (IOException | RuntimeException e) {
                String m = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "✗ " + url + " failed after " + (System.currentTimeMillis() - t0) + " ms: " + m);
                cb.onError(m);
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    public void shutdown() { io.shutdownNow(); }

    private static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) {
                b.write(buf, 0, n);
                if (b.size() > 1_000_000) break;               // a reply, not a download
            }
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }

    static String host(String u) {
        try { String h = URI.create(u.trim()).getHost(); return h == null ? null : h.toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** A server on the home network or this device. Adapted from couch-tv AskWire.isLocalHost (MIT). */
    static boolean isLocalHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        if (h.equals("localhost") || h.equals("::1") || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true;
        String[] parts = h.split("\\.");
        if (parts.length != 4) return false;
        int[] p = new int[4];
        for (int i = 0; i < 4; i++) {
            try { p[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { return false; }
            if (p[i] < 0 || p[i] > 255) return false;
        }
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] >= 16 && p[1] <= 31)
                || (p[0] == 169 && p[1] == 254);
    }
}
