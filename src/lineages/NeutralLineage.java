package lineages;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;
import lbm.GridBox;
import lbm.Settings;

public class NeutralLineage extends Lineage {

	public NeutralLineage(int sz, int id) {
		super(sz, id);
	}
	
	@Override
	public NeutralLineage copy(int num) {
		return new NeutralLineage(num, id);

	}
	
	@Override
	public double getSelectiveGrowth(double temp, boolean tempChanged) {
		return 1;
	}



}
