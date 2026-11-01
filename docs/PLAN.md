# Build plan

> **Status: waiting for the owner's approval. Don't start Step 0.1 until the owner approves this plan.**
>
> **Current step:** none yet. Update this line at every review gate.

This plan turns the decided design (D1–D20) into small steps, in build order. A new session with no memory of earlier conversations should be able to continue from this file alone.

This file says what to do next. The other docs say how things must behave: [REQUIREMENTS](REQUIREMENTS.md), [DOMAIN_RULES](DOMAIN_RULES.md), [ARCHITECTURE](ARCHITECTURE.md), [UI_SPEC](UI_SPEC.md) and [TEST_PLAN](TEST_PLAN.md). If this plan and those docs disagree, stop and ask the owner.

**Names.** Work is split into Phases and Steps, such as Step 2.2. `M1`–`M8` always means the map rules in DOMAIN_RULES. `Q1`–`Q24` and `P1`–`P2` in a "QA" line mean the rows in TEST_PLAN §5; everywhere else, `P1`–`P7` means the ranking rules.

## 1. Starting a session

1. Read `CLAUDE.md`, then this file.
2. Run `git status` and `git log --oneline -5`.
3. Read **Current step** above. If a step is committed and waiting for review, wait for the owner. Don't start the next one.
4. Otherwise, start the first unchecked step in §8.

## 2. How every step runs

1. **Read** the docs the step lists.
2. **Write the test first** for anything with a rule ID, and watch it fail. Name it after the rule: `f4_pointsEqualToMax_isIncluded`.
3. **Build only the step's scope.** Don't add screens, features or libraries from later steps.
4. **Run the checks** in §4 and read the results. If something fails, report it with the output.
5. **Update the docs in the same commit:**
   - the status in REQUIREMENTS, and the rows run in TEST_PLAN §5,
   - DOMAIN_RULES and its test, if a rule changed,
   - the AI usage log in SUBMISSION_NOTES, for notable work,
   - the Scalability sections, if a cost changed,
   - the **Current step** line in this file.
6. **Commit locally** as `area: summary`, with the requirement IDs when useful.
7. **Stop and ask for review** with the message below. Don't start the next step.
8. **Push only after the owner accepts.** Then tick the step's checkbox.

The brief says the owner must understand and be able to modify every line submitted, so each review message explains the code as well as listing it:

```
Step 2.2: Flight cache
What it does: in plain language, with a simple analogy if that helps.
What changed: the main files, and where to start reading.
Checked: the commands run and their results.
Not checked: anything skipped, and why.
Try it: one or two commands or taps.
Decisions for you: anything needed, or "none".
```

## 3. When to stop and ask

Stop and ask before any of these, with the options, tradeoffs and a recommendation:

- a module, endpoint, library, data source or permission not named in this plan or ARCHITECTURE §10,
- a change to the data model or shared API beyond §6 of this plan,
- behaviour that differs between platforms,
- sending data to a third party,
- using a fallback: SQLDelight instead of Room, or Google Maps instead of MapLibre,
- cutting anything from scope (§10),
- finding that any of D1–D20 doesn't work in practice.

For a small ambiguity, don't stop. Choose a reasonable behaviour, write it as an *(assumption)* rule in DOMAIN_RULES, add a row to SUBMISSION_NOTES §3, and continue.

Record every notable decision in SUBMISSION_NOTES §2 and ARCHITECTURE §11, including who made it and why.

## 4. Standard checks

If `./gradlew` says "Unable to locate a Java Runtime", first run:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

| Name | Command |
|---|---|
| Shared tests, JVM | `./gradlew :shared:jvmTest` |
| Shared tests, iOS | `./gradlew :shared:iosSimulatorArm64Test` |
| Android build | `./gradlew :androidApp:assembleDebug` |
| iOS build | `xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter -destination 'platform=iOS Simulator,name=<an installed iPhone>' build` |
| Rule coverage | `grep -rhoE '\b[a-z]{1,2}[0-9]+_' shared/src/*Test \| sort -u` |

