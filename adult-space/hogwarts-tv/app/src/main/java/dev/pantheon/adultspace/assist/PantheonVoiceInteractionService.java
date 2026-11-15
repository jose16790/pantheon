package dev.pantheon.adultspace.assist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import dev.pantheon.adultspace.home.HomeGuard;

/**
 * Pantheon as the TV's assistant: when it holds the ASSISTANT role, the remote's mic / Assistant button
 * comes here (via {@link PantheonSessionService}) instead of Google Assistant.
 *
 * Take the role:     adb shell cmd role add-role-holder android.app.role.ASSISTANT dev.pantheon.adultspace
 * Give it back:      see README.md, "Mic button"
 */
public final class PantheonVoiceInteractionService extends VoiceInteractionService {
    static final String TAG = "Pantheon/Assist";

    /** The system keeps the assistant bound, so this is a good place to notice the screen turning on (HomeGuard). */
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { HomeGuard.ensure(c, "screen on"); }
    };

    @Override public void onReady() {
        super.onReady();
        Log.i(TAG, "Pantheon is the TV's assistant: the mic button comes to Pantheon");
        HomeGuard.ensure(this, "assistant ready");
        try { registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON)); } catch (RuntimeException ignored) { }
    }

    @Override public void onShutdown() {
        Log.i(TAG, "Pantheon is no longer the TV's assistant");
        try { unregisterReceiver(screenOn); } catch (RuntimeException ignored) { }
        super.onShutdown();
    }
}
