# Domain rules

This file is the behavioural spec for `shared/`. Every rule has an ID, and each test in `shared/src/commonTest` names the rule it covers, for example `f4_pointsEqualToMax_isIncluded`.

Rules tagged **(assumption)** are our decision on something the brief leaves open. Each one is mirrored in [SUBMISSION_NOTES.md](SUBMISSION_NOTES.md).

When a rule changes, update this file and its test in the same commit.

---

## 0. Fixture facts

Profiled from `fixtures/flights.json` on 2026‑10‑29. Use these as test expectations and sanity checks. **Never hardcode them in app logic**: the API could return different data.

| Fact | Value |
|---|---|
| Envelope | `{"flights": [ … ]}` with no other keys (no timestamp, version or paging) |
| Records | 1,000. All 13 fields are present on every record, there are no nulls, and ids are unique. |
| Fields | `id`, `origin`, `originCity`, `originLatitude`, `originLongitude`, `destination`, `destinationCity`, `destinationLatitude`, `destinationLongitude`, `departureDate` (`yyyy-MM-dd`), `program`, `points` (int), `cabin` |
| Dates | 2027‑02‑01 to 2027‑04‑01: 60 distinct days, every day present. The file is sorted by date. |
| Airports | 32 in total: 8 origins (BOS, JFK, LAX, ORD, SEA, SFO, YVR, YYZ) and 24 destinations. No airport is both an origin and a destination. Coordinates are identical for every occurrence of a code. |
| Routes | 192 origin→destination pairs, with at most one flight per route per day |
| Cabins | Economy 467 · Business 344 · Premium Economy 189 |
| Programs | Flying Blue 212 · Mileage Plan 203 · MileagePlus 199 · Avios 196 · Aeroplan 190. **"Mileage Plan" and "MileagePlus" are different programs**, so never normalise them together. |
| Points | 9,000 to 133,000, all multiples of 500 |
| IDs | Mostly `fx-<ORIGIN>-<DEST>-<yyyyMMdd>`, but six are `f101`…`f106`. **IDs are opaque**: never parse them. |
| Cities | Tokyo has two airports (HND and NRT). "Sao Paulo" is stored without the tilde. |
| Geometry | 364 flights have \|Δlongitude\| > 180° (for example JFK→HKG and SEA→SYD), so route lines must handle the antimeridian. |
| Busiest destinations | NRT 55 · HND 55 · MAD 54 flights |
| Supplementary file | `fixtures/airports.json` (added by us, not part of the provided fixture): approximate 2024 annual passengers for all 32 airports, used only for ranking (P3). A copy ships inside the app (D15). An airport with no figure uses its flight count (P4). |

## 1. Data model (DM)

```kotlin
data class Airport(val code: String, val city: String, val latitude: Double, val longitude: Double)

data class Flight(
    val id: String,
    val origin: Airport,
    val destination: Airport,
    val departureDate: LocalDate,   // kotlinx.datetime: a calendar date, not an instant
    val program: String,
    val points: Int,
    val cabin: String,
)
```

- **DM1** Cabin and program are plain strings from the API, so an unknown value such as `"First"` must not break decoding. Known cabins have a rank used for ordering: Economy < Premium Economy < Business < First. Unknown cabins sort after the known ones, alphabetically.
- **DM2** When mapping a DTO to the domain model, trim all strings and uppercase airport codes. This is defensive; the fixture is already clean.
- **DM3** A record with a missing field or an unparseable date makes the **whole response invalid** (see R5). We don't partially accept a response. *(assumption)*

## 2. Search (S)

Implemented in `shared/src/commonMain/kotlin/travel/roame/takehome/domain/search/` (`AirportSearchEngine`).

**The design is the owner's** (SUBMISSION_NOTES §2, D11–D17):
- a prefix tree walked node by node,
- a tiered fallback to fuzzy matching that mixes phonetic and n-gram matching, plus accent folding,
- ranking by airport volume, plus the user's location in the origin field only.

Claude implemented it, added an edit-distance typo signal (D12), and chose the thresholds below.

