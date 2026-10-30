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
| `docs/PLAN.md` | Working agreement, milestones, risks, priority tiers |
| `docs/SUBMISSION_NOTES.md` | Decisions, assumptions, limitations, the AI usage log |

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
server/ †              Ktor JVM app: GET /flights (raw fixture bytes)
shared/                KMP module → Android library + static Shared.framework (+ test-only jvm target)
  src/commonMain/kotlin/travel/roame/takehome/
    domain/search/       airport search engine: trie, fuzzy, ranking (DOMAIN_RULES §2, §2b)
    vendor/commonscodec/ DoubleMetaphone — THIRD-PARTY port (Apache-2.0), see THIRD_PARTY_NOTICES.md
    {model,data,presentation,di} †
  src/commonMain/sqldelight/ †
  src/commonTest/        rule tests (run on JVM and iOS)
  src/jvmTest/           fixture contracts + DoubleMetaphoneOracleTest (port vs commons-codec jar)
  src/androidMain/  src/iosMain/   (iosMain/ios/ExplorerStoreIos.kt = Swift bridge †)
androidApp/            Compose app, package travel.roame.takehome.android
iosApp/                SwiftUI app: RoameStarter.xcodeproj, iOS 17, Swift 6
fixtures/flights.json  1,000 flights, envelope {"flights":[…]} — provided; never edit
fixtures/airports.json ours: approximate annual passengers for ranking; optional (P4 fallback)
docs/                  planning docs (see table above)
```

## Commands

| Task | Command |
|---|---|
| Run local API † | `./gradlew :server:run` → `http://localhost:8080/flights` |
| API in QA modes † | `./gradlew :server:run --args="--mode=empty"` (also `error`, `slow`) |
| Shared tests, any OS | `./gradlew :shared:jvmTest` |
| Shared tests on iOS sim (Mac) | `./gradlew :shared:iosSimulatorArm64Test` |
| Build Android | `./gradlew :androidApp:assembleDebug` |
| Install on emulator | `./gradlew :androidApp:installDebug` |
| Build iOS (Mac) | `xcodebuild -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter -destination 'platform=iOS Simulator,name=<an installed iPhone>' build` |

When a † command starts working, update this table and `README.md`.

**Toolchain** (pinned by the starter): Kotlin 2.4.21 · Gradle 9.7.0 · AGP 9.3.1 · JDK 25 (auto-provisioned) · compileSdk 37 · minSdk 23 · iOS 17 · Swift 6 · Compose BOM 2026.09.00. Library versions go in `gradle/libs.versions.toml` and must be compatible with this toolchain.

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
  - The starter manifest has **no INTERNET permission**.
  - The API is cleartext HTTP, so add a network security config that allows `10.0.2.2` and `localhost`.
  - The emulator reaches the host at `http://10.0.2.2:8080`.
- **iOS networking:** the simulator uses `http://localhost:8080`. Add `NSAppTransportSecurity › NSAllowsLocalNetworking = YES` to `iosApp/Info.plist`.
- **iOS linking:** `Shared` is a **static** framework. SQLDelight's native driver needs `-lsqlite3` in the app's `OTHER_LDFLAGS`.
- **SQLite on old Android:** `minSdk 23` means old platform SQLite with no `ON CONFLICT DO UPDATE`. Upsert with `INSERT OR IGNORE` + `UPDATE` instead.
- **Fixture traps:**
  - "Mileage Plan" and "MileagePlus" are different programs.
  - Tokyo has two airports (HND and NRT).
  - 364 routes cross the antimeridian, so draw them as geodesics or unwrap longitudes.
- **Date pickers:** Android's Material 3 date picker returns **UTC** midnight millis, so convert them with UTC.
- **Server working directory:** the Gradle `run` task's default working directory is the module dir. Set it to the repo root so `fixtures/` resolves.
- **Kotlin/Native tests:** `iosSimulatorArm64Test` only runs on macOS. On Linux, `compileKotlinIosSimulatorArm64` still type-checks the iOS code, and `jvmTest` runs the same `commonTest` suite.

## Before you say "done"

- [ ] `./gradlew :shared:jvmTest` passes. Android builds. iOS builds (on a Mac).
- [ ] New behaviour has a rule in DOMAIN_RULES, a test, and a status update in REQUIREMENTS.
- [ ] Nothing in `androidApp/` or `iosApp/` decides anything about flight data.
- [ ] Notable AI-assisted work has a row in the `docs/SUBMISSION_NOTES.md` AI usage log.
- [ ] Architectural decisions are in the SUBMISSION_NOTES decision record and the ARCHITECTURE decision log.
- [ ] Scalability impact is noted in the relevant docs.
- [ ] The owner has reviewed and accepted the change **before** you push.
