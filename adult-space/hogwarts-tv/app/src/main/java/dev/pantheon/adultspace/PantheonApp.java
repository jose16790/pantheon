package dev.pantheon.adultspace;

import android.app.Application;

import dev.pantheon.adultspace.home.CrashGuard;

public final class PantheonApp extends Application {
    @Override public void onCreate() { super.onCreate(); CrashGuard.install(this); }
}
