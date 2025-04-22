/**
 * Handles overall running of simulation
 */

package lbm;

import java.io.FileWriter;
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
import lbm.GridBox.DistributedMovement;
import lbm.GridBox.ExternalMovement;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import util.FileIO;
import util.MakeArtificialTM;

public class Runner {
	
	static final int HOUR_YEARS = 365 * 24;
	
	
	
	/**all locations*/
	private GridBox[] cells;
	
	private HashMap<Integer, int[]> nonDistReceiveMsgs = null;
	private ArrayList<GridBox> smpls;
	private FileWriter seedFile;
	private static int global;
	private static HashSet<Integer> modList;
	/**number of nodes on distributed cluster*/
	private static int numNodes = 1;
	private static int tempDay;
	
	
	//PARALLEL STUFF
	/**if running on one computer or distributed across several*/
	private static boolean distributed;
	/**manager of one "node" (either a separate computer if distributed or one computer may manage several nodes if testing locally)*/
	private static List<Runner> runners;
	/**time simulation starts*/
	private static long startTime;
	/**index of node running on this runner*/
	private int nodeNum = 0;
	/**Set of neighbouring locations with a lot of dispersal between suitable to be in parallelized unit (a "cluster"). 
	 * 	parallelized to one processor core = 1 cluster. A node may have several clusters on each core*/
	private Collection<Cluster> clustList;
	/**handles dispersa*/
	private ArrayList<ExternalMovement> extMovs = new ArrayList<ExternalMovement>();
	private HashMap<Integer, ArrayList<DistributedMovement>> distMovs = new HashMap<Integer, ArrayList<DistributedMovement>>();
	private HashSet<Integer> srcNodes = new HashSet<Integer>();
	/**locations on this node*/
	private ArrayList<GridBox> cellList;


	
	public Runner(int i) {
		nodeNum = i;
	}

	/**
	 * Launch program.
	 * @param args parameters
	 */
	public static void main(String[] args) {
		
		//separate number of nodes from other program arguments (i.e. settings)
		if(args.length > 0 && args[0].toLowerCase().equals("numnodes")) {
			numNodes = Integer.parseInt(args[1]);
			args = Arrays.asList(args).subList(2, args.length).toArray(new String[args.length - 2]);
		}
		else {
			System.err.println("Number of nodes not specified");
			System.exit(-1);
		}
		
		//setup parallel
		try {
			//try setting up MPI distributed cluster
			args = MPI.Init(args);
			startTime = getStartTime();
			distributed = true;
			runners = Arrays.asList (new Runner[] {new Runner(MPI.COMM_WORLD.Rank())});			
		}catch(java.lang.NoClassDefFoundError mpi ) {
			//if not running on distributed cluster revert to non distributed
			startTime = System.currentTimeMillis();
		    distributed = false;
			System.out.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
			runners = new ArrayList<Runner>();
			for(int i =0; i < numNodes; i++) {
				runners.add(new Runner(i));
			}
		}
		
		
		//RUN
		runAll(args);

	}
	
