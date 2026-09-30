/** Location in Transport Matrix
 * 
 */

package transportMatrix;


import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import config.ControlConfig;
import config.SciConfig;
import control.Runner;
import lineages.Lineage;
import lineages.NeutralLineage;
import lineages.SelLineage;
import parallelization.Cluster;
import dispersal.DispersalHandler;
import parallelization.GridBoxParallelization;
import util.ProbFunctions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;


public class GridBox implements Comparable<GridBox>{ 
 
 /**ID (zero indexed)*/
 public final int id;
 
 
 /**list of currently present lineages*/	
 protected TreeSet<Lineage> population;
/**current active population size; dormant individuals are excluded until they return to the active state*/
public int size;
 /**carrying capacity*/
 private int myCC;
 
 //-------------Selection
 /**temperature for each time interval*/
 private final double[] tsintvTemps; 
 /**current temperature*/
 private double currentTemp; 
 /**for calculating if temperature due change*/ 
 private int oldTempTsinvt = -1;
 /**lineage size at initialisation, 
 normally equal to settings.INIT_LIN_SIZE unless whole population = one lineage*/
 private int myLinSize = 1; 
 
 //-------------Dispersal
 /**probability of being dispersed anywhere*/
 private double totalMovProb;
 /**each handles dispersal to one sink*/
 private ArrayList<DispersalHandler> movers = new ArrayList<DispersalHandler>(); 

 /**Tracer Mode */
 private HashSet<Integer> arrivedFrom = new HashSet<Integer>();


 /**Parallelization*/
 private GridBoxParallelization parallelGB;
 
 private final Phylogeny phylogeny;
 /**ID of next mutant to be created*/
 private int numLins;

//just for debugging
private int numMutants = 0;
private int numBirths = 0;

/**ID of first lineage added*/
private final long minID;

/*******************************************************************************************/
/***************************** INITIALISATION **********************************************/
/*******************************************************************************************/

	public GridBox(int id, double[] temps) { 
		this.id = id;
		this.tsintvTemps = temps;
		this.currentTemp = temps[0];
		this.minID = id * Runner.settings.mutantOffset + 1;
		phylogeny = new Phylogeny(id, minID);
	}
	 
	 
	 /** Initialize with settings.INIT_LIN_SIZE individuals in each lineage 
	 * @param tempLins 
	 * @throws Exception */
	public void initPop(ConcurrentHashMap<Long, Float> tempLins) throws Exception {

		
		population = new TreeSet<Lineage>();
		     
		if(Runner.settings.ctrl.tracerMode) {						
			population.add(Lineage.makeNew(minID, myLinSize, 0, tempLins, null));
		}
		else {
			//create all lineages
			for(long i = minID; i < minID + numLins; i++)
				population.add(Lineage.makeNew(i, myLinSize, 0, tempLins, null));				

			//add initial population. Parent = -1 = no parent
			phylogeny.addInitialPopulation(numLins);
		}
		
		//initialise local carrying capacity
		myCC = Runner.settings.sci.K;
		
		
	}
	
	
	/**Initialise population from saved run
	 * 
	 * @param tokens saved csv holding lineage quantities - line pertaining to this location - split into string array
	 * @param tempLins 
	 * @throws Exception
	 */
	public void addLoadedPop(String[] tokens, ConcurrentHashMap<Long, Float> tempLins, boolean includesTemp, boolean includesBirthhours) throws Exception {
		population = new TreeSet<Lineage>();
		size = 0;
		
		int increment = 2;
		int birthIDx = 2;
		int tempIDx = 2;
		if(includesTemp) {
			increment ++;
		}
		if(includesBirthhours) {
			increment ++;
			tempIDx = 3;
		}
		
	
		
		
		
		for(int i = 1; i < tokens.length; i+= increment) {
			
			//troubleshooting = shouldn't happen
			if(i + 1 >= tokens.length)
					throw new Exception("Something went wrong loading population in location " + id + 
							" with token: " + Arrays.toString(tokens));
			///////////////
			
			//lineage id
			long id = Long.parseLong(tokens[i]);
			long birthHour = 0;
			
			
			//TEMP debug
			tokens[i + 1] = tokens[i + 1].split("lbm")[0];
			
			
			//size
			
			
			int num = Integer.parseInt(tokens[i + 1]);
			if(includesBirthhours)
				birthHour = Long.parseLong(tokens[i + birthIDx]);
			
			if(id > 0) 
				//active cells
				size += num; //add to population size
			

			
			population.add(Lineage.makeNew(id, num, birthHour, tempLins, null));
						
		}
		
		if(size < 0)
			throw new Exception("size cannot be < 0. size = " + size + " id = " + id);
		myCC = Runner.settings.sci.K;
		
		// NOTE = don't manipulate phylogeny now as this is done in setup() 
		//			from maxID field in previous phylogeny file
		//			this is done because ID numbers of mutants need to not overlap with any extinct mutants
		//			rather than just with present population
	}

	
	/**Adds sink location
	 * 
	 * @param prob probability of dispersal
	 * @param dest GridBox
	 * @param tmWriter : only used in util.MarkArtificialTM
	 */
	public void addDest(double prob, GridBox dest, StringBuilder tmWriter) {
		movers.add(new DispersalHandler(dest, prob));
		
		if(tmWriter != null)
			tmWriter.append("\n" + (id + 1) + "," + (dest.id + 1) + "," + prob);
	}
	
/***************************** END INITIALISATION ******************************************/

	 

	
	
