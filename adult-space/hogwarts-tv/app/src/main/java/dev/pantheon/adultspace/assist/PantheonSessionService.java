package dev.pantheon.adultspace.assist;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/** Makes a {@link PantheonSession} for each press of the mic button. */
public final class PantheonSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(Bundle args) { return new PantheonSession(this); }
}
