package lbm;

//from https://www.geeksforgeeks.org/implementing-sparse-vector-in-java/

//importing generic packages 
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
//import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import cern.colt.list.DoubleArrayList;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lineages.Lineage;
import lineages.SelLineage;
import lineages.SporeLineage;
import mpi.MPI;
import util.ProbFunctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.Stack; 

public class GridCell<T_lin extends Lineage> implements Comparable<GridCell<T_lin>>{ 
 
// TreeMap is used to maintain sorted order 
//REASONS FOR USING TREESET = 
//The iteration time is thus proportional to the capacity, regardless of the number of elements. On a sparsely populated hash-table, that's quite expensive.	
//https://stackoverflow.com/questions/62558996/what-is-the-time-complexity-of-iterating-btreeset-and-hashset	
 protected TreeSet<T_lin> st;
	
 private HashSet<Integer> arrivedFrom = new HashSet<Integer>();


 private TreeMap<T_lin, Integer> immigrants = new TreeMap<T_lin, Integer>();

 
 private LinkedList<int[]> extIms = new LinkedList<int[]>();

 
 protected ArrayList<Movement> movers = new ArrayList<Movement>();

 
 protected double totalMovProb;
 public final int id;
 boolean externalSending = false;
 public int size; 
 protected int myCC;
 protected Cluster clust = null;
 private double temp;
 private final double volume;
 private final double[] temps;
 private int begin;
 private int end;
	
 static double maxVol;
 private int oldFromSize = 0;
 

	class Movement implements Comparable<Movement>{
		final GridCell<T_lin> dest;
		private final double prob;
		double accumProb;
		double accumProb2;
		
