# Flight Explorer

A Roame-inspired award-flight explorer, built natively twice: **SwiftUI** on iOS and **Jetpack Compose** on Android. Both apps sit on one **Kotlin Multiplatform** core that does all the work: fetching, caching, search, filtering, sorting, map grouping, selection, saved flights and offline state. The apps only draw the shared state and send the user's actions back. A small Ktor server serves `fixtures/flights.json` as the local API.

## What's in it

From the brief, on both platforms:

- **Search** with separate From and To fields that match airport codes and city names, ignoring case and surrounding spaces.
- **Filters** for departure dates (both ends included), cabin, maximum points (the limit included) and loyalty programs (any of several), combined with AND. The result count and the active filters are always shown, with Clear all and Reset.
- **A list and a map of the same results.** The map groups flights into one pill per destination airport, showing its flight count. Tapping a pill lists that destination's flights under a title with the count and the lowest points. By the owner's choice the pill itself doesn't show the points (D44).
- **Selection:** a card with the route, date, cabin, points, program and Save or Remove, and the route drawn on the map. The selection survives moving between the list and the map, and clears when a filter removes the flight.
- **Saved flights** in their own tab, whatever the filters say.
- **Offline:** the last results and the saved flights survive a restart. A failed refresh keeps the data and says how old it is. With nothing cached, the app explains the error and offers Try again. An empty response is not shown as an error, and a failed save never looks successful.

Added at the owner's request, beyond the brief:

- An airport search engine: a prefix tree, then fuzzy matching for typos and sound-alike spellings, ranked by the user's approximate location and airport size. Suggestions appear as you type.
- A Roame-style landing page with best deals by destination and best offers by program, and a full-screen search page with its own calendar.
- Map-first results with the list as a sheet over the map, a plane flying the selected route, a sort (lowest or highest points, by program, best offer) and a flight details page.
- One design system shared by both apps, dark mode, and layouts for the largest text sizes.

