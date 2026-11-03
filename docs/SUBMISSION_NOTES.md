# Submission notes

> **Working draft.** Keep this file current while building, and finalise it in Phase 7. Delete this note before submitting.

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
| D2 | Share the *presentation state* too (`ExplorerStore`: selection, map grouping, offline state), beyond the four layers the brief requires. The owner's reasoning: the bridging code should be smaller than writing the same state in both apps. | DATA-3, VIEW-1, SEL-2 | Owner's choice (Claude's recommendation over a view model per platform) | Built; selection and saved state come in Step 3.4 | 2026‑10‑30 |
| D3 | **Room** (Google's database library) on its **bundled SQLite**. The owner's reasoning: one identical database engine on every phone and on iOS makes behaviour, and bugs, reproducible across devices. Room/KSP support for the new Kotlin 2.4.21 was a known risk, and Step 0.2 found it works (D21). | DATA-2, DATA-4, DATA-5 | Owner's choice (over SQLDelight; Claude leaned Room) | Built; compatibility checked in Step 0.2 | 2026‑10‑30 |
| D4 | Store saved flights as full snapshots in their own table. The owner's reasoning: a design without copies is more bug-prone, and its storage advantage is too small to be worth that. | OFF-6, OFF-7 | Owner's choice (Claude's recommendation over a shared flight table with saved ids) | Built | 2026‑10‑31 |
| D5 | Replace the whole cache on each successful refresh | DATA-4, OFF-3 | Owner's choice (Claude's recommendation over writing only what changed) | Built | 2026‑10‑31 |
| D6 | Filter in memory, off the main thread, with `mapLatest` so the latest input wins. Flights are sorted once when new data arrives, because filtering keeps the order. | PERF-2, PERF-3 | Owner's choice (Claude's recommendation over SQL queries) | Built | 2026‑10‑31 |
| D7 | A hand-written Kotlin→Swift bridge | DATA-3 | Owner's choice (Claude's recommendation over SKIE, KMP-NativeCoroutines and Swift export) | Decided; not built yet | 2026‑10‑31 |
| D8 | **MapLibre** is the Android map library: open source, no API key, GPU-drawn vector maps, with map tiles from OpenFreeMap's free public server (which, like Apple for MapKit, sees which map areas are viewed). Google Maps was rejected for its mandatory billing account and key; osmdroid for its raster rendering and visual glitches (§4.5). iOS uses Apple's built-in MapKit. | MAP-1 – MAP-4 | Owner's choice (Claude's recommendation over Google Maps and osmdroid) | Decided | 2026‑10‑30 |
| D9 | A test-only `jvm()` target in `:shared` | TEST-1 – TEST-5 | Owner's choice (Claude's recommendation over Android host tests) | Built | 2026‑10‑31 |
| D10 | One app-scoped store per process | SEL-2, VIEW-1 | Owner's choice (Claude's recommendation over a store per screen) | Built | 2026‑11‑01 |
| P1 | Keep the agent guide as `CLAUDE.md` (no `AGENTS.md`) | — | Owner | Decided | 2026‑10‑30 |
| P2 | One functionality at a time; the owner reviews it; push only after acceptance | — | Owner | Decided | 2026‑10‑30 |
| P3 | Ask the owner before significant architectural decisions, and record them here | — | Owner | Decided | 2026‑10‑30 |
| D11 | **Tiered search.** Tier 1 walks a prefix tree node by node (n → e → w) in O(m) and branches into the full names under it. Tier 2 (fuzzy) runs only when Tier 1 finds nothing. | SRCH-1 – SRCH-4 | **Owner**: the prefix-tree traversal is the owner's spec; tiered fallback was the owner's choice over a blended score | Built | 2026‑10‑30 |
| D12 | **Mixed fuzzy matching**: phonetic and n-gram matching plus accent folding. It covers the owner's hypothesis that people mistype a place in four ways: (1) a typo, (2) knowing the sound but not the spelling, (3) roughly knowing the spelling, (4) accents. Claude added an edit-distance typo signal alongside. | SRCH-5 (beyond the brief) | **Owner** (design and hypothesis); Claude (the edit-distance signal) | Built | 2026‑10‑30 |
| D13 | **Use Double Metaphone from an existing library, not our own implementation.** No library runs on iOS, so Apache Commons Codec's version is translated to Kotlin, credited, and verified against the original (§8) | SRCH-6 | **Owner** (use a library and disclose it); Claude proposed the translate-and-verify route, which was the owner's choice | Built | 2026‑10‑30 |
| D14 | A **"Showing Sydney (SYD)" hint** under a field when fuzzy matching was used | SRCH-8 | Owner's choice (the option Claude recommended) | Engine and store built; UI pending | 2026‑10‑30 |
| D15 | **Rank matches by the user's location and annual airport volume.** Volume lives in a separate new file, and a copy ships inside the app, so ranking is the same online and offline. `flights.json` is never edited. An airport with no figure falls back to the original data. Location only counts in the origin field (D19). | SRCH-7 | **Owner** (the idea, the separate file, the fallback, shipping it inside the app); Claude (the file format, and reading "fallback" as flight counts, A12) | Built | 2026‑11‑01 |
| D16 | **Location comes from the device**: Fused Location Provider (Android) and Core Location (iOS), approximate accuracy, requested only when the user taps "Use my location". Without it, ranking uses volume only. Nothing is sent to a third party. This replaces Claude's earlier geo-IP proposal. | SRCH-7 | **Owner** (suggested on-device location instead of geo-IP) | Decided; platform code in Phases 4 and 5 | 2026‑10‑30 |
| D17 | **Ranking only orders the matched airports** (the hint line and the suggestions, D20). The flight list keeps its date → points order (O1). | SRCH-7 | Owner's choice (option A) | Built | 2026‑10‑30 |
| D18 | Search runs on **airports, not flights**, behind indexes. The owner's reasoning: the brief names Roame as its functional reference, and Roame's website finds the place first and then searches flights for it. The brief's fields also "match airport codes or city names". Scale is a side benefit, not the reason. | SRCH-1, SRCH-2, SCALE-1 | Owner's choice (follow Roame; Claude's original design) | Built | 2026‑11‑01 |
| D19 | Ranking weights: origin 0.6 proximity / 0.4 volume, destination 0 / 1, and proximity halves every 250 km. The owner's reasoning: your location says nothing about where you're flying to. | SRCH-7 | Owner's choice (the destination reasoning, and asking for public sources); Claude found the sources and proposed the numbers (A13) | Built | 2026‑11‑01 |
| D20 | **A suggestions dropdown under each search field.** Matching airports from the data appear and change with every keystroke, ranked by P6. Tapping one picks that airport. When two or more matches share a city, one extra row picks them all, following Roame. The hint from D14 stays. | SRCH-9 | **Owner** (the idea, using only what's in the data, and the city row); Claude (5 rows and the tap behaviour, A14) | Store built; UI in Phases 4 and 5 | 2026‑11‑01 |
| D21 | Room **2.8.5** with `sqlite-bundled` **2.6.2**, not Room 3. The starter builds an `iosX64` target. Room 3.0.3 and `sqlite-bundled` 2.7.0 and later don't publish one, and 2.6.2 is the version Room 2.8.5 is built against. The owner's reasoning: this is a take-home, so the newer line's benefits, which are mostly for later development, wouldn't be used. | DATA-2 | Owner's choice (Claude's recommendation over Room 3) | Build setup done; no database code yet | 2026‑11‑02 |
| D22 | Android 8 (API 26) is the minimum: `minSdk` 26, raised from the starter's 23. The owner's reasoning: the brief doesn't ask for Android 6–7.1, and they're about 10 years old. `LocalDate` needs API 26 or extra setup. | — | **Owner** | Built | 2026‑11‑02 |
| D23 | MapLibre's **default package, which uses Vulkan**. Every Step 0.3 check passed on it. Claude recommended the OpenGL package, for its wider device support and smaller size. | MAP-1 | Owner's choice (over Claude's recommendation) | Proven on a scratch branch; not built yet | 2026‑11‑02 |
| D24 | On Android, a map that can't load shows **a message with Retry**, and flights stay available in the list. This only affects a map that has never loaded online. | OFF-9 | **Owner** (over drawing markers on a plain background) | Decided; built in Step 4.5 | 2026‑11‑02 |
| D25 | **No QA switches on the local API.** The planned `--mode` and `--fixture` options were built in Step 1.2, then removed before being pushed. The brief doesn't ask for them, and no app code or test depends on them. Automated tests fake empty and failed responses. By hand, stop the API or temporarily edit the fixture. | OFF-5 | **Owner** (over Claude's mild preference to keep them) | Removed | 2026‑11‑02 |

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
| A11 | Whether an extra API surface is allowed | Not needed: the server only has the required `GET /flights` (D25) | — |
| A12 | What "fall back to the original data" means when an airport has no passenger figure | Volume = each airport's number of flights in `flights.json` | P4 |
| A13 | How much location vs popularity should count | Origin 0.6 / 0.4, destination 0 / 1, proximity halves every 250 km (D19). Borrowed from public sources, because none publishes a split for airports | P2, P5 |
| A14 | How many suggestions to show, and what tapping one does | At most 5 rows, counting city rows. Tapping picks exactly what the row names, and the field shows "Tokyo (HND)" or "Tokyo (all airports)" until it is edited. | S14 |
| A15 | How long before the Android map says it can't load | 10 seconds without its style. MapLibre's failure event isn't reliable offline, so the app uses its own timer | UI_SPEC §4 |
| A16 | What a connection lost partway through a response counts as | `Offline`. The app can't reach the server, and the partial data is discarded | R9 |
| A17 | What a valid response that can't be saved counts as | A failed refresh, reported as `Unknown`. The old cache stays, because the write is one transaction | R5 |
| A18 | What a date or points limit past the data means after a refresh | It returns to its default, because it filters nothing. The default then follows the data | F8 |
| A19 | How compact points show a value that isn't a multiple of 500 | It rounds to one decimal, with halves rounding up. Every fixture value is a multiple of 500, so this never shows today | T2 |
| A20 | What happens to a date from an app that isn't ISO `yyyy-MM-dd` | It's ignored, because filtering relies on valid dates. The date pickers can't produce one | F3 |
| A21 | What happens to a picked airport that a refresh removes | The field goes back to searching its text, for good, like a dropped filter value (F8) | S14 |

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

**One app-scoped store** (D10, owner's choice: one per process)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ One store per process | Survives Android rotation; one source of truth; nothing per screen to leak | State lives in memory while the app runs (flights take 0.4 MB today and 35 MB at 100,000, measured on the JVM). If Android closes the app in the background, the search text, filters and selection reset; cached and saved flights survive in the DB. Skips Android's usual `ViewModel`. |
| A store per screen | Tighter scoping; can free memory when a screen closes; the `ViewModel` pattern Android developers expect | Risk of list and map holding different state; more objects to clean up correctly |

**Kotlin → Swift bridge** (D7, owner's choice: hand-written)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Hand-written `observe()` wrapper | No extra dependency; no compiler plugin tied to Kotlin 2.4.21 | Boilerplate per intent; Kotlin sealed types appear in Swift as class hierarchies (clumsy `switch`) |
| SKIE | Swift enums and `AsyncSequence`; much nicer Swift code | Must support the exact Kotlin version (2.4.21 is brand new) |
| KMP-NativeCoroutines | Swift async/await over Flows | Compiler plugin with the same version risk |
| Swift export (built into Kotlin) | No third-party library; maps Flows to Swift's `AsyncSequence` | Alpha as of Kotlin 2.4.0, according to community write-ups (not confirmed in official docs), so it may change |

### 4.2 Data and networking

**Local API server** (D1, owner's choice: Ktor)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Ktor module in the Gradle build (`./gradlew :server:run`) | One language and one build; reuses the JDK Gradle already downloads; serves the fixture byte for byte | Gradle startup is slow (tens of seconds on the first run); heavier than a script |
| Node or Python script | ~10 lines; starts instantly | Reviewers need that runtime installed; a second language in the repo |

The server uses Ktor's CIO engine (Claude), because it only needs plain HTTP.

**HTTP client and JSON** (ARCHITECTURE §10, Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Ktor client + kotlinx.serialization | The standard KMP pair: one implementation for both apps; `MockEngine` makes failure tests easy | Another library family to keep compatible with Kotlin 2.4.21 |
| Platform clients (OkHttp / URLSession) behind expect/actual | The most native networking | Networking would be written twice, against the brief's "once in KMP" |

**On-device database** (D3, owner's choice: Room)

Both options are libraries on top of **SQLite**, the database engine built into Android and iOS. Both meet every persistence requirement: atomic cache replacement, saving without duplicates, survival across restarts, reactive updates, work off the main thread, and in-memory tests on the JVM.

| Option | Strengths here | Weaknesses here |
|---|---|---|
| SQLDelight (Cash App) | Multiplatform since ~2019, very mature. **You write the SQL** and it generates type-safe Kotlin, so every query is visible and easy to explain. Smallest footprint (uses the phone's own SQLite). One Gradle plugin; simple migrations. | Uses the **phone's built-in SQLite**: Android 8 (our `minSdk 26`) ships SQLite 3.18, which has no "insert or update" syntax (we work around it with two statements), and the SQLite version differs by device and from iOS. The iOS app must link `sqlite3`. Its plugin's support for AGP 9's new multiplatform setup is unverified. |
| ✅ Room KMP (Google), with its bundled SQLite | Android's standard, so most reviewers know it. **You write Kotlin classes** with annotations. `@Upsert` maps directly onto OFF-6. It can **bundle its own modern SQLite**, so both apps and every Android version run the identical database, which also keeps modern features (full-text search, upsert) available for scaling. | Multiplatform only since 2025. More build machinery: the KSP code generator for 5 targets, plus database-constructor boilerplate for iOS. Generated code is hidden. The bundled SQLite adds 1.16–1.31 MB per CPU architecture (measured). Room and its SQLite library must be paired carefully to keep every target (D21). |
| JSON files / DataStore | No library; trivial for 1,000 flights | We'd hand-build atomic writes, duplicate checks and change notifications, which is exactly what the databases provide |

Neither database is strictly better. Room's edge here is one consistent, modern SQLite on both apps and every Android version, plus reviewer familiarity. SQLDelight's edge is fewer moving parts, visible SQL, the longest multiplatform track record, and the smallest app. Either sits behind our repositories, so switching later only touches the data layer.

**Owner's reasoning for Room:** with SQLDelight, each phone runs its own built-in SQLite (a 2015 version on Android 6 when this was decided, and still SQLite 3.18 on Android 8 after D22; a newer one on iOS), so a database issue could depend on the device and be hard to reproduce. Room's bundled SQLite is the same build everywhere, so what works, or breaks, on one device behaves the same on all of them. SQLDelight's fully visible SQL would make reading queries easier, but engine differences between devices are the harder kind of problem to debug. The accepted cost is Room's build setup and its version risk with Kotlin 2.4.21. PLAN Step 0.2 checked that risk: Room works on this toolchain, and the JVM, the iOS simulator and the Android emulator all reported the same SQLite 3.50.1 (§7, D21).

**Saved flights as snapshots** (D4, owner's choice: full copies)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A full copy per saved flight, in its own table | Saved flights survive being removed from the API (OFF-7) and cache wipes | The data is duplicated between the cache and saved flights, so a refresh must update both in one transaction (V5) |
| A table of saved ids joined to the cache | No duplication | Loses saved flights that disappear from the API, which breaks OFF-7 |
| One flight table plus saved ids, keeping saved rows on refresh | No duplicated details, so details can't disagree; about 66 bytes per saved flight instead of about 164 (measured with 1,000 saved, using hand-written tables in the system's SQLite, not Room) | A refresh bug could delete saved flights; every flight-list query must exclude delisted rows; removal takes two steps. The saving is at most 96 KB in the fixture, and each phone has its own database, so the number of users doesn't change it |

**Cache refresh** (D5, owner's choice: replace everything)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Replace everything in one transaction | Simple and atomic; matches an API that always returns the full list | Rewrites every row on every refresh, and the app recalculates results and rebuilds its search index even when nothing changed. Measured database time: 1 ms at 1,000 rows, 11 ms at 10,000, 136 ms at 100,000 |
| A diff (upsert changed rows, delete missing ones) | Writes less when little has changed, and can skip recalculating when nothing has | More code and edge cases. It still reads and compares every row, so it showed no real gain: 1 ms, 11 ms and 130 ms with 1% of rows changed |

Both timings are medians of 5 runs on a Mac, using Python and the system's SQLite rather than Room on a phone, so a phone will be slower. Sizes above 1,000 repeat the fixture.

**Filtering** (D6, owner's choice: in memory)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ In memory, off the main thread, latest input wins, sorted once per data change | Pure functions that are easy to test at the boundaries. Each input change with no filters takes 0.1 ms at 1,000 flights, 1 ms at 10,000 and 9.6 ms at 100,000 (35 ms at 100,000 if it sorted every time). | The whole dataset sits in memory: 0.4 MB at 1,000 flights, 3.4 MB at 10,000, 35 MB at 100,000. Sorting takes 69 ms per data change at 100,000. At ~100k flights it moves to SQL (ARCHITECTURE §12) |
| SQL `WHERE` clauses | Scales to very large data | Harder to compose and to test, and needs the database for every unit test. Took 0.9 ms, 10 ms and 124 ms at those sizes, without ordering each marker's flights |

In-memory timings are medians on a Mac in Java and exclude text search. SQL timings are medians in Python with the system's SQLite, so the comparison isn't exact. A phone will be slower, and sizes above 1,000 repeat the fixture.

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
| A suggestions dropdown | The best UX, and where ranking would shine | Much more UI work on both platforms. The owner later added one alongside the hint (D20) |
| Silent | Nothing to build | The user can't tell why `sidnee` shows Sydney flights |

**What search runs on** (D18, owner's choice: airports, following Roame)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Airports (32 here), behind indexes; flights are then filtered by airport code | Matches the brief ("match airport codes or city names") and Roame's website. The prefix tree, hint, ranking and suggestions all work on airports. One keystroke takes at most 0.006 ms at 1,000 flights and 0.56 ms at 100,000. | Text search only covers places (cabin, program and the rest are filters anyway) |
| Each flight's text | No separate index. Also fast enough at 1,000 flights (at most 0.35 ms per keystroke). | 49 ms per keystroke at 100,000 flights. The prefix tree would no longer drive search (D11), and the hint, ranking and suggestions would have to turn flights back into airports. |

Timings are the slowest of 6 queries in the destination field, and cover finding the matching flights only. They were measured on a Mac in Java with a throwaway test, which is the only place the flight-by-flight design exists. Both designs picked the same airports. A phone will be slower.

**How Roame's website does it.** This comes from reading the scripts its site sends to browsers on 2026‑11‑01. Roame hasn't documented it, its mobile app may differ, and no Roame code was copied.

- Each field runs a matcher in the browser over a built-in list of places. The search then carries the chosen place (its code and type), not the typed text.
- Each step runs only if the one before found nothing: exact name or code, prefix, word start (3+ letters), then fuzzy (4+ letters, using Fuse.js). Our tiers have the same shape (S4, S6).
- It shows at most 5 airport rows, like ours (A14), ordered by match quality and then a per-airport importance score.
- It offers one row for all of a city's airports, placed first, such as "Chicago (All Airports)". We follow this with "Tokyo (all airports)" (S14).

We differ on purpose in two places: phonetic matching (D12) and a prefix tree instead of a sorted list (D11). Roame also offers regions, countries and "Anywhere", which neither the brief nor the fixture has.

### 4.4 Ranking and location

**Where airport volume comes from** (D15, owner; file format by Claude)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A separate `fixtures/airports.json` with a copy shipped inside the app, falling back to flight counts from `flights.json` | The provided data stays untouched; the same order online and offline; no extra endpoint or table; Roame's website also ships its airport data with its scripts | The figures are approximate and unverified; updating them needs a new app build; the copy must match the file, which a test checks |
| Fetch from `GET /airports` and save a copy in the database | The figures can change without an app build | An endpoint the brief doesn't ask for, plus a client, a table and tests, all for yearly figures |
| Add a field to `flights.json` | One file | Changes the provided data, which the API must serve verbatim |

**Where the user's location comes from** (D16, owner)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ The device: Fused Location Provider (Android) and Core Location (iOS), approximate accuracy | Accurate; works offline (GPS needs no internet); nothing sent to a third party; no extra endpoint | A permission prompt; location code on each platform. Fused needs Google Play services, so it won't work on devices without them and the emulator needs a Play image. Android's plain `LocationManager` would avoid that dependency, but it is lower-level and less battery-efficient. |
| Geo-IP through our local API (Claude's earlier idea) | No permission prompt | Locally it finds the developer's machine, not the user. City-level only; wrong behind a VPN; needs an outside geo-IP service |
| The app calls a public geo-IP service | No permission prompt | Sends the device's IP to a third party; rate limits |

**What ranking reorders** (D17, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ A: only the matched airports (the hint and the suggestions, D20) | The flight list stays in date → points order, so earlier and cheaper flights are never pushed down | Ranking is visible only in the order of the hint and the suggestions |
| B: the flight list as well | Ranking is visible everywhere | Hides earlier and cheaper flights (with `tokio`, the Feb 1–3 NRT flights drop to row 56); the list order depends on where you are |

**Ranking weights** (D19, owner's choice; assumption A13). Claude first chose origin 0.7 / 0.3, destination 0.2 / 0.8 and a 693 km halving distance by judgement. The owner rejected using location for destinations and asked for public sources, which gave these values:

| Setting | Value | Based on |
|---|---|---|
| Destination | 0 proximity / 1 volume | Amadeus's keyword search sorts by traveler count. Duffel ranks by popularity. AirLabs' flight-app guide uses location for departure and popularity for destination. Google's patent infers destinations from flights out of the origin. Roame's website uses an importance score with no location. The closest exception is Booking.com, which requires a country code but takes no user coordinates. |
| Origin | 0.6 proximity / 0.4 volume | Photon's default, read from its code: the same formula shape, and the same volume-only fallback. It's general place search rather than flights. Amadeus's nearby-airport search also blends distance and traffic, without publishing the formula. Google's patent instead filters by distance and then picks the airport with the most flights. Roame's website doesn't use location. |
| Proximity halves every | 250 km | The widest range in Google's patent. Amadeus's nearby search stops at 500 km, where proximity is down to a quarter. |

**Effect on the fixture**, over every query typed letter by letter, with the user placed at each of the 32 airports:
- Destination: 27 of 640 cases change order, all on 1–2 letter queries.
- Origin: 4 of 64 cases change. Only `s` and `y` match more than one origin airport.
- The origin top row matches the rule in Google's patent (at 250 km) in 63 of 64 cases, up from 60.

These are sensible defaults rather than proof. No source publishes a split for airports, Photon isn't a flight product, the patent doesn't show what Google Flights does today, and there's no real usage data. Cost is unchanged at 10× and 100× the flights: one priority per airport, computed once. The numbers should be re-checked on a larger airport list (as with S12).

### 4.5 Maps

**Android map library** (D8, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ MapLibre | Open source with no account, key or billing, so any reviewer sees a working map. The GPU draws **vector** tiles: smooth zoom and rotation, and crisp text and lines at any zoom and screen density. Actively maintained. | Needs a Vulkan 1.0 GPU with its default package (D23). Pill markers are drawn through map layers rather than Compose, which Step 0.3 found straightforward. Map images come from OpenFreeMap's free server (no uptime guarantee). The map style differs from Apple Maps. Larger APK (native libraries). |
| Google Maps SDK (maps-compose) | The most polished and familiar; any composable can be a marker | **Requires a Google Cloud project with billing enabled and an API key.** The key can't be committed, so a reviewer without one sees a blank grey map. (Native mobile map loads are, to our knowledge, currently free of charge; the barrier is the mandatory billing account and key.) Needs Play services. |
| osmdroid | No key; lightweight | Draws **raster** (bitmap) tiles in a classic Android View: zooming between levels blurs or shows tile seams, gestures are less fluid, and it is dated, with known visual glitches. Compose only through an interop wrapper; slower-moving maintenance; OpenStreetMap's own tile servers discourage heavy app use. |

**Owner's reasoning:** Google Maps is ruled out by its mandatory billing account and key. osmdroid is ruled out by its known visual glitches. Its one advantage, being lightweight, doesn't matter here: we aren't targeting low-performance phones, so there's no reason to trade MapLibre's smoother, sharper, higher-resolution rendering for a smaller footprint.

**iOS map** (with D8)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ Apple MapKit | Built in, free, no key; native look and gestures; draws geodesic route lines itself | Looks different from the Android map |
| MapLibre on iOS too | Identical styling on both apps | More work and less native feel on iOS |

### 4.6 Tooling and tests

**A test-only JVM target** (D9, owner's choice)

| Option | Strengths here | Weaknesses here |
|---|---|---|
| ✅ `jvm()` target for shared tests | Runs on any OS and in CI; in-memory SQLite; lets the oracle test run the original Java Double Metaphone | The JVM isn't a shipped platform, so the same tests should also run on the iOS simulator (Mac) to cover Kotlin/Native. No tests run on Android itself yet. It isn't meaningfully faster than the iOS simulator on a Mac: 3–4 s vs 3–6 s after a small code change |
| Android host tests | Closer to a shipped platform | Expected to be slower, and awkward under AGP 9's new multiplatform plugin (neither was measured) |

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
- The passenger figures in `fixtures/airports.json` are approximate 2024 totals, recalled by an AI assistant and **not verified** against primary sources. They are good enough to rank airports, not to quote. Their order broadly agrees with public data, where 1.0 means an identical order: 0.82 with OpenTravelData's older passenger counts, 0.72 with route counts in Algolia's airport dataset, and 0.44 with OpenTravelData's flight-network rank. The fallback's flight counts barely track them (about 0.1), because the fixture's flights are fictional.
- Map areas can't be downloaded ahead of time (the brief doesn't require it). The Android map works offline from what it has already loaded. If it has never loaded online, it shows a message with Retry (D24).
- The Android map needs Vulkan 1.0 (D23).
- On iOS, the 5 s connect limit has no effect, because Ktor's Darwin engine ignores it. A stalled connection gives up at the 15 s request limit, with the same error (R10).
- Number formatting is en-US only (`25,000 pts`).
- Android 8 (API 26) and up only (D22). The brief doesn't ask for older versions.
- Local HTTP only: cleartext is allowed for `localhost` / `10.0.2.2`, and physical devices need extra config.
- No cross-device sync (out of scope per the brief).
- *…*

## 6. Scalability

How the design behaves as the data grows. The per-layer table, with each layer's next step, is in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability), and the targets are in [REQUIREMENTS.md §11](REQUIREMENTS.md#11-scalability-non-functional-self-imposed).

- **Built for now:** in-memory filtering, a full cache replace, and individual map markers. All are well within budget at 1,000 flights.
- **Search is built to scale:** it works on airports, not flights. Prefix lookup is O(m) in a trie. Fuzzy candidates come from inverted trigram and phonetic indexes rather than a full scan. Ranking priorities are precomputed (ARCHITECTURE §4.4, §12).
- **Scaling paths documented, not built:** SQL-side filtering with indexes, incremental sync (ETag and deltas), marker clustering, and paged lists.
- **Measured numbers:** *fill in from the Phase 6 benchmarks (TEST_PLAN §4.1).*

## 7. Checks performed

See [TEST_PLAN.md §5](TEST_PLAN.md#5-manual-qa-checklist) for the filled-in QA checklist for both platforms, and §4 for the performance and leak checks. Summarise here:

- Shared tests: *N tests, all passing* (`./gradlew :shared:jvmTest`, and `iosSimulatorArm64Test` on a Mac).
- Android: *device or emulator, API level, date*.
- iOS: *simulator model, iOS version, date*.
- Room and KSP on this toolchain (Step 0.2, 2026‑11‑01): a throwaway database passed 7 tests on the JVM and the iOS simulator, then ran inside the real apps on the `Medium_Phone_API_37.0` emulator and the iPhone 18 Pro simulator. All reported SQLite 3.50.1. It didn't run on Android below API 37, a physical iPhone or an Intel Mac, though `iosArm64` and `iosX64` compile and link.
- MapLibre in Compose (Step 0.3, 2026‑11‑02, on the `Medium_Phone_API_37.0` emulator, code on the unmerged local branch `scratch/maplibre-probe`):
  - **Worked, on both MapLibre graphics packages:** pill markers with text, a San Francisco to Tokyo route across the antimeridian, tapping markers, panning, pinch and double-tap zoom, fitting the route, the selected pill on top of a nearby one, backgrounding, rotation, and a full map offline once it had loaded online. Tiles loaded over OkHttp 5.5.1, although MapLibre is built against 4.12.
  - **Limits of this check:** pinch and double tap were two-finger events sent to the map view from inside the app, because `adb` can't send multi-touch. Pan and marker taps were real `adb` input. The emulator had no window and rendered in software, so this says nothing about speed, and its "isn't responding" dialogs came from that load.
  - **Didn't work:** if the map has never loaded online, it stays blank with no markers. A fallback style reported success but never drew, and MapLibre's failure event fired in one run and not the next.
  - **Cost, with the chosen Vulkan package:** the debug APK grew from 16.4 MB to 69.9 MB, because it holds all four CPU architectures. Each one's library is 9.2–13.0 MB.
- Fetching and failures (Step 2.1, 2026‑11‑02): real failing sockets through OkHttp on the JVM and through Darwin in the iPhone 18 Pro simulator. The results are in ARCHITECTURE §6.3. **Not yet checked:** airplane mode, and OkHttp on an Android device (QA Q16 and Q18).
- Flight cache (Step 2.2, 2026‑11‑02): a real Room database on the JVM and the iPhone 18 Pro simulator, in memory and on files, including the file the iOS app really uses. **Not yet checked:** the Android database file, which first runs in an app in Step 3.6.
- Saved flights (Step 2.3, 2026‑11‑02): the same real database, including failed writes that SQLite itself rejects. A failure while updating saved flights rolls the cache back too.
- Filter rules (Step 3.1, 2026‑11‑02): counts and full sort orders on the real fixture match a separate Python script. These are shared rules only; no screen uses them yet.
- Map grouping and route lines (Step 3.2, 2026‑11‑02): marker counts and midpoints match a separate Python script, and all 192 fixture routes are continuous, take the short way and have the right length. No map draws them yet. MapLibre drew an unwrapped route correctly in Step 0.3.
- The store (Step 3.3, 2026‑11‑02): the real fixture through the whole app graph, with only the network faked, gives the counts a separate Python script gives. The full JVM suite then passed 22 runs in a row with the CPU deliberately overloaded. **Not yet checked:** the store inside either app, which first happens in Step 3.6 on iOS and Step 4.1 on Android.
- Selection and saved state (Step 3.4, 2026‑11‑02): store tests with fakes, plus saving and removing through a real database after a failed refresh. No screen shows them yet.
- Search in the store (Step 3.5, 2026‑11‑02): on the real fixture through the whole app graph, `tokyo`, picks, `nrt`, `new york` and `sydeny` give the counts a separate Python script gives. The shipped passenger figures equal `fixtures/airports.json`. No screen shows search yet, and no device location is read yet.

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

**How strong that check is** (measured 2026‑10‑31 with JaCoCo, on the JVM):

| What was measured | Lines | Branches |
|---|---|---|
| The original library, under the oracle test | 98.2% | 98.2% |
| Our port, under the oracle test | 99.1% | 98.7% |
| Our port, under only the tests that also run on iOS | 71.9% | 55.8% |

- We also planted 30 random small mistakes in a throwaway copy of the port, one at a time. The oracle test caught all 30. The tests that also run on iOS caught 4. This is a sample, not proof.
- Of the missed code we read, some is an unused helper or null handling, and some can never run. For example, the `WITZ` check follows a `W` check. We didn't prove this for every missed line.
- The port matches the original exactly, so it would share any bugs the original has.
- The lower iOS numbers are acceptable. Both apps use the same Kotlin source, this code only handles letters and strings, and search folds accents before calling it. Claude withdrew its suggestion to add more iOS checks for it after the owner questioned the need.

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
| 2026‑11‑01 | D18 research | Timed both search designs with a throwaway test, and read Roame's public pages and website scripts to see how its search fields work | Timings were run on the owner's Mac, and both designs picked the same airports on 6 queries. Roame's behaviour comes from reading its minified scripts: not run, not confirmed by Roame, and its mobile app may differ |
| 2026‑11‑01 | D19 research | Read the code and specs of Photon, Pelias and Amadeus, Google's patent and vendor docs; measured how the weights change the fixture's results; compared the from-memory passenger figures with OpenTravelData and Algolia's airport dataset | Used Photon's code rather than its docs. Three new tests failed on the old numbers before the change. Vendor pages are self-reported, and the patent doesn't show what Google Flights does |
| 2026‑11‑01 | Build plan | Rewrote `docs/PLAN.md` into 37 reviewable steps, and found four small design gaps (PLAN §6) | A script confirmed that all 72 requirement IDs, all 78 rule IDs and every QA row appear in a step. Owner approved on 2026‑11‑01, after choosing to ship the passenger figures inside the app |
| 2026‑11‑01 | Step 0.2 | Chose library versions from official docs and each library's published targets, wrote a throwaway Room database, and ran it on every available platform | Test results, the on-screen text in both real apps, and the database file pulled from the emulator and read with Python's `sqlite3`. A first pick of `sqlite-bundled` 2.7.1, taken from Google's Room 3 page, didn't resolve for `iosX64` and was corrected to 2.6.2 |
| 2026‑11‑02 | Step 0.3 | Wrote a throwaway MapLibre map, compared its two graphics packages, tested gestures, offline use and rotation, and tried an offline fallback | Screenshots, the camera position read from the screen, and pixel colours. Those colours showed the fallback never drew, even though its status said it had loaded, so it's reported as failed |
| 2026‑11‑02 | Local API | Wrote the `:server` module and measured it at 1×, 10× and 100× the fixture | `curl` and `diff` against the fixture, including from a clean copy of the repo. On copies of the fixture: a missing file stops startup, `PORT` works, and edits appear on the next request |
| 2026‑11‑02 | Step 1.2, then removed | Built the `--mode` and `--fixture` switches the plan listed, without first asking whether the project needed them. The owner questioned them, and they were removed before being pushed (D25) | `curl` confirmed each mode while they existed. After removal, `Main.kt` matches the accepted Step 1.1 version exactly, and `curl` and `diff` confirm the response still matches the fixture |
| 2026‑11‑02 | Step 2.1 | Wrote the fetch and decode code and its tests. Probed real failing sockets through OkHttp and Darwin before writing the failure mapping | The probe corrected the design: timeouts must be checked first, and JSON errors arrive as `ContentConvertException`. 16 deliberate bugs were put in one at a time: tests caught 15, and the 16th showed a branch that could never run, which was deleted. Airplane mode isn't tested yet (QA Q16 and Q18) |
| 2026‑11‑02 | Step 2.2 | Wrote the Room database, the repository and their tests, and measured them at 1×, 10× and 100× | 16 deliberate bugs: tests caught 15. The miss, a read without its transaction, was then forced on a real file (36 of 373 observed results mixed two refreshes, 0 of 394 with the transaction) and became a test that failed in 3 of 3 runs without it |
| 2026‑11‑02 | Step 2.3 | Wrote the saved-flights table, DAO, repository and tests. Replaced the plan's per-flight update loop with one SQL statement, and made both flight tables share one column definition | 14 deliberate bugs, all caught, including running V5 outside the transaction. The measured gain from the single statement is small at this size: at 1,000 flights either approach takes about a millisecond or less |
| 2026‑11‑02 | Step 3.1 | Wrote the filter, ordering and active-filter rules and their tests, and measured them at 1×, 10× and 100× | Expected fixture counts and sort orders came from a separate Python script, not from the Kotlin code. 35 deliberate bugs: tests caught 34. The miss was a real gap (the O2 test's ids sorted the same way as its dates), so the test data was fixed and it now catches that bug |
| 2026‑11‑02 | Step 3.2 | Wrote the marker grouping, route lines and points formatting and their tests, and measured them at 1×, 10× and 100× | Expected marker counts and route midpoints came from a separate Python script using a different formula. 23 deliberate bugs: tests caught 21. One miss was a gap in the test data, now fixed. The other showed that a line of the unwrapping code couldn't change any result, so that code was simplified |
| 2026‑11‑02 | Step 3.3 | Wrote the store, its state, the reducer and the app graph and their tests, and measured them at 1×, 10× and 100× | 29 deliberate bugs: tests caught 27, and the 2 misses were gaps that were then closed. Those runs also exposed three flaky tests of Claude's own, including one from Step 2.1: assertions made a moment too early, a test socket closed by the garbage collector, and a database closed while a read was still running, which crashed the test process. All three were reproduced and fixed, then checked with 22 clean runs under load |
| 2026‑11‑02 | Step 3.4 | Added selection, saved state, pending writes and messages to the store, and their tests | 25 deliberate bugs: tests caught 23, and the 2 misses were gaps that were then closed. One needed a selection made at the exact moment an update finishes, which the test recreates by selecting from inside the reducer. Removed a branch nothing used |
| 2026‑11‑02 | Step 3.5 | Connected search to the store: the shipped passenger figures (generated from `fixtures/airports.json`, not typed), suggestions, picks, the hint and location ranking, with tests | Expected counts on the real fixture came from a separate Python script. 24 deliberate bugs: tests caught 20. Three misses were gaps that were then closed; the fourth showed a special case that couldn't change any result, so it was removed. One test expectation of Claude's was wrong (an exact code ranks first, S5), and was corrected rather than the code |