Search resolves each text field to a set of **airports**. Flights are then filtered by those airport codes. Matching never scans flights (SCALE-1).

```
query → S1 normalise → Tier 1: prefix tree (S4) ──found──▶ matches
                              │ found nothing
                              ▼
                        Tier 2: fuzzy (S6–S10)
                        typo · phonetic · n-gram ──▶ matches, or no match
      → ranked by P1–P6 → "Showing …" hint in the UI when fuzzy (S13)
```

The owner's hypothesis is that people mistype a place in four ways. Here is what catches each:

| Case | Example | Caught by |
|---|---|---|
| 1. Typo | `sydeny`, `bostno` | Typo distance (S7) |
| 2. Doesn't know the spelling but knows the sound | `sidnee`, `shicago`, `okland` | Phonetic, Double Metaphone (S8) |
| 3. Roughly knows the spelling | `johanesburg`, `amstedam` | N-gram (S9) and partial typo (S7) |
| 4. Accents | `São Paulo`, `MÉXICO`, `Páriss` | Normalisation (S1), then any tier |

### Rules

- **S1 Normalisation.** The same normalisation is applied to the query and to the index:
  - lowercase,
  - fold Latin accents (`São` → `sao`, `ß` → `ss`, `Ł` → `l`),
  - drop apostrophes (`O'Hare` → `ohare`),
  - treat every other character that isn't a letter or digit as a separator,
  - collapse separators into single spaces and trim.
- **S2** A blank query is tier `Blank`, and every airport on that side matches.
- **S3** The origin field searches only origin airports, and the destination field only destination airports. When both are filled, a flight must match both (AND).
- **S4 Tier 1: prefix tree (trie).** Each airport is indexed under:
  - its code,
  - its city name,
  - its city name without spaces (`hongkong`),
  - every word start in the city name (`york`, `angeles`, `paulo`).

  The query walks the tree one character at a time (n → e → w), so a lookup is O(m) for a query of length m. Every node stores the ids of the matching airports, precomputed, so the result is ready as soon as the walk ends. `completions()` branches from that node into the full names beneath it (`new` → `new york`). It always shows the display name, never an internal key such as `newyork`. The UI doesn't use it: suggestions (S14) use the ranked search matches, which carry the airport code and the priority.
- **S5** In Tier 1, an exact code match ranks first. Other prefix matches are ordered by priority (P6).
- **S6 Tier 2 gate.** Fuzzy matching runs **only when Tier 1 found nothing** and the query has at least 4 letters (spaces ignored).
  - Airport codes are never fuzzy-matched: 3 letters are too short to tell a typo from another code.
  - Fuzzy matching compares the query, with spaces removed, against two kinds of terms: each whole city name without spaces, and each word of 4 or more letters in multi-word names.
  - Candidates come from inverted indexes (trigram → terms, phonetic code → terms), not from a full scan.
- **S7 Typo signal.** Uses Optimal String Alignment distance, where an insert, delete, substitution or adjacent swap each costs 1.
  - Allowed distance: 1 for queries of 4–7 letters, 2 for 8 or more.
  - **Partial typo:** a query of 5 or more letters also matches when it is within 1 edit of the first |q|−1 … |q|+1 letters of a term. For example, `johanesb` matches while the user is still typing.
- **S8 Phonetic signal.** Uses Double Metaphone: a vendored port of Apache Commons Codec (SUBMISSION_NOTES §8), with code length 6, using both the primary and the alternate codes.
  - A query matches when it shares any code with a term. For example, `yohannesburg` matches Johannesburg's alternate code `AHNSPR`.
  - Codes of only 2 characters count only against a **whole** city name, never against a single word. So `roam` matches Rome (`RM`), but `sitty` does **not** match "Mexico City" through the word "city" (`ST`).
