# Submission notes

> **Working draft.** Assumptions and decisions are pre-filled from planning. Keep this file current while building, and finalise it in M7. Delete this note before submitting.

## 1. Approach

Flight Explorer is two native apps (SwiftUI and Jetpack Compose) on a single Kotlin Multiplatform core. The core owns every rule: fetching, caching, search, filtering, ordering, map grouping, selection, saved flights and offline state. It exposes them as one immutable `ExplorerState`, so both UIs only render state and send intents. The behaviour is specified rule by rule in [DOMAIN_RULES.md](DOMAIN_RULES.md) and tested once in `shared/src/commonTest`.

A small Ktor server (`./gradlew :server:run`) serves the fixture verbatim at `GET /flights`.

## 2. Key architectural decisions

Full rationale is in [ARCHITECTURE.md §11](ARCHITECTURE.md#11-decision-log).

- **Shared presentation state, not just shared repositories.** List and map can't disagree, and selection and offline rules are tested once. *(D2)*
- **SQLite via SQLDelight.** Atomic cache replacement and upserts for saved flights, with fast JVM tests on an in-memory DB. *(D3)*
- **Saved flights are snapshots in their own table.** They survive being missing from the API response. *(D4)*
- **In-memory filtering with `mapLatest`.** 1,000 rows filter in about a millisecond, the functions are pure and easy to test, and the latest input always wins. *(D6)*
- **A hand-written Kotlin→Swift bridge.** It avoids plugins tied to specific Kotlin versions. *(D7)*
- **MapLibre on Android and MapKit on iOS.** Neither needs an API key, so reviewers can run both apps as-is. *(D8)*

## 3. Assumptions

Each one is mirrored as an *(assumption)* rule in DOMAIN_RULES.

| # | Ambiguity in the brief | Assumption | Rule |
|---|---|---|---|
| A1 | How search "matches" codes and cities | Case- and whitespace-insensitive **prefix** match on the code, the city, or any word of the city (`york` → New York) | S4 |
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

## 4. Tradeoffs

- **Prefix search vs substring or fuzzy.** Prefix search is predictable and fast, but `"ondon"` finds nothing and accents aren't folded.
- **Full cache replacement vs diffing.** Full replacement is simple and correct for a full-dataset API, but wasteful for a large or paged API.
- **In-memory filtering vs SQL.** In-memory filtering is ideal at 1,000 rows. At around 100k rows it would move to indexed SQL queries or FTS.
- **Hand-written Swift bridge vs SKIE.** The bridge has no dependency risk, but it means more boilerplate per intent and sealed types that are clumsy to switch on in Swift.
- **MapLibre vs Google Maps on Android.** MapLibre needs no key, but custom markers take more work and the tile style differs from iOS.

## 5. Known limitations

*Fill in at the end. Starting list:*

- No accent folding or fuzzy search (S6).
- Map tiles aren't cached offline; markers still render (allowed by the brief).
- Number formatting is en-US only (`25,000 pts`).
- Local HTTP only: cleartext is allowed for `localhost` / `10.0.2.2`, and physical devices need extra config.
- No cross-device sync (out of scope per the brief).
- *…*

## 6. Checks performed

See [TEST_PLAN.md §5](TEST_PLAN.md#5-manual-qa-checklist) for the filled-in QA checklist for both platforms, and §4 for the performance and leak checks. Summarise here:

- Shared tests: *N tests, all passing* (`./gradlew :shared:jvmTest`, and `iosSimulatorArm64Test` on a Mac).
- Android: *device or emulator, API level, date*.
- iOS: *simulator model, iOS version, date*.

## 7. How AI was used

*Describe honestly. Suggested structure:*

- **Planning:** Claude Code read the brief and profiled the fixture (counts, edge cases such as non-pattern IDs, the two Tokyo airports, "Mileage Plan" vs "MileagePlus", and antimeridian routes). It drafted these planning docs, which I reviewed and edited.
- **Implementation:** *where AI generated code: e.g. boilerplate, SQL, Compose/SwiftUI scaffolding.*
- **Verification:** *how output was checked: every rule has a test written or reviewed by me; diffs reviewed before commit; behaviour verified on both platforms via the QA checklist; anything I couldn't explain was rewritten.*

### AI usage log

| Date | Area | What AI did | How I verified |
|---|---|---|---|
| 2026‑10‑29 | Planning | Profiled the fixture and drafted the docs in `docs/` and `CLAUDE.md` | Re-read the brief line by line against REQUIREMENTS.md |
| | | | |
