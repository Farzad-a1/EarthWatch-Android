# EarthWatch for Android

Native Kotlin and Jetpack Compose application for satellite tracking and Earth observation.

## Implemented milestones

### Milestone 1: Visual foundation

The application uses the requested ID `com.EarthWatch.myapp`, a light blue and white design system, welcome flow, five-tab navigation, live catalog views, pass predictions, an imagery placeholder, saved-location interactions, settings, satellite details, and a planned-globe state.

### Milestone 2: Interactive map

Explore now uses MapLibre with the public OpenFreeMap Liberty style. Users can pan and zoom, tap the map to move the selected coordinate, enter a location name and latitude/longitude, and recenter the map. The style URL can be overridden with `-PearthwatchMapStyleUrl=https://your-provider/style.json`. The map displays attribution for OpenFreeMap, OpenMapTiles, and OpenStreetMap and shows loading, retryable error, and timeout states.

### Milestone 3: Live satellite catalog and orbit display

The satellite list reads CelesTrak OMM JSON for the Earth Resources, Weather, and TDRSS groups. The app keeps the latest response per group in an on-device cache for at least two hours, uses cached records when offline, and stops further group requests after a request failure or non-200 response. The Room cache migrates records from the earlier SharedPreferences format. Search and category filters use the fetched catalog; the map plots propagated satellite positions and ground tracks. Satellite detail pages show the source element epoch and retrieval time.

Orbit propagation uses Orekit's SGP4 implementation with CelesTrak OMM fields. Position calculations are converted to WGS-84 ground coordinates; Orekit's SI positions are converted to kilometers. The reference test checks the Vallado satellite 00005 TEME epoch vector. Current map refresh covers up to 80 catalog objects, while ground tracks are drawn for up to eight objects to keep map rendering responsive.

The catalog uses OMM rather than legacy five-digit TLE strings so identifiers above 99,999 remain supported. Groups are fetched no more than once every two hours, in line with [CelesTrak's GP data documentation](https://celestrak.org/NORAD/documentation/gp-data-formats.php) and [usage policy](https://celestrak.org/usage-policy.php). Orbit elements are a public source, and they do not provide precision navigation; displayed positions are propagated estimates.

### Milestone 4: Pass predictions and reminders

The Passes tab calculates upcoming AOS/LOS windows from current SGP4 elements for the selected coordinates, applying a 10° minimum elevation and searching the next 24 hours. The date and time display uses the device time zone; observer coordinates are stored on-device. Users can opt into Android notifications scheduled about 15 minutes before a predicted pass.

WorkManager reminders are persistent and respect Android power management, but their delivery time is best-effort and can be delayed. Notifications require the Android 13+ runtime permission. Predictions depend on the age and quality of public GP elements and are approximate visibility windows.

### Milestone 5: NASA imagery and persistent locations

The Imagery tab displays NASA GIBS daily MODIS Terra or Aqua corrected-reflectance true-color tiles, with date selection and the observer location as the map center. These are global visual composites, not individual Sentinel acquisitions or downloadable scenes; cloud, daylight, and product-availability gaps are possible. Tile imagery can lag the observation date. GIBS tile service patterns and date dimensions are documented in [NASA's GIBS access guide](https://nasa-gibs.github.io/gibs-api-docs/access-basics/).

Saved locations and per-group CelesTrak caches are stored in Room. Saved coordinates can be selected as the observer site for pass predictions. The selected observer location is kept locally and no device location permission is requested.

## Build

Open this directory in Android Studio and sync the Gradle project. It uses Android Gradle Plugin 9.4.0 with built-in Kotlin, Jetpack Compose, minimum API 26, and target API 37. The Gradle wrapper is included for command-line builds.

```powershell
.\gradlew.bat assembleDebug
```

## Remaining milestones

6. Accessibility, broader automated tests, release preparation, and store assets

## Privacy and services

The map requests public style, font, and vector-tile resources from OpenFreeMap. The catalog requests public OMM group data from CelesTrak; the Imagery tab requests public NASA GIBS raster tiles. Saved coordinates and catalog cache records stay on-device in Room. No analytics, ads, account registration, or location permissions are used. The map does not read device location; the initial Tehran coordinate and any changes are supplied by the user. No private credentials are included in the APK.

## License

This project is intended to use Apache-2.0 licensing. No third-party image assets are bundled in the current milestone; the Earth, map, satellite-card, and imagery visuals are drawn or represented in-app as illustrative UI.
