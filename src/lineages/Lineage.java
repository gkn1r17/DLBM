/**Represents single Lineage
 * NOTE: There are huge numbers of this object so variables present are minimised
 * 		 as due to parallelised/distributed architecture must be copied to different clusters/nodes
 * TODO Move birthHour into LineageDetails object extensible for storing any further lineage details
 * 		(object not needing to be copied to different clusters/nodes to save memory)
 */

package lineages;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import config.ControlConfig;
import config.SciConfig;
import control.Runner;
import transportMatrix.GridBox;
import transportMatrix.Phylogeny;
import util.ProbFunctions;

public abstract class Lineage implements Comparable<Lineage>{

	public int size = 0;
	protected final long id;
	protected final long birthHour;
	
	protected Lineage(int sz, long id, long birthHour) {
		size = sz;
		this.id = id;
		this.birthHour = birthHour;
	}
	
	@Override
	public boolean equals(Object oth) {
		if(this == oth)
			return true;
		return( ((Lineage)oth).getId() == this.getId());
		
	}

	
	public boolean isSunk() {
		return id >= ControlConfig.SINK_OFFSET;
	}


	@Override
    public int hashCode(){
		
		//on the basis that in most runs id < Integer.MAX_VALUE 
		return (int)(id % Integer.MAX_VALUE);
    }

	@Override
	public int compareTo(Lineage oth) {
		if(this == oth)
			return 0;
		
		return(this.id == oth.id ? 0 :
				(this.id > oth.id ? 1 : -1)
				);
	}

	
	public Float getTopt() {
		throw new UnsupportedOperationException("Can't get topt from lineage in simulation without environmental selection!");
	}

	public String getDetails() {
		String out = id + "," + size;
		if(Runner.settings.CTRL.SAVE_BIRTHHOUR)
			out = out + "," + birthHour;
		return out;
	}
	
	public double[] getDetailsArr() {
		return new double[] {getId(), size};
	}

	public void prepareForMove() {
	}

	public abstract<T extends Lineage> T copy(int num);


	public double getSelectiveGrowth(double temp, boolean tempChanged, float w) {
		return 1.0;
	};


	public long getId() {
		return id;
	}

	/**
	 * 
	 * @param willGrow
	 * @param growth
	 * @param mortality
	 * @param temp
	 * @param tempChanged
	 * @param gridBox
	 * @param pn
	 * @param bn
	 * @param rd
	 * @param hour 
	 * @return
	 * @throws Exception
	 */
	public int growDie(boolean willGrow, double growth, double mortality,  
			double temp, boolean tempChanged, 
			GridBox gridBox, Poisson pn, Binomial bn, DRand rd, 
			Phylogeny phylogeny, LinkedList<Lineage> mutants, long hour) throws Exception {
		
		//******** GROWTH
		int add = willGrow ?
				ProbFunctions.getBinomial(size, growth  * getSelectiveGrowth(temp, tempChanged, Runner.settings.SCI.W), bn, rd) :
					0;
		//
		
		
		//******** MORTALITY
		int die = ProbFunctions.getBinomial(size, mortality, bn, rd);
		//
		
		
		//******** MUTATION 
		int mutate = (add > 0 && Runner.settings.SCI.MUTATION > 0) ?
					ProbFunctions.getBinomial(add, Runner.settings.SCI.MUTATION, bn, rd) :
						0;
		if(mutate > 0) {
			addMutants(mutate, phylogeny,  mutants, bn, rd, hour);
			phylogeny.addMutants(id, mutate, hour); //add record of mutants for outputting as phylogeny
		}
		if(Runner.settings.CTRL.DEBUG)
			gridBox.debugMutants(mutate, add - die);
		//
		
		
		//add net population size change for *Lineage*
		size += add - die - mutate;
		if(size < 0)
			throw new Exception("error in Lineage growth: size cannot be < 0. size = " + size + " id = " + id);
		
		//return net population size change for *GridBox* 
									//(i.e. net population size change for *Lineage* + mutants)
		return add - die;
		
	}
	
	protected abstract void addMutants(int numMuts, Phylogeny phylogeny, LinkedList<Lineage> mutants,
																						Binomial bn, DRand rd, long hour) throws Exception;
		



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
		if(id >= ControlConfig.SINK_OFFSET) //if already sunk then unsink
			return new NeutralLineage(sinkNum, id  - ControlConfig.SINK_OFFSET, birthHour); 
		else
			return new NeutralLineage(sinkNum, id  + ControlConfig.SINK_OFFSET, birthHour); 
	}
	
	/**Make Lineage from long[] = 
	 * For Lineages that are described in distributed message from other node
	 * 
	 * @param extIm
	 * @param settings
	 * @param tempLins
	 * @param newTemp
	 * @return
	 * @throws Exception
	 */
	public static Lineage makeNewFromDist(long[] extIm, 
			ConcurrentHashMap<Long, Float> tempLins, Float newTemp) throws Exception {
		//id = extIm[0]
		//quantity = extIm[1]
		
		int numNew = (int)extIm[1];
		long id = extIm[0];
		long hourBorn = extIm[2];
		
		return makeNew(id, numNew, hourBorn,  tempLins, newTemp);
		
	}
	
	/**Make Lineage from 
	 * 
	 * @param id
	 * @param numNew
	 * @param hourBorn
	 * @param settings
	 * @param tempLins
	 * @param newTemp
	 * @return
	 * @throws Exception
	 */
	public static Lineage makeNew(long id, int numNew, long hourBorn, 
			ConcurrentHashMap<Long, Float> tempLins, Float newTemp) throws Exception {
		
		if(Runner.settings.SCI.TEMP_FILE == null)
			return new NeutralLineage(numNew, id, hourBorn);
		else {
			if(newTemp == null)
				newTemp = tempLins.get(id);
			if(newTemp == null)
				throw new Exception("Temperature expected but not found for selective lineage " + id);
			return new SelLineage(numNew, id, hourBorn, newTemp);
		}
	}

	public long getBirthHour() {
		return birthHour;
	}


	
}
