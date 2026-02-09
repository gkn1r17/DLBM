/**Singleton class managing MPI interactions and other general aspects of parallelisation
 * 
 */

package parallelization;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import cern.jet.random.engine.DRand;
import control.Runner;
import dispersal.parallel.DispersalHandlerDistributed;
import inputOutput.FileIO;
import lineages.SelLineage;
import mpi.MPI;
import mpi.Status;
import transportMatrix.GridBox;

public class RunnerParallelization {
	
	public static final boolean debugSerial = false;


	
	/**"distributed" servers, if actually distributed will be list of length = 1
	 * , if pretend distributed will be longer*/
	private List<Node> nodes;
	private boolean distributed;
	private int distributedRank;
	private int numDistributedNodes = -1;
	
	/**
	 * Launch program.
	 * @param args parameters
	 */
	public String[] setupParallel(int numNodes, String[] args) {
		

		try {//try setting up MPI distributed cluster
			setDistributed(true);
			args = MPI.Init(args);
			distributedRank = MPI.COMM_WORLD.Rank();
			nodes = Arrays.asList (new Node[] {new Node(distributedRank, isDistributed())});
			numDistributedNodes = numNodes;
		}catch(java.lang.NoClassDefFoundError mpi ) {
			setDistributed(false);
			//if not running on distributed cluster revert to non distributed
			nodes = new ArrayList<Node>();
			for(int i =0; i < numNodes; i++)
				nodes.add(new Node(i, false));
			
			System.err.println("WARNING: Couldn't setup distributed FastMPJ network - are you testing locally?");
		}
		return(args);
	}
	
	/** Gets start time and ensures all nodes have same start time 
	 * (i.e. gets start time in node 0 and broadcasts to others)
	 * @return */
	public long getClusterStartTime() {
		if(!isDistributed())
			return System.currentTimeMillis();
		long[] startArr = new long[1];
		if(amIController())
			startArr[0] = System.currentTimeMillis();
		MPI.COMM_WORLD.Bcast(startArr, 0, 1, MPI.LONG, 0);		
		return(startArr[0]);
	}
	

	/**THE MAIN MODEL ACTIONS TO PERFORM EACH TIME STEP = growth, mortality and dispersal
	 * @param hour 
	 * @param tempDay 
	 */
	public void runEcologyAndDispersal(long day, int tempTidx,  long hour) {
		if(nodes.size() == 1) { //either totally serial (just one node) or distributed (one node on this server)
			nodes.get(0).runEcoDispersalLocal(tempTidx,  hour); //ecological actions + dispersal
			nodes.get(0).receiveMsg(); //receiving dispersed individuals afterwards to avoid concurrency issues
		}
		else {
			if(debugSerial) {
				nodes.stream().forEach(nd -> nd.runEcoDispersalLocal(tempTidx,  hour)); //ecological actions + dispersal
				for(Node nd : nodes) {
					for(Entry<Integer, ArrayList<DispersalHandlerDistributed>> dist : nd.getDistMovs().entrySet()) {
						int nodeID = dist.getKey();
						nd.fakeDistributedDisperse(dist.getValue(), nodes.get(nodeID));
					}
				}
				nodes.stream().forEach(nd -> nd.receiveMsg()); //receiving dispersed individuals afterwards to avoid concurrency issues
			}
			else {
				//if running multiple nodes on this machine
				nodes.parallelStream().forEach(nd -> nd.runEcoDispersalLocal(tempTidx,  hour)); //ecological actions + dispersal
				
				//this works the same as distributedDisperse in runEcoDispersalLocal(... ) but 
									//has to be in serial
									//note: the multiple "nodes" one machine setup here is not particularly optimized
									//it exists mostly to test. If you really need to run on one machine there is no benefit in not just having everything on one node
				for(Node nd : nodes) {
					for(Entry<Integer, ArrayList<DispersalHandlerDistributed>> dist : nd.getDistMovs().entrySet()) {
						int nodeID = dist.getKey();
						nd.fakeDistributedDisperse(dist.getValue(), nodes.get(nodeID));
					}
				}
				
				nodes.parallelStream().forEach(nd -> nd.receiveMsg()); //receiving dispersed individuals afterwards to avoid concurrency issues
			}
		}	
	}


	public boolean amIController() {
		return distributedRank == 0;
	}


