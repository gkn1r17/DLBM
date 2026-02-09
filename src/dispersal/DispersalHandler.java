/**Handles dispersal/movement from one source location to one sink location, 
 * storing individuals ready for dispersal, called "mov" for short 
 * 
 * DispersalHandler = between gridboxes in same cluster
 * 		|		
 * 		DispersalHandlerParallel = between clusters on one node
 * 				|
 * 				DispersalHandlerDistributed = between nodes via MPI message
 * */


package dispersal;

import lineages.Lineage;
import transportMatrix.GridBox;

public class DispersalHandler implements Comparable<DispersalHandler>{

	/**destination location*/
	protected final GridBox dest;
	/**probability*/
	private final double prob;
	/**accumulated probability from iterating through all mov's from one source location*/
	private double accumProb;
	/**normalised accumulated probability */
	private double normedAccumProb;
	
	public DispersalHandler(GridBox dest, double prob) {
		this.dest = dest;
		this.prob = prob;
		
	}
	
	/**Constructor for cloning
	 * 
	 * @param copyMov
	 */
	public DispersalHandler(DispersalHandler copyMov) {
		dest = copyMov.dest;
		prob = copyMov.prob;
		setAccumProb(copyMov.getAccumProb());
		setNormedAccumProb(copyMov.getNormedAccumProb());	
	}

	
	/**Add moved Lineage to destination "immigrants",
	 * 	(Lineages which will be added before next time step)
	 * 
	 * @param lin Lineage to move
	 * @param num number of individuals within Lineage to move
	 */
	public void move(Lineage lin, int num) {			
		dest.disperseToMe(lin, num);
	}
	
	@Override
	public int compareTo(DispersalHandler other) {
        if(this.prob > other.getProb())
        	return -1; //-1 so sorts descending
        else if(this.prob == other.getProb())
        	return 0;
        else
        	return 1;
	}

	public double getAccumProb() {
		return accumProb;
	}

	public void setAccumProb(double accumProb) {
		this.accumProb = accumProb;
	}

	public double getNormedAccumProb() {
		return normedAccumProb;
	}

	public void setNormedAccumProb(double normedAccumProb) {
		this.normedAccumProb = normedAccumProb;
	}

	public double getProb() {
		return prob;
	}

	public GridBox getDest() {
		return dest;
	}

}
