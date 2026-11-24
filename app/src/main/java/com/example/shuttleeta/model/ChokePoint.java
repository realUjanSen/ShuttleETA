package com.example.shuttleeta.model;

/** One row of choke_points.csv. */
public final class ChokePoint {
    public final String routeType;
    public final int binIdx;
    public final double speedKph;
    public final double congestion;
    public final double lat;
    public final double lng;
    public final int support;

    public ChokePoint(String routeType, int binIdx, double speedKph, double congestion,
                      double lat, double lng, int support) {
        this.routeType = routeType;
        this.binIdx = binIdx;
        this.speedKph = speedKph;
        this.congestion = congestion;
        this.lat = lat;
        this.lng = lng;
        this.support = support;
    }
}
