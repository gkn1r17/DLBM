package util;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.LongAdder;

public final class StochasticProfiler {

    private StochasticProfiler() {}

    public static final boolean ENABLED =
            Boolean.getBoolean("dlbm.profile");

    public static final LongAdder ecology = new LongAdder();
	public static final LongAdder growthCalls = new LongAdder();
	public static final LongAdder dispersalCalls = new LongAdder();
	public static final LongAdder dormancyCalls = new LongAdder();
	
	public static final LongAdder holding = new LongAdder();
	public static final LongAdder immigration = new LongAdder();
    public static final LongAdder forcing = new LongAdder();
    public static final LongAdder output = new LongAdder();

    private static long startTime;
	
	private static final ThreadMXBean threadBean =
			ManagementFactory.getThreadMXBean();
	
	/** Actual CPU time consumed by the calling thread, in nanoseconds. */
	public static long cpuTime() {
		return threadBean.getCurrentThreadCpuTime();
	}

    public static void start() {
		if (ENABLED) {
			if (!threadBean.isCurrentThreadCpuTimeSupported())
				throw new IllegalStateException(
						"Thread CPU time profiling is not supported by this JVM");
		
			if (!threadBean.isThreadCpuTimeEnabled())
				threadBean.setThreadCpuTimeEnabled(true);
		
			startTime = System.nanoTime();
		}
    }

    public static void report() {

        if (!ENABLED)
            return;

        double wall = seconds(System.nanoTime() - startTime);

        System.out.println();
        System.out.println("STOCHASTIC MODEL PROFILE");
        System.out.println("-------------------------------------------");
		System.out.println("Accumulated worker CPU time (seconds)");
		print("Grid-box updates", ecology.sum());
		print("Immigration", immigration.sum());
		
		System.out.println();
		System.out.println("Lineage operation counts");
		System.out.printf("Growth/mortality calls: %,d%n", growthCalls.sum());
		System.out.printf("Dispersal calls:        %,d%n", dispersalCalls.sum());
		System.out.printf("Dormancy calls:         %,d%n", dormancyCalls.sum());
		
		System.out.println();
		System.out.println("Serial phase wall times (seconds)");
		print("Forcing", forcing.sum());
		print("Holding resets", holding.sum());
		print("Output", output.sum());

        System.out.println("-------------------------------------------");
        System.out.printf("Total elapsed wall time: %.3f s%n", wall);
    }

    private static double seconds(long nanos) {
        return nanos / 1e9;
    }

    private static void print(String label, long nanos) {
        System.out.printf("%-24s %10.3f%n", label, seconds(nanos));
    }
}