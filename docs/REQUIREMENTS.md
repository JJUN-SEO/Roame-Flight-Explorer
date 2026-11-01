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
| API-1 | `GET /flights` returns HTTP 200 and the JSON envelope from `fixtures/flights.json` | `curl -i localhost:8080/flights` returns `200` with `Content-Type: application/json`. `curl -s localhost:8080/flights \| diff - fixtures/flights.json` shows no differences. | — | ⬜ |
| API-2 | A single documented command runs the API | The README shows one command (`./gradlew :server:run`), and it works from a clean clone. | — | ⬜ |

## 2. Shared data & persistence (KMP)

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| DATA-1 | Networking is implemented once in KMP | The HTTP client and DTO decoding live in `shared/commonMain`. Neither app makes HTTP calls. | R1–R12 | ⬜ |
| DATA-2 | Persistence is implemented once in KMP | The Room entities, DAOs, database and repositories live in `shared` (D3). Only the database file location is per platform. | V1–V8, R3 | ⬜ |
| DATA-3 | Search and filtering are implemented once in KMP and consumed by both apps | Neither app filters, sorts or groups flights itself. Both render `ExplorerState`. | S, F, O, M | ⬜ |
| DATA-4 | The last successful API results are persisted | After a successful fetch, killing the app and relaunching with the API stopped shows the same results. | R1, R3 | ⬜ |
| DATA-5 | Saved flights are stored locally | Saved flights survive app restarts and do not depend on the cache. | V1 | ⬜ |
| DATA-6 | Each platform keeps its own local data (no sync) | No sync code exists. This is noted in the submission notes. | — | ⬜ |

## 3. Search & filters

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| SRCH-1 | Separate origin and destination search fields | There are two fields. Each one only constrains its own side of the route. | S3 | 🟨 engine done (one index per side); UI pending |
| SRCH-2 | Fields match airport codes or city names | `jfk` and `new york` both find JFK flights. `tokyo` finds both HND and NRT. | S4, S5 | 🟨 engine done and tested; UI pending |
| SRCH-3 | Search ignores capitalisation and surrounding whitespace | `"  BoStOn "` behaves exactly like `boston`. | S1 | 🟨 engine done and tested; UI pending |
| SRCH-4 | *(owner-requested)* Prefix search walks a prefix tree in O(m) | Typing `new` walks n→e→w and returns every airport under that node. `completions()` returns full names. | S4 | ✅ engine |
| SRCH-5 | *(owner-requested)* Fuzzy fallback for typos, sound-alike spellings, partial spellings and accents | `sydeny`, `sidnee`, `johanesburg` and `São Paulo` all find the right airport. Unrelated places (`berlin`) find nothing. Fuzzy only runs when prefix finds nothing. | S6–S12 | ✅ engine |
| SRCH-6 | *(owner-requested)* Phonetic matching with Double Metaphone, as credited third-party code | The vendored port matches commons-codec on 150k+ generated words (oracle test). Credited in THIRD_PARTY_NOTICES.md and SUBMISSION_NOTES §8. | S8 | ✅ |
| SRCH-7 | *(owner-requested)* Ranking by user location and annual airport volume, falling back to the original data | Matches are ordered by priority. An airport with no passenger figure uses its flight count from `flights.json`. Without a location, it uses volume only. The destination field never uses location. | P1–P7 | 🟨 engine done. Device location (D16) is wired in Phases 4 and 5. Ranking orders airports only, never the flight list (D17). |
| SRCH-8 | *(owner-requested)* A "Showing … (CODE)" hint when fuzzy matching was used | Both apps show the hint under the field when the tier is `Fuzzy`. | S13 | ⬜ UI pending |
| SRCH-9 | *(owner-requested)* Suggestions appear and change as the user types | In From, typing `new` shows New York (JFK). Deleting that and typing `sea` shows Seattle (SEA). In To, `tokyo` also offers Tokyo (all airports). Tapping a suggestion picks what it names. | S14 | ⬜ |
| FILT-1 | Departure date range filter that includes both boundary dates | Flights on `start` and on `end` are included. Flights on the day before `start` and the day after `end` are excluded. | F1–F3 | ⬜ |
| FILT-2 | Cabin filter | Choosing a cabin shows only that cabin. Choosing "Any" shows all cabins. | F5 | ⬜ |
| FILT-3 | Maximum points filter that includes the selected limit | A flight whose points equal the limit is included. A flight 500 points above it is excluded. | F4 | ⬜ |
| FILT-4 | Loyalty program filter with multiple selections | Selecting {Avios, Aeroplan} shows flights from either program. | F6 | ⬜ |
| FILT-5 | Different filters combine with AND, and a flight may match any selected program | A cabin + points + programs combination returns only flights that satisfy every filter. | F7 | ⬜ |
| FILT-6 | Empty filters include all values | With no filters set, all 1,000 fixture flights are shown. | F2, F4–F6 | ⬜ |
| FILT-7 | Show the result count | The count always equals the number of rows in the list and the sum of all map marker counts. | C1 | ⬜ |
| FILT-8 | Show the active filters | Every non-default filter is visible as a chip or label without opening a sheet. | C2 | ⬜ |
| FILT-9 | Provide a clear way to reset | One tap on "Reset" restores all defaults. Each chip can also be cleared on its own. | C3, C4 | ⬜ |
| FILT-10 | Start with the full fixture date range so results are visible immediately | On first launch, the date filter shows 2027‑02‑01 – 2027‑04‑01 and all flights. The bounds come from the data and are not hardcoded. | F2, F8 | ⬜ |

