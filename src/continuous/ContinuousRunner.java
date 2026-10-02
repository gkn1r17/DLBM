package continuous;

import config.Config;
import control.RunState;
import inputOutput.FileIO;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**Entry point for deterministic continuous dLBM simulations.*/
public final class ContinuousRunner {
    private static final byte[] BINARY_MAGIC = "DLBMC01".getBytes(StandardCharsets.US_ASCII);
    private static final boolean PROFILE_WRITE_OUTPUT = true;

    private ContinuousRunner() {}

    public static void main(String[] args) {
        try {
            args = normaliseArguments(args);
            Config settings = new Config(args, true, true, true);
            validate(settings);

            String runDir = null;
            if (PROFILE_WRITE_OUTPUT) {
                runDir = makeRunDirectory(settings);
                FileIO.makeSettingsFile(runDir, settings.getSettingsIniOut());
            }

            ContinuousModel model = new ContinuousModel(settings);
            if (PROFILE_WRITE_OUTPUT)
                writePopulationMetadata(runDir, model, settings);

            System.out.println("Continuous deterministic dLBM");
            System.out.println("Initialisation: " + settings.continuous.initMode);
            System.out.println("Locations: " + model.getNumLocations());
            System.out.println("Populations: " + model.getNumPopulations());
            System.out.println("Phenotypes per location: " + model.getPhenotypesPerLocation());
            System.out.println("K: " + settings.sci.K + " (continuous abundance scale)");
            if (settings.sci.initLinSize != 1)
                System.out.println("Note: INIT_LIN_SIZE is not used by continuous mode.");

            long durationHour = settings.ctrl.durationDay * 24L;
            long dispHour = Math.round(settings.sci.dispHours);
            int saveIndex = 0;

            while (saveIndex < settings.saveTimestepsArr.length
                    && settings.saveTimestepsArr[saveIndex] < 0) saveIndex++;

            if (saveIndex < settings.saveTimestepsArr.length
                    && settings.saveTimestepsArr[saveIndex] == 0) {
                if (PROFILE_WRITE_OUTPUT)
                    saveSnapshot(runDir, 0, model, settings);
                saveIndex++;
            }

            long started = System.currentTimeMillis();
            for (long hour = dispHour; hour <= durationHour; hour += dispHour) {
                model.step(hour);

                if (saveIndex < settings.saveTimestepsArr.length
                        && hour == settings.saveTimestepsArr[saveIndex]) {
                    if (PROFILE_WRITE_OUTPUT)
                        saveSnapshot(runDir, hour, model, settings);
                    saveIndex++;
                }
            }

            double seconds = (System.currentTimeMillis() - started) / 1000.0;
            model.printProfile();
            System.out.printf("Finished in %.3f s%n", seconds);
            if (PROFILE_WRITE_OUTPUT)
                System.out.println("Output: " + runDir);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /**
     * Continuous mode uses one process with shared-memory worker threads.
     * Accept NUMNODES 1 for command-line compatibility, but do not pass it into Config.
     * NUM_WORKERS is deliberately retained so ContinuousConfig handles it normally.
     */
    private static String[] normaliseArguments(String[] args) {
        if (args.length % 2 != 0)
            throw new IllegalArgumentException("Continuous arguments must be NAME VALUE pairs");

        List<String> kept = new ArrayList<>();

        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            String value = args[i + 1];

            if (key.equalsIgnoreCase("NUMNODES")) {
                if (!value.equals("1"))
                    throw new IllegalArgumentException(
                            "Continuous mode uses one process: NUMNODES must be 1");
                continue;
            }
            if (key.equalsIgnoreCase("MODEL"))
                continue; // harmless for direct invocation; launcher normally removes this

            kept.add(key);
            kept.add(value);
        }

        return kept.toArray(new String[0]);
    }

    private static void validate(Config s) {
        if (s.sci.mutation != 0.0)
            throw new IllegalArgumentException("Continuous mode currently requires MUTATION=0");
        if (s.ctrl.loadFile != null)
            throw new IllegalArgumentException("Continuous restart/loading is not yet implemented; FILE_LOAD must be none");
        if (s.tm.buildTM)
            throw new IllegalArgumentException("Continuous mode currently requires a TM_FILE; BUILD_TM must be false");
        if (s.sci.K <= 0)
            throw new IllegalArgumentException("K must be > 0");
        if (s.sci.dormantFrac < 0.0 || s.sci.dormantFrac >= 1.0)
            throw new IllegalArgumentException("DORMANT_FRAC must be >= 0 and < 1");
        if (s.sci.dormantFrac > 0.0 && s.sci.dormantTauDay <= 0.0)
            throw new IllegalArgumentException("DORMANT_TAU_DAY must be > 0 when dormancy is enabled");
        if (Math.abs(s.growthPerDisp - Math.rint(s.growthPerDisp)) > 1e-12)
            throw new IllegalArgumentException("DISP_HOURS must be an integer multiple of GROWTH_HOURS");
        if (Math.abs(s.sci.dispHours - Math.rint(s.sci.dispHours)) > 1e-12)
            throw new IllegalArgumentException("Continuous runner currently requires integer DISP_HOURS");
        if ((s.ctrl.durationDay * 24L) % Math.round(s.sci.dispHours) != 0)
            throw new IllegalArgumentException("Simulation duration must be divisible by DISP_HOURS");
        if (!s.continuous.outputMode.equals("binary") && !s.continuous.outputMode.equals("csv"))
            throw new IllegalArgumentException("CONT_OUTPUT_MODE must be binary or csv");
        if (s.continuous.outputMinAbundance < 0.0)
            throw new IllegalArgumentException("CONT_OUTPUT_MIN_ABUNDANCE must be >= 0");
        long disp = Math.round(s.sci.dispHours);
        for (long t : s.saveTimestepsArr)
            if (t % disp != 0)
                throw new IllegalArgumentException("SAVE_TIMESTEPS_DAY contains a time not aligned with DISP_HOURS: hour " + t);
    }

    private static String makeRunDirectory(Config settings) {
        String date = RunState.DATE_FORMAT.format(new Date());
        File prefixFile = new File(settings.ctrl.saveFile);
        File parent = prefixFile.getParentFile();
        if (parent == null) parent = new File(".");
        parent.mkdirs();
        String prefix = prefixFile.getName();
        int run = nextRunNumber(parent, prefix, date);
        File dir = new File(parent, prefix + "_" + date + "_R" + String.format("%04d", run));
        if (!dir.mkdirs() && !dir.isDirectory())
            throw new IllegalStateException("Could not create output directory " + dir);
        return dir.getPath();
    }

    private static int nextRunNumber(File parent, String prefix, String date) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix + "_" + date + "_R") + "([0-9]+)");
        int max = 0;
        File[] files = parent.listFiles();
        if (files != null) {
            for (File f : files) {
                Matcher m = pattern.matcher(f.getName());
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max + 1;
    }

    private static void writePopulationMetadata(String runDir, ContinuousModel model, Config settings)
            throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(runDir + "/populations.csv"))) {
            w.write("population_index,population_id,origin_location,phenotype,topt\n");
            ContinuousModel.PopulationMeta[] meta = model.getPopulations();
            for (int i = 0; i < meta.length; i++) {
                ContinuousModel.PopulationMeta p = meta[i];
                w.write(i + "," + p.id + "," + p.originLocation + "," + p.phenotype + ",");
                if (settings.isSelective) w.write(Float.toString(p.tOpt));
                w.write("\n");
            }
        }
    }

    private static void saveSnapshot(String runDir, long hour, ContinuousModel model, Config settings)
            throws IOException {
        long day = hour / 24L;
        int hourOfDay = (int)(hour % 24L);
        String stem = String.format("D%09dhr%02d_N000", day, hourOfDay);
        if (settings.continuous.outputMode.equals("binary"))
            saveBinary(runDir + "/" + stem + ".bin", hour, model);
        else
            saveCsv(runDir + "/" + stem + ".csv", model, settings);
        System.out.printf("Saved day %d (year %.3f)%n", day, day / 360.0);
    }

    /**
     * Dense lossless snapshot. Layout is big-endian:
     * 7-byte ASCII magic DLBMC01; int nLoc; int nPop; byte nStates; long hour;
     * nLoc*nPop active doubles; then dormant doubles if nStates==2.
     * Population metadata are stored once in populations.csv.
     */
    private static void saveBinary(String filename, long hour, ContinuousModel model) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename), 1 << 20))) {
            out.write(BINARY_MAGIC);
            out.writeInt(model.getNumLocations());
            out.writeInt(model.getNumPopulations());
            out.writeByte(model.getDormant() == null ? 1 : 2);
            out.writeLong(hour);
            for (double x : model.getActive()) out.writeDouble(x);
            if (model.getDormant() != null)
                for (double x : model.getDormant()) out.writeDouble(x);
        }
    }

    /**Compatibility/debug output. Dense continuous simulations can make CSV files very large.*/
    private static void saveCsv(String filename, ContinuousModel model, Config settings) throws IOException {
        double min = settings.continuous.outputMinAbundance;
        double[] a = model.getActive();
        double[] d = model.getDormant();
        ContinuousModel.PopulationMeta[] meta = model.getPopulations();
        int nPop = model.getNumPopulations();

        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename), 1 << 20)) {
            if (settings.isSelective) w.write("temps\n");
            for (int loc = 0; loc < model.getNumLocations(); loc++) {
                w.write(Integer.toString(loc));
                int base = loc * nPop;
                for (int pop = 0; pop < nPop; pop++) {
                    double x = a[base + pop];
                    if (x > min) {
                        w.write("," + meta[pop].id + "," + x);
                        if (settings.isSelective) w.write("," + meta[pop].tOpt);
                    }
                    if (d != null) {
                        x = d[base + pop];
                        if (x > min) {
                            w.write("," + (-meta[pop].id) + "," + x);
                            if (settings.isSelective) w.write("," + meta[pop].tOpt);
                        }
                    }
                }
                w.write("\n");
            }
        }
    }

}
