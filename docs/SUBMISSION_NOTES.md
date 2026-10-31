# Submission notes

> **Working draft.** Keep this file current while building, and finalise it in M7. Delete this note before submitting.

## 1. Approach

Flight Explorer is two native apps (SwiftUI and Jetpack Compose) on a single Kotlin Multiplatform core. The core owns every rule: fetching, caching, search, filtering, ordering, map grouping, selection, saved flights and offline state. It exposes them as one immutable `ExplorerState`, so both UIs only render state and send intents. The behaviour is specified rule by rule in [DOMAIN_RULES.md](DOMAIN_RULES.md) and tested once in `shared/src/commonTest`.

A small Ktor server (`./gradlew :server:run`) serves the fixture verbatim at `GET /flights`.

The airport search engine follows the owner's design: a prefix tree first, then fuzzy matching that mixes phonetic and n-gram matching (plus accent folding). It is built around the owner's four ways people mistype a place. Matches are ranked by the device's location and by airport passenger volume (D11–D17).

## 2. Decision record

Who shaped each part of the design. The **Origin** column separates four things:

- **Brief:** the assignment requires it. It isn't our decision; §2.1 lists these only to show what each design serves.
- **Owner:** the owner's own idea or instruction.
- **Owner's choice:** the owner picked one of the options Claude laid out.
- **Claude:** Claude's choice of *how* to meet a brief requirement, written into the planning docs. The **Status** column says whether the owner has reviewed it yet.

