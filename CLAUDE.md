# CLAUDE.md

Working agreement for AI coding agents in this repo. Read this file first, then the doc for your task.

## What this is

**Flight Explorer** is a take-home project: a Roame-inspired award-flight list and map. It is built natively twice, in SwiftUI (iOS) and Jetpack Compose (Android), on one Kotlin Multiplatform data layer. A tiny local API serves `fixtures/flights.json`.

| Doc | Use it for |
|---|---|
| `docs/REQUIREMENTS.md` | Every requirement in the brief, with an ID (`FILT-3`…) and acceptance criteria. This is the definition of done. |
| `docs/DOMAIN_RULES.md` | Exact search, filter, map, selection, saved and offline semantics, with rule IDs (`F4`…). Tests encode these rules. |
| `docs/ARCHITECTURE.md` | Modules, data flow, persistence schema, networking, threading, iOS interop, libraries, decision log |
| `docs/UI_SPEC.md` | Screens, the state catalogue with copy, components, design tokens, platform notes |
| `docs/TEST_PLAN.md` | Shared test matrix, the manual QA checklist, the demo script |
| `docs/PLAN.md` | What to build next: phases, steps, review gates, risks, priority tiers |
| `docs/SUBMISSION_NOTES.md` | Decisions, assumptions, limitations, the AI usage log |
| `docs/CASE_STUDY_MAP_LEAK.md` | How the Android map's memory leak was found, narrowed down and fixed (Step 6.1, D31) |

## How we work (owner's rules — follow every time)

1. **One functionality at a time, behind a review gate.**
   - Implement one feature end to end: code, tests and docs. Run the checks.
   - Then **stop and ask the owner to review** it. Summarise what changed, what was verified and what wasn't.
   - Commit locally, but **push only after the owner explicitly accepts**. Never bundle unrelated features in one review.
2. **Ask before any significant architectural decision.** That includes:
   - a new module, endpoint, library, data source or permission,
   - changes to the data model or the shared API surface,
   - behaviour that differs between platforms,
   - sending data to a third party.

   Present the options with tradeoffs and a recommendation, in plain language with a concrete example. Don't implement until the owner answers.
3. **Record every notable architectural decision**, including the ones the owner makes, in two places:
   - the decision record in `docs/SUBMISSION_NOTES.md` §2 (what, why, who decided, date),
   - the decision log in `docs/ARCHITECTURE.md` §11.
4. **Consider scalability in every design and doc.** State the cost (time and memory) and what breaks or changes at 10× and 100× the data. Keep the Scalability sections of each doc current.
5. **Third-party code is never copied silently.**
   - Vendored code lives under a `vendor/` package and keeps its license header.
   - It is listed in `THIRD_PARTY_NOTICES.md` and disclosed in `docs/SUBMISSION_NOTES.md`.
   - Verify it against the original wherever possible.

## Golden rules

1. **Business logic lives only in `shared/`.** That covers fetching, caching, search, filters, ordering, map grouping, selection, saved flights and offline state. The platform UIs render `ExplorerState` and call store intents. They never filter, sort or group anything themselves.
2. **Every rule in `docs/DOMAIN_RULES.md` has a test** whose name starts with the rule ID, e.g. `f4_pointsEqualToMax_isIncluded`. If you change a rule, update the doc and the test in the same commit.
3. **Never block the main thread.** HTTP goes through Ktor `suspend` calls, SQLite through `Dispatchers.IO`, and filtering through `Dispatchers.Default` via `mapLatest`, so the latest input wins.
4. **Never fake success.** There is no optimistic save or remove: state changes only when the DB emits. A failed refresh keeps the existing data and shows it as stale. Always rethrow `CancellationException`.
5. **Don't hardcode fixture facts.** Date bounds, cabins, programs and the points range all come from the data. Flight IDs are opaque; six are `f101`…`f106`, so never parse an ID.
6. **Dates are calendar dates.** Use `kotlinx.datetime.LocalDate` in shared code, and pass ISO `yyyy-MM-dd` strings across the Swift boundary. Never convert a departure date through a timezone, or `2027-02-01` can render as Jan 31.
7. **When something is ambiguous,** choose a reasonable behaviour, write it as a rule tagged *(assumption)* in DOMAIN_RULES, add a row to SUBMISSION_NOTES §3, and move on.

