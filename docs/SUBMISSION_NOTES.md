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
| D11 | **Tiered search.** Tier 1 is a prefix tree walked node by node in O(m). Tier 2 (fuzzy) runs only when Tier 1 finds nothing. | Predictable while typing; fuzzy never pollutes good prefix results | Owner | 2026‑10‑30 |
| D12 | **Mixed fuzzy matching**: typo distance + phonetic (Double Metaphone) + trigram n-grams, plus accent folding | Covers the four ways people mistype a place: typo, sound-alike, partly right, accents | Owner | 2026‑10‑30 |
| D13 | **Double Metaphone is copied from Apache Commons Codec and translated to Kotlin, with credit** (not written from scratch) | No multiplatform library exists, and the Java library can't run on iOS. See §8. | Owner | 2026‑10‑30 |
| D14 | **"Showing Sydney (SYD)" hint** under a field when fuzzy matching was used | Silent corrections would confuse people | Owner (approved) | 2026‑10‑30 |
| D15 | **Ranking by user location + annual passenger volume.** Volume lives in a new, separate `fixtures/airports.json`; `flights.json` is never edited. If the file is missing, volume falls back to flight counts from `flights.json`. | Nearby and busy airports first, without changing the provided data, and the app works without the extra file | Owner | 2026‑10‑30 |
| D16 | Search runs on **airports, not flights**, behind indexes | Search cost doesn't grow with the number of flights (SCALE-1) | Proposed | 2026‑10‑30 |
| D17 | Ranking weights: origin 0.7 proximity / 0.3 volume, destination 0.2 / 0.8 | You fly *from* near you; *where* you go is about popularity | Proposed | 2026‑10‑30 |
| — | *Pending:* the source of the user's location. Proposed: on-device location (Fused Location Provider / Core Location). Alternative: geo-IP via the local API. | — | **Awaiting owner** | — |
| — | *Pending:* whether ranking also reorders the flight list (P7) | — | **Awaiting owner** | — |

## 3. Assumptions

Each one is mirrored as an *(assumption)* rule in DOMAIN_RULES.

| # | Ambiguity in the brief | Assumption | Rule |
|---|---|---|---|
| A1 | How search "matches" codes and cities | Tier 1 is a case-, accent- and whitespace-insensitive **prefix** match on the code, the city, or any word of the city (`york` → New York). Fuzzy matching is the fallback (owner design D11–D13). | S4–S10 |
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
| A12 | What "fall back to the original data" means when `airports.json` is missing | Volume = each airport's number of flights in `flights.json` | P4 |
| A13 | How much location vs popularity should count | Origin 0.7 / 0.3, destination 0.2 / 0.8 (D17) | P5 |

## 4. Tradeoffs

- **Tiered vs blended search.** Tiered is predictable: fuzzy never adds noise when prefix already matched. The cost is that a weak prefix hit can hide a better fuzzy one. A prototype of both passed the same 114 cases.
- **Copied Double Metaphone vs our own phonetic key.** The port is a well-known, battle-tested algorithm with multilingual rules. The cost is ~900 lines (with comments) that we didn't design. That is mitigated by the oracle test (§8).
- **Fuzzy thresholds** (4-letter gate, edit budget, Dice ≥ 0.55, 0.10 margin) were tuned on the 32 fixture airports. They are explainable and tested, but should be re-validated on a larger dataset.
- **Full cache replacement vs diffing.** Full replacement is simple and correct for a full-dataset API, but wasteful for a large or paged API.
- **In-memory filtering vs SQL.** In-memory filtering is ideal at 1,000 rows. At around 100k rows it would move to indexed SQL queries (ARCHITECTURE §12).
- **Hand-written Swift bridge vs SKIE.** The bridge has no dependency risk, but it means more boilerplate per intent and sealed types that are clumsy to switch on in Swift.
- **MapLibre vs Google Maps on Android.** MapLibre needs no key, but custom markers take more work and the tile style differs from iOS.

## 5. Known limitations

*Fill in at the end. Starting list:*

- Search doesn't transliterate non-Latin scripts (`東京`) and knows no nicknames (`NYC`, `LA`).
- Sound-alike places outside the data can match: `newark` → New York (JFK). The hint line makes this visible (DOMAIN_RULES S12).
- The passenger figures in `fixtures/airports.json` are approximate 2024 totals, recalled by an AI assistant and **not verified** against primary sources. They are good enough to rank airports, not to quote.
- Map tiles aren't cached offline; markers still render (allowed by the brief).
- Number formatting is en-US only (`25,000 pts`).
- Local HTTP only: cleartext is allowed for `localhost` / `10.0.2.2`, and physical devices need extra config.
- No cross-device sync (out of scope per the brief).
- *…*

## 6. Scalability