## 4. Flight list & map

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| VIEW-1 | List and map show the same filtered results and the same saved state | The list row count equals the sum of marker counts. Saving a flight on the map updates the list immediately, and the reverse. | M1, V3 | ⬜ |
| MAP-1 | Group markers by destination airport | There is one marker per destination code in the current results. | M2 | ⬜ |
| MAP-2 | Each marker shows the number of matching flights and the lowest points price | The marker label reads, for example, `CDG · 12 · 25k`, and the values match the filtered data. | M3, T2 | ⬜ |
| MAP-3 | Tapping a marker lets the user browse those flights | Tapping a marker opens a sheet listing that destination's flights. Tapping a flight in the sheet selects it. | M5, O2 | ⬜ |
| MAP-4 | Standard map gestures, including pan and zoom | Pan, pinch-zoom and double-tap zoom all work smoothly and are never fought by automatic camera moves. | M8 | ⬜ |

## 5. Selection & saved flights

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| SEL-1 | Selecting a flight shows its route connector and a card with: origin and destination, departure date, cabin, points price, loyalty program, and a Save or Remove action | The card shows all six items. On the map, a geodesic line connects the origin and the destination. | L6, M7 | ⬜ |
| SEL-2 | The selection is preserved when switching between list and map | Select in the list, switch to the map: the same flight is selected and its route is drawn. The reverse also works. | L1, L4 | ⬜ |
| SEL-3 | The selection is cleared when the flight leaves the filtered results | Select a flight, then filter it out: the card disappears, and it does not come back when the filter is undone. | L3 | ⬜ |
| SAVE-1 | A separate saved-flights view that is always reachable, whatever the discovery filters | The Saved tab lists every saved flight, even when the current filters match none of them. | V8, L7 | ⬜ |

## 6. Performance

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| PERF-1 | Scrolling, searching, filtering and map interaction stay responsive with the full dataset | No visible jank while scrolling 1,000 rows or typing quickly. The map stays smooth while results change. | — | ⬜ |
| PERF-2 | Network and database work never blocks the UI | Android StrictMode in debug builds reports no disk or network access on the main thread. All repository calls are `suspend` or `Flow` on IO dispatchers. | — | ⬜ |
| PERF-3 | Results always reflect the user's latest search and filter choices | A test sends rapid query changes and sees only the final query's results. A slow, stale computation can never overwrite a newer one (`mapLatest`). | — | ⬜ |
| PERF-4 | No crashes and no memory leaks | LeakCanary (Android) and the Xcode Memory Graph / Leaks instrument (iOS) are clean after a scripted session. No crashes during QA. | — | ⬜ |

## 7. Offline & error handling

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| OFF-1 | Cached results and saved flights survive closing and reopening the app | Force-quit, stop the API, relaunch: the cached results and saved flights are both present. | R1, V1 | ⬜ |
| OFF-2 | Offline, the user can search cached results, view saved flights and remove saved flights | With the API stopped, search, filters, map, the Saved tab and Remove all work. | R6, V6 | ⬜ |
| OFF-3 | A failed refresh keeps the existing data and shows an offline or stale-data indication | Stop the API and pull to refresh: the data is unchanged and a banner shows "Couldn't refresh · results from <time>" with Retry. | R5, R6, R11 | ⬜ |
| OFF-4 | With no cached data, show an understandable error with a retry action | Fresh install with the API stopped: a full-screen error explains the cause. Retry works once the API is started. | R7, R9 | ⬜ |
| OFF-5 | A successful empty response is distinguished from a failed request | With `--mode=empty`, the app shows "No flights available" (not an error). With `--mode=error`, the app shows the error or stale state. | R4, R8 | ⬜ |
| OFF-6 | Saving the same flight again updates its details without creating a duplicate | Re-saving an id after its details change leaves one row with the new details. | V2 | ⬜ |
| OFF-7 | Saved flights remain available when they are missing from the latest API response | Remove a saved flight from the fixture and refresh: it stays in Saved and is labelled "No longer listed". | V4 | ⬜ |
| OFF-8 | Failed save or remove operations never appear successful | With an injected DB failure, the button reverts, an error message is shown, and the saved state is unchanged. | V7 | ⬜ |
| OFF-9 | Offline map tiles are **not** required | Noted as out of scope. The map shows markers over empty tiles when offline. | — | ⬜ |

