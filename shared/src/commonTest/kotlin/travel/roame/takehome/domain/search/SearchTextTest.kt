package travel.roame.takehome.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTextTest {

    @Test
    fun s1_trimsAndLowercases() {
        assertEquals("boston", SearchText.normalize("  BoStOn "))
    }

    @Test
    fun s1_collapsesInternalWhitespace() {
        assertEquals("new york", SearchText.normalize("new \t  york"))
    }

    @Test
    fun s1_foldsAccents() {
        assertEquals("sao paulo", SearchText.normalize("São Paulo"))
        assertEquals("mexico", SearchText.normalize("MÉXICO"))
        assertEquals("zurich", SearchText.normalize("Zürich"))
        assertEquals("strasse", SearchText.normalize("Straße"))
        assertEquals("lodz", SearchText.normalize("Łódź"))
    }

    @Test
    fun s1_dropsApostrophesAndSplitsOnPunctuation() {
        assertEquals("ohare", SearchText.normalize("O'Hare"))
        assertEquals("st louis", SearchText.normalize("St. Louis"))
        assertEquals("winston salem", SearchText.normalize("Winston-Salem"))
    }

    @Test
    fun s1_blankAndSymbolOnlyInputsNormalizeToEmpty() {
        assertEquals("", SearchText.normalize("   "))
        assertEquals("", SearchText.normalize("--- !"))
    }

    @Test
    fun s1_compactRemovesSpaces() {
        assertEquals("hongkong", SearchText.compact("hong kong"))
    }
}
