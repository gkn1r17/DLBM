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
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.IntStream.Builder;
import java.util.stream.Stream;

import cern.jet.random.engine.DRand;
import dispersal.parallel.DispersalHandlerDistributed;
import dispersal.parallel.DispersalHandlerParallel;
import lbm.GridBox;
import parallelization.Node;
import lbm.Settings;
import lineages.Lineage;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Request;
import mpi.Status;
import util.FileIO;
import util.MakeArtificialTM;

public class RunnerParallelization {
	
	public static final boolean debugSerial = false;


	/**if running on one computer or distributed across several*/
	static boolean distributed;
	/**0 if non distributed or the server number if distributed*/
	private static int distributedRank;
	/**"distributed" servers, if actually distributed will be list of length = 1
	 * , if pretend distributed will be longer*/
	private static List<Node> nodes;
	static GridBoxParallelization[] activeGBPars;
	

	/**
	 * Launch program.
	 * @param args parameters
	 */
	public static String[] setupParallel(int numNodes, String[] args) {

		try {//try setting up MPI distributed cluster
			
			args = MPI.Init(args);
			distributed = true;
			distributedRank = MPI.COMM_WORLD.Rank();
			nodes = Arrays.asList (new Node[] {new Node(distributedRank)});

		}catch(java.lang.NoClassDefFoundError mpi ) {
			
			//if not running on distributed cluster revert to non distributed
		    distributed = false;
		    distributedRank = 0;
			nodes = new ArrayList<Node>();
			for(int i =0; i < numNodes; i++)
				nodes.add(new Node(i));
			
			System.out.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
		}
		return(args);
	}

	

	/** Gets start time and ensures all nodes have same start time 
	 * (i.e. gets start time in node 0 and broadcasts to others)
	 * @return */
	public static long getClusterStartTime() {
		if(!distributed)
			return System.currentTimeMillis();
		long[] startArr = new long[1];
		if(amIController())
			startArr[0] = System.currentTimeMillis();
		MPI.COMM_WORLD.Bcast(startArr, 0, 1, MPI.LONG, 0);		
		return(startArr[0]);
	}
	

	/**THE MAIN MODEL ACTIONS TO PERFORM EACH TIME STEP = growth, mortality and dispersal
	 * @param tempDay 
	 */
	public static void runEcologyAndDispersal(long day, int tempTidx) {
		if(nodes.size() == 1) { //either totally serial (just one node) or distributed (one node on this server)
			nodes.get(0).runEcoDispersalLocal(tempTidx); //ecological actions + dispersal
			nodes.get(0).receiveMsg(day); //receiving dispersed individuals afterwards to avoid concurrency issues
		}
		else {
			if(debugSerial) {
				nodes.stream().forEach(nd -> nd.runEcoDispersalLocal(tempTidx)); //ecological actions + dispersal
				nodes.stream().forEach(nd -> nd.receiveMsg(day)); //receiving dispersed individuals afterwards to avoid concurrency issues
			}
			else {
				//if running multiple nodes on this machine
				nodes.parallelStream().forEach(nd -> nd.runEcoDispersalLocal(tempTidx)); //ecological actions + dispersal
				nodes.parallelStream().forEach(nd -> nd.receiveMsg(day)); //receiving dispersed individuals afterwards to avoid concurrency issues
			}
		}	
	}


	

	public static boolean amIController() {
		return distributedRank == 0;
	}
	
	public static int getRank() {
		return distributedRank;
	}
	
	public static List<Node> getNodes() {
		return nodes;
	}

	public static GridBox[] setupNodes(ArrayList<GridBox> cells, DRand rd) throws Exception {
		
		int[][] boxClustNodes = allocateClusters(cells, rd);

		
		GridBox[] activeCells = new GridBox[Settings.NUM_BOXES];
		activeGBPars = new GridBoxParallelization[Settings.NUM_BOXES];
		
		for(Node node: nodes)
			node.setup(cells, activeCells, boxClustNodes);
		
		return activeCells;
		
	}

	
	private static int[][] allocateClusters(ArrayList<GridBox> cells, DRand rd) throws FileNotFoundException {
    	Scanner clustReader = new Scanner(new File(Settings.CLUST_FILE));

		
		int[][] cellsClustNodes = new int[Settings.NUM_BOXES][3];
		
    	int i =0;
        while (clustReader.hasNextLine()){
        	String[] lineBits = clustReader.nextLine().trim().split(",");
        	
        	
        	
            int clustNum = FileIO.getIntFromString(  lineBits[0]     );
            int nodeNum = FileIO.getIntFromString(  lineBits[1]     );
            
            cellsClustNodes[i][0] = clustNum;
            cellsClustNodes[i][1] = nodeNum;
            cellsClustNodes[i][2] = rd.nextInt(); //random seed for cluster 
            									//has to be calculated here to 
            									//ensure pattern of nodes doesn't effect cluster seeds
            									//so get same results without different parallelization configurations

            i++;

            
        }
        
        clustReader.close();

        return cellsClustNodes;
	}


	public static Node getNode(int n) {
		// TODO Auto-generated method stub
		return nodes.get(n);
	}



	public static int calcGlobalDiversity(List<GridBox> activeCells) {
		int globalDiversity;
		
		if(distributed) {
			
			////////////////// Get all lineages from each distributed cluster to calculate number of lineages globally
			////////////////// Not yet implemented for non distributed (test) configuration.
			
			            int[] allLins = activeCells.stream().flatMapToInt(cell -> cell.streamLinNums()).distinct().toArray();
		             	MPI.COMM_WORLD.Barrier();
								
						if (!amIController()) {
				            MPI.COMM_WORLD.Isend(allLins, 0, allLins.length, MPI.INT, 0, 1);
						}
						int[] allLins2 = null;
						if(amIController()) {
							
		
							IntStream globLins = IntStream.of(allLins);
				
	
							HashSet<Integer> msgRecieved = new HashSet<Integer>();
							for(int i = 1; i < nodes.size(); i++) {
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
						int[] numLins = amIController() ? new int[] {allLins2.length} : new int[1];
						MPI.COMM_WORLD.Bcast(numLins, 0, 1, MPI.INT, 0); //receive number of lins from all
						
						//save received numlins in nl
						int nl = numLins[0];

						
						if(!amIController())
							allLins2 = new int[nl];
						MPI.COMM_WORLD.Bcast(allLins2, 0, nl, MPI.INT, 0); //receive actual lins from all

						globalDiversity = allLins2.length;
						
						if(Settings.TEMP_FILE != null)
							SelLineage.trimTempArray(IntStream.of(allLins2));
							
		} 
		else {
			globalDiversity = (int) activeCells.stream().flatMapToInt(cell -> cell.streamLinNums()).distinct().count();

		}
		
		return globalDiversity;
	}



	public static void closeParallel() {
		if(distributed)
			MPI.Finalize();
	}
	
	public static void abortParallel() {
		if(distributed)
			MPI.COMM_WORLD.Abort(-1);
	}



	public static int synchSeeds(int seed) {
		if(!distributed)
			return seed;
		else{
			int[] broadcastSeed = new int[1];
			if(amIController())
				broadcastSeed[0] = seed;
			MPI.COMM_WORLD.Bcast(broadcastSeed, 0, 1, MPI.INT, 0);		
			return(broadcastSeed[0]);

		}
	}


}
