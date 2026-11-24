package com.example.shuttleeta.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Extra Trees regressor loaded from et_model.bin (see export_android_assets.py).
 * Prediction is the average of the leaf values reached in each tree, as in scikit-learn.
 */
public final class ExtraTreesModel {
    private static final int MAGIC = 0x45544D31;

    private final short[][] feature;   // -1 = leaf
    private final double[][] threshold;
    private final int[][] left;        // -1 = leaf
    private final int[][] right;
    private final double[][] value;
    private final int nFeatures;

    private ExtraTreesModel(short[][] feature, double[][] threshold, int[][] left, int[][] right,
                            double[][] value, int nFeatures) {
        this.feature = feature;
        this.threshold = threshold;
        this.left = left;
        this.right = right;
        this.value = value;
        this.nFeatures = nFeatures;
    }

    public static ExtraTreesModel load(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not an ETA model file");
            }
            int nTrees = in.readInt();
            int nFeatures = in.readInt();
            short[][] feature = new short[nTrees][];
            double[][] threshold = new double[nTrees][];
            int[][] left = new int[nTrees][];
            int[][] right = new int[nTrees][];
            double[][] value = new double[nTrees][];
            for (int t = 0; t < nTrees; t++) {
                int n = in.readInt();
                feature[t] = new short[n];
                threshold[t] = new double[n];
                left[t] = new int[n];
                right[t] = new int[n];
                value[t] = new double[n];
                for (int i = 0; i < n; i++) feature[t][i] = in.readShort();
                for (int i = 0; i < n; i++) threshold[t][i] = in.readDouble();
                for (int i = 0; i < n; i++) left[t][i] = in.readInt();
                for (int i = 0; i < n; i++) right[t][i] = in.readInt();
                for (int i = 0; i < n; i++) value[t][i] = in.readDouble();
            }
            return new ExtraTreesModel(feature, threshold, left, right, value, nFeatures);
        }
    }

    public int nFeatures() {
        return nFeatures;
    }

    public double predict(float[] x) {
        double total = 0.0;
        for (int t = 0; t < feature.length; t++) {
            int node = 0;
            while (left[t][node] != -1) {
                // scikit-learn compares the float32 input against the double threshold
                if (x[feature[t][node]] <= threshold[t][node]) {
                    node = left[t][node];
                } else {
                    node = right[t][node];
                }
            }
            total += value[t][node];
        }
        return total / feature.length;
    }
}
