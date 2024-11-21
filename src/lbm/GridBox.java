/** Location in Transport Matrix
 * 
 */

package lbm;


import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lineages.Lineage;
import lineages.SelLineage;
import parallelization.Cluster;
import util.ProbFunctions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

public class GridBox<T_lin extends Lineage> implements Comparable<GridBox<T_lin>>{ 
 
 public final int id;
	
 protected TreeSet<T_lin> population; //list of currently present lineages 
 public int size; //current total population size
 static double maxVol; //volume of largest grid box
 private final double volume; //water volume / maxVol
 protected int myCC; //carrying capacity (fixed K stored in Settings.CC * volume)
 private int begin; //ID of first lineage initialized in this location
 private int end; //ID of last lineage initialized in this location
 
 /**Selection */
 private final double[] dailyTemps;
 private double currentTemp;

 
 /**Dispersal */
 private TreeMap<T_lin, Integer> immigrants = new TreeMap<T_lin, Integer>(); //immigrants from same cluster
 protected ArrayList<Movement> movers = new ArrayList<Movement>();
 protected double totalMovProb; //probability of being dispersed
 
 /**Parallelization */
 protected Cluster clust = null; //nearby cells which disperse to more regularly (no effect on science - just for efficiency) 
 private LinkedList<int[]> extIms = new LinkedList<int[]>(); //immigrants from different cluster
 boolean externalSending = false; //true if disperses at all outside cluster
 
 /**Tracer Mode */
 private HashSet<Integer> arrivedFrom = new HashSet<Integer>();

private int oldDayYear = -1;
 
 
 

	 public GridBox(int id, double volume, double[] temps) { 
		this.id = id;
		this.dailyTemps = temps;
		this.currentTemp = temps[0];
		this.volume = volume;
	 } 
	 
	 
	 /** Initialize with Settings.INIT_LIN_SIZE individuals in each lineage */
		public void initPop() {
		     population = new TreeSet<T_lin>();
		     
				
				if(Settings.TRACER_MODE) {						
			    	 population.add((T_lin) Lineage.makeNew(myCC = (int) Math.round(Settings.CC * volume),begin));
				}
				else {
		     
				     for(int i = begin; i < end; i++)
				    	 population.add((T_lin) Lineage.makeNew(Settings.INIT_LIN_SIZE,i));

				}
		     myCC = (int) Math.round(Settings.CC * volume);
		}

	 
	 
	public void update(DRand rd, Binomial bn, Poisson pn, int dayOfYear) {
		boolean tempChanged = false;
		if(dayOfYear != oldDayYear && dailyTemps.length > 1) {
			this.currentTemp = dailyTemps[dayOfYear];
			oldDayYear = dayOfYear;
			tempChanged = true;
		}
		for(int i = 0; i < Settings.GROWTH_PER_DISP; i++)
			growDieAll(rd, bn, pn, i == Settings.GROWTH_PER_DISP - 1, i == 0 && tempChanged);

	}

