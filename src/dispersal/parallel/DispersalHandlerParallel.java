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

import java.util.LinkedList;

import dispersal.DispersalHandler;
import lineages.Lineage;
import transportMatrix.GridBox;

public class DispersalHandlerParallel extends DispersalHandler {
	LinkedList<ExtMoveLin> mov = new LinkedList<ExtMoveLin>();
	
	protected class ExtMoveLin{
		private final Lineage lin;
		private final int num;
		
		private ExtMoveLin(Lineage lin, int num) {
			this.lin = lin;
			this.num = num;
		}
	}

	public DispersalHandlerParallel(GridBox dest, double prob) {
		super(dest, prob);
	}


	/**
	 * 
	 * @param copyMov
	 */
	public DispersalHandlerParallel(DispersalHandler copyMov) {
		super(copyMov);
	}



	/**Queues moved Lineage to be added to destination "immigrants"
	 * 
	 * @param lin Lineage to move
	 * @param num number of individuals within Lineage to move
	 */
	@Override
	public void move(Lineage lin, int num) {			
		mov.add(new ExtMoveLin(lin, num) );
	}
	
	/**In serial part of update loop adds Lineage to destination "immigrants"
	 * 
	 */
	public void actuallyMove() {
		while(!mov.isEmpty()) {
			ExtMoveLin xMov = mov.remove();
			dest.disperseToMe(xMov.lin, xMov.num);
			
		}
	}
}
