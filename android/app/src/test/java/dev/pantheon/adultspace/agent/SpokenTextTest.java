package dev.pantheon.adultspace.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What the Hand says has to be fit for the ear (OpenRouter.clean) and for the screen (Speaker.plain). */
public class SpokenTextTest {
    @Test public void citationsAndLinksAreRemoved() {
        String raw = "GR00T is Nvidia's robot platform [nvidia.com](https://www.nvidia.com/groot). Weights open in December, per [The Verge](https://theverge.com/x).";
        assertEquals("GR00T is Nvidia's robot platform. Weights open in December.", OpenRouter.clean(raw));
    }

    @Test public void bareUrlsAndMarkdownAreRemoved() {
        assertEquals("See the report for details.", OpenRouter.clean("See the **report** (https://example.com/a) for details."));
    }

    @Test public void performanceCuesSurviveCleaning() {
        String out = OpenRouter.clean("[clears throat] Your Grace, the ravens carry more dread than numbers. [dryly]");
        assertTrue(out.startsWith("[clears throat]"));
        assertTrue(out.endsWith("[dryly]"));
    }

    @Test public void oddSpacesBeforePunctuationAreTidied() {
        assertEquals("A quarter of the board cancelled.", OpenRouter.clean("A quarter of the board cancelled ."));
    }

    @Test public void plainStripsCuesForTheScreen() {
        assertEquals("At once, Your Grace. It is done.", Speaker.plain("[clears throat] At once, Your Grace. [chuckles] It is done."));
    }

    @Test public void plainLeavesOrdinaryBracketsLongerThanACueAlone() {
        String s = "He said [this is a much longer aside that is clearly not a performance cue] and left.";
        assertEquals(s, Speaker.plain(s));
    }
}
