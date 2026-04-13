package com.example.aiassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternMatcherTest {

    @Test
    fun findBestMatchingTextElement_exactMatchPreferred() {
        val elements = listOf("Allow Once", "Allow", "Continue")

        val match = PatternMatcher.findBestMatchingTextElement(elements, "Allow")

        assertEquals("Allow", match)
    }

    @Test
    fun findBestMatchingTextElement_caseInsensitiveMatch() {
        val elements = listOf("dismiss", "Skip")

        val match = PatternMatcher.findBestMatchingTextElement(elements, "DISMISS")

        assertEquals("dismiss", match)
    }

    @Test
    fun findBestMatchingTextElement_unrelatedStrings_returnsNull() {
        val elements = listOf("Purchase", "Delete", "Cancel")

        val match = PatternMatcher.findBestMatchingTextElement(elements, "Allow")

        assertNull(match)
    }
}