	/** Setup this runner (will only be called once for one "Runner" object if running distributed or only one node, 
	 * 					will only be called for separate runners if testing multiple runners on this computer)
	 * 
	 * @throws Exception
	 */
	private void setup() throws Exception {

			
		//Setup seed	
		int seed = Settings.SEED[0] == -1 ?
				new Random().nextInt() :
					Settings.SEED[nodeNum];

		System.out.println("Starting " + nodeNum + " with seed = " + seed);
		DRand rd = new DRand(seed);
		//save
		seedFile = new FileWriter(Settings.DIR_OUT + "/seed_N" + this.nodeNum + " - " + Settings.FILE_OUT + ":" + seed,false);
		seedFile.close();
		//
		
		GridBox[] allCells = null;
		
		
		
		//Setup Transport matrix
		if(Settings.TM_FILE.equals("uniform"))
				allCells = MakeArtificialTM.makeUniformTM(rd); 
		else {
		
				allCells = FileIO.loadTM(Settings.TM_FILE, rd, 
						
						FileIO.loadDoubleFile(Settings.VOL_FILE),
						FileIO.loadDoubleFile(Settings.TEMP_FILE), 
						true
						
						);
		}
		//		
				
				
		cellList = new ArrayList<GridBox>(Arrays.asList(allCells));

		GridBox.maxVol = cellList.stream().mapToDouble(c -> c.getVol()).max().getAsDouble();

		//setup locations or "Grid boxes" as I used to call them
		int beginID = 0;
		for(GridBox cell : cellList) {
			beginID = cell.initIDSizeTemp(beginID); //establish which lineage ids are associated with which locations 
															//and (if selective) their thermal optima
			
			//parallel stuff - srcNodes = the nodes (each computer manages one "node" or set of clusters each containing a set of locations)
										//which occasionally have individuals dispersed into the node managed by this computer
			if(cell.clust.node != nodeNum && cell.imADest(nodeNum))
				srcNodes.add(cell.clust.node);
		}
		
		
		//---------parallel stuff---------- 
			if(!distributed)
				nonDistReceiveMsgs = new HashMap<Integer, int[]>();
			//get list of the clusters (each cluster contains a set of neighbouring locations and each cluster in this node is managed in parallel on this computer)
			//on this node

			clustList = cellList.stream()
					.map(cell -> cell.getClust())
					.distinct()
					.filter(clust -> clust.node == nodeNum)
					.collect(Collectors.toCollection(ArrayList::new));
			cellList = clustList.stream().flatMap(clust -> clust.getCells().stream()).collect(Collectors.toCollection(ArrayList::new));
			cellList.forEach(cell -> cell.externaliseMovers(extMovs, distMovs));
			Collections.sort(cellList);
			cells = new GridBox[Settings.NUM_BOXES];
			for(GridBox cell : cellList) {
				cells[cell.id] = cell;
			}
		//
		
		//load previous run	
		if(Settings.LOAD_FILE != null || Settings.LOAD_DAY > 0)
			if(Settings.LOAD_DAY > 0 && Settings.LOAD_FILE != null)
				FileIO.loadDay(Settings.LOAD_FILE, cells, nodeNum);
			else {
				throw new Exception("To load a previous run set both LOAD_DAY and LOAD_FILE ");
			}
		else { //initialise every location at start of simulation (not loaded)
			for(GridBox cell : cellList)
				cell.initPop();

		}
		
	}

	

