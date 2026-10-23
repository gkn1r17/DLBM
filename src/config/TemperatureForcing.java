package config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Immutable temperature input: one annual field or twelve cyclic monthly fields. */
public final class TemperatureForcing {
    private final double[][] fields; // [month or 0][box]

    private TemperatureForcing(double[][] fields) {
        this.fields = fields;
    }

    /**
     * Filename containing %02d identifies monthly files numbered 01 to 12.
     * Every file must have exactly numBoxes lines, each with one temperature.
     * Returns null for non-selective simulations with no temperature file.
     */
    public static TemperatureForcing load(String filename, int numBoxes) throws IOException {
        if (filename == null) return null;
        if (numBoxes <= 0) throw new IllegalArgumentException("numBoxes must be positive");

        boolean monthly = filename.contains("%02d");
        double[][] fields = new double[monthly ? 12 : 1][];
        for (int m = 0; m < fields.length; m++) {
            String path = monthly ? String.format(Locale.ROOT, filename, m + 1) : filename;
            fields[m] = readSingleColumn(Path.of(path), numBoxes);
        }
        return new TemperatureForcing(fields);
    }

    public boolean isSeasonal() {
        return fields.length == 12;
    }

    /** Interpolated temperature for a zero-based province index at simulation hour. */
    public double at(int box, double hour) {
        if (!isSeasonal()) return fields[0][box];
        SeasonalPhase phase = SeasonalPhase.atHour(hour);
        return phase.interpolate(fields[phase.month0][box], fields[phase.month1][box]);
    }

    private static double[] readSingleColumn(Path path, int numBoxes) throws IOException {
        double[] temperatures = new double[numBoxes];
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            for (int box = 0; box < numBoxes; box++) {
                String line = reader.readLine();
                if (line == null) throw new IOException("Too few temperatures in " + path
                        + ": expected " + numBoxes + ", got " + box);
                String value = line.trim();
                if (value.contains(",") || value.isEmpty())
                    throw new IOException("Expected one temperature per line in " + path
                            + " at line " + (box + 1));
                try {
                    temperatures[box] = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid temperature in " + path + " at line " + (box + 1), e);
                }
                if (!Double.isFinite(temperatures[box]))
                    throw new IOException("Non-finite temperature in " + path + " at line " + (box + 1));
            }
            if (reader.readLine() != null)
                throw new IOException("Too many temperatures in " + path
                        + ": expected " + numBoxes);
        }
        return temperatures;
    }
}
