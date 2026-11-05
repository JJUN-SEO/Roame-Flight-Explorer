# Requirements & acceptance criteria

Source: the *Flight Explorer* take-home brief (4 pages). This file lists every requirement in the brief once and gives each an ID. Use the IDs in commits, tests and the submission notes. Where the brief is ambiguous, [DOMAIN_RULES.md](DOMAIN_RULES.md) defines the exact behaviour; the **Rules** column links to it.

**Status:** ⬜ not started · 🟨 in progress · ✅ done (verified on both platforms where relevant)

## 0. Ground rules from the brief

- Build two native UIs: **SwiftUI** on iOS and **Kotlin + Jetpack Compose** on Android. Compose Multiplatform for the iOS UI is only acceptable without a Mac, and SwiftUI is *strongly preferred*.
- Back both apps with one shared **Kotlin Multiplatform** data and persistence layer.
- Libraries and the server language are our choice.
- Follow the starter README and **make sure both starter apps build before writing any code**.
- If something is ambiguous, document a reasonable assumption and proceed.
- AI tools are allowed. Explain how they were used and how their output was checked. You must understand and be able to modify every line you submit.

## 1. Local API

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| API-1 | `GET /flights` returns HTTP 200 and the JSON envelope from `fixtures/flights.json` | `curl -i localhost:8080/flights` returns `200` with `Content-Type: application/json`. `curl -s localhost:8080/flights \| diff - fixtures/flights.json` shows no differences. | — | ✅ 2026‑11‑02 |
| API-2 | A single documented command runs the API | The README shows one command (`./gradlew :server:run`), and it works from a clean clone. | — | ✅ 2026‑11‑02 |

## 2. Shared data & persistence (KMP)

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| DATA-1 | Networking is implemented once in KMP | The HTTP client and DTO decoding live in `shared/commonMain`. Neither app makes HTTP calls. | R1–R12 | ✅ 2026‑11‑02 |
| DATA-2 | Persistence is implemented once in KMP | The Room entities, DAOs, database and repositories live in `shared` (D3). Only the database file location is per platform. | V1–V8, R3 | ✅ 2026‑11‑02 |
| DATA-3 | Search and filtering are implemented once in KMP and consumed by both apps | Neither app filters, sorts or groups flights itself. Both render `ExplorerState`. | S, F, O, M | 🟨 |
| DATA-4 | The last successful API results are persisted | After a successful fetch, killing the app and relaunching with the API stopped shows the same results. | R1, R3 | ✅ 2026‑11‑03 |
| DATA-5 | Saved flights are stored locally | Saved flights survive app restarts and do not depend on the cache. | V1 | ✅ 2026‑11‑03 |
| DATA-6 | Each platform keeps its own local data (no sync) | No sync code exists. This is noted in the submission notes. | — | ✅ 2026‑11‑02 |