	/** Main Eco Loop */
	public void growDieAll(DRand rd, Binomial bn, Poisson pn, boolean dispersing, boolean tempChanged) {
		boolean willGrow = myCC > size;
		double gr = ((1.0 - ((double)size / myCC)) * Settings.GROWTH_RATE);
		
		for(T_lin s : population) { //for every lineage
			
			if(s.size == 0)
					continue;

			//grow/die
			if(!s.isSunk() && !Settings.TRACER_MODE)
				s.growDie(willGrow, gr, currentTemp, tempChanged, dispersing, this, pn, bn, rd);
			
			//disperse
			if(dispersing && s.size > 0) {
				dispBinomial(s, bn, rd);
				
				
				// ONLY USED IF DORMANT SPORES/SIZE REFUGE IN SYSTEM 
				if(Settings.SIZE_REFUGE > 0 && s.size > 0) {
					int sinkNum = ProbFunctions.getBinomial(s.size, Settings.SIZE_REFUGE, bn, rd);
					if(sinkNum > 0) {
						Lineage sunk = s.makeSunk(sinkNum);
						immigrants.compute((T_lin) sunk, (k, v) -> v == null ? sinkNum : v + sinkNum);
						
						s.size -= sinkNum;
						
						//if lineage was sunk (now unsunk) add these individuals, if not remove them from total box population
						
						if(!s.isSunk())
							size -= sinkNum;
					}
				}
				//END SIZE REFUGE CODE
				

			}
			
		}
	} 

	
	/**Disperse*/
	private void dispBinomial(T_lin s, Binomial bn, DRand rd) {
		
		//total number of individuals dispersed (to anywhere)
		int numMov = ProbFunctions.getBinomial(s.size, totalMovProb, bn, null);
		if(numMov == 0)
			return;
		
		//if movement probability too low to calculate binomial
		//will take separate uniform random decision for every individual
		boolean noBinomial = false;
		if(numMov == -1) {
			numMov = s.size; //classify every individual as "individual moving" - but may not actually move
			noBinomial = true;
		}
		
		
		int nMov = movers.size();
		int nMax = 0;
		int[] numToEach = null;
		for(int i =0; i < numMov; i++) { //for every individual moving
			double movProb = rd.nextDouble(); //uniform probability 0 (incl)  to 1 (excl)
			int n = 0;
			for(Movement mov : movers) { //work out which sink prob equates to
				
				//if no binomial use accumulative probabilities (does not guarantee movement as we're looping through all individuals) 
				//otherwise use normalized accumulative probabilities (guarantees movement as we're only looping through individuals already selected for movement)
				double prob = noBinomial ? mov.accumProb : mov.accumProb2; 
				
				if(movProb < prob) {
					
					s.size --;
					
					if(!s.isSunk())
						size--;
					if(numToEach == null)
						numToEach = new int[nMov];
					nMax = Math.max(nMax, n);
					numToEach[n]++; //ready to move to sink n
					break;
				}
				n++;
			}
		}
		
		
		//actually move (more efficient to separate from above loop so can move multiple together)
		if(numToEach != null) {
			for(int n = 0; n <= nMax; n++) {
				int num = numToEach[n];
				if(num > 0) {
					s.prepareForMove();
					movers.get(n).move(s, num);
				}
			}
		}
	}
	
	/**Record when individual dispersed into this box ready for adding to population */
	protected void incrMe(T_lin s, int num, boolean external) {
		immigrants.compute(s, (k, v) -> v == null ? num : v + num);	
	}
	
	

	/**Integrate immigrants into population */
	public void combineImmigrants() {
		if(immigrants.isEmpty() && extIms.isEmpty())
			return;
		
		extIms.sort((a,b) -> 
		{
			if(a[0] == b[0])
				return(a[1] - b[1]);
			return (a[0] - b[0]);}
		
		);
		
		Iterator<int[]> extIter = extIms.iterator(); 
		int[] nextExt = null;
		for(T_lin lin : population) {
			if(immigrants.isEmpty() && extIms.isEmpty())
				return;
			
			Integer imNum = immigrants.remove(lin);
			if(imNum != null) {
				lin.size += imNum;
				if(!lin.isSunk())
					size += imNum;
				
			}
			
			if(extIms.isEmpty())
				continue;
			
			int nextID = lin.getId();
			
			if(nextExt == null) {
				if(extIter.hasNext()) {
					nextExt = extIter.next();
					while(nextExt[0] < nextID && extIter.hasNext())
						nextExt = extIter.next();
				}
			
			}
			else {
				while(nextExt[0] < nextID && extIter.hasNext())
					nextExt = extIter.next();

			}
			if(nextExt != null && nextExt[0] == nextID) {
				//add found external lin
				lin.size += nextExt[1];
				if(!lin.isSunk())
					size += nextExt[1];
				extIter.remove(); //so don't add twice
				nextExt = null;
				

			}
		}
		
		
		extIter = extIms.iterator(); 
		nextExt = null;
		while(!immigrants.isEmpty()) {
			Entry<T_lin, Integer> imEntry = immigrants.pollFirstEntry();
			Integer imNum = imEntry.getValue();
			T_lin lin = imEntry.getKey();
			if(!lin.isSunk())
				size += imNum;
			Lineage newLin = lin.copy(imNum);
			int nextID = newLin.getId();		
			
			
			//System.out.println("C," + newLin.getId() + "," + imNum);

			
			if(nextExt == null) {
				if(extIter.hasNext()) {
					nextExt = extIter.next();
					while(nextExt[0] < nextID && extIter.hasNext())
						nextExt = extIter.next();
				}
			
			}
			else {
				while(nextExt[0] < nextID && extIter.hasNext())
					nextExt = extIter.next();

			}
			if(nextExt != null && nextExt[0] == nextID) {
				if(nextExt[1] > Settings.SINK_OFFSET)
					nextExt[1] = nextExt[1] - Settings.SINK_OFFSET;

				
				//add found external lin
				newLin.size += nextExt[1];
				if(!newLin.isSunk())
					size += nextExt[1];
				extIter.remove(); //so don't add twice
				
				//System.out.println("D," + newLin.getId() + "," + nextExt[1]);
				
				nextExt = null;
				
			}
			
			if(Settings.TRACER_MODE)
				arrivedFrom.add(nextID);
			
			population.add((T_lin) newLin);

		}
		while(!extIms.isEmpty()) {
			int[] extIm = extIms.pollFirst();
			
			
			if(Settings.TRACER_MODE)
				arrivedFrom.add(extIm[1]);
			
			T_lin newLin = (T_lin) Lineage.makeNew(extIm);
			population.add(newLin);
			
			if(!newLin.isSunk())
				size += extIm[1];
		}
	}
	



