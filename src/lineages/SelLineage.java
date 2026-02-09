/**Represents single Lineage with Selection
 * 
 */

package lineages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;
import config.ControlConfig;
import config.SciConfig;
import control.Runner;
import transportMatrix.GridBox;
import transportMatrix.Phylogeny;
import util.ProbFunctions;

public class SelLineage extends Lineage{
	
	
	
	
	/**thermal gamma 
	 * -1 if needs recalculating (due to new lineage, dispersed or temp changed with annual cycle) */
	private double t_growthRate = -1;
	/**thermal optima*/
	private final float t_opt;
	


	protected SelLineage(int sz, long id,  long birthHour, float t_tOpt) {
		super(sz, id, birthHour);
		this.t_opt = t_tOpt;
	}
	
	@Override
	public double getSelectiveGrowth(double tenv, boolean tempChanged, float W) {
		if(tempChanged || t_growthRate == -1)
			t_growthRate = tempFunc(t_opt, tenv, W);
		return t_growthRate;
		
		//return tempFunc(t_tOpt, tenv, Settings.W);
	}
	
	public static double tempFunc(float topt, double tenv, float W) {
		return Math.exp( -Math.pow(((tenv - topt)/W),2) );
	}
	
	public Float getTopt() {
		return t_opt;
	}
	
	public String getDetails() {
		return super.getDetails() + "," + t_opt;
	}
	
	public double[] getDetailsArr() {
		return new double[] {getId(), size, t_opt};
	}
	
	public void prepareForMove() {
		t_growthRate = -1;
	}
	
	@Override
	public SelLineage copy(int num) {
		return new SelLineage(num, id, birthHour, t_opt);
	}


	public static void trimTempArray(LongStream longStream) {
		TreeMap<Long, Float> newTempLins = new TreeMap<Long,Float>();
		longStream.forEach(e ->
					newTempLins.put(e, Runner.runState.tempLins.remove(e))
				);
		
		Runner.runState.tempLins.clear();
		Runner.runState.tempLins.putAll(newTempLins);

	}


	/**Make lineage sunken (or spore) lineage
	 * 
	 * @param sinkNum number to sink
	 * @param neutral if neutral lineages
	 * @return new sunken Lineage
	 */
	public Lineage makeSunk(int sinkNum) {
		
		
		//if already sunk will subtract SINK_OFFSET indicating not sunk
		//if not sunk will add SINK_OFFSET
		//(this complex process is to avoid need of additional variable when huge amounts of lineages)
		if(id >= ControlConfig.SINK_OFFSET) { //if already sunk then unsink
				return new SelLineage(sinkNum, id  - ControlConfig.SINK_OFFSET, birthHour, t_opt); 
		}
		else {
				return new SelLineage(sinkNum, id  + ControlConfig.SINK_OFFSET, birthHour, t_opt); 
		}
	}

	@Override
	protected void addMutants(int numMuts, Phylogeny phylogeny, LinkedList<Lineage> mutants,
																						Binomial bn, DRand rd, long hour) throws Exception {
		int numUpTemp = ProbFunctions.getBinomial(numMuts, 0.5, bn, rd);
		float addToTemp = Runner.settings.SCI.TEMP_MUTINTV;
		for(int i =0; i < numMuts; i++) {
			if(i == numUpTemp)
				addToTemp = addToTemp * -1;
			mutants.add(new SelLineage(1, phylogeny.getNextMutantCounter(), hour, t_opt + addToTemp));
		}
	}




}