| Phase | What to run |
|---|---|
| 0 and 1 | The checks listed in the step |
| 2 and 3 | Both shared test commands. Both app builds as well, since `shared/` changed |
| 4 | Shared tests on the JVM and the Android build. The iOS build too, if `shared/` changed |
| 5 | Shared tests on the JVM and the iOS build. The Android build too, if `shared/` changed |
| 6 and 7 | All of them |

Every step also meets the "Before you say done" list in `CLAUDE.md`.

**QA records must be real.** TEST_PLAN §5 only records checks that were actually performed, with the date and who ran them. Leave a row blank and ask the owner if it can't be observed from this session.

## 5. Who does what

| Who | Does |
|---|---|
| Owner | Approves this plan, reviews each step, accepts pushes, makes decisions, installs the Android SDK (Step 0.1), confirms QA on real interactions, and records the demo (Step 7.2) |
| Agent | Everything else, stopping at each review gate |

## 6. Additions to approve with this plan

These are small gaps found while writing the plan. Nothing here is built yet. Update ARCHITECTURE in the step that builds each one.

| Addition | Why it's needed | Step |
|---|---|---|
| An `airport_stat` table (`code`, `annualPassengers`) | D15 says to cache the passenger figures like flights, but ARCHITECTURE §5.1 has no table for them. Without one, ranking falls back to flight counts whenever the API is off. | 2.2 |
| `ExplorerState.originHint` and `destinationHint` (lists of `AirportSuggestion`, empty unless the tier is `Fuzzy`) | ARCHITECTURE §4.1 has nothing the apps can use to draw the S13 hint. | 3.5 |
| `ExplorerState.originPick` and `destinationPick` | The apps must show "Tokyo (HND)" while a pick is active (S14). | 3.5 |
| `ExplorerState.isLocationShared` and the intent `setUserLocation(latitude, longitude)` | ARCHITECTURE §4.2 has no way to hand the device location to the store, and the ⌖ button must show when location is on. | 3.5 |

## 7. Overview

| Phase | Steps | Result at the end |
|---|---|---|
| 0 Pre-flight | 0.1–0.3 | This Mac builds both starter apps, and the two risky libraries are proven |
| 1 Local API | 1.1–1.2 | One command serves the fixture |
| 2 Shared data | 2.1–2.3 | Fetching, caching and saved flights work and are tested |
| 3 Shared logic and store | 3.1–3.6 | Every data rule is tested, and one store is reachable from Kotlin and Swift |
| 4 Android app | 4.1–4.8 | A complete Android app, checked against the QA list |
| 5 iOS app | 5.1–5.8 | A complete iOS app, checked against the QA list |
| 6 Hardening | 6.1–6.4 | Performance, leaks, scale, accessibility and polish are checked, and every requirement is audited |
| 7 Submission | 7.1–7.3 | README, notes and recording are done, and the project is handed in |

**Already built and accepted (2026‑11‑01):** the search engine in `shared/.../domain/search/`, with 57 shared tests passing on the JVM and 52 on the iOS simulator (the other 5 are JVM-only). It covers S1–S12, P1–P6, SRCH-4, SRCH-5 and SRCH-6. Nothing uses it yet; Step 3.5 connects it to the store.

## 8. Steps

### Phase 0: Pre-flight

Nothing else starts until this phase is done. The brief says both starter apps must build first.

#### [ ] 0.1 Set up this Mac and build both starter apps

- **Goal:** both starter apps run here. On 2026‑11‑01, no Android SDK could be found on this Mac.
- **Covers:** DEL-6. QA: P1.
- **Owner does:** opens Android Studio once and finishes its setup, installs Android SDK 37.0 and build-tools 37.0.0, and creates an emulator using a **Google APIs or Google Play** image, which D16 needs.
- **Agent does:** points Gradle at the SDK with `local.properties` (already gitignored), then runs the Android build and the iOS build.
- **Done when:** both apps show "Hello world!" and QA row P1 is filled in.

#### [ ] 0.2 Prove the shared libraries work together

- **Goal:** find out now whether Room and KSP support Kotlin 2.4.21 and AGP 9.3.1. This is the project's biggest technical risk.
- **Covers:** the D3 compatibility check.
- **Read:** ARCHITECTURE §5.3, §5.4 and §10.
- **Build:**
  - Add coroutines, serialization, kotlinx-datetime, Ktor client, Room, KSP, `sqlite-bundled` and Turbine to `gradle/libs.versions.toml`. Read each library's official docs for compatible versions, rather than choosing from memory.
  - Register `room-compiler` with KSP for all 5 targets.
  - Write a throwaway entity and DAO, with a test that saves and reads back a row.