	/** Gets start time and ensures all nodes have same start time 
	 * (i.e. gets start time in node 0 and broadcasts to others)
	 * @return */
	private static long getStartTime() {
		long[] startArr = new long[1];
		if(MPI.COMM_WORLD.Rank() == 0)
			startArr[0] = System.currentTimeMillis();
		MPI.COMM_WORLD.Bcast(startArr, 0, 1, MPI.LONG, 0);		
		return(startArr[0]);
	}
	
	
	/**Start all Runners (distributed nodes)
	 * 
	 * @param args
	 */
	private static void runAll(String[] args) {
		
		try {
			
			System.out.println("Starting experiment at " + startTime);
			
			//setup settings and potentially load saved run
			Settings.loadSettings(args, runners.size() == 1 ? runners.get(0).nodeNum : 0, true);
			for(Runner run: runners)
				run.setup();
			//one Runner = this is one node of distributed system or clusters file only contains one node (oneNode used to access it)
			//multiple Runners = running clusters file with multiple nodes on one machine (oneNode = null)
			Runner oneRun = null;
			if(runners.size() == 1) {
				oneRun = runners.get(0);
			}
				
			
			//initialise test at right time
			long hour = Settings.LOAD_HOUR;
			int day = (int) Math.floor(hour  / 24.0);
			//find first time stamp (in days) on which to save
			int saveI = 0;
			int saveNext = Settings.SAVE_TIMESTEPS[saveI];
			//in case loaded saved run - find next time stamp (in days) on which to save
			while(day > 0 && day >= saveNext) {
				saveI++;
				saveNext = Settings.SAVE_TIMESTEPS[saveI];
			}
			
			//save timestamp 0 if requested
			if(saveNext == 0 && day == 0) {
				for(Runner run : runners)
					run.save("" + day);
				saveI++;
				saveNext = Settings.SAVE_TIMESTEPS[saveI];
			}
			
			//for finding out which temperature on each time in selective simulations
			double tempChangesPerYear = runners.get(0).cellList.get(0).getTempChangesPerYear();

			
			long lastTime = System.currentTimeMillis();
			int endCounter = 0;
			for(hour += Settings.DISP_HOURS  ;  day < Settings.DURATION  ; hour += Settings.DISP_HOURS) {
				
				day = (int) Math.floorDiv(hour, 24);
				int dayOfYear = (int) (day % 365);
				int hourOfDay = (int) (day == 0 ? hour : (int)(hour % (day * 24)));
				
				
				//find out which temperature currently on
				tempDay = (int) Math.floor(dayOfYear / (365.0 / tempChangesPerYear)  );
				if(tempDay == tempChangesPerYear)
					tempDay --;

				
				runEcologyAndDispersal(oneRun);

				
				
				///////////// Save output //////////////////
				
				//because only get limited time on cluster so output (twice to be safe in case interrupted) if about to end
				boolean timeAboutToRunOut = Settings.EXPERIMENT_HOURS > 0 && endCounter < 2 &&
						((System.currentTimeMillis() - startTime) / 1000.0 / 3600.0 > (Settings.EXPERIMENT_HOURS * 0.99));
			
				String hourDayString = "" + day; //+ ":" + hourOfDay + "hr"; 

				
				if(day == saveNext || timeAboutToRunOut) {
					for(Runner run : runners)
						run.save(hourDayString);
					System.out.println(hourDayString + ", year " + Math.floorDiv(day, 365));
					
					//next timestamp
					saveI ++;
					if(saveI < Settings.SAVE_TIMESTEPS.length)
						saveNext = Settings.SAVE_TIMESTEPS[saveI];
					else
						saveNext = Integer.MAX_VALUE;
					
					if(timeAboutToRunOut) {
						endCounter++;
						System.out.println("Saving early");
						
					}
				}
				
				//alert if still alive if progression too slow!
				double secondsTaken = (System.currentTimeMillis() - lastTime) / 1000.0;
				if(oneRun != null && oneRun.nodeNum == 0 && secondsTaken > Settings.TIME_THRESH) { //report if taken too long
					System.out.println("(I'm still alive) day: " + hourDayString + ", year " + Math.floorDiv(day, 365));
					lastTime = System.currentTimeMillis();
				}
				


			}
	}catch(Exception e){
		e.printStackTrace();
		MPI.COMM_WORLD.Abort(-1);
		System.exit(-1);

	}
	MPI.Finalize();
	}
	

	/**make output files
	 * 
	 * @param hourDayString timestamp for filename 
	 */
	private void save(String hourDayString) {
		cellList.forEach(GridBox::clean);
		
		//include length of longest row for easier loading into R
		int lineLength = (cellList.stream().mapToInt(GridBox::getNumLins).max().getAsInt() *    
					(Settings.TEMP_FILE == null ? 2 : 3) //line will be longer if topt printed (i.e. in selective simulations)
				) + 1;
		
		String filename = Settings.DIR_OUT + "/" + Settings.FILE_OUT + "s" + lineLength + "_D" + hourDayString + "_N" + nodeNum + ".csv";
		FileIO.printAll(cellList, filename);
		
	}