	/**
	 * Wrapper for ecological (growth/death) update loop
	 * @param rd DRand uniform random number generator
	 * @param bn binomial random number generator
	 * @param pn poisson random number generator
	 * @param currentTsintvTemp current time period for selection
	 * @param hour 
	 * @return 
	 * @throws Exception
	 */
	public void update(DRand rd, Binomial bn, Poisson pn, int currentTsintvTemp, long hour) throws Exception {
		boolean tempChanged = false;
		
		//set current temperature
		if(currentTsintvTemp != oldTempTsinvt && tsintvTemps.length > 1) {
			this.currentTemp = tsintvTemps[currentTsintvTemp];
			oldTempTsinvt = currentTsintvTemp;
			tempChanged = true;
		}
		
		//main ecological update loop repeats T_disp / T_growth
		for(int i = 0; i < Runner.settings.growthPerDisp; i++)
			growDieMutateAll(rd, bn, pn, i == Runner.settings.growthPerDisp - 1, i == 0 && tempChanged, hour);
	
		if(Runner.settings.ctrl.debug) {
			int measuredSize = population.stream().mapToInt(lin -> lin.size).sum();
			if(measuredSize != size)
				throw new Exception("Debug: size of grid box " + id + "(" + measuredSize + ") does not match "
						+ "predicted size ("  + size + ")");
						
		}

	}


