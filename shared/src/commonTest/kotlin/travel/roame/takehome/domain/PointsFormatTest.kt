package travel.roame.takehome.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES T1, T2. */
class PointsFormatTest {

    @Test
    fun t1_fullPointsUseUsGrouping() {
        assertEquals("25,000 pts", PointsFormat.full(25_000))
        assertEquals("9,000 pts", PointsFormat.full(9_000))
        assertEquals("101,500 pts", PointsFormat.full(101_500))
        assertEquals("133,000 pts", PointsFormat.full(133_000))
    }

    @Test
    fun t1_groupingWorksAtEveryLength() {
        assertEquals("0 pts", PointsFormat.full(0))
        assertEquals("950 pts", PointsFormat.full(950))
        assertEquals("1,000 pts", PointsFormat.full(1_000))
        assertEquals("1,000,000 pts", PointsFormat.full(1_000_000))
    }

    @Test
    fun t2_compactPointsDropAWholeThousandsDecimal() {
        assertEquals("9k", PointsFormat.compact(9_000))
        assertEquals("25k", PointsFormat.compact(25_000))
        assertEquals("133k", PointsFormat.compact(133_000))
    }

    @Test
    fun t2_compactPointsKeepAHalfThousand() {
        assertEquals("101.5k", PointsFormat.compact(101_500))
        assertEquals("9.5k", PointsFormat.compact(9_500))
    }

    @Test
    fun t2_valuesBelowOneThousandAreShownAsTheyAre() {
        assertEquals("950", PointsFormat.compact(950))
        assertEquals("999", PointsFormat.compact(999))
        assertEquals("0", PointsFormat.compact(0))
    }

    @Test
    fun t2_oneThousandIsTheFirstCompactValue() {
        assertEquals("1k", PointsFormat.compact(1_000))
    }

    @Test
    fun t2_otherValuesRoundToOneDecimal() {
        assertEquals("25.3k", PointsFormat.compact(25_250))
        assertEquals("25.2k", PointsFormat.compact(25_249))
        assertEquals("26k", PointsFormat.compact(25_960))
    }
}
