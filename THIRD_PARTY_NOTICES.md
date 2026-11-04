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

## Material Symbols icons (both apps)

| | |
|---|---|
| Component | 22 icons from Google's Material Symbols, Rounded style, 24 px: `search`, `search_off`, `tune`, `crown`, `airline_seat_recline_extra`, `airline_seat_recline_normal`, `auto_awesome`, `flight`, `calendar_month`, `arrow_back`, `close`, `check`, `chevron_right`, `cloud_off`, `favorite` (outline and filled), `near_me`, `person`, `settings`, `lock`, `info`, `logout` |
| Source | https://github.com/google/material-design-icons at commit `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7`: `symbols/android/<icon>/materialsymbolsrounded/` for Android, and `symbols/web/<icon>/materialsymbolsrounded/` for iOS |
| License | Apache License 2.0 (full text in [`LICENSE`](LICENSE)). Copyright Google LLC. The repository has no NOTICE file |
| Our files | Android: `androidApp/src/main/res/drawable/ms_*.xml`, each with a header naming its source file. iOS: `iosApp/Assets.xcassets/ms_*.imageset/ms_*.svg`, the same 22 icons under the same names |
| How it's used | Every icon in both apps, so the two look the same (D37, D38) |
| Modifications | Android: renamed with an `ms_` prefix, and `android:tint="?attr/colorControlNormal"` removed so the app can tint them. iOS: renamed with an `ms_` prefix; the SVGs are byte-for-byte the upstream files, set to render as templates so the app can tint them |
| Verification | Each iOS SVG was compared byte for byte with the upstream file at the commit above (2026‑11‑04) |

## Inter font (both apps)

