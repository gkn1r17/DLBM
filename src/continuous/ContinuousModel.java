package continuous;

import config.Config;
import lineages.SelLineage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Phaser;

/**
 * Deterministic continuous dLBM.
 *
 * State is a dense location x fixed-population matrix. Physical transport is
 * retained as a sparse source->destination edge list. No lineages are created
 * or destroyed: population identity is fixed at initialisation.
 */
public final class ContinuousModel {
    public static final class PopulationMeta {
        public final long id;
        public final int originLocation;
        public final int phenotype;
        public final float tOpt;

        PopulationMeta(long id, int originLocation, int phenotype, float tOpt) {
            this.id = id;
            this.originLocation = originLocation;
            this.phenotype = phenotype;
            this.tOpt = tOpt;
        }
    }

    private final Config settings;
    private final int nLoc;
    private final int nPop;
    private final int phenotypesPerLocation;
    private final double[][] temperatures;
    private final int tempIntervals;
    private final PopulationMeta[] populations;

    // Dense, location-major: index = location*nPop + population.
    private double[] active;
    private double[] dormant;
    private double[] activeWork;
    private double[] dormantWork;

    // Active abundance at each location. Dormant cells do not contribute to ecology.
    private final double[] locationTotal;

    // Fixed shared-memory population decomposition. Each worker owns one
    // contiguous population block for the lifetime of the model.
    private final int workerCount;
    private final Worker[] workers;
    private final Phaser workerPhaser;
    private final double[][] workerLocationTotals;
    private volatile WorkerOperation workerOperation = WorkerOperation.IDLE;
    private volatile double[] workerSource;
    private volatile double[] workerDestination;
    private volatile RuntimeException workerFailure;

    // Sparse transport, grouped by source.
    private final int[] edgeStart;
    private final int[] edgeDest;
    private final double[] edgeProb;

    private int currentTempIndex = -1;
    private double[] thermalGrowth;

    // Coarse profiling only. Timers surround whole model phases, never hot loops.
    private long profileEcologyNanos;
    private long profileTransportNanos;
    private long profileTotalNanos;

    private enum WorkerOperation {
        IDLE, ECOLOGY, REFRESH_TOTALS, TRANSPORT, STOP
    }


    public ContinuousModel(Config settings) throws Exception {
        this.settings = settings;
        this.nLoc = settings.numBoxes;
        this.temperatures = loadTemperatures(settings.sci.tempFile, nLoc);
        this.tempIntervals = temperatures == null ? 1 : temperatures[0].length;

        double[] offsets = parseOffsets(settings.continuous.phenotypeOffsets);
        switch (settings.continuous.initMode) {
            case "one_per_location":
                if (offsets.length != 1 || offsets[0] != 0.0)
                    throw new IllegalArgumentException(
                            "CONT_INIT_MODE=one_per_location requires CONT_PHENOTYPE_OFFSETS=0.0");
                phenotypesPerLocation = 1;
                break;
            case "local_phenotypes":
                if (offsets.length < 1)
                    throw new IllegalArgumentException("CONT_PHENOTYPE_OFFSETS must contain at least one value");
                phenotypesPerLocation = offsets.length;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown CONT_INIT_MODE: " + settings.continuous.initMode
                        + " (use one_per_location or local_phenotypes)");
        }

        this.nPop = Math.multiplyExact(nLoc, phenotypesPerLocation);
        long nState = (long)nLoc * nPop;
        if (nState > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Continuous state exceeds Java array index limit: " + nState);

        active = new double[(int)nState];
        activeWork = new double[(int)nState];
        if (settings.sci.dormantFrac > 0.0) {
            dormant = new double[(int)nState];
            dormantWork = new double[(int)nState];
        }

        locationTotal = new double[nLoc];
        populations = initialisePopulations(offsets);

        workerCount = Math.min(settings.continuous.numWorkers, nPop);
        workerLocationTotals = new double[workerCount][nLoc];
        workerPhaser = new Phaser(workerCount + 1);
        workers = new Worker[workerCount];
        for (int w = 0; w < workerCount; w++) {
            int popStart = (int)((long)w * nPop / workerCount);
            int popEnd = (int)((long)(w + 1) * nPop / workerCount);
            workers[w] = new Worker(w, popStart, popEnd);
            Thread thread = new Thread(workers[w], "continuous-worker-" + w);
            thread.setDaemon(true);
            thread.start();
        }
        refreshLocationTotals();

        Transport transport = loadTransport(settings.sci.tmFile, nLoc, settings.sci.dispScaler);
        edgeStart = transport.edgeStart;
        edgeDest = transport.edgeDest;
        edgeProb = transport.edgeProb;

        if (settings.isSelective) {
            thermalGrowth = new double[(int)nState];
            updateThermalGrowth(0);
        }
    }

