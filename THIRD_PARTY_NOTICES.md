# Third-party notices

This project includes code derived from the third-party software listed below. Each component keeps its original license. The project's process for third-party code is in `CLAUDE.md` (How we work, rule 5).

## Apache Commons Codec: Double Metaphone

| | |
|---|---|
| Component | `org.apache.commons.codec.language.DoubleMetaphone` |
| Version | 1.23.0 |
| Project | https://commons.apache.org/proper/commons-codec/ |
| License | Apache License 2.0 (full text in [`LICENSE`](LICENSE)) |
| Our file | `shared/src/commonMain/kotlin/travel/roame/takehome/vendor/commonscodec/DoubleMetaphone.kt` |
| How it's used | Phonetic matching in airport search (`docs/DOMAIN_RULES.md` S8) |
| Modifications | Translated from Java to Kotlin common code so it also runs on iOS. The modifications are listed in the file header and in `docs/SUBMISSION_NOTES.md` §8. |
| Verification | `shared/src/jvmTest/.../DoubleMetaphoneOracleTest.kt` compares the port with the original library. The library is used only in tests and never ships in the apps. |

NOTICE from Apache Commons Codec:

```
Apache Commons Codec
Copyright 2002-2026 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (https://www.apache.org/).
```

The algorithm is Lawrence Philips' Double Metaphone ("The Double Metaphone Search Algorithm", *C/C++ Users Journal*, June 2000).

## Material Symbols icons (Android)

| | |
|---|---|
| Component | 13 icons from Google's Material Symbols, Rounded style, 24 px: `search`, `tune`, `crown`, `airline_seat_recline_extra`, `airline_seat_recline_normal`, `auto_awesome`, `flight`, `calendar_month`, `arrow_back`, `close`, `favorite` (outline and filled), `near_me` |
| Source | https://github.com/google/material-design-icons at commit `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7`, `symbols/android/<icon>/materialsymbolsrounded/` |
| License | Apache License 2.0 (full text in [`LICENSE`](LICENSE)). Copyright Google LLC. The repository has no NOTICE file |
| Our files | `androidApp/src/main/res/drawable/ms_*.xml`, each with a header naming its source file |
| How it's used | The Android app's icons (D37). The iOS app uses Apple's SF Symbols, which ship with iOS and aren't copied |
| Modifications | Renamed with an `ms_` prefix, and `android:tint="?attr/colorControlNormal"` removed so the app can tint them |

## Loyalty-program logos

Three logos, from Wikimedia Commons, where each file is marked **public domain** (too simple for copyright) and **trademarked**. They're used only to identify each program next to its flights (D34). Avios and Flying Blue have no freely licensed logo on Commons, so the apps draw a plain badge with the program's name instead.

| Program | Commons file | Licence on Commons | Trademark owner | Our files |
|---|---|---|---|---|
| Aeroplan | [File:Aeroplan 2020 Logo.svg](https://commons.wikimedia.org/wiki/File:Aeroplan_2020_Logo.svg) | Public domain; trademarked | Aeroplan Inc. / Air Canada | `logo_aeroplan.png` (rendered from the SVG, 96 px high) |
| MileagePlus | [File:United MileagePlus wordmark.svg](https://commons.wikimedia.org/wiki/File:United_MileagePlus_wordmark.svg) | Public domain; trademarked | United Airlines | `logo_mileageplus.png` (rendered from the SVG, 96 px high) |
| Mileage Plan | [File:AAG Mileage Plan logo.png](https://commons.wikimedia.org/wiki/File:AAG_Mileage_Plan_logo.png) | Public domain; trademarked | Alaska Airlines | `logo_mileage_plan.png` (unchanged) |

The files are in `androidApp/src/main/res/drawable-nodpi/` and `iosApp/Logos/`. This project isn't affiliated with or endorsed by these programs.

## Test-only dependencies

These are used only by tests and are not distributed with the apps:

- `commons-codec:commons-codec:1.23.0` (Apache License 2.0): the oracle for the port above.
