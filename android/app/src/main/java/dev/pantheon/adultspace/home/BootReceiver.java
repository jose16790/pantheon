package dev.pantheon.adultspace.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart, and after Pantheon is updated: make sure the Home watch is still on (HomeGuard). */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { HomeGuard.ensure(c, String.valueOf(i.getAction())); }
}
