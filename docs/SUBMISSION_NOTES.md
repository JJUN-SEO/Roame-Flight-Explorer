# Submission notes

> **Working draft.** Keep this file current while building, and finalise it in M7. Delete this note before submitting.

## 1. Approach

Flight Explorer is two native apps (SwiftUI and Jetpack Compose) on a single Kotlin Multiplatform core. The core owns every rule: fetching, caching, search, filtering, ordering, map grouping, selection, saved flights and offline state. It exposes them as one immutable `ExplorerState`, so both UIs only render state and send intents. The behaviour is specified rule by rule in [DOMAIN_RULES.md](DOMAIN_RULES.md) and tested once in `shared/src/commonTest`.

A small Ktor server (`./gradlew :server:run`) serves the fixture verbatim at `GET /flights`.

## 2. Decision record

Every notable architectural decision, who made it, and when. The full rationale and the rejected alternatives are in [ARCHITECTURE.md §11](ARCHITECTURE.md#11-decision-log).

**Decided by** means:
- **Owner:** the project owner chose it.
- **Owner (approved):** Claude proposed it and the owner explicitly approved it.
- **Proposed:** Claude proposed it in the planning docs, and the owner hasn't individually reviewed it yet.

| # | Decision | Why | Decided by | Date |
|---|---|---|---|---|
| D1 | The local API is a Ktor JVM module in the same Gradle build | One language and one command (`./gradlew :server:run`) | Proposed | 2026‑10‑29 |
| D2 | Share presentation state (`ExplorerStore`), not just repositories | List and map can't disagree; selection and offline rules are tested once | Proposed | 2026‑10‑29 |
| D3 | SQLite via SQLDelight | Atomic cache replacement, upserts, reactive queries, fast JVM tests | Proposed | 2026‑10‑29 |
| D4 | Saved flights are snapshots in their own table | They survive being missing from the API response | Proposed | 2026‑10‑29 |
| D5 | Replace the whole cache on each successful refresh | The API returns the full dataset, so a full replace is simplest and correct | Proposed | 2026‑10‑29 |
| D6 | Filter in memory with `mapLatest` | 1,000 rows; pure, testable functions; latest input wins | Proposed | 2026‑10‑29 |
| D7 | A hand-written Kotlin→Swift bridge | Avoids plugins tied to specific Kotlin versions | Proposed | 2026‑10‑29 |
| D8 | MapLibre on Android, MapKit on iOS | No API keys, so reviewers can run both apps | Proposed | 2026‑10‑29 |
| D9 | A test-only `jvm()` target in `:shared` | Fast shared tests on any OS | Proposed | 2026‑10‑29 |
| D10 | One app-scoped store per process | Survives rotation, avoids leaks, one source of truth | Proposed | 2026‑10‑29 |
| P1 | Keep the agent guide as `CLAUDE.md` (no `AGENTS.md`) | Owner's preference | Owner | 2026‑10‑30 |
| P2 | Work one functionality at a time. The owner reviews each one, and it is pushed only after acceptance. | Keeps every change reviewable and owned | Owner | 2026‑10‑30 |
| P3 | Ask the owner before significant architectural decisions, and record them here | The owner must be able to explain and defend every design | Owner | 2026‑10‑30 |

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

- **Full cache replacement vs diffing.** Full replacement is simple and correct for a full-dataset API, but wasteful for a large or paged API.
- **In-memory filtering vs SQL.** In-memory filtering is ideal at 1,000 rows. At around 100k rows it would move to indexed SQL queries (ARCHITECTURE §12).
- **Hand-written Swift bridge vs SKIE.** The bridge has no dependency risk, but it means more boilerplate per intent and sealed types that are clumsy to switch on in Swift.
- **MapLibre vs Google Maps on Android.** MapLibre needs no key, but custom markers take more work and the tile style differs from iOS.

## 5. Known limitations

*Fill in at the end. Starting list:*

- Map tiles aren't cached offline; markers still render (allowed by the brief).
- Number formatting is en-US only (`25,000 pts`).
- Local HTTP only: cleartext is allowed for `localhost` / `10.0.2.2`, and physical devices need extra config.
- No cross-device sync (out of scope per the brief).
- *…*

## 6. Scalability

How the design behaves as the data grows. The per-layer table, with each layer's next step, is in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability), and the targets are in [REQUIREMENTS.md §11](REQUIREMENTS.md#11-scalability-non-functional-self-imposed).

- **Built for now:** in-memory filtering, a full cache replace, and individual map markers. All are well within budget at 1,000 flights.
- **Scaling paths documented, not built:** SQL-side filtering with indexes, incremental sync (ETag and deltas), marker clustering, and paged lists.
- **Measured numbers:** *fill in from the M6 benchmarks (TEST_PLAN §4.1).*

## 7. Checks performed

See [TEST_PLAN.md §5](TEST_PLAN.md#5-manual-qa-checklist) for the filled-in QA checklist for both platforms, and §4 for the performance and leak checks. Summarise here:

- Shared tests: *N tests, all passing* (`./gradlew :shared:jvmTest`, and `iosSimulatorArm64Test` on a Mac).
- Android: *device or emulator, API level, date*.
- iOS: *simulator model, iOS version, date*.

## 8. How AI was used

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
