package dev.pantheon.adultspace.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** TMDB's provider names have to meet the TV's app labels (Services.norm is the shared form both are reduced to). */
public class ServicesTest {
    @Test public void amazonPrimeVideoMatchesThePrimeVideoApp() {
        assertEquals(Services.norm("Prime Video"), Services.norm("Amazon Prime Video"));
    }

    @Test public void plusAndTheWordPlusAreTheSame() {
        assertEquals(Services.norm("Disney+"), Services.norm("Disney Plus"));
        assertEquals(Services.norm("Apple TV+"), Services.norm("Apple TV Plus"));
    }

    @Test public void punctuationAndCaseDontMatter() {
        assertEquals("jiohotstar", Services.norm("JioHotstar"));
        assertEquals("crave", Services.norm("Crave"));
    }
}