- **Done when:** the round-trip test passes on the JVM and the iOS simulator, and both app builds still pass. Then delete the throwaway code and keep the build setup.
- **If it fails:** stop and bring the options to the owner. SQLDelight is the fallback for D3.

#### [ ] 0.3 Prove MapLibre works in Compose

- **Goal:** find out whether D8 works before any map code depends on it.
- **Covers:** the D8 check.
- **Read:** ARCHITECTURE §10 and UI_SPEC §9.
- **Build:** on a scratch branch that is never merged, show one text marker and one line over OpenFreeMap tiles on the emulator. Limit this to one working session.
- **Done when:** both draw correctly and pan and zoom work. Record the result in SUBMISSION_NOTES §7.
- **If it fails:** stop and bring the options to the owner. Google Maps needs an API key, and only the owner can approve it.

### Phase 1: Local API

#### [ ] 1.1 Serve the fixture

- **Goal:** one command runs the API.
- **Covers:** API-1, API-2, D1. QA: P2.
- **Read:** ARCHITECTURE §6.1 and §6.2.
- **Build:**
  - A `:server` module with Ktor and `tasks.run { workingDir = rootDir }`, listening on port 8080 or `PORT`.
  - `GET /flights` returns the raw bytes of `fixtures/flights.json`, reading the file on every request. If the file is missing, fail at startup.
  - `GET /airports` serves `fixtures/airports.json` the same way, and returns 404 if it's missing (D15).
  - In the README and `CLAUDE.md`, remove the † marks and add the `JAVA_HOME` note from §4.
- **Done when:** `curl -i localhost:8080/flights` returns 200 with `application/json`, and `curl -s localhost:8080/flights | diff - fixtures/flights.json` shows no differences.

#### [ ] 1.2 Add QA switches

- **Goal:** empty, error and slow responses can be shown on demand. This is the first thing to drop if time runs short (§10).
- **Covers:** the server half of OFF-5 and TEST_PLAN §6.
- **Build:** `--mode=normal|empty|error|slow` and `--fixture=<path>`.
- **Done when:** each mode returns what ARCHITECTURE §6.2 says when checked with `curl`.

### Phase 2: Shared data

Tests use `MockEngine` and an in-memory or temp-file database. Package layout: ARCHITECTURE §3.

#### [ ] 2.1 Decode and fetch

- **Goal:** the shared code can fetch flights and classify every way that can fail.
- **Covers:** DM1–DM3, R3, R4, R5, R9, R10, R12, DATA-1, TEST-5.
- **Read:** DOMAIN_RULES §0, §1 and §9; ARCHITECTURE §6.3.
- **Build:**
  - `Airport`, `Flight`, the DTOs and their mapping.
  - `FlightsApi` and `FetchError`, with `expect fun Throwable.isConnectivityFailure()`.
  - A small client for `GET /airports`. Any failure returns empty figures and never counts as a refresh failure (P4).
- **Tests:** `FlightsApiTest`, and the §0 facts added to `FixtureContractTest`.
- **Done when:** the real fixture decodes to 1,000 flights, and each failure row in ARCHITECTURE §6.3 has a passing test.

#### [ ] 2.2 Flight cache

- **Goal:** flights survive closing the app, and a failed refresh never loses them.
- **Covers:** R1, R2, R3, R4, R5, R8, P3, P4, DATA-2, DATA-4, DATA-6, TEST-4, D3, D5.
- **Read:** ARCHITECTURE §5; DOMAIN_RULES §9.
- **Build:**
  - The entities, including `airport_stat` (§6), plus `FlightCacheDao`, `AppDatabase` and the database builders for Android, iOS and the JVM.
  - `FlightRepository`: the cache flow, sync status, an atomic refresh, and one refresh at a time.
- **Tests:** `FlightCacheTest`.
- **Done when:** a failure halfway through an insert leaves the old cache intact, data survives reopening the file, and "never synced" differs from "synced empty". Room's schema is exported to `shared/schemas/`.

