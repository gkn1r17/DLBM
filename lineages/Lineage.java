package lineages;

import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lbm.GridCell;
import lbm.Settings;
import util.ProbFunctions;

public abstract class Lineage implements Comparable<Lineage>{

	public int size = 0;
	protected final int id;
	
	public Lineage(int sz, int id) {
		size = sz;
		this.id = id;
	}
	
	@Override
	public boolean equals(Object oth) {
		if(this == oth)
			return true;
		if(((Lineage)oth).isSunk())
			return false;
		return( ((Lineage)oth).getId() == this.getId());
		
	}

	
	public boolean isSunk() {
		return false;
	}

	@Override
    public int hashCode(){
        return getId();
    }

	@Override
	public int compareTo(Lineage oth) {
		if(this == oth)
			return 0;
		int id1 = this.getId();
		int id2 = oth.getId();
		if(id1 == id2) {
			return oth.isSunk() ? -1 : 0;
		}
			
		return(id1 - id2);
	}

	
	public Float getTopt() {
		throw new UnsupportedOperationException("Can't get topt from lineage in simulation without environmental selection!");
	}

	public String getDetails() {
		return getId() + "," + size;
	}

	public void prepareForMove() {
	}

	public abstract<T extends Lineage> T copy(int num);


	public double getSelectiveGrowth(double temp) {
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

	public void growDie(boolean willGrow, double addee, double temp, boolean dispersing, GridCell gridCell, Poisson pn, Binomial bn, DRand rd) {
		if(willGrow) {
			int add = pn.nextInt(addee  * size * getSelectiveGrowth(temp)); 
			size += add;
			gridCell.size += add;
		}
		int dieNum = ProbFunctions.getBinomial(size, Settings.MORTALITY, bn, rd);
		size -= dieNum;
		gridCell.size -= dieNum;	
	}

	public Lineage makeSunk(int sinkNum) {
		return new SporeLineage(sinkNum, id);
	}
	
}