	/**Ecological loop (growth, death, mutation) for all Lineages
	 * 
	 * @param rd DRand uniform random number generator
	 * @param bn binomial random number generator
	 * @param pn poisson random number generator
	 * @param dispersing
	 * @param tempChanged
	 * @param hour 
	 * @return 
	 * @throws Exception
	 */
	public void growDieMutateAll(DRand rd, Binomial bn, Poisson pn, boolean dispersing, boolean tempChanged, long hour) throws Exception {
		boolean willGrow = Runner.settings.sci.topDown ? true : myCC > size;
		
		
		//get growth rate and mortality
		double gr = Runner.settings.sci.topDown ? 
				Runner.settings.growthRate
				: ((1.0 - ((double)size / myCC)) * Runner.settings.growthRate);
		double mort = Runner.settings.sci.topDown ? 
				(Runner.settings.mortality / myCC) * size
				: Runner.settings.mortality;
		
		
		//New lineages created, will be added to population
		LinkedList<Lineage> mutants = new LinkedList<Lineage>();

		for(Lineage s : population) { //for every lineage
			
			if(s.size == 0)
					continue;

			//grow/die
			if(!s.isDormant() && !Runner.settings.ctrl.tracerMode)
				size += s.growDie(willGrow, gr, mort, currentTemp, 
						tempChanged, this, pn, bn, rd, phylogeny, mutants, hour);
			
			
			//disperse
			if(dispersing && s.size > 0) {
				size -= dispBinomial(s, bn, rd);
				
				//active/dormant switching
				if(Runner.settings.sci.dormantFrac > 0 && s.size > 0) {
					switchDormancy(s, bn, rd);
				}
				

			}
			
			if(size < 0)
				throw new Exception("error in GridBox update loop: size cannot be < 0. size = " + size + " id = " + id);

			
		}
		
		//incorporate mutants into regular population
		while(!mutants.isEmpty())
			population.add(mutants.removeFirst());

	} 

	
	
	
	
	
	
/*******************************************************************************************/
/***************************** DISPERSAL **********************************************/
/*******************************************************************************************/

	
	/**Choose if/where lineage should disperse to
	 * 
	 * @param lin Lineage object
	 * @param bn
	 * @param rd
	 * @throws Exception
	 */
	private int dispBinomial(Lineage lin, Binomial bn, DRand rd) throws Exception {
		
		//calculate total number of individuals to disperse
		int numMov = ProbFunctions.getBinomial(lin.size, totalMovProb, bn, null);
		if(numMov == 0)
			return 0;
				//if DispersalHandler probability too low to calculate binomial
				//will take separate uniform random decision for every individual
				boolean noBinomial = false;
				if(numMov == -1) {
					numMov = lin.size;
					noBinomial = true;
				}
		//

		
		//work out what is going where
		int nMov = movers.size();
		int nMax = 0;
		int[] numToEach = null;
		int numActuallyMoved = 0;
		for(int i =0; i < numMov; i++) { //for every individual moving
			double randomWhich = rd.nextDouble(); //random value 0 to 1 to calculate which sink to move to
			int n = 0;
			for(DispersalHandler mov : movers) { //choose a sink
				double prob = noBinomial ? mov.getAccumProb() : mov.getNormedAccumProb(); 
				
				if(randomWhich <= prob) {
					if(numToEach == null)
						numToEach = new int[nMov];
					nMax = Math.max(nMax, n);
					numToEach[n]++; //ready to move to sink n
					
					numActuallyMoved++;
					
					break;
				}
				n++;
			}
		}
		//
		
		
		//actually move (more efficient to separate from above loop as move multiple together)
		if(numToEach != null) {
			for(int n = 0; n <= nMax; n++) {
				int num = numToEach[n];
				if(num > 0) {
					lin.prepareForMove();
					movers.get(n).move(lin, num);
				}
			}
		}
		
		lin.size -= numActuallyMoved;
		return numActuallyMoved;
	}
	
	/**Record when portion of lineage dispersed into this box ready for adding to population
	 * 
	 * @param lin Lineage object
	 * @param num number individuals dispersed here
	 */
	public void disperseToMe(Lineage lin, int num) {
		parallelGB.disperseToMe(lin, num);
	}
	


	/**	get total DispersalHandler probability/ accumulative probability for each sink/ sorts sinks into most probable first (just for efficiency - no effect on how model works) 
	 * 
	 * @param boxes all locations in system
	 * @throws Exception
	 */
	public void sortMovers(GridBox[] boxes) throws Exception {
		Collections.sort(movers);
		double accum = 0.0;
		for(DispersalHandler m : movers) {
			accum += m.getProb();
			m.setAccumProb(accum);
		}

		totalMovProb = accum;
		
		for(DispersalHandler m : movers) {
			m.setNormedAccumProb(m.getAccumProb() / totalMovProb);
		}
	}
	