#### [ ] 2.3 Saved flights

- **Goal:** saved flights live independently of the cache and never duplicate.
- **Covers:** V1, V2, V4, V5, V6, DATA-5, OFF-6, OFF-7, TEST-4, D4.
- **Read:** ARCHITECTURE §5.2; DOMAIN_RULES §8.
- **Build:** `SavedFlightDao` and `SavedFlightsRepository`. Write failures come back as a `Result`, ready for V7.
- **Tests:** `SavedFlightsTest`.
- **Done when:** saving again leaves one row with new details and the original `savedAt`, and a saved flight survives a refresh that drops it.

### Phase 3: Shared logic and store

The apps stay untouched until Step 3.6. Pure rules come first, because they need no coroutines to test.

#### [ ] 3.1 Filters, ordering and active filters

- **Goal:** every filter rule exists as a pure function with its boundaries tested.
- **Covers:** F1–F8, O1–O3, C1–C4, DM1, FILT-1–FILT-10, DATA-3, TEST-1, TEST-2.
- **Read:** DOMAIN_RULES §3, §4 and §5.
- **Build:** `FlightFilter`, `FilterOptions`, `Ordering` and `ActiveFilters`. Date bounds, cabins, programs and points come from the data.
- **Tests:** `DateFilterTest`, `PointsFilterTest`, `CabinFilterTest`, `ProgramFilterTest`, `CombinedFilterTest`, `FilterOptionsTest`, `OrderingTest` and `ActiveFiltersTest`.
- **Done when:** a flight on either date boundary and a flight at exactly the points limit are included, and "Mileage Plan" stays separate from "MileagePlus".

#### [ ] 3.2 Map data and formatting

- **Goal:** marker grouping and route points exist before any map does.
- **Covers:** M1, M2, M3, M4, M5, M6, M7, T1, T2, MAP-1, MAP-2, TEST-3.
- **Read:** DOMAIN_RULES §6 and §10.
- **Build:** `MapAggregator`, `GreatCircle` and `PointsFormat`.
- **Tests:** `MapAggregatorTest`, `GreatCircleTest` and `PointsFormatTest`.
- **Done when:** marker counts add up to the result count, HND and NRT are separate markers, and JFK→HKG has no jump over 180° after unwrapping.

#### [ ] 3.3 The store and its offline states

- **Goal:** one app-wide store turns data and inputs into `ExplorerState`, with the newest input always winning.
- **Covers:** R1, R2, R4, R5, R6, R7, R11, R12, PERF-3, OFF-1, OFF-2, OFF-3, OFF-4, OFF-5, TEST-5, D2, D6, D10.
- **Read:** ARCHITECTURE §4 and §7.
- **Build:** `ExplorerStore`, `ExplorerState`, `DataStatus`, `reduce`, `index` (sorts once per data change) and `AppGraph`, with the filter intents and `refresh()`.
- **Tests:** `ExplorerStoreOfflineTest` and `ExplorerStoreLatestWinsTest`, using `FakeFlightsApi` and `TestClock`.
- **Done when:** cached data appears before the network responds, a failed refresh keeps the data and reports it, and rapid input only ever ends on the latest query.

#### [ ] 3.4 Selection and saved state in the store

- **Goal:** list and map can't disagree about what's selected or saved.
- **Covers:** L1, L2, L3, L4, L5, L7, V3, V4, V7, V8, VIEW-1, SEL-2, SEL-3, SAVE-1, OFF-8.
- **Read:** DOMAIN_RULES §7 and §8.
- **Build:** `select`, `selectSaved`, `toggleSave` and `removeSaved`, plus `pendingWriteIds`, `messages` and `isListed`.
- **Tests:** `ExplorerStoreSelectionTest` and `ExplorerStoreSavedTest`, using `FakeSavedFlightsRepository`.
- **Done when:** filtering out a selected flight clears it for good, and a failed save leaves `isSaved` unchanged and reports an error.

#### [ ] 3.5 Connect search to the store