## 3. Search & filters

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| SRCH-1 | Separate origin and destination search fields | There are two fields. Each one only constrains its own side of the route. | S3 | ✅ 2026‑11‑03 |
| SRCH-2 | Fields match airport codes or city names | `jfk` and `new york` both find JFK flights. `tokyo` finds both HND and NRT. | S4, S5 | ✅ 2026‑11‑03 |
| SRCH-3 | Search ignores capitalisation and surrounding whitespace | `"  BoStOn "` behaves exactly like `boston`. | S1 | ✅ 2026‑11‑03 |
| SRCH-4 | *(owner-requested)* Prefix search walks a prefix tree in O(m) | Typing `new` walks n→e→w and returns every airport under that node. `completions()` returns full names. | S4 | ✅ engine |
| SRCH-5 | *(owner-requested)* Fuzzy fallback for typos, sound-alike spellings, partial spellings and accents | `sydeny`, `sidnee`, `johanesburg` and `São Paulo` all find the right airport. Unrelated places (`berlin`) find nothing. Fuzzy only runs when prefix finds nothing. | S6–S12 | ✅ engine |
| SRCH-6 | *(owner-requested)* Phonetic matching with Double Metaphone, as credited third-party code | The vendored port matches commons-codec on 150k+ generated words (oracle test). Credited in THIRD_PARTY_NOTICES.md and SUBMISSION_NOTES §8. | S8 | ✅ |
| SRCH-7 | *(owner-requested)* Ranking by user location and annual airport volume, falling back to the original data | Matches are ordered by priority. An airport with no passenger figure uses its flight count from `flights.json`. Without a location, it uses volume only. The destination field never uses location. | P1–P7 | ✅ 2026‑11‑03: engine and store, with the shipped passenger figures. Device location (D16, D30) on Android (Step 4.7) and iOS (Step 5.7), asked at first launch since Step 6.3b (D32). Ranking orders airports only, never the flight list (D17). |
| SRCH-8 | *(owner-requested)* A "Showing … (CODE)" hint when fuzzy matching was used | Both apps show the hint under the field when the tier is `Fuzzy`. | S13 | ✅ 2026‑11‑03 |
| SRCH-9 | *(owner-requested)* Suggestions appear and change as the user types | In From, typing `new` shows New York (JFK). Deleting that and typing `sea` shows Seattle (SEA). In To, `tokyo` also offers Tokyo (all airports). Tapping a suggestion picks what it names. | S14 | ✅ 2026‑11‑03 |
| FILT-1 | Departure date range filter that includes both boundary dates | Flights on `start` and on `end` are included. Flights on the day before `start` and the day after `end` are excluded. | F1–F3 | ✅ 2026‑11‑03 |
| FILT-2 | Cabin filter | Choosing a cabin shows only that cabin. Choosing "Any" shows all cabins. | F5 | ✅ 2026‑11‑03 |
| FILT-3 | Maximum points filter that includes the selected limit | A flight whose points equal the limit is included. A flight 500 points above it is excluded. | F4 | ✅ 2026‑11‑03 |
| FILT-4 | Loyalty program filter with multiple selections | Selecting {Avios, Aeroplan} shows flights from either program. | F6 | ✅ 2026‑11‑03 |
| FILT-5 | Different filters combine with AND, and a flight may match any selected program | A cabin + points + programs combination returns only flights that satisfy every filter. | F7 | ✅ 2026‑11‑03 |
| FILT-6 | Empty filters include all values | With no filters set, all 1,000 fixture flights are shown. | F2, F4–F6 | ✅ 2026‑11‑03 |
| FILT-7 | Show the result count | The count always equals the number of rows in the list and the sum of all map marker counts. | C1 | ✅ 2026‑11‑03 |
| FILT-8 | Show the active filters | Every non-default filter is visible as a chip or label without opening a sheet. | C2 | ✅ 2026‑11‑03 |
| FILT-9 | Provide a clear way to reset | One tap on "Reset" restores all defaults. Each chip can also be cleared on its own. | C3, C4 | ✅ 2026‑11‑03 |
| FILT-10 | Start with the full fixture date range so results are visible immediately | On first launch, the date filter shows 2027‑02‑01 – 2027‑04‑01 and all flights. The bounds come from the data and are not hardcoded. | F2, F8 | ✅ 2026‑11‑03 |

