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
  - *Engine and store done; the UI comes with the screens.*
- **S14 Suggestions** (owner decision D20). While a field has focus and text, both apps show a dropdown of matching airports under it, updated on every keystroke.
  - The rows are that field's ranked matches from the search engine, in P6 order. They include fuzzy matches and never the other side's airports (S3). Each row shows the city and code, for example "Tokyo (HND)", and each airport gets its own row.
  - **City row** (owner decision, following Roame). When two or more matches share a city, one row for all of them, for example "Tokyo (all airports)", comes directly above that city's first airport row. It comes from the data, so no city is hardcoded, and `nrt` shows no city row because it matches one airport.
  - At most 5 rows are shown, counting city rows. *(assumption)*
  - Tapping a row picks exactly what it names and closes the dropdown. The field shows "Tokyo (HND)" and flights are filtered by that code alone, or it shows "Tokyo (all airports)" and flights are filtered by that city's matched codes. Editing the text clears the pick and returns to normal search. A pick is needed because the text alone isn't enough: `dub` also matches Dubai (DXB). *(assumption)*
  - A blank query or `NoMatch` shows no dropdown.
  - A pick counts as search text for Reset (C2, C4), and Reset clears it.
  - The store holds a pick as its city and codes, and each app writes the text ("Tokyo (HND)", "Tokyo (all airports)"), because copy lives in the apps. A pick of codes that aren't on that side is ignored. If a refresh removes a picked airport, the field goes back to searching its text, for good. *(assumption)*
  - The S13 hint stays, because it still explains fuzzy results after the dropdown closes. A pick shows no hint.