- **Goal:** typing in either field filters flights and fills the suggestions, using the engine already built.
- **Covers:** S2, S3, S13, S14, P1, P2, P3, P4, P5, P6, P7, SRCH-1, SRCH-2, SRCH-3, SRCH-7, SRCH-8, SRCH-9, SCALE-1, D14, D15, D17, D18, D19, D20.
- **Read:** DOMAIN_RULES §2 and §2b; ARCHITECTURE §4.4.
- **Build:**
  - One engine per side, built when the cache emits. Ranking is rebuilt when the location changes.
  - `AirportSuggestion`, including the city row, plus `pickOrigin(codes)`, `pickDestination(codes)` and the §6 additions. Update ARCHITECTURE §4.
- **Tests:** `SearchSuggestionsTest`, plus store tests for both fields combined with filters.
- **Done when:** `tokyo` offers Tokyo (all airports), Tokyo (HND) and Tokyo (NRT); picking one Tokyo airport excludes the other; `nrt` shows no city row; and location never changes the destination field or the flight list.

#### [ ] 3.6 The Swift bridge

- **Goal:** prove SwiftUI can read the real store before building any screens.
- **Covers:** D7, DATA-3.
- **Read:** ARCHITECTURE §8.
- **Build:**
  - `ExplorerStoreIos` and `Cancellable`, with one plain method per intent.
  - A temporary line on the iOS starter screen that shows the live flight count from the running API. Step 5.1 replaces it.
- **Done when:** the simulator shows 1,000, and the rule coverage check in §4 lists a test for every rule the shared code owns. Rules that only exist on screen, such as M8 and T3, are checked in the UI steps instead.

### Phase 4: Android app

The apps only render `ExplorerState` and send intents. If a step needs an `if` about flight data in `androidApp/`, that logic belongs in `shared/`. Copy comes from UI_SPEC §4.

#### [ ] 4.1 App shell and live list

- **Goal:** the app shows all 1,000 flights from the API, and explains itself when it can't.
- **Covers:** R7, R9, T3, OFF-4, OFF-5, PERF-2, PERF-4, SCALE-6, UX-3. QA: Q1, Q18.
- **Read:** ARCHITECTURE §6.4 and §9; UI_SPEC §1, §2.1, §4 and §6.
- **Build:**
  - The INTERNET permission, the network security config, `BuildConfig.API_BASE_URL`, `RoameApp`, StrictMode and LeakCanary. Correct ARCHITECTURE §9, which still mentions `DriverFactory`.
  - The theme and tabs, `FlightRow` and `RouteConnector`, and a `LazyColumn` with stable ids.
  - First load, the unavailable errors with Retry, and synced empty.
- **Done when:** QA rows Q1 and Q18 pass, and StrictMode reports nothing on the main thread.

#### [ ] 4.2 Search

- **Goal:** the owner's "new, then delete, then sea" search works on screen.
- **Covers:** SRCH-1, SRCH-2, SRCH-3, SRCH-8, SRCH-9, S13, S14. QA: Q2, Q2b, Q2d.
- **Read:** UI_SPEC §3, §4 and §5.
- **Build:** `SearchField`, `SuggestionList` and the fuzzy hint.
- **Done when:** QA rows Q2, Q2b and Q2d pass.

#### [ ] 4.3 Filters

- **Goal:** all four filters work, with a visible count, chips and Reset.
- **Covers:** FILT-1–FILT-10, F2, F3, F4, C1, C2, C3, C4. QA: Q3, Q4, Q5, Q6, Q7.
- **Read:** UI_SPEC §2.4; the date picker note in `CLAUDE.md`.
- **Build:** `FilterChip`, the four sheets, `ResultSummary` and the filtered-empty state.
- **Done when:** those QA rows pass, and the date picker's UTC millis never shift a date.

#### [ ] 4.4 Selection, card and saved flights

- **Goal:** select, save and remove work, and failures never look like success.
- **Covers:** L2, L4, L6, L7, V3, V4, V7, V8, SEL-1, SEL-2, SEL-3, SAVE-1, OFF-6, OFF-7, OFF-8, UX-2. QA: Q11, Q12, Q13, Q20.
- **Read:** UI_SPEC §2.3 and §5; TEST_PLAN §6.
- **Build:** `FlightCard`, the pending and error states, snackbars, haptics, the Saved tab with its badge, and the debug-only "Fail next DB write" toggle.
- **Done when:** those QA rows pass.

