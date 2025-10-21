package lineages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.TreeSet;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lbm.GridBox;
import lbm.Settings;
import parallelization.RunnerParallelization;
import util.ProbFunctions;

public abstract class Lineage implements Comparable<Lineage>{

	public int size = 0;
	protected final int id;
	
	//private static ArrayList<Integer> originLins = new ArrayList<Integer>();

	
	public Lineage(int sz, int id) {
		size = sz;
		this.id = id;
	}
	
	@Override
	public boolean equals(Object oth) {
		if(this == oth)
			return true;
		return( ((Lineage)oth).getId() == this.getId());
		
	}

	
	public boolean isSunk() {
		return id >= Settings.SINK_OFFSET;
	}


	@Override
    public int hashCode(){
        return getId();
    }

	@Override
	public int compareTo(Lineage oth) {
		if(this == oth)
			return 0;
		return(this.getId() - oth.getId());
	}

	
	public Float getTopt() {
		throw new UnsupportedOperationException("Can't get topt from lineage in simulation without environmental selection!");
	}

	public String getDetails() {
		return getId() + "," + size;
	}
	
	public double[] getDetailsArr() {
		return new double[] {getId(), size};
	}

	public void prepareForMove() {
	}

	public abstract<T extends Lineage> T copy(int num);


	public double getSelectiveGrowth(double temp, boolean tempChanged) {
		return 1.0;
	};

	public static Lineage makeNew(int[] extIm) {
		if(Settings.TEMP_FILE == null)
			return new NeutralLineage(extIm[1], extIm[0]);
		else
			return new SelLineage(extIm[1], extIm[0]);
	}
	
	public static Lineage makeNew(int size, int id) {
		if(Settings.TEMP_FILE == null)
			return new NeutralLineage(size, id);
		else
			return new SelLineage(size, id);
	}
	
	public static Lineage makeNew(int size, int id, float temp) {
		SelLineage.addTemp(id, temp);
		return new SelLineage(size, id);
	}


	public int getId() {
		return id;
	}

	/**
	 * 
	 * @param willGrow
	 * @param growth
	 * @param mortality
	 * @param temp
	 * @param tempChanged
	 * @param gridCell
	 * @param pn
	 * @param bn
	 * @param rd
	 * @return
	 * @throws Exception
	 */
	public int growDie(boolean willGrow, double growth, double mortality, double temp, boolean tempChanged, GridBox gridCell, Poisson pn, Binomial bn, DRand rd) throws Exception {
		int add = willGrow ?
				ProbFunctions.getBinomial(size, growth  * getSelectiveGrowth(temp, tempChanged), bn, rd) :
					0;

		int die = ProbFunctions.getBinomial(size, mortality, bn, rd);
		int totalAdd = Math.max(-size, add - die);
		size += totalAdd;
		if(size < 0)
			throw new Exception("error in Lineage growth: size cannot be < 0. size = " + size + " id = " + id);
		
		if(RunnerParallelization.debugSerial)
			System.out.println(id + "," + add + "," + die);
		
		return totalAdd;	
	}

	public Lineage makeSunk(int sinkNum) {
		if(isSunk())
			return makeNew(sinkNum, id - Settings.SINK_OFFSET);
		else
			return makeNew(sinkNum, id + Settings.SINK_OFFSET);
	}

//	public static void addOrigin(int id) {
//		originLins.add(id);
//		
//	}
//
//	public static int getOrign(int currentID, int i) {
//		
//		ListIterator<Integer> origIter1 = originLins.listIterator(currentID);
//		ListIterator<Integer> origIter2 = originLins.listIterator(currentID);
//
//		boolean found = false;
//		int ind = currentID - 1;
//		while(!found) {
//			int next = origIter1.next();
//			ind ++;
//			if(next > i) {
//				while(origIter2.hasPrevious()) {
//					int previous = origIter2.previous();
//					if(previous < i)
//						return ind;
//					ind --;
//				}
//				return ind;
//			}
//		}
//		
//		
//		return ind;
//	}


	
}