    private PopulationMeta[] initialisePopulations(double[] offsets) {
        PopulationMeta[] meta = new PopulationMeta[nPop];
        double abundancePerPhenotype = settings.initialP / phenotypesPerLocation;

        for (int loc = 0; loc < nLoc; loc++) {
            for (int ph = 0; ph < phenotypesPerLocation; ph++) {
                int pop = loc * phenotypesPerLocation + ph;
                long id = (long)loc * settings.mutantOffset + ph + 1L;
                float tOpt = Float.NaN;
                if (settings.isSelective)
                    tOpt = (float)(temperatures[loc][0] + offsets[ph]);
                meta[pop] = new PopulationMeta(id, loc, ph, tOpt);
                active[index(loc, pop)] = abundancePerPhenotype;
            }
        }
        return meta;
    }

    /**Advance one dispersal interval: ecology substeps, transport, then dormancy switching.*/
    public void step(long hour) {
        long stepStarted = System.nanoTime();

        int tempIndex = temperatureIndex(hour);
        if (settings.isSelective && tempIndex != currentTempIndex)
            updateThermalGrowth(tempIndex);

        int growthSteps = (int)Math.round(settings.growthPerDisp);
        long phaseStarted = System.nanoTime();
        for (int i = 0; i < growthSteps; i++)
            ecologyStep();
        profileEcologyNanos += System.nanoTime() - phaseStarted;

        phaseStarted = System.nanoTime();
        transport(active, activeWork);
        profileTransportNanos += System.nanoTime() - phaseStarted;

        double[] swap = active;
        active = activeWork;
        activeWork = swap;

        // Transport changes local active abundance. Refresh once per dispersal
        // interval, then carry the totals through the ecology substeps.
        refreshLocationTotals();

        if (dormant != null) {
            phaseStarted = System.nanoTime();
            transport(dormant, dormantWork);
            profileTransportNanos += System.nanoTime() - phaseStarted;

            swap = dormant;
            dormant = dormantWork;
            dormantWork = swap;

            switchDormancy();
        }

        profileTotalNanos += System.nanoTime() - stepStarted;
    }

    private void ecologyStep() {
        runWorkers(WorkerOperation.ECOLOGY, null, null);
        reduceWorkerLocationTotals();
    }

    /** Refresh active abundance totals after transport. Dormant cells are excluded. */
    private void refreshLocationTotals() {
        runWorkers(WorkerOperation.REFRESH_TOTALS, null, null);
        reduceWorkerLocationTotals();
    }

    private void transport(double[] source, double[] destination) {
        Arrays.fill(destination, 0.0);
        runWorkers(WorkerOperation.TRANSPORT, source, destination);
    }

    private void runWorkers(WorkerOperation operation, double[] source, double[] destination) {
        workerFailure = null;
        workerSource = source;
        workerDestination = destination;
        workerOperation = operation;

        // First barrier releases the persistent workers; second waits for all
        // fixed population blocks to finish the phase.
        workerPhaser.arriveAndAwaitAdvance();
        workerPhaser.arriveAndAwaitAdvance();

        RuntimeException failure = workerFailure;
        if (failure != null)
            throw failure;
    }

    private void reduceWorkerLocationTotals() {
        for (int loc = 0; loc < nLoc; loc++) {
            double total = 0.0;
            for (int w = 0; w < workerCount; w++)
                total += workerLocationTotals[w][loc];
            locationTotal[loc] = total;
        }
    }

    private final class Worker implements Runnable {
        private final int workerIndex;
        private final int popStart;
        private final int popEnd;

        Worker(int workerIndex, int popStart, int popEnd) {
            this.workerIndex = workerIndex;
            this.popStart = popStart;
            this.popEnd = popEnd;
        }

