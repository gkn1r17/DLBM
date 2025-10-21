/**
 * Handles overall running of simulation
 */

package parallelization;

import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import dispersal.DispersalHandler;
import dispersal.parallel.DispersalHandlerDistributed;
import dispersal.parallel.DispersalHandlerParallel;
import lbm.GridBox;
import lbm.Settings;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import parallelization.Cluster;
import util.FileIO;
//import util.MakeArtificialTM;
import util.MakeArtificialTM;

public class Node {
	
	
	private HashMap<Integer, int[]> nonDistReceiveMsgs = null;
	
	/**index of node running on this runner*/
	private final int nodeNum;
	/**Set of neighbouring locations with a lot of dispersal between suitable to be in parallelized unit (a "cluster"). 
	 * 	parallelized to one processor core = 1 cluster. A node may have several clusters on each core*/
	private final ArrayList<Cluster> clustList = new ArrayList<Cluster>();
	/**locations on this node*/
	private final ArrayList<GridBox> cellList = new ArrayList<GridBox>();

	
	/**handles dispersa*/
	private final ArrayList<DispersalHandlerParallel> extMovs = new ArrayList<DispersalHandlerParallel>();
	final HashMap<Integer, ArrayList<DispersalHandlerDistributed>> distMovs = new HashMap<Integer, ArrayList<DispersalHandlerDistributed>>();
	final HashSet<Integer> srcNodes = new HashSet<Integer>();

	
	public Node(int i) {
		nodeNum = i;
	}
	
	public int getNodeNode() {
		return nodeNum;
	}
	
	/** Setup this runner (will only be called once for one "Runner" object if running distributed or only one node, 
	 * 					will only be called for separate runners if testing multiple runners on this computer)
	 * @param cells 
	 * @param distributed 
	 * @param boxClustNodes 
	 * 
	 * @throws Exception
	 */
	public void setup(ArrayList<GridBox> allCells,  GridBox[] activeCells, int[][] boxClustNodes) throws Exception {
		
		if(!RunnerParallelization.distributed)
			nonDistReceiveMsgs = new HashMap<Integer, int[]>();
		

		HashMap<Integer, Cluster> clustsFound = new HashMap<Integer, Cluster>();	
			
		for(GridBox box : allCells) {
			int idFrom = box.id;
			int clustFrom = boxClustNodes[idFrom][0];
			int nodeFrom = boxClustNodes[idFrom][1];
			
			ArrayList<DispersalHandler> movers = box.getMovers();
			
			if(nodeFrom == nodeNum) { //if current cell belongs to this node
				activeCells[idFrom] = box;
				cellList.add(box);
				
				//allocate Cluster, create if needed
				Cluster clust = clustsFound.get(clustFrom);
				if(clust == null) {
					clust = new Cluster(boxClustNodes[idFrom][2]);
					clustList.add(clust);
					clustsFound.put(clustFrom, clust);
				}
				clust.addGridCell(box);
				
				RunnerParallelization.activeGBPars[idFrom] = box.getParallel();

				
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
			else { //if current cell does not belong to this node
				for(DispersalHandler mov : movers) {
					int nodeTo = boxClustNodes[mov.getDest().id][1];
					if(nodeTo == nodeNum) {
						srcNodes.add(nodeFrom);
						break;
					}
						
				}

				
			}
			
		}
			
		
		Collections.sort(cellList);

		
	}

	


	

	/** Ecological actions + dispersal
	 * 
	 * @param dayOfYear
	 */
	void runEcoDispersalLocal(int dayOfYear) {
		
		Stream<Cluster> stream = RunnerParallelization.debugSerial ?
										clustList.stream() :
										clustList.parallelStream();
		
		
		stream.forEach(clust -> {
				try {
					clust.update(dayOfYear);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}); //grow + die
		
		//disperse
		for(Entry<Integer, ArrayList<DispersalHandlerDistributed>> dist : distMovs.entrySet())
			dispDisperse(dist.getValue(), dist.getKey(), nodeNum);
		for(DispersalHandlerParallel mov : extMovs)
			mov.actuallyMove();
		//
	}
	










	/** 
	 * 
	 */
	void receiveMsg(long day) {
		
		
		if(RunnerParallelization.distributed) {
			
			
			
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
									addDist(from1, day);
								req.Free();
								msgIter.remove();
							}
						}

			
					}
					
		}
		else { //non distributed
			for(Entry<Integer, int[]> nonDist : nonDistReceiveMsgs.entrySet()) {
				 addDist(nonDist.getValue(), day); 
				
				
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
	private void dispDisperse(ArrayList<DispersalHandlerDistributed> distM, int othNod, int thisNod) {
		TreeMap<GridBox, TreeMap<Lineage, Integer>> movTree = new TreeMap<GridBox, TreeMap<Lineage, Integer>>();
		for(DispersalHandlerDistributed mov : distM)
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
		if(RunnerParallelization.distributed) {
			
			MPI.COMM_WORLD.Isend(len > 0 ? to1 : new int[] {0}
					,0,
					len > 0 ? len : 1,MPI.INT,othNod,0);
		}
		else {
			RunnerParallelization.getNode(othNod).nonDistReceiveMsgs.put(thisNod, to1);

		}
	}
	


	/**Set up a movement object handling dispersal from one node to another
	 * 
	 * @param from
	 * @param source
	 */
	private void addDist(int[] from, long day) {
		GridBoxParallelization cell = null;
		for(int i = 0; i < from.length; i++) {
			int val = from[i];
			if(val < 0) //start of new cell
				cell = RunnerParallelization.activeGBPars[-val - 1];
			else { //[i] = lin id, [i + 1] = num moving 
				cell.addExt(new int[] {from[i], from[i + 1]} );
				i++;
			}
		}

	}


	public ArrayList<GridBox> getLocs() {
		return cellList;
	}








}
