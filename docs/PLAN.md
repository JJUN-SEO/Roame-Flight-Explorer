# Delivery plan

Build vertically: get the shared core right and tested first, then Android end to end, then iOS.

## Working agreement

Every unit of work below follows the owner's review gate (see [CLAUDE.md](../CLAUDE.md#how-we-work-owners-rules--follow-every-time)):

1. Implement **one** functionality end to end: code, tests and docs.
2. Run the checks, then **ask the owner to review**.
3. Commit locally. **Push only after the owner accepts.**
4. Ask the owner first about any significant architectural decision, and record it in SUBMISSION_NOTES §2.

## Milestones

### M0 · Pre-flight

Gate: nothing else starts until M0 is done.

- [ ] Build and run the starter on Android (emulator) and iOS (simulator). Record the result in TEST_PLAN P1 (DEL-6).
- [ ] Create `gradle/libs.versions.toml` and pick library versions compatible with Kotlin 2.4.21, AGP 9.3.1 and Gradle 9.7.
- [ ] **Spike (do this first):** add Room KMP + KSP (for all 5 targets) + the bundled SQLite driver, plus Ktor + serialization, to `:shared`. Confirm that `./gradlew :shared:jvmTest` runs with an in-memory Room database and that the iOS framework still builds. If Room or KSP doesn't support Kotlin 2.4.21 / AGP 9.3.1, **bring the options back to the owner** (SQLDelight is the fallback for D3).
- [x] Owner decision D8: **MapLibre** for the Android map.
- [ ] **Spike (time-boxed, can run in parallel with M4):** a MapLibre map in Compose with one text marker and one line. If it doesn't work out, bring the options back to the owner before switching (rule 2).

### M1 · Local API → API-1, API-2

- [ ] Add the `:server` module: Ktor, `GET /flights` serving the raw fixture bytes, port 8080, run working directory = repo root.
- [ ] Add `--mode=normal|empty|error|slow` and `--fixture=`.
- [ ] Verify: `curl -s localhost:8080/flights | diff - fixtures/flights.json`.
- [ ] Update the README Quick start.

### M2 · Shared foundation → DATA-1, DATA-2, DATA-4, DATA-5

- [ ] Models + DTOs + mapping (DM1–DM3). Add `FixtureContractTest`.
- [ ] `FlightsApi` + `FetchError` mapping + `FlightsApiTest`, using `MockEngine`.
- [ ] Room entities, DAOs and `AppDatabase` (+ `AppDatabaseConstructor`), with database builders for Android, iOS and the JVM (ARCHITECTURE §5).
- [ ] `FlightRepository` (cache flow, atomic refresh, sync status, coalescing) + `FlightCacheTest`.
- [ ] `SavedFlightsRepository` (snapshots, upsert, remove, V5) + `SavedFlightsTest`.

### M3 · Shared logic + store → SRCH-*, FILT-*, MAP-1/2, SEL-*, OFF-*, PERF-3

- [ ] Pure domain: `FlightSearch`, `FlightFilter`, `Ordering`, `ActiveFilters`, `MapAggregator`, `GreatCircle`, `PointsFormat`, each with table-driven tests.
- [ ] `ExplorerStore` pipeline (`combine` → `mapLatest(reduce)` → `stateIn`), intents, selection clearing, `UserMessage`s.
- [ ] Store tests: offline, selection, saved, latest-wins.
- [ ] `AppGraph` + the iOS bridge `ExplorerStoreIos`.
- [ ] ✅ Checkpoint: `jvmTest` green, and every DOMAIN_RULES ID is covered.

### M3a · Search engine (shared) → SRCH-4…SRCH-7, SCALE-1

Requested by the owner. Implemented ahead of the rest of M3, under review.

- [x] Normalisation with accent folding (S1); prefix tree with O(m) traversal and completions (S4).
- [x] Tiered fuzzy fallback: typo distance, Double Metaphone and trigram n-grams (S6–S10).
- [x] Vendored Double Metaphone port with license and credit, verified against commons-codec (S8).
- [x] Ranking by proximity + volume, with the `flights.json` fallback when `airports.json` is missing (P1–P6).
- [x] `fixtures/airports.json` (separate file; `flights.json` untouched).
- [x] Tests: 54 shared tests passing on the JVM; the iOS klib compiles.
- [ ] **Owner review** → push.
- [x] Owner decisions: location comes from the device (D16); ranking orders matched airports only, never the flight list (D17).
- [ ] *Later:* `GET /airports` in M1, plus loading and caching in M2. Wire the engine into `ExplorerStore`, the hint UI (S13) and the suggestions dropdown (S14) in M3–M5. Add device location in M4 and M5.