	public double getTotalMovProb() {
		return totalMovProb;
	}

	/**Gets number of sinks from here
	 * 
	 * @return
	 */
	public int getMovNum() {
		return movers.size();
	}

/***************************** END DISPERSAL ******************************************/
	
	
	
	
	
/*******************************************************************************************/
/***************************** OUTPUT **********************************************/
/*******************************************************************************************/	
	

	/**Returns string for outputting to population csv file
	 * 
	 * @return string containing of format "LIN_ID,LIN_SIZE..." for every lineage in population 
	 */
	public String writeDetails() {
		StringBuilder tStr = new StringBuilder();
		for (Lineage lin : population) {
			tStr.append(lin.getDetails() + ",");
		}
		return tStr.toString();
	}


	/**Counts total richness
	 * 
	 * @return number of unsunken lineages with population > 0
	 */
	public int getNumLins() {
		return (int) (population.stream().filter(lin -> !lin.isDormant()).count());
	}
	
	
		@Override
		public boolean equals(Object oth) {
			if(this == oth)
				return true;
			return( ((GridBox)oth).id == this.id);
			
		}

		
	    @Override
	    public int hashCode(){
	        return id;
	    }

		@Override
		public int compareTo(GridBox oth) {
			if(this == oth)
				return 0;
			return(this.id - ((GridBox)oth).id);
		}


 


		public double getTenv() {
			return currentTemp;
		}

		
		/** Get mean/ standard deviation of temperatures */
		
//		public void getMeanSD() {
//			//get distribution of sizes
//			HashMap<Float,Integer> numByTopt = new HashMap<Float,Integer>();
//			for(Lineage lin : population)
//				numByTopt.compute(lin.getTopt(), (k, v) -> (v == null) ? lin.size : v + lin.size);
//
//
//			//get average
//			double totalTopt = 0;
//			for(Entry<Float, Integer> toptSize : numByTopt.entrySet()) {
//				totalTopt += toptSize.getKey() * toptSize.getValue();
//			}
//			double avgTopt = totalTopt / size;
//			
//			//get SD
//			double totalSD = 0;
//			for(Entry<Float, Integer> toptSize : numByTopt.entrySet()) {
//				totalSD += Math.pow(toptSize.getKey() - avgTopt, 2) * toptSize.getValue();
//			}
//			double sd = totalSD / size;
//			sd = Math.sqrt(sd);
//			
//			System.out.print((Math.round(avgTopt * 1000.0) / 1000.0) + "+-" + (Math.round(sd * 1000.0) / 1000.0)  + ",");
//		}






		public LongStream streamLinNums() {
			return population.stream().filter(lin -> !lin.isDormant()).
					mapToLong(lin -> lin.getId());
		}
		
		public LongStream streamPhylo() {
			return phylogeny.streamAllChildren();
		}

		/**Initialize range of indices for all individuals initialised in this location
		 * 
		 * @param bgn first index
		 * @return
		 */
		public void initIDSizeTemp(ConcurrentHashMap<Long, Float> tempLins) {
			
			
			
			//set initial population size at equilibrium
			size = (int) Math.round(Runner.settings.initialP);
			
			//number of lineages to start with
			numLins = (int) Math.round((double)size / (double)Runner.settings.sci.initLinSize);
			
			
			if(Runner.settings.sci.initLinSize == Runner.settings.initialP) { //one lineage per location scenario
				myLinSize = size;
				numLins = 1;
				
			}else //multiple lineages per location
				myLinSize = Runner.settings.sci.initLinSize;
			
			//adjust initial population size to exact multiple of number of lineages
					//(not relevant if one individual per lineage equal to equilibrium size)
			size = myLinSize * numLins;
			
			if(Runner.settings.ctrl.tracerMode)
				numLins = 1;

			
					//alternative simpler indexing (but breaks back compatibility: ) 
					//begin = (int) (id * (1e5 * 2) );


			
			/////////// TEMPERATURE STUFF ////////////////////////
			if(Runner.settings.isSelective) {
			
				double curTemp = currentTemp;
			
				for(long i = minID; i < minID + numLins; i++) {
			
					if(Runner.settings.ctrl.loadFile == null)
						tempLins.put(i, (float) curTemp);
				}
			
			}
			
			}







