/**Handles overall running of simulation.
 *
 */

//TODO check pointing better handled through object streaming, 
                            //including seeds rather than separate files 

package control;

import java.io.File;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.IntStream.Builder;

import cern.jet.random.engine.DRand;
import config.Config;
import inputOutput.FileIO;
import inputOutput.Output;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import parallelization.GridBoxParallelization;
import parallelization.Node;
import parallelization.RunnerParallelization;
import test.GridBoxForComparison;
import transportMatrix.GridBox;
import transportMatrix.Phylogeny;
import transportMatrix.artificialTM.MakeArtificialTM;

public class Runner {
	

	//********* Singleton classes containing fundamental information about run
	/**General variables needed across simulation
	 		* (time parameters,
	 		* random seed,
	 		* references to each grid box, 
	 		* lineage information not stored in the Lineage objects for memory reasons).*/
	public static RunState runState;
	/**General variables and methods needed across simulation concerned with parallelization / distribution.*/ 
	public static RunnerParallelization runParallel;
	/**Configuration variables loaded from ini files (default.ini and additional ini files).*/
	public static Config settings;
	//***



	//////////////////////////////////////////////////////////
	////////////////// SETUP /////////////////////////////////
	//////////////////////////////////////////////////////////



	
	
	/**
	 * Launch program.
	 * @param args parameters
	 */
	public static void main(String[] args) {
		//just in case: clean up from possible previous runs
		runState = null;
		runParallel = null;
		settings = null;
		//
		
		
		
		//separate number of nodes from other program arguments (i.e. settings)
		if(args.length > 0 && args[0].toLowerCase().equals("numnodes")) {
			int numNodes = Integer.parseInt(args[1]);
			args = Arrays.asList(args).subList(2, args.length).toArray(new String[args.length - 2]);
			//setup distributed parallel framework (mpi), extract relevent args and return remaining args
			runParallel = new RunnerParallelization();
			args = runParallel.setupParallel(numNodes, args);
		}
		else {
			System.err.println("Number of nodes not specified. Exiting");
			System.exit(-1);
		}
		
		//setup
		try {
			runState = setup(args);
			
			//run
			runAll(args);

		} catch (Exception e) {
			e.printStackTrace();			
			System.exit(-1);
		}
		

	}
	
	/** Setup this runner (will only be called once for one "Runner" object if running distributed or only one node, 
	 * 					will only be called for separate runners if testing multiple runners on this computer)
	 * @param args parameters.
	 * @return 
	 * 
	 * @throws Exception
	 */
	private static RunState setup(String[] args) throws Exception {
		
		ArrayList<GridBox> activeBoxs = null;
		
		//setup settings
		settings = new Config(args, runParallel.amIController(), true);
		
		ConcurrentHashMap<Long, Float> tempLins = new ConcurrentHashMap<Long, Float>();
		//Setup Transport matrix and associated locations
		ArrayList<GridBox> allBoxs = setupTM( tempLins );

		
		//setup random engines and seeds
		int seed = setupRandom();
		DRand rd = new DRand(seed);

		//initialise parallelization stuff and 
					//return array of boxs with 
					//boxs not handled on this server set to null to save memory
		GridBox[] activeBoxsArr = runParallel.setupNodes(allBoxs, rd);
		
		long loadedStartTime = 0;

		//Initialise lineages either...	
		if(settings.CTRL.LOAD_FILE != null || settings.CTRL.LOAD_HOUR > 0)         //...from end of previous run
			if(settings.CTRL.LOAD_HOUR > 0 && settings.CTRL.LOAD_FILE != null) {
				
				loadedStartTime = getLoadedStartTime();
				String loadName = settings.CTRL.LOAD_FILE + "_T" + RunState.SDF.format(new Date(loadedStartTime));
				activeBoxs = FileIO.loadDay(loadName, activeBoxsArr,  tempLins);
				
				if(settings.SCI.MUTATION > 0) {
					long[] maxMutnums = Phylogeny.loadMutants(loadName, allBoxs, false, settings.CTRL.LOAD_HOUR); 
					for(GridBox box : activeBoxs) {
						box.addLoadedPhylogeny(new Phylogeny(box.id, maxMutnums[box.id] + 1) );
					}
				}
				
				Arrays.asList(activeBoxsArr);
			}
			else {
				throw new Exception("To load a previous run "
						+ "set both LOAD_DAY and FILE_LOAD ");
			}
		else {                                                          //...or from initialisation state
			activeBoxs = new ArrayList<GridBox>();
			for(GridBox box : activeBoxsArr) {
				if(box != null) {
					box.initPop(tempLins);
					activeBoxs.add(box);
				}
			}
		}
		
		long startTime = runParallel.getClusterStartTime();
		
		String simulationName =  settings.CTRL.SAVE_FILE + "_T" + RunState.SDF.format(new Date
				(loadedStartTime == 0 ? startTime : loadedStartTime));
		
		return new RunState (activeBoxs, tempLins, startTime, simulationName, seed);
	}


