package com.example.shuttleeta.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Route bins from route_profile_*.csv: one point per 120 m bin along the route. */
public final class RouteProfile {

    public static final class Bin {
        public final double lat;
        public final double lng;
        public final double speedKph;
        public final int support;
        public final double congestion;

        Bin(double lat, double lng, double speedKph, int support, double congestion) {
            this.lat = lat;
            this.lng = lng;
            this.speedKph = speedKph;
            this.support = support;
            this.congestion = congestion;
        }
    }

    private final List<Bin> bins;

    private RouteProfile(List<Bin> bins) {
        this.bins = bins;
    }

    public static RouteProfile load(InputStream raw) throws IOException {
        List<Bin> bins = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String header = r.readLine();
            if (header == null) throw new IOException("Empty route profile");
            String[] cols = header.split(",");
            int iSpeed = indexOf(cols, "avg_speed_kph");
            int iSupport = indexOf(cols, "session_support");
            int iCong = indexOf(cols, "congestion");
            int iLat = indexOf(cols, "lat");
            int iLng = indexOf(cols, "lng");
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                bins.add(new Bin(
                        Double.parseDouble(p[iLat]),
                        Double.parseDouble(p[iLng]),
                        Double.parseDouble(p[iSpeed]),
                        (int) Double.parseDouble(p[iSupport]),
                        Double.parseDouble(p[iCong])));
            }
        }
        return new RouteProfile(bins);
    }

    private static int indexOf(String[] cols, String name) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].trim().equals(name)) return i;
        }
        throw new IOException("Missing column " + name);
    }

    public int size() {
        return bins.size();
    }

    public Bin get(int i) {
        return bins.get(i);
    }

    /** Index of the bin nearest to the given point (same rule as predict_eta.py). */
    public int nearestBin(double lat, double lng) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < bins.size(); i++) {
            Bin b = bins.get(i);
            double d = haversineM(lat, lng, b.lat, b.lng);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Remaining time in minutes from start bin to end bin, slowed by congestion. */
    public double segmentLocalEtaMin(int startBin, int endBin) {
        int n = bins.size();
        int s = clamp(startBin, 0, n - 1);
        int e = clamp(endBin, 0, n - 1);
        if (e <= s) return 0.0;
        double total = 0.0;
        for (int i = s; i < e; i++) {
            Bin a = bins.get(i);
            Bin b = bins.get(i + 1);
            double dM = haversineM(a.lat, a.lng, b.lat, b.lng);
            double speed = Math.max(3.0, b.speedKph);
            double cong = Math.min(Math.max(b.congestion, 0.0), 1.0);
            double eff = Math.max(2.0, speed / (1.0 + cong * cong * 2.4));
            total += (dM / 1000.0) / eff * 60.0;
        }
        return Math.max(0.0, total);
    }

    static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
