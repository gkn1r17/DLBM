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
	


	protected SelLineage(int sz, long id, float t_tOpt) { //,  long birthHour, float t_tOpt) {
		super(sz, id); //, birthHour);
		this.t_opt = t_tOpt;
	}
	
	/**Set temperature dependent growth function to t_growthRate (growth coefficient)
	 * 
	 * @param tenv environmental temperature
	 * @param tempChanged if environmental temperature changed so needs recalculating
	 * @return t_growthRate (selective coefficient on growth rate)
	 */
	@Override
	public double getSelectiveGrowth(double tenv, boolean tempChanged, float W) {
		if(tempChanged || t_growthRate == -1)
			t_growthRate = tempFunc(t_opt, tenv, W);
		return t_growthRate;
		
		//return tempFunc(t_tOpt, tenv, Settings.W);
	}
	
	/**Temperature dependent growth function. Sets to t_growthRate
	 * 
	 * @param topt thermal optimum
	 * @param tenv environmental temperature
	 * @param W niche width
	 * @return
	 */
	public static double tempFunc(float topt, double tenv, float W) {
		return Math.exp( -Math.pow(((tenv - topt)/W),2) );
	}
	
	public Float getTopt() {
		return t_opt;
	}
	

	
	/**If move make so will recalculate 
	 * 
	 */
	public void prepareForMove() {
		t_growthRate = -1;
	}
	
	@Override
	public SelLineage copy(int num) {
		return new SelLineage(num, id, t_opt); //birthHour, t_opt);
	}


	/**For efficiency, periodically remove extinct lineages from saved temperatures.
	 * NOTE: temperatures are not saved in SelLineage as this would mean repeated storage for every Lineage at every Location
	 * which means run out of memory 
	 * 
	 * @param allLins surviving lineages
	 */
	public static void trimTempArray(long[] allLins) {
		TreeMap<Long, Float> newTempLins = new TreeMap<Long,Float>();
		for(long lin : allLins) {
			Float topt = Runner.runState.tempLins.remove(lin);
			if(topt != null)
				newTempLins.put(lin, topt);
		}
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
				return new SelLineage(sinkNum, id  - ControlConfig.SINK_OFFSET, t_opt); //birthHour, t_opt); 
		}
		else {
				return new SelLineage(sinkNum, id  + ControlConfig.SINK_OFFSET, t_opt); //birthHour, t_opt); 
		}
	}

	@Override
	protected void addMutants(int numMuts, Phylogeny phylogeny, LinkedList<Lineage> mutants,
										Binomial bn, DRand rd, long hour) throws Exception {
		int numUpTemp = ProbFunctions.getBinomial(numMuts, 0.5, bn, rd);
		float addToTemp = Runner.settings.sci.tempMutIntv;
		for(int i =0; i < numMuts; i++) {
			if(i == numUpTemp)
				addToTemp = addToTemp * -1;
			mutants.add(new SelLineage(1, phylogeny.getNextMutantCounter(),  t_opt + addToTemp)); //hour, t_opt + addToTemp));
		}
	}



	///////////////// OUTPUT //////////////////////
	
	public String getDetails() {
		return super.getDetails() + "," + t_opt;
	}
	
	public double[] getDetailsArr() {
		return new double[] {getId(), size, t_opt};
	}
	

}