	public GridBox[] setupNodes(ArrayList<GridBox> boxs, DRand rd) throws Exception {
		
		int[][] boxClustNodes = allocateClusters(boxs, rd);

		
		GridBox[] activeboxs = new GridBox[Runner.settings.NUM_BOXES];
		
		for(Node node: nodes)
			node.setup(boxs, activeboxs, boxClustNodes);
		
		return activeboxs;
		
	}

	
	private static int[][] allocateClusters(ArrayList<GridBox> boxs, DRand rd) throws FileNotFoundException {
    	Scanner clustReader = new Scanner(new File(Runner.settings.CTRL.CLUST_FILE));

		
		int[][] boxsClustNodes = new int[Runner.settings.NUM_BOXES][3];
		
    	int i =0;
        while (clustReader.hasNextLine()){
        	String[] lineBits = clustReader.nextLine().trim().split(",");
        	
        	
        	
            int clustNum = (int) FileIO.getLongFromString(  lineBits[0]     );
            int nodeNum = (int) FileIO.getLongFromString(  lineBits[1]     );
            
            boxsClustNodes[i][0] = clustNum;
            boxsClustNodes[i][1] = nodeNum;
            boxsClustNodes[i][2] = rd.nextInt(); //random seed for cluster 
            									//has to be calculated here to 
            									//ensure pattern of nodes doesn't effect cluster seeds
            									//so get same results without different parallelization configurations

            i++;

            
        }
        
        clustReader.close();

        return boxsClustNodes;
	}



	public int calcGlobalDiversity(List<GridBox> activeboxs) {
		int globalDiversity;
		long[] allLins2 = new long[0];
		
		if(isDistributed()) {
			
			////////////////// Get all lineages from each distributed cluster to calculate number of lineages globally
			
						//get list of all lineages on this machine
			            long[] allLins = activeboxs.stream().flatMapToLong(box -> box.streamLinNums())
			            									.distinct().toArray();
		             	MPI.COMM_WORLD.Barrier();
								
						if (!amIController()) {
							
							//each non controller sends list of lineages to controller
				            MPI.COMM_WORLD.Isend(allLins, 0, allLins.length, MPI.LONG, 0, 1);

						}else {
							
		
							//controller calculates global list of lineages from:
									//1) It's own list of lineages
							LongStream globLins = LongStream.of(allLins);

									//2) Receive list of lineages from others
							HashSet<Integer> msgRecieved = new HashSet<Integer>();
							for(int i = 1; i < numDistributedNodes; i++) {
								msgRecieved.add(i);
							}
							while(!msgRecieved.isEmpty()) {
								Iterator<Integer> msgIter = msgRecieved.iterator();
								while(msgIter.hasNext()) {
									Integer othNode = msgIter.next();
									Status probe = MPI.COMM_WORLD.Iprobe(othNode, 1);
									if(probe != null) {
										int len = probe.Get_count(MPI.LONG);
										long[] from1 = new long[len];
										MPI.COMM_WORLD.Irecv(from1, 0, len, MPI.LONG, othNode, 1);

										//3) Combine them
										globLins = LongStream.concat(globLins, LongStream.of(from1)).distinct();

										msgIter.remove();
									}
								}
							}
							
										//4) Convert to array
							allLins2 = globLins.toArray();
						}
						
						
			////////////////// If needed to trim temp array,
			////////////////// controller then sends global list of lineages to everything else
			if(Runner.settings.IS_SELECTIVE) {
				int[] numLins = amIController() ? new int[] {allLins2.length} : new int[1];
				MPI.COMM_WORLD.Bcast(numLins, 0, 1, MPI.INT, 0);
				
				//save received numlins in nl
				int nl = numLins[0];
				
				if(!amIController())
					allLins2 = new long[nl];
				MPI.COMM_WORLD.Bcast(allLins2, 0, nl, MPI.LONG, 0); //receive actual lins from all

			}
						
						
		} 
		else {
			
			allLins2 = activeboxs.stream().flatMapToLong(box -> box.streamLinNums()).distinct().toArray();

		}
		
		globalDiversity = allLins2.length;
		if(Runner.settings.IS_SELECTIVE)
			SelLineage.trimTempArray(LongStream.of(allLins2));

		
		return globalDiversity;
	}



	public void closeParallel() {
		if(isDistributed())
			MPI.Finalize();
	}
	
	public void abortParallel() {
		if(isDistributed())
			MPI.COMM_WORLD.Abort(-1);
	}



	public int synchSeeds(int seed) {
		if(!isDistributed())
			return seed;
		else{
			int[] broadcastSeed = new int[1];
			if(amIController())
				broadcastSeed[0] = seed;
			MPI.COMM_WORLD.Bcast(broadcastSeed, 0, 1, MPI.INT, 0);		
			return(broadcastSeed[0]);

		}
	}

	public int getRank() {
		return distributedRank;
	}

	public boolean isDistributed() {
		return distributed;
	}

	public void setDistributed(boolean distributed) {
		this.distributed = distributed;
	}

}
