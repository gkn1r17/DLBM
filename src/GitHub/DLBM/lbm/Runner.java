package lbm;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import lbm.GridCell.DistributedMovement;
import lbm.GridCell.ExternalMovement;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;
import mpi.Status;
import util.FileIO;

public class Runner {

	private static boolean distributed;
	private static List<Runner> runners;
	private static long startTime;
	private int nodeNum = 0;
	private ArrayList<Integer> otherNodes;
	private ArrayList<GridCell> cellList;
	private Collection<Cluster> clustList;
	private Collection<GridCell> extCells;
	private ArrayList<ExternalMovement> extMovs = new ArrayList<ExternalMovement>();
	private HashMap<Integer, ArrayList<DistributedMovement>> distMovs = new HashMap<Integer, ArrayList<DistributedMovement>>();
	private HashSet<Integer> srcNodes = new HashSet<Integer>();
	

	
	private GridCell[] cells;
	
	private HashMap<Integer, int[]> nonDistReceiveMsgs = null;
	private ArrayList<GridCell> smpls;
	private static int global;
	private static HashSet<Integer> modList;

	
	private static int totalLinNum;
	
	private static int numNodes = 2;
	
	public Runner(int i) {
		nodeNum = i;
	}

	public static void main(String[] args) {
		
		if(args.length > 0 && args[0].toLowerCase().equals("numnodes")) {
			numNodes = Integer.parseInt(args[1]);
			args = Arrays.asList(args).subList(2, args.length).toArray(new String[args.length - 2]);
		}
		
		startTime = System.currentTimeMillis();
		
		try {
			
			args = MPI.Init(args);

			getStartTime();
			
			distributed = true;
			runners = Arrays.asList (new Runner[] {new Runner(MPI.COMM_WORLD.Rank())});
			runAll(
					args
					);

		}catch(MPIException mpi) {
			distributed = false;
			System.out.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
			runners = new ArrayList<Runner>();
			for(int i =0; i < numNodes; i++) {
				runners.add(new Runner(i));
			}
			runAll(
					args
					);

		}
	}

	private static void getStartTime() {
		
		long[] startArr = new long[1];
		
		if(MPI.COMM_WORLD.Rank() == 0)
			startArr[0] = System.currentTimeMillis();
			
		MPI.COMM_WORLD.Bcast(startArr, 0, 1, MPI.LONG, 0);
		
		startTime = startArr[0];
		//System.out.println(startTime);
		
	}

