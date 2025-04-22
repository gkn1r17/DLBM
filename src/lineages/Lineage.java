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
import util.ProbFunctions;

public abstract class Lineage implements Comparable<Lineage>{

	public int size = 0;
	protected final int id;
	
	private static ArrayList<Integer> originLins = new ArrayList<Integer>();

	
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

	public void growDie(boolean willGrow, double addee, double temp, boolean tempChanged, boolean dispersing, GridBox gridCell, Poisson pn, Binomial bn, DRand rd) {
		
		// as (k * (1 + n)) / (1 - 1) < k surely we should applying births and deaths together? (i.e. option 2)
		// I haven't been doing that because I've been using poisson for births and binomial for deaths, poisson because it's not restricted to <= k and so reflects chance of exponential births (i.e. children birth more children in time period)
					// except because of this lack of restriction I decided to give every individual - including new individuals a chance to die otherwise there's a bias towards population size increasing
		
		//OPTION 1 = default
		
		/*
		 * if(willGrow) { int add = pn.nextInt(addee * size * getSelectiveGrowth(temp,
		 * tempChanged)); size += add; gridCell.size += add; } int dieNum =
		 * ProbFunctions.getBinomial(size, Settings.MORTALITY, bn, rd); size -= dieNum;
		 * gridCell.size -= dieNum;
		 */		
		
		
		//OPTION 2 = Mortality and births together 
		
		int add = willGrow ?
				ProbFunctions.getBinomial(size, addee  * getSelectiveGrowth(temp, tempChanged), bn, rd) :
					0;

		int die = ProbFunctions.getBinomial(size, Settings.MORTALITY, bn, rd);
		int totalAdd = Math.max(-size, add - die);
		size += totalAdd;
		gridCell.size += totalAdd;	
	}

	public Lineage makeSunk(int sinkNum) {
		if(isSunk())
			return makeNew(sinkNum, id - Settings.SINK_OFFSET);
		else
			return makeNew(sinkNum, id + Settings.SINK_OFFSET);
	}

	public static void addOrigin(int id) {
		originLins.add(id);
		
	}

	public static int getOrign(int currentID, int i) {
		
		ListIterator<Integer> origIter1 = originLins.listIterator(currentID);
		ListIterator<Integer> origIter2 = originLins.listIterator(currentID);

		boolean found = false;
		int ind = currentID - 1;
		while(!found) {
			int next = origIter1.next();
			ind ++;
			if(next > i) {
				while(origIter2.hasPrevious()) {
					int previous = origIter2.previous();
					if(previous < i)
						return ind;
					ind --;
				}
				return ind;
			}
		}
		
		
		return ind;
	}


	
}
