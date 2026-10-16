# Touristeye

Point your phone at the city and see what you are looking at. Touristeye shows the live camera image and
places a label over every tourist sight in that direction (monuments, museums, churches, viewpoints,
castles, mountain peaks…) within a radius you choose, with its distance and compass direction.

One Kotlin codebase for **Android** and **iOS** (Kotlin Multiplatform + Compose Multiplatform).

## What you see

- **Camera view with AR labels.** Each place gets a label with a stem down to the exact point where it is.
  Labels never overlap: nearer places sit closest to the horizon, farther ones stack above and fade a little.
  Mountain peaks with a known height are drawn at their real elevation angle.
- **Compass tape** at the top with your heading and a dot for every place around you.
- **Radar** (bottom left) with places around you, rotated so the way you face is up.
- **Radius slider**: 250 m, 500 m, 1 km, 2 km, 5 km, 10 km or 20 km.
- **Category filters**: Sights, Museums, Art, Historic, Religious, Viewpoints, Nature.
- **Tap a label** to see details and open its Wikipedia article, website or map. When the selected place
  is out of view, an arrow at the screen edge tells you which way to turn.

Android follows the phone's rotation (portrait and landscape); iOS is portrait only for now.

## How it works

| Piece | Where |
|---|---|
| GPS position | Android `LocationManager` (GPS + network) · iOS `CLLocationManager` |
| Where the camera points | Accelerometer + gyroscope + magnetometer fused: Android rotation-vector sensor (fallback: gravity/accelerometer + magnetometer) with magnetic declination → true north · iOS CoreMotion device motion in `XTrueNorthZVertical` |
| Camera preview | Android CameraX `PreviewView` · iOS `AVCaptureVideoPreviewLayer` |
| Camera field of view | Android: sensor size and focal length (Camera2) · iOS: `activeFormat.videoFieldOfView` |
| Places | [OpenStreetMap](https://www.openstreetmap.org) via the public Overpass servers (several, tried in turn when one is slow) and nearby Wikipedia articles, both free with no API key. Both are asked at once; Wikipedia articles are categorised from their short description and dropped when OpenStreetMap already has the place |
| Projection, label layout, filtering | Shared Kotlin in `composeApp/src/commonMain` |

The shared code turns the device's camera direction into azimuth, pitch and roll, then projects each
place's bearing and elevation through a pinhole camera model that matches the cropped camera preview.
Places are downloaded once for the area and downloaded again only when you move more than a quarter
of the radius or widen it.

```
composeApp/src/
  commonMain/kotlin/com/touristeye/
    geo/        distances, bearings, POI model
    ar/         orientation math, camera projection, label stacking
    data/       Overpass + Wikipedia sources
    app/        ArController (state, when to download)
    ui/         Compose screens: overlay, compass tape, radar, panels
    platform/   expect declarations (camera, sensors, location, permissions)
  androidMain/  CameraX, sensors, location, permissions, MainActivity
  iosMain/      AVFoundation, CoreMotion, CoreLocation, permissions
  commonTest/   unit tests for the math, parsing and controller
iosApp/         SwiftUI host app (XcodeGen spec)
```

## Run on Android

1. Open the `touristeye` folder in Android Studio (Ladybug or newer).
2. Let Gradle sync, pick the `composeApp` run configuration and run it on a **real phone**
   (emulators have no real camera or compass).

From the command line: `./gradlew :composeApp:installDebug`.

### Download a ready-made APK

GitHub builds the APK on every push ([releases page](https://github.com/jonbul/touristeye/releases)):

- **Any branch except `main`**: a *snapshot* pre-release named `snapshot-<branch>`, replaced on each push,
  with version `X.Y.Z-SNAPSHOT.<build>+<branch>`.
- **`main`** (when a pull request is merged): a numbered release `vX.Y.Z`. `X.Y` is `appVersion` in
  `gradle.properties` (raise it for bigger releases); `Z` goes up by one with every release.

## Run on iOS (needs a Mac with Xcode)

```bash
brew install xcodegen
cd iosApp && xcodegen          # creates iosApp.xcodeproj
open iosApp.xcodeproj
```

In Xcode set your team under *Signing & Capabilities*, choose your iPhone and press Run. The first build
compiles the Kotlin framework through Gradle, so it takes a few minutes. The simulator has no camera,
so use a real device.

## Tests

`./gradlew :composeApp:allTests` (or `testDebugUnitTest` for Android only) runs the shared tests:
geo math, camera projection and roll, label stacking, Overpass/Wikipedia parsing and the download logic.

## Tuning

- Default radius and options: `RadiusOptions` and `ArState.radiusMeters` in `app/ArController.kt`.
- Which OpenStreetMap tags count as sights: `OverpassPoiSource.buildQuery` and `categorize`.
- Label size, max rows and count: top of `ui/ArOverlay.kt` and `LabelLayout.layout(maxLevels, maxLabels)`.
- Sensor smoothing: `OrientationSmoother(alpha)` (lower = smoother but slower).

## Notes

- Compass accuracy is the main limit on how well labels line up; the app asks you to calibrate when the
  sensor reports low accuracy (Android) and iOS shows its own calibration screen.
- The public Overpass servers are free but rate-limited. For a published app, consider running your own
  Overpass instance or caching results.
- Place data © OpenStreetMap contributors (ODbL) and Wikipedia (CC BY-SA).
