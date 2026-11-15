package dev.pantheon.adultspace.agent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PersonaTest {
    @Test public void eachThemeNamesBothSides() {
        assertArrayEquals(new String[] { "The King", "The Hand" }, Persona.names("got"));
        assertArrayEquals(new String[] { "You", "The Headmaster" }, Persona.names("hogwarts"));
        assertArrayEquals(new String[] { "You", "Pantheon" }, Persona.names("marvel"));
    }

    @Test public void briefByDefaultDetailedOnRequest() {
        assertTrue(Persona.of("got").contains("35 words at most"));
        assertTrue(Persona.of("got", true).contains("up to six sentences"));
    }

    @Test public void theHandIsTheHand() {
        assertTrue(Persona.of("got").contains("Hand of the King"));
        assertTrue(Persona.of("got").contains("[chuckles]"));
    }

    @Test public void councilAnnouncementListsThePicks() {
        String s = Persona.picks("got", "Upload", "Futurama", "Silicon Valley");
        assertTrue(s.contains("Upload, Futurama, and Silicon Valley"));
    }
}
