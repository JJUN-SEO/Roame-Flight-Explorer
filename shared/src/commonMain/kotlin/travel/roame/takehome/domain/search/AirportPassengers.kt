package travel.roame.takehome.domain.search

/**
 * Approximate 2024 annual passengers per airport: a copy of `fixtures/airports.json` that ships
 * inside the app, so ranking is the same online and offline (DOMAIN_RULES P3, D15). `FixtureContractTest`
 * fails if the two drift apart. An airport missing from here uses its flight count instead (P4).
 *
 * The figures were recalled by an AI assistant and not verified against primary sources: accurate
 * enough to rank airports, not to quote.
 */
internal val AIRPORT_PASSENGERS: Map<String, Long> = mapOf(
    "AKL" to 18_700_000L,
    "AMS" to 66_800_000L,
    "ATH" to 31_800_000L,
    "BCN" to 55_000_000L,
    "BKK" to 62_200_000L,
    "BOS" to 43_500_000L,
    "CDG" to 70_300_000L,
    "DOH" to 52_700_000L,
    "DUB" to 34_600_000L,
    "DXB" to 92_300_000L,
    "FCO" to 49_200_000L,
    "FRA" to 61_600_000L,
    "GRU" to 43_600_000L,
    "HKG" to 53_100_000L,
    "HND" to 85_900_000L,
    "ICN" to 71_200_000L,
    "JFK" to 63_300_000L,
    "JNB" to 20_500_000L,
    "LAX" to 76_600_000L,
    "LHR" to 83_900_000L,
    "LIS" to 35_100_000L,
    "MAD" to 66_200_000L,
    "MEL" to 36_400_000L,
    "MEX" to 45_400_000L,
    "NRT" to 39_600_000L,
    "ORD" to 80_000_000L,
    "SEA" to 52_600_000L,
    "SFO" to 52_000_000L,
    "SIN" to 67_700_000L,
    "SYD" to 41_400_000L,
    "YVR" to 26_400_000L,
    "YYZ" to 46_800_000L,
)
