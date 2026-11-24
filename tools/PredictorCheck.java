import com.example.shuttleeta.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Desktop check of the Java predictor against the Python reference, using the same assets. */
public class PredictorCheck {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        ExtraTreesModel model = ExtraTreesModel.load(new FileInputStream(new File(dir, "et_model.bin")));
        RouteProfile morning = RouteProfile.load(new FileInputStream(new File(dir, "route_profile_morning.csv")));
        RouteProfile evening = RouteProfile.load(new FileInputStream(new File(dir, "route_profile_evening.csv")));
        List<ChokePoint> chokes = new ArrayList<>();
        List<String> lines = Files.readAllLines(new File(dir, "choke_points.csv").toPath());
        for (String line : lines.subList(1, lines.size())) {
            String[] p = line.split(",");
            chokes.add(new ChokePoint(p[0], Integer.parseInt(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[4]),
                    Double.parseDouble(p[5]), Double.parseDouble(p[6]), (int) Double.parseDouble(p[3])));
        }
        EtaPredictor predictor = new EtaPredictor(model, morning, evening, chokes);

        // Case 1: fraction along route (same as --progress-ratio). Case 2: tap points.
        RouteProfile m = morning;
        PredictionResult r1 = predictor.predict("morning", 8 * 60, 0, pointAt(m, 0.5), null);
        System.out.println("case1 " + r1.etaMin + " " + r1.modelFullMin + " " + r1.localMin + " " + r1.localWeight);
        PredictionResult r2 = predictor.predict("morning", 17 * 60 + 30, 3, pointAt(m, 0.2), null);
        System.out.println("case2 " + r2.etaMin + " " + r2.modelFullMin + " " + r2.localMin + " " + r2.localWeight);
        PredictionResult r3 = predictor.predict("evening", 18 * 60 + 5, 4, pointAt(evening, 0.7), null);
        System.out.println("case3 " + r3.etaMin + " " + r3.modelFullMin + " " + r3.localMin + " " + r3.localWeight);
        PredictionResult r4 = predictor.predict("morning", 8 * 60 + 30, 2, pointOf(m, 30, 0.00002), pointOf(m, 150, 0));
        System.out.println("case4 " + r4.etaMin + " " + r4.localMin + " " + r4.localWeight + " bins " + r4.startBin + " " + r4.destinationBin);
    }

    static double[] pointAt(RouteProfile p, double ratio) {
        RouteProfile.Bin b = p.get((int) (ratio * (p.size() - 1)));
        return new double[]{b.lat, b.lng};
    }

    static double[] pointOf(RouteProfile p, int bin, double dlat) {
        RouteProfile.Bin b = p.get(bin);
        return new double[]{b.lat + dlat, b.lng};
    }
}
