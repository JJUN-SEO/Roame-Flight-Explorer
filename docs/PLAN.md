# Delivery plan

**Time box:** two days, roughly 16–18 focused hours. Build vertically: get the shared core right and tested first, then Android end to end, then iOS. Each milestone ends in a working, committed state.

## Milestones

### M0 · Pre-flight (≈1 h)

Gate: nothing else starts until M0 is done.

- [ ] Build and run the starter on Android (emulator) and iOS (simulator). Record the result in TEST_PLAN P1 (DEL-6).
- [ ] Create `gradle/libs.versions.toml` and pick library versions compatible with Kotlin 2.4.21, AGP 9.3.1 and Gradle 9.7.
- [ ] **Spike (30 min):** add SQLDelight + Ktor + serialization to `:shared` and add the `jvm()` target. Confirm that `./gradlew :shared:jvmTest` runs and that the iOS framework still builds. If SQLDelight fights the AGP 9 KMP plugin, switch to Room KMP (D3).
- [ ] **Spike (≤1.5 h, can run in parallel with M4):** a MapLibre map in Compose with one text marker and one line. If it isn't working within the time box, switch to Google Maps Compose and document the API key setup (D8).

### M1 · Local API (≈45 min) → API-1, API-2

- [ ] Add the `:server` module: Ktor, `GET /flights` serving the raw fixture bytes, port 8080, run working directory = repo root.
- [ ] Add `--mode=normal|empty|error|slow` and `--fixture=`.
- [ ] Verify: `curl -s localhost:8080/flights | diff - fixtures/flights.json`.
- [ ] Update the README Quick start.

### M2 · Shared foundation (≈3 h) → DATA-1, DATA-2, DATA-4, DATA-5

- [ ] Models + DTOs + mapping (DM1–DM3). Add `FixtureContractTest`.
- [ ] `FlightsApi` + `FetchError` mapping + `FlightsApiTest`, using `MockEngine`.
- [ ] SQLDelight schema, DAOs, `DriverFactory` actuals (Android, iOS, JVM).
- [ ] `FlightRepository` (cache flow, atomic refresh, sync status, coalescing) + `FlightCacheTest`.
- [ ] `SavedFlightsRepository` (snapshots, upsert, remove, V5) + `SavedFlightsTest`.

### M3 · Shared logic + store (≈3 h) → SRCH-*, FILT-*, MAP-1/2, SEL-*, OFF-*, PERF-3

- [ ] Pure domain: `FlightSearch`, `FlightFilter`, `Ordering`, `ActiveFilters`, `MapAggregator`, `GreatCircle`, `PointsFormat`, each with table-driven tests.
- [ ] `ExplorerStore` pipeline (`combine` → `mapLatest(reduce)` → `stateIn`), intents, selection clearing, `UserMessage`s.
- [ ] Store tests: offline, selection, saved, latest-wins.
- [ ] `AppGraph` + the iOS bridge `ExplorerStoreIos`.
- [ ] ✅ Checkpoint: `jvmTest` green, and every DOMAIN_RULES ID is covered.

### M4 · Android app (≈4 h)

- [ ] Platform setup: INTERNET permission, network security config, `BuildConfig.API_BASE_URL`, `RoameApp`, StrictMode, LeakCanary.
- [ ] Theme + tokens; tab shell (Explore, Saved).
- [ ] Search header, filter chips + sheets, summary + Reset.
- [ ] List with rows, selection, pull-to-refresh, every state from UI_SPEC §4.
- [ ] Map: markers, marker sheet, route line, camera rules.
- [ ] Selection card + Save/Remove with pending and error states; snackbar.
- [ ] Saved tab.
- [ ] Run QA Q1–Q24 on Android.

### M5 · iOS app (≈4 h)

- [ ] Platform setup: ATS local networking, `API_BASE_URL` in Info.plist, `-lsqlite3`, `ExplorerModel` bridge.
- [ ] The same feature list as M4, in SwiftUI with MapKit.
- [ ] Run QA Q1–Q24 on iOS.

### M6 · Hardening & polish (≈2 h)

- [ ] Performance and leak checks (TEST_PLAN §4) on both platforms.
- [ ] Accessibility pass and dark mode.
- [ ] Visual consistency pass: put the two apps side by side and compare spacing, typography and copy.
- [ ] Re-read REQUIREMENTS.md and set every status to ✅, or explain the gap in Known limitations.

### M7 · Submission (≈1.5 h)

- [ ] Finalise the README: build, run and test instructions, verified from a fresh clone.
- [ ] Finish SUBMISSION_NOTES.md: approach, decisions, assumptions, tradeoffs, limitations, AI usage.
- [ ] Record the demo (TEST_PLAN §7) for both apps.
- [ ] Final `git status` is clean, then tag or zip.

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Library versions incompatible with Kotlin 2.4.21 / AGP 9.3.1 (both very new) | Medium | High | M0 spike first; fall back to Room KMP; pin versions in the catalog |
| SQLDelight + the new `com.android.kotlin.multiplatform.library` plugin | Medium | Medium | Same spike; fall back to Room KMP |
| MapLibre in Compose takes too long (custom pill markers) | Medium | Medium | Time-boxed spike; fall back to Google Maps Compose (needs a key) |
| Swift 6 strict concurrency vs Kotlin callbacks | Medium | Low | Main-thread delivery + `MainActor.assumeIsolated`; keep the bridge tiny |
| Static framework missing `sqlite3` at link time | High (if forgotten) | Low | Add `-lsqlite3` to `OTHER_LDFLAGS` during iOS setup |
| Emulator or simulator can't reach the API (cleartext, wrong host) | High (if forgotten) | Medium | Platform setup checklists in M4 and M5; ARCHITECTURE §6.4 |
| Running out of time | Medium | High | Cut lines below; shared core and Android first, so iOS reuses everything |

## Cut lines

**Must** (do not submit without these): every REQUIREMENTS row; shared tests for TEST-1 to TEST-5; QA on both platforms; README; submission notes; recording.

**Should** (cut in this order if you're behind):

1. The `--mode` server switch. Instead, demo empty and error states by editing the fixture or stopping the server.
2. The "Fail next DB write" debug toggle. The store tests already cover V7.
3. Animating the camera to fit the route on selection.
4. The live "Show N flights" count inside filter sheets.

**Could** (only if time remains):

- Accent folding in search (S6).
- Sort options (price vs date).
- A few Compose UI tests or XCUITests for the selection flow.
- A GitHub Actions workflow running `:shared:jvmTest`.
- Persisting the last-used filters across launches.