#### [ ] 4.5 Map

- **Goal:** the same results as the list, as markers with a route line.
- **Covers:** M1, M2, M3, M4, M5, M6, M7, M8, T2, MAP-1, MAP-2, MAP-3, MAP-4, VIEW-1, SEL-1, OFF-9, D8. QA: Q8, Q9, Q10.
- **Read:** UI_SPEC §2.2 and §5.
- **Build:** the List and Map switch, MapLibre markers, the marker sheet, the route line from `GreatCircle`, and the camera rules.
- **Done when:** those QA rows pass, and changing filters never moves the camera.

#### [ ] 4.6 Refresh and offline

- **Goal:** the app stays useful with the API stopped.
- **Covers:** R1, R2, R5, R6, R11, T4, OFF-1, OFF-2, OFF-3, OFF-5. QA: Q14, Q15, Q16, Q17, Q19.
- **Read:** DOMAIN_RULES §9; UI_SPEC §4.
- **Build:** pull to refresh, the ⟳ button in Map mode, `StatusBanner` and Retry.
- **Done when:** those QA rows pass, including a force-quit and relaunch with the API stopped.

#### [ ] 4.7 Use my location

- **Goal:** the ⌖ button improves the origin ranking, and asks for nothing until tapped.
- **Covers:** P2, P5, SRCH-7, D16. QA: Q2c.
- **Read:** ARCHITECTURE §9; `CLAUDE.md` gotchas.
- **Build:** `ACCESS_COARSE_LOCATION`, one Fused Location reading on tap, and `setUserLocation`.
- **Done when:** QA row Q2c passes: with Seattle set, From `y` lists Vancouver first, and denying permission causes no error.

#### [ ] 4.8 Android QA

- **Goal:** a full pass over the whole app before starting iOS.
- **Covers:** TEST-6, PERF-1. QA: all Android rows Q1–Q24, including rotation (Q21).
- **Done when:** every Android row is filled in honestly. Problems found are fixed, or listed for the owner.

### Phase 5: iOS app

Each step has the same goal, coverage, QA rows and "done when" as its Android step, on iOS. Only the differences are listed.

| Step | Mirrors | iOS specifics |
|---|---|---|
| [ ] 5.1 App shell and live list | 4.1 | `NSAllowsLocalNetworking` and `API_BASE_URL` in `Info.plist`, `ExplorerModel`, and `TabView`. Remove the temporary count from Step 3.6 and the starter's `Greeting.kt`. Use Xcode's Thread Performance Checker instead of StrictMode |
| [ ] 5.2 Search | 4.2 | Native text fields, with the dropdown under the focused field |
| [ ] 5.3 Filters | 4.3 | `.sheet` with detents and `DatePicker`. Convert dates from year, month and day components |
| [ ] 5.4 Selection, card and saved flights | 4.4 | `.sensoryFeedback` for haptics and an overlay toast with Retry |
| [ ] 5.5 Map | 4.5 | MapKit: `Map`, `Annotation`, and `MapPolyline` with `contourStyle: .geodesic` |
| [ ] 5.6 Refresh and offline | 4.6 | `.refreshable` |
| [ ] 5.7 Use my location | 4.7 | Core Location, `NSLocationWhenInUseUsageDescription`, and a single `requestLocation()` |
| [ ] 5.8 iOS QA | 4.8 | All iOS rows in TEST_PLAN §5 |

Differences in behaviour between platforms aren't allowed without asking (§3). Any difference must be native styling only.

### Phase 6: Hardening

#### [ ] 6.1 Performance and leaks

- **Goal:** prove the app stays smooth and leak-free with the full dataset.
- **Covers:** PERF-1, PERF-2, PERF-3, PERF-4, UX-1. QA: Q24.
- **Read:** TEST_PLAN §4.
- **Done when:** every check in TEST_PLAN §4 is run on both platforms and recorded.

#### [ ] 6.2 Scale benchmarks

