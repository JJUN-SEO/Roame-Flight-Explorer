package travel.roame.takehome.bench

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import kotlin.random.Random

/**
 * Seeded data in the fixture's shape, for the scale benchmarks (TEST_PLAN §4.1): random routes, dates, cabins,
 * programs and points. The airports, cities and programs are made up, so no fixture fact is copied. The same seed
 * always gives the same data.
 */
internal object SyntheticData {
    private val syllables = listOf(
        "ba", "ca", "da", "fa", "ga", "ka", "la", "ma", "na", "pa", "ra", "sa", "ta", "va",
        "be", "de", "le", "me", "ne", "re", "se", "te", "bi", "di", "li", "mi", "ni", "ri", "si", "ti",
        "bo", "do", "lo", "mo", "no", "ro", "so", "to", "bu", "du", "lu", "mu", "ru", "tu",
        "an", "en", "in", "on", "ar", "er", "or", "ton", "ville", "burg", "port", "field",
    )
    private val programs = List(8) { "Program ${'A' + it}" }
    private val firstDate = LocalDate(2027, 2, 1)

    fun airports(count: Int, seed: Long = 1): List<Airport> {
        val random = Random(seed)
        val codes = LinkedHashSet<String>()
        while (codes.size < count) codes += String(CharArray(3) { 'A' + random.nextInt(26) })
        return codes.map { code ->
            Airport(code, city(random), latitude = random.nextDouble(-60.0, 70.0), longitude = random.nextDouble(-180.0, 180.0))
        }
    }

    fun flights(count: Int, airports: List<Airport>, seed: Long = 2): List<Flight> {
        val random = Random(seed)
        return List(count) { i ->
            val origin = airports[random.nextInt(airports.size)]
            var destination = airports[random.nextInt(airports.size)]
            while (destination.code == origin.code) destination = airports[random.nextInt(airports.size)]
            val roll = random.nextDouble()
            val (cabin, points) = when {
                roll < 0.6 -> "Economy" to random.nextInt(20, 121) * 500
                roll < 0.75 -> "Premium Economy" to random.nextInt(50, 161) * 500
                else -> "Business" to random.nextInt(80, 301) * 500
            }
            Flight(
                id = "syn-$i",
                origin = origin,
                destination = destination,
                departureDate = firstDate.plus(random.nextInt(60), DateTimeUnit.DAY),
                program = programs[random.nextInt(programs.size)],
                points = points,
                cabin = cabin,
            )
        }
    }

    /** Two to four syllables, sometimes two words, so prefixes overlap and fuzzy search has real work to do. */
    private fun city(random: Random): String {
        fun word() = List(random.nextInt(2, 5)) { syllables[random.nextInt(syllables.size)] }
            .joinToString("").replaceFirstChar { it.uppercase() }
        return if (random.nextInt(5) == 0) "${word()} ${word()}" else word()
    }
}
