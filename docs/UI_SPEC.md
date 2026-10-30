# UI spec

Roame is a *functional* reference, not a pixel spec. The goal is a calm, fast, information-dense award-search app:

- The points price is the hero number.
- The route is instantly scannable.
- Every state is explained.

Both platforms follow the same information architecture and copy, but each uses native idioms: SwiftUI system controls on iOS, and Material 3 on Android.

## 1. Information architecture

```
Tab bar
├── Explore                       ← discovery
│   ├── Search header: From / To fields
│   ├── Filter bar: [Dates] [Cabin] [Points] [Programs]  ·  Reset
│   ├── Summary line: "238 flights · 2 filters"
│   ├── Segmented control: List | Map      (switching never changes the selection, L4)
│   ├── Status banner (stale / refreshing), when relevant
│   ├── Content: List  or  Map
│   └── Selection card (overlay at the bottom, shared by List and Map)
└── Saved (badge = count)         ← always reachable, ignores filters (V8)
    ├── Saved list
    └── Saved card (its own selection, L7)
```

## 2. Wireframes

### 2.1 Explore: List

```
┌────────────────────────────────────────┐
│ Explore                          ⟳     │
│ ┌────────────────────────────────────┐ │
│ │ ✈︎ From   city or airport        ⓧ │ │
│ ├────────────────────────────────────┤ │
│ │ ⚑ To     city or airport        ⓧ │ │
│ └────────────────────────────────────┘ │
│ (Feb 1 – Apr 1)(Business ✓)(≤ 60k ✓)(Programs ▾)  Reset │
│ 238 flights · 2 filters                │
│ ┌──────── List ────────┬──── Map ────┐ │
│ ⚠ Couldn't refresh · results from 3:42 PM   Retry │
│ ┌────────────────────────────────────┐ │
│ │ BOS ───────✈─────── CDG  25,000 pts│ │
│ │ Boston        Paris     Prem. Econ.│ │
│ │ Mon, Feb 1 · Avios              ♡  │ │
│ └────────────────────────────────────┘ │
│   … rows (selected row = tinted + accent border) …
│ ┌────────── selection card ──────────┐ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│   ◉ Explore             ♡ Saved (3)    │
└────────────────────────────────────────┘
```

### 2.2 Explore: Map

```
┌────────────────────────────────────────┐
│ (same header, filter bar, summary, segmented control)
│                                        │
│        ╭─────────────╮                 │
│        │ CDG  12·25k │   ╭───────────╮ │
│        ╰──────▾──────╯   │ NRT 55·19k│ │
│   •BOS ⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒⌒▶ (highlighted)  │
│                                        │
│ ┌────────── selection card ──────────┐ │
└────────────────────────────────────────┘
Tap a marker → sheet (half height):
  "Paris · CDG — 12 flights from 25,000 pts"
  rows ordered by points (O2); tap a row → select, collapse the sheet, draw the route, fit the camera (M8)
```

### 2.3 Selection card (the same component in List, Map and Saved)

```
┌──────────────────────────────────────────────┐
│ Selected flight                           ✕  │
│  BOS  ●──────────── ✈ ────────────●  CDG     │  ← route connector graphic
│  Boston                          Paris       │
│                                              │
│  Mon, Feb 1, 2027  ·  Premium Economy        │
│  Avios                                       │
│  25,000 pts                                  │  ← hero number
│  [  ♡  Save flight  ]  /  [  ♥  Remove  ]    │  ← spinner + disabled while pending (V7)
└──────────────────────────────────────────────┘
In the Saved view, add "No longer listed" when the flight isn't in the latest results (V4).
```

### 2.4 Filter sheets

| Filter | Control | Notes |
|---|---|---|
| Dates | Two date pickers (Start and End), limited to the dataset bounds and to each other | Shows "Feb 1 – Apr 1" by default. "Any dates" resets to the defaults. |
| Cabin | Segmented or radio: Any · Economy · Premium Economy · Business | Single choice (F5) |
| Points | Slider from min to max in steps of 500, with a live label "Up to 60,000 pts" or "Any" | The top end means no limit (F4) |
| Programs | Checklist of every program, with "Clear" | Multi-select. Empty means all (F6). |

Each sheet shows a live **"Show N flights"** button. N is the count if the user applied the current choice.

## 3. Components