	/**Object handling dispersal, storing individuals ready for dispersal */
	class Movement implements Comparable<Movement>{
		final GridBox<T_lin> dest;
		private final double prob;
		double accumProb;
		double accumProb2;
		
		public Movement(GridBox<T_lin> dest, double prob) {
			this.dest = dest;
			this.prob = prob;
			
		}
		
		public Movement(Movement copyMov) {
			dest = copyMov.dest;
			prob = copyMov.prob;
			accumProb = copyMov.accumProb;
			accumProb2 = copyMov.accumProb2;	
		}

		public void move(T_lin s, int num) {			
			dest.incrMe(s, num, false);
		}
		
		@Override
		public int compareTo(Movement other) {
            if(this.prob > other.prob)
            	return -1; //-1 so sorts descending
            else if(this.prob == other.prob)
            	return 0;
            else
            	return 1;
		}

	}
	
	public void addDest(double prob, GridBox<T_lin> dest) {
		movers.add(new Movement(dest, prob));
	}
	
	/** get total movement probability/ accumulative probability for each sink/ sorts sinks into most probable first (just for efficiency - no effect on how model works) */
	public void sortMovers(GridBox<T_lin>[] cells) throws Exception {
		Collections.sort(movers);
		double accum = 0.0;
		for(Movement m : movers) {
			accum += m.prob;
			m.accumProb = accum;
		}

		totalMovProb = accum;
		
		
		for(Movement m : movers) {
			m.accumProb2 = m.accumProb / totalMovProb;
		}
	}
	
	

	public static void printSize( TreeSet<Lineage> myST) {
		System.out.println(myST.stream().mapToInt(e -> e.size).sum());
	}
	
	void clean() {
		combineImmigrants();
		clearEmptys();
	}


	public String writeTree() {
		StringBuilder tStr = new StringBuilder();
		for (T_lin lin : population) {
			tStr.append(lin.getDetails() + ",");
		}
		return tStr.toString();
	}

	public double getTotalMovProb() {
		return totalMovProb;
	}

	public int getMovNum() {
		return movers.size();
	}
	
	public int getNumLins() {
		return (int) (population.stream().filter(lin -> !lin.isSunk()).count());
	}

	public void addLoadedPop(String[] tokens) {
		population = new TreeSet<T_lin>();
		size = 0;
		try {
		
				for(int i = 1; i < tokens.length; i+= Settings.LOAD_STEP) {
					if(i + 1 >= tokens.length) {
						System.out.println(id);
						for(int j = i; j >= 0; j-- )
							System.out.print(tokens[j] + ",");
						System.out.println();
					}
					int id = Integer.parseInt(tokens[i]);
					
					int num = Integer.parseInt(tokens[i + 1]);
					
					if(id < Settings.SINK_OFFSET)
						size += num;
					
					if(Settings.LOAD_STEP == 3)
						population.add((T_lin) Lineage.makeNew(num, id, Float.parseFloat(tokens[i + 2])));
					else 
						population.add((T_lin) Lineage.makeNew(num, id));
				}
		}catch(Exception e) {
			e.printStackTrace();
			System.out.println("because you can");
		}
		
		myCC = (int) Math.round(Settings.CC * volume);
	}



