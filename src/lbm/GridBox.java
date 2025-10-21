/** Location in Transport Matrix
 * 
 */

package lbm;


import java.util.TreeSet;
import java.util.stream.IntStream;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lineages.Lineage;
import lineages.SelLineage;
import parallelization.Cluster;
import dispersal.DispersalHandler;
import parallelization.GridBoxParallelization;
import util.ProbFunctions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;


public class GridBox<T_lin extends Lineage> implements Comparable<GridBox<T_lin>>{ 
 
	
 public static double maxVol; //volume of largest grid box
 private final double volume; //proportional volume (water volume / maxVol)
 
 /**ID (zero indexed)*/
 public final int id;
	
 protected TreeSet<T_lin> population; //list of currently present lineages 
 private int size; //current total population size
 private int myCC; //carrying capacity (fixed K stored in Settings.CC * volume)
 private int begin; //ID of first lineage initialized in this location
 private int end; //ID of last lineage initialized in this location
 
 /**Selection */
 private final double[] tsintvTemps; //temperature for each time interval
 private double currentTemp; //current temperature
 private int oldTempTsinvt = -1; //for calculating if temperature due change 

 private int myLinSize = 1; //lineage size, normally equal to Settings.INIT_LIN_SIZE unless whole population = one lineage
 
 /**Dispersal*/
 private double totalMovProb; //probability of being dispersed anywhere
 private ArrayList<DispersalHandler<T_lin>> movers = new ArrayList<DispersalHandler<T_lin>>(); //each handles dispersal to one sink

 /**Tracer Mode */
 private HashSet<Integer> arrivedFrom = new HashSet<Integer>();


 /**Parallelization*/
 private GridBoxParallelization<T_lin> parallelGB;



/*******************************************************************************************/
/***************************** INITIALISATION **********************************************/
/*******************************************************************************************/

