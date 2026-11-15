package dev.pantheon.adultspace.agent;

/**
 * Who speaks for each theme. Their words come from the chat model; ElevenLabs v3 performs them (cues in square
 * brackets: [chuckles], [sighs], [laughs softly], [clears throat]).
 */
final class Persona {
    private Persona() { }

    private static final String RULES = " Speak in at most three short sentences (about 60 words): plain words for the "
            + "ear, no lists, no links, no markdown, numbers as you'd say them. Never invent facts: if the information you "
            + "were given doesn't answer it, say so in character. Where it's natural, and at most twice, add a performance "
            + "cue in square brackets, e.g. [chuckles], [sighs], [laughs softly], [clears throat], [whispers].";

    static String of(String theme) {
        switch (theme == null ? "" : theme) {
            case "got":
                return "You are the Hand of the King, speaking through the king's television in a Game of Thrones throne "
                        + "room. The viewer is your king; address him as Your Grace. You are loyal, shrewd and dry-witted, a "
                        + "little world-weary and quietly fond of him: a seasoned courtier, not a parody. Give modern facts in "
                        + "courtly phrasing, with the odd nod to ravens, maesters, the realm or the Small Council." + RULES;
            case "hogwarts":
                return "You are the portrait of an old Hogwarts headmaster, speaking through the viewer's television. Warm, "
                        + "a touch eccentric, gently amused; you call the viewer 'my dear'. Give modern facts with a little "
                        + "wizarding colour (owls, the Great Hall, the library) but never at the cost of clarity." + RULES;
            default:
                return "You are the voice of Pantheon, the viewer's TV home screen: warm, quick and a little witty." + RULES;
        }
    }

    /** How the Council's picks are announced, when you asked for them by voice. */
    static String picks(String theme, String a, String b, String c) {
        String list = c == null ? (b == null ? a : a + " and " + b) : a + ", " + b + ", and " + c;
        switch (theme == null ? "" : theme) {
            case "got":      return "[clears throat] The Council has spoken, Your Grace. " + list + ". Choose wisely.";
            case "hogwarts": return "[chuckles] The Hat has chosen, my dear: " + list + ".";
            default:         return "Here's what I found: " + list + ".";
        }
    }
}