- **S9 N-gram signal.** Trigram Dice similarity of at least 0.55, with the words padded at the edges (`"  r"`, `" ro"`, …).
- **S10 Fuzzy scoring.** The signals score as follows:
  - typo: 0.90 − 0.05 × distance,
  - partial typo: 0.80,
  - phonetic: 0.85,
  - n-gram: 0.9 × Dice.

  An airport's score is its best signal over all its terms. Airports more than 0.10 below the best fuzzy score are dropped. Fuzzy scores always stay below the prefix score (0.95) and the exact-code score (1.0). Each match reports the signal that won (`MatchKind`).
- **S11 Examples.** A subset of the test tables (`PrefixSearchTest`, `FuzzySearchTest`: 90+ cases):

  | Field | Query | Tier | Matches | Why |
  |---|---|---|---|---|
  | origin | `"  BOS "` | Prefix | BOS | trim + case |
  | origin | `"york"` | Prefix | JFK | a word start in "New York" |
  | destination | `"tokyo"` | Prefix | HND, NRT | the city has two airports |
  | destination | `"dub"` | Prefix | DUB, DXB | branches to every match |
  | destination | `"São Paulo"` | Prefix | GRU | accent folding |
  | destination | `"hongkong"` | Prefix | HKG | no-space key |
  | destination | `"ondon"` | Fuzzy | LHR | typo: one letter missing |
  | destination | `"sydeny"` | Fuzzy | SYD | typo: swapped letters |
  | destination | `"sidnee"` | Fuzzy | SYD | phonetic `STN` = `STN` |
  | origin | `"shicago"` | Fuzzy | ORD | phonetic `XKK` |
  | destination | `"okland"` | Fuzzy | AKL | phonetic `AKLNT` |
  | destination | `"johanesburg"` | Fuzzy | JNB | partly right spelling |
  | destination | `"roma"` | Fuzzy | FCO | local name, 1 edit |
  | destination | `"berlin"` | NoMatch | — | not in the data |
  | destination | `"sitty"` | NoMatch | — | the 2-character-code rule (S8) |
  | origin | `"paris"` | NoMatch | — | Paris is only a destination |
- **S12 Known behaviour and limitations:**
  - Non-Latin scripts are not transliterated (`東京` finds nothing).
  - There are no nicknames or aliases (`NYC`, `LA`).
  - Places outside the data can sound like places in it: `newark` → New York (both encode to `NRK`). The hint line (S13) makes this visible. There is a test that documents it.
  - The thresholds were tuned on 32 airports and should be re-validated on a larger dataset.
- **S13 UI feedback** (owner decision: hint line).
  - When a field's tier is `Fuzzy`, both apps show a hint under that field: "Showing Sydney (SYD)". It lists the matches in ranked order: the first two, then "+N more".
  - No hint is shown for the `Prefix` or `Blank` tier.
  - `NoMatch` shows the filtered-empty state.
  - *Engine implemented; the UI comes with the store and screens.*
- **S14 Suggestions** (owner decision D20). While a field has focus and text, both apps show a dropdown of matching airports under it, updated on every keystroke.
  - The rows are that field's ranked matches from the search engine, in P6 order. They include fuzzy matches and never the other side's airports (S3). Each row shows the city and code, for example "Tokyo (HND)", and each airport gets its own row.
  - **City row** (owner decision, following Roame). When two or more matches share a city, one row for all of them, for example "Tokyo (all airports)", comes directly above that city's first airport row. It comes from the data, so no city is hardcoded, and `nrt` shows no city row because it matches one airport.
  - At most 5 rows are shown, counting city rows. *(assumption)*
  - Tapping a row picks exactly what it names and closes the dropdown. The field shows "Tokyo (HND)" and flights are filtered by that code alone, or it shows "Tokyo (all airports)" and flights are filtered by that city's matched codes. Editing the text clears the pick and returns to normal search. A pick is needed because the text alone isn't enough: `dub` also matches Dubai (DXB). *(assumption)*
  - A blank query or `NoMatch` shows no dropdown.
  - A pick counts as search text for Reset (C2, C4), and Reset clears it.
  - The S13 hint stays, because it still explains fuzzy results after the dropdown closes. A pick shows no hint.

## 2b. Prioritisation (P)

