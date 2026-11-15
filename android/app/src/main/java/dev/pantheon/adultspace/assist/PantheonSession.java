package dev.pantheon.adultspace.assist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import dev.pantheon.adultspace.MainActivity;

/**
 * One press of the mic button. It has no UI of its own:
 *   Pantheon on screen  -> Pantheon listens (the castle shows the banner)
 *   any other app       -> ListenActivity, a small transparent listener over that app
 */
final class PantheonSession extends VoiceInteractionSession {
    PantheonSession(Context c) { super(c); }

    @Override public void onCreate() {
        super.onCreate();
        setUiEnabled(false);                                   // no window: the listening UI is Google's voice screen
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        Log.i(PantheonVoiceInteractionService.TAG, "mic button pressed (flags " + flags + ")");
        if (MainActivity.listenIfShowing()) {
            Log.i(PantheonVoiceInteractionService.TAG, "Pantheon is on screen: listening there");
        } else {
            Log.i(PantheonVoiceInteractionService.TAG, "another app is on screen: opening the listener over it");
            startAssistantActivity(new Intent(getContext(), ListenActivity.class));
        }
        hide();
    }
}
