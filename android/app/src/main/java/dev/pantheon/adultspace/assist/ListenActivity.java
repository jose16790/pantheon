package dev.pantheon.adultspace.assist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.json.JSONObject;

import dev.pantheon.adultspace.theme.ThemeManager;
import dev.pantheon.adultspace.voice.AgentClient;
import dev.pantheon.adultspace.voice.AgentConfig;
import dev.pantheon.adultspace.voice.VoiceAgent;

/**
 * The mic button pressed while another app is on screen: a transparent activity that listens, sends the words to
 * the agent and shows the reply as a toast, then gets out of the way.
 */
public final class ListenActivity extends Activity {
    private VoiceAgent voice;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager themes = new ThemeManager(this);
        voice = new VoiceAgent(this, new AgentClient(() -> AgentConfig.url(this)), () -> themes.current().id(), this::onVoice);
        if (state == null) voice.listen();
    }

    private void onVoice(String state, String text) {
        switch (state) {
            case "heard": toast("Pantheon: “" + text + "”", Toast.LENGTH_SHORT); break;
            case "reply": toast(replyText(text), Toast.LENGTH_LONG); finish(); break;
            case "error": toast("Pantheon: " + text, Toast.LENGTH_LONG); finish(); break;
            case "idle": finish(); break;
            default: break;                                     // listening: Google's voice screen is showing
        }
    }

    /** The agent may answer JSON ({reply}, {text}, {message}, {response}, {answer}) or plain text. */
    static String replyText(String body) {
        if (body == null || body.isEmpty()) return "Done.";
        try {
            JSONObject j = new JSONObject(body);
            for (String k : new String[] { "reply", "text", "message", "response", "answer" })
                if (j.has(k)) return j.optString(k);
        } catch (Exception ignored) { }
        return body.length() > 280 ? body.substring(0, 277) + "…" : body;
    }

    private void toast(String s, int len) { Toast.makeText(getApplicationContext(), s, len).show(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (!voice.onActivityResult(request, result, data)) super.onActivityResult(request, result, data);
    }

    @Override public void onRequestPermissionsResult(int request, String[] perms, int[] grants) {
        if (!voice.onPermissionResult(request, grants)) super.onRequestPermissionsResult(request, perms, grants);
    }

    @Override protected void onDestroy() { voice.destroy(); super.onDestroy(); }
}