| | |
|---|---|
| Component | Inter 4.1, four weights: Regular, Medium, SemiBold, Bold (static TTFs) |
| Source | https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip, `extras/ttf/` |
| License | SIL Open Font License 1.1. Copyright (c) 2016 The Inter Project Authors. The full licence is next to the fonts in the repo: [`iosApp/Fonts/OFL.txt`](iosApp/Fonts/OFL.txt) and [`androidApp/OFL-Inter.txt`](androidApp/OFL-Inter.txt). Inside the apps, each font file carries its copyright and licence notice in its own metadata (the `name` table's copyright, licence and licence-URL fields), as the OFL allows; the Info page also credits the font |
| Our files | `iosApp/Fonts/Inter-*.ttf` and `androidApp/src/main/res/font/inter_*.ttf` |
| How it's used | All text in both apps, so the two look the same (D38, D40) |
| Modifications | None. Android's copies are renamed to lowercase, as Android resources must be. The OFL allows bundling the fonts with software; it forbids selling them on their own |
| Verification | All four files and the licence were compared byte for byte with the release zip (2026‑11‑04) |

## Loyalty-program logos

Three logos, from Wikimedia Commons, where each file is marked **public domain** (too simple for copyright) and **trademarked**. They're used only to identify each program next to its flights (D34). Avios and Flying Blue have no freely licensed logo on Commons, so the apps draw a plain badge with the program's name instead.

| Program | Commons file | Licence on Commons | Trademark owner | Our files |
|---|---|---|---|---|
| Aeroplan | [File:Aeroplan 2020 Logo.svg](https://commons.wikimedia.org/wiki/File:Aeroplan_2020_Logo.svg) | Public domain; trademarked | Aeroplan Inc. / Air Canada | `logo_aeroplan.png` (rendered from the SVG, 96 px high) |
| MileagePlus | [File:United MileagePlus wordmark.svg](https://commons.wikimedia.org/wiki/File:United_MileagePlus_wordmark.svg) | Public domain; trademarked | United Airlines | `logo_mileageplus.png` (rendered from the SVG, 96 px high) |
| Mileage Plan | [File:AAG Mileage Plan logo.png](https://commons.wikimedia.org/wiki/File:AAG_Mileage_Plan_logo.png) | Public domain; trademarked | Alaska Airlines | `logo_mileage_plan.png` (unchanged) |

The files are in `androidApp/src/main/res/drawable-nodpi/` and `iosApp/Logos/`. This project isn't affiliated with or endorsed by these programs.

## Destination photos (both apps)

24 photos from Wikimedia Commons, one per destination city in the data and one for the landing page's banner (D33). 21 come from Commons' Quality images; Doha, Dubai and Johannesburg had none that fit, so theirs come from a general search. Each one's licence, author and any requested credit were read from the file's Commons metadata on 2026‑11‑04. Each was cropped to 3:2 around its centre and scaled to 900 × 600; the changed photo keeps the original's licence. The same credits are shown in each app under Account › Info › Photo credits.

| Photo | Commons file | Credit | Licence |
|---|---|---|---|
| Amsterdam (`dest_amsterdam`) | [Water reflection of canal houses at blue hour in Damrak Amsterdam the Netherlands.jpg](https://commons.wikimedia.org/wiki/File:Water_reflection_of_canal_houses_at_blue_hour_in_Damrak_Amsterdam_the_Netherlands.jpg) | Basile Morin | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Athens (`dest_athens`) | [Acropolis Parthenon Athens Greece.jpg](https://commons.wikimedia.org/wiki/File:Acropolis_Parthenon_Athens_Greece.jpg) | Jebulon | [CC0](http://creativecommons.org/publicdomain/zero/1.0/deed.en) |
| Auckland (`dest_auckland`) | [Auckland Skyline as seen from Devonport 20100128 3.jpg](https://commons.wikimedia.org/wiki/File:Auckland_Skyline_as_seen_from_Devonport_20100128_3.jpg) | DXR | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Bangkok (`dest_bangkok`) | [Templo Wat Arun, Bangkok, Tailandia, 2013-08-22, DD 37.jpg](https://commons.wikimedia.org/wiki/File:Templo_Wat_Arun,_Bangkok,_Tailandia,_2013-08-22,_DD_37.jpg) | Diego Delso | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Barcelona (`dest_barcelona`) | [Sagrada Familia Barcelona 2.jpg](https://commons.wikimedia.org/wiki/File:Sagrada_Familia_Barcelona_2.jpg) | kallerna | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Doha (`dest_doha`) | [Doha Skyline 10.jpg](https://commons.wikimedia.org/wiki/File:Doha_Skyline_10.jpg) | Zairon | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) |
| Dubai (`dest_dubai`) | [Dubai Skyline mit Burj Khalifa (cropped).jpg](https://commons.wikimedia.org/wiki/File:Dubai_Skyline_mit_Burj_Khalifa_(cropped).jpg) | Tim Reckmann from Hamm, Deutschland | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Dublin (`dest_dublin`) | [River Liffey and Samuel Beckett Bridge.jpg](https://commons.wikimedia.org/wiki/File:River_Liffey_and_Samuel_Beckett_Bridge.jpg) | Christian David | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Frankfurt (`dest_frankfurt`) | [Skyline Frankfurt 2011-01.jpg](https://commons.wikimedia.org/wiki/File:Skyline_Frankfurt_2011-01.jpg) | Thomas Wolf (Der Wolf im Wald) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Hong Kong (`dest_hong_kong`) | [Hong Kong Skyline Restitch - Dec 2007.jpg](https://commons.wikimedia.org/wiki/File:Hong_Kong_Skyline_Restitch_-_Dec_2007.jpg) | Diliff | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0) |
| Johannesburg (`dest_johannesburg`) | [Johannesburg-city-skyline-night.jpg](https://commons.wikimedia.org/wiki/File:Johannesburg-city-skyline-night.jpg) | Frankbel | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Lisbon (`dest_lisbon`) | [Lissabon - Alfama - Largo de Santa Luzia - Streetcar - 2.jpg](https://commons.wikimedia.org/wiki/File:Lissabon_-_Alfama_-_Largo_de_Santa_Luzia_-_Streetcar_-_2.jpg) | Ingo Mehling | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| London (`dest_london`) | [Puente de la Torre, Londres, Inglaterra, 2014-08-11, DD 092.JPG](https://commons.wikimedia.org/wiki/File:Puente_de_la_Torre,_Londres,_Inglaterra,_2014-08-11,_DD_092.JPG) | Diego Delso | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Madrid (`dest_madrid`) | [Palacio Real de Madrid - 13.jpg](https://commons.wikimedia.org/wiki/File:Palacio_Real_de_Madrid_-_13.jpg) | Carlos Delgado | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Melbourne (`dest_melbourne`) | [Melbourne CBD skyline, south view 20230221 1.jpg](https://commons.wikimedia.org/wiki/File:Melbourne_CBD_skyline,_south_view_20230221_1.jpg) | DXR | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Mexico City (`dest_mexico_city`) | [Palaco de Bellas Artes, Mexico City, from across Eje Central Lazaro Cardenas in evening.jpg](https://commons.wikimedia.org/wiki/File:Palaco_de_Bellas_Artes,_Mexico_City,_from_across_Eje_Central_Lazaro_Cardenas_in_evening.jpg) | Daniel Case | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Paris (`dest_paris`) | [Paris - The Eiffel Tower in spring - 2307.jpg](https://commons.wikimedia.org/wiki/File:Paris_-_The_Eiffel_Tower_in_spring_-_2307.jpg) | © Jorge Royan / http://www.royan.com.ar | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Rome (`dest_rome`) | [Colosseum in Rome, Italy - April 2007.jpg](https://commons.wikimedia.org/wiki/File:Colosseum_in_Rome,_Italy_-_April_2007.jpg) | Diliff | [CC BY-SA 2.5](https://creativecommons.org/licenses/by-sa/2.5) |
| Sao Paulo (`dest_sao_paulo`) | [Ponte estaiada Octavio Frias - Sao Paulo.jpg](https://commons.wikimedia.org/wiki/File:Ponte_estaiada_Octavio_Frias_-_Sao_Paulo.jpg) | Marcosleal | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Seoul (`dest_seoul`) | [Gyeonghoeru (Royal Banquet Hall) at Gyeongbokgung Palace, Seoul.jpg](https://commons.wikimedia.org/wiki/File:Gyeonghoeru_(Royal_Banquet_Hall)_at_Gyeongbokgung_Palace,_Seoul.jpg) | © Frank Schulenburg | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Singapore (`dest_singapore`) | [Marina Bay Sands and illuminated polyhedral building Louis Vuitton over the water at blue hour with pink clouds in Singapore.jpg](https://commons.wikimedia.org/wiki/File:Marina_Bay_Sands_and_illuminated_polyhedral_building_Louis_Vuitton_over_the_water_at_blue_hour_with_pink_clouds_in_Singapore.jpg) | Basile Morin | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |
| Sydney (`dest_sydney`) | [Sydney Opera House - Dec 2008.jpg](https://commons.wikimedia.org/wiki/File:Sydney_Opera_House_-_Dec_2008.jpg) | Diliff | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| Tokyo (`dest_tokyo`) | [Tokyo Tower, Minato City.jpg](https://commons.wikimedia.org/wiki/File:Tokyo_Tower,_Minato_City.jpg) | David Kernan | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) |
| Banner (Tuscany) (`hero_landscape`) | [Sunrise in Crete dell'Orcia.jpg](https://commons.wikimedia.org/wiki/File:Sunrise_in_Crete_dell%27Orcia.jpg) | Ciorophotoproject | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0) |

Our files: `androidApp/src/main/res/drawable-nodpi/dest_*.jpg` and `hero_landscape.jpg`, and `iosApp/Assets.xcassets/dest_*.imageset` and `hero_landscape.imageset`. About 3.2 MB per app. The photos show places, not products; any buildings or trademarks in them belong to their owners.

## Test-only dependencies

These are used only by tests and are not distributed with the apps:

- `commons-codec:commons-codec:1.23.0` (Apache License 2.0): the oracle for the port above.
