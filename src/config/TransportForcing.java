package config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable 30-day transition-matrix forcing on a fixed union of directed edges.
 * Input format: one-based source, one-based destination, probability (whitespace separated).
 * Monthly filenames contain %02d, expanded to 01,...,12.
 *
 * Each source's edges are stored contiguously; the diagonal edge is always first.
 * The off-diagonal probabilities may be multiplied by a dispersal scaler, with
 * the residual assigned to the diagonal, matching the existing DLBM behaviour.
 */
public final class TransportForcing {
    private static final double SUM_TOLERANCE = 1e-8;

    private final int[] edgeStart;   // nBoxes + 1
    private final int[] edgeDest;    // zero-based destination for each edge
    private final double[][] probabilities; // [month or 0][edge], before scaling

    private TransportForcing(int[] edgeStart, int[] edgeDest, double[][] probabilities) {
        this.edgeStart = edgeStart;
        this.edgeDest = edgeDest;
        this.probabilities = probabilities;
    }

    /** Load one matrix or twelve matrices, validating every source distribution. */
    public static TransportForcing load(String filename, int numBoxes) throws IOException {
        if (filename == null || filename.isBlank())
            throw new IllegalArgumentException("TM_FILE must be specified");
        if (numBoxes <= 0)
            throw new IllegalArgumentException("numBoxes must be positive");

        boolean monthly = filename.contains("%02d");
        int nFields = monthly ? 12 : 1;
        TreeMap<Long, double[]> edges = new TreeMap<>();
        double[][] sums = new double[nFields][numBoxes];
        boolean[][] diagonals = new boolean[nFields][numBoxes];

        for (int m = 0; m < nFields; m++) {
            Path path = Path.of(monthly ? String.format(Locale.ROOT, filename, m + 1) : filename);
            HashSet<Long> seen = new HashSet<>();
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank()) continue;
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length != 3)
                        throw new IOException("Expected source destination probability at " + path + ":" + lineNumber);
                    final int src, dst;
                    final double p;
                    try {
                        src = Integer.parseInt(fields[0]) - 1;
                        dst = Integer.parseInt(fields[1]) - 1;
                        p = Double.parseDouble(fields[2]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid transport entry at " + path + ":" + lineNumber, e);
                    }
                    if (src < 0 || src >= numBoxes || dst < 0 || dst >= numBoxes)
                        throw new IOException("Out-of-range province at " + path + ":" + lineNumber);
                    if (!Double.isFinite(p) || p < 0.0 || p > 1.0 + SUM_TOLERANCE)
                        throw new IOException("Invalid probability at " + path + ":" + lineNumber);

                    long key = (long)src * numBoxes + dst;
                    if (!seen.add(key))
                        throw new IOException("Duplicate transport edge at " + path + ":" + lineNumber);
                    double[] monthlyValues = edges.computeIfAbsent(key, k -> new double[nFields]);
                    monthlyValues[m] = p;
                    sums[m][src] += p;
                    if (src == dst) diagonals[m][src] = true;
                }
            }
            for (int src = 0; src < numBoxes; src++) {
                if (!diagonals[m][src])
                    throw new IOException("Missing diagonal entry for province " + (src + 1) + " in " + path);
                if (Math.abs(sums[m][src] - 1.0) > SUM_TOLERANCE)
                    throw new IOException("Transition probabilities for province " + (src + 1)
                            + " in " + path + " sum to " + sums[m][src]);
            }
        }

        // Ensure a diagonal edge exists for every province and every source has
        // a stable edge ordering. Sort off-diagonal destinations numerically.
        int[] edgeStart = new int[numBoxes + 1];
        for (long key : edges.keySet()) edgeStart[(int)(key / numBoxes) + 1]++;
        for (int src = 0; src < numBoxes; src++) edgeStart[src + 1] += edgeStart[src];

        int edgeCount = edges.size();
        int[] edgeDest = new int[edgeCount];
        double[][] probabilities = new double[nFields][edgeCount];
        int[] cursor = Arrays.copyOf(edgeStart, numBoxes);
        // First pass: populate diagonal first in every province block.
        for (int src = 0; src < numBoxes; src++) {
            double[] values = edges.get((long)src * numBoxes + src);
            int e = cursor[src]++;
            edgeDest[e] = src;
            for (int m = 0; m < nFields; m++) probabilities[m][e] = values[m];
        }
        for (Map.Entry<Long, double[]> entry : edges.entrySet()) {
            int src = (int)(entry.getKey() / numBoxes);
            int dst = (int)(entry.getKey() % numBoxes);
            if (src == dst) continue;
            int e = cursor[src]++;
            edgeDest[e] = dst;
            for (int m = 0; m < nFields; m++) probabilities[m][e] = entry.getValue()[m];
        }
        return new TransportForcing(edgeStart, edgeDest, probabilities);
    }

    public boolean isSeasonal() { return probabilities.length == 12; }
    public int numBoxes() { return edgeStart.length - 1; }
    public int numEdges() { return edgeDest.length; }
    public int edgeStart(int source) { return edgeStart[source]; }
    public int edgeEnd(int source) { return edgeStart[source + 1]; }
    public int edgeDestination(int edge) { return edgeDest[edge]; }

    /**
     * Fill a caller-owned array with interpolated transition probabilities.
     * The diagonal absorbs the non-moving fraction after DISP_SCALER is applied.
     * Call this once per dispersal event, before parallel transport workers start.
     */
    public void fillProbabilities(double midpointHour, double dispersalScaler, double[] result) {
        if (result.length != numEdges())
            throw new IllegalArgumentException("Probability array length must be " + numEdges());
        if (!Double.isFinite(dispersalScaler) || dispersalScaler < 0.0)
            throw new IllegalArgumentException("Invalid DISP_SCALER");

        SeasonalPhase phase = isSeasonal() ? SeasonalPhase.atHour(midpointHour) : null;
        for (int src = 0; src < numBoxes(); src++) {
            int first = edgeStart[src]; // diagonal
            double moving = 0.0;
            for (int e = first + 1; e < edgeStart[src + 1]; e++) {
                double p = phase == null ? probabilities[0][e] :
                        phase.interpolate(probabilities[phase.month0][e], probabilities[phase.month1][e]);
                result[e] = p * dispersalScaler;
                moving += result[e];
            }
            if (moving > 1.0 + 1e-10)
                throw new IllegalArgumentException("DISP_SCALER yields movement probability > 1 for province " + (src + 1));
            result[first] = Math.max(0.0, 1.0 - moving);
        }
    }
}
