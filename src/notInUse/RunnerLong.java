package notInUse;

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
import java.util.stream.LongStream;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
//import lbm.GridCell.DistributedMovement;
//import lbm.GridCell.ExternalMovement;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import util.FileIO;

public class RunnerLong {

//	private static boolean distributed;
//	private static List<RunnerLong> runners;
//	private int nodeNum = 0;
//	private ArrayList<Integer> otherNodes;
//	private ArrayList<GridCell> cellList;
//	private Collection<Cluster> clustList;
//	private Collection<GridCell> extCells;
//	private ArrayList<ExternalMovement> extMovs = new ArrayList<ExternalMovement>();
//	private HashMap<Integer, ArrayList<DistributedMovement>> distMovs = new HashMap<Integer, ArrayList<DistributedMovement>>();
//	private HashSet<Integer> srcNodes = new HashSet<Integer>();
//	private GridCell[] cells;
//	private static int global;
//
//	private HashMap<Integer, int[]> nonDistReceiveMsgs = null;
//	
//	private static int numNodes = 2;
//	
//	public RunnerLong(int i) {
//		nodeNum = i;
//	}
//
//	public static void main(String[] args) {
//		
//		if(args[0].toLowerCase().equals("numnodes")) {
//			numNodes = Integer.parseInt(args[0]);
//			args = Arrays.asList(args).subList(2, args.length).toArray(args);
//		}
//		
//		try {
//			args = MPI.Init(args);
//			distributed = true;
//			runners = Arrays.asList (new RunnerLong[] {new RunnerLong(MPI.COMM_WORLD.Rank())});
//			runAll(
//					args
//					);
//
//		}catch(NoClassDefFoundError ncdf) {
//			distributed = false;
//			System.out.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
//			runners = new ArrayList<RunnerLong>();
//			for(int i =0; i < numNodes; i++) {
//				runners.add(new RunnerLong(i));
//			}
//			runAll(
//					args
//					);
//
//		}
//	}
//
//	private static void runAll(String[] args) {
//		
//		try {
//			
//			System.out.println("Starting experiment at " + new Date());
//			Settings.loadSettings(args, runners.size() == 1 ? runners.get(0).nodeNum : 0, true);
//			
//			
//			for(RunnerLong run: runners)
//				run.setup();
//			
//			int year = (int) Settings.LOAD_YEAR;
//			int hourYears = 365 * 24;
//			int hour = 0;
//
//			
//			RunnerLong oneRun = null;
//			if(runners.size() == 1) {
//				oneRun = runners.get(0);
//			}
//				
//			
//			long lastTime = System.currentTimeMillis();
//			long lastTime2 = lastTime;
//			
//			double saveCounter = 0;
//			double reportCounter = 0;
//			
//			for(; year < Settings.DURATION; hour += Settings.DISP_HOURS) {	
//				
//				double day = ( (hour + (year * hourYears)) / 24.0);
//				
//				int dayOfYear = (int) (Math.floor(day) % 365);
//					
//				
//				//***actual model actions
//				if(oneRun != null) {
//					oneRun.timeStep(dayOfYear);
//					oneRun.receiveMsg();
//				}
//				else {
//					runners.parallelStream().forEach(run -> run.timeStep(dayOfYear));
//					runners.parallelStream().forEach(RunnerLong::receiveMsg);
//				}
//				
//				long currentTime = System.currentTimeMillis();
//				double secondsTaken = (currentTime - lastTime) / 1000.0;
//				double secondsTaken2 = (currentTime - lastTime2) / 1000.0;
//
//				if(oneRun != null && oneRun.nodeNum == 0 && secondsTaken2 > Settings.TIME_THRESH) { //report if taken too long
//					System.out.println("(update) day " + ((hour + (year * hourYears)) / 24.0) + " in " + secondsTaken + " seconds");
//					lastTime2 = currentTime;
//				}
//				if(hour >= hourYears) {
//					hour -= hourYears;
//					year++;
//				}
//				
//				saveCounter += Settings.DISP_HOURS;
//				if(saveCounter / hourYears > Settings.SAVE_INTV) {
//					for(RunnerLong run : runners)
//						run.save(year);
//					saveCounter -= (hourYears * Settings.SAVE_INTV);
//					System.out.println("day " + day + " year " + year + " in " + secondsTaken + " seconds");
//					lastTime = currentTime;
//				}
//					
//				reportCounter += Settings.DISP_HOURS;
//				if(reportCounter / hourYears > Settings.REPORT_INTV) {
//					for(RunnerLong run : runners)
//						run.report(year);					
//					reportCounter -= (hourYears * Settings.REPORT_INTV);
//					System.out.println("day " + day + " year " + year + " in " + secondsTaken + " seconds");
//					lastTime = currentTime;
//				}
//
//
//			}
//	}catch(Exception e){
//		e.printStackTrace();
//		MPI.COMM_WORLD.Abort(-1);
//		System.exit(-1);
//
//	}
//	MPI.Finalize();
//	}
//	
//	private void report(int year) {
//		cellList.forEach(GridCell::clean);
//		
//		System.out.println("year," + year + ",node," + nodeNum + "," +
//					Arrays.toString(cellList.stream().mapToInt(c -> c.getNumLins()).toArray()).replaceAll("(\\[|\\]| )+", "")
//		
//		);
//		
//		
//		/* 108 */           if (Settings.SEND_GLOBAL) {
//																	/* 109 */             int[] allLins = cellList.stream().flatMapToInt(cell -> cell.streamLinNums()).distinct().toArray();
//																	
//																	if (nodeNum > 0)
//														/* 113 */               MPI.COMM_WORLD.Isend(allLins, 0, allLins.length, MPI.INT, 0, 1);
//																	
//														/*     */             	MPI.COMM_WORLD.Barrier();
//														
//																	int[] allLins2 = null;
//																	if(nodeNum == 0) {
//																					IntStream globLins = IntStream.of(allLins);
//															
//																					HashSet<Integer> msgRecieved = new HashSet<Integer>();
//																					for(int i = 1; i < numNodes; i++) {
//																						msgRecieved.add(i);
//																					}
//																					
//																					
//																					while(!msgRecieved.isEmpty()) {
//																						
//																						Iterator<Integer> msgIter = msgRecieved.iterator();
//																						while(msgIter.hasNext()) {
//																							Integer othNode = msgIter.next();
//																							Status probe = MPI.COMM_WORLD.Iprobe(othNode, 1);
//																							if(probe != null) {
//																								int len = probe.Get_count(MPI.INT);
//																								int[] from1 = new int[len];
//																								
//																								
//																								MPI.COMM_WORLD.Irecv(from1, 0, len, MPI.INT, 1, 1);
//																								globLins = IntStream.concat(globLins, IntStream.of(from1)).distinct();
//														
//																								msgIter.remove();
//																							}
//																						}
//																						
//																						
//																			
//																			
//																					}
//																					
//																					allLins2 = globLins.toArray();
//																	}
//																	int[] numLins = nodeNum == 0 ? new int[] {allLins2.length} : new int[1];
//																	
//																	MPI.COMM_WORLD.Bcast(numLins, 0, 1, MPI.INT, 0);
//														
//																	
//																	int nl = numLins[0];
//																	
//																	if(nodeNum > 0)
//																		allLins2 = new int[nl];
//																		
//																	MPI.COMM_WORLD.Bcast(allLins2, 0, nl, MPI.INT, 0);									
//									global = allLins2.length;
//									System.out.println("global," + global);
//										
//										if(Settings.TEMP_FILE != null)
//											SelLineage.trimTempArray(IntStream.of(allLins2));
//			/*     */             } 
//		
//		
//	}
//
//
//
//
//	private void save(int year) {
//		cellList.forEach(GridCell::clean);
//		printAll(year, cellList);
//	}
//
//
//
//
//	private void timeStep(int dayOfYear) {
//		clustList.parallelStream().forEach(clust -> clust.update(dayOfYear)); //grow
//		for(Entry<Integer, ArrayList<DistributedMovement>> dist : distMovs.entrySet())
//			dispDisperse(dist.getValue(), dist.getKey(), nodeNum);
//		
//		for(ExternalMovement mov : extMovs)
//			mov.actuallyMove();
//	}
//
//
//	private void setup() throws Exception {
//		if(!distributed)
//			nonDistReceiveMsgs = new HashMap<Integer, int[]>();
//
//		
//		
//			int seed = Settings.SEED[0] == -1 ?
//					new Random().nextInt() :
//						Settings.SEED[nodeNum];
//
//		System.out.println("Starting with seed = " + seed);
//		DRand rd = new DRand(seed);
//		
//		
//		GridCell[] allCells = FileIO.loadTM(Settings.SMPL_FILE, Settings.USE_ARRAY, rd, 
//				
//				FileIO.loadDoubleFile(Settings.VOLS_FILE),
//				FileIO.loadDoubleFile(Settings.TEMP_FILE)
//				
//				);
//		
//		cellList = new ArrayList<GridCell>(Arrays.asList(allCells));
//		
//		
//		
//		GridCell.maxVol = cellList.stream().mapToDouble(c -> c.getVol()).max().getAsDouble();
//		
//		int beginID = 0;
//		for(GridCell cell : cellList) {
//			beginID = cell.initIDSizeTemp(beginID);
//			if(cell.clust.node != nodeNum && cell.imADest(nodeNum))
//				srcNodes.add(cell.clust.node);
//		}		
//		
//
//		System.out.println(this.nodeNum + "," + srcNodes.toString());
//		
//		clustList = cellList.stream()
//				.map(cell -> cell.getClust())
//				.distinct()
//				.filter(clust -> clust.node == nodeNum)
//				.collect(Collectors.toCollection(ArrayList::new));
//		
//		
//		
//		cellList = clustList.stream().flatMap(clust -> clust.cells.stream()).collect(Collectors.toCollection(ArrayList::new));
//		cellList.forEach(cell -> cell.externaliseMovers(extMovs, distMovs));
//		
//		Collections.sort(cellList);
//		
//		cells = new GridCell[Settings.NUM_BOXES];
//		for(GridCell cell : cellList) {
//			cells[cell.id] = cell;
//		}
//		
//		if(Settings.LOAD_FILE != null)
//			FileIO.loadOldRun(Settings.LOAD_FILE, cells, nodeNum);
//		else {
//			for(GridCell cell : cellList)
//				cell.initPop();
//
//		}		
//	}
//
//
//
//
//
//	private void receiveMsg() {
//		
//		
//		if(distributed) {
//			
//			
//			
//					MPI.COMM_WORLD.Barrier();
//			
//			
//					HashSet<Integer> msgRecieved = new HashSet<Integer>(srcNodes);
//					
//					while(!msgRecieved.isEmpty()) {
//						
//						Iterator<Integer> msgIter = msgRecieved.iterator();
//						while(msgIter.hasNext()) {
//							Integer othNode = msgIter.next();
//							Status probe = MPI.COMM_WORLD.Iprobe(othNode, 0);
//							if(probe != null) {
//								int len = probe.Get_count(MPI.INT);
//								long[] from1 = new long[len];
//								Request blah = MPI.COMM_WORLD.Irecv(from1,0,len,MPI.LONG,othNode,0);
//								if(len > 1)
//									addDist(from1,othNode);
//								blah.Free();
//								msgIter.remove();
//							}
//						}
//						
//						
//			
//			
//					}
//					
//		}
//		else {
//			//for(Entry<Integer, int[]> nonDist : nonDistReceiveMsgs.entrySet()) {
//				 //addDist(nonDist.getValue(), nonDist.getKey()); 
//				
//				
//			//}
//		}
//	}
//
//
//	private static void dispDisperse(ArrayList<DistributedMovement> distM, int othNod, int thisNod) {
//		TreeMap<GridCell, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridCell, TreeMap<Lineage, Integer>>();
//		for(DistributedMovement mov : distM)
//			mov.collateMovs(movTree);
//		
//		
//		LongStream.Builder movStrm = LongStream.builder();
//		
//		for(Entry<GridCell, TreeMap<Lineage, Integer>> mt : movTree.entrySet()) {
//			
//			movStrm.add(-mt.getKey().id - 1); // add the grid box id
//			for(Entry<Lineage, Integer> lin : mt.getValue().entrySet()) {
//				long val = lin.getKey().getId();
//				val += Integer.MAX_VALUE;
//				System.out.println(lin.getKey().getId() + "," + val  );
//				movStrm.add(val);
//				movStrm.add(lin.getValue());
//			}
//			
//
//		}
//		long[] to1 = movStrm.build().toArray();
//		int len = to1.length;
//		if(distributed) {
//			
//			MPI.COMM_WORLD.Isend(len > 0 ? to1 : new long[] {0}
//					,0,
//					len > 0 ? len : 1,MPI.LONG,othNod,0);
//		}
//		else {
//			//runners.get(othNod).nonDistReceiveMsgs.put(thisNod, to1);
//		}
//	}
//	
//
//	private void addDist(long[] from1, int source) {
//		GridCell cell = null;
//		System.out.println(Arrays.toString(from1));
//		for(int i = 0; i < from1.length; i++) {
//			long val = from1[i];
//			if(val < 0)
//				cell = cells[-(int)val - 1];
//			else {
//				cell.addExt(new int[] {(int)(from1[i] - Integer.MAX_VALUE), (int)from1[i + 1]} );
//				i++;
//			}
//		}
//
//		
//	}
//
//
//
//	private void printAll(int year, ArrayList<GridCell> cellList) {
//		
//		
//		int lineLength = (cellList.stream().mapToInt(GridCell::getNumLins).max().getAsInt() * 2) + 1;
//		FileIO.printAll(cellList, Settings.FILE_OUT + "s" + lineLength + "_Y" + year + "_N" + nodeNum + ".csv");
//	}

}
