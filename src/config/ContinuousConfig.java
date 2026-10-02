package config;

import org.ini4j.Profile.Section;

/** Configuration used only by the deterministic continuous implementation. */
public final class ContinuousConfig {
    /** Initialisation experiment: one_per_location or local_phenotypes. */
    public final String initMode;
    /** Comma-separated Topt offsets (deg C) for local_phenotypes. */
    public final String phenotypeOffsets;
    /** Snapshot format: binary or csv. */
    public final String outputMode;
    /** Minimum abundance written in CSV mode. Binary output is always lossless. */
    public final double outputMinAbundance;
    /** Number of shared-memory worker threads used by continuous mode. */
    public final int numWorkers;

    public ContinuousConfig(IniFileReader iniFR) throws Exception {
        initMode = getWithDefault(iniFR, "CONT_INIT_MODE", "one_per_location")
                .trim().toLowerCase();
        phenotypeOffsets = getWithDefault(iniFR, "CONT_PHENOTYPE_OFFSETS", "0.0").trim();
        outputMode = getWithDefault(iniFR, "CONT_OUTPUT_MODE", "binary")
                .trim().toLowerCase();
        outputMinAbundance = Double.parseDouble(
                getWithDefault(iniFR, "CONT_OUTPUT_MIN_ABUNDANCE", "0.0"));
        numWorkers = Integer.parseInt(getWithDefault(iniFR, "NUM_WORKERS", "16"));
        if (numWorkers <= 0)
            throw new IllegalArgumentException("NUM_WORKERS must be > 0");
    }

    /**
     * Read a continuous-only setting without requiring a [Continuous] section.
     * Command-line values take precedence.  Consumed values are removed from the
     * Ini object so Config's existing unknown-setting check continues to work.
     */
    private static String getWithDefault(IniFileReader iniFR, String name, String defaultValue) {
        String value = null;

        Section continuous = iniFR.settingsIni.get("Continuous");
        if (continuous != null && continuous.containsKey(name))
            value = continuous.remove(name);

        Section commandLine = iniFR.settingsIni.get("commandLine");
        if (commandLine != null && commandLine.containsKey(name))
            value = commandLine.remove(name);

        if (value == null)
            value = defaultValue;

        iniFR.settingsIniOut.append("\n\n\n[Continuous]\n" + name + "=" + value);
        return value;
    }
}
