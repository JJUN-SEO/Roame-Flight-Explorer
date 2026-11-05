package travel.roame.takehome.bench

import kotlinx.coroutines.runBlocking
import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.data.SyncStatus
import travel.roame.takehome.data.local.CachedFlightEntity
import travel.roame.takehome.data.local.configure
import travel.roame.takehome.data.local.toCachedEntity
import travel.roame.takehome.data.local.toDomain
import travel.roame.takehome.domain.DealIndex
import travel.roame.takehome.domain.sortedFor
import travel.roame.takehome.domain.search.AIRPORT_PASSENGERS
import travel.roame.takehome.domain.search.AirportRanking
import travel.roame.takehome.domain.search.AirportSearchEngine
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.domain.search.RankingWeights
import travel.roame.takehome.domain.search.SearchableAirport
import travel.roame.takehome.domain.toDestinationGroups
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.model.SortOrder
import travel.roame.takehome.presentation.ExplorerInputs
import travel.roame.takehome.presentation.Snapshot
import travel.roame.takehome.presentation.index
import travel.roame.takehome.presentation.reduce
import travel.roame.takehome.testing.deleteDatabaseFiles
import travel.roame.takehome.testing.fileDatabaseBuilder
import travel.roame.takehome.testing.inMemoryDatabaseBuilder
import travel.roame.takehome.testing.newTempDatabasePath
import java.io.File
import kotlin.test.Test
import kotlin.time.Instant

/**
 * TEST_PLAN §4.1: the scale benchmarks, on [SyntheticData]. Each case is the median of 20 runs after warm-up runs.
 * They only run when asked: `./gradlew :shared:jvmTest -Pbench --tests "*ScaleBenchmark*"`. The report is written to
 * `shared/build/reports/scale-benchmarks.md`. JVM numbers stand in for phones, which are slower.
 */
class ScaleBenchmark {
    private val report = StringBuilder()
    private val now = Instant.fromEpochMilliseconds(1_793_000_000_000)

    @Test
    fun scale() {
        val runtime = Runtime.getRuntime()
        line("# Scale benchmarks (TEST_PLAN §4.1)")
        line()
        line("JVM ${System.getProperty("java.version")} on ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, " +
            "${runtime.availableProcessors()} cores, max heap ${runtime.maxMemory() / MB} MB. Median of $RUNS runs after warm-up; min–max in brackets.")

        pipeline()
        search()
        cache()
        aggregation()
        deals()
        sorts()
        memory()

        File("build/reports").mkdirs()
        File("build/reports/scale-benchmarks.md").writeText(report.toString())
        println(report)
    }

    /** `index()` runs once per data change; `reduce()` once per input change (search → filter → sort → aggregate). */
    private fun pipeline() {
        section("Pipeline: index per data change, reduce per input change", "Flights × airports", "index()", "reduce(), no input", "reduce(), typing 3 letters", "reduce(), Business cabin")
        for ((flightCount, airportCount) in listOf(1_000 to 32, 10_000 to 32, 100_000 to 32, 100_000 to 1_000)) {
            val airports = SyntheticData.airports(airportCount)
            val cache = CacheSnapshot(readBack(SyntheticData.flights(flightCount, airports)), now)
            val indexed = index(cache)
            fun reduceWith(inputs: ExplorerInputs) = time { reduce(Snapshot(indexed, SyncStatus(), emptyList(), inputs)) }
            row(
                "${count(flightCount)} × ${count(airportCount)}",
                time(warmups = 3) { index(cache) },
                reduceWith(ExplorerInputs()),
                reduceWith(ExplorerInputs(destinationQuery = airports[0].city.take(3).lowercase())),
                reduceWith(ExplorerInputs(filters = FlightFilters(cabin = "Business"))),
            )
        }
    }

    /** SCALE-1: one keystroke's airport lookup, with the destination field's ranking. */
    private fun search() {
        section("Airport search per keystroke", "Airports", "1 letter", "3 letters", "a typo (fuzzy)")
        for (airportCount in listOf(32, 1_000, 5_000)) {
            val airports = SyntheticData.airports(airportCount)
            val searchable = airports.map { SearchableAirport(it.code, it.city, GeoPoint(it.latitude, it.longitude)) }
            val engine = AirportSearchEngine(searchable)
            val ranking = AirportRanking.build(searchable, AIRPORT_PASSENGERS, airports.associate { it.code to 1 }, null, RankingWeights.DESTINATION)
            val city = airports.first { it.city.length >= 7 && ' ' !in it.city }.city.lowercase()
            row(
                count(airportCount),
                time { engine.search(city.take(1), ranking) },
                time { engine.search(city.take(3), ranking) },
                time { engine.search(typo(city), ranking) },
            )
        }
    }

    /** SCALE-3, R3: the whole cache replaced in one transaction, then read back, on a database file. */
    private fun cache() {
        section("Cache replace and read (Room, bundled SQLite, a file)", "Rows", "replaceAll()", "read()")
        val airports = SyntheticData.airports(32)
        for (rowCount in listOf(1_000, 10_000, 100_000)) {
            val rows = SyntheticData.flights(rowCount, airports).map { it.toCachedEntity() }
            val path = newTempDatabasePath()
            val db = fileDatabaseBuilder(path).configure()
            try {
                val dao = db.flightCacheDao()
                row(
                    count(rowCount),
                    time(warmups = 2) { runBlocking { dao.replaceAll(rows, now.toEpochMilliseconds()) } },
                    time(warmups = 2) { runBlocking { dao.read() } },
                )
            } finally {
                db.close()
                deleteDatabaseFiles(path)
            }
        }
    }