	 public GridBox(int id, double volume, double[] temps) { 
		this.id = id;
		this.tsintvTemps = temps;
		this.currentTemp = temps[0];
		this.volume = volume;
	 } 
	 
	 
	 /** Initialize with Settings.INIT_LIN_SIZE individuals in each lineage */
	public void initPop() {
		population = new TreeSet<T_lin>();
		     
		if(Settings.TRACER_MODE) {						
			population.add((T_lin) Lineage.makeNew(myCC = (int) Math.round(Settings.K * volume),begin));
		}
		else {
			
			//create all lineages
			for(int i = begin; i < end; i++)
				population.add((T_lin) Lineage.makeNew(myLinSize,i));
		}
		
		//initialise local carrying capacity
		myCC = (int) Math.round(Settings.K * volume);
	}
	
	
	/**Initialise population from saved run
	 * 
	 * @param tokens saved csv holding lineage quantities - line pertaining to this location - split into string array
	 * @throws Exception
	 */
	public void addLoadedPop(String[] tokens) throws Exception {
		population = new TreeSet<T_lin>();
		size = 0;
		for(int i = 1; i < tokens.length; i+= 
				(Settings.TEMP_FILE == null ? 2 : 3) //find lineage ID every two columns in non selective simulations 
															//and every three columns in selective simulations  
				) {
			
			//troubleshooting = shouldn't happen
			if(i + 1 >= tokens.length)
					throw new Exception("Something went wrong loading population in location " + id + 
							" with token: " + Arrays.toString(tokens));
			///////////////
			
			//lineage id
			int id = Integer.parseInt(tokens[i]);
			
			//size
			int num = Integer.parseInt(tokens[i + 1]);
			
			if(id < Settings.SINK_OFFSET)
				size += num; //add to population size
			
			if(Settings.TEMP_FILE == null)//add neutral lineage
				population.add((T_lin) Lineage.makeNew(num, id)); 
			else //add selective lineage with saved temperature	
				population.add((T_lin) Lineage.makeNew(num, id, Float.parseFloat(tokens[i + 2]))); 
				
			
		}
		
		if(size < 0)
			throw new Exception("size cannot be < 0. size = " + size + " id = " + id);

		myCC = (int) Math.round(Settings.K * volume);
	}

	
	/**Adds sink location
	 * 
	 * @param prob probability of dispersal
	 * @param dest GridBox
	 * @param tmWriter : only used in util.MarkArtificialTM
	 */
	public void addDest(double prob, GridBox<T_lin> dest, StringBuilder tmWriter) {
		movers.add(new DispersalHandler<T_lin>(dest, prob));
		
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
	 * @throws Exception
	 */
	public void update(DRand rd, Binomial bn, Poisson pn, int currentTsintvTemp) throws Exception {
		boolean tempChanged = false;
		
		//set current temperature
		if(currentTsintvTemp != oldTempTsinvt && tsintvTemps.length > 1) {
			this.currentTemp = tsintvTemps[currentTsintvTemp];
			oldTempTsinvt = currentTsintvTemp;
			tempChanged = true;
		}
		
		//main ecological update loop repeats T_disp / T_growth
		for(int i = 0; i < Settings.GROWTH_PER_DISP; i++)
			growDieAll(rd, bn, pn, i == Settings.GROWTH_PER_DISP - 1, i == 0 && tempChanged);

	}


	/**Ecological loop (growth, death)
	 * 
	 * @param rd DRand uniform random number generator
	 * @param bn binomial random number generator
	 * @param pn poisson random number generator
	 * @param dispersing
	 * @param tempChanged
	 * @throws Exception
	 */
	public void growDieAll(DRand rd, Binomial bn, Poisson pn, boolean dispersing, boolean tempChanged) throws Exception {
		boolean willGrow = Settings.TOP_DOWN ? true : myCC > size;
		
		//get growth rate and mortality
		double gr = Settings.TOP_DOWN ? 
				Settings.GROWTH_RATE
				: ((1.0 - ((double)size / myCC)) * Settings.GROWTH_RATE);
		double mort = Settings.TOP_DOWN ? 
				(Settings.GROWTH_RATE / myCC) * size 
				: Settings.MORTALITY;
		
		
		for(T_lin s : population) { //for every lineage
			
			if(s.size == 0)
					continue;

			//grow/die
			if(!s.isSunk() && !Settings.TRACER_MODE)
				size += s.growDie(willGrow, gr, mort, currentTemp, tempChanged, this, pn, bn, rd);
			
			
			//disperse
			if(dispersing && s.size > 0) {
				dispBinomial(s, bn, rd);
				
				//dormant spores
				if(Settings.SIZE_REFUGE > 0 && s.size > 0) {
					disperseSpores(s, bn, rd);
				}
				

			}
			
			if(size < 0)
				throw new Exception("error in GridBox update loop: size cannot be < 0. size = " + size + " id = " + id);

			
		}
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
	private void dispBinomial(T_lin lin, Binomial bn, DRand rd) throws Exception {
		
		//calculate total number of individuals to disperse
		int numMov = ProbFunctions.getBinomial(lin.size, totalMovProb, bn, null);
		if(numMov == 0)
			return;
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
			for(DispersalHandler<T_lin> mov : movers) { //choose a sink
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
		
		
		//add negative fluxes so removes from here
		disperseToMe(lin, -numActuallyMoved);
	}
	
	/**Record when portion of lineage dispersed into this box ready for adding to population
	 * 
	 * @param lin Lineage object
	 * @param num number individuals dispersed here
	 */
	public void disperseToMe(T_lin lin, int num) {
		parallelGB.disperseToMe(lin, num);
	}
	


	/**	get total DispersalHandler probability/ accumulative probability for each sink/ sorts sinks into most probable first (just for efficiency - no effect on how model works) 
	 * 
	 * @param boxes all locations in system
	 * @throws Exception
	 */
	public void sortMovers(GridBox<T_lin>[] boxes) throws Exception {
		Collections.sort(movers);
		double accum = 0.0;
		for(DispersalHandler<T_lin> m : movers) {
			accum += m.getProb();
			m.setAccumProb(accum);
		}

		totalMovProb = accum;
		
		for(DispersalHandler<T_lin> m : movers) {
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
	public String writeTree() {
		StringBuilder tStr = new StringBuilder();
		for (T_lin lin : population) {
			tStr.append(lin.getDetails() + ",");
		}
		return tStr.toString();
	}


	/**Counts total richness
	 * 
	 * @return number of unsunken lineages with population > 0
	 */
	public int getNumLins() {
		return (int) (population.stream().filter(lin -> !lin.isSunk()).count());
	}
	
	
//	public double getTotalEvenness(double logN) {
//		return -population.stream().filter(lin -> !lin.isSunk()).
//				mapToDouble(lin -> (  (lin.size / (double)size) * Math.log(  (lin.size  / (double)size    )     )) ).sum() /  logN;
//	}
//
//	public double getOriginEvenness() {
//		double logN = Math.log(size);
//		
//		HashMap<Integer, Integer> cellCounts = new HashMap<Integer, Integer>();
//		for(Lineage lin : population) {
//			
//			if(lin.getId() < Settings.SINK_OFFSET) {
//				int box = Lineage.getOrign(id, lin.getId());
//			
//				cellCounts.compute(box, (k, v) -> (v == null) ? lin.size : v + lin.size);
//			}
//		}
//		
//		return -cellCounts.values().stream().mapToDouble(val -> (   (val / (double)size) * Math.log(   (val / (double)size )  )) ).sum() /  logN;
//		
//		
//	}








		@Override
		public boolean equals(Object oth) {
			if(this == oth)
				return true;
			return( ((GridBox<T_lin>)oth).id == this.id);
			
		}

		
	    @Override
	    public int hashCode(){
	        return id;
	    }

		@Override
		public int compareTo(GridBox<T_lin> oth) {
			if(this == oth)
				return 0;
			return(this.id - ((GridBox<T_lin>)oth).id);
		}


 


		public double getTenv() {
			return currentTemp;
		}

		
		/** Get mean/ standard deviation of temperatures */
		
//		public void getMeanSD() {
//			//get distribution of sizes
//			HashMap<Float,Integer> numByTopt = new HashMap<Float,Integer>();
//			for(T_lin lin : population)
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






		public IntStream streamLinNums() {
			return population.stream().filter(lin -> !lin.isSunk()).
					mapToInt(lin -> lin.getId());
		}

		public double getVol() {
			return volume;
		}


		/**Initialize range of indices for all individuals initialised in this location
		 * 
		 * @param bgn first index
		 * @return
		 */
		public int initIDSizeTemp(int bgn) {
			
			//set initial population size at equilibrium
			size = (int) Math.round(Settings.INITIAL_P * volume);
			//number of lineages to start with
			int numLins = (int) Math.round((double)size / (double)Settings.INIT_LIN_SIZE);
			
			
			if(Settings.INIT_LIN_SIZE == Settings.INITIAL_P) { //one lineage per location scenario
				myLinSize = size;
				numLins = 1;
				
			}else //multiple lineages per location
				myLinSize = Settings.INIT_LIN_SIZE;
			
			//adjust initial population size to exact multiple of number of lineages
					//(not relevant if one individual per lineage equal to equilibrium size)
			size = myLinSize * numLins;
			
			if(Settings.TRACER_MODE)
				numLins = 1;

			
					//alternative simpler indexing (but breaks back compatibility: ) 
					//begin = (int) (id * (1e5 * 2) );
			begin = bgn;
			end = begin + numLins;

			
			if(Settings.TEMP_FILE == null) {
				
					return end;

			}
			
			
			
			/////////// TEMPERATURE STUFF ////////////////////////
			
		     double minTemp =  (currentTemp - (Settings.TEMP_START_RANGE / 2.0)  );
		     double maxTemp = (float) (currentTemp + (Settings.TEMP_START_RANGE/2.0)   );
		     
		     if(tsintvTemps.length > 1) {
		    	 minTemp = Arrays.stream(tsintvTemps).min().getAsDouble();
		    	 maxTemp = Arrays.stream(tsintvTemps).max().getAsDouble();
		     }
		    	 //.stream().mapToDouble(e -> e).min().getAsDouble();
		     
		     double curTemp = 0;
		     double tempIntv = (maxTemp - minTemp) / numLins;

		     
		     for(int i = begin; i < end; i++) {
		    	 curTemp = minTemp + (tempIntv * (i - begin) );
		    	 SelLineage.addTemp(i, (float) curTemp);
		     }
		     //Lineage.addOrigin(end);
		     
		     
		     return end;
		}



		public int getEnd() {
			// TODO Auto-generated method stub
			return end;
		}



		public String writeModules(HashSet<Integer> modList) {
			StringBuilder outStr = new StringBuilder();

			for(Lineage lin : population) {
				if(modList.contains(lin.getId()))
					outStr.append("," + lin.getDetails());
			}
			
			if(outStr.length() > 0)
				return id + outStr.toString() + "\n";
			return "";
		}




		public void tracerPrint(double day, List<GridBox> activeCells) {
			System.out.print(Math.floor(day / 365.0) + "," + id + "," + arrivedFrom.size());
			
			int count = 0;
			for(GridBox cell : activeCells) {
				if(cell.arrivedFrom.contains(id))
					count++;
			}
			System.out.println("," + count  );
		}
	
	
	
		public int getTempChangesPerYear() {
			return tsintvTemps.length;
		}



		
	


	
	




//   /**NOT CURRENTLY USED ****************************************/
//	/**replace population with a uniform random subsample of size n
//	 * 
//	 * @param n
//	 * @param year
//	 */
//	public void getSampleset(int n, int year) {
//					if(size <= n)
//						return;
//		
//					int numSmpls = n; //Math.min(n, size );
//					int[] smplInds = IntStream.generate(() -> (int)Math.floor(Math.random() * size)).
//										distinct().
//										limit(numSmpls).
//										sorted().toArray();
//					
//					int smplIndx = 0;
//					int cumSize = 0;
//					int nextSmpl = smplInds[smplIndx];
//					int sizeFromThisLin = 0;
//					
//					size = 0;
//					
//					for(Lineage lin : population) {
//						if(smplIndx == numSmpls)
//							break;
//						if(lin.size == 0)
//							continue;
//						
//						
//						
//						//get inds in lineage
//						cumSize += lin.size;
//						sizeFromThisLin = 0;
//						while(nextSmpl < cumSize && smplIndx < numSmpls) {
//							sizeFromThisLin ++;
//							smplIndx ++;
//							if(smplIndx < numSmpls)
//								nextSmpl = smplInds[smplIndx];
//						}
//						
//						
//						if(sizeFromThisLin == 0) //none sampled from this lineage
//							continue;
//						
//						lin.size = -sizeFromThisLin; 
//						size += sizeFromThisLin;
//						
//					}
//					
//					
//					population.forEach(s -> s.size = Math.min(0, s.size));
//					clearEmptys();
//					population.forEach(s -> s.size = -s.size);
//	}


	public TreeSet<T_lin> getPopulation() {
		return population;
	}


	public void disperseSpores(T_lin s, Binomial bn, DRand rd) throws Exception {
		int sinkNum = ProbFunctions.getBinomial(s.size, Settings.SIZE_REFUGE, bn, rd);
		if(sinkNum > 0) {
			T_lin sunk = (T_lin) s.makeSunk(sinkNum);
			
			disperseToMe(sunk, sinkNum);
			
			s.size -= sinkNum;
			
			//if lineage was sunk (now unsunk) add these individuals, if not remove them from total box population
			if(!s.isSunk()) {
				size -= sinkNum;
				if(size < 0)
					throw new Exception("size cannot be < 0. size = " + size + " id = " + id);
	
			}
		}
	}


	public void setupParallelization() {
		parallelGB = new GridBoxParallelization<T_lin>();
	}
	
	public GridBoxParallelization<T_lin> getParallel(){
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


	public ArrayList<DispersalHandler<T_lin>> getMovers() {
		return movers;
	}



}