        @Override
        public void run() {
            while (true) {
                workerPhaser.arriveAndAwaitAdvance();
                WorkerOperation operation = workerOperation;

                if (operation == WorkerOperation.STOP) {
                    workerPhaser.arriveAndDeregister();
                    return;
                }

                try {
                    switch (operation) {
                        case ECOLOGY:
                            ecologyBlock();
                            break;
                        case REFRESH_TOTALS:
                            refreshTotalsBlock();
                            break;
                        case TRANSPORT:
                            transportBlock(workerSource, workerDestination);
                            break;
                        default:
                            break;
                    }
                } catch (RuntimeException ex) {
                    synchronized (ContinuousModel.this) {
                        if (workerFailure == null)
                            workerFailure = ex;
                    }
                }

                workerPhaser.arriveAndAwaitAdvance();
            }
        }

        private void ecologyBlock() {
            final double K = settings.sci.K;
            final double[] partial = workerLocationTotals[workerIndex];

            for (int loc = 0; loc < nLoc; loc++) {
                int base = loc * nPop;
                double total = locationTotal[loc];

                boolean willGrow = settings.sci.topDown || total < K;
                double growth = settings.sci.topDown
                        ? settings.growthRate
                        : Math.max(0.0, 1.0 - total / K) * settings.growthRate;
                double mortality = settings.sci.topDown
                        ? settings.mortality * total / K
                        : settings.mortality;

                double survival = 1.0 - mortality;
                double growthContribution = willGrow ? growth : 0.0;

                // Four independent accumulation chains, now over this worker's
                // fixed population block only.
                double sum0 = 0.0;
                double sum1 = 0.0;
                double sum2 = 0.0;
                double sum3 = 0.0;

                int pop = popStart;
                int blockLength = popEnd - popStart;
                int unrolledEnd = popStart + (blockLength - (blockLength % 4));

                for (; pop < unrolledEnd; pop += 4) {
                    int idx0 = base + pop;
                    int idx1 = idx0 + 1;
                    int idx2 = idx0 + 2;
                    int idx3 = idx0 + 3;

                    double a0 = active[idx0];
                    if (a0 != 0.0) {
                        double select = settings.isSelective ? thermalGrowth[idx0] : 1.0;
                        double next = a0 * (survival + growthContribution * select);
                        if (next < 0.0 && next > -1e-14) next = 0.0;
                        if (next < 0.0)
                            throw new IllegalStateException("Negative continuous abundance at location " + loc);
                        active[idx0] = next;
                        sum0 += next;
                    }

                    double a1 = active[idx1];
                    if (a1 != 0.0) {
                        double select = settings.isSelective ? thermalGrowth[idx1] : 1.0;
                        double next = a1 * (survival + growthContribution * select);
                        if (next < 0.0 && next > -1e-14) next = 0.0;
                        if (next < 0.0)
                            throw new IllegalStateException("Negative continuous abundance at location " + loc);
                        active[idx1] = next;
                        sum1 += next;
                    }

                    double a2 = active[idx2];
                    if (a2 != 0.0) {
                        double select = settings.isSelective ? thermalGrowth[idx2] : 1.0;
                        double next = a2 * (survival + growthContribution * select);
                        if (next < 0.0 && next > -1e-14) next = 0.0;
                        if (next < 0.0)
                            throw new IllegalStateException("Negative continuous abundance at location " + loc);
                        active[idx2] = next;
                        sum2 += next;
                    }

                    double a3 = active[idx3];
                    if (a3 != 0.0) {
                        double select = settings.isSelective ? thermalGrowth[idx3] : 1.0;
                        double next = a3 * (survival + growthContribution * select);
                        if (next < 0.0 && next > -1e-14) next = 0.0;
                        if (next < 0.0)
                            throw new IllegalStateException("Negative continuous abundance at location " + loc);
                        active[idx3] = next;
                        sum3 += next;
                    }
                }

                for (; pop < popEnd; pop++) {
                    int idx = base + pop;
                    double a = active[idx];
                    if (a == 0.0)
                        continue;
                    double select = settings.isSelective ? thermalGrowth[idx] : 1.0;
                    double next = a * (survival + growthContribution * select);
                    if (next < 0.0 && next > -1e-14) next = 0.0;
                    if (next < 0.0)
                        throw new IllegalStateException("Negative continuous abundance at location " + loc);
                    active[idx] = next;
                    sum0 += next;
                }

                partial[loc] = sum0 + sum1 + sum2 + sum3;
            }
        }

