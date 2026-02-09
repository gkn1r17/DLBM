/**Represents cluster
 * 
 * Grid Boxes are grouped into 
 * "Clusters" (on same machine) and "Nodes" (one each on separate machine)
 * Although when testing locally, even with multiple "Nodes", 
 * 		these will all in practice be on same machine
 * 
 */

package parallelization;


import java.util.ArrayList;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lineages.Lineage;
import transportMatrix.GridBox;

public class Cluster{ 
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
	 * @param hour 
	 * @param originalMutantOffset 
	 * @throws Exception
	 */
	public void update(int tempTsindx, long hour) throws Exception {
		//combine immigrants dispersed in last update
		     // (if output saved will have already happened - will have no affect)    
		     // (has to happen in separate loop so doesn't accidentally include next time steps immigrants)
		for(GridBox box : gridBoxes) 
			box.combineImmigrants();
		
		//main update loop for each grid box= growth, selection, mortality, dispersal
		for(GridBox box : gridBoxes)
			box.update(rd, bn, pn, tempTsindx, hour);

	}

	public void addGridBox(GridBox gridBox) {
		gridBoxes.add(gridBox);
		gridBox.setupParallelization();
	}
	
	public ArrayList<GridBox> getBoxs() {
		return gridBoxes;
	}


}