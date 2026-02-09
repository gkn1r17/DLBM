/**Represents node
 * 
 * Grid Boxes are grouped into 
 * "Clusters" (on same machine) and "Nodes" (one each on separate machine)
 * Although when testing locally, even with multiple "Nodes", 
 * 		these will all in practice be on same machine
 * 
 */

package parallelization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import config.SciConfig;
import control.Runner;
import dispersal.DispersalHandler;
import dispersal.parallel.DispersalHandlerDistributed;
import dispersal.parallel.DispersalHandlerParallel;
import inputOutput.FileIO;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import transportMatrix.GridBox;
import transportMatrix.artificialTM.MakeArtificialTM;

public class Node {
	

	/**Holds long arrays equivalent to those sent by MPI but for testing multiple nodes on one machine*/
	private Stack<long[]> wouldBeMPIinMsgsButNonDist = new Stack<long[]>();

	/**index of node running on this runner*/
	private final int nodeNum;
	/**Set of neighbouring locations with a lot of dispersal between suitable to be in parallelized unit (a "cluster"). 
	 * 	parallelized to one processor core = 1 cluster. A node may have several clusters on each core*/
	private final ArrayList<Cluster> clustList = new ArrayList<Cluster>();
	/**locations on this node*/
	private final ArrayList<GridBox> boxList = new ArrayList<GridBox>();

	
	/**Handles movement between clusters on on same node*/
	private final ArrayList<DispersalHandlerParallel> extMovs = new ArrayList<DispersalHandlerParallel>();
	/**Handles movement between nodes via mpi messages*/
	private final HashMap<Integer, ArrayList<DispersalHandlerDistributed>> distMovs = new HashMap<Integer, ArrayList<DispersalHandlerDistributed>>();
	/**All nodes from which this node can expect mpi message (nodes feeding into this node)*/
	private final HashSet<Integer> srcNodes = new HashSet<Integer>();
	/**If running across multiple machines*/
	private final boolean distributed;
	/**Array of objects handling parallelization for every Grid Box*/
	private GridBoxParallelization[] activeGBPars;


	
	public Node(int i, boolean distributed) {
		this.distributed = distributed;
		nodeNum = i;
	}


	/**
	 * 
	 * @param allBoxs every GridBox
	 * @param activeBoxess GridBoxes on this Node
	 * @param boxClustNodes information about GridBoxes in format [clusterID][nodeID][randomSeed]
	 * @throws Exception
	 */
	public void setup(ArrayList<GridBox> allBoxs,  GridBox[] activeBoxess, int[][] boxClustNodes) throws Exception {
		
		
		activeGBPars = new GridBoxParallelization[Runner.settings.NUM_BOXES];
		
		HashMap<Integer, Cluster> clustsFound = new HashMap<Integer, Cluster>();	
		
			
		for(GridBox box : allBoxs) {
			int idFrom = box.id;
			int clustFrom = boxClustNodes[idFrom][0];
			int nodeFrom = boxClustNodes[idFrom][1];
			
			ArrayList<DispersalHandler> movers = box.getMovers();
			
			if(nodeFrom == nodeNum) { //if current box belongs to this node
				activeBoxess[idFrom] = box;
				boxList.add(box);
				
				//allocate Cluster, create if needed
				Cluster clust = clustsFound.get(clustFrom);
				if(clust == null) {
					clust = new Cluster(boxClustNodes[idFrom][2]);
					clustList.add(clust);
					clustsFound.put(clustFrom, clust);
				}
				clust.addGridBox(box);
				
				activeGBPars[idFrom] = box.getParallel();

				
				//setup dispersal managers so know dispersing in distributed/parallel way if needed
				
				for(int i =0; i < movers.size(); i++) {
					DispersalHandler mov = movers.get(i);
					
					int idTo = mov.getDest().id;
					int clustTo = boxClustNodes[idTo][0];
					int nodeTo = boxClustNodes[idTo][1];
					
					if(nodeTo != nodeNum) {
						DispersalHandlerDistributed dMov = new DispersalHandlerDistributed(mov);
						//add distributed mover in list for correct node
							distMovs.compute(nodeTo, (k, v) -> {
										if(v == null)
											v = new ArrayList<DispersalHandlerDistributed>(); //create list for node if not yet created
										v.add(dMov);
										return(v);	
									});
						movers.set(i, dMov);
					}
					else if(clustTo != clustFrom) {
						DispersalHandlerParallel pMov = new DispersalHandlerParallel(mov);
						extMovs.add(pMov);
						movers.set(i, pMov);
					}
				}
			}
			else { //if current box does not belong to this node
				for(DispersalHandler mov : movers) {
					int nodeTo = boxClustNodes[mov.getDest().id][1];
					if(nodeTo == nodeNum) {
						srcNodes.add(nodeFrom);
						break;
					}
						
				}

				
			}
			
		}
			
		
		Collections.sort(boxList);

		
	}

	


	

