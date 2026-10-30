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

- **S1** Normalise each query: trim it, lowercase it (locale-invariant `lowercase()`), and collapse internal runs of whitespace to a single space.
- **S2** A blank query matches every flight.
- **S3** The origin field matches only the flight's origin airport, and the destination field only its destination airport. When both are filled, a flight must match both (AND).
- **S4** A normalised query matches an airport when it is a **prefix** of any of the following:
  - the airport code,
  - the city name,
  - any word in the city name.

  *(assumption: prefix rather than substring, so typing narrows results predictably and `"a"` doesn't match every city that contains an "a")*
- **S5** Examples. These are also the test table:

  | Field | Query | Matches | Why |
  |---|---|---|---|
  | origin | `"  BOS "` | BOS | trim + case |
  | origin | `"bo"` | BOS | code prefix, and the city "Boston" |
  | origin | `"york"` | JFK | prefix of a word in "New York" |
  | origin | `"new   york"` | JFK | internal whitespace is collapsed |
  | origin | `"angeles"` | LAX | prefix of a word in "Los Angeles" |
  | origin | `"san"` | SFO | "San Francisco" |
  | destination | `"tokyo"` | HND, NRT | the city has two airports |
  | destination | `"nrt"` | NRT | code |
  | destination | `"ondon"` | nothing | not a prefix |
  | origin | `"paris"` | nothing | Paris only appears as a destination |
- **S6** There is no fuzzy matching or accent folding, so `"são paulo"` does not match "Sao Paulo". *(known limitation; stretch goal: fold common Latin diacritics on both sides)*

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
| Filters (F) | Per-flight predicates: O(n) per input change, off the main thread | Same rules evaluated as indexed SQL `WHERE` clauses. Semantics and tests unchanged. |
| Ordering (O) | O(n log n). Every ordering ends with an `id` tiebreak | The deterministic order makes paging safe |
| Map (M) | O(n) grouping by destination | Beyond ~200 markers, clustering changes the *rendering*, not M1–M3 |
| Selection (L) | O(1) membership check against the result ids | Unchanged |
| Saved (V) | One upsert or delete per id; independent of cache size | Unchanged; the Saved tab pages |
| Refresh (R) | Full replace is O(n) in one transaction | Incremental sync keeps R3's atomicity: one transaction per sync |