- **S15 Swap** (Step 6.3c, the search page's ⇅ button). `swapSides()` trades From and To: each field's text and pick move to the other field. A pick goes with its airports when the other side has them. Where it doesn't, that field searches the pick's city name instead, so the swap still reads as the same two places ("From Tokyo, To Toronto"), and S14 drops the pick. Swapping twice gives the same route back, by city name if a pick was dropped. Filters don't change. *(assumption: in the fixture no airport is both an origin and a destination, so a swapped route of picks always finds no flights, and the filtered-empty state says so)*

## 2b. Prioritisation (P)

Implemented in `AirportRanking`. Priority **orders** matched airports. It never adds or removes matches.

- **P1** Priority is a value in [0, 1]:

  `(w_proximity · proximity + w_volume · volume) / (w_proximity + w_volume)`
- **P2** `proximity = 0.5^(distance_km / 250)`, so it halves every 250 km. The distance is the great-circle (haversine) distance from the user's location to the airport.
  - Without a location (unknown, permission denied, or not available yet), priority is volume only.
  - The location comes from **the device** (owner decision D16): Fused Location Provider on Android and Core Location on iOS, at approximate accuracy. It is asked for when the app first opens (D32, which replaced D30's first focus of From), and never again automatically after a denial. Once allowed, each launch reads it again without asking. There is no geo-IP and no third party. The same location picks the home airport for the landing page's deals (B5).
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
- **F3** The UI cannot produce `start > end`, because the pickers constrain each other. If it happens anyway, the domain returns zero results; it does not swap the dates. A date that isn't ISO `yyyy-MM-dd` is ignored, which the pickers can't produce either. *(assumption)*
- **F4** The maximum points limit is inclusive: `points ≤ maxPoints`. `null` means no limit.
  - The UI slider spans the dataset's [min, max] points in steps of 500.
  - Dragging to the top end stores `null`, displayed as "Any".
- **F5** Cabin is a **single** choice, and `null` means any cabin. The options come from the data and are ordered by rank (DM1). *(assumption: the brief says "Cabin." in the singular, but explicitly allows multiple selections for programs)*
- **F6** Programs are a multi-select set, and an empty set means all programs. A flight matches when its program is in the set (exact string compare against the option values). The options come from the data and are sorted alphabetically.
- **F7** All filters and both search fields combine with **AND**.
- **F8** Filter options (date bounds, points bounds, cabins, programs) come from the **cached dataset**, not from the current results, so options don't disappear while the user filters. After a refresh, selected values that no longer exist in the dataset are dropped from the selection. A date or points limit at or past the dataset's own limit also returns to its default, because it filters nothing. *(assumption)*

## 3b. Date calendar (K)

Implemented in `presentation/DateCalendar.kt` (owner decision D41: written once in shared code, so both apps' calendars behave the same). Each app keeps a `DateSelection` while its calendar is open, applies each tap with `setDateRange` straight away (like the other filters), and draws the months it's given. The apps write the month names and weekday letters.

- **K1** The calendar shows every month from the data's first date to its last (F8), in order. Each month is weeks of seven days, **Sunday first**; the days before the 1st and after the last day are the neighbouring months' days, marked as outside the month, and the apps leave those cells empty. *(assumption: weeks start on Sunday, as in en-US, the only locale the app formats for, T1)*
- **K2** Only the data's dates can be picked. A tap on any other day changes nothing.
- **K3** Tapping: with a start whose end is awaited, a day on or after the start ends the range (the same day gives a one-day range). Any other tap starts a new range at that day and awaits its end. While the end is awaited, the filter is that day onward (F2: no end bound).
- **K4** Marks: while the end is awaited, only the start is marked. Otherwise the range runs from the start, or the data's first date, to the end, or the data's last date (F2), so with no date filter the whole range is marked. Its first day is `Start`, its last `End`, one day alone `Single`, and the days between `Between`. Days outside the month are never marked.
- **K5** The calendar opens on the current filter with no end awaited, so the first tap always starts a new range. "Any dates" clears the date filter (C3) and the awaited start.

## 4. Ordering (O)

- **O1** Results follow the chosen sort (D46: the owner's addition, as the brief asks for none). The default is **Lowest points**. Every order ends with `departureDate` ↑, then `id` ↑, so it's deterministic and never relies on the order in the file.
  - **Lowest points:** `points` ↑.
  - **Highest points:** `points` ↓.
  - **By program:** programs in F6's order (alphabetical, so "Mileage Plan" comes before "MileagePlus"), then `points` ↑. The list shows a heading per program with its flight count (`ExplorerState.sections`).
  - **Best offer:** B4's value ↑, so the flight most below its cabin's typical points per 1,000 km comes first, whatever its cabin. Then `points` ↑. A flight with no value (B2) goes last.
  - Each sort's order is made once per data change, the first time it's used (D6).
- **O2** The flights in a marker sheet are sorted by `points` ↑, then `departureDate` ↑, then `id` ↑, so the "from" price in its title is the first row. This holds whatever the sort. *(assumption)*
- **O3** The Saved list is sorted by `departureDate` ↑, then `id` ↑.
- **O4** The sort isn't a filter. It has no chip, doesn't count on the Filters badge or towards Reset, and Clear all, Reset and a new search all keep it. Changing it keeps the selection, and the list starts again from its top. It lasts while the app runs and isn't saved between launches. *(assumption)*

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
- **M3** Each marker carries the destination code, city, `flightCount` and `minPoints` over its flights in `results`. The pills draw the code and the count; `minPoints` is in their spoken labels and the destination's sheet (D44).
- **M4** Markers are ordered by code, which keeps them deterministic. When they overlap, the selected flight's destination is always drawn on top. Android gives it the higher symbol sort key. SwiftUI's map has no z-order and MapKit stacks pills by latitude, so iOS makes it the map's own selection, which MapKit draws above the rest (owner's review of Step 6.3d).
- **M5** Tapping a marker opens that destination's flights in O2 order.
  - The sheet updates live as filters change.
  - If the destination drops out of the results, the sheet closes.
- **M6** If `results` is empty, there are no markers and an empty-state overlay is shown. The map stays interactive.
- **M7** A selected flight draws a **gentle arc** as its route connector, from origin to destination (D50, the owner's review of Step 6.3f; until then a great circle). The origin is drawn as a small dot and the destination marker is highlighted. The route is drawn above the other pills and under the destination's pill (owner's review of Step 6.3d).
  - The arc isn't the flight's real path. On the flat, north-up map it's the straight line from origin to destination, bent into a quadratic curve whose middle sits a tenth of the line's length to the north, or to the east for a route due north or south. A great circle bows far towards the pole on this map (JFK → HKG's middle is at 79°N), so it looked too curved and could leave the screen; the arc stays within a tenth of its length of the straight line, so fitting the route (M8) shows all of it.
  - A shared `RouteArc.interpolate(from, to, segments = 64)` provides the points for both maps.
  - It unwraps longitudes (adding or subtracting 360° to keep every point within 180° of the origin), so trans-Pacific routes don't wrap the long way around the world. A shortest path never spans more than 180° of longitude, so this also rules out any jump between consecutive points.
- **M8** Camera behaviour:
  - On first display, fit all markers, unless a flight is already selected, as after a best offer (B9): then fit its route.
  - **Never** move the camera automatically when filters change, so the user's pan and zoom are respected.
  - On an explicit selection, animate to fit the route, once the sheet has dropped to its peek and the card has been measured, so the route lands above them.
- **M9** The selected route is drawn as still dots, with a plane flying along them from the origin to the destination (Step 6.3d, owner's design; D43 replaced the moving dots after the owner's review).
  - `PlaneFlight.at(elapsed)` gives each frame. A flight takes 3.6 s, easing out of the origin and into the destination (smoothstep). The plane fades in over the first 3% of the route and out between 80% and 92%, so it has gone before the destination's pill, which covers the end of the route. It waits 0.4 s out of sight, then flies again. Each new selection takes off from the origin.
  - `RouteArc.plane(from, to, fraction)` gives the plane's position that fraction of the way along the arc (halfway, it's the middle of `interpolate`'s points), with its longitude back in -180..180. It also gives the heading: the arc's direction there as a north-up Web Mercator map draws it, in degrees clockwise from north, so the plane climbs away from the origin and descends into the destination. Both maps stay north-up and flat, so the heading holds.
  - Under Reduce Motion the plane rests halfway, fully visible (`PlaneFlight.resting`).

## 6b. Landing page deals (B)

Implemented in `domain/Deals.kt` (`DealIndex`), built once per data change in `index()`, and exposed as `ExplorerState.landing`. The owner left the method to Claude (D35). Points alone favour short hops, so a deal is a flight that costs few points **for its distance**, compared with others in the same cabin. The deals ignore the search and filters, like Saved (V8).

- **B1** A flight's distance is the great-circle (haversine) distance between its two airports, as in P2.
- **B2** Its rate is points per 1,000 km: `points / (distanceKm / 1000)`. A flight shorter than 1 km, or with no points, has no rate and is never a deal. *(defensive: the fixture has neither)*
- **B3** A cabin's typical rate is the median rate of every cached flight in that cabin; for an even count, the mean of the middle two. It comes from the whole cache, like F8, so it doesn't move while the user filters.
- **B4** A flight's value is `rate / typical rate of its cabin`; lower is better. `percentBelowTypical = floor((1 − value) × 100)`, and 0 when the flight isn't below typical. The apps show it only above 0.
- **B5** **Home airport (D32).** When the user's location is known, the home airport is the origin airport nearest to it (B1's distance; ties by code), and every deal comes from flights leaving it. Without a location, the deals come from every cached flight. *(assumption: "location-aware" means deals from the origin nearest you, however far away it is; the data only has North American origins)*
- **B6** *(removed, D49)* There was an estimated cash value, from Roame's average cents per point by cabin. The owner dropped it: it's too inaccurate to show without a lot of justification. B6 is kept as a number so the other rules keep theirs.
- **B7** **Best offer per program:** for each program among the candidates (B5), its best-value flight; ties by points ↑, then date ↑, then id ↑. Offers are ordered best value first, then by program name. Each offer's flight carries `isSaved` (V3).
- **B8** **Destination deals:** one per destination city among the candidates, so a city's airports are one deal (Tokyo is HND and NRT). Each has the city's codes, its lowest points, the best value among its flights (B4), and a photo key, `dest_` plus the city in lowercase ASCII with other characters as `_` (`dest_sao_paulo`), which each app uses to find its bundled photo (D33). Ordered best value first, then by city. At most 30. *(assumption: the cap keeps the deck short at any size; the fixture has 23 cities)*
- **B9** **Opening a deal starts a new search.** `openDestination(codes)` resets everything as C4 does, picks the deal's airports in To (S14) and, with a home airport, picks it in From. `openOffer(flightId)` resets the same way, picks the flight's origin and destination airports, and selects the flight (L1). An id that isn't one of the current offers changes nothing. *(assumption: a deal replaces the current search, as tapping one in Roame does)*

## 7. Selection (L)

- **L1** The shared store holds a single discovery selection, `selectedFlightId`. The list and the map both read it.
- **L2** The user selects a flight by tapping a list row or a flight in a marker sheet. Tapping the selected row again, or the card's close button, deselects it. The store does this, so both apps behave the same: selecting the selected flight again deselects it.
- **L3** Every time the results are recomputed, a selected id that is no longer in the results is cleared. The clearing is permanent: the selection does not come back when the filter is undone. *(assumption)*
- **L4** Switching between List and Map never changes the selection. Since Step 6.3d there is no switch: the list is a sheet over the map, and moving it (more list, more map) never changes the selection either.
- **L5** The selection is not persisted across launches.
- **L6** The selection card shows:
  - the origin (code + city) and destination (code + city), joined by a route connector graphic,
  - the departure date,
  - the cabin,
  - the points price,
  - the loyalty program,
  - a Save or Remove button that reflects the current saved state.

  In Map mode, the arc from M7 is drawn as well. *(assumption: "route connector" is satisfied by the map line and also by the connector graphic in the card)*
- **L7** The Saved view has its own selection, `savedSelectedId`. It is independent of the discovery filters and is cleared when that flight is removed. *(assumption)*

## 7b. Flight details (DT)

The details page is the owner's addition (Step 6.3f, D47): Roame opens one for a picked flight, and the brief asks for none.

- **DT1** `ExplorerState.selectedDetails` and `savedSelectedDetails` hold the page's figures for each selection (L1, L7), and are null when there's none. Each carries the `FlightItem`, so its `isSaved` follows V3, and:
  - `distanceKm`: B1's distance, rounded to the nearest kilometre;
  - `percentBelowTypical`: B4, against the cabin's typical rate over the whole cache (B3), wherever the user is. It's 0 when the flight isn't below typical, has no rate (B2), or its cabin has no flights in the cache, as can happen to a saved flight after a refresh;
  - `photoKey`: B8's key for the destination city.

  They're worked out from the flight's own fields, so a saved flight the cache no longer has (V4) still gets them.
- **DT2** The data has no departure or arrival times, no travel time and no aircraft, so the page shows placeholders: `XX:XX`, `Travel time: XX hr XX min` and `Aircraft TBD`, with a line saying they're placeholders. Screen readers say "not in the data". *(the owner's decision, D47)*
- **DT3** The page opens from the card's "View details" and follows its selection live. Its button is the card's: Save or Remove in Explore, Remove in Saved (L7), and the page updates as the card does. When its selection goes, the page closes: after Remove in Saved (L7), or when the filters or a refresh drop the flight from the results (L3). "Updated …" is `lastSuccessAt` (T4). *(assumption)*

## 8. Saved flights (V)

- **V1** Saved flights are stored as **full snapshots** (every `Flight` field) plus `savedAt`, in their own table, independent of the cache.
- **V2** Saving is an **upsert by id**. Saving an already-saved id updates its details and leaves exactly one row. `savedAt` keeps its original value.
- **V3** Every `FlightItem` has `isSaved`, which is true when its id is among the saved ids. The list, map, card and Saved view all derive it from the same source.
- **V4** Saved flights stay in the Saved view when they are missing from the latest API response. When a cache exists (at least one refresh has succeeded, R8) and doesn't contain the id, the flight is labelled "No longer listed".
- **V5** On a successful refresh, any saved snapshot whose id appears in the response is updated with the new details, in the same transaction as the cache replacement. *(assumption: keeps details consistent between the Saved view and discovery)*
- **V6** Removal is by id. Save and remove are local-only operations, so both work offline.
- **V7** **There is no optimistic UI.**
  - The saved state only changes when the database emits.
  - While a write is pending, its button shows progress and is disabled. It stays pending until the saved state has changed, because the database reports the change a moment after the write returns. Otherwise the button would briefly look finished with the old state.
  - A second tap while a write for that flight is pending is ignored.
  - On failure the state is unchanged, and a `UserMessage` reports the error with a Retry action. Retry calls `toggleSave` after a failed save and `removeSaved` after a failed remove.
- **V8** The Saved list ignores the discovery search and filters.

## 9. Refresh, cache & offline (R)

- **R1** On launch, show the cached data from the database immediately, without waiting for the network. Then start a refresh in the background.
- **R2** A refresh is triggered by app launch, and by Retry on the first-load error (R7). There is no manual refresh, such as pull to refresh (D29). Only one refresh runs at a time; requests made while one is running are ignored.
- **R3** **Success** means an HTTP 2xx status and a valid envelope. On success:
  - Replace the cache **atomically** in one transaction: delete all rows, insert all flights, set `lastSuccessAt`.
  - Then apply V5.
- **R4** A successful response with `"flights": []` **is a success**. The cache becomes empty, `lastSuccessAt` is set, and the UI shows "No flights available". It is never shown as an error. *(assumption: a 200 response is authoritative)*
- **R5** **Failure** means any of: no connection or connection refused (for example, the API is stopped), a timeout, a non-2xx status, or an invalid body. A valid response that can't be written to the database also counts as a failure, reported as `Unknown`. *(assumption)* On failure, the cache and saved flights are left untouched.
- **R6** On failure after at least one successful sync:
  - Keep showing the cached data, with a stale indicator: "Couldn't refresh · showing results from \<lastSuccessAt\>". It has no Retry (D29): the next launch refreshes again.
  - Search, filters, map, save and remove all keep working.
- **R7** On failure when no sync has ever succeeded, show a full-screen error that explains the cause, with Retry.
- **R8** Whether data was "never synced" or "synced but empty" is decided by whether `lastSuccessAt` exists, **not** by the row count.
- **R9** The error kinds are `Offline` (unreachable, refused, DNS failure, no network), `Timeout`, `Server(status)`, `InvalidData` and `Unknown`. Each has its own copy in UI_SPEC §4. A connection lost partway through a response is also `Offline`, because the app can't reach the server and any partial data is discarded. *(assumption)*
- **R10** Timeouts are 5 s to connect and 15 s per request. On iOS the 5 s limit has no effect, because Ktor's Darwin engine ignores connect timeouts, so a stalled connection gives up at 15 s with the same `Timeout` error.
- **R11** The stale indicator clears after the next successful refresh. A refresh while data is on screen shows an inline progress indicator, never a full-screen loader.
- **R12** Coroutine cancellation is never reported as an error: always rethrow `CancellationException`, and never swallow it in `runCatching`.

## 10. Formatting (T)

- **T1** Full points use a shared formatter with en-US grouping: `25,000 pts`.
- **T2** Compact points on markers come from the shared formatter: `9k`, `25k`, `101.5k`. Values below 1,000 are shown as they are. Anything else rounds to one decimal, with halves rounding up: `25,250` is `25.3k`. *(assumption: every fixture value is a multiple of 500, so this never shows today)*
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
| Deals (B) | Once per data change: one distance per flight, a median per cabin, and an O(n log n) sort, for every flight and per origin. A location only looks up the nearest origin's deals: O(origins) | Measured at 6 ms for 10k flights and about 150 ms for 100k, off the main thread (ARCHITECTURE §12). At 100k+, keep only each program's and city's best in one pass instead of sorting everything |
| Selection (L) | O(1) membership check against the result ids | Unchanged |
| Details (DT) | At most two selections per state change, each one distance and one lookup of its cabin's typical rate, kept from B3 | Unchanged: it never grows with the data |
| Saved (V) | One upsert or delete per id; independent of cache size | Unchanged; the Saved tab pages |
| Refresh (R) | Full replace is O(n) in one transaction | Incremental sync keeps R3's atomicity: one transaction per sync |