	private static void runAll(String[] args) {
		
		try {
			
			System.out.println("Starting experiment at " + new Date());
			Settings.loadSettings(args, runners.size() == 1 ? runners.get(0).nodeNum : 0, true);
			
			for(Runner run: runners)
				run.setup();
			
			int year = (int) Settings.LOAD_YEAR;
			int hourYears = 365 * 24;
			int hour = 0;
			if(Settings.LOAD_HOUR > 0) {
				
				year = Math.floorDiv(Settings.LOAD_HOUR, hourYears);
				hour = Settings.LOAD_HOUR - (year * hourYears);
			}
			
			Runner oneRun = null;
			if(runners.size() == 1) {
				oneRun = runners.get(0);
			}
				
			
			long lastTime = System.currentTimeMillis();
			long lastTime2 = lastTime;
			
			
			int endCounter = 0;
			
			if(Settings.SAVE_DAILY)
				for(Runner run : runners)
					run.save(year,0,false);

			if(Settings.REPORT_DAILY) {
				for(Runner run : runners)
					run.report(year, 0);					
				System.out.println("day " + 0 + " year " + year + " in " + 0 + " seconds");

				
				
			}
				
					
			//writeSubsets(0, totalLinNum);
			
			for(; year < Settings.DURATION; hour += Settings.DISP_HOURS) {
				
				
				
				
				double day = ( (hour + (year * hourYears)) / 24.0);
				
				int dayOfYear = (int) (Math.floor(day) % 365);
	
					
				
				//***actual model actions
				if(oneRun != null) {
					oneRun.timeStep(dayOfYear);
					oneRun.receiveMsg();
				}
				else {
					
					
					//runners.forEach(run -> run.timeStep(dayOfYear));
					//System.out.println(dayOfYear);
					//runners.forEach(Runner::receiveMsg);

					
					
					runners.parallelStream().forEach(run -> run.timeStep(dayOfYear));
					runners.parallelStream().forEach(Runner::receiveMsg);
				}
				
				if(Settings.REPORT_HALF && dayOfYear == 365 / 2) {
					for(Runner run : runners)
						run.save(year,(365 / 2),false);
					break;
				}

				
				long currentTime = System.currentTimeMillis();
				double secondsTaken = (currentTime - lastTime) / 1000.0;
				double secondsTaken2 = (currentTime - lastTime2) / 1000.0;
				
				
				if(Settings.SAVE_DAILY)
					for(Runner run : runners)
						run.save(year,day,false);
				
				if(Settings.REPORT_DAILY) {
					for(Runner run : runners)
						run.report(year, day);					
					System.out.println("day " + day + " year " + year + " in " + secondsTaken + " seconds");
				}


				if(oneRun != null && oneRun.nodeNum == 0 && secondsTaken2 > Settings.TIME_THRESH) { //report if taken too long
					System.out.println("(update) day " + ((hour + (year * hourYears)) / 24.0) + " in " + secondsTaken + " seconds");
					lastTime2 = currentTime;
				}
				if(hour >= hourYears) {
					hour -= hourYears;
					year++;
					if(!Settings.SAVE_DAILY && year % Settings.SAVE_INTV == 0) {
						for(Runner run : runners)
							run.save(year,-1, false);
						System.out.println("day " + day + " year " + year + " in " + secondsTaken + " seconds");
						lastTime = currentTime;
					}
					if(!Settings.REPORT_DAILY && year % Settings.REPORT_INTV == 0) {
						for(Runner run : runners)
							run.report(year, day);					
						System.out.println("day " + day + " year " + year + " in " + secondsTaken + " seconds");
						lastTime = currentTime;
					}
					if(year % Settings.SUBSET_INTV == 0)
						writeSubsets(year, global);

					
				}

				
				if(Settings.EXPERIMENT_HOURS > 0 && endCounter < 2) {
						
						double actualHour = (System.currentTimeMillis() - startTime) / 1000.0 / 3600.0;
						if( (actualHour > (Settings.EXPERIMENT_HOURS * 0.99) ) ) {
							endCounter ++;
							System.out.println("printing final at " + actualHour + " hours");
							for(Runner run : runners)
								run.save(hour + (hourYears * year), -1, true);
		

						}
							
				}
				
			}
	}catch(Exception e){
		e.printStackTrace();
		MPI.COMM_WORLD.Abort(-1);
		System.exit(-1);

	}
	MPI.Finalize();
	}
	


	private static void writeSubsets(int year, int numLins) {
		if(!Settings.DO_SUBSETS)
			return;
		
		for(Runner run : runners) {
			
			if(run.smpls != null && !run.smpls.isEmpty()) {
			
				//Transact Samples
				int lineLength = (run.smpls.stream().mapToInt(GridCell::getNumLins).max().getAsInt() * 2) + 1;
				FileIO.saveSample(0,run.smpls,numLins, Settings.FILE_OUT + "SMPLs" + lineLength + "_Y" + year + "_N" + run.nodeNum + ".csv");
			}
			//Module lineages
			
			if(modList != null)
				FileIO.saveModSample(0, modList,run.cellList,numLins, Settings.FILE_OUT + "MODslinelength_Y" + year + "_N" + run.nodeNum + ".csv");

		}
		
	}