		public Movement(GridCell<T_lin> dest, double prob) {
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

		public ExternalMovement(GridCell<T_lin> dest, double prob) {
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
		
		public DistributedMovement(GridCell<T_lin> dest, double prob) {
			super(dest, prob);
		}
		
		public DistributedMovement(Movement copyMov) {
			super(copyMov);
		}

		@Override
		public void move(T_lin lin, int num) {
			if(lin.isSunk())
				num = (int) (num + Settings.SINK_OFFSET);
			
			mov.put(lin, num);
		}

		
		public void collateMovs(TreeMap<GridCell<T_lin>,TreeMap<T_lin,Integer>> movMap) {
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
	
	
	public void update(DRand rd, Binomial bn, Poisson pn, int dayOfYear) {
		if(temps.length > 1)
			this.temp = temps[dayOfYear];
		for(int i = 0; i < Settings.GROWTH_PER_DISP; i++)
			growDieAll(rd, bn, pn, i == Settings.GROWTH_PER_DISP - 1);

	}
	


	
	 // Constructor 
	 public GridCell(int id, double volume, double[] temps) { 
		 this.id = id;
		this.temps = temps;
		this.temp = temps[0];
		this.volume = volume;
	 } 


	public void growDieAll(DRand rd, Binomial bn, Poisson pn, boolean dispersing) {
		boolean willGrow = myCC > size;
		double addee = ((1.0 - ((double)size / myCC)) * Settings.GROWTH_RATE);
		
		for(T_lin s : st) {
			
			
			
			if(s.size == 0)
					continue;

			
			if(!Settings.TRACER_MODE)
				s.growDie(willGrow, addee, temp, dispersing, this, pn, bn, rd);
			
			if(dispersing && s.size > 0) {
				dispBinomial(s, bn, rd);
				if(Settings.SIZE_REFUGE > 0 && s.size > 0) {
					int sinkNum = ProbFunctions.getBinomial(s.size, Settings.SIZE_REFUGE, bn, rd);
					if(sinkNum > 0) {
						Lineage sunk = s.makeSunk(sinkNum);
						immigrants.compute((T_lin) sunk, (k, v) -> v == null ? sinkNum : v + sinkNum);
						
						
						System.out.println(id + "," + s.getId() + ":" + s.size + "," + sinkNum);
						
						s.size -= sinkNum;
						if(!s.isSunk())
							size -= sinkNum;
					}
				}

			}
			
		}
	} 





	private static double mortalitySizeFunction(int sz) {
		if(Settings.SIZE_REFUGE == 1)
			return 1;
		
		else
			return(Math.pow(sz,Settings.SIZE_REFUGE) / sz);
	}



	public void sortMovers(GridCell<T_lin>[] cells) throws Exception {
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
		
		//System.out.println(movers.size() + "," + movers.get(movers.size() - 1).prob);
	}

	public void getSampleset(int n, int year) {
					int numSmpls = Math.min(n, size );
					if(numSmpls < n)
						System.out.println("WARNING - collecting only," + numSmpls + ",from,"  + id + ",year," + year);
		
					//grab a subsample of size 1000
					int[] smplInds = IntStream.generate(() -> (int)Math.floor(Math.random() * size)).
										distinct().
										limit(numSmpls).
										sorted().toArray();
					
					int smplIndx = 0;
					int cumSize = 0;
					int nextSmpl = smplInds[smplIndx];
					int sizeFromThisLin = 0;
					
					for(Lineage lin : st) {
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
					
					st.forEach(s -> s.size = -s.size);
	}


	private void dispBinomial(T_lin s, Binomial bn, DRand rd) {
		
		int numMov = ProbFunctions.getBinomial(s.size, totalMovProb, bn, null);
		if(numMov == 0)
			return;
		
		boolean noBinomial = false;
		if(numMov == -1) {
			numMov = s.size;
			noBinomial = true;
		}
		
		int nMov = movers.size();
		int nMax = 0;
		
		int[] numToEach = null;
		
		for(int i =0; i < numMov; i++) {
			double movProb = rd.nextDouble();
			int n = 0;
			for(Movement mov : movers) {
				double prob = noBinomial ? mov.accumProb : mov.accumProb2;
				
				if(movProb < prob) {
					
					s.size --;
					size--;
					if(numToEach == null)
						numToEach = new int[nMov];
					nMax = Math.max(nMax, n);
					numToEach[n]++;
					break;
				}
				n++;

			}

		}
		
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


	protected void incrMe(T_lin s, int num, boolean external) {
		immigrants.compute(s, (k, v) -> v == null ? num : v + num);	
	}

	public static void printSize( TreeSet<Lineage> myST) {
		System.out.println(myST.stream().mapToInt(e -> e.size).sum());
	}
	
	void clean() {
		combineImmigrants();
		clearEmptys();
	}

	protected void combineImmigrants() {
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
		//System.out.println("," + st.stream().mapToInt(e -> e.size).sum() + "," + size );
		for(T_lin lin : st) {
			if(immigrants.isEmpty() && extIms.isEmpty())
				return;
			
			Integer imNum = immigrants.remove(lin);
			if(imNum != null) {
				lin.size += imNum;
				if(!lin.isSunk())
					size += imNum;
				
				//System.out.println("A," + lin.getId() + "," + imNum);
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
				if(nextExt[1] > Settings.SINK_OFFSET)
					nextExt[1] = nextExt[1] - Settings.SINK_OFFSET;
				//add found external lin
				lin.size += nextExt[1];
				if(!lin.isSunk())
					size += nextExt[1];
				extIter.remove(); //so don't add twice
				
				
				//System.out.println("B," + lin.getId() + "," + imNum);

				
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
				size += nextExt[1];
				extIter.remove(); //so don't add twice
				
				//System.out.println("D," + newLin.getId() + "," + nextExt[1]);
				
				nextExt = null;
				
			}
			
			if(Settings.TRACER_MODE)
				arrivedFrom.add(nextID);
			
			st.add((T_lin) newLin);

		}
		while(!extIms.isEmpty()) {
			int[] extIm = extIms.pollFirst();
			
			
			if(Settings.TRACER_MODE)
				arrivedFrom.add(extIm[1]);
			
			
			
			if(extIm[1] > Settings.SINK_OFFSET) {
				extIm[1] = extIm[1] - Settings.SINK_OFFSET;
				st.add((T_lin) SporeLineage.makeNew(extIm));
			}
			else {
				st.add((T_lin) Lineage.makeNew(extIm));
				size += extIm[1];
			}
			
			//System.out.println("E," + extIm[0] + "," + extIm[1]);
		}
		//System.out.println("," + st.stream().mapToInt(e -> e.size).sum() + "," + size );
	}
	





	public void addDest(double prob, GridCell<T_lin> dest) {
		movers.add(new Movement(dest, prob));
	}

	
	public void initPop() {
		


				 //size = (int) Math.round(Settings.INITIAL_P * volume);
				 //int begin = (int) Math.ceil  (id * (  (maxVol * Settings.INITIAL_P) / Settings.INIT_LIN_SIZE)  );
				 
			     st = new TreeSet<T_lin>();
			     
					
					if(Settings.TRACER_MODE) {						
				    	 st.add((T_lin) Lineage.makeNew(myCC = (int) Math.round(Settings.CC * volume),begin));
					}
					else {
			     
					     for(int i = begin; i < end; i++)
					    	 st.add((T_lin) Lineage.makeNew(Settings.INIT_LIN_SIZE,i));
					     //for(int i = 0; i < Settings.CC; i++)
					    	 //st.add((T_lin) Lineage.makeNew(Settings.INIT_LIN_SIZE,i));

					}
			     myCC = (int) Math.round(Settings.CC * volume);
			     //System.out.println(myCC);
	}



	public String writeTree() {
		StringBuilder tStr = new StringBuilder();
		for (T_lin lin : st) {
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
		return (int) (st.stream().filter(lin -> !lin.isSunk()).count());
		
		
		//return(st.size());
	}

	public void addLoadedPop(String[] tokens) {
		st = new TreeSet<T_lin>();
		size = 0;
		for(int i = Settings.LOAD_DIST ? 1 : 0; i < tokens.length; i+= Settings.LOAD_STEP) {
			if(i + 1 >= tokens.length) {
				System.out.println(id);
				for(int j = i; j >= 0; j-- )
					System.out.print(tokens[j] + ",");
				System.out.println();
			}
			int id = Integer.parseInt(tokens[i]);
			
			int num = Integer.parseInt(tokens[i + 1]);
			size += num;
			if(Settings.LOAD_STEP == 3)
				st.add((T_lin) Lineage.makeNew(num, id, Float.parseFloat(tokens[i + 2])));
			else 
				st.add((T_lin) Lineage.makeNew(num, id));
		}
		myCC = (int) Math.round(Settings.CC * volume);
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
			return( ((GridCell<T_lin>)oth).id == this.id);
			
		}

		
	    @Override
	    public int hashCode(){
	        return id;
	    }

		@Override
		public int compareTo(GridCell<T_lin> oth) {
			if(this == oth)
				return 0;
			return(this.id - ((GridCell<T_lin>)oth).id);
		}


 


		public double getTenv() {
			return temp;
		}

		
		public void getMeanSD() {
			//get distribution of sizes
			HashMap<Float,Integer> numByTopt = new HashMap<Float,Integer>();
			for(T_lin lin : st)
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
			return st.stream().filter(lin -> !lin.isSunk()).
					mapToInt(lin -> lin.getId());
		}

		public void clearEmptys() {
			st.removeIf(s -> s.size == 0);		
		}



		public double getVol() {
			return volume;
		}



		public int initIDSizeTemp(int bgn) {
			size = (int) Math.round(Settings.INITIAL_P * volume);
			
			int numLins = (int) Math.round((double)size / (double)Settings.INIT_LIN_SIZE);
			
			if(Settings.TRACER_MODE)
				numLins = 1;
			
			begin = bgn; // - Integer.MAX_VALUE;
			end = begin + numLins;
			
			//System.out.println(id + "," + begin + "," + end);
			
			
			//System.out.println(bgn + size);
			
			if(Settings.TEMP_FILE == null)
				return end;
			
			 

			
			
		     double minTemp =  (temp - Settings.TEMP_START_RANGE);
		     double maxTemp = (float) (temp + Settings.TEMP_START_RANGE);
		     
		     if(temps.length > 1) {
		    	 minTemp = Arrays.stream(temps).min().getAsDouble();
		    	 maxTemp = Arrays.stream(temps).max().getAsDouble();
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

			for(Lineage lin : st) {
				if(modList.contains(lin.getId()))
					outStr.append("," + lin.getDetails());
			}
			
			if(outStr.length() > 0)
				return id + outStr.toString() + "\n";
			return "";
		}




		public void tracerPrint(double day, ArrayList<GridCell> cellList) {
			System.out.print(Math.floor(day / 365.0) + "," + id + "," + arrivedFrom.size());
			
			int count = 0;
			for(GridCell cell : cellList) {
				if(cell.arrivedFrom.contains(id))
					count++;
			}
			System.out.println("," + count  );
			
//			
//			double fromSize = arrivedFrom.size();
//			
//			fromSize = Math.floor((fromSize / Settings.NUM_BOXES) * 10) * 10;
//			
//			
//			if(fromSize > oldFromSize) {
//				System.out.println(id + "," + fromSize + "," + day);
//				oldFromSize = (int) fromSize;
//			}
			
		}




}
