package com.example.aiassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenReactorTest {

    @Test
    fun keywordsMatch_halfOrMoreKeywordsPresent_returnsTrue() {
        val matched = ScreenReactor.keywordsMatch(
            storedKeywords = "sponsored,visit advertiser,skip",
            screenText = listOf("Sponsored content", "Tap to Skip")
        )

        assertTrue(matched)
    }

    @Test
    fun keywordsMatch_noKeywordsPresent_returnsFalse() {
        val matched = ScreenReactor.keywordsMatch(
            storedKeywords = "allow,continue",
            screenText = listOf("Purchase now", "Delete")
        )

        assertFalse(matched)
    }

    @Test
    fun keywordsMatch_emptyStoredKeywords_returnsFalse() {
        val matched = ScreenReactor.keywordsMatch(
            storedKeywords = "",
            screenText = listOf("Allow")
        )

        assertFalse(matched)
    }
}