Implemented in `AirportRanking`. Priority **orders** matched airports. It never adds or removes matches.

- **P1** Priority is a value in [0, 1]:

  `(w_proximity · proximity + w_volume · volume) / (w_proximity + w_volume)`
- **P2** `proximity = 0.5^(distance_km / 250)`, so it halves every 250 km. The distance is the great-circle (haversine) distance from the user's location to the airport.
  - Without a location (unknown, permission denied, or not available yet), priority is volume only.
  - The location comes from **the device** (owner decision D16): Fused Location Provider on Android and Core Location on iOS, at approximate accuracy. It is requested only when the user taps "Use my location". There is no geo-IP and no third party.
- **P3** `volume = annualPassengers / max(annualPassengers)` over that side's airports. The figures come from `fixtures/airports.json`, a **separate file we added**; `flights.json` is untouched. A copy ships inside the app (owner decision D15), so ranking is the same online and offline, and a test keeps the copy equal to the file.
- **P4 Fallback to the original data.**
  - With no passenger figures at all, `volume = flights per airport in flights.json / max`.
  - If only some airports have passenger figures, the rest use their flight counts (`VolumeSource.Mixed`). This is what happens if the API returns an airport the app has no figure for.
  - Search and ranking always work with `flights.json` alone. *(assumption: "fall back to the original data" means deriving volume from the flight counts in `flights.json`)*