| Component | Purpose | States |
|---|---|---|
| `SearchField` | From / To text input | empty · typing · has text (ⓧ clears it) |
| `FilterChip` | Opens a filter sheet | default · active (filled, accent, shows the value) · pressed |
| `ResultSummary` | "N flights · K filters" + Reset | Reset is hidden when nothing is active |
| `FlightRow` | One result | default · pressed · selected · saved (filled heart) |
| `RouteConnector` | `ORIGIN ●──✈──● DEST` graphic | Used in the row (compact) and in the card |
| `DestinationMarker` | Map pill showing `CODE count·minPts` | default · selected (accent, on top) · pressed |
| `FlightCard` | Selection details + Save / Remove | idle · pending · error message shown |
| `StatusBanner` | Stale, offline or refreshing info + Retry | stale · refreshing (inline progress) |
| `EmptyState` / `ErrorState` | Full-area explanations | See §4 |
| `Snackbar` / toast | One-shot `UserMessage` | "Couldn't save flight" + Retry |

## 4. State catalogue

Use the same copy on both platforms.

| State | When (rule) | What the user sees |
|---|---|---|
| First load | `DataStatus.Loading` | 6–8 skeleton rows (List) or a centred spinner (Map), with the label "Finding flights…" |
| Ready | `Ready`, has results | Results. No banner when the last refresh succeeded. |
| Refreshing | `Ready.isRefreshing` | Pull-to-refresh indicator or a thin progress bar. Content stays interactive. |
| Stale / offline | `Ready.refreshError != null` (R6) | Banner: "Couldn't refresh · showing results from 3:42 PM" + **Retry**. Results stay usable. |
| Unavailable: offline | `Unavailable(Offline)` (R7) | Icon + "Can't reach Flight Explorer" + "Make sure the local API is running and you're connected, then try again." + **Try again** |
| Unavailable: timeout | `Unavailable(Timeout)` | "The server took too long to respond." + **Try again** |
| Unavailable: server | `Unavailable(Server(503))` | "The server had a problem (503)." + **Try again** |
| Unavailable: bad data | `Unavailable(InvalidData)` | "We received flight data we couldn't read." + **Try again** |
| Synced empty | `Ready` and the cache is empty (R4) | "No flights available right now" + "The server returned no flights. Pull to refresh later." No error styling. |
| Filtered empty | `Ready`, cache not empty, `results` empty | "No flights match your search" + the list of active filters + **Reset filters** |
| Map empty | Same as filtered empty, in Map mode | The map stays visible with a floating card: "No flights match · Reset filters" |
| Saved empty | `saved` is empty | "No saved flights yet" + "Tap ♡ on any flight to keep it here — even offline." |
| Save pending | id in `pendingWriteIds` | The button shows a spinner and is disabled |
| Save or remove failed | A `UserMessage` arrives (V7) | Snackbar: "Couldn't save flight." / "Couldn't remove flight." + **Retry**. The heart stays in its previous state. |
| Not listed | A saved flight missing from the cache (V4) | A grey "No longer listed" tag in the Saved row and card |

## 5. Interaction details

- **Typing** updates results live, without a submit button. The keyboard's Search or Done key dismisses the keyboard. Scrolling the list also dismisses the keyboard.
- **Tapping a row** selects it, highlights it and shows the card. Tapping the selected row again, or ✕, deselects it.
- **Save / Remove** gives haptic feedback on success: `.sensoryFeedback(.success, …)` on iOS and `HapticFeedbackType.LongPress` on Android. On failure, show the snackbar with no haptic.
- **Pull to refresh** on the List (`.refreshable` on iOS, `PullToRefreshBox` on Android). Map mode has a ⟳ button in the toolbar.
- **Reset** gives a light animation as the chips clear. Selection behaviour follows C4 and L3.
- **Map:**
  - Marker tap → sheet.
  - Sheet row tap → select.
  - Tapping empty map does nothing; it does *not* deselect, to avoid surprise.
  - The camera only moves on the first display and on explicit selection (M8).
- **List ↔ Map** uses a cross-fade. The selection and filters are untouched. The list's scroll position is kept while the list is in the composition or view tree.
- **Back / swipe-to-dismiss** closes sheets. Android's back gesture closes a sheet first, then deselects, then exits.

## 6. Design tokens

Use the same tokens on both platforms, mapped to native APIs.

**Spacing:** 4 · 8 · 12 · 16 · 24 · 32. Screen padding is 16, card padding 16, the gap between rows 8, and the gap between sections 24.

**Corner radius:** chips 999 (pill) · rows and cards 16 · sheets use the system default.

**Typography** (system fonts; points use tabular or monospaced digits):

