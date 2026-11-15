package dev.pantheon.adultspace.news;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Without the agent's key, the Iron Bank falls back to a word list (News.MONEY). */
public class FinanceWordsTest {
    private static boolean money(String h) { return News.MONEY.matcher(h).find(); }

    @Test public void financeHeadlines() {
        assertTrue(money("Stock futures rise ahead of Nvidia earnings"));
        assertTrue(money("Bank of Canada holds interest rates steady"));
        assertTrue(money("Inflation cools to 2.1% in October"));
    }

    @Test public void notFinance() {
        assertFalse(money("Canadian travellers face delays as airlines recover"));
        assertFalse(money("Leafs beat the Canadiens in overtime"));
    }
}
