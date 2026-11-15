package dev.pantheon.adultspace.agent;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Small JSON-over-HTTPS helper for the agent (TMDB and OpenRouter). */
final class Http {
    private Http() { }

    static final class Error extends Exception {
        final int status;
        Error(int status, String msg) { super(msg); this.status = status; }
    }

    static JSONObject get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        return call("GET", url, headers, null, timeoutMs);
    }

    static JSONObject post(String url, Map<String, String> headers, JSONObject body, int timeoutMs) throws Exception {
        return call("POST", url, headers, body, timeoutMs);
    }

    private static JSONObject call(String method, String url, Map<String, String> headers, JSONObject body, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(Math.min(timeoutMs, 6000)); c.setReadTimeout(timeoutMs);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Pantheon-agent/1.0");
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            String text = read(status >= 400 ? c.getErrorStream() : c.getInputStream());
            if (status >= 400) throw new Error(status, "HTTP " + status + ": " + (text.length() > 300 ? text.substring(0, 300) : text));
            return new JSONObject(text);
        } finally { c.disconnect(); }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }
}