	/** Ecological actions + dispersal
	 * 
	 * @param dayOfYear
	 * @param hour 
	 */
	void runEcoDispersalLocal(int dayOfYear, long hour) {
		
		Stream<Cluster> stream = RunnerParallelization.debugSerial ?
										clustList.stream() :
										clustList.parallelStream();
		
		stream.forEach(clust -> {
				try {
					clust.update(dayOfYear, hour);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}); //grow + die
		
		//disperse
		//send MPI messages for dispersal to other nodes
		if(distributed) {
			for(Entry<Integer, ArrayList<DispersalHandlerDistributed>> dist : distMovs.entrySet())
				distributedDisperse(dist.getValue(), dist.getKey(), nodeNum);
		}
		//move individuals to other clusters on this node
		for(DispersalHandlerParallel mov : extMovs)
			mov.actuallyMove(); 
		//
	}
	


	/** 
	 * 
	 */
	void receiveMsg() {
		
		
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
									decompileMPImessage(from1);
								req.Free();
								msgIter.remove();
							}
						}

			
					}
					
		}
		else { //non distributed
			//for(Entry<Integer, long[]> WOW  : nonDistReceiveMsgs.entrySet()) {
				//decompileMPImessage(WOW.getValue()); 
				
			//}
			while(!wouldBeMPIinMsgsButNonDist.isEmpty())
				decompileMPImessageTest(wouldBeMPIinMsgsButNonDist.pop()); 
		}
	}



	/**Broadcast a list of all lineages / quantities dispersed to one destination which need to be handled by MPI 
	 * as destination is on another node
	 * 
	 * @param distM list of movement objects holding all lineages to move and quantity of individuals within which to move
	 * @param othNod destination (location ID) 
	 * @param thisNod source (location I)
	 */
	private void distributedDisperse(ArrayList<DispersalHandlerDistributed> distM, int othNod, int thisNod) {
		
		int[] to1 = compileMPImessage(distM);
		int len = to1.length;
		
		//broadcast
		MPI.COMM_WORLD.Isend(len > 0 ? to1 : new int[] {0}
					,0,
					len > 0 ? len : 1,MPI.INT,othNod,0);

	
		sendMutants();
	}
	
	
	
	private void sendMutants() {
		// TODO Auto-generated method stub
		
	}


	public void fakeDistributedDisperse(ArrayList<DispersalHandlerDistributed> distM, Node othNode) {
		long[] to1 = compileMPImessageTest(distM);
		//othNode.nonDistReceiveMsgs.put(nodeNum, to1);
		othNode.wouldBeMPIinMsgsButNonDist.push(to1);
	}
	
	/**Converts list of all individuals to be dispersed to grid boxes on other nodes 
	 * into int array suitable for sending via MPI messages.
	 * List consists of -[dest grid box id],[list of lineages]
	 * [list of individuals] = [lineage id],[number of indidivuals dispersed]
	 * lineage ID is converted into int due to problems sending messages of type "long"
	 * TODO not sure why it was a problem: could investigate in future   
	 * --
	 * For decoding see decompileMPImessage(...) */
	private int[] compileMPImessage(ArrayList<DispersalHandlerDistributed> distM) {
		TreeMap<GridBox, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridBox, TreeMap<Lineage, Integer>>();
		for(DispersalHandlerDistributed mov : distM)
			mov.collateMovs(movTree);
		
		//convert list of movement objects into format below for broadcasting:
				//[-locationID, lin1ID, lin1Quant, Lin2ID, lin2Quant]
		IntStream.Builder movStrm = IntStream.builder();
		
		for(Entry<GridBox, TreeMap<Lineage, Integer>> mt : movTree.entrySet()) {
			movStrm.add(-mt.getKey().id - 2); // add the location id in negative so indicates new location (i.e. not confused with lineage ID)
			for(Entry<Lineage, Integer> lin : mt.getValue().entrySet()) {
				
				//********** add ID 
				long id = lin.getKey().getId();
				
				//convert to int so int message can send longs
				if(id > Runner.settings.CTRL.MUTANT_MAX_OFFSET) { 
					movStrm.add(-1);
					movStrm.add( (int) Math.floor(id / Runner.settings.CTRL.MUTANT_MAX_OFFSET)); 
					id = id % Runner.settings.CTRL.MUTANT_MAX_OFFSET; 
				}
				movStrm.add((int) id); //add lineage id
				
				//********** add population size
				movStrm.add(lin.getValue()); //add number of individuals moved
				
				//********** add birth hour
				if(Runner.settings.CTRL.SAVE_BIRTHHOUR) {
					long birthHour = lin.getKey().getBirthHour();
					if(birthHour > Integer.MAX_VALUE) { 
						movStrm.add(-1);
						movStrm.add( (int) Math.floor(birthHour / Runner.settings.CTRL.MUTANT_MAX_OFFSET)); 
						birthHour = birthHour % Runner.settings.CTRL.MUTANT_MAX_OFFSET; 
					}
					movStrm.add((int) birthHour); //add lineage id
				}
			}
		}
		
		return movStrm.build().toArray();
	}
	
	/**Should behave exactly the same as compileMPImessage except without complicated conversion of long into ints
	 * Used when testing multiple "nodes" locally
	 * ---
	 * For decoding see decompileMPImessageTest(...) 
	 * @param settings */
	private long[] compileMPImessageTest(ArrayList<DispersalHandlerDistributed> distM) {
		TreeMap<GridBox, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridBox, TreeMap<Lineage, Integer>>();
		for(DispersalHandlerDistributed mov : distM)
			mov.collateMovs(movTree);
		
		//convert list of movement objects into format below for broadcasting:
				//[-locationID, lin1ID, lin1Quant, Lin2ID, lin2Quant]
		LongStream.Builder movStrm = LongStream.builder();
		
		for(Entry<GridBox, TreeMap<Lineage, Integer>> mt : movTree.entrySet()) {
			movStrm.add(-mt.getKey().id - 1); // add the location id in negative so indicates new location (i.e. not confused with lineage ID)
			for(Entry<Lineage, Integer> lin : mt.getValue().entrySet()) {
				movStrm.add(lin.getKey().getId()); //add lineage id
				movStrm.add(lin.getValue()); //add number of individuals moved
				if(Runner.settings.CTRL.SAVE_BIRTHHOUR)
					movStrm.add(lin.getKey().getBirthHour());
			}
		}
		
		return movStrm.build().toArray();
	}
	


		
	/**decoding messages sent by compileMPImessage(...)
	 * Set up a movement object handling dispersal from one node to another
	 * 
	 * @param from1
	 * @param source
	 */
	private void decompileMPImessage(int[] from1) {
		GridBoxParallelization box = null;
		int nextLongEncoded = 0;
		for(int i = 0; i < from1.length; i++) {
			long val = from1[i];
			if(val == -1) { //long encoded as int array
				nextLongEncoded = from1[i + 1];
				i++;
			}
			else if(val < 0) //start of new box
				box = activeGBPars[(int) (-val - 2)];
			else { //[i] = lin id, [i + 1] = num moving
				
				if(Runner.settings.CTRL.SAVE_BIRTHHOUR) {
					box.addExt(new long[] {
							val + (Runner.settings.CTRL.MUTANT_MAX_OFFSET * nextLongEncoded) , //ID  may be converted long -> int
							from1[i + 1] ,										   //Number of individuals
																				  //birth hour, 
																						//may also be converted long -> int
							(from1[i + 2] == -1 ? from1[i + 4] + (Integer.MAX_VALUE * from1[i + 3]) : from1[i + 2]) ,
							} );									   
					nextLongEncoded = 0;
					i+= from1[i + 2] == -1 ? 4 : 2;
				}
				else {
					
					box.addExt(new long[] {val + (Runner.settings.CTRL.MUTANT_MAX_OFFSET * nextLongEncoded) , 
											from1[i + 1]} );
					nextLongEncoded = 0;
					i++;
				}
			}
		}

	}
	
	
	/**	/**decoding messages sent by compileMPImessageTest(...) 
	 * USED IN NON DISTRIBUTED (TEST) IMPLEMENTATIONS FOR COMPLICATED decompileMPImessage(int[] from1) 
	 * 																		TO BE VALIDATED AGAINST
	 * Set up a movement object handling dispersal from one node to another
	 * 
	 * @param from1
	 * @param source
	 */
	private void decompileMPImessageTest(long[] from1) {
		GridBoxParallelization box = null;
		for(int i = 0; i < from1.length; i++) {
			long val = from1[i];
			if(val < 0) //start of new box
				box = activeGBPars[(int) (-val - 1)];
			else { //[i] = lin id, [i + 1] = num moving
				if(Runner.settings.CTRL.SAVE_BIRTHHOUR) {
					box.addExt(new long[] {val, from1[i + 1], from1[i + 2]} );
					i++;
				}	
				else
					box.addExt(new long[] {val, from1[i + 1]} );
				i++;
				
			}
		}

	}


	public HashMap<Integer, ArrayList<DispersalHandlerDistributed>> getDistMovs() {
		return distMovs;
	}






	



	






}
