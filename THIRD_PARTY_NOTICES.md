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

## Test-only dependencies

These are used only by tests and are not distributed with the apps:

- `commons-codec:commons-codec:1.23.0` (Apache License 2.0): the oracle for the port above.
