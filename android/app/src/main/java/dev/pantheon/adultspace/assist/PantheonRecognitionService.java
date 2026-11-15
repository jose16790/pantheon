package dev.pantheon.adultspace.assist;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.List;

/**
 * Android requires an assistant to name a speech recognizer, and may make it the TV's default one while Pantheon
 * holds the assistant role. Pantheon doesn't do speech-to-text itself, so this forwards everything to the TV's real
 * recognizer (Google's, com.google.android.tts, when present), so voice typing in other apps keeps working.
 */
public final class PantheonRecognitionService extends RecognitionService {
    private static final String TAG = "Pantheon/Recognizer";
    private static final String PREFERRED = "com.google.android.tts";
    private SpeechRecognizer real;

    @Override protected void onStartListening(Intent intent, Callback cb) {
        ComponentName target = findRealRecognizer();
        if (target == null) { Log.e(TAG, "no other speech recognizer on this TV"); safe(() -> cb.error(SpeechRecognizer.ERROR_CLIENT)); return; }
        if (real != null) real.destroy();
        real = SpeechRecognizer.createSpeechRecognizer(this, target);
        real.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) { safe(() -> cb.readyForSpeech(b)); }
            @Override public void onBeginningOfSpeech() { safe(cb::beginningOfSpeech); }
            @Override public void onRmsChanged(float v) { safe(() -> cb.rmsChanged(v)); }
            @Override public void onBufferReceived(byte[] b) { safe(() -> cb.bufferReceived(b)); }
            @Override public void onEndOfSpeech() { safe(cb::endOfSpeech); }
            @Override public void onError(int e) { safe(() -> cb.error(e)); }
            @Override public void onResults(Bundle b) { safe(() -> cb.results(b)); }
            @Override public void onPartialResults(Bundle b) { safe(() -> cb.partialResults(b)); }
            @Override public void onEvent(int t, Bundle b) { }
        });
        Log.d(TAG, "forwarding to " + target.flattenToShortString());
        real.startListening(intent);
    }

    @Override protected void onStopListening(Callback cb) { if (real != null) real.stopListening(); }
    @Override protected void onCancel(Callback cb) { if (real != null) real.cancel(); }
    @Override public void onDestroy() { if (real != null) { real.destroy(); real = null; } super.onDestroy(); }

    private ComponentName findRealRecognizer() {
        List<ResolveInfo> all = getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        ComponentName fallback = null;
        for (ResolveInfo r : all) {
            if (r.serviceInfo.packageName.equals(getPackageName())) continue;
            ComponentName c = new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
            if (r.serviceInfo.packageName.equals(PREFERRED)) return c;
            if (fallback == null) fallback = c;
        }
        return fallback;
    }

    private interface Call { void run() throws RemoteException; }
    private static void safe(Call c) { try { c.run(); } catch (RemoteException e) { Log.w(TAG, "caller went away"); } }
}
