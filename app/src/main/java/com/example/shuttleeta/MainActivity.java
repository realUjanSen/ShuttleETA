package com.example.shuttleeta;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import com.example.shuttleeta.Installer;
import java.io.File;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.shuttleeta.model.ChokePoint;
import com.example.shuttleeta.model.EtaPredictor;
import com.example.shuttleeta.model.ExtraTreesModel;
import com.example.shuttleeta.model.PredictionResult;
import com.example.shuttleeta.model.RouteProfile;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int COLOR_START = 0xFF14A44D;   // same as web app
    private static final int COLOR_DEST = 0xFFD73434;
    private static final int COLOR_INACTIVE = 0xFF7A8797;
    private static final int COLOR_GREEN = 0xFF2CA94F;   // congestion <= 0.33
    private static final int COLOR_YELLOW = 0xFFD9AA1F;  // congestion <= 0.66
    private static final int COLOR_RED = 0xFFD73434;     // congestion > 0.66

    private final ExecutorService background = Executors.newSingleThreadExecutor();

    private MapView map;
    private Spinner routeSpinner;
    private Button timeButton, startModeButton, destModeButton, tapMethodButton, sliderMethodButton;
    private Button predictButton, clearDestButton, checkUpdateButton;
    private SeekBar routeSlider;
    private TextView sliderLabel, statusText, resultText;

    private EtaPredictor predictor;
    private String route = "morning";
    private RouteProfile profile;
    private int minuteOfDay = 8 * 60;
    private boolean selectingDestination = false;
    private boolean sliderMode = false;

    private double[] start;
    private double[] destination;
    private int startBin = -1;
    private int destBin = -1;
    private Marker startMarker;
    private Marker destMarker;
    private final List<Overlay> routeOverlays = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        // OpenStreetMap blocks tile requests without an identifying User-Agent (HTTP 403).
        Configuration.getInstance().setUserAgentValue(
                "ShuttleETA-Android/1.0 (+https://github.com/realUjanSen/ShuttleETA)");
        // Keep downloaded tiles in the app cache so the map works again without refetching.
        Configuration.getInstance().setOsmdroidBasePath(new java.io.File(getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new java.io.File(getCacheDir(), "osmdroid/tiles"));

        routeSpinner = findViewById(R.id.routeSpinner);
        timeButton = findViewById(R.id.timeButton);
        startModeButton = findViewById(R.id.startModeButton);
        destModeButton = findViewById(R.id.destModeButton);
        tapMethodButton = findViewById(R.id.tapMethodButton);
        sliderMethodButton = findViewById(R.id.sliderMethodButton);
        predictButton = findViewById(R.id.predictButton);
        clearDestButton = findViewById(R.id.clearDestButton);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);
        checkUpdateButton.setOnClickListener(v -> checkForUpdate());
        routeSlider = findViewById(R.id.routeSlider);
        sliderLabel = findViewById(R.id.sliderLabel);
        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);
        map = findViewById(R.id.mapView);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (predictor == null || sliderMode) return false;
                setPointBin(profile.nearestBin(p.getLatitude(), p.getLongitude()));
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.routes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        routeSpinner.setAdapter(adapter);
        routeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                route = position == 0 ? "morning" : "evening";
                if (predictor != null) {
                    profile = predictor.profile(route);
                    clearSelection();
                    drawRoute();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        timeButton.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, hour, minute) -> {
                minuteOfDay = hour * 60 + minute;
                timeButton.setText(String.format(Locale.US, "%02d:%02d", hour, minute));
            }, minuteOfDay / 60, minuteOfDay % 60, true).show();
        });

        startModeButton.setOnClickListener(v -> setSelectingDestination(false));
        destModeButton.setOnClickListener(v -> setSelectingDestination(true));
        tapMethodButton.setOnClickListener(v -> setSliderMode(false));
        sliderMethodButton.setOnClickListener(v -> setSliderMode(true));
        clearDestButton.setOnClickListener(v -> clearDestination());
        predictButton.setOnClickListener(v -> predict());

        routeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sliderLabel.setText(String.format(Locale.US, "%.1f%%", progress / 10.0));
                if (fromUser && predictor != null) {
                    int n = profile.size();
                    setPointBin((int) Math.round(progress / 1000.0 * (n - 1)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        setSelectingDestination(false);
        setSliderMode(false);
        loadModel();
    }

    private void loadModel() {
        background.execute(() -> {
            try {
                ExtraTreesModel model = ExtraTreesModel.load(getAssets().open("et_model.bin"));
                RouteProfile morning = RouteProfile.load(getAssets().open("route_profile_morning.csv"));
                RouteProfile evening = RouteProfile.load(getAssets().open("route_profile_evening.csv"));
                List<ChokePoint> chokes = loadChokes();
                runOnUiThread(() -> {
                    predictor = new EtaPredictor(model, morning, evening, chokes);
                    profile = predictor.profile(route);
                    drawRoute();
                    statusText.setText("Loaded " + profile.size() + " bins. Pick a start point.");
                });
            } catch (IOException e) {
                runOnUiThread(() -> statusText.setText("Could not load model: " + e.getMessage()));
            }
        });
    }

    private List<ChokePoint> loadChokes() throws IOException {
        List<ChokePoint> chokes = new ArrayList<>();
        try (InputStream in = getAssets().open("choke_points.csv");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                chokes.add(new ChokePoint(p[0], Integer.parseInt(p[1]), Double.parseDouble(p[2]),
                        Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]),
                        (int) Double.parseDouble(p[3])));
            }
        }
        return chokes;
    }

    /** Draws the route as coloured segments with a black outline, as the web app does. */
    private void drawRoute() {
        map.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();
        int n = profile.size();
        for (int i = 1; i < n; i++) {
            RouteProfile.Bin a = profile.get(i - 1);
            RouteProfile.Bin b = profile.get(i);
            List<GeoPoint> seg = new ArrayList<>();
            seg.add(new GeoPoint(a.lat, a.lng));
            seg.add(new GeoPoint(b.lat, b.lng));

            Polyline outline = new Polyline();
            outline.setPoints(seg);
            outline.setColor(Color.BLACK);
            outline.setWidth(5f);

            Polyline colour = new Polyline();
            colour.setPoints(seg);
            colour.setColor(congestionColor(b.congestion));
            colour.setWidth(4f);

            routeOverlays.add(outline);
            routeOverlays.add(colour);
        }
        // Route lines go under the markers and tap handler, which sit at the front of the list.
        map.getOverlays().addAll(1, routeOverlays);

        List<GeoPoint> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RouteProfile.Bin b = profile.get(i);
            all.add(new GeoPoint(b.lat, b.lng));
        }
        BoundingBox box = BoundingBox.fromGeoPointsSafe(all);
        map.zoomToBoundingBox(box, false, 60);
        map.invalidate();
    }

    private static int congestionColor(double v) {
        if (v <= 0.33) return COLOR_GREEN;
        if (v <= 0.66) return COLOR_YELLOW;
        return COLOR_RED;
    }

    private void setSelectingDestination(boolean destination) {
        selectingDestination = destination;
        startModeButton.setBackgroundColor(destination ? COLOR_INACTIVE : COLOR_START);
        destModeButton.setBackgroundColor(destination ? COLOR_DEST : COLOR_INACTIVE);
    }

    private void setSliderMode(boolean slider) {
        sliderMode = slider;
        tapMethodButton.setBackgroundColor(slider ? COLOR_INACTIVE : COLOR_START);
        sliderMethodButton.setBackgroundColor(slider ? COLOR_START : COLOR_INACTIVE);
        findViewById(R.id.sliderRow).setVisibility(slider ? android.view.View.VISIBLE : android.view.View.GONE);
        if (slider && predictor != null) {
            statusText.setText("Move the slider to pick a point on the route.");
        }
    }

    /** Places the start or destination on the route bin and draws its marker. */
    private void setPointBin(int bin) {
        if (profile == null) return;
        bin = Math.max(0, Math.min(profile.size() - 1, bin));
        RouteProfile.Bin b = profile.get(bin);
        GeoPoint gp = new GeoPoint(b.lat, b.lng);
        if (selectingDestination) {
            destination = new double[]{b.lat, b.lng};
            destBin = bin;
            destMarker = placeMarker(destMarker, gp, COLOR_DEST, "Destination");
            statusText.setText("Destination set at bin " + bin + ". Press Predict ETA.");
        } else {
            start = new double[]{b.lat, b.lng};
            startBin = bin;
            startMarker = placeMarker(startMarker, gp, COLOR_START, "Start");
            statusText.setText("Start set at bin " + bin + ".");
        }
        if (sliderMode) {
            sliderLabel.setText(String.format(Locale.US, "%.1f%%", 100.0 * bin / Math.max(1, profile.size() - 1)));
        }
        map.invalidate();
    }

    private Marker placeMarker(Marker existing, GeoPoint at, int colour, String title) {
        Marker m = existing != null ? existing : new Marker(map);
        m.setPosition(at);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        m.setIcon(dot(colour));
        m.setTitle(title);
        if (existing == null) map.getOverlays().add(m);
        return m;
    }

    private GradientDrawable dot(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(colour);
        d.setStroke(3, Color.BLACK);
        int px = Math.round(20 * getResources().getDisplayMetrics().density);
        d.setSize(px, px);
        return d;
    }

    private void clearDestination() {
        destination = null;
        destBin = -1;
        if (destMarker != null) {
            map.getOverlays().remove(destMarker);
            destMarker = null;
        }
        statusText.setText("Destination cleared. Trip runs to the route end.");
        map.invalidate();
    }

    private void clearSelection() {
        start = null;
        startBin = -1;
        clearDestination();
        if (startMarker != null) {
            map.getOverlays().remove(startMarker);
            startMarker = null;
        }
        resultText.setText("No prediction yet.");
    }

    private void predict() {
        if (predictor == null) return;
        if (start == null) {
            statusText.setText("Tap the map or move the slider to pick a start point first.");
            return;
        }
        int weekday = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        PredictionResult r = predictor.predict(route, minuteOfDay, weekday, start, destination);

        int arrival = (int) Math.round(minuteOfDay + r.etaMin) % (24 * 60);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "ETA: %.1f min%n", r.etaMin));
        sb.append(String.format(Locale.US, "Arrival: %02d:%02d%n", arrival / 60, arrival % 60));
        sb.append(String.format(Locale.US, "Model to route end: %.1f min%n", r.modelFullMin));
        sb.append(String.format(Locale.US, "Local profile: %.1f min%n", r.localMin));
        sb.append(String.format(Locale.US, "Local weight: %.0f%%%n", r.localWeight * 100));
        sb.append(String.format(Locale.US, "Start bin %d (%.1f%% along route)%n", r.startBin, r.progressRatio * 100));
        if (r.destinationBin >= 0) {
            sb.append(String.format(Locale.US, "Destination bin %d%n", r.destinationBin));
        }
        sb.append("\nChoke points ahead:\n");
        if (r.chokesAhead.isEmpty()) {
            sb.append("None listed for this stretch.");
        } else {
            int i = 1;
            for (ChokePoint c : r.chokesAhead) {
                sb.append(String.format(Locale.US, "%d. Bin %d | congestion %.1f%% | speed %.1f km/h%n",
                        i++, c.binIdx, c.congestion * 100, c.speedKph));
            }
        }
        resultText.setText(sb.toString().trim());
        statusText.setText("Prediction ready.");
    }

    private void checkForUpdate() {
        statusText.setText("Checking GitHub for a new release...");
        background.execute(() -> {
            try {
                Updater.Release release = Updater.fetchLatest();
                String current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                runOnUiThread(() -> {
                    if (release == null) {
                        statusText.setText("The latest release has no APK attached.");
                    } else if (VersionCompare.compare(release.tag, current) > 0) {
                        askToInstall(release);
                    } else {
                        statusText.setText("Up to date (version " + current + ").");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Update check failed: " + e.getMessage()));
            }
        });
    }

    private void askToInstall(Updater.Release release) {
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Version " + release.tag + " is available. Download and install it?")
                .setPositiveButton("Download", (d, w) -> downloadAndInstall(release))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstall(Updater.Release release) {
        File dir = new File(getCacheDir(), "updates");
        dir.mkdirs();
        File apk = new File(dir, release.apkName);
        statusText.setText("Downloading " + release.tag + "...");
        background.execute(() -> {
            try {
                Updater.download(release.apkUrl, apk, percent ->
                        runOnUiThread(() -> statusText.setText("Downloading " + release.tag + "... " + percent + "%")));
                runOnUiThread(() -> installApk(apk));
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Download failed: " + e.getMessage()));
            }
        });
    }

    private void installApk(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            statusText.setText("Allow installs from this app, then press Check for update again.");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        try {
            Installer.install(this, apk);
            statusText.setText("Installing... confirm if Android asks.");
        } catch (IOException | RuntimeException e) {
            statusText.setText("Install failed: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        background.shutdownNow();
        super.onDestroy();
    }
}
