# UI spec

Roame is a *functional* reference, not a pixel spec. The goal is a calm, fast, information-dense award-search app:

- The points price is the hero number.
- The route is instantly scannable.
- Every state is explained.

Both platforms follow the same information architecture and copy. Since Step 6.3a they also look the same (D38): one font (Inter, D40), the same Material Symbols icons (D37), and the app's own tab bar, segmented control, chips, buttons, toasts and empty states, drawn the same way in SwiftUI and Compose (§6). Only what the platforms can't share stays native: the maps (MapKit and MapLibre), sheets, the system date picker and slider until Step 6.3c, haptics and permission dialogs.

## 1. Information architecture

```
Tab bar
├── Explore                       ← discovery
│   ├── Search header: From / To fields
│   │     ├── Suggestions dropdown under the focused field, updated on every keystroke (S14)
│   │     ├── "Showing Sydney (SYD)" hint under a field when fuzzy matching was used (S13)
│   │     └── Location is asked for the first time From gets focus, with no button (D16, D30)
│   ├── Filter bar: [Dates] [Cabin] [Points] [Programs]  ·  Reset
│   ├── Summary line: "238 flights · 2 filters"
│   ├── Segmented control: List | Map      (switching never changes the selection, L4)
│   ├── Status banner (stale / refreshing), when relevant
│   ├── Content: List  or  Map
│   └── Selection card (overlay at the bottom, shared by List and Map)
├── Saved (badge = count)         ← always reachable, ignores filters (V8)
│   ├── Saved list
│   └── Saved card (its own selection, L7)
└── Account (placeholder, D39)
    ├── Profile header: avatar, "Guest", "View profile"
    ├── Settings → Appearance: System | Light | Dark   (the only working page; saved between launches)
    ├── Change profile, Privacy, Info                  (pages of text)
    └── Log out                                        (a toast: there's no account)
```

Steps 6.3b–6.3e replace Explore with Roame's landing page, a full-screen search page and map-first results (PLAN Step 6.3); this section and §2 change with them.

## 2. Wireframes

### 2.1 Explore: List