	/**THE MAIN MODEL ACTIONS TO PERFORM EACH TIME STEP = growth, mortality and dispersal
	 * 
	 * @param oneRun
	 */
	private static void runEcologyAndDispersal(Runner oneRun) {
		if(oneRun != null) {
			//if distributed or there is only one node
			oneRun.runEcoDispersalLocal(tempDay); //ecological actions + dispersal
			oneRun.receiveMsg(); //receiving dispersed individuals afterwards to avoid concurrency issues
		}
		else {
			//if running multiple nodes on this machine
			runners.parallelStream().forEach(run -> run.runEcoDispersalLocal(tempDay)); //ecological actions + dispersal
			runners.parallelStream().forEach(Runner::receiveMsg); //receiving dispersed individuals afterwards to avoid concurrency issues
		}	
	}
	
	/** Ecological actions + dispersal
	 * 
	 * @param dayOfYear
	 */
	private void runEcoDispersalLocal(int dayOfYear) {
		clustList.parallelStream().forEach(clust -> clust.update(dayOfYear)); //grow + die
		
		//disperse
		for(Entry<Integer, ArrayList<DistributedMovement>> dist : distMovs.entrySet())
			dispDisperse(dist.getValue(), dist.getKey(), nodeNum);
		for(ExternalMovement mov : extMovs)
			mov.actuallyMove();
		//
	}
	










	/** 
	 * 
	 */
	private void receiveMsg() {
		
		
		if(distributed) {
			
			
			
					MPI.COMM_WORLD.Barrier();
			
			
					HashSet<Integer> msgRecieved = new HashSet<Integer>(srcNodes);
					
					while(!msgRecieved.isEmpty()) {
						
						
						Iterator<Integer> msgIter = msgRecieved.iterator();
						while(msgIter.hasNext()) {
							Integer othNode = msgIter.next();
							Status probe = MPI.COMM_WORLD.Iprobe(othNode, 0);
							if(probe != null) {
								int len = probe.Get_count(MPI.INT);
								int[] from1 = new int[len];
								Request req = MPI.COMM_WORLD.Irecv(from1,0,len,MPI.INT,othNode,0);

								
								if(len > 1)
									addDist(from1,othNode);
								req.Free();
								msgIter.remove();
							}
						}
						
						
			
			
					}
					
		}
		else { //non distributed
			for(Entry<Integer, int[]> nonDist : nonDistReceiveMsgs.entrySet()) {
				 addDist(nonDist.getValue(), nonDist.getKey()); 
				
				
			}
		}
	}

	/**Broadcast a list of all lineages / quantities dispersed to one destination which need to be handled by MPI 
	 * as destination is on another node
	 * 
	 * @param distM list of movement objects holding all lineages to move and quantity of individuals within which to move
	 * @param othNod destination (location ID) 
	 * @param thisNod source (location I)
	 */
	private static void dispDisperse(ArrayList<DistributedMovement> distM, int othNod, int thisNod) {
		TreeMap<GridBox, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridBox, TreeMap<Lineage, Integer>>();
		for(DistributedMovement mov : distM)
			mov.collateMovs(movTree);
		
		//convert list of movement objects into format below for broadcasting:
				//[-locationID, lin1ID, lin1Quant, Lin2ID, lin2Quant]
		Builder movStrm = IntStream.builder();
		
		for(Entry<GridBox, TreeMap<Lineage, Integer>> mt : movTree.entrySet()) {
			movStrm.add(-mt.getKey().id - 1); // add the location id in negative so indicates new location (i.e. not confused with lineage ID)
			for(Entry<Lineage, Integer> lin : mt.getValue().entrySet()) {
				movStrm.add(lin.getKey().getId());
				movStrm.add(lin.getValue());
			}
		}
		int[] to1 = movStrm.build().toArray();
		int len = to1.length;
		
		//broadcast
		if(distributed) {
			
			MPI.COMM_WORLD.Isend(len > 0 ? to1 : new int[] {0}
					,0,
					len > 0 ? len : 1,MPI.INT,othNod,0);
		}
		else {
			runners.get(othNod).nonDistReceiveMsgs.put(thisNod, to1);

		}
	}
	