### M4 · Android app

- [ ] Platform setup: INTERNET permission, network security config, `BuildConfig.API_BASE_URL`, `RoameApp`, StrictMode, LeakCanary.
- [ ] Theme + tokens; tab shell (Explore, Saved).
- [ ] Search header with suggestions (S14), filter chips + sheets, summary + Reset.
- [ ] List with rows, selection, pull-to-refresh, every state from UI_SPEC §4.
- [ ] Map: markers, marker sheet, route line, camera rules.
- [ ] Selection card + Save/Remove with pending and error states; snackbar.
- [ ] Saved tab.
- [ ] "Use my location" (⌖): Fused Location Provider, `ACCESS_COARSE_LOCATION`, one balanced-power fix → store (D16).
- [ ] Run QA Q1–Q24 on Android.

### M5 · iOS app

- [ ] Platform setup: ATS local networking, `API_BASE_URL` in Info.plist, `ExplorerModel` bridge.
- [ ] The same feature list as M4, in SwiftUI with MapKit, including "Use my location" with Core Location and `NSLocationWhenInUseUsageDescription` (D16).
- [ ] Run QA Q1–Q24 on iOS.

### M6 · Hardening, scale & polish

- [ ] Performance and leak checks (TEST_PLAN §4) on both platforms.
- [ ] Scalability benchmarks with synthetic data (TEST_PLAN §4.1). Record the numbers in SUBMISSION_NOTES §6 and update the SCALE-* statuses.
- [ ] Accessibility pass and dark mode.
- [ ] Visual consistency pass: put the two apps side by side and compare spacing, typography and copy.
- [ ] Re-read REQUIREMENTS.md and set every status to ✅, or explain the gap in Known limitations.

### M7 · Submission

- [ ] Finalise the README: build, run and test instructions, verified from a fresh clone.
- [ ] Finish SUBMISSION_NOTES.md: approach, decisions, assumptions, tradeoffs, limitations, scalability, AI usage.
- [ ] Record the demo (TEST_PLAN §7) for both apps.
- [ ] Final `git status` is clean, then tag or zip.

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Library versions incompatible with Kotlin 2.4.21 / AGP 9.3.1 (both very new) | Medium | High | M0 spike first; pin versions in the catalog; take any fallback back to the owner |
| Room / KSP don't yet support Kotlin 2.4.21 or AGP 9's new KMP library plugin | Medium | High | M0 spike first. If it fails, present the options (wait for a release, or SQLDelight) to the owner |
| MapLibre in Compose takes too long (custom pill markers) | Medium | Medium | Time-boxed spike. If it fails, ask the owner before falling back to Google Maps Compose (needs a key) |
| Swift 6 strict concurrency vs Kotlin callbacks | Medium | Low | Main-thread delivery + `MainActor.assumeIsolated`; keep the bridge tiny |
| Emulator or simulator can't reach the API (cleartext, wrong host) | High (if forgotten) | Medium | Platform setup checklists in M4 and M5; ARCHITECTURE §6.4 |
| Designs that work at 1,000 flights but not at 100k | Medium | Medium | Scalability rows (REQUIREMENTS §11), ARCHITECTURE §12 scaling paths, synthetic benchmarks in M6 |
| Scope creep beyond the brief | Medium | Medium | Review gate per functionality; the priority tiers below |

## Priority tiers

**Must:** every REQUIREMENTS row (§1–§10), shared tests for TEST-1 to TEST-5, QA on both platforms, README, submission notes, recording.

**Should** (drop in this order if needed):

1. The `--mode` server switch. Instead, demo empty and error states by editing the fixture or stopping the server.
2. The "Fail next DB write" debug toggle. The store tests already cover V7.
3. Animating the camera to fit the route on selection.
4. The live "Show N flights" count inside filter sheets.

**Could:**

- Sort options (price vs date).
- A few Compose UI tests or XCUITests for the selection flow.
- A GitHub Actions workflow running `:shared:jvmTest`.
- Persisting the last-used filters across launches.
- Building the "Documented" scalability paths (REQUIREMENTS §11).
