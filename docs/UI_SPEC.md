# UI spec

Roame is a *functional* reference, not a pixel spec. The goal is a calm, fast, information-dense award-search app:

- The points price is the hero number.
- The route is instantly scannable.
- Every state is explained.

Both platforms follow the same information architecture and copy. Since Step 6.3a they also look the same (D38): one font (Inter, D40), the same Material Symbols icons (D37), and the app's own tab bar, segmented control, chips, buttons, toasts and empty states, drawn the same way in SwiftUI and Compose (§6). Only what the platforms can't share stays native: the maps (MapKit and MapLibre), sheets, the points slider until Step 6.3d, haptics and permission dialogs.

## 1. Information architecture

```
Tab bar
├── Explore                       ← discovery
│   ├── Landing page (Step 6.3b, D36): where Explore opens; no flight list until a search
│   │     ├── "Flight Explorer" in pink, and the search bar: "Where to?" or the current route, with dates · cabin
│   │     ├── Status banner (stale / refreshing), when relevant
│   │     ├── Banner: a landscape, "See where your points can take you", [Find a flight] → the search, like the search bar
│   │     ├── Best deals by destination: a swipeable deck of photo cards (B8) → that city's flights
│   │     └── Best offers by program: a program's logo over its best flight (B7) → that route, the flight selected
│   │         With a location, every deal leaves the nearest airport (B5); the cards' routes show it, with no subtitle (owner's choice)
│   ├── Search page (Step 6.3c), from the search bar or the banner; ✕ returns to the landing page
│   │     ├── One card: From, To (with ⇅ on the line between them, S15), Dates, Cabin ▾
│   │     ├── From / To → a picker page: the field, focused, with suggestions (S14) and the hint (S13)
│   │     ├── Dates → the calendar (K1–K5), with Any dates and Done
│   │     ├── Cabin → the cabin sheet
│   │     └── [Search flights] → the results
│   └── Results (Step 6.3d, like Airbnb's; from the search page or a deal; ← and back return to the landing page)
│       ├── Header: ← · the search summary (tap → the search page) · Filters (badge = active count) → the Filters sheet
│       ├── Active filters as chips, each with ✕ (C3), then "Clear all" (clears filters, keeps the search)
│       ├── Status banner (stale / refreshing), when relevant
│       ├── The map, filling the rest: pills (M1–M6); a selected route as dots with a plane flying along them (M7, M9)
│       │     └── Tap a pill → the destination's flights (M5) → tap one → selected
│       ├── The list, as a sheet over the map: peek (handle + "238 flights"), half (the start), or full
│       │     ├── Drag it anywhere while it isn't full; when full, the list scrolls and only its header drags
│       │     └── When full, a "Map" button drops it to the peek
│       └── Selecting a flight drops the sheet to the peek and shows the selection card above it; the map frames
│           the route above the card (M8). Moving the sheet never changes the selection (L4)
├── Saved (badge = count)         ← always reachable, ignores filters (V8)
│   ├── Saved list
│   └── Saved card (its own selection, L7)
└── Account (placeholder, D39)
    ├── Profile header: avatar, "Guest", "View profile"
    ├── Settings → Appearance: System | Light | Dark   (the only working page; saved between launches)
    ├── Change profile, Privacy, Info                  (pages of text)
    └── Log out                                        (a toast: there's no account)
```

Location is asked for when the app first opens (D32).

## 2. Wireframes

### 2.0 Explore: landing page (Step 6.3b)

