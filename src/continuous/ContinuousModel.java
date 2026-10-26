package continuous;

import config.Config;
import config.TemperatureForcing;
import config.TransportForcing;
import lineages.SelLineage;

import java.util.Arrays;
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
    private final TemperatureForcing temperatureForcing;
    private final PopulationMeta[] populations;

    // Dense, location-major: index = location*nPop + population.
    private double[] active;
    private double[] dormant;
    private double[] activeWork;
	private double[] dormantWork;
	
	// Population temporarily unavailable for transport.
	private double[] activeUnavailable;
	private double[] dormantUnavailable;
	private double[] activeUnavailableWork;
	private double[] dormantUnavailableWork;

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
	private volatile double[] workerUnavailableSource;
	private volatile double[] workerUnavailableDestination;
    private volatile RuntimeException workerFailure;

	// Sparse transport, grouped by source.
	private final TransportForcing transportForcing;
	private final int[] edgeStart;
	private final int[] edgeDest;
	private final double[] edgeProb;

    private double[] thermalGrowth;

    // Coarse profiling only. Timers surround whole model phases, never hot loops.
    private long profileEcologyNanos;
    private long profileTransportNanos;
    private long profileTotalNanos;

    private enum WorkerOperation {
        IDLE, ECOLOGY, REFRESH_TOTALS, TRANSPORT, HOLDING_TRANSPORT, STOP
    }


    public ContinuousModel(Config settings) throws Exception {
        this.settings = settings;
        this.nLoc = settings.numBoxes;
        this.temperatureForcing = TemperatureForcing.load(settings.sci.tempFile, nLoc);

	switch (settings.sci.initMode) {
	
		case "local_singletons":
		case "global_equal":
			phenotypesPerLocation = settings.sci.K;
			break;
	
		case "local_dominant":
			phenotypesPerLocation = 1;
			break;
		
		case "local_phenotypes":
			phenotypesPerLocation = settings.continuous.globalPhenotypeCount;
			break;
	
		default:
			throw new IllegalArgumentException(
					"Unknown INIT_MODE: " + settings.sci.initMode);
	}
	
	this.nPop = settings.sci.initMode.equals("global_equal")
			? phenotypesPerLocation
			: Math.multiplyExact(nLoc, phenotypesPerLocation);
        long nState = (long)nLoc * nPop;
        if (nState > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Continuous state exceeds Java array index limit: " + nState);

        active = new double[(int)nState];
        activeWork = new double[(int)nState];
		
		if (settings.sci.unavailableDays > 0) {
			activeUnavailable = new double[(int)nState];
			activeUnavailableWork = new double[(int)nState];
		}
        if (settings.sci.dormantFrac > 0.0) {
            dormant = new double[(int)nState];
			dormantWork = new double[(int)nState];
			
			if (settings.sci.unavailableDays > 0) {
				dormantUnavailable = new double[(int)nState];
				dormantUnavailableWork = new double[(int)nState];
			}
        }

        locationTotal = new double[nLoc];
        populations = initialisePopulations();
        

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

		transportForcing = TransportForcing.load(settings.sci.tmFile, nLoc);
		
		edgeStart = new int[nLoc + 1];
		edgeDest = new int[transportForcing.numEdges()];
		edgeProb = new double[transportForcing.numEdges()];
		
		for (int src = 0; src < nLoc; src++) {
			edgeStart[src] = transportForcing.edgeStart(src);
		
			for (int e = transportForcing.edgeStart(src);
					e < transportForcing.edgeEnd(src); e++) {
				edgeDest[e] = transportForcing.edgeDestination(e);
			}
		}
		
		edgeStart[nLoc] = transportForcing.numEdges();
		
		// Initialise transport probabilities.
		transportForcing.fillProbabilities(
				settings.sci.dispHours / 2.0,
				settings.sci.dispScaler,
				edgeProb
		);

        if (settings.isSelective) {
			thermalGrowth = new double[(int)nState];
			updateThermalGrowth(0.0);
		}
    }

	private PopulationMeta[] initialisePopulations() {
	
		PopulationMeta[] meta = new PopulationMeta[nPop];
	
		String mode = settings.sci.initMode;
		boolean global = mode.equals("global_equal");
	
		double min = Double.NaN;
		double max = Double.NaN;
	
		if (settings.isSelective && global) {
			min = temperatureForcing.minAnnualMean();
			max = temperatureForcing.maxAnnualMean();
		}
	
		if (global) {
	
			// Globally shared lineages.
			for (int ph = 0; ph < nPop; ph++) {
	
				float tOpt = Float.NaN;
	
				if (settings.isSelective) {
					tOpt = (float)(
							nPop == 1
									? (min + max) / 2.0
									: min + (max - min) * ph / (nPop - 1.0)
					);
				}
	
				meta[ph] = new PopulationMeta(
						ph + 1L,
						-1,
						ph,
						tOpt
				);
			}
	
			// One individual of every lineage in every location.
			for (int loc = 0; loc < nLoc; loc++) {
				for (int ph = 0; ph < nPop; ph++) {
					active[index(loc, ph)] = 1.0;
				}
			}
	
		} else {
	
			// Locally unique lineages.
			for (int loc = 0; loc < nLoc; loc++) {
	
				for (int ph = 0; ph < phenotypesPerLocation; ph++) {
				
					int pop = loc * phenotypesPerLocation + ph;
				
					long id = (long) loc * settings.mutantOffset
							+ ph + 1L;
				
					float tOpt = Float.NaN;
				
					if (settings.isSelective) {
				
						if (mode.equals("local_phenotypes")) {
				
							min = settings.continuous.globalPhenotypeMin;
							max = settings.continuous.globalPhenotypeMax;
				
							tOpt = (float)(
									phenotypesPerLocation == 1
											? (min + max) / 2.0
											: min + (max - min) * ph
													/ (phenotypesPerLocation - 1.0)
							);
				
						} else {
				
							tOpt = (float) temperatureForcing.annualMean(loc);
						}
					}
				
					meta[pop] = new PopulationMeta(
							id,
							loc,
							ph,
							tOpt
					);
				
					active[index(loc, pop)] =
							mode.equals("local_dominant")
									? settings.sci.K
									: mode.equals("local_phenotypes")
											? (double) settings.sci.K / phenotypesPerLocation
											: 1.0;
				}
			}
		}
	
		return meta;
	}

    /**Advance one dispersal interval: ecology substeps, transport, then dormancy switching.*/
    public void step(long hour) {
        long stepStarted = System.nanoTime();

		int growthSteps = (int)Math.round(settings.growthPerDisp);
		long phaseStarted = System.nanoTime();
		
		double intervalStartHour = hour - settings.sci.dispHours;
		
		for (int i = 0; i < growthSteps; i++) {
		
			// Temperature at the midpoint of this ecological timestep
			double growthHour = intervalStartHour
					+ (i + 0.5) * settings.sci.growthHours;
		
			if (settings.isSelective && temperatureForcing.isSeasonal())
				updateThermalGrowth(growthHour);
		
			ecologyStep();
		}
		
		profileEcologyNanos += System.nanoTime() - phaseStarted;

		phaseStarted = System.nanoTime();
		
		// Evaluate transport at the midpoint of the dispersal interval.
		double transportHour = hour - settings.sci.dispHours / 2.0;
		
		if (transportForcing.isSeasonal()) {
			transportForcing.fillProbabilities(
					transportHour,
					settings.sci.dispScaler,
					edgeProb
			);
		}
		
		if (activeUnavailable == null)
			transport(active, activeWork);
		else
			transportHolding(active, activeUnavailable,
					activeWork, activeUnavailableWork);
		
		profileTransportNanos += System.nanoTime() - phaseStarted;
		
		double[] swap = active;
		active = activeWork;
		activeWork = swap;
		
		if (activeUnavailable != null) {
			swap = activeUnavailable;
			activeUnavailable = activeUnavailableWork;
			activeUnavailableWork = swap;
		}

        // Transport changes local active abundance. Refresh once per dispersal
        // interval, then carry the totals through the ecology substeps.
        refreshLocationTotals();

        if (dormant != null) {
            phaseStarted = System.nanoTime();
			if (dormantUnavailable == null)
				transport(dormant, dormantWork);
			else
				transportHolding(dormant, dormantUnavailable,
						dormantWork, dormantUnavailableWork);
			
			profileTransportNanos += System.nanoTime() - phaseStarted;
			
			swap = dormant;
			dormant = dormantWork;
			dormantWork = swap;
			
			if (dormantUnavailable != null) {
				swap = dormantUnavailable;
				dormantUnavailable = dormantUnavailableWork;
				dormantUnavailableWork = swap;
			}

            switchDormancy();
        }

        // Remove locally nonviable lineages after transport and dormancy.
        if (settings.sci.minViablePop > 0.0) {
            removeNonviablePopulations();
            refreshLocationTotals();
        }

        // Restore transport eligibility at the end of each holding interval.
		if (activeUnavailable != null &&
				hour % (settings.sci.unavailableDays * 24L) == 0) {
		
			Arrays.fill(activeUnavailable, 0.0);
		
			if (dormantUnavailable != null)
				Arrays.fill(dormantUnavailable, 0.0);
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

    private void transportHolding(double[] source, double[] unavailableSource,
			double[] destination, double[] unavailableDestination) {
	
		Arrays.fill(destination, 0.0);
		Arrays.fill(unavailableDestination, 0.0);
	
		workerUnavailableSource = unavailableSource;
		workerUnavailableDestination = unavailableDestination;
	
		runWorkers(WorkerOperation.HOLDING_TRANSPORT, source, destination);
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
						
						case HOLDING_TRANSPORT:
							transportHoldingBlock(
									workerSource,
									workerUnavailableSource,
									workerDestination,
									workerUnavailableDestination);
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
                        ? settings.mortality * total
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
						if (activeUnavailable != null)
							activeUnavailable[idx0] *= next / a0;
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
						if (activeUnavailable != null)
							activeUnavailable[idx1] *= next / a1;			
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
						if (activeUnavailable != null)
							activeUnavailable[idx2] *= next / a2;
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
						if (activeUnavailable != null)
							activeUnavailable[idx3] *= next / a3;
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
					if (activeUnavailable != null)
						activeUnavailable[idx] *= next / a;
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
    

		private void transportHoldingBlock(double[] source,
			double[] unavailableSource,
			double[] destination,
			double[] unavailableDestination) {
	
		for (int src = 0; src < nLoc; src++) {
	
			int srcBase = src * nPop;
	
			// Unavailable cells remain in their current province.
			for (int pop = popStart; pop < popEnd; pop++) {
				int idx = srcBase + pop;
				double held = unavailableSource[idx];
	
				destination[idx] += held;
				unavailableDestination[idx] += held;
			}
	
			// Transport only available cells.
			for (int e = edgeStart[src]; e < edgeStart[src + 1]; e++) {
	
				int dst = edgeDest[e];
				int dstBase = dst * nPop;
				double p = edgeProb[e];
				boolean moved = dst != src;
	
				for (int pop = popStart; pop < popEnd; pop++) {
	
					int srcIdx = srcBase + pop;
	
					double eligible =
							source[srcIdx] - unavailableSource[srcIdx];
	
					if (eligible < 0.0) {
						if (eligible < -1e-10 *
								Math.max(1.0, source[srcIdx]))
							throw new IllegalStateException(
									"Unavailable exceeds abundance at index "
									+ srcIdx);
	
						eligible = 0.0;
					}
	
					double amount = p * eligible;
	
					destination[dstBase + pop] += amount;
	
					if (moved)
						unavailableDestination[dstBase + pop] += amount;
				}
			}
		}
	}
	}
	
	/**
     * Eliminate local lineages whose combined active and dormant
     * abundance falls below the minimum viable population.
     */
    private void removeNonviablePopulations() {

        final double threshold = settings.sci.minViablePop;

        for (int i = 0; i < active.length; i++) {

            double total = active[i];

            if (dormant != null)
                total += dormant[i];

            if (total < threshold) {

                active[i] = 0.0;

                if (dormant != null)
                    dormant[i] = 0.0;

                if (activeUnavailable != null)
                    activeUnavailable[i] = 0.0;

                if (dormantUnavailable != null)
                    dormantUnavailable[i] = 0.0;
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

			if (activeUnavailable != null) {
			
				double uA = activeUnavailable[i];
				double uD = dormantUnavailable[i];
			
				double uAToD = uA * pAD;
				double uDToA = uD * pDA;
			
				activeUnavailable[i] = uA - uAToD + uDToA;
				dormantUnavailable[i] = uD + uAToD - uDToA;
			}
        }
    }

	private void updateThermalGrowth(double hour) {
		for (int loc = 0; loc < nLoc; loc++) {
	
			int base = loc * nPop;
			double tEnv = temperatureForcing.at(loc, hour);
	
			for (int pop = 0; pop < nPop; pop++) {
				thermalGrowth[base + pop] = SelLineage.tempFunc(
						populations[pop].tOpt,
						tEnv,
						settings.sci.W
				);
			}
		}
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

}
