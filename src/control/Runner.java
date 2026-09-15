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
import java.util.stream.LongStream;
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
	/**Starting time for simulation*/
	public static long startHour;
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
		startHour = 0;
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
		
		String simulationName;

		//Initialise lineages either...
		if(settings.ctrl.loadFile != null) {         //...from previous run
			String loadName = settings.ctrl.loadFile;
			simulationName = settings.ctrl.loadFile;

			if(startHour > 0) //load from specified hour
				activeBoxs = FileIO.loadDay(loadName, activeBoxsArr, tempLins);
			else //load from checkpoint
				activeBoxs = FileIO.loadCheckpoint(loadName, activeBoxsArr, tempLins);
		}
		else {                                       //...or from initialisation state
			activeBoxs = new ArrayList<GridBox>();
			for(GridBox box : activeBoxsArr) {
				if(box != null) {
					box.initPop(tempLins);
					activeBoxs.add(box);
				}
			}

			long startTime = runParallel.getClusterStartTime();
			String dateStr = RunState.DATE_FORMAT.format(new Date(startTime));
			int runNumber = getNextRunNumber(settings.ctrl.saveFile, dateStr);

			String runID = new File(settings.ctrl.saveFile).getName()
					+ "_" + dateStr
					+ "_R" + String.format("%04d", runNumber);
			
			String runDir = settings.saveDir + "/" + runID;
			new File(runDir).mkdirs();

			simulationName = runDir;
		}

		long startTime = runParallel.getClusterStartTime();
		
		return new RunState (activeBoxs, tempLins, startTime, simulationName, seed);
	}


	/**Find the next available run number for this output prefix and date.*/
	private static int getNextRunNumber(String saveFile, String dateStr) {
		File saveFileObj = new File(saveFile);
		File saveDir = saveFileObj.getParentFile();
		String prefix = saveFileObj.getName();
	
		Pattern pattern = Pattern.compile(
				Pattern.quote(prefix + "_" + dateStr + "_R") + "([0-9]+)"
		);
	
		int maxRun = 0;
	
		File[] files = saveDir.listFiles();
		if(files != null) {
			for(File file : files) {
				Matcher matcher = pattern.matcher(file.getName());
				if(matcher.matches())
					maxRun = Math.max(maxRun, Integer.parseInt(matcher.group(1)));
			}
		}
	
		return maxRun + 1;
	}

	/**Sets up random seed
	 * 
	 * @return
	 * @throws IOException
	 */
	private static int setupRandom() throws IOException {
		//Setup seed	
		int seed = settings.ctrl.seed == -1 
					?    runParallel.synchSeeds(new Random().nextInt())          //initialise randomly from current time, ensuring consistent across distributed nodes
					:    settings.ctrl.seed; 
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
		if(settings.tm.buildTM)
				boxs = MakeArtificialTM.makeUniformTM(); 
		else {
		
				boxs = FileIO.loadTM(settings.sci.tmFile,  //TM configuration
							FileIO.loadDoubleFile(settings.sci.volFile), //Volumes of each location
							FileIO.loadDoubleFile(settings.sci.tempFile), //Temp of each location
							true
						);
		}
		//		
		
		
		ArrayList<GridBox> boxList = new ArrayList<GridBox>(Arrays.asList(boxs));
		

		//setup locations
		for(GridBox box : boxs) { 
			box.initIDSizeTemp(tempLins); //establish which lineage ids are associated with which locations 
															//and (if selective) their thermal optima
		}
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
		ArrayList<GridBox> activeBoxes = null;
		try {
			
			
			activeBoxes = runState.activeBoxes;
			
					//////////////// OUTPUT /////////////////
					//saves/reports data at t=0 (if specified in SAVE_TIMESTEPS_FILE)
					//prints starting time
					//gets when next to save/report
					long[] allLineages = runParallel.getAllLineageIDs(activeBoxes, startHour);
			
					long[] saveReport = out.startOutput(activeBoxes, 
															allLineages.length,
														 runParallel.amIController());
					long saveNext = saveReport[0];
					long reportNext = saveReport[1];
					long saveMutantNext = saveReport[2];
					
					System.out.println("Starting experiment at " + runState.startTimeStr );
					System.out.println("Seed: " + runState.seed );
					System.out.println("Initial state: day 0 (year 0.00), extant lineages " + allLineages.length);

					long lastTime = runState.startTime;
					char checkLetter = 'A';
					int checkpointCounter = settings.ctrl.checkpointIntervalDay * 24;
					///////////////////////////////////////////

			//initialise selective if needed
			int tempChangesPerYear = 0;
			int tempTidx = 0;
			if(settings.sci.tempFile != null)
				tempChangesPerYear = activeBoxes.get(0).getTempChangesPerYear();
			
			int duration = settings.ctrl.durationDay * 24;
			
			
			//******************************************************************
			//**************************** MAIN RUN LOOP ***********************
			//******************************************************************
			
			for(hour = (long) (startHour + settings.sci.dispHours);  hour <= duration  ; hour += settings.sci.dispHours) {
				
				
				//get current day/hour/year
				day = (int) Math.floorDiv(hour, 24);
				int dayOfYear = (int) (day % 365);
				int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
				
				checkpointCounter -= settings.sci.dispHours;
				
				runState.day = day;
				runState.hour = hour;
				
				//find out which temperature currently on
				if(settings.sci.tempFile != null) {
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
									saveNext = out.save(hour, activeBoxes);
							
							//check if saving interval and if so save to file
							if(hour == saveMutantNext)
									saveMutantNext = out.saveMutants(hour, activeBoxes);

							
							//check if reporting interval and if so report to screen/log
							if(hour == reportNext) {
								    allLineages = runParallel.getAllLineageIDs(activeBoxes, hour);
									
								    if(Runner.settings.isSelective && allLineages.length > 0) //for speed/memory ensure only store temperatures of surviving lineages
										SelLineage.trimTempArray(allLineages);
								    
								    int globalDiversity = allLineages.length;
								    reportNext = out.report(hour, activeBoxes, globalDiversity);
							        if(settings.ctrl.stopAt1 && globalDiversity == 1)
							        	break;
							        
							        if(settings.ctrl.debugMutants) {
							        	if(settings.sci.mutation > 0) {
								        	if(runParallel.isDistributed())
								        		System.err.println("Can't currently debug mutation when distributed."
								        				+ "Instead please test locally and compare results.");
								        	else
								        		out.debug.debugMutation(globalDiversity, runState.activeBoxes, hour);
							        	}else {
							        		System.out.println("Not debugging mutation as MUTATION = 0");
							        	}
							        }
							}


							////////// CHECKPOINTING
							if (checkpointCounter <= 0) {
								//BAW out.checkPoint(hour, activeBoxes, checkLetter);
								int checkpointGlobalDiversity = runParallel.getAllLineageIDs(activeBoxes, hour).length;
                                                                out.checkPoint(hour, activeBoxes, checkLetter, checkpointGlobalDiversity);
								if(checkLetter == 'A')
									checkLetter = 'B';
								else 
									checkLetter = 'A';
							
						  		checkpointCounter = settings.ctrl.checkpointIntervalDay * 24;
								
							}
							//
			
							//just to show hasn't frozen
							double secondsTaken = (System.currentTimeMillis() - lastTime) / 1000.0;
							if (secondsTaken > settings.ctrl.timeThresh && runParallel.amIController()) { //report if taken too long
								System.out.println("(I'm still alive) day: " + day + "hr" + hourOfDay + ", year " + Math.floorDiv(day, 365));
								lastTime = System.currentTimeMillis();
							}
							
							////////////////////////////////////////////////////////////

			}
			//***************************** END OF RUN LOOP 
			
			
			
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
			out.finalOutput(runState.simName, activeBoxes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		runParallel.closeParallel();
		runState.finished = true;
	}
	
	
	//////////////////////////////////////////////////////////
	////////////////// FINALIZE /////////////////////////////////
	//////////////////////////////////////////////////////////

	
	/**Check simulation has finished and return list of locations (activeBoxes) in format for testing
	 * (wrapped in GridBoxComparison)
	 * 
	 * @return
	 * @throws Exception
	 */
	public static List<GridBoxForComparison> getFinalResults() throws Exception {
		if(!runState.finished)
			throw new Exception("Fatal Exception: Attempt to get final results before simulation finished");
		
		return runState.activeBoxes.stream().map
		(box -> new GridBoxForComparison(box)).distinct().collect(Collectors.toList());
	}

	/**Check simulation has finished and return list of locations (activeBoxes)
	 * 
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<GridBox> getFinalBoxes() throws Exception {
		if(!runState.finished)
			throw new Exception("Fatal Exception: Attempt to get final results before simulation finished");
		
		return runState.activeBoxes;
	}

	/**Get unique list of lineages
	 * 
	 * @param activeBoxes complete list of locations
	 * @return
	 */
	public static long[] getAllLins(List<GridBox> activeBoxes) {
		// TODO Auto-generated method stub
		return activeBoxes.stream().flatMapToLong(box -> box.streamLinNums())
				.distinct().toArray();
	}
	






	
}
