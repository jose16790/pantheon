package dev.pantheon.adultspace.voice;

import android.app.Activity;
import android.util.Log;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple voice commands Pantheon does by itself, on the TV, with no agent: instant, and working with no network.
 *
 *   "open YouTube" / "launch Disney Plus" / "go to Crave"     open that app
 *   "go home"                                                  the home screen
 *   "pause" / "play" / "resume" / "next" / "skip" / "previous" media keys for whatever is playing
 *   "volume up" / "louder" / "turn it down" / "mute"           volume
 *   "set volume to 30" / "volume up by three" / "turn it down 5"  volume to a level (percent), or by steps
 *   "search for Dune" / "find The Bear"                        the TV's own search
 *
 * Anything else ("put on the latest episode of The Bear", "what should we watch?") needs thinking, and goes to
 * the agent (AgentClient).
 */
public final class LocalCommands {
    private static final String TAG = "Pantheon/Local";
    private LocalCommands() { }

    private static final Pattern OPEN = Pattern.compile("^(?:please )?(?:open|launch|start|go to|switch to|load)(?: up)? (?:the )?(.+?)(?: app)?(?: please)?$");
    private static final Pattern SET_VOLUME = Pattern.compile("^(?:set|change|put) (?:the )?volume (?:to|at) (\\w+)(?: percent| %)?$|^volume (?:to|at) (\\w+)(?: percent| %)?$");
    private static final Pattern STEP_VOLUME = Pattern.compile(
            "^(?:(?:turn )?(?:the )?volume (up|down|increase|decrease|raise|lower)|(increase|decrease|raise|lower) (?:the )?volume|turn (?:it|the volume) (up|down))(?: by)? (\\w+)(?: steps?| notches?| points?)?$");
    private static final String[] NUMBERS = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten" };

    private static final Pattern SEARCH = Pattern.compile("^(?:search(?: for)?|find|look for|look up) (.+)$");

    /** The reply to show if Pantheon did it itself, or null if this one is for the agent. Call on the UI thread. */
    public static String tryRun(Activity a, String said) {
        String s = said.toLowerCase(Locale.ROOT).replaceAll("[.!?,]", "").trim();

        if (s.matches("(?:go )?(?:back )?home|(?:go to )?(?:the )?home screen")) return did(a, "key", "home", "Going home");
        Matcher v = SET_VOLUME.matcher(s);
        if (v.matches()) {
            int n = number(v.group(1) != null ? v.group(1) : v.group(2));
            if (n >= 0 && n <= 100 && Actions.setVolume(a, n)) return log(said, "Volume " + n);
        }
        v = STEP_VOLUME.matcher(s);
        if (v.matches()) {
            String dir = v.group(1) != null ? v.group(1) : v.group(2) != null ? v.group(2) : v.group(3);
            boolean up = dir.equals("up") || dir.equals("increase") || dir.equals("raise");
            int n = number(v.group(4));
            if (n > 0 && n <= 50 && Actions.stepVolume(a, up, n)) return log(said, (up ? "Louder" : "Quieter") + " by " + n);
        }
        if (s.matches("(?:volume up|louder|turn (?:it|the volume) up|increase (?:the )?volume)")) return did(a, "key", "volume_up", "Louder");
        if (s.matches("(?:volume down|quieter|softer|turn (?:it|the volume) down|lower (?:the )?volume|decrease (?:the )?volume)")) return did(a, "key", "volume_down", "Quieter");
        if (s.matches("(?:mute|unmute)(?: (?:it|the tv|the sound))?")) return did(a, "key", "mute", "Mute toggled");
        if (s.matches("pause(?: it| this| the (?:show|film|movie|video))?")) return did(a, "key", "pause", "Paused");
        if (s.matches("(?:play|resume|continue|unpause)(?: it| this)?")) return did(a, "key", "play", "Playing");
        if (s.matches("(?:next|skip)(?: (?:episode|song|track|one))?")) return did(a, "key", "next", "Next");
        if (s.matches("(?:previous|go back)(?: (?:episode|song|track|one))?")) return did(a, "key", "previous", "Previous");

        Matcher m = OPEN.matcher(s);
        if (m.matches() && Actions.launch(a, m.group(1))) return log(said, "Opening " + titleCase(m.group(1)));
        // "open <something that isn't an app>" falls through to the agent

        m = SEARCH.matcher(s);
        if (m.matches() && Actions.search(a, m.group(1))) return log(said, "Searching for " + m.group(1));

        return null;
    }

    /** "30", "three" -> 30, 3; -1 if it isn't a number. */
    private static int number(String w) {
        if (w == null) return -1;
        if (w.matches("\\d{1,3}")) return Integer.parseInt(w);
        for (int i = 0; i < NUMBERS.length; i++) if (NUMBERS[i].equals(w)) return i;
        return -1;
    }

    private static String did(Activity a, String type, String arg, String reply) {
        boolean ok = type.equals("key") && Actions.key(a, arg);
        return ok ? log(type + " " + arg, reply) : null;
    }

    private static String log(String what, String reply) { Log.i(TAG, "done on the TV: " + what + " -> " + reply); return reply; }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
}
