package dev.pantheon.adultspace.theme;

import java.util.Map;

/**
 * One look for the Adult Space home screen. A theme is data only: the page (assets/www/index.html)
 * draws everything, and {@link ThemeManager} hands the active theme to it as JSON.
 *
 * To add a theme: implement this, put its pictures under assets/www/themes/<id>/, and register it
 * in {@link ThemeManager#ALL}.
 */
public interface ThemeData {
    /** Stable id, saved in preferences and accepted by `--es theme <id>`. */
    String id();

    /** The name shown to people. */
    String displayName();

    /** The window colour behind the page while it loads (ARGB). */
    int windowBackground();

    /**
     * CSS custom properties, without the leading "--". The page understands:
     *   gold, gold-hi   the main accent and its highlight (focus rings, icons in focus)
     *   ink             text and line icons on the photos
     *   accent          labels, tile numbers and selection in the Customise panel
     *   accent-soft     app names under the dock
     *   spine           book-spine lettering in the Library
     *   panel-1, panel-2  the Customise panel's background gradient
     */
    Map<String, String> colors();

    /**
     * Pictures, by slot, as paths relative to assets/www. A slot left out keeps whatever the page
     * already shows (so a stub theme can change only the colours). Slots:
     *   castle, windowsA, windowsB   the Home backdrop and its two window-glow layers
     *   shelf                        the Library backdrop
     *   greenhouse                   the Greenhouse backdrop
     *   mist                         the drifting mist strip
     */
    Map<String, String> images();

    /** The animated guide that hops along the dock. Only "owl" is drawn today. */
    default String mascot() { return "owl"; }

    /** False while a theme is a palette-only stub waiting for its own pictures. */
    default boolean complete() { return true; }
}
