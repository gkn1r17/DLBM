package lbm;


import java.util.ArrayList;
import java.util.TreeMap;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;

public class Cluster { 
	private final DRand rd;
	private final Binomial bn;
	private final Poisson pn;
	final ArrayList<GridCell> cells = new ArrayList<GridCell>();
	public final int node;
	
	public Cluster(int seed, int n) {
		 rd = new DRand(seed);
		 bn = new Binomial(1, 0.5, new DRand(rd.nextInt()));
		 pn = new Poisson(1, new DRand(rd.nextInt()));
		 node = n;
	}
	
	public void update(int dayOfYear) {
		for(GridCell cell : cells) {
			cell.combineImmigrants();
			cell.clearEmptys();
		}
		for(GridCell cell : cells)
			cell.update(rd, bn, pn, dayOfYear);

	}

	public void addGridCell(GridCell gridCell) {
		cells.add(gridCell);
		gridCell.setClust(this);
	}

	
}