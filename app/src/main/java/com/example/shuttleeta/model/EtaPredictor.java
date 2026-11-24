package com.example.shuttleeta.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Same steps as predict_eta.py (without the session blend, which the web app does not use):
 * snap points to route bins, run the Extra Trees model, blend with the local route-profile
 * estimate, and list the choke points ahead.
 */
public final class EtaPredictor {

    private final ExtraTreesModel model;
    private final RouteProfile morning;
    private final RouteProfile evening;
    private final List<ChokePoint> chokes;

    public EtaPredictor(ExtraTreesModel model, RouteProfile morning, RouteProfile evening, List<ChokePoint> chokes) {
        this.model = model;
        this.morning = morning;
        this.evening = evening;
        this.chokes = chokes;
    }

    public RouteProfile profile(String route) {
        return "morning".equals(route) ? morning : evening;
    }

    /**
     * @param route        "morning" or "evening"
     * @param minuteOfDay  start time, minutes after midnight
     * @param weekday      0 = Monday .. 6 = Sunday
     * @param start        start point, or null to start at the route beginning
     * @param destination  destination point, or null for the route end
     */
    public PredictionResult predict(String route, int minuteOfDay, int weekday,
                                    double[] start, double[] destination) {
        RouteProfile profile = profile(route);
        int n = profile.size();
        int routeEnd = n - 1;

        double progress = 0.0;
        if (start != null) {
            progress = clamp01((double) profile.nearestBin(start[0], start[1]) / Math.max(1, routeEnd));
        }
        int binIdx = clampInt((int) (progress * Math.max(1, routeEnd)), 0, routeEnd);
        RouteProfile.Bin row = profile.get(binIdx);

        float[] x = new float[]{
                "morning".equals(route) ? 0f : 1f,      // route_type
                (float) minuteOfDay,                    // start_minute
                (float) weekday,                        // weekday
                (float) progress,                       // progress_ratio
                0f,                                     // offroute_m
                (float) row.speedKph,                   // speed_kph
                (float) row.speedKph,                   // global_bin_speed_kph
                (float) row.support                     // bin_session_support
        };
        double full = Math.max(0.0, model.predict(x));

        double remaining = Math.max(0.0, 1.0 - progress);
        double modelTrip = full;
        double localTrip = profile.segmentLocalEtaMin(binIdx, routeEnd);
        double wLocal = 0.45;
        int destBin = -1;

        if (destination != null) {
            destBin = profile.nearestBin(destination[0], destination[1]);
            double destRatio = clamp01((double) destBin / Math.max(1, routeEnd));
            double delta = destRatio - progress;
            double remDest = Math.max(0.0, delta);
            int destTarget = clampInt((int) Math.rint(destRatio * routeEnd), 0, routeEnd);
            modelTrip = remaining > 1e-9 ? full * (remDest / remaining) : 0.0;
            localTrip = delta >= 0 ? profile.segmentLocalEtaMin(binIdx, destTarget) : 0.0;
            double span = Math.min(Math.max(Math.abs(delta), 0.0), 1.0);
            wLocal = Math.min(Math.max(0.45 + (0.25 - span) * 1.2, 0.45), 0.8);
        }

        double eta = Math.max(0.0, (1.0 - wLocal) * modelTrip + wLocal * localTrip);

        List<ChokePoint> ahead = new ArrayList<>();
        for (ChokePoint c : chokes) {
            if (c.routeType.equals(route) && c.binIdx >= binIdx) ahead.add(c);
        }
        Collections.sort(ahead, new Comparator<ChokePoint>() {
            @Override
            public int compare(ChokePoint a, ChokePoint b) {
                return Double.compare(b.congestion, a.congestion);
            }
        });
        if (ahead.size() > 5) ahead = new ArrayList<>(ahead.subList(0, 5));

        return new PredictionResult(eta, full, localTrip, wLocal, progress, binIdx, destBin, ahead);
    }

    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