## Repo map

† = planned, does not exist yet.

```
server/                Ktor JVM app: GET /flights (raw fixture bytes)
shared/                KMP module → Android library + static Shared.framework (+ test-only jvm target)
  src/commonMain/kotlin/travel/roame/takehome/
    domain/              filters, ordering, active filters, map grouping, route lines, points text, landing-page deals: pure functions (DOMAIN_RULES §3–§6b, §10)
    domain/search/       airport search engine: trie, fuzzy, ranking, suggestions, shipped passenger figures (DOMAIN_RULES §2, §2b)
    vendor/commonscodec/ DoubleMetaphone — THIRD-PARTY port (Apache-2.0), see THIRD_PARTY_NOTICES.md
    model/               Flight, Airport, FlightFilters, FilterOptions, ActiveFilter, FetchError
    data/remote/         fetch, decode and classify failures (ARCHITECTURE §6.3)
    data/                FlightRepository (cache flow, sync status, refresh), SavedFlightsRepository, the "Fail next DB write" debug hook
    data/local/          Room database (ARCHITECTURE §5)
    presentation/        ExplorerStore, ExplorerState, the reducer, the date calendar (DOMAIN_RULES §3b)
    di/                  AppGraph, the single composition root
  shared/schemas/        Room's exported schema JSON
  src/commonTest/        rule tests (run on JVM and iOS)
  src/jvmTest/           fixture contracts, DoubleMetaphoneOracleTest (port vs commons-codec jar), real OkHttp failures
  src/iosTest/           real Darwin failures, the app's database file
  src/androidMain/  src/iosMain/   (iosMain/ios/ExplorerStoreIos.kt = Swift bridge †)
androidApp/            Compose app, package travel.roame.takehome.android
iosApp/                SwiftUI app: RoameStarter.xcodeproj, iOS 17, Swift 6
fixtures/flights.json  1,000 flights, envelope {"flights":[…]} — provided; never edit
fixtures/airports.json ours: approximate annual passengers for ranking; a copy ships in the app (D15)
docs/                  planning docs (see table above)
```

## Commands

| Task | Command |
|---|---|
| Run local API | `./gradlew :server:run` → `http://localhost:8080/flights` |
| Shared tests, any OS | `./gradlew :shared:jvmTest` |
| Shared tests on iOS sim (Mac) | `./gradlew :shared:iosSimulatorArm64Test` |
| Scale benchmarks (on demand) | `./gradlew :shared:jvmTest -Pbench --tests "*ScaleBenchmark*"` → `shared/build/reports/scale-benchmarks.md` |
| Android unit tests | `./gradlew :androidApp:testDebugUnitTest` |
| Build Android | `./gradlew :androidApp:assembleDebug` |
| Install on emulator | `./gradlew :androidApp:installDebug` |
| Build iOS (Mac) | `xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter -destination 'platform=iOS Simulator,name=<an installed iPhone>' build` |

When a † command starts working, update this table and `README.md`.

If `./gradlew` says "Unable to locate a Java Runtime", first run `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

**Toolchain** (pinned by the starter): Kotlin 2.4.21 · Gradle 9.7.0 · AGP 9.3.1 · JDK 25 (auto-provisioned) · compileSdk 37 · targetSdk 36 (lowered from 37, D26) · minSdk 26 (raised from the starter's 23, D22) · iOS 17 · Swift 6 · Compose BOM 2026.09.00. Library versions go in `gradle/libs.versions.toml` and must be compatible with this toolchain.

## Conventions

- Kotlin follows the official code style. The package root is `travel.roame.takehome`.
- Shared UI models are immutable `data class`es, built from `List`, `Set`, primitives and ISO date strings.
- **Swift-facing API:**
  - No `suspend`, `Flow`, generics or reliance on default arguments in public signatures.
  - Swift goes through the `iosMain` bridge (ARCHITECTURE §8).
- Inject `CoroutineDispatcher`s and a `Clock` so tests are deterministic. Test with `runTest`, Turbine and fakes from `commonTest/.../testing/`.
- Shared code returns typed values (enums and sealed types), never user-facing copy. The copy lives in the platform apps and matches UI_SPEC §4.
- Commits are small and focused, with messages like `area: summary` (e.g. `shared: inclusive points boundary`). Reference requirement IDs when useful.
- Test-only libraries go in `gradle/libs.versions.toml` and are added only to `*Test` source sets.
- Don't edit `vendor/` by hand. To update it, re-port from upstream and rerun `DoubleMetaphoneOracleTest`.

## Gotchas

These were found in the starter and the fixture.

- **Android networking:**
  - Android 17 blocks apps that target API 37 from local network addresses such as `10.0.2.2`, and the request just times out. So the app targets 36 (D26).
  - The starter manifest has **no INTERNET permission**.
  - The API is cleartext HTTP, so add a network security config that allows `10.0.2.2` and `localhost`.
  - The emulator reaches the host at `http://10.0.2.2:8080`.