        private void refreshTotalsBlock() {
            final double[] partial = workerLocationTotals[workerIndex];

            for (int loc = 0; loc < nLoc; loc++) {
                int base = loc * nPop;
                double total = 0.0;
                for (int pop = popStart; pop < popEnd; pop++)
                    total += active[base + pop];
                partial[loc] = total;
            }
        }

        private void transportBlock(double[] source, double[] destination) {
            // Unified sparse transport. Population ownership makes destination
            // writes disjoint across workers, so no locks or atomics are needed.
            for (int src = 0; src < nLoc; src++) {
                int srcBase = src * nPop;
                int first = edgeStart[src];
                int end = edgeStart[src + 1];

                for (int e = first; e < end; e++) {
                    int dstBase = edgeDest[e] * nPop;
                    double p = edgeProb[e];
                    for (int pop = popStart; pop < popEnd; pop++)
                        destination[dstBase + pop] += p * source[srcBase + pop];
                }
            }
        }
    }

    private void switchDormancy() {
        double f = settings.sci.dormantFrac;
        double tau = settings.sci.dormantTauDay;
        double dtDay = settings.sci.dispHours / 24.0;
        double pAD = 1.0 - Math.exp(-(f / tau) * dtDay);
        double pDA = 1.0 - Math.exp(-((1.0 - f) / tau) * dtDay);

        for (int i = 0; i < active.length; i++) {
            double a = active[i];
            double d = dormant[i];
            double aToD = a * pAD;
            double dToA = d * pDA;
            active[i] = a - aToD + dToA;
            dormant[i] = d + aToD - dToA;
        }
    }

    private int temperatureIndex(long hour) {
        if (!settings.isSelective || tempIntervals == 1)
            return 0;
        long day = hour / 24L;
        int dayOfYear = (int)(day % 360L);
        int idx = (int)Math.floor(dayOfYear / (360.0 / tempIntervals));
        return Math.min(idx, tempIntervals - 1);
    }

    private void updateThermalGrowth(int tempIndex) {
        for (int loc = 0; loc < nLoc; loc++) {
            int base = loc * nPop;
            double tEnv = temperatures[loc][tempIndex];
            for (int pop = 0; pop < nPop; pop++)
                thermalGrowth[base + pop] = SelLineage.tempFunc(
                        populations[pop].tOpt, tEnv, settings.sci.W);
        }
        currentTempIndex = tempIndex;
    }

    private int index(int loc, int pop) {
        return loc * nPop + pop;
    }

    public int getNumLocations() { return nLoc; }
    public int getNumPopulations() { return nPop; }
    public int getPhenotypesPerLocation() { return phenotypesPerLocation; }
    public PopulationMeta[] getPopulations() { return populations; }
    public double[] getActive() { return active; }
    public double[] getDormant() { return dormant; }

    public void printProfile() {
        double ecologySeconds = profileEcologyNanos / 1.0e9;
        double transportSeconds = profileTransportNanos / 1.0e9;
        double totalSeconds = profileTotalNanos / 1.0e9;
        double otherSeconds = Math.max(0.0, totalSeconds - ecologySeconds - transportSeconds);

        double ecologyPercent = totalSeconds > 0.0 ? 100.0 * ecologySeconds / totalSeconds : 0.0;
        double transportPercent = totalSeconds > 0.0 ? 100.0 * transportSeconds / totalSeconds : 0.0;
        double otherPercent = totalSeconds > 0.0 ? 100.0 * otherSeconds / totalSeconds : 0.0;

        System.out.println("Continuous model profile");
        System.out.printf("  population workers:    %d%n", workerCount);
        System.out.printf("  ecology                %8.3f s  %6.2f%%%n", ecologySeconds, ecologyPercent);
        System.out.printf("  transport              %8.3f s  %6.2f%%%n", transportSeconds, transportPercent);
        System.out.printf("  other                  %8.3f s  %6.2f%%%n", otherSeconds, otherPercent);
        System.out.printf("  measured total         %8.3f s  100.00%%%n", totalSeconds);
    }

    public double totalAbundance() {
        double total = 0.0;
        for (double x : active) total += x;
        if (dormant != null)
            for (double x : dormant) total += x;
        return total;
    }

