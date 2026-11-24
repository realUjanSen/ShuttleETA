package com.example.shuttleeta;

/** Compares version strings such as "v1.0.3" and "1.0.2" by their numeric parts. */
public final class VersionCompare {
    private VersionCompare() {
    }

    /** Returns >0 if a is newer than b, 0 if equal, <0 if older. Non-numeric parts count as 0. */
    public static int compare(String a, String b) {
        int[] pa = parts(a);
        int[] pb = parts(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parts(String v) {
        String s = v == null ? "" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] raw = s.split("\\.");
        int[] out = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                out[i] = Integer.parseInt(raw[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