	/**When loading from previous run start time for files will be start time of previous run.
	 * This methods obtains that time
	 * 
	 * @return
	 * @throws Exception
	 */
	private static long getLoadedStartTime() throws Exception {
		String datePattern = "T([0-9]{2}\\-[0-9]{2}\\-[0-9]{4} [0-9]{2}\\-[0-9]{2}\\-[0-9]{2}?)";
		
		//if time specified in filename use that time
		Matcher loadFileMatcher = Pattern.compile(".*" + datePattern + ".*\\.csv").matcher(settings.CTRL.LOAD_FILE);
		if(loadFileMatcher.find()) {
			String dateMatch = loadFileMatcher.group(0);
			return RunState.SDF.parse(dateMatch).getTime();
		}
			
		
		
		//if not then use most recent run
		String loadDir = Runner.settings.LOAD_DIR;
		Pattern pattern = Pattern.compile(Runner.settings.CTRL.LOAD_FILE.replace(loadDir + "/", "") 
												+ "_" + datePattern + ".*\\.csv", Pattern.CASE_INSENSITIVE);
		
		ArrayList<Matcher> patternMatches = Stream.of(new File(loadDir).listFiles())
					      .filter(file -> !file.isDirectory())
					      .map(File::getName)
					      .map(f -> pattern.matcher(f))
					      .collect(Collectors.toCollection(ArrayList::new));
		
		long maxTime = 0;
		for(Matcher match : patternMatches) {
			if(match.find()) {
				String dateMatch = match.group(1);
				long actualTimestamp = RunState.SDF.parse(dateMatch).getTime();
				maxTime = Math.max(actualTimestamp, maxTime);
			}
		}
		
		if(maxTime == 0)
			throw new Exception("Can't find load file " + settings.CTRL.LOAD_FILE);
		
		return maxTime;
	}

	/**Sets up random seed
	 * 
	 * @return
	 * @throws IOException
	 */
	private static int setupRandom() throws IOException {
		//Setup seed	
		int seed = settings.CTRL.SEED == -1 
					?    runParallel.synchSeeds(new Random().nextInt())          //initialise randomly from current time, ensuring consistent across distributed nodes
					:    settings.CTRL.SEED; 
									                  //initialise to specified seed
		return seed;
	}

	/**Setup Transport Matrix
	 * 
	 * @param tempLins temperature for each lineage
	 * @return
	 * @throws Exception
	 */
	private static ArrayList<GridBox> setupTM(ConcurrentHashMap<Long, Float> tempLins) throws Exception {
		GridBox[] boxs;
		if(settings.TM.BUILD_TM)
				boxs = MakeArtificialTM.makeUniformTM(); 
		else {
		
				boxs = FileIO.loadTM(settings.SCI.TM_FILE,  //TM configuration
							FileIO.loadDoubleFile(settings.SCI.VOL_FILE), //Volumes of each location
							FileIO.loadDoubleFile(settings.SCI.TEMP_FILE), //Temp of each location
							true
						);
		}
		//		
		
		
		ArrayList<GridBox> boxList = new ArrayList<GridBox>(Arrays.asList(boxs));
		

		//setup locations
		for(GridBox box : boxs) 
			box.initIDSizeTemp(tempLins); //establish which lineage ids are associated with which locations 
															//and (if selective) their thermal optima
		
		return boxList;
	}



