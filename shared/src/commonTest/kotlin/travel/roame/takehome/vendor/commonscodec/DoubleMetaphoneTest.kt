package travel.roame.takehome.vendor.commonscodec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Known vectors for the vendored Double Metaphone port. These run on every target, including
 * iOS, so they check the translation on Kotlin/Native too. The exhaustive comparison against
 * the original Java library is DoubleMetaphoneOracleTest (jvmTest).
 *
 * The first block of vectors is copied from Apache Commons Codec's own DoubleMetaphoneTest
 * (Apache License 2.0).
 */
class DoubleMetaphoneTest {

    private val dm = DoubleMetaphone()

    @Test
    fun s8_primaryCodesMatchCommonsCodecVectors() {
        mapOf(
            "testing" to "TSTN", "The" to "0", "quick" to "KK", "brown" to "PRN", "fox" to "FKS",
            "jumped" to "JMPT", "over" to "AFR", "lazy" to "LS", "dogs" to "TKS", "MacCafferey" to "MKFR",
            "Stephan" to "STFN", "Kuczewski" to "KSSK", "McClelland" to "MKLL", "san jose" to "SNHS",
            "xenophobia" to "SNFP",
        ).forEach { (word, code) -> assertEquals(code, dm.doubleMetaphone(word), word) }
    }

    @Test
    fun s8_alternateCodesMatchCommonsCodecVectors() {
        mapOf(
            "testing" to "TSTN", "The" to "T", "jumped" to "AMPT", "Kutchefski" to "KXFS", "Fokker" to "FKR",
            "Joqqi" to "AK", "Hovvi" to "HF", "Czerny" to "XRN",
        ).forEach { (word, code) -> assertEquals(code, dm.doubleMetaphone(word, alternate = true), word) }
    }

    @Test
    fun s8_soundAlikeCityNamesShareCodes() {
        val long = DoubleMetaphone(maxCodeLen = 6)
        assertEquals(long.doubleMetaphone("sydney"), long.doubleMetaphone("sidnee"))
        assertEquals(long.doubleMetaphone("chicago"), long.doubleMetaphone("shicago"))
        assertEquals(long.doubleMetaphone("auckland"), long.doubleMetaphone("okland"))
        // "Johannesburg" (J or Y sound) meets "Yohannesburg" through the alternate code.
        assertEquals(long.doubleMetaphone("johannesburg", alternate = true), long.doubleMetaphone("yohannesburg"))
    }

    @Test
    fun s8_blankInputHasNoCode() {
        assertNull(dm.doubleMetaphone(null))
        assertNull(dm.doubleMetaphone("   "))
        assertTrue(dm.isDoubleMetaphoneEqual("Smith", "Smyth"))
    }
}
