package dev.pantheon.adultspace.assist;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

/**
 * Pantheon as the TV's assistant: when it holds the ASSISTANT role, the remote's mic / Assistant button
 * comes here (via {@link PantheonSessionService}) instead of Google Assistant.
 *
 * Take the role:     adb shell cmd role add-role-holder android.app.role.ASSISTANT dev.pantheon.adultspace
 * Give it back:      see README.md, "Mic button"
 */
public final class PantheonVoiceInteractionService extends VoiceInteractionService {
    static final String TAG = "Pantheon/Assist";

    @Override public void onReady() {
        super.onReady();
        Log.i(TAG, "Pantheon is the TV's assistant: the mic button comes to Pantheon");
    }

    @Override public void onShutdown() {
        Log.i(TAG, "Pantheon is no longer the TV's assistant");
        super.onShutdown();
    }
}