    private static double[] parseOffsets(String text) {
        if (text == null || text.isBlank())
            return new double[0];
        String[] bits = text.split(",");
        double[] values = new double[bits.length];
        for (int i = 0; i < bits.length; i++)
            values[i] = Double.parseDouble(bits[i].trim());
        return values;
    }

    private static double[][] loadTemperatures(String filename, int nLoc) throws Exception {
        if (filename == null)
            return null;
        double[][] result = new double[nLoc][];
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int loc = 0;
            int nCols = -1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (loc >= nLoc)
                    throw new IllegalArgumentException("Temperature file has more than NUM_BOXES rows");
                String[] bits = line.trim().split("[,\\s]+");
                if (nCols < 0) nCols = bits.length;
                if (bits.length != nCols)
                    throw new IllegalArgumentException("Temperature file has inconsistent column counts");
                result[loc] = new double[nCols];
                for (int i = 0; i < nCols; i++)
                    result[loc][i] = Double.parseDouble(bits[i]);
                loc++;
            }
            if (loc != nLoc)
                throw new IllegalArgumentException(
                        "Temperature file rows (" + loc + ") != NUM_BOXES (" + nLoc + ")");
        }
        return result;
    }

    private static final class Edge {
        final int src, dst;
        final double p;
        Edge(int src, int dst, double p) { this.src = src; this.dst = dst; this.p = p; }
    }

    private static final class Transport {
        final int[] edgeStart, edgeDest;
        final double[] edgeProb;
        Transport(int[] edgeStart, int[] edgeDest, double[] edgeProb) {
            this.edgeStart = edgeStart;
            this.edgeDest = edgeDest;
            this.edgeProb = edgeProb;
        }
    }

    private static Transport loadTransport(String filename, int nLoc, double scaler) throws Exception {
        if (filename == null)
            throw new IllegalArgumentException("Continuous mode requires TM_FILE");
        List<Edge> edges = new ArrayList<>();
        double[] moveSum = new double[nLoc];
        int maxSource = -1;

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] bits = line.trim().split("\\s+");
                int src = (int)Double.parseDouble(bits[0]) - 1;
                int dst = (int)Double.parseDouble(bits[1]) - 1;
                double p = Double.parseDouble(bits[2]) * scaler;
                maxSource = Math.max(maxSource, src);
                if (src < 0 || src >= nLoc || dst < 0 || dst >= nLoc)
                    continue;
                if (src != dst && p != 0.0) {
                    if (p < 0.0)
                        throw new IllegalArgumentException("Negative transport probability");
                    edges.add(new Edge(src, dst, p));
                    moveSum[src] += p;
                }
            }
        }
        if (maxSource + 1 != nLoc)
            throw new IllegalArgumentException("NUM_BOXES does not match transport matrix source count");

        edges.sort((a, b) -> a.src != b.src ? Integer.compare(a.src, b.src) : Integer.compare(a.dst, b.dst));

        // Build one source-major sparse list containing both stay and movement.
        // Each source has exactly one diagonal entry, stored first, followed by
        // its off-diagonal destinations. This lets transport use one edge loop.
        int[] moveStart = new int[nLoc + 1];
        for (Edge e : edges) moveStart[e.src + 1]++;
        for (int i = 1; i <= nLoc; i++) moveStart[i] += moveStart[i - 1];

        int[] edgeStart = new int[nLoc + 1];
        for (int src = 0; src < nLoc; src++)
            edgeStart[src + 1] = edgeStart[src] + 1 + (moveStart[src + 1] - moveStart[src]);

        int[] dest = new int[edges.size() + nLoc];
        double[] prob = new double[edges.size() + nLoc];

        for (int src = 0; src < nLoc; src++) {
            if (moveSum[src] > 1.0 + 1e-12)
                throw new IllegalArgumentException(
                        "DISP_SCALER gives total movement probability > 1 at location " + src
                        + ": " + moveSum[src]);

            int out = edgeStart[src];
            dest[out] = src;
            prob[out] = Math.max(0.0, 1.0 - moveSum[src]);
            out++;

            for (int i = moveStart[src]; i < moveStart[src + 1]; i++) {
                Edge e = edges.get(i);
                dest[out] = e.dst;
                prob[out] = e.p;
                out++;
            }
        }
        return new Transport(edgeStart, dest, prob);
    }
}
