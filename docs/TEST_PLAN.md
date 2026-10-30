# Test plan

## 1. Strategy

| Layer | What | How | Runs on |
|---|---|---|---|
| Pure domain | Search, filters, ordering, active filters, aggregation, great-circle, formatting | Table-driven unit tests with no coroutines | `commonTest` (JVM + iOS sim) |
| Data | API client, cache, saved flights, transactions | Ktor `MockEngine` + in-memory or temp-file SQLite | `commonTest` / `jvmTest` |
| Store | Offline state machine, selection, latest-wins, failure messages | Fakes + `runTest` + Turbine | `commonTest` |
| Contract | The real fixture decodes and its facts hold | Read `fixtures/flights.json` | `jvmTest` |
| Platform UI | Rendering, gestures, states | **Manual QA checklist (§5)**, filled in on both platforms | Emulator / simulator |

The brief asks for *focused* shared tests plus a written record of the checks performed on both platforms. Platform UI automation is out of scope unless time allows (see PLAN cut lines).

**Naming.** Prefix each test with the rule ID and use camelCase, so it works on Kotlin/Native: `@Test fun f4_pointsEqualToMax_isIncluded()`.

**Test helpers** live in `commonTest/.../testing/`:

- `flight(id = "x", origin = "BOS", destination = "CDG", date = "2027-02-01", points = 25_000, cabin = "Economy", program = "Avios")`: a builder with sensible defaults.
- `FakeFlightsApi`: a scripted queue of responses or errors, with a configurable delay.
- `FakeSavedFlightsRepository(failOn = setOf(Op.Save))`: for V7.
- `TestClock`: a settable `now`.
- `testDb()`: `JdbcSqliteDriver(IN_MEMORY)` on the JVM, or a temp file for the "reopen" tests.

## 2. Shared test matrix

| Test class | Rules | Cases |
|---|---|---|
| `SearchNormalizationTest` | S1, S2 | trim; mixed case; internal spaces collapsed; blank and whitespace-only → matches all |
| `SearchMatchTest` | S3–S5 | Every row of the S5 table; origin and destination fields are independent; both filled → AND; no false positives (`"ondon"`) |
| `DateFilterTest` | F1–F3 | start-only; end-only; both; **flight on start included**; **flight on end included**; day before start and day after end excluded; start == end matches a single day; start > end → empty; null/null → all |
| `PointsFilterTest` | F4 | **points == max included**; max − 500 → excluded flight at max; max + 500 → included; null → all; max below the dataset min → empty |
| `CabinFilterTest` | F5, DM1 | single cabin; null → all; unknown cabin value decodes and sorts last |
| `ProgramFilterTest` | F6 | one program; two programs (OR); empty → all; "Mileage Plan" ≠ "MileagePlus" |
| `CombinedFilterTest` | F7 | search + date + cabin + points + programs → only flights that satisfy every one; removing one filter widens the results |
| `FilterOptionsTest` | F2, F8 | bounds come from the data; options don't shrink while filtering; a selected program missing after a refresh is dropped |
| `OrderingTest` | O1–O3 | date, then points, then id tiebreak; marker sheet ordered by points; saved ordering |
| `ActiveFiltersTest` | C1–C4 | defaults → no chips; each filter → one chip in a fixed order; clearing one chip; resetAll restores every default; `hasAnythingToReset` is true for search text |
| `MapAggregatorTest` | M1–M6 | groups by destination; count = number of flights; `minPoints` correct; marker counts sum to `resultCount`; empty input → no markers; NRT and HND are separate markers; coordinates taken from the data |
| `GreatCircleTest` | M7 | endpoints exact; returns N+1 points; JFK→HKG and SEA→SYD have no consecutive jump > 180° after unwrapping; the same point twice is handled |
| `PointsFormatTest` | T1, T2 | `9000 → 9k`, `25000 → 25k`, `101500 → 101.5k`, `950 → 950`, `25000 → "25,000 pts"` |
| `FlightsApiTest` | R3–R5, R9, R12 | 200 + valid → flights; 200 + `[]` → success with empty list; 503 → `Server(503)`; malformed JSON → `InvalidData`; missing field → `InvalidData`; bad date → `InvalidData`; timeout → `Timeout`; connection failure → `Offline`; cancellation is rethrown, not mapped |
| `FlightCacheTest` | R3, R8 | replace is atomic (a failure in the middle of the insert leaves the old cache intact); `lastSuccessAt` is set only on success; never synced vs synced empty; **data survives closing and reopening the DB file** |
| `SavedFlightsTest` | V1, V2, V4–V6 | save → one row; **save again with changed points → still one row, new points, original `savedAt`**; remove; saved survives a cache replace that drops its id; V5 updates details on refresh; data survives reopen |
| `ExplorerStoreOfflineTest` | R1, R2, R4–R7, R11 | cached data appears before the network responds; success clears the stale state; failure with cache → `Ready` + `refreshError`, data unchanged; failure without cache → `Unavailable(kind)`; success-empty → `Ready` + empty cache (not an error); concurrent `refresh()` calls → one API call |
| `ExplorerStoreSelectionTest` | L1–L5, L7 | select → `selected` set; a filter that excludes it → cleared; undoing the filter → still cleared; reset → a selection still in the results is kept; the saved-view selection is independent; removing a saved flight clears the saved selection |
| `ExplorerStoreSavedTest` | V3, V7 | save → `isSaved` true in both results and markers; **save failure → `isSaved` unchanged + `UserMessage`**; remove failure, likewise; `pendingWriteIds` is set during the write and cleared after |
| `ExplorerStoreLatestWinsTest` | PERF-3 | type "b", "bo", "bos" rapidly with a slow reducer → the only final results are for "bos", and no intermediate state from an older query arrives after a newer one |
| `FixtureContractTest` (jvmTest) | §0 facts | 1,000 flights; 32 airports; 60 dates; three cabins; five programs; ids unique; six ids not in the `fx-` format |

