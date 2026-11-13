# EarthWatch for Android

Native Kotlin and Jetpack Compose application for satellite tracking and Earth observation.

## Milestone 1 status

The first visual foundation is implemented with the requested application ID, a light blue and white design system, welcome flow, five-tab navigation, map preview, catalog cards, pass preview, imagery placeholder, saved-location interactions, settings, satellite details, and a planned-globe state.

Screens that do not yet have a data provider are explicitly labeled as previews. Sample catalog and pass rows are UI fixtures; they are not live tracking or predictions. The map illustration is not a navigable map and the imagery illustration is not a real acquisition.

## Build

Open this directory in Android Studio and sync the Gradle project. It uses Android Gradle Plugin 9.4.0 with built-in Kotlin, Jetpack Compose, minimum API 26, and target API 37. The Gradle wrapper is included for command-line builds.

```powershell
.\gradlew.bat assembleDebug
```

## Planned milestones

1. Visual foundation and navigation (current)
2. Configurable MapLibre 2D map and coordinate entry
3. CelesTrak catalog, orbital-element caching, and SGP4 propagation
4. Geometric pass predictions, time-zone handling, and opt-in notifications
5. Public imagery provider and persistent saved locations
6. Accessibility, automated tests, and release preparation

## Privacy and services

The current milestone has no network calls, analytics, ads, account registration, or location permissions. Future integrations will be documented here with provider attribution, terms, quotas, and required permissions. No private credentials will be placed in the APK.

## License

This project is intended to use Apache-2.0 licensing. No third-party image assets are bundled in the current milestone; the Earth, map, satellite-card, and imagery visuals are drawn or represented in-app as illustrative UI.