```
┌────────────────────────────────────────┐
│ Explore                                │
│ ┌────────────────────────────────────┐ │
│ │ ✈︎ From   city or airport        ⓧ │ │
│ ├────────────────────────────────────┤ │
│ │ ⚑ To     city or airport        ⓧ │ │
│ └────────────────────────────────────┘ │
│ (Feb 1 – Apr 1)(Business ✓)(≤ 60k ✓)(Programs ▾)  Reset │
│ 238 flights · 2 filters                │
│ ┌──────── List ────────┬──── Map ────┐ │
│ ⚠ Couldn't refresh · results from 3:42 PM │
│ ┌────────────────────────────────────┐ │
│ │ BOS ───────✈─────── CDG  25,000 pts│ │
│ │ Boston → Paris          Prem. Econ.│ │
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

Each sheet shows a live **"Show N flights"** button. Choices apply as they're made, so N is the real count and the button just closes the sheet. Sheets open fully, so the button is always on screen.

## 3. Components

| Component | Purpose | States |
|---|---|---|
| `SearchField` | From / To text input | empty · typing · has text (ⓧ clears it) · fuzzy hint visible · airport picked |
| `SuggestionList` | Dropdown of matching airports under the focused field | hidden · up to 5 rows · row pressed |
| `FilterChip` | Opens a filter sheet | default · active (filled, accent, shows the value) · pressed |
| `ResultSummary` | "N flights · K filters" + Reset | Reset is hidden when nothing is active |
| `FlightRow` | One result | default · pressed · selected · saved (filled heart) |
| `RouteConnector` | `ORIGIN ●──✈──● DEST` graphic | Used in the row (compact) and in the card |
| `DestinationMarker` | Map pill showing `CODE count·minPts` | default · selected (accent, on top) · pressed |
| `FlightCard` | Selection details + Save / Remove | idle · pending · error message shown |
| `StatusBanner` | Stale or offline info, with no Retry (D29) | stale · refreshing (inline progress) |
| `EmptyState` / `ErrorState` | Full-area explanations | See §4 |
| `AppToast` | One-shot `UserMessage`, at the top of the screen (§6) | "Couldn't save flight" + Retry |

## 4. State catalogue

Use the same copy on both platforms.

| State | When (rule) | What the user sees |
|---|---|---|
| First load | `DataStatus.Loading` | 6–8 skeleton rows (List) or a centred spinner (Map), with the label "Finding flights…" |
| Ready | `Ready`, has results | Results. No banner when the last refresh succeeded. |
| Refreshing | `Ready.isRefreshing` | A thin progress bar. Content stays interactive. |
| Stale / offline | `Ready.refreshError != null` (R6) | Banner: "Couldn't refresh · showing results from 3:42 PM". Results stay usable. No Retry (D29): the next launch refreshes. |
| Unavailable: offline | `Unavailable(Offline)` (R7) | Icon + "Can't reach Flight Explorer" + "Make sure the local API is running and you're connected, then try again." + **Try again** |
| Unavailable: timeout | `Unavailable(Timeout)` | "The server took too long to respond." + **Try again** |
| Unavailable: server | `Unavailable(Server(503))` | "The server had a problem (503)." + **Try again** |
| Unavailable: bad data | `Unavailable(InvalidData)` | "We received flight data we couldn't read." + **Try again** |
| Unavailable: other | `Unavailable(Unknown)` | "Something went wrong while loading flights." + **Try again** |
| Synced empty | `Ready` and the cache is empty (R4) | "No flights available right now" + "The server returned no flights." No error styling. |
| Filtered empty | `Ready`, cache not empty, `results` empty | "No flights match your search" + the list of active filters + **Reset filters** |
| Location not shared | From hasn't had focus yet, or the permission was denied (D16, D30) | Nothing extra is shown and there is no error. Ranking uses airport volume only. A denial isn't asked again by the app. |
| Location shared | Permission granted and a location received | Nothing extra is shown. In the From field, matched airports near the user rank higher in the hint and suggestions. The To field doesn't use location. |
| Fuzzy match hint | A field's search tier is `Fuzzy` (S13) | A secondary caption under that field: "Showing Sydney (SYD)". Two airports: "Showing Tokyo (HND), Tokyo (NRT)". More than two: "Showing Sydney (SYD), Seoul (ICN) +2 more". Not shown for prefix matches. |
| Search suggestions | A field has focus and its query has matches (S14) | A dropdown under the field with up to 5 rows, such as "Tokyo (all airports)" and "Tokyo (HND)". Hidden for a blank query or no match. |
| Map empty | Same as filtered empty, in Map mode | The map stays visible with a floating card: "No flights match · Reset filters" |
| Map can't load (Android only, D24) | The map's style hasn't loaded after 10 seconds (A15) | The map area shows "Can't load the map" + "It needs internet the first time. Your flights are still in the list." + **Retry**. It disappears as soon as the map loads. |
| Saved empty | `saved` is empty | "No saved flights yet" + "Tap ♡ on any flight to keep it here — even offline." |
| Save pending | id in `pendingWriteIds` | The button shows a spinner and is disabled |
| Save or remove failed | A `UserMessage` arrives (V7) | Toast: "Couldn't save flight." / "Couldn't remove flight." + **Retry**. The heart stays in its previous state. |
| Not listed | A saved flight missing from the cache (V4) | A grey "No longer listed" tag in the Saved row and card |

## 5. Interaction details

- **Typing** updates results live, without a submit button. The keyboard's Search or Done key dismisses the keyboard. Scrolling the list also dismisses the keyboard.
- **A new search or filter** scrolls the list back to the top, so the best results are in view. A save or a background refresh keeps the scroll position. (Found in Step 4.4: Compose keeps the old top flight in view, so after Reset the first rows were hidden above it.)
- **Tapping a suggestion** picks what it names, closes the dropdown and shows "Tokyo (HND)" or "Tokyo (all airports)" in the field. Editing the text clears the pick.
- **Tapping a row** selects it, highlights it and shows the card. Tapping the selected row again, or ✕, deselects it.
- **Save / Remove** gives haptic feedback on success: `.sensoryFeedback(.success, …)` on iOS and `HapticFeedbackType.LongPress` on Android. On failure, show the toast with no haptic. A write succeeded when its id leaves `pendingWriteIds` with no `UserMessage` for it, because the store has no success event.
- **Hearts:** each row has a ♡ that saves or removes the flight, the same as the card's button. While its write runs, it shows a spinner and ignores taps (V7).
- **No manual refresh** (D29, owner): no pull to refresh and no ⟳ button. The app refreshes when it opens, showing a thin progress bar over the data, and the first-load error has Try again (R7). The stale banner sits under the List | Map switch; on the "No flights available" screen it sits above the message.
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

**Typography (D38, D40).** Inter on both platforms, bundled, at the same sizes: points on iOS, sp on Android. Both scale with the text-size setting (iOS through `Font.custom(_:size:relativeTo:)`, Android through sp). Points use tabular digits.

| Style | Inter | iOS (`Theme.swift`) | Android (`Theme.kt`) | Used for |
|---|---|---|---|---|
| Display | 28 Bold | `.appDisplay` | `headlineLarge` | Tab titles (Account), the avatar's initial |
| Points | 24 Bold | `.appPoints` | `headlineSmall` | The selection card's points |
| Title large | 22 SemiBold | `.appTitleLarge` | `titleLarge` | The profile name |
| Title | 16 SemiBold | `.appTitle` | `titleMedium` | Airport codes, row points, sheet and page titles, empty-state titles |
| Title small | 14 SemiBold | `.appTitleSmall` | `titleSmall` | Section labels |
| Body large | 16 Regular | `.appBodyLarge` | `bodyLarge` | List rows, option rows, field text |
| Body | 14 Regular | `.appBody` | `bodyMedium` | City, date, explanations (in secondary text) |
| Caption | 12 Regular | `.appCaption` | `bodySmall` | Hints under fields |
| Label | 14 Medium | `.appLabel` | `labelLarge` | Chips, field labels, the segmented control |
| Button | 14 SemiBold | `.appButton` | `labelLarge` at SemiBold | Buttons, text links, the chosen segment |
| Label small | 12 Medium | `.appLabelSmall` | `labelMedium` | Tab labels, the stale banner |
| Label tiny | 11 Bold | `.appLabelTiny` | `labelSmall` at Bold | The tab badge's count |
| Badge | 12 Bold | `.appBadge` | `labelMedium` at Bold | Program badges; the map's pills on iOS (MapLibre draws Android's with the map style's own font) |

**Colour roles (Step 6.3a, Roame's look).** Sampled from Roame's app, the same values on both platforms: iOS `Theme.swift`, Android `Theme.kt`. Light / dark:

| Role | Value | iOS | Android |
|---|---|---|---|
| Brand pink (route, plane, hearts, logos, gradients) | `#EA4167` / `#FF7A98` | `Color.brand` | `LocalExtraColors.brand` |
| Accent for controls and pink text (4.5:1 on white) | `#D63A60` / `#FF7A98` | `Color.accent` | `primary` |
| Text on the accent | white / `#4A0016` | `Color.onAccent` | `onPrimary` |
| Background (warm white) | `#FEFCF6` / `#151312` | `Color.background` | `background`, `surface` |
| Cards | white / `#211E1C`, with a `#E8E5E0` / `#3A3532` border | `Color.card`, `Color.cardBorder` | `surfaceContainer`, `outlineVariant` |
| Selected card | `#FDE3E9` / `#5C1A2C` with a 2 pt accent border | `Color.selectedCard` | `primaryContainer` |
| Text / secondary text | `#302D2A` / `#F2EEE9`, and `#6B6661` / `#BDB6AF` | `Color.text` (set at the root and on each sheet), `Color.secondaryText` | `onBackground` and `onSurface` (the default content colour, set at the theme's root), `onSurfaceVariant` |
| Segmented control's track | `#F2F0EA` / `#34302E` | `Color.track` | `surfaceContainerHighest` |
| Chip border | `#D6D3CE` / `#5A5450` | `Color.outline` | `outline` |
| Toast, and its action | `#2B2B2B` with `#FFB3C4`, in both modes | `Color.toast`, `Color.toastAction` | the same values in `DesignSystem.kt` |
| Avatar gradient | `#F2BD93` → `#DA7083` / `#C98E68` → `#B4506A` | `Color.gradientStart`, `.gradientEnd` | `LocalExtraColors.gradientStart`, `.gradientEnd` |
| Warning (stale banner) | soft peach | `.orange` at 15% | `tertiaryContainer` |

**Seat classes**, as Roame shows them: an icon and the name in the class's colour, with no capsule. The icon uses Roame's colour; the text uses a deeper shade where Roame's is too light to read at 4.5:1.

| Cabin | Icon (both platforms) | Icon colour | Text colour |
|---|---|---|---|
| Economy, and any cabin the app doesn't know | `ms_seat` (`airline_seat_recline_normal`) | `#312D2A` | the same |
| Premium Economy | `ms_seat_premium` (`airline_seat_recline_extra`) | `#007986` | the same |
| Business | `ms_crown` (`crown`) | `#EC9D26` | `#A65F00` |
| First (not in the data yet) | `ms_sparkles` (`auto_awesome`) | `#FF2765` | `#D81B50` |

**Icons (D37).** The same 22 Material Symbols (Rounded, 24) on both: vector drawables on Android, template SVGs in an asset catalog on iOS, each tinted by its context. They're decorative: the control around them carries the spoken label (THIRD_PARTY_NOTICES).

**Program logos (D34):** each program's logo on a white pill with a thin border, white in dark mode too. Aeroplan, MileagePlus and Mileage Plan use their public-domain logos. Avios and Flying Blue show their name, because no freely licensed logo exists (THIRD_PARTY_NOTICES).

**Components (D38).** Each is written once per platform, under the same name, in `DesignSystem.kt` and `DesignSystem.swift`, at the same sizes (pt = dp):

| Component | Looks like | Used for |
|---|---|---|
| `AppTabBar` | Card colour, a 1 pt top border; each tab a 24 pt icon over a Label small; the chosen tab pink, with a 2 pt pink line along its top. The Saved tab's icon fills when chosen, and carries a pink count badge. Spoken as "Saved, 3 saved flights" | The three tabs |
| `SegmentedControl` | A 40 pt track with a pink pill (radius 17) for the chosen option | List \| Map; Appearance |
| `AppChip` | A 36 pt pill: card colour with an outline border, or pink when active, with its own ✕ | The filter bar |
| `PrimaryButton`, `TintedButton` | 44 pt pills: pink with white text, or pink text on light pink, with an optional leading icon | Save / Remove, Retry, Show results |
| `TextLink` | Pink Button text with a 44 pt target | Reset, Clear |
| `AppToast` | A dark rounded card at the top of the screen, white text and a pink action; failures for 6 s, notes for 3 s | Failed writes with Retry; the app's own notes |
| `EmptyState` | A 32 pt icon in a 72 pt light-pink circle, a Title, a line of Body, an optional button | No results, no saved flights, unavailable, no flights |
| `PageTitle`, `SubpageTopBar` | A Display title; or a 56 pt bar with Back and a centred Title over a hairline | Account and its pages |
| `ListRow`, `OptionRow` | 56 pt rows: an icon, Body large text, a chevron; or 52 pt rows with a pink check when chosen | Account's menu; sheet options |

Search fields are pills in the card colour with a magnifying glass. Sheets, the date range picker and the points slider stay native until Step 6.3c and 6.3d redraw them.

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
| Shell | A `VStack` of the content and `AppTabBar` (§6). Explore stays alive, hidden, under the other tabs. Portrait only (D28) | A `Column` of the content and `AppTabBar` (§6). Explore stays composed but unplaced under the other tabs, so the map is never detached (D31). Portrait only (D28) |
| Sheets | `.sheet` + `.presentationDetents([.medium, .large])` | `ModalBottomSheet` |
| List | `List` or `LazyVStack` with `id: \.id` | `LazyColumn` with `key = { it.id }` |
| Map | `Map(position:)` + `Annotation` + `MapPolyline(coordinates:contourStyle: .geodesic)`, muted and flat standard style (Step 5.5) | MapLibre: symbol layer for markers, line layer with `GreatCircle` points for the route. OpenFreeMap's grey "Positron" style, no MapLibre logo, and the ⓘ credit kept above the selection card (D27) |
| Refresh | None (D29): on launch, with a thin progress bar | None (D29): on launch, with a thin progress bar |
| Messages | `AppToast` at the top, with Retry (§6) | `AppToast` at the top, with Retry (§6) |
| Date picker | `DatePicker(…, displayedComponents: .date)`. Convert with `Calendar.current.dateComponents([.year, .month, .day], from:)`. | `DateRangePicker`. The millis are **UTC** midnight, so convert with UTC, never the local zone. |
| State | `@Observable ExplorerModel` via `.environment` | `collectAsStateWithLifecycle()` |
| Location for ranking (D16, D30) | Core Location, "when in use", approximate accuracy (`NSLocationDefaultAccuracyReduced`), asked on the first focus of From. The dialog's reason (`NSLocationWhenInUseUsageDescription`): "Your approximate location puts nearby airports first when you choose where you're flying from." | Fused Location Provider (`play-services-location`), `ACCESS_COARSE_LOCATION`, asked on the first focus of From |

## 10. Scalability

The fixture fits on screen comfortably. These rules keep the UI usable when the data grows (REQUIREMENTS §11):

| Element | Today | When the data grows |
|---|---|---|
| Result list | Virtualised (`LazyColumn` / `List`) with stable ids | Unchanged. If the API paginates, add a "Loading more…" footer, and the summary reads "1,000+ flights". |
| Map markers | 24 individual pills | Beyond ~200 markers, cluster by zoom. A cluster bubble shows the total count and the lowest points; tap it to zoom in. |
| Marker sheet | Lists every flight for the destination | Virtualised, with the count in the header |
| Programs filter | Checklist of 5 | Beyond ~15 options, a searchable list with the selected items pinned on top |
| Active filter chips | Fit on one row | Scroll horizontally, with a "+N" overflow chip that opens the full list |
| Fuzzy hint | One or two airports | Never more than one line: the first two in ranked order, then "+N more" |
| Search suggestions | Up to 5 ranked rows (S14) | Unchanged: the cap keeps the dropdown short, and ranking decides which airports appear |
| Saved tab | Small list | Virtualised; grouped by month when long |