	public static ArrayList<GridBox> getAllLocs(){
		
		return runners.stream().flatMap(run -> run.cellList.stream()).collect(Collectors.toCollection(ArrayList::new));
	}

	/**Set up a movement object handling dispersal from one node to another
	 * 
	 * @param from
	 * @param source
	 */
	private void addDist(int[] from, int source) {
		
		GridBox cell = null;
		for(int i = 0; i < from.length; i++) {
			int val = from[i];
			if(val < 0)
				cell = cells[-val - 1];
			else {
				cell.addExt(new int[] {from[i], from[i + 1]} );
				i++;
			}
		}

	}


	
	
	
	
		private boolean report(int year, double day) {
		cellList.forEach(GridBox::clean);
		
		if(Settings.TRACER_MODE) {
			for(GridBox cell : cellList)
				cell.tracerPrint(day, cellList);
			
		}
		else {	
				
				String timeStr = "day," + day;
				// print local diversities
				System.out.println(timeStr + ",node," + nodeNum + "," +
							Arrays.toString(cellList.stream().mapToInt(c -> c.getNumLins()).toArray()).replaceAll("(\\[|\\]| )+", "")
				
				);
				
	
				
				
				//print global diversities
				if(distributed) {
					
					////////////////// Get all lineages from each distributed cluster to calculate number of lineages globally
					////////////////// Not yet implemented for non distributed (test) configuration.
					
					            int[] allLins = cellList.stream().flatMapToInt(cell -> cell.streamLinNums()).distinct().toArray();
				             	MPI.COMM_WORLD.Barrier();
										
								if (nodeNum > 0) {
						            MPI.COMM_WORLD.Isend(allLins, 0, allLins.length, MPI.INT, 0, 1);
								}
								int[] allLins2 = null;
								if(nodeNum == 0) {
									
				
									IntStream globLins = IntStream.of(allLins);
						
			
									HashSet<Integer> msgRecieved = new HashSet<Integer>();
									for(int i = 1; i < numNodes; i++) {
										msgRecieved.add(i);
									}
									
									
									while(!msgRecieved.isEmpty()) {
										
										Iterator<Integer> msgIter = msgRecieved.iterator();
										while(msgIter.hasNext()) {
											
											
											Integer othNode = msgIter.next();
											Status probe = MPI.COMM_WORLD.Iprobe(othNode, 1);
											if(probe != null) {
												int len = probe.Get_count(MPI.INT);
												int[] from1 = new int[len];
												MPI.COMM_WORLD.Irecv(from1, 0, len, MPI.INT, othNode, 1);
												globLins = IntStream.concat(globLins, IntStream.of(from1)).distinct();
	
												msgIter.remove();
											}
										}
										
										
							
							
									}
									
									allLins2 = globLins.toArray();
								}
								
								//broadcast number of lins (all to 0)
								int[] numLins = nodeNum == 0 ? new int[] {allLins2.length} : new int[1];
								MPI.COMM_WORLD.Bcast(numLins, 0, 1, MPI.INT, 0); //receive number of lins from all
								
								//save received numlins in nl
								int nl = numLins[0];
	
								
								if(nodeNum > 0)
									allLins2 = new int[nl];
								MPI.COMM_WORLD.Bcast(allLins2, 0, nl, MPI.INT, 0); //receive actual lins from all
	
								global = allLins2.length;
								System.out.println("global," + global);
									if(Settings.TEMP_FILE != null)
										SelLineage.trimTempArray(IntStream.of(allLins2));
								
								if(Settings.STOP_AT_1) {	
									if(global == 1 || global == cellList.stream().mapToInt(c -> c.getNumLins()).min().getAsInt()  )
										return true;
								}
									
									
				} /////////////////////// End of get number of lineages globally	
				
				
				
	
		
		}
		return false;
	}








}