	//////////////////////////////////////////////////////////
	////////////////// RUN /////////////////////////////////
	//////////////////////////////////////////////////////////
	
	
	/**Start simulation
	 * 
	 * @param args command line args
	 * @param startTime 
	 */
	private static void runAll(String[] args) {
		long hour = 0;
		long day = 0;
		Output out = new Output();
		ArrayList<GridBox> activeBoxs = null;
		try {
			
			
			activeBoxs = runState.activeBoxs;
			
					//////////////// OUTPUT /////////////////
					//saves/reports data at t=0 (if specified in SAVE_TIMESTEPS_FILE)
					//prints starting time
					//gets when next to save/report
					long[] saveReport = out.startOutput(activeBoxs, 
														runParallel.calcGlobalDiversity(activeBoxs),
														 runParallel.amIController());
					long saveNext = saveReport[0];
					long reportNext = saveReport[1];
					long saveMutantNext = saveReport[2];
					
					System.out.println("Starting experiment at " + runState.startTimeStr );
					System.out.println("Seed: " + runState.seed );

					long lastTime = runState.startTime;
					///////////////////////////////////////////

			//initialise selective if needed
			int tempChangesPerYear = 0;
			int tempTidx = 0;
			if(settings.SCI.TEMP_FILE != null)
				tempChangesPerYear = activeBoxs.get(0).getTempChangesPerYear();
			
			int duration = settings.CTRL.DURATION_DAY * 24;
			for(hour = (long) (settings.CTRL.LOAD_HOUR + settings.SCI.DISP_HOURS);  hour <= duration  ; hour += settings.SCI.DISP_HOURS) {
				
				
				//get current day/hour/year
				day = (int) Math.floorDiv(hour, 24);
				int dayOfYear = (int) (day % 365);
				int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
				
				
				runState.day = day;
				runState.hour = hour;
				
				//find out which temperature currently on
				if(settings.SCI.TEMP_FILE != null) {
					tempTidx = (int) Math.floor(dayOfYear / (365.0 / tempChangesPerYear)  );
					if(tempTidx == tempChangesPerYear)
						tempTidx --;
				}
				
				//RUN MAIN LOOP
				runParallel.runEcologyAndDispersal(day, tempTidx, hour);
				//
				
				
							///////////////////// OUTPUT ///////////////////////
							
							
							//check if saving interval and if so save to file
							if(hour == saveNext)
									saveNext = out.save(hour, activeBoxs);
							
							//check if saving interval and if so save to file
							if(hour == saveMutantNext)
									saveMutantNext = out.saveMutants(hour, activeBoxs);

							
							//check if reporting interval and if so report to screen/log
							if(hour == reportNext) {
								    int globalDiversity = runParallel.calcGlobalDiversity(activeBoxs);
								    reportNext = out.report(hour, activeBoxs, globalDiversity);
							        if(settings.CTRL.STOP_AT_1 && globalDiversity == 1)
							        	break;
							        
							        if(settings.CTRL.DEBUG) {
							        	if(settings.SCI.MUTATION > 0) {
								        	if(runParallel.isDistributed())
								        		System.err.println("Can't currently debug mutation when distributed."
								        				+ "Instead please test locally and compare results.");
								        	else
								        		out.debug.debugMutation(globalDiversity, runState.activeBoxs, hour);
							        	}else {
							        		System.out.println("Not debugging mutation as MUTATION = 0");
							        	}
							        }
							}
//							
//							
							//checkpoint before getting kicked off cluster
							if ((System.currentTimeMillis() - runState.startTime) / 1000.0 / 3600.0 > (settings.CTRL.CHECKPOINT_HOURS * 0.99))
								out.checkPoint(hour, activeBoxs);
			
							//just to show hasn't frozen
							double secondsTaken = (System.currentTimeMillis() - lastTime) / 1000.0;
							if (secondsTaken > settings.CTRL.TIME_THRESH && runParallel.amIController()) { //report if taken too long
								System.out.println("(I'm still alive) day: " + day + "hr" + hourOfDay + ", year " + Math.floorDiv(day, 365));
								lastTime = System.currentTimeMillis();
							}
							
							////////////////////////////////////////////////////////////

			}
			
			
			runState.day = (int) Math.floorDiv(hour, 24);
			runState.hour = hour;

			
			
		}catch(Exception e){
			int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
			System.err.println("Failed at day " + day + " hour" + hourOfDay);
			e.printStackTrace();
			runParallel.abortParallel();
			System.exit(-1);		

		}
		
		try {
			out.finalOutput(runState.simulationName, activeBoxs);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		runParallel.closeParallel();
		runState.finished = true;
	}
	
	
	//////////////////////////////////////////////////////////
	////////////////// FINALIZE /////////////////////////////////
	//////////////////////////////////////////////////////////

	

	public static List<GridBoxForComparison> getFinalResults() throws Exception {
		if(!runState.finished)
			throw new Exception("Fatal Exception: Attempt to get final results before simulation finished");
		
		return runState.activeBoxs.stream().map
		(box -> new GridBoxForComparison(box)).distinct().collect(Collectors.toList());
	}




	
}