```
┌────────────────────────────────────────┐
│ Flight Explorer                        │  pink, Display-weight 24
│ ╭────────────────────────────────────╮ │
│ │ 🔍  Where to?                      │ │  card-colour pill with a soft shadow
│ │     Feb 1 – Apr 1 · Any cabin      │ │
│ ╰────────────────────────────────────╯ │
│ ┌────────────────────────────────────┐ │
│ │      (landscape photo, 4:3)        │ │
│ │ See where your points can take you │ │  white, Display 28, centred
│ │         ( Find a flight )          │ │  gradient pill, 48 tall
│ └────────────────────────────────────┘ │
│ Best deals by destination              │  Title large
│ ┌──────────────┐ ┌──────────          │  260 × 173 photo cards, swipe sideways
│ │[Up to 35% below typical]│           │  pink pill
│ │ Johannesburg │ │ Singapore          │  white Title large over a dark fade
│ │ from 39,000 pts                     │
│ └──────────────┘ └──────────          │
│ Best offers by program                 │
│ (MileagePlus logo)                     │
│ ┌────────────────────────────┐ ┌───   │  300-wide cards, swipe sideways
│ │ Mon, Mar 22   ⌂ Premium Economy     │
│ │ YYZ ───────✈─────── JNB    │        │
│ │ Toronto → Johannesburg     │        │
│ │ ──────────────────────────          │
│ │ (35% below typical)  39,000 pts     │
│ │                     ≈ $702 value    │
│ └────────────────────────────┘        │
├────────────────────────────────────────┤
│   Explore        ♡ Saved (1)   Account │
└────────────────────────────────────────┘
```

A city with no bundled photo gets a card in the avatar's gradient with a plane (D33). The data has no airlines, times or durations, so the offer cards show what it has.

### 2.0b Explore: search page (Step 6.3c)

```
┌────────────────────────────────────────┐
│ ✕          Search flights              │
├────────────────────────────────────────┤
│ ┌────────────────────────────────────┐ │  one card, 16 radius, thin border
│ │ ✈↗  From                           │ │  label: Label small, grey
│ │     Toronto (YYZ)                  │ │  value: Body large, or "City or airport" in grey
│ ├──────────────────────────────(⇅)──┤ │  swap: a 40 pt grey circle on the line
│ │ ✈↘  To                             │ │
│ │     Tokyo (all airports)           │ │
│ ├────────────────────────────────────┤ │
│ │ 📅  Dates                          │ │
│ │     Feb 10 – Feb 20                │ │
│ ├────────────────────────────────────┤ │
│ │ 💺  Cabin                        ▾ │ │
│ │     Any cabin                      │ │
│ └────────────────────────────────────┘ │
│ (          Search flights            ) │  full-width primary button
└────────────────────────────────────────┘
```

The calendar page: a bar with Back and "Dates", the weekday letters S M T W T F S, then each month ("February 2027") as rows of 44 pt day cells. The range's ends are pink 40 pt circles with white numbers, the days between sit on a light pink band, and days outside the data are faint and can't be tapped. "Any dates" and "Done" sit at the bottom. The data has no travellers or trip type, so the page shows neither.

### 2.1 Explore: results, the list at half (Step 6.3d)

```
┌────────────────────────────────────────┐
│ ←  ╭ 🔍 Toronto → Tokyo             ╮ (⚙)²│  the search summary; Filters with its badge
│    ╰    Feb 10 – Feb 20 · Business  ╯    │
│ (Feb 10 – Feb 20 ✕)(Business ✕)  Clear all │
│ ⚠ Couldn't refresh · results from 3:42 PM │  only when the last refresh failed
│      MAP  (CDG 12·25k)  (NRT 55·19k)   │
│ Maps · Legal                           │  Apple's (iOS) or the ⓘ credit (Android), above the sheet
│╭──────────────────────────────────────╮│
││                 ───                  ││  handle
││              238 flights             ││  peek height: 64
││ ┌──────────────────────────────────┐ ││
││ │ YYZ ──────✈────── NRT 86,000 pts │ ││  the same rows as before
││ └──────────────────────────────────┘ ││
├────────────────────────────────────────┤
│   Explore        ♡ Saved (3)   Account │
└────────────────────────────────────────┘
```

Fully open, the sheet covers the map up to the header, the list scrolls, and a dark "Map" pill floats at its bottom. At the peek only the handle and the count show.