    /** B1–B8: the landing page's deals, built once per data change inside `index()`; a location only picks the home airport's. */
    private fun deals() {
        section("Landing page deals", "Flights × airports", "DealIndex.of()", "reduce(), with a location")
        val ottawa = ExplorerInputs(userLocation = GeoPoint(45.42, -75.70))
        for ((flightCount, airportCount) in listOf(1_000 to 32, 10_000 to 32, 100_000 to 32, 100_000 to 1_000)) {
            val flights = readBack(SyntheticData.flights(flightCount, SyntheticData.airports(airportCount)))
            val indexed = index(CacheSnapshot(flights, now))
            row(
                "${count(flightCount)} × ${count(airportCount)}",
                time(warmups = 3) { DealIndex.of(flights) },
                time { reduce(Snapshot(indexed, SyncStatus(), emptyList(), ottawa)) },
            )
        }
    }

    /** O1 (D46): each sort's order, made the first time it's used after a data change, then kept. Best offer rates first. */
    private fun sorts() {
        section("Sort orders, each once per data change", "Flights", *SortOrder.entries.map { it.name }.toTypedArray())
        for (flightCount in listOf(1_000, 10_000, 100_000)) {
            val flights = readBack(SyntheticData.flights(flightCount, SyntheticData.airports(32)))
            val times = SortOrder.entries.map { sort -> time(warmups = 3) { flights.sortedFor(sort) } }
            row(count(flightCount), *times.toTypedArray())
        }
    }

    /** M2, SCALE-5: grouping the results into one marker per destination. */
    private fun aggregation() {
        section("Map aggregation", "Flights × destinations", "toDestinationGroups()")
        for ((flightCount, airportCount) in listOf(1_000 to 32, 10_000 to 32, 100_000 to 32, 100_000 to 1_000)) {
            val flights = readBack(SyntheticData.flights(flightCount, SyntheticData.airports(airportCount)))
            row("${count(flightCount)} × ${count(airportCount)}", time { flights.toDestinationGroups() })
        }
    }

    /** SCALE-7: what the dataset and its indexes keep on the heap. Approximate: measured after garbage collection. */
    private fun memory() {
        section("Memory kept (heap after garbage collection)", "Flights × airports", "Flights", "index()", "Together")
        for ((flightCount, airportCount) in listOf(10_000 to 32, 100_000 to 32, 100_000 to 1_000)) {
            val generated = SyntheticData.flights(flightCount, SyntheticData.airports(airportCount))
            val before = usedHeap()
            val flights = readBack(generated)
            val withFlights = usedHeap()
            val indexed = index(CacheSnapshot(flights, now))
            val withIndex = usedHeap()
            sink = listOf(flights, indexed)
            row(
                "${count(flightCount)} × ${count(airportCount)}",
                megabytes(withFlights - before),
                megabytes(withIndex - withFlights),
                megabytes(withIndex - before),
            )
            sink = null
        }
    }

    /**
     * The flights as the app holds them: written to the cache and read back, as `FlightRepository` does. Each flight then
     * has its own airport objects and strings, unlike [SyntheticData]'s, which share them.
     */
    private fun readBack(flights: List<Flight>): List<Flight> {
        val db = inMemoryDatabaseBuilder().configure()
        try {
            val dao = db.flightCacheDao()
            return runBlocking {
                dao.replaceAll(flights.map { it.toCachedEntity() }, now.toEpochMilliseconds())
                dao.read().flights.map(CachedFlightEntity::toDomain)
            }
        } finally {
            db.close()
        }
    }

    private fun time(warmups: Int = 5, block: () -> Any?): String {
        repeat(warmups) { sink = block() }
        val ms = DoubleArray(RUNS) {
            val start = System.nanoTime()
            sink = block()
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val median = (ms[RUNS / 2 - 1] + ms[RUNS / 2]) / 2
        return "${format(median)} ms (${format(ms.first())}–${format(ms.last())})"
    }

    private fun usedHeap(): Long {
        val runtime = Runtime.getRuntime()
        repeat(3) {
            System.gc()
            Thread.sleep(200)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /** Drops the fourth letter, so no prefix matches and the fuzzy tier has to run. */
    private fun typo(word: String) = word.removeRange(3, 4)

    private fun section(title: String, vararg columns: String) {
        line()
        line("## $title")
        line()
        line(columns.joinToString(" | ", "| ", " |"))
        line(columns.joinToString(" | ", "| ", " |") { "---" })
    }

    private fun row(vararg cells: String) = line(cells.joinToString(" | ", "| ", " |"))

    private fun line(text: String = "") {
        report.appendLine(text)
    }

    private fun count(n: Int) = "%,d".format(n)

    private fun format(ms: Double) = if (ms < 10) "%.2f".format(ms) else "%.0f".format(ms)

    private fun megabytes(bytes: Long) = "%.1f MB".format(bytes / MB.toDouble())

    private companion object {
        const val RUNS = 20
        const val MB = 1024L * 1024
        // Keeps results reachable, so the JIT can't skip the work being timed.
        @Volatile var sink: Any? = null
    }
}