The submission notes list every decision and who made it ([§2](docs/SUBMISSION_NOTES.md#2-decision-record)).

## Prerequisites

- **A Mac** for iOS: Xcode 26.4 or newer, with an iOS 17+ simulator.
- **Android Studio** Quail 3 or newer, with Android SDK 37.0 and build-tools 37.0.0, and an emulator image **with Google Play or Google APIs**, which location needs. API 26 or newer.
- **A JDK to start Gradle.** Gradle downloads the JDK 25 it builds with. If `./gradlew` says "Unable to locate a Java Runtime", use Android Studio's:

  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```

- **Internet access** for the first build, and for map tiles (Apple Maps on iOS, OpenFreeMap on Android).

Kotlin 2.4.21 · Gradle 9.7.0 · AGP 9.3.1 · Compose BOM 2026.09.00 · Swift 6 · iOS 17 · Android 8 (API 26) and up.

## Run it

**1. Start the local API** from the repository root, and leave it running:

```bash
./gradlew :server:run
```

It serves `GET http://localhost:8080/flights`, the fixture's envelope with HTTP 200. Set `PORT` to use another port; the apps expect 8080.

**2. Android.** Start an emulator, then:

```bash
./gradlew :androidApp:installDebug
```

Or open the folder in Android Studio and run `androidApp`. The app reaches the API at `http://10.0.2.2:8080`, the emulator's address for your computer. After an install, Android 17 can show an "Updating…" screen for a minute; wait, then open the app.

**3. iOS.** Open `iosApp/RoameStarter.xcodeproj`, choose the `RoameStarter` scheme and an iPhone simulator, and Run. The first build also builds the shared Kotlin framework, which takes a few minutes; until then the editor may show "No such module 'Shared'". From the command line:

```bash
xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
  -destination 'platform=iOS Simulator,name=<an installed iPhone>' build
```

Both apps ask for approximate location when they first open. It only ranks airports and picks the deals' home airport, and everything works without it. In a simulator or emulator, set a location by hand.

**On a physical iPhone** (tested on an iPhone 16): set your team under Signing & Capabilities, and set `API_BASE_URL` in `iosApp/Info.plist` to your Mac's address on the same Wi‑Fi, such as `http://192.168.1.3:8080`. Running the app on a physical Android phone wasn't tested.

## Things to try

- **Search:** From `  BoStOn ` finds Boston. To `tokyo` finds both Tokyo airports, HND and NRT. A typo such as `sidnee` finds Sydney, with a "Showing Sydney (SYD)" hint.
- **Filters:** set dates, a cabin, a points limit and two programs. The chips and the count follow every change. Clear one chip, then Reset.
- **Map and list:** tap a pill to see its flights, pick one, and drag the list up and down. The selection stays. Filter the flight out and the selection clears.
- **Offline:** stop the API and reopen the app. The data stays, with a "Couldn't refresh" banner. Search, filters and removing a saved flight all still work.
- **No cached data:** delete the app, then open it with the API stopped. It explains the error and offers Try again. Start the API and tap it.
- **A failed save** (debug builds): long-press the "Selected flight" title on a flight's card, then save or remove it. A toast reports the failure with Retry, and the heart doesn't change.

[TEST_PLAN §6](docs/TEST_PLAN.md#6-failure-injection-hooks-debug-builds-only) has more ways to cause failures: a hanging server, empty and edited data.

## Tests

```bash
./gradlew :shared:jvmTest                 # the shared rules, on any OS (433 tests)
./gradlew :shared:iosSimulatorArm64Test   # the same tests and the iOS-only ones, on the iOS simulator (Mac only)
./gradlew :androidApp:testDebugUnitTest   # Android's own unit tests
```

Every rule in [DOMAIN_RULES](docs/DOMAIN_RULES.md) has a test whose name starts with the rule's ID, such as `f4_pointsEqualToMax_isIncluded`. Five rules only exist on screen, and those are checked by hand. The shared tests cover the filtering rules, the date and points boundaries, map grouping, persistence on a real database, and failure handling with real network errors.

The checks performed by hand on both platforms, with dates and results, are in the [QA checklist](docs/TEST_PLAN.md#5-manual-qa-checklist). Scale benchmarks on synthetic data up to 100,000 flights run on demand; their report goes to `shared/build/reports/scale-benchmarks.md`, and the results are in [submission notes §6](docs/SUBMISSION_NOTES.md#6-scalability):

```bash
./gradlew :shared:jvmTest -Pbench --tests "*ScaleBenchmark*"
```

## How it's built

```
fixtures/flights.json ─► server (Ktor) ─► GET /flights
                                              │
shared (Kotlin Multiplatform)                 ▼
  Ktor client ─► Room (SQLite): the cached flights and the saved flights
                   │
                   ▼
  ExplorerStore ─► search, filters, sort, map grouping, deals ─► ExplorerState
        ▲                                                            │
        │ user actions                                               ▼
  androidApp (Compose) and iosApp (SwiftUI) draw the state; neither decides anything about flights
```

- **One source of truth.** The store combines the cached flights, the saved flights and the user's input into one immutable `ExplorerState`. Search and filtering run off the main thread, and the latest input always wins.
- **No optimistic saves.** The saved state changes only when the database reports it, so a failed write can't look like a success.
- **Swift sees no coroutines.** A hand-written bridge in `iosMain` turns the store's state into a callback and its actions into plain functions.

The full design is in [ARCHITECTURE.md](docs/ARCHITECTURE.md), and the reasons and rejected alternatives in its [decision log](docs/ARCHITECTURE.md#11-decision-log).

## Documents

| Document | Contents |
|---|---|
| [Submission notes](docs/SUBMISSION_NOTES.md) | Approach, decisions, assumptions, tradeoffs, known limitations, scalability, the checks performed, and how AI was used |
| [Requirements](docs/REQUIREMENTS.md) | Every requirement in the brief, with acceptance criteria and its status |
| [Domain rules](docs/DOMAIN_RULES.md) | The exact behaviour of search, filters, sorting, the map, selection, saved flights and offline, rule by rule |
| [Architecture](docs/ARCHITECTURE.md) | Modules, data flow, the database, networking, threading, the iOS bridge, libraries, decision log, scalability |
| [UI spec](docs/UI_SPEC.md) | Screens, every loading, empty and error state with its wording, the design system |
| [Test plan](docs/TEST_PLAN.md) | The shared tests, the QA checklist with results on both platforms, how to cause failures |
| [Plan](docs/PLAN.md) | The build order, step by step, with each step's result |
| [Case study: the map leak](docs/CASE_STUDY_MAP_LEAK.md) | How a memory leak in the Android map was found, narrowed down and fixed |
| [CLAUDE.md](CLAUDE.md) | The working agreement for the AI coding agent |

## Layout

```
server/        The local API (Ktor)
shared/        Kotlin Multiplatform: models, networking, the Room database, search, filters, sorting, the store
androidApp/    The Jetpack Compose app
iosApp/        The SwiftUI app
fixtures/      flights.json: the 1,000 provided flights (unchanged)
               airports.json: approximate annual passengers for search ranking (ours; a copy ships in the app)
docs/          The documents above
```

Third-party code and assets (a Kotlin port of Apache Commons Codec's Double Metaphone, the Inter font, Material Symbols icons, program logos and city photos) are credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The Gradle wrapper's licence is in `LICENSE`.
