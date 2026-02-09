/**Represents single Lineage without Selection
 * 
 */

package lineages;

import java.util.LinkedList;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;
import config.SciConfig;
import transportMatrix.GridBox;
import transportMatrix.Phylogeny;

public class NeutralLineage extends Lineage {

	protected NeutralLineage(int sz, long id, long birthHour) {
		super(sz, id, birthHour);
	}
	
	@Override
	public NeutralLineage copy(int num) {
		return new NeutralLineage(num, id, birthHour);

	}
	
	@Override
	public double getSelectiveGrowth(double temp, boolean tempChanged, float W) {
		return 1;
	}

	@Override
	protected void addMutants(int numMuts, Phylogeny phylogeny, LinkedList<Lineage> mutants,
			Binomial bn, DRand rd, long hour) throws Exception {
			for(int i =0; i < numMuts; i++)
				mutants.add(new NeutralLineage(1, phylogeny.getNextMutantCounter(), hour));
	}






}