| Role | iOS (SwiftUI) | Android (M3) |
|---|---|---|
| Screen title | `.largeTitle.bold()` | `headlineMedium` |
| Points hero (card) | `.title2.weight(.semibold).monospacedDigit()` | `headlineSmall` + `tnum` |
| Points (row) | `.headline.monospacedDigit()` | `titleMedium` + `tnum` |
| Airport codes | `.headline` | `titleMedium` |
| City, date, program | `.subheadline`, `.secondary` | `bodyMedium`, `onSurfaceVariant` |
| Chips and buttons | `.subheadline.weight(.medium)` | `labelLarge` |
| Captions and banner | `.footnote` | `labelMedium` |

**Colour roles.** Use system colours so light and dark mode come for free.

| Role | iOS | Android |
|---|---|---|
| Accent (selection, active chips, Save) | App `AccentColor`, a deep blue | `primary` (derived from the same seed colour) |
| Surface / card | `.background` / `secondarySystemGroupedBackground` | `surface` / `surfaceContainer` |
| Secondary text | `.secondary` | `onSurfaceVariant` |
| Saved (heart) | `.pink` / `systemPink` | A custom `saved` colour token |
| Warning (stale banner) | `.orange` at 15% background opacity | `tertiaryContainer` |
| Error | `.red` | `error` / `errorContainer` |

**Cabin tags:** Economy is neutral grey, Premium Economy is teal, and Business is indigo. Each tag is a small capsule with a tinted background and full-colour text.

## 7. Formatting

Follow DOMAIN_RULES §10. Points come from the shared formatter: `25,000 pts` in full and `25k` in compact form. Dates are formatted from components (`Mon, Feb 1`). Counts are pluralised by the platform: "1 flight" vs "238 flights".

## 8. Accessibility

- Support Dynamic Type and font scaling up to the largest accessibility size. Rows reflow to a vertical layout instead of truncating the points.
- Touch targets are at least 44 pt on iOS and 48 dp on Android, including the heart icon and chips.
- **VoiceOver / TalkBack labels:**
  - Row: "Boston to Paris, Monday February 1, Premium Economy, Avios, 25,000 points, saved."
  - Marker: "Paris, 12 flights, from 25,000 points."
  - Chips announce "Cabin: Business, active".
- Colour is never the only signal. Selected rows also get a border and a checkmark or "Selected" accessibility trait.
- Respect Reduce Motion: no camera fly-to animation and no cross-fade.

## 9. Platform notes

| | iOS (SwiftUI, iOS 17) | Android (Compose, M3) |
|---|---|---|
| Shell | `TabView` + `NavigationStack` | `Scaffold` + `NavigationBar` |
| Sheets | `.sheet` + `.presentationDetents([.medium, .large])` | `ModalBottomSheet` |
| List | `List` or `LazyVStack` with `id: \.id` | `LazyColumn` with `key = { it.id }` |
| Map | `Map(position:)` + `Annotation` + `MapPolyline(coordinates:contourStyle: .geodesic)` | MapLibre: symbol layer for markers, line layer with `GreatCircle` points for the route |
| Refresh | `.refreshable` | `PullToRefreshBox` |
| Messages | Overlay toast with Retry | `SnackbarHost` with an action |
| Date picker | `DatePicker(…, displayedComponents: .date)`. Convert with `Calendar.current.dateComponents([.year, .month, .day], from:)`. | `DateRangePicker`. The millis are **UTC** midnight, so convert with UTC, never the local zone. |
| State | `@Observable ExplorerModel` via `.environment` | `collectAsStateWithLifecycle()` |

## 10. Scalability

The fixture fits on screen comfortably. These rules keep the UI usable when the data grows (REQUIREMENTS §11):

| Element | Today | When the data grows |
|---|---|---|
| Result list | Virtualised (`LazyColumn` / `List`) with stable ids | Unchanged. If the API paginates, add a "Loading more…" footer, and the summary reads "1,000+ flights". |
| Map markers | 24 individual pills | Beyond ~200 markers, cluster by zoom. A cluster bubble shows the total count and the lowest points; tap it to zoom in. |
| Marker sheet | Lists every flight for the destination | Virtualised, with the count in the header |
| Programs filter | Checklist of 5 | Beyond ~15 options, a searchable list with the selected items pinned on top |
| Active filter chips | Fit on one row | Scroll horizontally, with a "+N" overflow chip that opens the full list |
| Saved tab | Small list | Virtualised; grouped by month when long |