- **iOS networking:** the simulator uses `http://localhost:8080`. The plan adds `NSAppTransportSecurity › NSAllowsLocalNetworking = YES` to `iosApp/Info.plist`, but in Step 3.6 the simulator fetched it without that key.
- **Database (D3):** use Room with the **bundled** SQLite driver (`BundledSQLiteDriver`), never the platform driver. That keeps one modern SQLite on every Android version and iOS, and the iOS app needs no `-lsqlite3`.
  - In multiplatform Room, DAO functions must be `suspend` or return `Flow`.
  - Register `room-compiler` with KSP for every target.
  - Keep `sqlite-bundled` on the version Room is built against (2.6.2 for Room 2.8.5). Later versions, and Room 3, publish no `iosX64` target, which the starter builds (D21).
  - Re-saving a flight must keep its original `savedAt` (V2), so use the `@Transaction` save in ARCHITECTURE §5.2 rather than a bare `@Upsert`.
  - Read related tables in one `@Transaction`. Otherwise observers can see two refreshes mixed (ARCHITECTURE §5.2).
  - To test a failed write, add a trigger that raises an error (`SavedFlightsTest`). Closing the database doesn't work, because Room then reports a cancellation.
- **Fixture traps:**
  - "Mileage Plan" and "MileagePlus" are different programs.
  - Tokyo has two airports (HND and NRT).
  - 364 routes cross the antimeridian, so draw them as geodesics or unwrap longitudes.
- **Dates in the UI (Step 6.3c, D41):** both apps use their own calendar, drawn from the shared `DateCalendar` (K1–K5). Don't bring back the system pickers: Material's returns UTC midnight millis and iOS's works in the device's calendar, and both look different from each other.
- **Server working directory:** the Gradle `run` task's default working directory is the module dir. Set it to the repo root so `fixtures/` resolves.
- **MapLibre (D8, D23, D24, D27):** PLAN Step 4.5 lists what Step 0.3 found. MapLibre adds `ACCESS_FINE_LOCATION`, which has to be removed because D16 allows approximate location only, and `LatLngBounds.from` needs unwrapped longitudes across the antimeridian. Found in Step 4.5:
  - Fit a selected route only after the selection card has been measured, or it lands under the card.
  - Keep the ⓘ credit visible above the card: OpenStreetMap and OpenFreeMap require it.
  - Creating the first `MapView` logs three StrictMode disk reads from inside MapLibre (moved to a background thread in Step 6.1).
  - Made inside the results (Step 6.3d), the map stayed blank until the app went to the background and back: Android never created its `SurfaceView`'s surface, so MapLibre never became ready. `ExplorerMap` starts the map after its first layout with a size and hides and shows it once, which makes Android create the surface (found in Step 6.3d's review).
  - Every `MapView` that's made, or detached from the window and attached again, leaks about 4–7 MB of native memory. So the app keeps one map and never detaches it (D31); don't put the map back inside a `Crossfade` or a tab that leaves composition.
- **The owner's 8 GB Mac:** a build takes about 15 s with the emulator off and up to 8 minutes with it on, and the emulator has crashed under memory pressure. The owner's API server may run inside Android Studio's Gradle process, so never stop Gradle processes you didn't start. After an install, Android 17 can show an "Updating…" screen; press back and open the app again.
- **Device location (D16):** Android's Fused Location Provider needs Google Play services, so use an emulator image with Google Play or Google APIs. iOS needs `NSLocationWhenInUseUsageDescription`. In both simulators, set a location by hand. Ask for it when the app first opens (D32, which replaced D30's first focus of From), and never again automatically after a denial. On the Android 37 emulator, `adb emu geo fix` didn't take effect; Android's test location providers did (ARCHITECTURE §9). On the iOS simulator, `xcrun simctl location` and `xcrun simctl privacy … reset location` work (ARCHITECTURE §8).
- **Ktor failures (found in Step 2.1):**
  - Check timeouts before connectivity. On the JVM and Android, every timeout is also an `IOException`.
  - JSON errors arrive as `ContentConvertException`, not `SerializationException`.
  - Ktor decodes on the caller's thread, so `KtorFlightsApi` moves the whole fetch to `Dispatchers.Default`.
  - The iOS engine ignores connect timeouts (R10).