	public void tracerPrint(double day, List<GridBox> activeBoxes) {
		System.out.print(Math.floor(day / 360.0) + "," + id + "," + arrivedFrom.size());
		
		int count = 0;
		for(GridBox boxes : activeBoxes) {
			if(boxes.arrivedFrom.contains(id))
				count++;
		}
		System.out.println("," + count  );
	}



	public int getTempChangesPerYear() {
		return tsintvTemps.length;
	}


	public TreeSet<Lineage> getPopulation() {
		return population;
	}


	public void switchDormancy(Lineage s, Binomial bn, DRand rd) throws Exception {
	
		double dormantFrac = Runner.settings.sci.dormantFrac;
		double tauDay = Runner.settings.sci.dormantTauDay;
		double dtDay = Runner.settings.sci.dispHours / 24.0;
	
		double rate;
	
		if(s.isDormant()) {
			// dormant -> active
			rate = (1.0 - dormantFrac) / tauDay;
		}
		else {
			// active -> dormant
			rate = dormantFrac / tauDay;
		}
	
		double pSwitch = 1.0 - Math.exp(-rate * dtDay);
	
		int switchNum = ProbFunctions.getBinomial(s.size, pSwitch, bn, rd);
	
		if(switchNum > 0) {
	
			Lineage switched = s.switchDormancyState(switchNum);
	
			disperseToMe(switched, switchNum);
	
			s.size -= switchNum;
	
			//if lineage was active, remove newly dormant individuals
			//from the active population size
			if(!s.isDormant()) {
				size -= switchNum;
	
				if(size < 0)
					throw new Exception("size cannot be < 0. size = " + size + " id = " + id);
			}
		}
	}


	public void setupParallelization() {
		parallelGB = new GridBoxParallelization();
	}
	
	public GridBoxParallelization getParallel(){
		return parallelGB;
	}


	/** Combines individuals dispersed into this gridbox 
	 * with existing population and updates population size
	 * Note: Combine immigrants has no effect if run twice (i.e. before new round of updating)
	 * 			so can be carried out whenever needed (e.g. before printing output)
	 * @throws Exception
	 */
	public void combineImmigrants() throws Exception {
		size = parallelGB.combineImmigrants(size, population, arrivedFrom, id);
		population.removeIf(s -> s.size == 0);	
	}


	public ArrayList<DispersalHandler> getMovers() {
		return movers;
	}


	
	public void debugMutants(int mutNum, int numBirths) {
			this.numMutants += mutNum;
			this.numBirths += numBirths;
	}
	
	
	//debug


	public int getNumMutants() {
		return numMutants;
	}
	
	public int getNumBirths() {
		return numBirths;
	}
	
	public void clearMutantDebug() {
		numMutants = 0;
		numBirths = 0;
	}


	public Phylogeny getPhylogeny() {
		return phylogeny;
	}


	public void clearPhylogeny() {
		phylogeny.clear();
	}

	public long getMinID() {
		return minID;
	}


	public long getPhyloSize() {
		return phylogeny.getSize();
	}

	/**
	 * 
	 * @return  phylogeny.getCurrentMutantID()
	 */
	public long getCurrentMutantID() {
		return phylogeny.getCurID();
	}

	public void setCurrentMutantID(long id) {
		phylogeny.setCurID(id);
		
	}
	
	/**JUST FOR TESTING FOR NOW: Merge two phylogenies for complete phylogeny
	 * 
	 * @param phyl phylogeny to merge into current phylogeny
	 */
	void mergePhylogeny(Phylogeny phyl) {
		phylogeny.merge(phyl);
	}


}
