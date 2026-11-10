package com.strmr.tv.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageChoicesTest {
    @Test fun regionalDubsFollowTheirLanguage() {
        val names = LanguageChoices.options(regional = true).map { it.name }
        val french = names.indexOf("French")
        assertEquals(listOf("French (Canada)", "French (France)"), names.subList(french + 1, french + 3))
        assertEquals(false, LanguageChoices.options(regional = false).any { it.name == "French (Canada)" })
    }

    @Test fun findsAChoiceByAnySpellingOfItsCode() {
        val options = LanguageChoices.options(regional = true)
        assertEquals("French", options[LanguageChoices.indexOf(options, "fre")!!].name)
        assertEquals("French (Canada)", options[LanguageChoices.indexOf(options, "fr-ca")!!].name)
        assertNull(LanguageChoices.indexOf(options, null))
        assertEquals(-1, LanguageChoices.indexOf(options, "xx"))
    }
}
