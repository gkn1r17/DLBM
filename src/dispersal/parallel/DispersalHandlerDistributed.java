/**Handles dispersal/movement from one source location to one sink location, 
 * storing individuals ready for dispersal, called "mov" for short 
 * 
 * DispersalHandler = between gridboxes in same cluster
 * 		|		
 * 		DispersalHandlerParallel = between clusters on one node
 * 				|
 * 				DispersalHandlerDistributed = between nodes via MPI message
 * */


package dispersal.parallel;

import java.util.Map.Entry;
import java.util.TreeMap;

import dispersal.DispersalHandler;
import lineages.Lineage;
import transportMatrix.GridBox;

public class DispersalHandlerDistributed extends DispersalHandlerParallel {
	TreeMap<Lineage,Integer> mov = new TreeMap<Lineage,Integer>();
	
	public DispersalHandlerDistributed(GridBox dest, double prob) {
		super(dest, prob);
	}
	
	public DispersalHandlerDistributed(DispersalHandler copyMov) {
		super(copyMov);
	}

	/**Queues moved Lineage to be sent to other node via MPI
	 * 
	 * @param lin Lineage to move
	 * @param num number of individuals within Lineage to move
	 */
	@Override
	public void move(Lineage lin, int num) {
		mov.put(lin, num);
	}

	/**Adds number of each queued Lineage to move to to movMap.
	 * 
	 * @param movMap will be formatted into int array for sending via MPI
	 */
	public void collateMovs(TreeMap<GridBox,TreeMap<Lineage,Integer>> movMap) {
		if(mov.isEmpty())
			return;
		TreeMap<Lineage, Integer> alreadyThere = movMap.putIfAbsent(getDest(),mov);
		if(alreadyThere != null)
			for(Entry<Lineage, Integer> m : mov.entrySet()) {
				int num = m.getValue();
				alreadyThere.compute(m.getKey(), (k, v) -> v == null ? 
						num : v + num);
			}
		mov = new TreeMap<Lineage,Integer>();
	}
}
