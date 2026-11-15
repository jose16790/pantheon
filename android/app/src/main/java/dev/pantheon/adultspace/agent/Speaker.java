package dev.pantheon.adultspace.agent;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The agent's voice, per theme (the Hand of the King in Game of Thrones).
 *   stock lines   recorded once (themes/<theme>/voice/, made by the theme's voice tool): instant and free, e.g. "At once,
 *                 Your Grace." while the agent works
 *   live lines    anything else, spoken by ElevenLabs v3 (expressive: it performs cues like [chuckles] and [sighs]
 *                 written into the line; ~3-4 s, covered by a stock line) with your key. Each is cached, so a line said
 *                 twice costs once. Without a key, or if ElevenLabs fails, the TV's own voice speaks instead.
 * Only one thing speaks at a time; the theme's music and fire are turned down while it does (pantheonAudio.duck).
 */
final class Speaker {
    // ElevenLabs voices, from its public library (scripts/agent-keys.sh adds them to your account):
    static final String VOICE_GOT = "Ix8C14HEHgIQkJswik2o";        // the Hand of the King: Peter Baker, deep, clear, mature
    static final String VOICE_HOGWARTS = "htZQqY7WtacRNV7s62Iy";   // the Headmaster's portrait: a gruff old British wizard
    static final String MODEL = "eleven_v3";                        // performs [chuckles], [sighs], [laughs softly]…

    private final Context ctx;
    private final ExecutorService pool;
    private final Consumer<String> js;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private MediaPlayer player;
    private TextToSpeech tts;
    private boolean ttsReady;
    private int turn;                                                // newest line; older ones are dropped

    Speaker(Context c, ExecutorService pool, Consumer<String> js) {
        this.ctx = c.getApplicationContext(); this.pool = pool; this.js = js;
        tts = new TextToSpeech(ctx, s -> {
            ttsReady = s == TextToSpeech.SUCCESS;
            if (ttsReady) { tts.setLanguage(Locale.UK); tts.setSpeechRate(0.95f); tts.setPitch(0.9f); }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { duck(true); }
            @Override public void onDone(String id) { duck(false); }
            @Override public void onError(String id) { duck(false); }
        });
    }

    static String voiceFor(String theme) { return "hogwarts".equals(theme) ? VOICE_HOGWARTS : VOICE_GOT; }

    /** A recorded line of this kind (ack, think, council, sorry…), if the theme has them; returns its words, or null. */
    String stock(String theme, String kind) {
        try (InputStream in = ctx.getAssets().open("www/themes/" + theme + "/voice/voice.json")) {
            JSONObject v = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            JSONArray lines = v.getJSONObject("lines").optJSONArray(kind);
            if (lines == null || lines.length() == 0) return null;
            JSONObject l = lines.getJSONObject(rnd.nextInt(lines.length()));
            final int my = ++turn;
            main.post(() -> {
                try {
                    AssetFileDescriptor fd = ctx.getAssets().openFd("www/themes/" + theme + "/voice/" + l.getString("file"));
                    play(my, mp -> mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength()));
                } catch (Exception e) { Log.w(Agent.TAG, "stock line failed: " + e.getMessage()); }
            });
            return l.optString("text");
        } catch (Exception e) { return null; }                       // this theme has no recorded lines
    }

    /** Say something live (off the UI thread): ElevenLabs if there's a key, else the TV's voice. */
    void say(String theme, String text) {
        if (text == null || text.trim().isEmpty()) return;
        final int my = ++turn;
        String key = Keys.elevenLabs(ctx);
        if (key.isEmpty()) { main.post(() -> fallback(my, text)); return; }
        pool.execute(() -> {
            try {
                File f = cached(theme, text);
                if (!f.exists()) fetch(key, voiceFor(theme), text, f);
                main.post(() -> play(my, mp -> mp.setDataSource(f.getAbsolutePath())));
            } catch (Exception e) {
                Log.w(Agent.TAG, "ElevenLabs failed (" + e.getMessage() + "); the TV's voice instead");
                main.post(() -> fallback(my, text));
            }
        });
    }

    void stop() { turn++; main.post(this::release); }

    void shutdown() { release(); if (tts != null) tts.shutdown(); }

    // ---------------------------------------------------------------- plumbing
    private interface Source { void set(MediaPlayer mp) throws Exception; }

    private void play(int my, Source src) {
        if (my != turn) return;                                      // something newer is already speaking
        release();
        try {
            MediaPlayer mp = player = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            src.set(mp);
            mp.setOnCompletionListener(m -> { if (m == player) release(); });
            mp.setOnErrorListener((m, w, x) -> { if (m == player) release(); return true; });
            mp.setOnPreparedListener(m -> { duck(true); m.start(); });
            mp.prepareAsync();
        } catch (Exception e) { Log.w(Agent.TAG, "play failed: " + e.getMessage()); release(); }
    }

    private void release() {
        if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; duck(false); }
        if (tts != null) tts.stop();
    }

    private void fallback(int my, String text) {
        if (my != turn || !ttsReady) return;
        release();
        tts.speak(plain(text), TextToSpeech.QUEUE_FLUSH, null, "pantheon-" + my);
    }

    /** The words alone, without the performance cues: for the screen and the TV's own voice. */
    static String plain(String s) { return s == null ? "" : s.replaceAll("\\[[^\\]]{1,30}\\]\\s*", "").replaceAll("\\s{2,}", " ").trim(); }

    private void duck(boolean on) { main.post(() -> js.accept("window.pantheonAudio && pantheonAudio.duck && pantheonAudio.duck(" + on + ")")); }

    private File cached(String theme, String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] h = md.digest((voiceFor(theme) + "|" + text).getBytes(StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(String.format("%02x", h[i]));
        File dir = new File(ctx.getCacheDir(), "voice"); dir.mkdirs();
        return new File(dir, b + ".mp3");
    }

    private static void fetch(String key, String voice, String text, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.elevenlabs.io/v1/text-to-speech/" + voice
                + "?output_format=mp3_44100_64").openConnection();
        try {
            c.setRequestMethod("POST"); c.setConnectTimeout(5000); c.setReadTimeout(20000); c.setDoOutput(true);
            c.setRequestProperty("xi-api-key", key); c.setRequestProperty("Content-Type", "application/json");
            JSONObject body = new JSONObject().put("text", text).put("model_id", MODEL)
                    .put("voice_settings", new JSONObject().put("stability", 0.5));
            c.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
            if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
            File tmp = new File(out.getPath() + ".part");
            try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(tmp)) { o.write(readAll(in)); }
            if (!tmp.renameTo(out)) throw new Exception("couldn't save the line");
        } finally { c.disconnect(); }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
