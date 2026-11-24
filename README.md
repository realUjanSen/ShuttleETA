# Shuttle ETA (Android)

Android version of the web ETA predictor (`eta_model_app.html`). Same model, same route
bins, same blending as `predict_eta.py`, except that the web app's session blend is not used.

## What it does
- Shows the route as congestion-coloured segments with a black outline (green / yellow / red).
- Morning (Home to Office) or evening (Office to Home) route, departure time picker.
- Pick the start and destination in either of two ways:
  - **Pick by tapping map**: tap snaps to the nearest route bin.
  - **Pick with slider**: move along the route, 0 to 100 %.
- "Set start" / "Set destination" chooses which point the next tap or slider move sets.
- Predict ETA: final ETA, arrival time, model estimate, local route-profile estimate,
  blend weight, and the top 5 choke points ahead.

## Building
1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync. Dependencies come from `gradle/libs.versions.toml`.
3. Run on a device or emulator (Android 8.1+, minSdk 27).

Map tiles come from OpenStreetMap, so the app needs an internet connection.

## Model files (app/src/main/assets)
| File | Made by |
|---|---|
| `et_model.bin` | `export_android_assets.py` (Extra Trees, 25 trees, gzip) |
| `route_profile_morning.csv`, `route_profile_evening.csv` | training script |
| `choke_points.csv`, `session_statistics.csv` | training script |
| `route_refs.json` | `export_android_assets.py` |

After you retrain on your machine, run this from the project folder and copy the output
into `app/src/main/assets`:

    python export_android_assets.py

## Checking the predictor without Android
`tools/PredictorCheck.java` runs the same prediction code on a desktop JDK:

    javac -d build app/src/main/java/com/example/shuttleeta/model/*.java tools/PredictorCheck.java
    java -cp build PredictorCheck app/src/main/assets

The output matches `predict_eta.py` (run without `--session-id`) for the same inputs.