Target: every rule ID in DOMAIN_RULES is referenced by at least one test name. Check with:

```bash
grep -rhoE '\b[a-z]{1,2}[0-9]+_' shared/src/*Test | sort -u
```

## 3. Running tests

```bash
./gradlew :shared:jvmTest
```
```bash
./gradlew :shared:iosSimulatorArm64Test
```
```bash
./gradlew :shared:allTests
```

The second and third commands need a Mac. `allTests` runs every target that is available on the host.

## 4. Performance & leak checks

| Check | Android | iOS |
|---|---|---|
| Main-thread IO | `StrictMode` thread policy in debug builds (disk + network, `penaltyLog`): no violations during the demo script | Xcode Thread Performance Checker on, with no purple warnings |
| Scroll smoothness | Profile with the GPU rendering overlay or JankStats while flinging through 1,000 rows | Instruments › Animation Hitches while scrolling |
| Rapid input | Type and delete quickly in both fields while on the Map; there should be no ANR or stutter | Same |
| Leaks | LeakCanary installed in debug; rotate, switch tabs and open/close sheets 10× → no leak reports | Debug Memory Graph after the same steps → no leaked `ExplorerModel` or cycles; Instruments › Leaks clean |
| Memory | Android Studio Profiler: heap stable after repeated filter changes | Instruments › Allocations: no unbounded growth |

### 4.1 Scalability benchmarks (M6)

These measure the SCALE-* targets in REQUIREMENTS §11. Record the medians in SUBMISSION_NOTES §6.

- **Synthetic data:** a seeded generator in `jvmTest` (`SyntheticData.flights(count, airports, seed)`) that keeps the fixture's shape: random routes, dates, cabins, programs and points.
- **Cases**, each with warm-up runs and the median of 20 runs:

  | Case | Sizes | Target |
  |---|---|---|
  | `reduce()` (search → filter → sort → aggregate) | 1k / 10k / 100k flights | < 16 ms at 10k |
  | Airport search per keystroke | 32 / 1,000 / 5,000 airports | < 1 ms at 5k |
  | SQLite cache replace (JVM driver) | 10k / 100k rows | < 2 s at 100k |
  | Map aggregation | 100k flights | < 16 ms |
  | Memory: dataset + indexes | 10k / 100k flights | Recorded, not gated |
- **Tooling:** plain timed loops are enough. `kotlinx-benchmark` is optional. JVM numbers are a proxy for devices; spot-check the 10k case on a real phone.

## 5. Manual QA checklist

Fill this in as you go: date, ✅/❌, and a short note. This table *is* the record of "checks actually performed" required by TEST-6.

