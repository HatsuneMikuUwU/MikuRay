package com.miku.ray.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DropdownFilterTest {
    private val names = listOf("123abc", "hk-hongkong", "Japan-Tokyo")

    @Test
    fun blankQueryKeepsEveryOption() {
        assertEquals(names, filterDropdownOptions(names, ""))
        assertEquals(names, filterDropdownOptions(names, "   "))
    }

    @Test
    fun exactMatchKeepsEveryOptionSoTheFieldStillBrowses() {
        assertEquals(names, filterDropdownOptions(names, "123abc"))
    }

    @Test
    fun queryFromTheMiddleMatches() {
        assertEquals(listOf("123abc"), filterDropdownOptions(names, "ab"))
    }

    @Test
    fun everyMatchingOptionIsKeptInItsOriginalOrder() {
        assertEquals(listOf("123abc", "Japan-Tokyo"), filterDropdownOptions(names, "a"))
    }

    @Test
    fun queryIgnoresCase() {
        assertEquals(listOf("Japan-Tokyo"), filterDropdownOptions(names, "TOKYO"))
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        assertEquals(listOf("hk-hongkong"), filterDropdownOptions(names, " ng "))
    }

    @Test
    fun unmatchedQueryKeepsNothing() {
        assertEquals(emptyList<String>(), filterDropdownOptions(names, "zzz"))
    }
}