- **Store tests on a real database:** stop the store and wait for it before closing the database, because `backgroundScope` isn't enough (ARCHITECTURE §7). A read still running can hit the closed database and crash the test process.
- **Xcode project (Step 5.1):** it doesn't use folder-synced groups, so every new Swift file must be added to `project.pbxproj` (a build file, a file reference, the group and the Sources phase), or it silently isn't compiled. The owner's working copy holds their signing settings (`DEVELOPMENT_TEAM`, a personal bundle ID), so commit only your own lines: stage the committed version plus your change (`git hash-object -w` and `git update-index --cacheinfo`), edit the working copy the same way, and check that no `DEVELOPMENT_TEAM` is staged.
- **SwiftUI search fields (Step 5.2):** when a field loses focus, SwiftUI can write its old text back through the binding unchanged. Treat that as an edit and it clears a pick (S14), so the binding ignores writes that change nothing.
- **Kotlin types in Swift (Step 5.3):** Kotlin/Native lowercases a whole enum entry name, so `FilterKind.MaxPoints` is `.maxpoints` in Swift; check the generated `Shared.h` when unsure. Foundation has its own `SortOrder`, so Swift needs `Shared.SortOrder` for ours (Step 6.3d2). Kotlin classes aren't `Sendable`, so Swift 6 rejects a global `let` of them; use a computed `var`.
- **`#if DEBUG` in Swift (Step 5.4):** the hand-written Xcode project didn't define `DEBUG`, so `#if DEBUG` code compiled away even in Debug builds. The Debug configuration now sets `SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG`.
- **SwiftUI sizing (Step 6.3e):** a `.frame(maxHeight:)` grows a view's frame up to the cap when its parent offers more, so measure the view before that modifier (`onGeometryChange`), or the size you read is the cap.
- **idb after the simulator restarts:** taps fail ("dtuhidd connection invalidated") until `idb_companion` is restarted for the device.
- **SwiftUI progress (Step 5.6):** a linear `ProgressView` with no value is a still grey line on iOS, so the refresh bar is drawn by hand.
- **A hanging API for QA (Step 5.6):** macOS's `nc -l 8080` closes the connection once its input ends, so in the background it gives a quick failure. Use `sleep 45 | nc -l 8080` to hold a request open (TEST_PLAN §6).
- **Android performance checks (Step 6.1):** a headless emulator (`-no-window`) renders in software (`lavapipe`), so measure frames with a window and `-gpu host`. Under memory pressure, `am dumpheap` can freeze the app; to force a garbage collection, use `adb shell run-as <package> kill -10 <pid>` instead.
- **adb QA with a route selected (Step 6.3f):** the plane redraws every frame, so `uiautomator dump` waits for an idle screen that never comes and can take minutes. Read the screen before selecting, then tap by coordinates and take screenshots with `screencap`.
- **iOS performance checks (Step 5.8):** Instruments' Animation Hitches doesn't run on the simulator, and the Thread Performance Checker only runs from Xcode. `leaks`, `heap` and `footprint` work on the simulator app's process; pick the `travel.roame.takehome.ios` one, because the owner's own `.eric` build may be running too.
- **One design system (Step 6.3a, D38):** the two apps must look the same, so build UI from the components in `DesignSystem.kt` and `DesignSystem.swift` (UI_SPEC §6). A change to one is made to its twin in the same commit.
  - The palette and type scale are in `Theme.kt` and `Theme.swift`, the same values on both. Small pink text uses the accent (`#D63A60`), not Roame's lighter `#EA4167`, which is for graphics.
  - Text colour: on iOS use `Color.text`, never `Color.primary` (the system's pure black) or `.foregroundStyle(.primary)` (inside a `Button` that's the tint, pink). A new sheet needs `.foregroundStyle(Color.text)`, because sheets don't inherit the root's. On Android, text outside a `Surface` takes the theme's `onBackground`; without that it was black on the dark background.
  - Icons are the same Material Symbols on both. Add one as `res/drawable/ms_<name>.xml` (with its source in a header) and as `iosApp/Assets.xcassets/ms_<name>.imageset` (the upstream web SVG, template rendering), and list it in THIRD_PARTY_NOTICES. iOS draws them with `MSIcon`, which is decorative, so give the control around it an accessibility label.
  - Fonts are Inter (`res/font/`, `iosApp/Fonts/` under `UIAppFonts`). Use the named styles; on iOS a size without a `relativeTo:` text style won't follow Dynamic Type.