How the design behaves as the data grows. The per-layer table, with each layer's next step, is in [ARCHITECTURE.md §12](ARCHITECTURE.md#12-scalability), and the targets are in [REQUIREMENTS.md §11](REQUIREMENTS.md#11-scalability-non-functional-self-imposed).

- **Built for now:** in-memory filtering, a full cache replace, and individual map markers. All are well within budget at 1,000 flights.
- **Search is built to scale:** it works on airports, not flights. Prefix lookup is O(m) in a trie. Fuzzy candidates come from inverted trigram and phonetic indexes rather than a full scan. Ranking priorities are precomputed (ARCHITECTURE §4.4, §12).
- **Scaling paths documented, not built:** SQL-side filtering with indexes, incremental sync (ETag and deltas), marker clustering, and paged lists.
- **Measured numbers:** *fill in from the M6 benchmarks (TEST_PLAN §4.1).*

## 7. Checks performed

See [TEST_PLAN.md §5](TEST_PLAN.md#5-manual-qa-checklist) for the filled-in QA checklist for both platforms, and §4 for the performance and leak checks. Summarise here:

- Shared tests: *N tests, all passing* (`./gradlew :shared:jvmTest`, and `iosSimulatorArm64Test` on a Mac).
- Android: *device or emulator, API level, date*.
- iOS: *simulator model, iOS version, date*.

## 8. Third-party code: Double Metaphone

**What.** `shared/src/commonMain/kotlin/travel/roame/takehome/vendor/commonscodec/DoubleMetaphone.kt` is a Java → Kotlin translation of `org.apache.commons.codec.language.DoubleMetaphone` from **Apache Commons Codec 1.23.0** (Apache License 2.0). The algorithm is Lawrence Philips' Double Metaphone. It is credited in the file header and in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

**This is not our own implementation.** We copied the library's source and translated it so it runs in shared Kotlin. We did not design its 100+ branching pronunciation rules (for example "CH" before "AE", or the Germanic "SCH"), and we don't claim to understand every one of them. What we own and understand is:
- how we *use* it,
- how we *verified* it,
- the rest of the search engine around it.

**Why a translation and not a dependency.** No Kotlin Multiplatform library provides Double Metaphone. Commons Codec is a Java library: it runs on Android and the JVM, but not on iOS, which uses Kotlin/Native and can't run Java bytecode. Translating the source into common Kotlin gives both apps the same algorithm (decision D13).

**What changed in the translation** (also listed in the file header, as Apache-2.0 §4(b) requires):
- Java syntax → Kotlin. The rule order and every condition are unchanged.
- `maxCodeLen` is an immutable constructor parameter, so instances are thread-safe.
- The `StringEncoder` / `encode(Object)` plumbing is removed.
- `toUpperCase(Locale.ENGLISH)` → the locale-invariant `uppercase()`.
- Visibility is `internal`.

**How we know it's correct.** `DoubleMetaphoneOracleTest` (`shared/src/jvmTest`) uses the **original commons-codec jar as an answer key**. That jar is a test-only dependency and never ships in the apps. The test runs both implementations on:
- curated tricky words (`bacchus`, `schermerhorn`, `filipowicz`, …),
- every 1–3 letter string,
- 150,000 seeded generated words built from the letter groups that trigger special rules,

at code lengths 1, 4, 6 and 12, for primary and alternate codes. Every code must match exactly. `DoubleMetaphoneTest` (commonTest) also runs commons-codec's own published vectors on every target, including iOS.

**How the search engine uses it** (DOMAIN_RULES S8):
- code length 6 instead of the default 4, which gives fewer accidental collisions on long city names,
- both the primary and the alternate codes (`yohannesburg` matches Johannesburg's alternate `AHNSPR`),
- codes of only 2 characters count only against whole city names (`roam` → Rome, but not `sitty` → "Mexico City"),
- codes for the index are precomputed once.

**How to update it.** Re-translate from the newer Commons Codec release, bump the test dependency in `gradle/libs.versions.toml` to the same version, and run the oracle test.

## 9. How AI was used

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
| 2026‑10‑30 | Search prototype | Prototyped prefix, typo, phonetic and n-gram matching in Python against the fixture cities, using codes from the real commons-codec jar, and compared tiered vs blended mixing (114 cases) | Test cases chosen to cover the four kinds of mistyping, plus negatives |
| 2026‑10‑30 | Double Metaphone port | Translated commons-codec's `DoubleMetaphone.java` to Kotlin | Oracle test against the original jar (150k+ words), plus commons-codec's own vectors |
| 2026‑10‑30 | Search engine | Wrote the trie, trigram index, edit distance, ranking and tiered engine, with 54 tests | Tests pass on the JVM; the iOS klib compiles; owner review pending |
| 2026‑10‑30 | `airports.json` | Recalled approximate 2024 passenger totals for the 32 airports | **Not verified**: labelled as approximate in the file and in §5 |
