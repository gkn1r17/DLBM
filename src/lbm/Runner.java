/**
 * Handles overall running of simulation
 */

package lbm;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
	
	
	public static RunState runState;;


	public static class RunState {
		public final ArrayList<GridBox> activeCells;
		public final Settings settings;
		public long hour;
		public long day;
		
		public RunState(ArrayList<GridBox> activeCells, Settings settings) {
			this.activeCells = activeCells;
			this.settings = settings;
		}
		
	}





	
	
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
			System.err.println("Number of nodes not specified. Exiting");
			System.exit(-1);
		}
		
		//setup
		try {
			runState = setup(args);
			
			//run
			runAll(args, runState);

		} catch (Exception e) {
			e.printStackTrace();			
			System.exit(-1);
		}
		

	}
	
	/** Setup this runner (will only be called once for one "Runner" object if running distributed or only one node, 
	 * 					will only be called for separate runners if testing multiple runners on this computer)
	 * @param args 
	 * @return 
	 * 
	 * @throws Exception
	 */
	private static RunState setup(String[] args) throws Exception {
		
		ArrayList<GridBox> activeCells = null;
		
		//setup settings
		Settings settings = new Settings(args, RunnerParallelization.amIController(), true);
		
		//Setup Transport matrix and associated locations
		ArrayList<GridBox> allCells = setupTM(settings);

		
		//setup random engines and seeds
		DRand rd = setupRandom(settings);
		

		//initialise parallelization stuff and 
					//return array of cells with 
					//cells not handled on this server set to null to save memory
		GridBox[] activeCellsArr = RunnerParallelization.setupNodes(allCells, rd, settings);

		//Initialise lineages either...	
		if(settings.FILE_LOAD != null || settings.LOAD_HOUR > 0)         //...from end of previous run
			if(settings.LOAD_HOUR > 0 && settings.FILE_LOAD != null) {
				activeCells = FileIO.loadDay(settings.FILE_LOAD, activeCellsArr, settings);
				Arrays.asList(activeCellsArr);
			}
			else {
				throw new Exception("To load a previous run "
						+ "set both LOAD_DAY and FILE_LOAD ");
			}
		else {                                                          //...or from initialisation state
			activeCells = new ArrayList<GridBox>();
			for(GridBox cell : activeCellsArr) {
				if(cell != null) {
					cell.initPop();
					activeCells.add(cell);
				}
			}
		}
		
		return new RunState (activeCells, settings);
	}


	private static DRand setupRandom(Settings settings) throws IOException {
		//Setup seed	
		int seed = (settings.SEED == settings.LOAD_SEED) ? //load seed from saved for run of same name
				        FileIO.loadSeed(settings)
						:
						(		
						settings.SEED == -1 ?              //initialise randomly from current time, ensuring consistent across distributed nodes
								RunnerParallelization.synchSeeds(new Random().nextInt()) :  
						settings.SEED );                   //initialise to specified seed
	
		System.out.println("Starting " + 0 + " with seed = " + seed);
		DRand rd = new DRand(seed);
		
		//save
		Files.createDirectories(Paths.get(settings.FILE_OUT + "/seeds"));
		
		//all output is currently not in hours but [day]h[hourOfDay] format
			//for back compatibility as formally only counted days
		long day = (long) Math.floor(settings.LOAD_HOUR / 24);
		int hourOfDay = (int) (settings.LOAD_HOUR - (day * 24));
		String hourDayString = "" + day + "h" + hourOfDay; 
	
		
		FileWriter seedFile = new FileWriter(settings.FILE_OUT + "/seeds/settings.FILE_OUT_seed_D" + hourDayString, false);
		seedFile.append("" + seed);
		seedFile.close();
		
		return rd;
	}


	private static ArrayList<GridBox> setupTM(Settings settings) throws Exception {
		GridBox[] cells;
		if(settings.BUILD_TM)
				cells = MakeArtificialTM.makeUniformTM(settings); 
		else {
		
				cells = FileIO.loadTM(settings.TM_FILE,  //TM configuration
							FileIO.loadDoubleFile(settings.VOL_FILE, settings), //Volumes of each location
							FileIO.loadDoubleFile(settings.TEMP_FILE, settings), //Temp of each location
							true,
							settings
						);
		}
		//		
		
		
		ArrayList<GridBox> cellList = new ArrayList<GridBox>(Arrays.asList(cells));
		

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
	 * @param runS 
	 */
	private static void runAll(String[] args, RunState runS) {
		long hour = 0;
		long day = 0;
		Settings settings = runS.settings;

		
		try {
			
			
			ArrayList<GridBox> activeCells = runS.activeCells;
			
					//////////////// OUTPUT /////////////////
					//saves/reports data at t=0 (if specified in SAVE_TIMESTEPS_FILE)
					//prints starting time
					//gets when next to save/report
					long[] saveReport = Output.startOutput(activeCells, 
														RunnerParallelization.calcGlobalDiversity(activeCells, settings),
														settings);
					long saveNext = saveReport[0];
					long reportNext = saveReport[1];
					long startTime = RunnerParallelization.getClusterStartTime();
					SimpleDateFormat sdf = new SimpleDateFormat("dd/mm/yyyy HH:mm");    
					Date startDate = new Date(startTime);
					System.out.println("Starting experiment at " + sdf.format(startDate) );
					long lastTime = startTime;
					///////////////////////////////////////////

			//initialise selective if needed
			int tempChangesPerYear = 0;
			int tempTidx = 0;
			if(settings.TEMP_FILE != null)
				tempChangesPerYear = activeCells.get(0).getTempChangesPerYear();
			
			int duration = settings.DURATION_DAY * 24;
			for(hour = settings.LOAD_HOUR;  hour < duration  ; hour += settings.DISP_HOURS) {
				
				//get current day/hour/year
				day = (int) Math.floorDiv(hour, 24);
				int dayOfYear = (int) (day % 365);
				int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
				
				
				runS.day = day;
				runS.hour = hour;
				
				//find out which temperature currently on
				if(settings.TEMP_FILE != null) {
					tempTidx = (int) Math.floor(dayOfYear / (365.0 / tempChangesPerYear)  );
					if(tempTidx == tempChangesPerYear)
						tempTidx --;
				}
				
				
				//RUN MAIN LOOP
				RunnerParallelization.runEcologyAndDispersal(day, tempTidx);
				//
				
				
				
							///////////////////// OUTPUT ///////////////////////
							
							
							//check if saving interval and if so save to file
							if(hour == saveNext)
									saveNext = Output.save(hour, activeCells, settings);
							//check if reporting interval and if so report to screen/log
							if(hour == reportNext) {
								    int globalDiversity = RunnerParallelization.calcGlobalDiversity(activeCells, settings);
								    reportNext = Output.report(hour, activeCells, 0, settings);
							        if(settings.STOP_AT_1 && globalDiversity == 1)
							        	break;
							}
//							
//							
							//checkpoint before getting kicked off cluster
							if ((System.currentTimeMillis() - startTime) / 1000.0 / 3600.0 > (settings.CHECKPOINT_HOURS * 0.99))
								Output.checkPoint(hour, activeCells, settings);
			
							//just to show hasn't frozen
							double secondsTaken = (System.currentTimeMillis() - lastTime) / 1000.0;
							if (secondsTaken > settings.TIME_THRESH && RunnerParallelization.amIController()) { //report if taken too long
								System.out.println("(I'm still alive) day: " + day + "hr" + hourOfDay + ", year " + Math.floorDiv(day, 365));
								lastTime = System.currentTimeMillis();
							}
							
							////////////////////////////////////////////////////////////

			}
			
			
			runS.day = (int) Math.floorDiv(hour, 24);
			runS.hour = hour;

			
			
		}catch(Exception e){
			int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
			System.err.println("Failed at day " + day + " hour" + hourOfDay);
			e.printStackTrace();
			RunnerParallelization.abortParallel();
			System.exit(-1);		

		}
		
		try {
			Output.logToCSV(settings.FILE_OUT);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		RunnerParallelization.closeParallel();
		
	}


	









}
