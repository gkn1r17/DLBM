/**
 * Handles overall running of simulation
 */

package lbm;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;

import cern.jet.random.engine.DRand;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import parallelization.RunnerParallelization;
//import test.GridBoxForComparison;
import util.FileIO;
//import util.MakeArtificialTM;
import util.MakeArtificialTM;

public class Runner {
	
	/**all locations*/
	private static int tempTidx;
	private static int tempChangesPerYear;
	private static long hour;
	private static long day;

	private static final ArrayList<GridBox> activeCells = new ArrayList<GridBox>();

	
	
	
	
	/**
	 * Launch program.
	 * @param args parameters
	 */
	public static void main(String[] args) {
		
		//separate number of nodes from other program arguments (i.e. settings)
		if(args.length > 0 && args[0].toLowerCase().equals("numnodes")) {
			int numNodes = Integer.parseInt(args[1]);
			args = Arrays.asList(args).subList(2, args.length).toArray(new String[args.length - 2]);
			//setup distributed parallel framework (mpi), extract relevent args and return remaining args
			args = RunnerParallelization.setupParallel(numNodes, args);
		}
		else {
			System.err.println("Number of nodes not specified");
			System.exit(-1);
		}
		
		//setup
		try {
			setup(args);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();			
			System.exit(-1);
		}
		
		//run
		runAll(args);

	}
	
	/** Setup this runner (will only be called once for one "Runner" object if running distributed or only one node, 
	 * 					will only be called for separate runners if testing multiple runners on this computer)
	 * @param args 
	 * 
	 * @throws Exception
	 */
	private static void setup(String[] args) throws Exception {
		
		//setup settings and potentially load saved run
		Settings.loadSettings(args, RunnerParallelization.amIController(), true);
		
		//initialise test at right time (default = 0)
		hour = Settings.LOAD_HOUR;
		day = (int) Math.floor(hour  / 24.0);
		
			
		//Setup Transport matrix and associated locations
		ArrayList<GridBox> allCells = setupTM();

		//initialise selective if needed
		tempChangesPerYear = allCells.get(0).getTempChangesPerYear();
		
		//setup random engines and seeds
		DRand rd = setupRandom();
		

		//initialise parallelization stuff and 
					//return array of cells with 
					//cells not handled on this server set to null to save memory
		GridBox[] activeCellsArr = RunnerParallelization.setupNodes(allCells, rd);

		//Initialise lineages either...	
		if(Settings.LOAD_FILE != null || Settings.LOAD_DAY > 0)         //...from end of previous run
			if(Settings.LOAD_DAY > 0 && Settings.LOAD_FILE != null)
				FileIO.loadDay(Settings.LOAD_FILE, activeCellsArr);
			else {
				throw new Exception("To load a previous run "
						+ "set both LOAD_DAY and LOAD_FILE ");
			}
		else {                                                          //...or from initialisation state
			for(GridBox cell : activeCellsArr) {
				if(cell != null) {
					cell.initPop();
					activeCells.add(cell);
				}
			}
		}
		

	}


	private static DRand setupRandom() throws IOException {
		//Setup seed	
		int seed = (Settings.SEED == Settings.LOAD_SEED) ? //load seed from saved for run of same name
				        FileIO.loadSeed()
						:
						(		
						Settings.SEED == -1 ?              //initialise randomly from current time, ensuring consistent across distributed nodes
								RunnerParallelization.synchSeeds(new Random().nextInt()) :  
						Settings.SEED );                   //initialise to specified seed
	
		System.out.println("Starting " + 0 + " with seed = " + seed);
		DRand rd = new DRand(seed);
		
		//save
		Files.createDirectories(Paths.get(Settings.DIR_OUT + "/seeds"));
		
		//TODO - can't yet load/save part way through day I believe
		String hourDayString = "" + Settings.LOAD_DAY + "hr0"; 
	
		
		FileWriter seedFile = new FileWriter(Settings.DIR_OUT + "/seeds/Settings.FILE_OUT_seed_D" + hourDayString, false);
		seedFile.append("" + seed);
		seedFile.close();
		
		return rd;
	}