- **The landing page (Step 6.3b):**
  - A Compose `LazyRow` keeps its first visible item in view when its items reorder, so when the location arrived the deck hid its new best deal off to the left. The rows are keyed by the home airport (Android `key(...)`, iOS `.id(...)`) so new deals start at the first card.
  - A SwiftUI `ScrollView` draws its content under the status bar when scrolled; the landing page's is `.clipped()` to match Android.
  - Destination photos are found by the shared photo key (`dest_sao_paulo`). Android maps each key to `R.drawable` explicitly, never by name at runtime. A new photo goes in both apps and in THIRD_PARTY_NOTICES and the Photo credits text, with its Commons licence and requested credit.
  - Keep the results composed under the landing page (unplaced on Android, hidden on iOS), or the Android map is detached and leaks (D31).
- **The results' map (Step 6.3d):**
  - Don't animate a line's dashes on either map. MapKit redraws a `MapPolyline` only when its points change, and it draws a line's width and dashes about twice as large as asked but its dash phase at a different scale, so moving dots crawled and snapped back. On MapLibre, a zero-length leading dash with round caps draws an extra dot. The route is still dots with a flying plane (D43).
  - MapKit always draws lines under annotations, so iOS draws the route and the plane over the map as Core Animation layers (`RouteLayer`), placed with `MapReader`'s `convert` from a display link. Don't redraw SwiftUI views over the map every frame or on `onMapCameraChange(.continuous)`: it held up the map's drags, which on a real iPhone only took hold on the line. Don't move an `Annotation` every frame (MapKit places it unevenly) or inside `withAnimation` (it leaves the route). Cut the selected pill out rather than drawing a copy, which trails the map by a frame while dragging.
  - SwiftUI's map has no z-order, and MapKit stacks annotations by latitude. The selected pill is raised by making it the map's own selection (M4). Annotations need `.fixedSize()`, or MapKit squeezes them and cuts off text.
  - On iOS, record the camera only once, after the first automatic framing. Recording it on every `onMapCameraChange(.onEnd)` catches MapKit mid-animation while the dots redraw the map, and freezes a route's framing halfway. The user's own moves reach the camera through the position binding.
  - Fit a selected route only after the card has been measured and the sheet has dropped (Android `insetReady`, iOS `routeToFit`), or it lands under them.
  - Rotation and tilt are off on both maps, so the plane's heading stays true.
- **Search fields (Step 6.3c):** a field keeps what's typed locally and ignores the store's late echoes of it, but must take any other text the store sets (a swap, a deal, Reset). Both apps' `SearchField` remember what they sent until the store shows the last of it; keep that, or a swap leaves the results page's fields showing the old text.
- **Kotlin/Native tests:** `iosSimulatorArm64Test` only runs on macOS. On Linux, `compileKotlinIosSimulatorArm64` still type-checks the iOS code, and `jvmTest` runs the same `commonTest` suite.

## Before you say "done"

- [ ] `./gradlew :shared:jvmTest` passes. Android builds. iOS builds (on a Mac).
- [ ] New behaviour has a rule in DOMAIN_RULES, a test, and a status update in REQUIREMENTS.
- [ ] Nothing in `androidApp/` or `iosApp/` decides anything about flight data.
- [ ] Notable AI-assisted work has a row in the `docs/SUBMISSION_NOTES.md` AI usage log.
- [ ] Architectural decisions are in the SUBMISSION_NOTES decision record and the ARCHITECTURE decision log.
- [ ] Scalability impact is noted in the relevant docs.
- [ ] The owner has reviewed and accepted the change **before** you push.