- **P5** The weights are origin 0.6 proximity / 0.4 volume (you usually fly from somewhere near you), and destination 0 / 1 (owner decision D19: your location says nothing about where you're flying to, so it never affects the destination field). *(assumption: the origin split and the 250 km in P2 are borrowed from public sources, SUBMISSION_NOTES §4.4)*
- **P6 Order of matches:** match score ↓, then priority ↓, then code ↑. A blank query is ordered by priority ↓, then code ↑.
- **P7** Priority **never reorders the flight list** (owner decision D17, option A). It only orders the matched airports: the hint line (S13) and the suggestions (S14). The flight list always keeps O1 (date, then points).

## 3. Filters (F)

- **F1** The date range includes both ends: `start ≤ departureDate ≤ end`.
- **F2** Each bound is optional, and `null` means unbounded.
  - The default is both bounds `null`. The UI displays them as the dataset's min and max dates, so the full fixture range is the starting state and results show immediately. *(assumption)*
  - If the user picks a bound equal to the dataset bound, store `null` instead.
  - Because of this, the default range follows the data after a refresh.
- **F3** The UI cannot produce `start > end`, because the pickers constrain each other. If it happens anyway, the domain returns zero results; it does not swap the dates.
- **F4** The maximum points limit is inclusive: `points ≤ maxPoints`. `null` means no limit.
  - The UI slider spans the dataset's [min, max] points in steps of 500.
  - Dragging to the top end stores `null`, displayed as "Any".
- **F5** Cabin is a **single** choice, and `null` means any cabin. The options come from the data and are ordered by rank (DM1). *(assumption: the brief says "Cabin." in the singular, but explicitly allows multiple selections for programs)*
- **F6** Programs are a multi-select set, and an empty set means all programs. A flight matches when its program is in the set (exact string compare against the option values). The options come from the data and are sorted alphabetically.
- **F7** All filters and both search fields combine with **AND**.
- **F8** Filter options (date bounds, points bounds, cabins, programs) come from the **cached dataset**, not from the current results, so options don't disappear while the user filters. After a refresh, selected values that no longer exist in the dataset are dropped from the selection.

## 4. Ordering (O)

- **O1** Results are sorted by `departureDate` ↑, then `points` ↑, then `id` ↑. The order is deterministic, and we never rely on the order in the file.
- **O2** The flights in a marker sheet are sorted by `points` ↑, then `departureDate` ↑, then `id` ↑, so the "from" price shown on the marker is the first row.
- **O3** The Saved list is sorted by `departureDate` ↑, then `id` ↑.

## 5. Count, active filters, reset (C)

- **C1** `resultCount` is the size of `results` after search and filters. The platform UI handles pluralisation ("1 flight" vs "N flights").
- **C2** `activeFilters` has one entry per non-default filter, in a fixed order: Dates, Cabin, Max points, Programs.
  - Search text is not a chip, because it is already visible in its own field.
  - Search text does count toward "anything active?", which decides whether Reset is shown.
- **C3** Each active filter can be cleared on its own.
- **C4** "Reset" empties both search fields and returns every filter to its default. It doesn't touch saved flights. After a reset, results are the full dataset, so any selection survives L3. *(assumption)*

## 6. Map (M)

- **M1** Markers are built from **exactly** the same `results` list as the list view.
- **M2** There is one marker per destination code present in `results`, positioned at the destination's coordinates.
- **M3** Each marker carries the destination code, city, `flightCount` and `minPoints` over its flights in `results`.
- **M4** Markers are ordered by code, which keeps them deterministic. When they overlap, the selected flight's destination is drawn on top.
- **M5** Tapping a marker opens that destination's flights in O2 order.
  - The sheet updates live as filters change.
  - If the destination drops out of the results, the sheet closes.
- **M6** If `results` is empty, there are no markers and an empty-state overlay is shown. The map stays interactive.
- **M7** A selected flight draws a **great-circle** route connector from origin to destination. The origin is drawn as a small dot and the destination marker is highlighted.
  - A shared `GreatCircle.interpolate(from, to, segments = 64)` provides the points for map SDKs that can't draw geodesics.
  - It unwraps longitudes (adding or subtracting 360° when consecutive points jump more than 180°), so trans-Pacific routes don't wrap the long way around the world.
- **M8** Camera behaviour:
  - On first display, fit all markers.
  - **Never** move the camera automatically when filters change, so the user's pan and zoom are respected.
  - On an explicit selection, animate to fit the route.

## 7. Selection (L)

- **L1** The shared store holds a single discovery selection, `selectedFlightId`. The list and the map both read it.
- **L2** The user selects a flight by tapping a list row or a flight in a marker sheet. Tapping the selected row again, or the card's close button, deselects it.
- **L3** Every time the results are recomputed, a selected id that is no longer in the results is cleared. The clearing is permanent: the selection does not come back when the filter is undone. *(assumption)*
- **L4** Switching between List and Map never changes the selection.
- **L5** The selection is not persisted across launches.
- **L6** The selection card shows:
  - the origin (code + city) and destination (code + city), joined by a route connector graphic,
  - the departure date,
  - the cabin,
  - the points price,
  - the loyalty program,
  - a Save or Remove button that reflects the current saved state.

  In Map mode, the great-circle line from M7 is drawn as well. *(assumption: "route connector" is satisfied by the map line and also by the connector graphic in the card)*
- **L7** The Saved view has its own selection, `savedSelectedId`. It is independent of the discovery filters and is cleared when that flight is removed. *(assumption)*

## 8. Saved flights (V)

- **V1** Saved flights are stored as **full snapshots** (every `Flight` field) plus `savedAt`, in their own table, independent of the cache.
- **V2** Saving is an **upsert by id**. Saving an already-saved id updates its details and leaves exactly one row. `savedAt` keeps its original value.
- **V3** Every `FlightItem` has `isSaved`, which is true when its id is among the saved ids. The list, map, card and Saved view all derive it from the same source.
- **V4** Saved flights stay in the Saved view when they are missing from the latest API response. When a cache exists and doesn't contain the id, the flight is labelled "No longer listed".
- **V5** On a successful refresh, any saved snapshot whose id appears in the response is updated with the new details, in the same transaction as the cache replacement. *(assumption: keeps details consistent between the Saved view and discovery)*
- **V6** Removal is by id. Save and remove are local-only operations, so both work offline.
- **V7** **There is no optimistic UI.**
  - The saved state only changes when the database emits.
  - While a write is pending, its button shows progress and is disabled.
  - On failure the state is unchanged, and a `UserMessage` reports the error with a Retry action.
- **V8** The Saved list ignores the discovery search and filters.

## 9. Refresh, cache & offline (R)

- **R1** On launch, show the cached data from the database immediately, without waiting for the network. Then start a refresh in the background.
- **R2** A refresh is triggered by app launch, by pull-to-refresh, and by Retry. Only one refresh runs at a time; requests made while one is running are ignored.
- **R3** **Success** means an HTTP 2xx status and a valid envelope. On success:
  - Replace the cache **atomically** in one transaction: delete all rows, insert all flights, set `lastSuccessAt`.
  - Then apply V5.
- **R4** A successful response with `"flights": []` **is a success**. The cache becomes empty, `lastSuccessAt` is set, and the UI shows "No flights available". It is never shown as an error. *(assumption: a 200 response is authoritative)*
- **R5** **Failure** means any of: no connection or connection refused (for example, the API is stopped), a timeout, a non-2xx status, or an invalid body. On failure, the cache and saved flights are left untouched.
- **R6** On failure after at least one successful sync:
  - Keep showing the cached data, with a stale indicator: "Couldn't refresh · showing results from \<lastSuccessAt\>", plus Retry.
  - Search, filters, map, save and remove all keep working.
- **R7** On failure when no sync has ever succeeded, show a full-screen error that explains the cause, with Retry.
- **R8** Whether data was "never synced" or "synced but empty" is decided by whether `lastSuccessAt` exists, **not** by the row count.
- **R9** The error kinds are `Offline` (unreachable, refused, DNS failure, no network), `Timeout`, `Server(status)`, `InvalidData` and `Unknown`. Each has its own copy in UI_SPEC §4.
- **R10** Timeouts are 5 s to connect and 15 s per request.
- **R11** The stale indicator clears after the next successful refresh. A refresh while data is on screen shows an inline progress indicator, never a full-screen loader.
- **R12** Coroutine cancellation is never reported as an error: always rethrow `CancellationException`, and never swallow it in `runCatching`.

## 10. Formatting (T)

- **T1** Full points use a shared formatter with en-US grouping: `25,000 pts`.
- **T2** Compact points on markers come from the shared formatter: `9k`, `25k`, `101.5k`. Values below 1,000 are shown as they are.
- **T3** Departure dates are **calendar dates**. Format them from year/month/day components on each platform, never through an instant or timezone; otherwise `2027-02-01` can render as "Jan 31" in the Americas.
  - Rows show `Mon, Feb 1`.
  - The card shows `Mon, Feb 1, 2027`.
  - Dates cross the KMP boundary as ISO `yyyy-MM-dd` strings.
- **T4** `lastSuccessAt` **is** an instant (epoch milliseconds). Format it in local time, for example "today 3:42 PM".

## 11. Scale notes

The rules are written so that their cost stays predictable as the data grows (REQUIREMENTS §11, ARCHITECTURE §12).

| Area | Cost | At 10× / 100× the data |
|---|---|---|
| Search (S, P) | Prefix: O(m) trie walk. Fuzzy: inverted-index lookups, then edit distance on the candidates only. Ranking: one priority per airport, precomputed. All of it runs on **airports**, not flights. | Trie memory grows with the total length of the names (≈ 50k nodes for 5k airports, est.). With many thousands of airports, cap the stored ids per node to the top K by priority, and move fuzzy candidate generation to the server. |
| Filters (F) | Per-flight predicates: O(n) per input change, off the main thread | Same rules evaluated as indexed SQL `WHERE` clauses. Semantics and tests unchanged. |
| Ordering (O) | O(n log n), once per data change, because filtering keeps the order (D6). Every ordering ends with an `id` tiebreak | The deterministic order makes paging safe |
| Map (M) | O(n) grouping by destination | Beyond ~200 markers, clustering changes the *rendering*, not M1–M3 |
| Selection (L) | O(1) membership check against the result ids | Unchanged |
| Saved (V) | One upsert or delete per id; independent of cache size | Unchanged; the Saved tab pages |
| Refresh (R) | Full replace is O(n) in one transaction | Incremental sync keeps R3's atomicity: one transaction per sync |