## 4. Flight list & map

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| VIEW-1 | List and map show the same filtered results and the same saved state | The list row count equals the sum of marker counts. Saving a flight on the map updates the list immediately, and the reverse. | M1, V3 | ✅ 2026‑11‑03 |
| VIEW-2 | Sort the results (the owner's addition; the brief asks for no sort) | The list's header shows the current sort, and its sheet offers Lowest points (the default), Highest points, By program (with a heading per program) and Best offer. Both apps' lists follow it. | O1, O4 | ✅ 2026‑11‑04: checked on the iOS simulator and the Android emulator (QA Q30) |
| MAP-1 | Group markers by destination airport | There is one marker per destination code in the current results. | M2 | ✅ 2026‑11‑03 |
| MAP-2 | Each marker shows the number of matching flights and the lowest points price | The marker reads, for example, `CDG ✈ 12`: the code and the count on a badge marked with a plane. The values match the filtered data. | M3, T2 | 🟨 partly met, by the owner's decision (D44, 2026‑11‑04): the count is drawn, but the lowest points aren't. They're in each pill's spoken label ("Paris, 12 flights, from 25,000 points") and in the title of the sheet a tap opens. Met in full until then (✅ 2026‑11‑03) |
| MAP-3 | Tapping a marker lets the user browse those flights | Tapping a marker opens a sheet listing that destination's flights. Tapping a flight in the sheet selects it. | M5, O2 | ✅ 2026‑11‑03 |
| MAP-4 | Standard map gestures, including pan and zoom | Pan, pinch-zoom and double-tap zoom all work smoothly and are never fought by automatic camera moves. | M8 | 🟨 pan and double-tap checked on both; pinch not yet (neither adb nor idb can make two fingers) |

## 5. Selection & saved flights

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| SEL-1 | Selecting a flight shows its route connector and a card with: origin and destination, departure date, cabin, points price, loyalty program, and a Save or Remove action | The card shows all six items. On the map, a geodesic line connects the origin and the destination. | L6, M7 | ✅ 2026‑11‑03 |
| SEL-2 | The selection is preserved when switching between list and map | Select in the list, switch to the map: the same flight is selected and its route is drawn. The reverse also works. | L1, L4 | ✅ 2026‑11‑03. Since Step 6.3d the list is a sheet over the map: selecting in it drops the sheet and shows the route, and a pill's sheet selects into the list |
| SEL-3 | The selection is cleared when the flight leaves the filtered results | Select a flight, then filter it out: the card disappears, and it does not come back when the filter is undone. | L3 | ✅ 2026‑11‑03 |
| SEL-4 | A flight details page (the owner's addition; the brief asks for none) | "View details" on the selection card, in Explore and Saved, opens a page like Roame's: the destination's photo, the departure, program and cabin, both stops, the points with how far below typical they are, the distance and an estimated value. Times, travel time and aircraft show as placeholders. Save or Remove works on the page, and Back returns to the card. | DT1–DT3 | ✅ 2026‑11‑04: checked on the iOS simulator and the Android emulator (QA Q31) |
| SAVE-1 | A separate saved-flights view that is always reachable, whatever the discovery filters | The Saved tab lists every saved flight, even when the current filters match none of them. | V8, L7 | ✅ 2026‑11‑03 |

## 6. Performance

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| PERF-1 | Scrolling, searching, filtering and map interaction stay responsive with the full dataset | No visible jank while scrolling 1,000 rows or typing quickly. The map stays smooth while results change. | — | 🟨 typing quickly and the map changing results checked on both. Android scrolling with the Mac's GPU: 9.3% janky frames on a debug build, against 4.5% for Android's Settings app (Step 6.1); a release build and an iPhone are still to measure |
| PERF-2 | Network and database work never blocks the UI | Android StrictMode in debug builds reports no disk or network access on the main thread. All repository calls are `suspend` or `Flow` on IO dispatchers. | — | 🟨 Android: StrictMode reports nothing, including opening the map (Step 6.1). iOS: the Thread Performance Checker only runs from Xcode |
| PERF-3 | Results always reflect the user's latest search and filter choices | A test sends rapid query changes and sees only the final query's results. A slow, stale computation can never overwrite a newer one (`mapLatest`). | — | ✅ 2026‑11‑02 |
| PERF-4 | No crashes and no memory leaks | LeakCanary (Android) and the Xcode Memory Graph / Leaks instrument (iOS) are clean after a scripted session. No crashes during QA. | — | ✅ 2026‑11‑04: iOS, no leaks from app code (Step 5.8). Android: LeakCanary finds none, and since D31 native memory stays flat across 18 map opens and 14 trips to Saved and back (Step 6.1). No crashes during QA |

## 7. Offline & error handling

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| OFF-1 | Cached results and saved flights survive closing and reopening the app | Force-quit, stop the API, relaunch: the cached results and saved flights are both present. | R1, V1 | ✅ 2026‑11‑03 |
| OFF-2 | Offline, the user can search cached results, view saved flights and remove saved flights | With the API stopped, search, filters, map, the Saved tab and Remove all work. | R6, V6 | ✅ 2026‑11‑03 |
| OFF-3 | A failed refresh keeps the existing data and shows an offline or stale-data indication | Stop the API and reopen the app: the data is unchanged and a banner shows "Couldn't refresh · showing results from <time>". | R5, R6, R11 | ✅ 2026‑11‑03 |
| OFF-4 | With no cached data, show an understandable error with a retry action | Fresh install with the API stopped: a full-screen error explains the cause. Retry works once the API is started. | R7, R9 | ✅ 2026‑11‑03 |
| OFF-5 | A successful empty response is distinguished from a failed request | A 200 response with `{"flights":[]}` shows "No flights available" (not an error). A failed request shows the error or stale state. | R4, R8 | ✅ 2026‑11‑03 |
| OFF-6 | Saving the same flight again updates its details without creating a duplicate | Re-saving an id after its details change leaves one row with the new details. | V2 | ✅ 2026‑11‑02 |
| OFF-7 | Saved flights remain available when they are missing from the latest API response | Remove a saved flight from the fixture and refresh: it stays in Saved and is labelled "No longer listed". | V4 | ✅ 2026‑11‑03 |
| OFF-8 | Failed save or remove operations never appear successful | With an injected DB failure, the button reverts, an error message is shown, and the saved state is unchanged. | V7 | ✅ 2026‑11‑03 |
| OFF-9 | Offline map tiles are **not** required | Noted as out of scope. Once the map has loaded online, it works offline from its cache. If the Android map has never loaded online, it shows a message with Retry (D24), and flights stay available in the list. iOS is expected to show markers over empty tiles. | — | 🟨 Android: a never-loaded map shows the message and Retry. iOS: offline, the pills draw over saved tiles; blank, never-loaded tiles weren't seen |

## 8. UX quality

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| UX-1 | Smooth, understandable experience: responsive scrolling and map gestures | See PERF-1 and MAP-4. | — | ⬜ |
| UX-2 | Consistent selection | See SEL-1 to SEL-3. The selected row and the selected marker are visually highlighted. | L1–L7 | ✅ 2026‑11‑03 |
| UX-3 | Clear loading, empty and error states | Every state in [UI_SPEC.md §4](UI_SPEC.md#4-state-catalogue) is implemented on both platforms. | R, C | 🟨 |
| UX-4 | Cohesive, visually appealing UI with attention to spacing, typography, hierarchy and interaction feedback | The design tokens in UI_SPEC are used. There is pressed, selected and loading feedback, plus haptics on save. Roame is a functional reference, not a pixel spec. | — | 🟨 Step 6.3a: one design system on both platforms (D38): Roame's colours, Inter, the same Material Symbols, seat-class icons, program logos, and the same tab bar, controls, toasts and empty states; the Account tab (D39). Step 6.3b: Roame's landing page on both, with the destination deck, best offers by program (B1–B9) and location at launch. Step 6.3c: Roame's full-screen search page with a swap button and the app's own calendar on both (D41). Step 6.3d: map-first results like Airbnb's, the list as a sheet over the map, one Filters sheet, and the selected route as moving dots with a plane (M9). Step 6.3e: Saved's title, spoken labels in full words, layouts for the largest text sizes, and the card's border in dark mode, on both |

## 9. Testing & ownership

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| TEST-1 | Focused tests for the shared filtering rules | See [TEST_PLAN.md](TEST_PLAN.md) §2: search and filter test classes. | S, F | ✅ 2026‑11‑02 |
| TEST-2 | Tests for date and points boundaries | Inclusive and exclusive edge cases for both. | F1, F4 | ✅ 2026‑11‑02 |
| TEST-3 | Tests for map aggregation | Grouping, counts, minimum points, empty input, and consistency with the filters. | M | ✅ 2026‑11‑02 |
| TEST-4 | Tests for persistence | Atomic cache replacement, survival across reopen, upsert without duplicates, and saved flights independent of the cache. | V, R3 | ✅ 2026‑11‑02 |
| TEST-5 | Tests for failure handling | Offline, timeout, 5xx, malformed body, success-empty, and save/remove failures. | R, V7 | ✅ 2026‑11‑02 |
| TEST-6 | Document the checks actually performed on both platforms | The QA checklist in TEST_PLAN §5 is filled in with real results and dates. | — | ✅ 2026‑11‑03: every row has a dated Android and iOS entry |

## 10. Deliverables

| ID | Deliverable | Acceptance criteria | Status |
|---|---|---|---|
| DEL-1 | A repository link or ZIP containing both apps, the shared KMP module and the local API | A clean clone builds. Nothing depends on files outside the repo. | ⬜ |
| DEL-2 | Instructions to build, run and test | The README Quick start and Tests sections have been verified from a clean clone. | ⬜ |
| DEL-3 | Submission notes: approach and key architectural decisions, assumptions and tradeoffs, known limitations, and how AI was used and checked | [SUBMISSION_NOTES.md](SUBMISSION_NOTES.md) is complete, with no TODOs left. | ⬜ |
| DEL-4 | Screen recording walkthrough of **both** apps showing search, filters, map selection, saving flights, and reopening the app with the API stopped | Follows the demo script in TEST_PLAN §7. | ⬜ |
| DEL-5 | Ambiguities documented as assumptions | Every *(assumption)* rule in DOMAIN_RULES appears in the submission notes. | ⬜ |
| DEL-6 | Both starter apps built before implementation | Recorded in TEST_PLAN §5 (pre-flight). | ✅ 2026‑11‑01, before any app, server or data work. The shared search engine came first, at the owner's request. |

## 11. Scalability (non-functional, self-imposed)

The brief only requires the app to stay smooth with the 1,000-flight fixture (PERF-1). These rows record how the design should behave as the data grows, so scale is considered from the start rather than bolted on. "Documented" means the scaling path is written down in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability) but not built, because the fixture doesn't need it.

| ID | Requirement | Target / acceptance | Status |
|---|---|---|---|
| SCALE-1 | Text search doesn't scan flights | Search resolves each field to a set of *airports* through indexes. Flights are then filtered once by airport code. The airport lookup grows with the query length and the number of airports, not the number of flights. The whole keystroke still grows with flights, because of that one filter pass. | ✅ 2026‑11‑04: one keystroke's airport lookup takes 0.08–0.18 ms at 5,000 airports by prefix (JVM, Step 6.2). A typo takes about 1.25 ms there, just over the 1 ms target |
| SCALE-2 | Each keystroke stays within one frame at 10× and 100× the data | On a JVM benchmark with synthetic data (10k and 100k flights, up to 5k airports), search + filter + sort + aggregate stays under 16 ms at 10k flights. At 100k the work stays off the main thread with latest-wins (`mapLatest`), and the UI never stalls. | ✅ 2026‑11‑04 (JVM, a stand-in for phones): search + filter + sort + aggregate takes 3.2–5.7 ms at 10k flights with no input, under 1.1 ms when typing. At 100k, 41–51 ms, off the main thread with latest-wins (PERF-3) |
| SCALE-3 | The cache refresh scales | One transaction with batched inserts. Beyond ~100k rows, switch to incremental sync (ETag / `If-None-Match`, deltas) instead of a full replace. | ✅ 2026‑11‑04: replacing 100k rows takes 270–284 ms in one transaction (JVM, Step 6.2). Incremental sync beyond ~100k is documented |
| SCALE-4 | The API contract can grow without breaking clients | Pagination, compression and conditional requests can be added alongside `GET /flights` without changing its current response. | Documented |
| SCALE-5 | The map stays smooth with many destinations | Up to ~200 markers render individually. Beyond that, markers are clustered by zoom level. The fixture has 24. | Documented. Grouping 100k flights into markers takes 2.6–4.5 ms (JVM, Step 6.2); rendering ~200 markers isn't measured |
| SCALE-6 | Lists are virtualised | `LazyColumn` / SwiftUI `List` with stable ids, so memory is proportional to the visible rows. Marker sheets and the Saved list use the same approach. | ✅ 2026‑11‑04: Android `LazyColumn` and iOS `LazyVStack`, keyed by flight id, for results, marker sheets and Saved |
| SCALE-7 | In-memory indexes have a known memory budget | The size of the search indexes and the in-memory dataset is measured at 10k and 100k flights and written down. If a budget is exceeded, filtering moves into indexed SQL queries (ARCHITECTURE §12). | ✅ 2026‑11‑04: flights + indexes take 5.8 MB at 10k and 56–67 MB at 100k (JVM, Step 6.2) |