## 8. UX quality

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| UX-1 | Smooth, understandable experience: responsive scrolling and map gestures | See PERF-1 and MAP-4. | — | ⬜ |
| UX-2 | Consistent selection | See SEL-1 to SEL-3. The selected row and the selected marker are visually highlighted. | L1–L7 | ⬜ |
| UX-3 | Clear loading, empty and error states | Every state in [UI_SPEC.md §4](UI_SPEC.md#4-state-catalogue) is implemented on both platforms. | R, C | ⬜ |
| UX-4 | Cohesive, visually appealing UI with attention to spacing, typography, hierarchy and interaction feedback | The design tokens in UI_SPEC are used. There is pressed, selected and loading feedback, plus haptics on save. Roame is a functional reference, not a pixel spec. | — | ⬜ |

## 9. Testing & ownership

| ID | Requirement | Acceptance criteria | Rules | Status |
|---|---|---|---|---|
| TEST-1 | Focused tests for the shared filtering rules | See [TEST_PLAN.md](TEST_PLAN.md) §2: search and filter test classes. | S, F | 🟨 search tests done; filter tests pending |
| TEST-2 | Tests for date and points boundaries | Inclusive and exclusive edge cases for both. | F1, F4 | ⬜ |
| TEST-3 | Tests for map aggregation | Grouping, counts, minimum points, empty input, and consistency with the filters. | M | ⬜ |
| TEST-4 | Tests for persistence | Atomic cache replacement, survival across reopen, upsert without duplicates, and saved flights independent of the cache. | V, R3 | ⬜ |
| TEST-5 | Tests for failure handling | Offline, timeout, 5xx, malformed body, success-empty, and save/remove failures. | R, V7 | ⬜ |
| TEST-6 | Document the checks actually performed on both platforms | The QA checklist in TEST_PLAN §5 is filled in with real results and dates. | — | ⬜ |

## 10. Deliverables

| ID | Deliverable | Acceptance criteria | Status |
|---|---|---|---|
| DEL-1 | A repository link or ZIP containing both apps, the shared KMP module and the local API | A clean clone builds. Nothing depends on files outside the repo. | ⬜ |
| DEL-2 | Instructions to build, run and test | The README Quick start and Tests sections have been verified from a clean clone. | ⬜ |
| DEL-3 | Submission notes: approach and key architectural decisions, assumptions and tradeoffs, known limitations, and how AI was used and checked | [SUBMISSION_NOTES.md](SUBMISSION_NOTES.md) is complete, with no TODOs left. | ⬜ |
| DEL-4 | Screen recording walkthrough of **both** apps showing search, filters, map selection, saving flights, and reopening the app with the API stopped | Follows the demo script in TEST_PLAN §7. | ⬜ |
| DEL-5 | Ambiguities documented as assumptions | Every *(assumption)* rule in DOMAIN_RULES appears in the submission notes. | ⬜ |
| DEL-6 | Both starter apps built before implementation | Recorded in TEST_PLAN §5 (pre-flight). | ⬜ |

## 11. Scalability (non-functional, self-imposed)

The brief only requires the app to stay smooth with the 1,000-flight fixture (PERF-1). These rows record how the design should behave as the data grows, so scale is considered from the start rather than bolted on. "Documented" means the scaling path is written down in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability) but not built, because the fixture doesn't need it.

| ID | Requirement | Target / acceptance | Status |
|---|---|---|---|
| SCALE-1 | Text search doesn't scan flights | Search resolves each field to a set of *airports* through indexes. Flights are then filtered once by airport code. The airport lookup grows with the query length and the number of airports, not the number of flights. The whole keystroke still grows with flights, because of that one filter pass. | ✅ engine (D18). Benchmark pending (TEST_PLAN §4.1) |
| SCALE-2 | Each keystroke stays within one frame at 10× and 100× the data | On a JVM benchmark with synthetic data (10k and 100k flights, up to 5k airports), search + filter + sort + aggregate stays under 16 ms at 10k flights. At 100k the work stays off the main thread with latest-wins (`mapLatest`), and the UI never stalls. | ⬜ |
| SCALE-3 | The cache refresh scales | One transaction with batched inserts. Beyond ~100k rows, switch to incremental sync (ETag / `If-None-Match`, deltas) instead of a full replace. | Documented |
| SCALE-4 | The API contract can grow without breaking clients | Pagination, compression and conditional requests can be added alongside `GET /flights` without changing its current response. | Documented |
| SCALE-5 | The map stays smooth with many destinations | Up to ~200 markers render individually. Beyond that, markers are clustered by zoom level. The fixture has 24. | Documented |
| SCALE-6 | Lists are virtualised | `LazyColumn` / SwiftUI `List` with stable ids, so memory is proportional to the visible rows. Marker sheets and the Saved list use the same approach. | ⬜ |
| SCALE-7 | In-memory indexes have a known memory budget | The size of the search indexes and the in-memory dataset is measured at 10k and 100k flights and written down. If a budget is exceeded, filtering moves into indexed SQL queries (ARCHITECTURE §12). | ⬜ |