### 2.2 Explore: results, a flight selected

```
│      •YYZ ·  ·  · ✈ ·  ·  ·  ·(NRT)    │  still dots over the other pills; the plane flies
│                                        │  along them and fades before the pink pill (M7, M9)
│ ┌────────── selection card ──────────┐ │  above the sheet, as before (§2.3)
│ └────────────────────────────────────┘ │
│╭──────────────── 238 flights ─────────╮│  the sheet, dropped to its peek
```

Tap a pill → a sheet with that destination's flights: "Paris · CDG — 12 flights from 25,000 pts", ordered by points (O2). Tapping a row selects it and closes that sheet; the results sheet drops and the camera frames the route above the card (M8).

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

### 2.4 The Filters sheet (Step 6.3d)

One sheet with every filter, opened by the Filters button or an active chip. Top to bottom:

| Section | Control | Notes |
|---|---|---|
| Dates | A row with the range and ›, which opens the calendar (K1–K5, §2.0b) inside the sheet, with Back, "Any dates" and Done | Shows "Feb 1 – Apr 1" by default (F2) |
| Cabin | Option rows: Any · Economy · Premium Economy · Business, a pink ✓ on the choice | Single choice (F5) |
| Points | Slider from min to max in steps of 500, with a live label "Up to 60,000 pts" or "Any" | The top end means no limit (F4) |
| Programs | Option rows for every program, ✓ on each chosen one | Multi-select. Empty means all (F6) |

At the bottom, "Clear all" (the filters only, keeping the search) and a live **"Show N flights"** button. Choices apply as they're made, so N is the real count and the button just closes the sheet. The sheet opens fully, so the button is always on screen. The search page's Cabin row still opens a cabin-only sheet.

## 3. Components