- **Goal:** replace estimates with measurements, as the owner's scalability rule asks.
- **Covers:** SCALE-1, SCALE-2, SCALE-3, SCALE-4, SCALE-5, SCALE-6, SCALE-7.
- **Read:** TEST_PLAN §4.1; ARCHITECTURE §12.
- **Done when:** the medians are in SUBMISSION_NOTES §6 and each SCALE row has a current status. JVM numbers are labelled as a stand-in for phones.

#### [ ] 6.3 Accessibility, dark mode and visual polish

- **Goal:** the two apps feel cohesive and finished.
- **Covers:** UX-2, UX-3, UX-4. QA: Q22, Q23.
- **Read:** UI_SPEC §6 and §8.
- **Build:** fixes from comparing the two apps side by side for spacing, typography and copy, including the largest font size, VoiceOver and TalkBack, and dark mode.
- **Done when:** those QA rows pass and the owner has seen the apps side by side.

#### [ ] 6.4 Requirements audit

- **Goal:** nothing from the brief is missed.
- **Covers:** every REQUIREMENTS row, DEL-5.
- **Done when:**
  - The brief has been re-read line by line against REQUIREMENTS.
  - Every row is ✅, or explained under Known limitations.
  - Every *(assumption)* in DOMAIN_RULES appears in SUBMISSION_NOTES.
  - The rule coverage check has no gaps.

### Phase 7: Submission

#### [ ] 7.1 README and submission notes

- **Goal:** a reviewer can build, run and test from the README alone.
- **Covers:** DEL-1, DEL-2, DEL-3, DEL-5, TEST-6, DATA-6.
- **Build:** the final README and SUBMISSION_NOTES: approach, decisions, assumptions, tradeoffs, limitations, scalability and AI usage. Remove the "Working draft" note.
- **Done when:** a fresh clone builds and runs using only the README, and no TODOs are left.

#### [ ] 7.2 Screen recording

- **Goal:** the walkthrough the brief requires.
- **Covers:** DEL-4.
- **Owner does:** records both apps, following TEST_PLAN §7: search, filters, map selection, saving flights, and reopening with the API stopped.
- **Agent does:** resets the data and lists the steps before each take.

#### [ ] 7.3 Hand in

- **Goal:** submit.
- **Covers:** DEL-1.
- **Done when:** `git status` is clean, all accepted work is pushed, and the owner has the repository link or ZIP.

## 9. Risks

| Risk | Likelihood | Impact | What to do |
|---|---|---|---|
| No Android SDK on this Mac | Certain today | High | Step 0.1, as the first thing |
| Room or KSP don't support Kotlin 2.4.21 or AGP 9's new KMP plugin | Medium | High | Step 0.2, before anything depends on them. If it fails, the owner chooses between waiting and SQLDelight |
| MapLibre in Compose takes too long, because of custom pill markers | Medium | Medium | Step 0.3 is time-limited. If it fails, ask before using Google Maps |
| Swift 6 strict concurrency conflicts with Kotlin callbacks | Medium | Low | Step 3.6 tests this early. Deliver on the main thread and keep the bridge small |
| An emulator or simulator can't reach the API | High if forgotten | Medium | The setup in Steps 4.1 and 5.1, and ARCHITECTURE §6.4 |
| Passenger figures are approximate | Certain | Low | They only order airports. Already disclosed in SUBMISSION_NOTES §5 |
| Scope grows beyond the brief | Medium | Medium | One step per review, and the tiers in §10 |

## 10. Priority tiers

Only the owner cuts scope.

**Must:** every REQUIREMENTS row in §1–§10, shared tests for TEST-1 to TEST-5, QA on both platforms, the README, the submission notes and the recording.

**Should**, dropped in this order if needed:

1. The `--mode` server switch (Step 1.2). Instead, demo empty and error states by editing the fixture or stopping the server.
2. The "Fail next DB write" debug toggle (Step 4.4). The store tests already cover V7.
3. Animating the camera to fit the route on selection.
4. The live "Show N flights" count inside filter sheets.

**Could:**

- Sort options (price vs date).
- A few Compose UI tests or XCUITests for the selection flow.
- A GitHub Actions workflow running `:shared:jvmTest`.
- Persisting the last-used filters across launches.
- Building the "Documented" scalability paths (REQUIREMENTS §11).
