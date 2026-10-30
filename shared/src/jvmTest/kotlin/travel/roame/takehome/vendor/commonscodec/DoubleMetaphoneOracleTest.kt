package travel.roame.takehome.vendor.commonscodec

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.apache.commons.codec.language.DoubleMetaphone as OriginalDoubleMetaphone

/**
 * Proves the Kotlin translation behaves exactly like the original Java library (commons-codec
 * 1.23.0, test dependency only) without auditing it line by line: both encode the same large
 * corpus and every primary and alternate code must be identical.
 */
class DoubleMetaphoneOracleTest {

    @Test
    fun s8_portMatchesCommonsCodecOnCuratedWords() = compare(CURATED + CITY_WORDS)

    @Test
    fun s8_portMatchesCommonsCodecOnAllShortStrings() {
        val letters = ('A'..'Z').map { it.toString() }
        val words = ArrayList<String>()
        for (a in letters) {
            words += a
            for (b in letters) {
                words += a + b
                for (c in letters) words += a + b + c
            }
        }
        compare(words)
    }

    @Test
    fun s8_portMatchesCommonsCodecOnGeneratedWords() {
        val random = Random(20261030)
        val words = List(150_000) {
            buildString {
                repeat(random.nextInt(1, 6)) {
                    append(
                        when (random.nextInt(4)) {
                            0 -> CLUSTERS[random.nextInt(CLUSTERS.size)]
                            1 -> "AEIOUY"[random.nextInt(6)]
                            else -> ALPHABET[random.nextInt(ALPHABET.length)]
                        },
                    )
                }
            }
        }
        compare(words)
    }

    private fun compare(words: Collection<String>) {
        val mismatches = ArrayList<String>()
        for (maxCodeLen in listOf(1, 4, 6, 12)) {
            val port = DoubleMetaphone(maxCodeLen)
            val original = OriginalDoubleMetaphone().apply { setMaxCodeLen(maxCodeLen) }
            for (word in words) {
                for (alternate in listOf(false, true)) {
                    val expected = original.doubleMetaphone(word, alternate)
                    val actual = port.doubleMetaphone(word, alternate)
                    if (expected != actual && mismatches.size < 20) {
                        mismatches += "\"$word\" len=$maxCodeLen alt=$alternate: expected $expected, got $actual"
                    }
                }
            }
        }
        if (mismatches.isNotEmpty()) fail(mismatches.joinToString("\n"))
        assertEquals(0, mismatches.size)
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ ÇÑ"

        /** Letter groups that trigger the algorithm's special-case rules. */
        val CLUSTERS = listOf(
            "CH", "SCH", "GH", "GN", "KN", "PN", "WR", "PS", "PH", "TH", "TTH", "TCH", "CC", "CIA", "CZ", "WICZ",
            "WITZ", "SUGAR", "ISL", "YSL", "SIAN", "SIO", "DG", "DT", "JOSE", "SAN ", "VAN ", "VON ", "MC", "ACH",
            "BACHER", "MACHER", "CAESAR", "CHIA", "CHAE", "HARAC", "HOR", "ORCHES", "ARCHIT", "ILLO", "ILLA", "ALLE",
            "UMB", "TION", "TIA", "EAU", "IAU", "AU", "OU", "ZH", "ZZ", "ZO", "ZI", "WH", "EWSKI", "OWSKY", "GLI",
            "GY", "GER", "AGGI", "OGGI", "HU", "UCCEE", "UCCES", "IER", "ET", "RGY", "OGY", "DANGER", "ME", "MA",
            "IE", "AI", "OI", "HEIM", "HOLM", "OO", "ER", "EN", "UY", "ED", "EM", "CK", "CQ", "CG", "CE", "CI",
        )

        val CURATED = listOf(
            "bacchus", "accident", "accede", "succeed", "focaccia", "bellocchio", "bertucci", "schermerhorn",
            "schenker", "school", "schooner", "schlesinger", "sugar", "island", "isle", "carlisle", "carlysle",
            "thomas", "thames", "edge", "edgar", "laugh", "cough", "rough", "hugh", "mclaughlin", "jose",
            "san jacinto", "czerny", "filipowicz", "breaux", "zhao", "zhang", "wasserman", "vasserman", "arnow",
            "arnoff", "michael", "chemistry", "chorus", "chore", "orchestra", "orchid", "architect", "mcclelland",
            "caesar", "chianti", "gnome", "knight", "pneumonia", "wright", "psychology", "danger", "ranger",
            "manger", "biaggi", "tagliaro", "cabrillo", "gallegos", "dumb", "thumb", "rogier", "resnais", "artois",
            "smith", "schmidt", "snider", "schneider", "kuczewski", "mac caffrey", "mac gregor", "van gogh",
            "von braun", "jankelowicz", "ghislane", "ghiradelli", "hochmeier", "womo", "uomo", "xavier", "jalapeño",
            "garçon", "", "   ", " a ", "o'hare", "st. louis",
        )

        val CITY_WORDS = listOf(
            "auckland", "amsterdam", "athens", "barcelona", "bangkok", "boston", "paris", "doha", "dublin", "dubai",
            "rome", "frankfurt", "saopaulo", "sao paulo", "hongkong", "hong kong", "tokyo", "seoul", "johannesburg",
            "newyork", "new york", "losangeles", "chicago", "london", "lisbon", "madrid", "melbourne", "mexicocity",
            "seattle", "sanfrancisco", "singapore", "sydney", "vancouver", "toronto", "sidnee", "shicago", "okland",
            "yohannesburg", "melborn", "lundun", "dooblin", "ateens", "lizbon", "madreed", "seol", "tokio", "roam",
            "dubay", "meksiko", "sowpaulo", "siattle", "vankuver", "tronto", "doja", "newark", "sitty",
        )
    }
}
