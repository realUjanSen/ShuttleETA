package com.example.shuttleeta.model;

import java.util.List;

/** Output of one prediction, mirroring the fields the web app shows. */
public final class PredictionResult {
    public final double etaMin;            // final ETA, minutes
    public final double modelFullMin;      // model estimate to route end
    public final double localMin;          // local route-profile estimate
    public final double localWeight;       // blend weight of the local estimate
    public final double progressRatio;     // start position on the route, 0..1
    public final int startBin;
    public final int destinationBin;       // -1 if no destination
    public final List<ChokePoint> chokesAhead;

    PredictionResult(double etaMin, double modelFullMin, double localMin, double localWeight,
                     double progressRatio, int startBin, int destinationBin, List<ChokePoint> chokesAhead) {
        this.etaMin = etaMin;
        this.modelFullMin = modelFullMin;
        this.localMin = localMin;
        this.localWeight = localWeight;
        this.progressRatio = progressRatio;
        this.startBin = startBin;
        this.destinationBin = destinationBin;
        this.chokesAhead = chokesAhead;
    }
}