The full rationale and the rejected alternatives are in [ARCHITECTURE.md §11](ARCHITECTURE.md#11-decision-log).

### 2.1 What the brief fixes (not our decisions)

| Brief requirement | IDs |
|---|---|
| Two native apps: SwiftUI on iOS, Kotlin + Jetpack Compose on Android | REQUIREMENTS §0 |
| One shared Kotlin Multiplatform data and persistence layer. Networking, persistence, search and filtering are implemented once, and both apps consume them | DATA-1 – DATA-3 |
| A local API: `GET /flights` returns the fixture envelope with HTTP 200, in any server language, with one documented run command | API-1, API-2 |
| Persist the last successful results and the saved flights locally, plus the offline and error behaviours | DATA-4, DATA-5, OFF-1 – OFF-8 |
| A list and a map showing the same results; markers grouped by destination, with the count and the lowest points | VIEW-1, MAP-1 – MAP-4 |
| Selection shows a route connector and a card. It is preserved across list/map and cleared when the flight is filtered out | SEL-1 – SEL-3 |
| Libraries and the server framework are ours to choose | — |

### 2.2 Decisions

| # | Decision | Serves | Origin | Status | Date |
|---|---|---|---|---|---|
| D1 | Build the local API as a Ktor (Kotlin) module inside the existing Gradle build, run with `./gradlew :server:run`. The owner's reasoning: a natural fit for a Kotlin Multiplatform project. | API-1, API-2 | Owner's choice (Claude's recommendation over Node or Python) | Decided | 2026‑10‑30 |
| D2 | Share the *presentation state* too (`ExplorerStore`: selection, map grouping, offline state), beyond the four layers the brief requires. The owner's reasoning: the bridging code should be smaller than writing the same state in both apps. | DATA-3, VIEW-1, SEL-2 | Owner's choice (Claude's recommendation over a view model per platform) | Decided; not built yet | 2026‑10‑30 |
| D3 | **Room** (Google's database library) on its **bundled SQLite**. The owner's reasoning: one identical database engine on every phone and on iOS makes behaviour, and bugs, reproducible across devices. Room/KSP support for the new Kotlin 2.4.21 is a known risk, checked first in M0. | DATA-2, DATA-4, DATA-5 | Owner's choice (over SQLDelight; Claude leaned Room) | Decided; compatibility check in M0 | 2026‑10‑30 |
| D4 | Store saved flights as full snapshots in their own table | OFF-6, OFF-7 | Claude | Awaiting owner review | 2026‑10‑29 |
| D5 | Replace the whole cache on each successful refresh | DATA-4, OFF-3 | Claude | Awaiting owner review | 2026‑10‑29 |
| D6 | Filter in memory, off the main thread, with `mapLatest` so the latest input wins | PERF-2, PERF-3 | Claude | Awaiting owner review | 2026‑10‑29 |
| D7 | A hand-written Kotlin→Swift bridge | DATA-3 | Claude | Awaiting owner review | 2026‑10‑29 |
| D8 | **MapLibre** is the Android map library: open source, no API key, GPU-drawn vector maps, with map tiles from OpenFreeMap's free public server (which, like Apple for MapKit, sees which map areas are viewed). Google Maps was rejected for its mandatory billing account and key; osmdroid for its raster rendering and visual glitches (§4.5). iOS uses Apple's built-in MapKit. | MAP-1 – MAP-4 | Owner's choice (Claude's recommendation over Google Maps and osmdroid) | Decided | 2026‑10‑30 |
| D9 | A test-only `jvm()` target in `:shared` | TEST-1 – TEST-5 | Claude | Built with the search engine; awaiting owner review | 2026‑10‑30 |
| D10 | One app-scoped store per process | SEL-2, VIEW-1 | Claude | Awaiting owner review | 2026‑10‑29 |
| P1 | Keep the agent guide as `CLAUDE.md` (no `AGENTS.md`) | — | Owner | Decided | 2026‑10‑30 |
| P2 | One functionality at a time; the owner reviews it; push only after acceptance | — | Owner | Decided | 2026‑10‑30 |
| P3 | Ask the owner before significant architectural decisions, and record them here | — | Owner | Decided | 2026‑10‑30 |
| D11 | **Tiered search.** Tier 1 walks a prefix tree node by node (n → e → w) in O(m) and branches into the full names under it. Tier 2 (fuzzy) runs only when Tier 1 finds nothing. | SRCH-1 – SRCH-4 | **Owner**: the prefix-tree traversal is the owner's spec; tiered fallback was the owner's choice over a blended score | Built | 2026‑10‑30 |
| D12 | **Mixed fuzzy matching**: phonetic and n-gram matching plus accent folding. It covers the owner's hypothesis that people mistype a place in four ways: (1) a typo, (2) knowing the sound but not the spelling, (3) roughly knowing the spelling, (4) accents. Claude added an edit-distance typo signal alongside. | SRCH-5 (beyond the brief) | **Owner** (design and hypothesis); Claude (the edit-distance signal) | Built | 2026‑10‑30 |
| D13 | **Use Double Metaphone from an existing library, not our own implementation.** No library runs on iOS, so Apache Commons Codec's version is translated to Kotlin, credited, and verified against the original (§8) | SRCH-6 | **Owner** (use a library and disclose it); Claude proposed the translate-and-verify route, which was the owner's choice | Built | 2026‑10‑30 |
| D14 | A **"Showing Sydney (SYD)" hint** under a field when fuzzy matching was used | SRCH-8 | Owner's choice (the option Claude recommended) | Engine ready; UI pending | 2026‑10‑30 |
| D15 | **Rank matches by the user's location and annual airport volume.** Volume lives in a separate new file; `flights.json` is never edited. When the file is missing, the app falls back to the original data. | SRCH-7 | **Owner** (the idea, the separate file, the fallback); Claude (the file format, and reading "fallback" as flight counts, A12) | Built | 2026‑10‑30 |
| D16 | **Location comes from the device**: Fused Location Provider (Android) and Core Location (iOS), approximate accuracy, requested only when the user taps "Use my location". Without it, ranking uses volume only. Nothing is sent to a third party. This replaces Claude's earlier geo-IP proposal. | SRCH-7 | **Owner** (suggested on-device location instead of geo-IP) | Decided; platform code in M4/M5 | 2026‑10‑30 |
| D17 | **Ranking only orders the matched airports** (the hint line, and autocomplete later). The flight list keeps its date → points order (O1). | SRCH-7 | Owner's choice (option A) | Decided | 2026‑10‑30 |
| D18 | Search runs on **airports, not flights**, behind indexes | SCALE-1 | Claude | Built; awaiting owner review | 2026‑10‑30 |
| D19 | Ranking weights: origin 0.7 proximity / 0.3 volume, destination 0.2 / 0.8 | SRCH-7 | Claude (assumption A13) | Built; awaiting owner review | 2026‑10‑30 |

## 3. Assumptions

Each one is mirrored as an *(assumption)* rule in DOMAIN_RULES.

| # | Ambiguity in the brief | Assumption | Rule |
|---|---|---|---|
| A1 | How search "matches" codes and cities | Tier 1 is a case-, accent- and whitespace-insensitive **prefix** match on the code, the city, or any word of the city (`york` → New York). Fuzzy matching is the fallback (the owner's design, D11–D13). | S4–S10 |
| A2 | Whether cabin is single- or multi-select | Single-select ("Cabin." is singular; programs are explicitly multi) | F5 |
| A3 | What "start with the full fixture date range" means | The default range is the dataset's min–max, derived from the data, and it follows the data after a refresh | F2 |
| A4 | Whether selection clearing is permanent | Clearing is permanent; the selection doesn't come back when the filter is undone | L3 |
| A5 | What "route connector" means | Both a geodesic line on the map and an origin→destination connector graphic in the card | L6, M7 |
| A6 | Whether the saved view shares the discovery selection | No. The saved view has its own selection, independent of filters. | L7 |
| A7 | Whether saved details refresh from the API | Yes. On a successful refresh, saved snapshots whose id is present are updated, as well as on re-save. | V5 |
| A8 | Whether a successful empty response replaces a non-empty cache | Yes. A 200 with `[]` is authoritative, and is shown as "No flights available", not as an error. | R4 |
| A9 | How a partially invalid response is handled | The whole response is treated as failed, and the cache is kept | DM3 |
| A10 | What reset clears | Search fields and all filters; saved flights are untouched | C4 |
| A11 | Whether an extra API surface is allowed | The required contract is untouched; `--mode` is a QA-only switch | — |
| A12 | What "fall back to the original data" means when `airports.json` is missing | Volume = each airport's number of flights in `flights.json` | P4 |
| A13 | How much location vs popularity should count | Origin 0.7 / 0.3, destination 0.2 / 0.8 (D19) | P5 |

## 4. Technical tradeoffs by stack choice

Every technology choice made so far: the options considered, what each one gives and costs **in this project**, who chose it, and why. ✅ marks the chosen option. The IDs refer to the decision record (§2). "Claude" means Claude's choice, awaiting the owner's review unless the status says otherwise.

### 4.1 Platform and architecture

**Native UIs: SwiftUI + Jetpack Compose** (brief)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ SwiftUI + Compose | What the brief asks for; each app feels native | Two UIs to build and keep consistent |
| Compose Multiplatform on iOS | One UI codebase | The brief allows it only without a Mac, and prefers SwiftUI |

**Share presentation state, not only data** (D2, owner's choice: shared store)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Shared `ExplorerStore` (selection, map grouping, offline state) | List and map can't disagree; selection and offline rules are written and tested once | More Kotlin→Swift interop, and less idiomatic for SwiftUI developers, who expect their own view models |
| A view model per platform over shared repositories | More native on each side | Selection, grouping and offline rules would be written twice and could drift |

**One app-scoped store** (D10, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ One store per process | Survives Android rotation; one source of truth; nothing per screen to leak | State lives in memory for the app's lifetime (small). Android process death loses the in-memory query and filters; the cached data survives in the DB. |
| A store per screen | Tighter scoping | Risk of list and map holding different state |

**Kotlin → Swift bridge** (D7, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Hand-written `observe()` wrapper | No extra dependency; no compiler plugin tied to Kotlin 2.4.21 | Boilerplate per intent; Kotlin sealed types appear in Swift as class hierarchies (clumsy `switch`) |
| SKIE | Swift enums and `AsyncSequence`; much nicer Swift code | Must support the exact Kotlin version (2.4.21 is brand new) |
| KMP-NativeCoroutines | Swift async/await over Flows | Compiler plugin with the same version risk |

### 4.2 Data and networking

**Local API server** (D1, owner's choice: Ktor)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Ktor module in the Gradle build (`./gradlew :server:run`) | One language and one build; reuses the JDK Gradle already downloads; serves the fixture byte for byte | Gradle startup is slow (tens of seconds on the first run); heavier than a script |
| Node or Python script | ~10 lines; starts instantly | Reviewers need that runtime installed; a second language in the repo |

**HTTP client and JSON** (ARCHITECTURE §10, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Ktor client + kotlinx.serialization | The standard KMP pair: one implementation for both apps; `MockEngine` makes failure tests easy | Another library family to keep compatible with Kotlin 2.4.21 |
| Platform clients (OkHttp / URLSession) behind expect/actual | The most native networking | Networking would be written twice, against the brief's "once in KMP" |

**On-device database** (D3, owner's choice: Room)

Both options are libraries on top of **SQLite**, the database engine built into Android and iOS. Both meet every persistence requirement: atomic cache replacement, saving without duplicates, survival across restarts, reactive updates, work off the main thread, and in-memory tests on the JVM.

| Option | Strengths here | Weaknesses here |
|---|---|---|
| SQLDelight (Cash App) | Multiplatform since ~2019, very mature. **You write the SQL** and it generates type-safe Kotlin, so every query is visible and easy to explain. Smallest footprint (uses the phone's own SQLite). One Gradle plugin; simple migrations. | Uses the **phone's built-in SQLite**: Android 6 (our `minSdk 23`) ships a 2015 version with no "insert or update" syntax (we work around it with two statements), and the SQLite version differs by device and from iOS. The iOS app must link `sqlite3`. Its plugin's support for AGP 9's new multiplatform setup is unverified. |
| ✅ Room KMP (Google), with its bundled SQLite | Android's standard, so most reviewers know it. **You write Kotlin classes** with annotations. `@Upsert` maps directly onto OFF-6. It can **bundle its own modern SQLite**, so both apps and every Android version run the identical database, which also keeps modern features (full-text search, upsert) available for scaling. | Multiplatform only since 2025. More build machinery: the KSP code generator for 5 targets, plus database-constructor boilerplate for iOS. Generated code is hidden. The bundled SQLite adds roughly a megabyte per CPU architecture (est.). KSP/Room support for Kotlin 2.4.21 is unverified. |
| JSON files / DataStore | No library; trivial for 1,000 flights | We'd hand-build atomic writes, duplicate checks and change notifications, which is exactly what the databases provide |

Neither database is strictly better. Room's edge here is one consistent, modern SQLite on both apps and every Android version, plus reviewer familiarity. SQLDelight's edge is fewer moving parts, visible SQL, the longest multiplatform track record, and the smallest app. Either sits behind our repositories, so switching later only touches the data layer.

**Owner's reasoning for Room:** with SQLDelight, each phone runs its own built-in SQLite (a 2015 version on Android 6, a newer one on iOS), so a database issue could depend on the device and be hard to reproduce. Room's bundled SQLite is the same build everywhere, so what works, or breaks, on one device behaves the same on all of them. SQLDelight's fully visible SQL would make reading queries easier, but engine differences between devices are the harder kind of problem to debug. The accepted cost is Room's build setup and its version risk with Kotlin 2.4.21, which the PLAN M0 check confirms first; if it fails, the options go back to the owner.

**Saved flights as snapshots** (D4, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A full copy per saved flight, in its own table | Saved flights survive being removed from the API (OFF-7) and cache wipes | The data is duplicated between the cache and saved flights, so a refresh must update both in one transaction (V5) |
| A table of saved ids joined to the cache | No duplication | Loses saved flights that disappear from the API, which breaks OFF-7 |

**Cache refresh** (D5, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Replace everything in one transaction | Simple and atomic; matches an API that always returns the full list | Rewrites every row on every refresh: trivial at 1,000, seconds at 100k |
| A diff (upsert changed rows, delete missing ones) | Less work per refresh at scale | More code and edge cases, for no benefit at this size |

**Filtering** (D6, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ In memory, off the main thread, latest input wins | Pure functions that are easy to test at the boundaries; ~1 ms at 1,000 flights | The whole dataset sits in memory; at ~100k flights it moves to SQL (ARCHITECTURE §12) |
| SQL `WHERE` clauses | Scales to very large data | Harder to compose and to test, and needs the database for every unit test |

### 4.3 Search

**How prefix and fuzzy matching are combined** (D11, owner)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Tiered: a prefix tree first (O(m) walk); fuzzy only when that finds nothing | Predictable while typing; fuzzy never adds noise to good prefix results | A weak prefix hit can hide a better fuzzy one |
| Blended: one score over every signal | Every signal always competes; it produces a natural ranking | Less predictable while typing. It passed the same 114 prototype cases, so the choice is about behaviour, not accuracy |

**Which fuzzy signals** (D12, owner; edit distance added by Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Phonetic + n-gram + typo distance + accent folding | Covers the owner's four cases: typo, sound-alike, partly right spelling, accents | More code, and thresholds tuned on only 32 airports. Places outside the data can sound alike (`newark` → New York) |
| Prefix only | Simplest, fully predictable | No tolerance for any mistake |
| Soundex | Tiny | English-only rules that keep the first letter, so it misses `shicago` and `okland` |

**The phonetic algorithm's implementation** (D13, owner)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Double Metaphone translated from Apache Commons Codec, credited, oracle-tested | A proven algorithm with multilingual rules; identical on both apps; correctness proven against the original (§8) | ~900 lines of third-party logic we didn't design; license obligations (notice, modification statement); updates mean re-porting |
| Commons Codec as a normal dependency | Zero copied code | JVM-only, so iOS would get no phonetic matching and the apps would behave differently |
| Our own small Metaphone-style key | Fully ours and explainable | Owner preferred a library over a from-scratch implementation; narrower rules |

**Accent folding** (S1, Claude, for the owner's case 4)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Our own Latin character table (`ã` → `a`, `ß` → `ss`) | Works in common Kotlin on every platform; tiny | Latin scripts only; no transliteration (`東京`) |
| Unicode normalisation / ICU | Complete | Not available in common Kotlin (`java.text.Normalizer` is JVM-only), so it would need per-platform code |

**Feedback when fuzzy matching kicks in** (D14, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A "Showing Sydney (SYD)" hint under the field | Corrections are never silent; small UI cost | One more line in the header |
| A suggestions dropdown | The best UX, and where ranking would shine | Much more UI work on both platforms |
| Silent | Nothing to build | The user can't tell why `sidnee` shows Sydney flights |

**What search runs on** (D18, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Airports (32 here), behind indexes; flights are then filtered by airport code | Cost doesn't grow with the number of flights (SCALE-1) | Text search only covers places (cabin, program and the rest are filters anyway) |
| Each flight's text | No separate index | Work grows with the number of flights on every keystroke |

### 4.4 Ranking and location

**Where airport volume comes from** (D15, owner; file format by Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A separate `fixtures/airports.json`, falling back to flight counts from `flights.json` | The provided data stays untouched; the app works without the file; the fallback is tested | The figures are approximate and unverified; one more file to serve and cache |
| Add a field to `flights.json` | One file | Changes the provided data, which the API must serve verbatim |
| A table compiled into the app | Always available offline | Needs an app release to update |

**Where the user's location comes from** (D16, owner)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ The device: Fused Location Provider (Android) and Core Location (iOS), approximate accuracy | Accurate; works offline (GPS needs no internet); nothing sent to a third party; no extra endpoint | A permission prompt; location code on each platform. Fused needs Google Play services, so it won't work on devices without them and the emulator needs a Play image. Android's plain `LocationManager` would avoid that dependency, but it is lower-level and less battery-efficient. |
| Geo-IP through our local API (Claude's earlier idea) | No permission prompt | Locally it finds the developer's machine, not the user. City-level only; wrong behind a VPN; needs an outside geo-IP service |
| The app calls a public geo-IP service | No permission prompt | Sends the device's IP to a third party; rate limits |

**What ranking reorders** (D17, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A: only the matched airports (the hint, and autocomplete later) | The flight list stays in date → points order, so earlier and cheaper flights are never pushed down | Without autocomplete, ranking's visible effect is small (the order inside the hint) |
| B: the flight list as well | Ranking is visible everywhere | Hides earlier and cheaper flights (with `tokio`, the Feb 1–3 NRT flights drop to row 56); the list order depends on where you are |

**Ranking weights** (D19, Claude, assumption A13): origin 0.7 proximity / 0.3 volume, destination 0.2 / 0.8. Simple and explainable, but chosen by judgement, not measured. Revisit with real usage data.

### 4.5 Maps

**Android map library** (D8, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ MapLibre | Open source with no account, key or billing, so any reviewer sees a working map. The GPU draws **vector** tiles: smooth zoom and rotation, and crisp text and lines at any zoom and screen density. Actively maintained. | Needs an OpenGL ES 3.0 GPU (practically every phone of the last decade). Custom pill markers take more work. Map images come from OpenFreeMap's free server (no uptime guarantee). The map style differs from Apple Maps. Larger APK (native libraries). |
| Google Maps SDK (maps-compose) | The most polished and familiar; any composable can be a marker | **Requires a Google Cloud project with billing enabled and an API key.** The key can't be committed, so a reviewer without one sees a blank grey map. (Native mobile map loads are, to our knowledge, currently free of charge; the barrier is the mandatory billing account and key.) Needs Play services. |
| osmdroid | No key; lightweight | Draws **raster** (bitmap) tiles in a classic Android View: zooming between levels blurs or shows tile seams, gestures are less fluid, and it is dated, with known visual glitches. Compose only through an interop wrapper; slower-moving maintenance; OpenStreetMap's own tile servers discourage heavy app use. |

**Owner's reasoning:** Google Maps is ruled out by its mandatory billing account and key. osmdroid is ruled out by its known visual glitches. Its one advantage, being lightweight, doesn't matter here: we aren't targeting low-performance phones, so there's no reason to trade MapLibre's smoother, sharper, higher-resolution rendering for a smaller footprint.

**iOS map** (with D8)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Apple MapKit | Built in, free, no key; native look and gestures; draws geodesic route lines itself | Looks different from the Android map |
| MapLibre on iOS too | Identical styling on both apps | More work and less native feel on iOS |

### 4.6 Tooling and tests

**A test-only JVM target** (D9, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ `jvm()` target for shared tests | Fast, runs on any OS and in CI; in-memory SQLite; lets the oracle test run the original Java Double Metaphone | The JVM isn't a shipped platform, so the same tests should also run on the iOS simulator (Mac) to cover Kotlin/Native |
| Android host tests | Closer to a shipped platform | Slower, and awkward under AGP 9's new multiplatform plugin |

**Agent instructions file** (P1, owner)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ `CLAUDE.md` | Claude Code reads it automatically | Other AI tools (Codex, Cursor, …) look for `AGENTS.md` and would need to be pointed at it |
| `AGENTS.md` (+ a `CLAUDE.md` import) | Every tool reads the same file | One more indirection |

**Fuzzy thresholds** (S6–S10, Claude): a 4-letter gate, an edit budget of 1–2, Dice ≥ 0.55, and a 0.10 margin. They are explainable and covered by 87 table tests, but were tuned on 32 airports, so re-validate them on a larger dataset.


## 5. Known limitations

*Fill in at the end. Starting list:*

- Search doesn't transliterate non-Latin scripts (`東京`) and knows no nicknames (`NYC`, `LA`).
- Sound-alike places outside the data can match: `newark` → New York (JFK). The hint line makes this visible (DOMAIN_RULES S12).
- The passenger figures in `fixtures/airports.json` are approximate 2024 totals, recalled by an AI assistant and **not verified** against primary sources. They are good enough to rank airports, not to quote.
- Map tiles aren't cached offline; markers still render (allowed by the brief).
- Number formatting is en-US only (`25,000 pts`).
- Local HTTP only: cleartext is allowed for `localhost` / `10.0.2.2`, and physical devices need extra config.
- No cross-device sync (out of scope per the brief).
- *…*

## 6. Scalability

How the design behaves as the data grows. The per-layer table, with each layer's next step, is in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability), and the targets are in [REQUIREMENTS.md §11](REQUIREMENTS.md#11-scalability-non-functional-self-imposed).

- **Built for now:** in-memory filtering, a full cache replace, and individual map markers. All are well within budget at 1,000 flights.
- **Search is built to scale:** it works on airports, not flights. Prefix lookup is O(m) in a trie. Fuzzy candidates come from inverted trigram and phonetic indexes rather than a full scan. Ranking priorities are precomputed (ARCHITECTURE §4.4, §12).
- **Scaling paths documented, not built:** SQL-side filtering with indexes, incremental sync (ETag and deltas), marker clustering, and paged lists.
- **Measured numbers:** *fill in from the M6 benchmarks (TEST_PLAN §4.1).*

## 7. Checks performed

See [TEST_PLAN.md §5](TEST_PLAN.md#5-manual-qa-checklist) for the filled-in QA checklist for both platforms, and §4 for the performance and leak checks. Summarise here:

- Shared tests: *N tests, all passing* (`./gradlew :shared:jvmTest`, and `iosSimulatorArm64Test` on a Mac).
- Android: *device or emulator, API level, date*.
- iOS: *simulator model, iOS version, date*.

## 8. Third-party code: Double Metaphone

**What.** `shared/src/commonMain/kotlin/travel/roame/takehome/vendor/commonscodec/DoubleMetaphone.kt` is a Java → Kotlin translation of `org.apache.commons.codec.language.DoubleMetaphone` from **Apache Commons Codec 1.23.0** (Apache License 2.0). The algorithm is Lawrence Philips' Double Metaphone. It is credited in the file header and in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

**This is not our own implementation.** We copied the library's source and translated it so it runs in shared Kotlin. We did not design its 100+ branching pronunciation rules (for example "CH" before "AE", or the Germanic "SCH"), and we don't claim to understand every one of them. What we own and understand is:
- how we *use* it,
- how we *verified* it,
- the rest of the search engine around it.

**Why a translation and not a dependency.** No Kotlin Multiplatform library provides Double Metaphone. Commons Codec is a Java library: it runs on Android and the JVM, but not on iOS, which uses Kotlin/Native and can't run Java bytecode. Translating the source into common Kotlin gives both apps the same algorithm (decision D13).

**What changed in the translation** (also listed in the file header, as Apache-2.0 §4(b) requires):
- Java syntax → Kotlin. The rule order and every condition are unchanged.
- `maxCodeLen` is an immutable constructor parameter, so instances are thread-safe.
- The `StringEncoder` / `encode(Object)` plumbing is removed.
- `toUpperCase(Locale.ENGLISH)` → the locale-invariant `uppercase()`.
- Visibility is `internal`.

**How we know it's correct.** `DoubleMetaphoneOracleTest` (`shared/src/jvmTest`) uses the **original commons-codec jar as an answer key**. That jar is a test-only dependency and never ships in the apps. The test runs both implementations on:
- curated tricky words (`bacchus`, `schermerhorn`, `filipowicz`, …),
- every 1–3 letter string,
- 150,000 seeded generated words built from the letter groups that trigger special rules,

at code lengths 1, 4, 6 and 12, for primary and alternate codes. Every code must match exactly. `DoubleMetaphoneTest` (commonTest) also runs commons-codec's own published vectors on every target, including iOS.

**How the search engine uses it** (DOMAIN_RULES S8):
- code length 6 instead of the default 4, which gives fewer accidental collisions on long city names,
- both the primary and the alternate codes (`yohannesburg` matches Johannesburg's alternate `AHNSPR`),
- codes of only 2 characters count only against whole city names (`roam` → Rome, but not `sitty` → "Mexico City"),
- codes for the index are precomputed once.

**How to update it.** Re-translate from the newer Commons Codec release, bump the test dependency in `gradle/libs.versions.toml` to the same version, and run the oracle test.

## 9. How AI was used

*Describe honestly. Suggested structure:*

- **Planning:** Claude Code read the brief and profiled the fixture (counts, edge cases such as non-pattern IDs, the two Tokyo airports, "Mileage Plan" vs "MileagePlus", and antimeridian routes). It drafted these planning docs, which I reviewed and edited.
- **Implementation:** *where AI generated code: e.g. boilerplate, SQL, Compose/SwiftUI scaffolding.*
- **Verification:** *how output was checked: every rule has a test written or reviewed by me; diffs reviewed before commit; behaviour verified on both platforms via the QA checklist; anything I couldn't explain was rewritten.*
- **Process:** each functionality is reviewed by me before it is pushed (decision P2).

### AI usage log

| Date | Area | What AI did | How I verified |
|---|---|---|---|
| 2026‑10‑29 | Planning | Profiled the fixture and drafted the docs in `docs/` and `CLAUDE.md` | Re-read the brief line by line against REQUIREMENTS.md |
| 2026‑10‑30 | Docs | Added the working agreement, scalability sections, and this decision record; removed the deadline references | Reviewed in the review gate |
| 2026‑10‑30 | Search prototype | Prototyped the owner's search design (prefix + phonetic + n-gram) in Python against the fixture cities, using codes from the real commons-codec jar, and compared tiered vs blended mixing (114 cases) for the owner to choose | Test cases cover the owner's four kinds of mistyping, plus negatives |
| 2026‑10‑30 | Double Metaphone port | Translated commons-codec's `DoubleMetaphone.java` to Kotlin | Oracle test against the original jar (150k+ words), plus commons-codec's own vectors |
| 2026‑10‑30 | Search engine | Implemented the owner's design in Kotlin: the trie, trigram index, edit distance, ranking and tiered engine, with 54 tests | Tests pass on the JVM; the iOS klib compiles; owner review pending |
| 2026‑10‑30 | Docs | After owner review: corrected the decision attribution (brief vs owner vs Claude), and recorded D16 (device location) and D17 (ranking scope) | Owner review |
| 2026‑10‑30 | Docs | Wrote up every stack tradeoff from the planning conversation (§4), including the owner's reasoning for MapLibre | Owner review |
| 2026‑10‑30 | Docs | Explained SQLDelight vs Room for the owner's D3 decision; recorded Room and updated the persistence design (ARCHITECTURE §5) | Owner review |
| 2026‑10‑30 | `airports.json` | Recalled approximate 2024 passenger totals for the 32 airports | **Not verified**: labelled as approximate in the file and in §5 |