	private static ArrayList<GridBox> setupTM() throws Exception {
		GridBox[] cells;
		if(Settings.BUILD_TM)
				cells = MakeArtificialTM.makeUniformTM(); 
		else {
		
				cells = FileIO.loadTM(Settings.TM_FILE,  //TM configuration
							FileIO.loadDoubleFile(Settings.VOL_FILE), //Volumes of each location
							FileIO.loadDoubleFile(Settings.TEMP_FILE), //Temp of each location
							true
						);
		}
		//		
		
		
		ArrayList<GridBox> cellList = new ArrayList<GridBox>(Arrays.asList(cells));
		GridBox.maxVol = cellList.stream()
				.mapToDouble(c -> c.getVol()).max().getAsDouble();
		

		//setup locations
		int beginID = 0;
		for(GridBox cell : cells) 
			beginID = cell.initIDSizeTemp(beginID); //establish which lineage ids are associated with which locations 
															//and (if selective) their thermal optima
		
		return cellList;
	}



	
	
	/**Start all Runners (distributed nodes)
	 * 
	 * @param args
	 */
	private static void runAll(String[] args) {
		
		
		try {
			
					//////////////// OUTPUT /////////////////
					//saves/reports data at t=0 (if specified in SAVE_TIMESTEPS_FILE)
					//prints starting time
					//gets when next to save/report
					int[] saveReport = Output.startOutput(day, activeCells, RunnerParallelization.calcGlobalDiversity(activeCells));
					int saveNext = saveReport[0];
					int reportNext = saveReport[1];
					long startTime = RunnerParallelization.getClusterStartTime();
					System.out.println("Starting experiment at " + startTime);
					long lastTime = startTime;
					///////////////////////////////////////////
			

			for(hour += Settings.DISP_HOURS  ;  day < Settings.DURATION  ; hour += Settings.DISP_HOURS) {
				
				//get current day/hour/year
				day = (int) Math.floorDiv(hour, 24);
				int dayOfYear = (int) (day % 365);
				int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
				
				
				//find out which temperature currently on
				tempTidx = (int) Math.floor(dayOfYear / (365.0 / tempChangesPerYear)  );
				if(tempTidx == tempChangesPerYear)
					tempTidx --;

				
				
				//RUN MAIN LOOP
				RunnerParallelization.runEcologyAndDispersal(day, tempTidx);
				//
				
				
				
							///////////////////// OUTPUT ///////////////////////
							
							
							//check if saving interval and if so save to file
							if(day == saveNext)
									saveNext = Output.save(day, hourOfDay, activeCells);
							//check if reporting interval and if so report to screen/log
							if(day == reportNext) {
								    int globalDiversity = RunnerParallelization.calcGlobalDiversity(activeCells);
								    reportNext = Output.report(day, activeCells, 0);
							        if(Settings.STOP_AT_1 && globalDiversity == 1)
							        	break;
							}
//							
//							
							//checkpoint before getting kicked off cluster
							if ((System.currentTimeMillis() - startTime) / 1000.0 / 3600.0 > (Settings.EXPERIMENT_HOURS * 0.99))
								Output.checkPoint(day, hourOfDay, activeCells);
			
							//just to show hasn't frozen
							double secondsTaken = (System.currentTimeMillis() - lastTime) / 1000.0;
							if (secondsTaken > Settings.TIME_THRESH && RunnerParallelization.amIController()) { //report if taken too long
								System.out.println("(I'm still alive) day: " + day + "hr" + hourOfDay + ", year " + Math.floorDiv(day, 365));
								lastTime = System.currentTimeMillis();
							}
							
							////////////////////////////////////////////////////////////

			}
			
			
		}catch(Exception e){
			int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
			System.err.println("Failed at day " + day + " hour" + hourOfDay);
			e.printStackTrace();
			RunnerParallelization.abortParallel();
			System.exit(-1);		

		}
		
		try {
			Output.logToCSV();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		RunnerParallelization.closeParallel();
		
	}

	public static List<GridBox> getAllLocs() {
		return activeCells;
	}

	public static long getDay() {
		return day;
	}
	









}
