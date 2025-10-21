package parallelization;


import java.util.ArrayList;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lbm.GridBox;
import lineages.Lineage;

public class Cluster<T_lin extends Lineage>{ 
	private final DRand rd;
	private final Binomial bn;
	private final Poisson pn;
	private final ArrayList<GridBox> gridBoxes = new ArrayList<GridBox>();
	
	public Cluster(int seed) {
		 rd = new DRand(seed);
		 bn = new Binomial(1, 0.5, new DRand(rd.nextInt()));
		 pn = new Poisson(1, new DRand(rd.nextInt()));
	}
	
	/**Update all gridboxes
	 * 
	 * @param tempTsindx if select int indicate where in temperature cycle we are
	 * @throws Exception
	 */
	public void update(int tempTsindx) throws Exception {
		//combine immigrants dispersed in last update
		     // (if output saved will have already happened - will have no affect)    
		     // (has to happen in separate loop so doesn't accidentally include next time steps immigrants)
		for(GridBox<T_lin> box : gridBoxes) 
			box.combineImmigrants();
		
		//main update loop for each grid box= growth, selection, mortality, dispersal
		for(GridBox<T_lin> box : gridBoxes)
			box.update(rd, bn, pn, tempTsindx);

	}

	public void addGridCell(GridBox<T_lin> gridCell) {
		gridBoxes.add(gridCell);
		gridCell.setupParallelization();
	}
	
	public ArrayList<GridBox> getCells() {
		return gridBoxes;
	}


}