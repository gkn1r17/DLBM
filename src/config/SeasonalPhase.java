package config;

/** Cyclic linear interpolation between twelve monthly midpoint forcing fields.
 *  A model year contains 360 days, with observations centred at days 15,45,...,345.
 */
public final class SeasonalPhase {
    public final int month0; // zero-based earlier month
    public final int month1; // zero-based later month
    public final double alpha; // weight of later month, in [0,1]

    private SeasonalPhase(int month0, int month1, double alpha) {
        this.month0 = month0;
        this.month1 = month1;
        this.alpha = alpha;
    }

    /** @param hour simulation time in hours, possibly fractional. */
    public static SeasonalPhase atHour(double hour) {
        double dayOfYear = ((hour / 24.0) % 360.0 + 360.0) % 360.0;
        double monthPosition = (dayOfYear - 15.0) / 30.0;
        int lower = (int)Math.floor(monthPosition);
        double fraction = monthPosition - lower;
        int m0 = Math.floorMod(lower, 12);
        return new SeasonalPhase(m0, (m0 + 1) % 12, fraction);
    }

    public double interpolate(double earlier, double later) {
        return (1.0 - alpha) * earlier + alpha * later;
    }

    /** For fixed 30-day displacement operators, evaluate forcing at interval midpoint. */
    public static SeasonalPhase transportInterval(long startHour, double intervalHours) {
        return atHour(startHour + intervalHours / 2.0);
    }
}