| # | Check | Android | iOS |
|---|---|---|---|
| P1 | The starter app builds and shows "Hello world!" | | |
| P2 | `./gradlew :server:run` → `curl` returns 200 and the body matches the fixture | | — |
| Q1 | Fresh install with the API running → loading state → 1,000 flights, count shown, date chip shows Feb 1 – Apr 1 | | |
| Q2 | From `"  BoStOn "` → BOS only. To `tokyo` → HND and NRT. To `nrt` → NRT only. | | |
| Q3 | Date range Feb 10 – Feb 12: flights on the 10th and the 12th are present, the 9th and the 13th are absent | | |
| Q4 | Max points at a value equal to some flight's points → that flight is included | | |
| Q5 | Programs {Avios, Aeroplan} → only those two programs; plus Business → AND | | |
| Q6 | Active chips are shown; clearing one chip works; Reset restores everything | | |
| Q7 | Filtered-empty state + Reset button | | |
| Q8 | Map: one marker per destination; count and min points match the list; pan, zoom and double-tap all work | | |
| Q9 | Tap a marker → sheet ordered by points → tap a flight → route line + card | | |
| Q10 | Switch Map → List: the same flight is selected and highlighted; the reverse also works | | |
| Q11 | Apply a filter that excludes the selected flight → the card disappears; undo the filter → it stays cleared | | |
| Q12 | Save from the list → the heart fills on the map card and in the Saved tab; the badge count updates | | |
| Q13 | Saved tab with filters that exclude the saved flights → the saved flights are still listed | | |
| Q14 | Edit the fixture (change the points of a saved flight), refresh → the saved details are updated, with no duplicate | | |
| Q15 | Remove a saved flight's record from the fixture, refresh → it is still in Saved with "No longer listed" | | |
| Q16 | Stop the API, pull to refresh → stale banner, data intact, Retry shown | | |
| Q17 | API stopped: force-quit, relaunch → cached results + saved flights; search and filters work; remove a saved flight | | |
| Q18 | Clear app data (or reinstall) with the API stopped → full-screen offline error; start the API → Try again → loads | | |
| Q19 | `--mode=empty` → "No flights available" (not an error); `--mode=error` → stale banner or error | | |
| Q20 | Simulated save failure (debug toggle or test build) → snackbar, heart unchanged | | |
| Q21 | Rotation, or the app in the background for a minute → state preserved (Android) | | — |
| Q22 | Largest font size + VoiceOver/TalkBack pass over a row, a marker and the card | | |
| Q23 | Dark mode pass | | |
| Q24 | §4 performance and leak checks done | | |

## 6. Failure-injection hooks (debug builds only)

To demo and QA failures without code changes:

- Server: `--mode=empty|error|slow` (ARCHITECTURE §6.2).
- App: a hidden debug toggle, "Fail next DB write", that makes `SavedFlightsRepository` throw once. This covers Q20 and OFF-8. Strip it from release builds or keep it behind `BuildConfig.DEBUG` / `#if DEBUG`.

## 7. Demo recording script (DEL-4)

Aim for about 4 minutes per platform. Record Android first, then repeat on iOS.

1. **Setup shot:** a terminal running `./gradlew :server:run`, with `curl -s localhost:8080/flights | head` showing the data.
2. **Launch:** a fresh install shows the loading state, then 1,000 flights. Point out the count and the default date range.
3. **Search:** type `" boston "` in From → BOS. Type `tokyo` in To → HND and NRT. Clear both.
4. **Filters:** dates Feb 10 – 12 (call out the inclusive ends), cabin Business, points up to 60k, programs Avios + Aeroplan → show the chips and the count. Clear one chip, then Reset.
5. **Map:** pan and zoom. Tap the Paris marker → sheet → pick a flight → route line + card. Switch to List → the same selection. Apply a filter that removes it → the selection clears.
6. **Save:** save 2–3 flights (from the list and from the map card). Open the Saved tab, change the filters to show it's independent, and remove one.
7. **Offline:** Ctrl‑C the API. Pull to refresh → stale banner, data intact.
8. **Reopen with the API stopped:** force-quit and relaunch → cached results and saved flights are present. Search still works, and removing a saved flight works.
9. *(Optional)* Start the API again → Retry → the banner clears. Briefly show `--mode=empty`.