		public void setClust(Cluster cluster) {
			this.clust = cluster;
			
		}
		
		public Cluster getClust() {
			return this.clust;
			
		}



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

		
		public void getMeanSD() {
			//get distribution of sizes
			HashMap<Float,Integer> numByTopt = new HashMap<Float,Integer>();
			for(T_lin lin : population)
				numByTopt.compute(lin.getTopt(), (k, v) -> (v == null) ? lin.size : v + lin.size);


			//get average
			double totalTopt = 0;
			for(Entry<Float, Integer> toptSize : numByTopt.entrySet()) {
				totalTopt += toptSize.getKey() * toptSize.getValue();
			}
			double avgTopt = totalTopt / size;
			
			//get SD
			double totalSD = 0;
			for(Entry<Float, Integer> toptSize : numByTopt.entrySet()) {
				totalSD += Math.pow(toptSize.getKey() - avgTopt, 2) * toptSize.getValue();
			}
			double sd = totalSD / size;
			sd = Math.sqrt(sd);
			
			System.out.print((Math.round(avgTopt * 1000.0) / 1000.0) + "+-" + (Math.round(sd * 1000.0) / 1000.0)  + ",");
		}




		public void addExt(int[] ext) {
			extIms.add(ext);
			
		}



		public boolean imADest(int nodeNum) {
			return movers.stream().mapToInt(mov -> mov.dest.clust.node).anyMatch(n -> n == nodeNum);
		}

		public IntStream streamLinNums() {
			return population.stream().filter(lin -> !lin.isSunk()).
					mapToInt(lin -> lin.getId());
		}

		public void clearEmptys() {
			population.removeIf(s -> s.size == 0);		
		}



		public double getVol() {
			return volume;
		}



