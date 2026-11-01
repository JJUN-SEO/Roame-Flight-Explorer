# Roame Flight Explorer

A Roame-inspired award-flight explorer, built natively twice: **SwiftUI** on iOS and **Jetpack Compose** on Android. Both apps sit on one **Kotlin Multiplatform** layer that handles networking, persistence, search, filtering and map aggregation. A small local API serves `fixtures/flights.json`.

> **Status: in progress.** Built so far: the shared airport search engine (prefix tree + fuzzy fallback + ranking; [DOMAIN_RULES §2](docs/DOMAIN_RULES.md#2-search-s)). The starter apps still display the shared "Hello world!". Commands marked † don't exist yet; this README will be finalised before submission.

## Docs

| Doc | Contents |
|---|---|
| [Requirements](docs/REQUIREMENTS.md) | Every requirement from the brief, with IDs and acceptance criteria, plus scalability targets |
| [Domain rules](docs/DOMAIN_RULES.md) | Exact search, filter, map, selection, saved and offline behaviour, plus fixture facts |
| [Architecture](docs/ARCHITECTURE.md) | Modules, data flow, persistence, networking, iOS interop, libraries, decision log, scalability |
| [UI spec](docs/UI_SPEC.md) | Screens, state catalogue, components, design tokens |
| [Test plan](docs/TEST_PLAN.md) | Shared test matrix, manual QA checklist, demo script |
| [Plan](docs/PLAN.md) | Build order: phases, steps, review gates, risks, priority tiers |
| [Submission notes](docs/SUBMISSION_NOTES.md) | Approach, decision record, assumptions, tradeoffs, limitations, scalability, AI usage |
| [CLAUDE.md](CLAUDE.md) | Working agreement for AI coding agents, including the review-before-push rule |

## Prerequisites

These come from the starter:

- **Android:** Android Studio Quail 3 or newer. Install Android SDK 37.0 and build-tools 37.0.0 if prompted.
- **iOS:** a Mac with Xcode 26.4 or newer. Install Android Studio or a JDK first; Xcode builds the KMP framework automatically.
- Gradle selects or downloads JDK 25 automatically. First builds need internet access.

Kotlin 2.4.21 · Gradle 9.7.0 · AGP 9.3.1 · Compose BOM 2026.09.00 · Activity 1.13.0 · Swift 6 · iOS 17.

## Quick start

**1. Start the local API †**

```bash
./gradlew :server:run
```

This serves `GET http://localhost:8080/flights`.

**2. Android.** Open this folder in Android Studio and run `androidApp` on an emulator. The emulator reaches the API at `http://10.0.2.2:8080`. Or, from the command line:

```bash
./gradlew :androidApp:installDebug
```

**3. iOS.** Open `iosApp/RoameStarter.xcodeproj`, select the `RoameStarter` scheme and an iPhone simulator, then Run. On first open, build once (⌘B) to generate the `Shared` module. The initial "No such module 'Shared'" editor error clears after that build.

## Tests

Shared tests on any OS:

```bash
./gradlew :shared:jvmTest
```

Shared tests on the iOS simulator (Mac only):

```bash
./gradlew :shared:iosSimulatorArm64Test
```

The manual QA results for both platforms are in [docs/TEST_PLAN.md](docs/TEST_PLAN.md#5-manual-qa-checklist).

## Layout

```
server/        Local API (Ktor) †
shared/        Kotlin Multiplatform: models, networking, SQLite, search/filter, ExplorerStore
androidApp/    Jetpack Compose UI
iosApp/        SwiftUI UI
fixtures/      flights.json: 1,000 fictional flights across 32 airports (provided)
               airports.json: approximate annual passengers for search ranking (ours; a copy ships in the app)
docs/          Planning and submission docs
```

The Gradle wrapper's upstream license is included in `LICENSE`. Third-party code (a Kotlin port of Apache Commons Codec's Double Metaphone) is credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