	private void report(int year, double day) {
		cellList.forEach(GridCell::clean);
		
		if(Settings.TRACER_MODE) {
			for(GridCell cell : cellList)
				cell.tracerPrint(day, cellList);
			
		}
		else {	
				
				System.out.println("year," + year + ",node," + nodeNum + "," +
							Arrays.toString(cellList.stream().mapToInt(c -> c.getNumLins()).toArray()).replaceAll("(\\[|\\]| )+", "")
				
				);
				
				
				/* 108 */           if (Settings.SEND_GLOBAL) {
					/* 109 */             int[] allLins = cellList.stream().flatMapToInt(cell -> cell.streamLinNums()).distinct().toArray();
										
											if (nodeNum > 0)
						/* 113 */               MPI.COMM_WORLD.Isend(allLins, 0, allLins.length, MPI.INT, 0, 1);
											
					/*     */             	MPI.COMM_WORLD.Barrier();
					
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
																		
																		
																		MPI.COMM_WORLD.Irecv(from1, 0, len, MPI.INT, 1, 1);
																		globLins = IntStream.concat(globLins, IntStream.of(from1)).distinct();
						
																		msgIter.remove();
																	}
																}
																
																
													
													
															}
															
															allLins2 = globLins.toArray();
											}
											int[] numLins = nodeNum == 0 ? new int[] {allLins2.length} : new int[1];
											
											MPI.COMM_WORLD.Bcast(numLins, 0, 1, MPI.INT, 0);
		
											
											int nl = numLins[0];
											
											if(nodeNum > 0)
												allLins2 = new int[nl];
												
											MPI.COMM_WORLD.Bcast(allLins2, 0, nl, MPI.INT, 0);
		
											global = allLins2.length;
											System.out.println("global," + global);
												
												if(Settings.TEMP_FILE != null)
													SelLineage.trimTempArray(IntStream.of(allLins2));
		
					} 
		}
		
	}




	private void save(int year, double day, boolean finalSave) {
		cellList.forEach(GridCell::clean);
		printAll(year, (int) day, cellList, finalSave);
	}



	private void timeStep(int dayOfYear) {
		clustList.parallelStream().forEach(clust -> clust.update(dayOfYear)); //grow
		
		//clustList.forEach(clust -> clust.update(dayOfYear)); //grow
		for(Entry<Integer, ArrayList<DistributedMovement>> dist : distMovs.entrySet())
			dispDisperse(dist.getValue(), dist.getKey(), nodeNum);
		
		for(ExternalMovement mov : extMovs)
			mov.actuallyMove();
	}


	private void setup() throws Exception {
		if(!distributed)
			nonDistReceiveMsgs = new HashMap<Integer, int[]>();

		int seed = Settings.SEED[0] == -1 ?
				new Random().nextInt() :
					Settings.SEED[nodeNum];

		System.out.println("Starting " + nodeNum + " with seed = " + seed);
		DRand rd = new DRand(seed);
		
		
		GridCell[] allCells = FileIO.loadTM(Settings.SMPL_FILE, Settings.USE_ARRAY, rd, 
				
				FileIO.loadDoubleFile(Settings.VOLS_FILE, true),
				FileIO.loadDoubleFile(Settings.TEMP_FILE, true), true
				
				);
		
		cellList = new ArrayList<GridCell>(Arrays.asList(allCells));
		
		if(Settings.DO_SUBSETS) {
		
				if(Settings.SUBSET_FILE != null) {
					HashSet<Integer> blah = FileIO.loadIntSet(Settings.SUBSET_FILE);
					smpls = blah.
												stream().
												map(e -> 
												cellList.get(e)).
												collect(Collectors.toCollection(ArrayList::new));
					smpls = smpls.stream()
								.filter(cell -> cell.clust.node == nodeNum)
								.collect(Collectors.toCollection(ArrayList::new));
		
					
					
				}
				if(Settings.MODULE_FILE != null)
					modList = FileIO.loadIntSet(Settings.MODULE_FILE);
		}
		
		GridCell.maxVol = cellList.stream().mapToDouble(c -> c.getVol()).max().getAsDouble();

		
		int beginID = 0;
		for(GridCell cell : cellList) {
			beginID = cell.initIDSizeTemp(beginID);
			if(cell.clust.node != nodeNum && cell.imADest(nodeNum))
				srcNodes.add(cell.clust.node);
		}
		
		totalLinNum = cellList.get(cellList.size() - 1).getEnd();
		
		//System.out.println(this.nodeNum + "," + srcNodes.toString());
		
		clustList = cellList.stream()
				.map(cell -> cell.getClust())
				.distinct()
				.filter(clust -> clust.node == nodeNum)
				.collect(Collectors.toCollection(ArrayList::new));
		
		
		
		cellList = clustList.stream().flatMap(clust -> clust.cells.stream()).collect(Collectors.toCollection(ArrayList::new));
		cellList.forEach(cell -> cell.externaliseMovers(extMovs, distMovs));
		
		Collections.sort(cellList);
		
		cells = new GridCell[Settings.NUM_BOXES];
		for(GridCell cell : cellList) {
			cells[cell.id] = cell;
		}
		
		if(Settings.LOAD_FILE != null)
			if(Settings.LOAD_HOUR > 0)
				FileIO.loadFinalRun(Settings.LOAD_FILE, cells, nodeNum);
			else
				FileIO.loadOldRun(Settings.LOAD_FILE, cells, nodeNum);
		else {
			for(GridCell cell : cellList)
				cell.initPop();

		}
		
	}





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
								Request blah = MPI.COMM_WORLD.Irecv(from1,0,len,MPI.INT,othNode,0);
								if(len > 1)
									addDist(from1,othNode);
								blah.Free();
								msgIter.remove();
							}
						}
						
						
			
			
					}
					
		}
		else {
			for(Entry<Integer, int[]> nonDist : nonDistReceiveMsgs.entrySet()) {
				 addDist(nonDist.getValue(), nonDist.getKey()); 
				
				
			}
		}
	}


	private static void dispDisperse(ArrayList<DistributedMovement> distM, int othNod, int thisNod) {
		TreeMap<GridCell, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridCell, TreeMap<Lineage, Integer>>();
		for(DistributedMovement mov : distM)
			mov.collateMovs(movTree);
		
		
		Builder movStrm = IntStream.builder();
		
		for(Entry<GridCell, TreeMap<Lineage, Integer>> mt : movTree.entrySet()) {
			movStrm.add(-mt.getKey().id - 1); // add the grid box id
			for(Entry<Lineage, Integer> lin : mt.getValue().entrySet()) {
				movStrm.add(lin.getKey().getId());
				movStrm.add(lin.getValue());
			}
		}
		int[] to1 = movStrm.build().toArray();
		int len = to1.length;
		if(distributed) {
			
			MPI.COMM_WORLD.Isend(len > 0 ? to1 : new int[] {0}
					,0,
					len > 0 ? len : 1,MPI.INT,othNod,0);
		}
		else {
			runners.get(othNod).nonDistReceiveMsgs.put(thisNod, to1);

		}
	}
	

	private void addDist(int[] from, int source) {
		
		GridCell cell = null;
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



	private void printAll(int year, int day, ArrayList<GridCell> cellList, boolean finalSave) {
		String filename = Settings.FILE_OUT + "z" + "_Y" + year + "_N" + nodeNum + ".csv";
		if(!finalSave) {
			int lineLength = (cellList.stream().mapToInt(GridCell::getNumLins).max().getAsInt() * 2) + 1;
			filename = Settings.FILE_OUT + "s" + lineLength + "_Y" + year + "_N" + nodeNum + ".csv";
			if(day != -1)
				filename = Settings.FILE_OUT + "s" + lineLength + "_Y" + year + "_D" + day + "_N" + nodeNum + ".csv";

		}
		FileIO.printAll(cellList, filename);
	}


}