		public int initIDSizeTemp(int bgn) {
			size = (int) Math.round(Settings.INITIAL_P * volume);
			
			int numLins = (int) Math.round((double)size / (double)Settings.INIT_LIN_SIZE);
			
			if(Settings.TRACER_MODE)
				numLins = 1;
			
			begin = bgn;
			end = begin + numLins;
			
			System.out.println(end);
			
			if(Settings.TEMP_FILE == null)
				return end;
			
		     double minTemp =  (currentTemp - (Settings.TEMP_START_RANGE / 2.0)  );
		     double maxTemp = (float) (currentTemp + (Settings.TEMP_START_RANGE/2.0)   );
		     
		     if(dailyTemps.length > 1) {
		    	 minTemp = Arrays.stream(dailyTemps).min().getAsDouble();
		    	 maxTemp = Arrays.stream(dailyTemps).max().getAsDouble();
		     }
		    	 //.stream().mapToDouble(e -> e).min().getAsDouble();
		     
		     double curTemp = 0;
		     double tempIntv = (maxTemp - minTemp) / numLins;

		     
		     for(int i = begin; i < end; i++) {
		    	 curTemp = minTemp + (tempIntv * (i - begin) );
		    	 SelLineage.addTemp(i, (float) curTemp);

		     }
		     
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




		public void tracerPrint(double day, ArrayList<GridBox> cellList) {
			System.out.print(Math.floor(day / 365.0) + "," + id + "," + arrivedFrom.size());
			
			int count = 0;
			for(GridBox cell : cellList) {
				if(cell.arrivedFrom.contains(id))
					count++;
			}
			System.out.println("," + count  );
		}
	
	
	
	
	
	/** PARALLELIZATION ********************************************/
							class ExternalMovement extends Movement {
								LinkedList<ExtMoveLin> mov = new LinkedList<ExtMoveLin>();
								
								protected class ExtMoveLin{
									private final T_lin lin;
									private final int num;
									
									private ExtMoveLin(T_lin lin, int num) {
										this.lin = lin;
										this.num = num;
									}
								}
						
								public ExternalMovement(GridBox<T_lin> dest, double prob) {
									super(dest, prob);
								}
						
						
						
								public ExternalMovement(Movement copyMov) {
									super(copyMov);
								}
						
						
						
								@Override
								public void move(T_lin lin, int num) {			
									mov.add(new ExtMoveLin(lin, num) );
								}
								
								public void actuallyMove() {
									while(!mov.isEmpty()) {
										ExtMoveLin xMov = mov.remove();
										dest.incrMe(xMov.lin, xMov.num, true);
										
									}
								}
							}
							
							class DistributedMovement extends ExternalMovement {
								TreeMap<T_lin,Integer> mov = new TreeMap<T_lin,Integer>();
								//LinkedList<int[]> mov = new LinkedList<int[]>();
								
								public DistributedMovement(GridBox<T_lin> dest, double prob) {
									super(dest, prob);
								}
								
								public DistributedMovement(Movement copyMov) {
									super(copyMov);
								}
						
								@Override
								public void move(T_lin lin, int num) {
									mov.put(lin, num);
								}
						
								
								public void collateMovs(TreeMap<GridBox<T_lin>,TreeMap<T_lin,Integer>> movMap) {
									if(mov.isEmpty())
										return;
									TreeMap<T_lin, Integer> alreadyThere = movMap.putIfAbsent(dest,mov);
									
									
									if(alreadyThere != null)
										for(Entry<T_lin, Integer> m : mov.entrySet()) {
											int num = m.getValue();
											alreadyThere.compute(m.getKey(), (k, v) -> v == null ? 
													num : v + num);
										}
									mov = new TreeMap<T_lin,Integer>();
								}
						
							}
							
							
							
							public void externaliseMovers(ArrayList<ExternalMovement> extMovs, HashMap<Integer, ArrayList<DistributedMovement>> distMovs) {
								for(int i =0; i < movers.size(); i++) {
									Movement mov = movers.get(i);
									
									if(mov.dest.clust != this.clust) {
										ExternalMovement xMov = null;
										
										if(mov.dest.clust.node == this.clust.node) {
												xMov = new ExternalMovement(mov);
												extMovs.add(xMov);
										}
										else {
											DistributedMovement dMov = new DistributedMovement(mov);
											distMovs.compute(mov.dest.clust.node, (k, v) -> {
														if(v == null)
															v = new ArrayList<DistributedMovement>();
														v.add(dMov);
														return(v);	
													}
													
											);
											
											xMov = dMov;
										}
										movers.set(i, xMov);
										

									}
										
								}

							}

	/** END PARALLELIZATION ********************************************/

	
	




   /**NOT CURRENTLY USED ****************************************/
	//grab a uniform random subsample of size n
	public void getSampleset(int n, int year) {
					int numSmpls = Math.min(n, size );
					if(numSmpls < n)
						System.out.println("WARNING - collecting only," + numSmpls + ",from,"  + id + ",year," + year);
		
					int[] smplInds = IntStream.generate(() -> (int)Math.floor(Math.random() * size)).
										distinct().
										limit(numSmpls).
										sorted().toArray();
					
					int smplIndx = 0;
					int cumSize = 0;
					int nextSmpl = smplInds[smplIndx];
					int sizeFromThisLin = 0;
					
					for(Lineage lin : population) {
						if(smplIndx == numSmpls)
							break;
						if(lin.size == 0)
							continue;
						
						
						
						//get inds in lineage
						cumSize += lin.size;
						sizeFromThisLin = 0;
						while(nextSmpl < cumSize && smplIndx < numSmpls) {
							sizeFromThisLin ++;
							smplIndx ++;
							if(smplIndx < numSmpls)
								nextSmpl = smplInds[smplIndx];
						}
						
						
						if(sizeFromThisLin == 0) //none sampled from this lineage
							continue;
						
						lin.size = -sizeFromThisLin; 

						
					}
					
					clearEmptys();
					
					population.forEach(s -> s.size = -s.size);
	}


	
	public int getNumTemps() {
		return dailyTemps.length;
	}


}
