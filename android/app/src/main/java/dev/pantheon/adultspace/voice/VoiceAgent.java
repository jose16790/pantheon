package dev.pantheon.adultspace.voice;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * The remote's microphone → text → the Pantheon agent.
 *
 * 1. First choice: the TV's own voice screen (RecognizerIntent). On Google TV that is the system
 *    "listening" overlay, which uses the remote's microphone and needs no permission from us.
 * 2. Fallback: an in-app SpeechRecognizer (needs RECORD_AUDIO, asked for the first time it's used).
 *
 * Each step is reported to the page through {@link Listener}, so the castle can show what is happening.
 */
public final class VoiceAgent {
    public static final String TAG = "Pantheon/Voice";
    public static final int REQUEST_SPEECH = 4201;
    public static final int REQUEST_MIC = 4202;

    /** States: listening, heard, reply, error, idle. Always called on the UI thread. */
    public interface Listener { void onVoiceState(String state, String text); }

    private final Activity act;
    private final AgentClient agent;
    private final Listener ui;
    private final Supplier<String> theme;
    private SpeechRecognizer rec;
    private boolean listening;
    private dev.pantheon.adultspace.agent.Agent brain;          // the agent on the TV (agent/), when it has its keys

    /** The on-TV agent: anything the simple commands don't cover goes to it, when it has its keys. */
    public void setAgent(dev.pantheon.adultspace.agent.Agent a) { brain = a; }

    public VoiceAgent(Activity act, AgentClient agent, Supplier<String> theme, Listener ui) {
        this.act = act; this.agent = agent; this.theme = theme; this.ui = ui;
    }

    /** Start listening (the mic button, or the mic icon on the page). */
    public void listen() {
        if (listening) { Log.d(TAG, "already listening"); return; }
        Intent i = recognizerIntent()
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Pantheon…");
        try {
            act.startActivityForResult(i, REQUEST_SPEECH);
            listening = true;
            Log.i(TAG, "listening (system voice screen)");
            state("listening", null);
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "no system voice screen (" + e.getMessage() + "); using the in-app recognizer");
        }
        listenInApp();
    }

    /** From Activity.onActivityResult. True if it was ours. */
    public boolean onActivityResult(int request, int result, Intent data) {
        if (request != REQUEST_SPEECH) return false;
        listening = false;
        ArrayList<String> heard = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (result == Activity.RESULT_OK && heard != null && !heard.isEmpty()) handle(heard.get(0));
        else { Log.i(TAG, "voice screen closed with nothing heard (result " + result + ")"); state("idle", null); }
        return true;
    }

    /** From Activity.onRequestPermissionsResult. True if it was ours. */
    public boolean onPermissionResult(int request, int[] grants) {
        if (request != REQUEST_MIC) return false;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) listenInApp();
        else state("error", "Microphone permission was declined");
        return true;
    }

    public void destroy() {
        if (rec != null) { rec.destroy(); rec = null; }
        agent.shutdown();
    }

    private void listenInApp() {
        if (!SpeechRecognizer.isRecognitionAvailable(act)) {
            Log.e(TAG, "this TV has no speech recognizer");
            state("error", "This TV has no speech recognizer");
            return;
        }
        if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQUEST_MIC);
            return;
        }
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(act);
            rec.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { state("listening", null); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int t, Bundle b) { }
                @Override public void onPartialResults(Bundle b) {
                    ArrayList<String> p = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (p != null && !p.isEmpty() && !p.get(0).isEmpty()) state("listening", p.get(0));
                }
                @Override public void onResults(Bundle b) {
                    listening = false;
                    ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && !r.get(0).trim().isEmpty()) handle(r.get(0));
                    else state("idle", null);
                }
                @Override public void onError(int code) {
                    listening = false;
                    Log.w(TAG, "recognizer error " + code);
                    state(code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "idle" : "error",
                            code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? null : "Voice error " + code);
                }
            });
        }
        listening = true;
        Log.i(TAG, "listening (in-app recognizer)");
        rec.startListening(recognizerIntent().putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true));
    }

    private static Intent recognizerIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    private void handle(String text) {
        String prompt = text.trim();
        Log.i(TAG, "heard: \"" + prompt + "\"");
        // Simple commands (open an app, pause, volume...) are done right here on the TV: no agent needed.
        String local = LocalCommands.tryRun(act, prompt);
        if (local != null) {
            state("reply", org.json.JSONObject.quote(local).replaceFirst("^", "{\"reply\":") + "}");
            return;
        }
        state("heard", prompt);
        if (brain != null && brain.ready()) { brain.voice(prompt, this::state); return; }
        agent.send(prompt, theme.get(), new AgentClient.Callback() {
            @Override public void onReply(int status, String body) {
                act.runOnUiThread(() -> {
                    if (status < 400) Actions.run(act, body);      // the agent thinks, the TV acts (voice/Actions.java)
                    state(status < 400 ? "reply" : "error", status < 400 ? body : "Agent answered " + status);
                });
            }
            @Override public void onError(String message) {
                act.runOnUiThread(() -> state("error", "That one needs the agent, and it isn't running at " + host(agent)
                        + ". I can do “open YouTube”, “pause”, “volume up” and “search for…” by myself."));
            }
        });
    }

    private void state(String s, String text) { ui.onVoiceState(s, text); }

    private String host(AgentClient a) {
        String h = AgentClient.host(AgentConfig.url(act));
        return h == null ? "its address" : h;
    }
}