| Component | Purpose | States |
|---|---|---|
| `SearchField` | From / To text input | empty · typing · has text (ⓧ clears it) · fuzzy hint visible · airport picked |
| `SuggestionList` | Dropdown of matching airports under the focused field | hidden · up to 5 rows · row pressed |
| `FilterChip` | Opens a filter sheet | default · active (filled, accent, shows the value) · pressed |
| `ResultSummary` | "N flights · K filters" + Reset | Reset is hidden when nothing is active |
| `FlightRow` | One result | default · pressed · selected · saved (filled heart) |
| `RouteConnector` | `ORIGIN ●──✈──● DEST` graphic | Used in the row (compact) and in the card |
| `DestinationMarker` | Map pill (D44, the owner's decision): the code in bold, then the flight count on a light pink badge marked with a plane, e.g. `LIS ✈ 33`. The lowest points aren't drawn; they're in the pill's spoken label and the destination's sheet title. Centred on the airport, with a soft shadow | default (card colour) · selected (pink with a white badge, always on top, M4) · pressed |
| `FlightCard` | Selection details + Save / Remove | idle · pending · error message shown |
| `StatusBanner` | Stale or offline info, with no Retry (D29) | stale · refreshing (inline progress) |
| `EmptyState` / `ErrorState` | Full-area explanations | See §4 |
| `AppToast` | One-shot `UserMessage`, at the top of the screen (§6) | "Couldn't save flight" + Retry |

## 4. State catalogue

Use the same copy on both platforms.

| State | When (rule) | What the user sees |
|---|---|---|
| First load | `DataStatus.Loading` | Under the results' header, 6–8 skeleton rows with the label "Finding flights…". The landing page shows its header and search bar, then "Finding flights…" over grey shapes for the banner and a deck card |
| Ready | `Ready`, has results | Results. No banner when the last refresh succeeded. |
| Refreshing | `Ready.isRefreshing` | A thin progress bar. Content stays interactive. |
| Stale / offline | `Ready.refreshError != null` (R6) | Banner: "Couldn't refresh · showing results from 3:42 PM". Results stay usable. No Retry (D29): the next launch refreshes. The landing page shows the same banner under its search bar, and its deals come from the cached data |
| Landing page, unavailable or empty | `Unavailable` (R7), or `Ready` with an empty cache (R4) | Under the header and search bar, the same message as the results would show, with **Try again** where they have it |
| Unavailable: offline | `Unavailable(Offline)` (R7) | Icon + "Can't reach Flight Explorer" + "Make sure the local API is running and you're connected, then try again." + **Try again** |
| Unavailable: timeout | `Unavailable(Timeout)` | "The server took too long to respond." + **Try again** |
| Unavailable: server | `Unavailable(Server(503))` | "The server had a problem (503)." + **Try again** |
| Unavailable: bad data | `Unavailable(InvalidData)` | "We received flight data we couldn't read." + **Try again** |
| Unavailable: other | `Unavailable(Unknown)` | "Something went wrong while loading flights." + **Try again** |
| Synced empty | `Ready` and the cache is empty (R4) | "No flights available right now" + "The server returned no flights." No error styling. |
| Filtered empty | `Ready`, cache not empty, `results` empty | The sheet's header reads "No flights match your search", and the sheet shows the active filters + **Reset filters** (C4). The map shows no pills (M6) |
| Location not shared | The permission wasn't asked yet, or was denied (D16, D32) | Nothing extra is shown and there is no error. Ranking uses airport volume only. A denial isn't asked again by the app. |
| Location shared | Permission granted and a location received | Nothing extra is shown. In the From field, matched airports near the user rank higher in the hint and suggestions. The To field doesn't use location. |
| Fuzzy match hint | A field's search tier is `Fuzzy` (S13) | A secondary caption under that field: "Showing Sydney (SYD)". Two airports: "Showing Tokyo (HND), Tokyo (NRT)". More than two: "Showing Sydney (SYD), Seoul (ICN) +2 more". Not shown for prefix matches. |
| Search suggestions | A field has focus and its query has matches (S14) | A dropdown under the field with up to 5 rows, such as "Tokyo (all airports)" and "Tokyo (HND)". Hidden for a blank query or no match. |
| Map can't load (Android only, D24) | The map's style hasn't loaded after 10 seconds (A15) | The map area, behind the sheet, shows "Can't load the map" + "It needs internet the first time. Your flights are still in the list." + **Retry**. It disappears as soon as the map loads. |
| Saved empty | `saved` is empty | "No saved flights yet" + "Tap ♡ on any flight to keep it here — even offline." |
| Save pending | id in `pendingWriteIds` | The button shows a spinner and is disabled |
| Save or remove failed | A `UserMessage` arrives (V7) | Toast: "Couldn't save flight." / "Couldn't remove flight." + **Retry**. The heart stays in its previous state. |
| Not listed | A saved flight missing from the cache (V4) | A grey "No longer listed" tag in the Saved row and card |

## 5. Interaction details

- **Typing** updates results live, without a submit button. The keyboard's Search or Done key dismisses the keyboard. Scrolling the list also dismisses the keyboard.
- **A new search or filter** scrolls the list back to the top, so the best results are in view. A save or a background refresh keeps the scroll position. (Found in Step 4.4: Compose keeps the old top flight in view, so after Reset the first rows were hidden above it.)
- **Tapping a suggestion** picks what it names, closes the dropdown and shows "Tokyo (HND)" or "Tokyo (all airports)" in the field. Editing the text clears the pick.
- **Tapping a row** selects it and highlights it; the sheet drops to its peek and the card shows above it, over the map, which frames the route. Tapping the selected row again, or ✕, deselects it.
- **Save / Remove** gives haptic feedback on success: `.sensoryFeedback(.success, …)` on iOS and `HapticFeedbackType.LongPress` on Android. On failure, show the toast with no haptic. A write succeeded when its id leaves `pendingWriteIds` with no `UserMessage` for it, because the store has no success event.
- **Hearts:** each row has a ♡ that saves or removes the flight, the same as the card's button. While its write runs, it shows a spinner and ignores taps (V7).
- **No manual refresh** (D29, owner): no pull to refresh and no ⟳ button. The app refreshes when it opens, showing a thin progress bar over the data, and the first-load error has Try again (R7). The stale banner sits under the results' header and the landing page's search bar; on the "No flights available" screen it sits above the message.
- **Reset** gives a light animation as the chips clear. Selection behaviour follows C4 and L3.
- **The search page** applies each choice as it's made (like the filter sheets), so ✕ keeps them and "Search flights" only opens the results. Picking a suggestion, or the keyboard's Search key, returns from a picker to the form. Back leaves a picker or the calendar first, then the page.
- **The calendar** (K3): the first tap marks a start and waits for the end; a day on or after it ends the range; an earlier day starts again. The results follow each tap.
- **⇅** swaps From and To (S15).
- **Map:**
  - Marker tap → the destination's sheet. Row tap there → select.
  - Tapping empty map does nothing; it does *not* deselect, to avoid surprise.
  - The camera only moves on the first display and on explicit selection (M8). It doesn't rotate or tilt, on either app, so the plane's heading stays true (M9).
- **The results sheet** (the same rules on both apps): it rests at the peek (64 pt: the handle and the count), half the map (where it starts), or full. While it isn't full, a drag anywhere on it moves it and the list doesn't scroll; when full, the list scrolls and only its header drags it. A release goes to the nearest rest, or one rest on in the direction of a flick. When full, a dark "Map" pill drops it to the peek. Moving it never changes the selection or the filters (L4), and the list keeps its scroll position.
- **"Clear all"** clears every active filter (C3 on each) and keeps the search; the filtered-empty state's **Reset filters** resets the search too (C4).
- **Back / swipe-to-dismiss** closes sheets. Android's back gesture closes a sheet first, then deselects, then leaves the results for the landing page.

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
| Badge | 12 Bold | `.appBadge` | `labelMedium` at Bold | Program badges; the map pills' codes (Android draws its pills as images, in Inter at the same sizes) |

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

**The map in dark mode (D45):** the map, its pills, the route and the plane stay light on both apps, with the light theme's colours.

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
| `DateRangeCalendar` | Weekday letters, then months of 44 pt day cells; pink 40 pt circles on the ends, a light pink band between | The search page's dates; the Dates sheet |
| `SubpageTopBar(closes:)` | As above, with ✕ instead of Back | The search page |
| Results sheet | The background colour, with 20 pt top corners and a shadow; a 36 × 4 handle and the count | The results (§2.1) |
| `FiltersButton` | A 48 pt outlined circle with ⚙ (tune) and a count badge | The results' header |
| Map button | A dark (#2B2B2B) 44 pt pill with a map icon and "Map" in white | Over the full results sheet |
| Plane | Roame's pink plane on a white 32 pt disc with a faint ring, turned to the route's heading (M9) | The selected route |

Search fields are pills in the card colour with a magnifying glass, and a pink border while focused. Their suggestions are a card with a soft shadow. Sheets stay native containers; the points slider stays native until Step 6.3d.

## 7. Formatting

Follow DOMAIN_RULES §10. Points come from the shared formatter: `25,000 pts` in full and `25k` in compact form. Dates are formatted from components (`Mon, Feb 1`). Counts are pluralised by the platform: "1 flight" vs "238 flights".

## 8. Accessibility

- Support Dynamic Type and font scaling up to the largest accessibility size. Rows reflow to a vertical layout instead of truncating the points.
- Touch targets are at least 44 pt on iOS and 48 dp on Android, including the heart icon and chips.
- **VoiceOver / TalkBack labels:**
  - Row: "Boston to Paris, Monday February 1, Premium Economy, Avios, 25,000 points, saved."
  - Marker: "Paris, 12 flights, from 25,000 points."
  - Chips announce "Cabin: Business, active".
  - Landing search bar: "Search flights. Where to?, Feb 1 – Apr 1 · Any cabin" (or the current route).
  - Destination card: "Johannesburg, from 39,000 points, up to 35% below typical".
  - Offer card: "MileagePlus best offer: Toronto to Johannesburg, Mon, Mar 22, Premium Economy, 39,000 points, 35% below typical, worth about $702".
  - Section titles are headings, and each card is one button.
  - Search page rows: "From, Toronto (YYZ)", "Dates, Feb 10 – Feb 20"; the swap button: "Swap From and To".
  - Calendar days: "Wed, Feb 10, 2027, start date" (or "end date", "selected", "in range"); month names are headings; the weekday letters are hidden.
- Colour is never the only signal. Selected rows also get a border and a checkmark or "Selected" accessibility trait.
- Respect Reduce Motion: no camera fly-to animation, and the plane rests halfway along the route (M9).

## 9. Platform notes

| | iOS (SwiftUI, iOS 17) | Android (Compose, M3) |
|---|---|---|
| Shell | A `VStack` of the content and `AppTabBar` (§6). Explore stays alive, hidden, under the other tabs. Portrait only (D28) | A `Column` of the content and `AppTabBar` (§6). Explore stays composed but unplaced under the other tabs, so the map is never detached (D31). Portrait only (D28) |
| Sheets | `.sheet` + `.presentationDetents` for the Filters, cabin and marker sheets. The results sheet is the app's own (`ResultsScreen.swift`) | `ModalBottomSheet` for the same three. The results sheet is the app's own (`ResultsScreen.kt`) |
| List | `List` or `LazyVStack` with `id: \.id` | `LazyColumn` with `key = { it.id }` |
| Map | `Map(position:, interactionModes: [.pan, .zoom])` + `Annotation` + `MapPolyline(coordinates:contourStyle: .geodesic)`, muted and flat standard style (Step 5.5). MapKit draws lines under annotations, so the route (its dots, the origin's dot and the plane) is drawn over the map, placed with `MapReader`'s `convert` whenever the map moves and the plane every frame, with the selected pill cut out of it. The selected destination is the map's own selection, so MapKit draws it on top (M4). Its bottom safe area is what the sheet and card cover | MapLibre, bottom to top: a symbol layer for the other pills, a line layer with `GreatCircle` points for the route as a still dash pattern, the origin's dot, a symbol layer for the selected pill, and one for the plane, its source moved every frame. Rotation and tilt off. OpenFreeMap's grey "Positron" style, no MapLibre logo, and the ⓘ credit kept above the sheet and card (D27) |
| Refresh | None (D29): on launch, with a thin progress bar | None (D29): on launch, with a thin progress bar |
| Messages | `AppToast` at the top, with Retry (§6) | `AppToast` at the top, with Retry (§6) |
| Date picker | `DateRangeCalendar` (`Calendar.swift`), drawing the shared `DateCalendar` (D41). Dates stay ISO strings throughout | `DateRangeCalendar` (`Calendar.kt`), the same. Material's `DateRangePicker` and its UTC millis are no longer used |
| State | `@Observable ExplorerModel` via `.environment` | `collectAsStateWithLifecycle()` |
| Location for ranking and deals (D16, D32) | Core Location, "when in use", approximate accuracy (`NSLocationDefaultAccuracyReduced`), asked when the app first opens. The dialog's reason (`NSLocationWhenInUseUsageDescription`), short and conventional at the owner's request: "Your location is used to show flights from airports near you." | Fused Location Provider (`play-services-location`), `ACCESS_COARSE_LOCATION`, asked when the app first opens. Android's dialog is the system's own: apps can't add a reason or restyle it, and its colours follow the phone's theme |

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
| Destination deck | 23 photo cards | At most 30 cards (B8), best value first. Photos are bundled per city; a new city gets the drawn card |
| Best offers | One card per program (5) | One per program, so it grows only with the number of programs; past about 10, add "See all" as Roame does |
