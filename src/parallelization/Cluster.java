package parallelization;


import java.util.ArrayList;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lbm.GridBox;

public class Cluster { 
	private final DRand rd;
	private final Binomial bn;
	private final Poisson pn;
	private final ArrayList<GridBox> cells = new ArrayList<GridBox>();
	public final int node;
	
	public Cluster(int seed, int n) {
		 rd = new DRand(seed);
		 bn = new Binomial(1, 0.5, new DRand(rd.nextInt()));
		 pn = new Poisson(1, new DRand(rd.nextInt()));
		 node = n;
	}
	
	public void update(int dayOfYear) {
		for(GridBox cell : getCells()) {
			cell.combineImmigrants();
			cell.clearEmptys();
		}
		for(GridBox cell : getCells())
			cell.update(rd, bn, pn, dayOfYear);

	}

	public void addGridCell(GridBox gridCell) {
		getCells().add(gridCell);
		gridCell.setClust(this);
	}

	public ArrayList<GridBox> getCells() {
		return cells;
	}

}