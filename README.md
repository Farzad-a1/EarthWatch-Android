# EarthWatch for Android

Native Kotlin and Jetpack Compose application for satellite tracking and Earth observation.

## Implemented milestones

### Milestone 1: Visual foundation

The application uses the requested ID `com.EarthWatch.myapp`, a light blue and white design system, welcome flow, five-tab navigation, catalog cards, pass preview, imagery placeholder, saved-location interactions, settings, satellite details, and a planned-globe state.

### Milestone 2: Interactive map

Explore now uses MapLibre with the public OpenFreeMap Liberty style. Users can pan and zoom, tap the map to move the selected coordinate, enter a location name and latitude/longitude, and recenter the map. The style URL can be overridden with `-PearthwatchMapStyleUrl=https://your-provider/style.json`. The map displays attribution for OpenFreeMap, OpenMapTiles, and OpenStreetMap and shows loading, retryable error, and timeout states.

Screens that do not yet have a data provider are labeled as previews. Satellite and pass rows remain UI fixtures; they are not live tracking or predictions. Satellite positions and orbital tracks are planned for Milestone 3. The imagery panel is not a real acquisition.

## Build

Open this directory in Android Studio and sync the Gradle project. It uses Android Gradle Plugin 9.4.0 with built-in Kotlin, Jetpack Compose, minimum API 26, and target API 37. The Gradle wrapper is included for command-line builds.

```powershell
.\gradlew.bat assembleDebug
```

## Remaining milestones

3. CelesTrak catalog, orbital-element caching, and SGP4 propagation
4. Geometric pass predictions, time-zone handling, and opt-in notifications
5. Public imagery provider and persistent saved locations
6. Accessibility, automated tests, and release preparation

## Privacy and services

The map requests public style, font, and vector-tile resources from OpenFreeMap. No analytics, ads, account registration, or location permissions are used. The map does not read device location; the initial Tehran coordinate and any changes are supplied by the user. Future integrations will be documented here with provider attribution, terms, quotas, and required permissions. No private credentials will be placed in the APK.

## License

This project is intended to use Apache-2.0 licensing. No third-party image assets are bundled in the current milestone; the Earth, map, satellite-card, and imagery visuals are drawn or represented in-app as illustrative UI.
