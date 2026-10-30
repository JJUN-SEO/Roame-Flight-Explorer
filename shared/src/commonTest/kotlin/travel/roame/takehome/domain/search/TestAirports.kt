package travel.roame.takehome.domain.search

/**
 * The 32 fixture airports, split by side as they appear in `fixtures/flights.json`.
 * Test data only; `FixtureContractTest` (jvmTest) fails if this drifts from the fixture.
 */
internal object TestAirports {

    fun airport(code: String, city: String, latitude: Double, longitude: Double) =
        SearchableAirport(code, city, GeoPoint(latitude, longitude))

    val origins = listOf(
        airport("BOS", "Boston", 42.36197, -71.0079),
        airport("JFK", "New York", 40.63945, -73.77932),
        airport("LAX", "Los Angeles", 33.9425, -118.408),
        airport("ORD", "Chicago", 41.9786, -87.9048),
        airport("SEA", "Seattle", 47.44794, -122.31028),
        airport("SFO", "San Francisco", 37.61981, -122.37482),
        airport("YVR", "Vancouver", 49.1939, -123.184),
        airport("YYZ", "Toronto", 43.67594, -79.62942),
    )

    val destinations = listOf(
        airport("AKL", "Auckland", -37.01199, 174.78633),
        airport("AMS", "Amsterdam", 52.3086, 4.76389),
        airport("ATH", "Athens", 37.9364, 23.9445),
        airport("BCN", "Barcelona", 41.2971, 2.07846),
        airport("BKK", "Bangkok", 13.6811, 100.747),
        airport("CDG", "Paris", 49.00896, 2.55412),
        airport("DOH", "Doha", 25.27306, 51.60806),
        airport("DUB", "Dublin", 53.42871, -6.26212),
        airport("DXB", "Dubai", 25.24979, 55.37099),
        airport("FCO", "Rome", 41.80453, 12.252),
        airport("FRA", "Frankfurt", 50.02671, 8.55835),
        airport("GRU", "Sao Paulo", -23.43127, -46.46995),
        airport("HKG", "Hong Kong", 22.31184, 113.91486),
        airport("HND", "Tokyo", 35.54968, 139.78696),
        airport("ICN", "Seoul", 37.4691, 126.451),
        airport("JNB", "Johannesburg", -26.14008, 28.2468),
        airport("LHR", "London", 51.47075, -0.45991),
        airport("LIS", "Lisbon", 38.7813, -9.13592),
        airport("MAD", "Madrid", 40.49341, -3.57225),
        airport("MEL", "Melbourne", -37.67073, 144.8379),
        airport("MEX", "Mexico City", 19.43582, -99.07033),
        airport("NRT", "Tokyo", 35.76858, 140.38871),
        airport("SIN", "Singapore", 1.35019, 103.994),
        airport("SYD", "Sydney", -33.9461, 151.177),
    )

    /** Flights per airport in `flights.json`: the fallback volume signal (P4). */
    val originFlightCounts = mapOf(
        "BOS" to 129, "JFK" to 113, "LAX" to 125, "ORD" to 126, "SEA" to 121, "SFO" to 123, "YVR" to 126, "YYZ" to 137,
    )

    val destinationFlightCounts = mapOf(
        "AKL" to 49, "AMS" to 43, "ATH" to 42, "BCN" to 43, "BKK" to 37, "CDG" to 45, "DOH" to 43, "DUB" to 29,
        "DXB" to 40, "FCO" to 37, "FRA" to 45, "GRU" to 45, "HKG" to 25, "HND" to 55, "ICN" to 40, "JNB" to 39,
        "LHR" to 49, "LIS" to 33, "MAD" to 54, "MEL" to 41, "MEX" to 32, "NRT" to 55, "SIN" to 36, "SYD" to 43,
    )

    val originEngine by lazy { AirportSearchEngine(origins) }
    val destinationEngine by lazy { AirportSearchEngine(destinations) }
}
