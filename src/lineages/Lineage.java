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

	/** Number of individuals temporarily unavailable for transport. */
	public int unavailable = 0;
	
	/** Number of individuals eligible for transport. */
	public int available() {
		return size - unavailable;
	}
	
	/** Restore transport eligibility to the entire lineage. */
	public void resetAvailability() {
		unavailable = 0;
	}

	protected final long id;
	//protected final long birthHour;
	
	protected Lineage(int sz, long id) { //, long birthHour) {
		size = sz;
		this.id = id;
		//this.birthHour = birthHour;
	}
	
	@Override
	public boolean equals(Object oth) {
		if(this == oth)
			return true;
		return( ((Lineage)oth).getId() == this.getId());
		
	}

	
	public boolean isDormant() {
		return id < 0;
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
		//if(Runner.settings.CTRL.SAVE_BIRTHHOUR)
			//out = out + "," + birthHour;
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
	
		double growthProb = growth * getSelectiveGrowth(
				temp, tempChanged, Runner.settings.sci.W);
	
		int add;
		int die;
		int mutate;
	
		if (Runner.settings.sci.unavailableDays == 0) {
	
			// Original demographic calculations.
			add = willGrow
					? ProbFunctions.getBinomial(size, growthProb, bn, rd)
					: 0;
	
			die = ProbFunctions.getBinomial(size, mortality, bn, rd);
	
			mutate = (add > 0 && Runner.settings.sci.mutation > 0)
					? ProbFunctions.getBinomial(
							add, Runner.settings.sci.mutation, bn, rd)
					: 0;
	
			if (mutate > 0) {
				addMutants(mutate, 0, phylogeny, mutants, bn, rd, hour);
			}
	
		} else {
	
			int available = size - unavailable;
	
			// Births inherit parental transport availability.
			int addAvailable = willGrow
					? ProbFunctions.getBinomial(available, growthProb, bn, rd)
					: 0;
	
			int addUnavailable = willGrow
					? ProbFunctions.getBinomial(unavailable, growthProb, bn, rd)
					: 0;
	
			add = addAvailable + addUnavailable;
	
			// Mortality acts independently on both populations.
			int dieAvailable = ProbFunctions.getBinomial(
					available, mortality, bn, rd);
	
			int dieUnavailable = ProbFunctions.getBinomial(
					unavailable, mortality, bn, rd);
	
			die = dieAvailable + dieUnavailable;
	
			// Mutation occurs only among newborn cells.
			int mutateAvailable = (addAvailable > 0
					&& Runner.settings.sci.mutation > 0)
					? ProbFunctions.getBinomial(
							addAvailable, Runner.settings.sci.mutation, bn, rd)
					: 0;
	
			int mutateUnavailable = (addUnavailable > 0
					&& Runner.settings.sci.mutation > 0)
					? ProbFunctions.getBinomial(
							addUnavailable, Runner.settings.sci.mutation, bn, rd)
					: 0;
	
			mutate = mutateAvailable + mutateUnavailable;
	
			if (mutate > 0) {
				addMutants(mutate, mutateUnavailable,
						phylogeny, mutants, bn, rd, hour);
			}
	
			// Update unavailable abundance.
			// Mutant offspring leave this lineage.
			unavailable += addUnavailable
					- dieUnavailable
					- mutateUnavailable;
		}
	
		if (mutate > 0 && Runner.settings.mutantTimestepsArr != null) {
			phylogeny.addMutants(id, mutate, hour);
		}
	
		if (Runner.settings.ctrl.debugMutants) {
			gridBox.debugMutants(mutate, add - die);
		}
	
		// Update total abundance of this lineage.
		size += add - die - mutate;
	
		if (size < 0) {
			throw new Exception(
					"error in Lineage growth: size cannot be < 0. size = "
					+ size + " id = " + id);
		}
	
		if (unavailable < 0 || unavailable > size) {
			throw new Exception(
					"Invalid unavailable population: unavailable = "
					+ unavailable + " size = " + size + " id = " + id);
		}
	
		// Includes mutant offspring added to other lineages.
		return add - die;
	}
	
	/**When mutating, add mutants to temporary cache to add to population before next updating
	 * (avoids concurrent modification of population)
	 * 
	 * @param numMuts number of mutants
	 * @param phylogeny phylogeny holds record of all mutants up to now - importantly for this method holds "counter" so can add 
	 * 																		mutant with next available ID
	 * @param mutants mutants added this time step at this gridbox - to add new mutants too
	 * @param bn Binomial used in getBinomial(...) to randomly adjust temperature in selective mutation (choose number to increment temp upwards)
	 * @param rd DRand used in getBinomial(...) to randomly adjust temperature in selective mutation (choose number to increment temp upwards)
	 * @param hour hour of mutation
	 * @throws Exception
	 */
	protected abstract void addMutants(int numMuts, int unavailableMuts,
			Phylogeny phylogeny, LinkedList<Lineage> mutants,
			Binomial bn, DRand rd, long hour) throws Exception;


	/**Create a lineage in the opposite dormancy state.
	 * 
	 * @param switchNum number of individuals switching state
	 * @return new Lineage in the opposite dormancy state
	 */
	public Lineage switchDormancyState(int switchNum) throws Exception {
		if(id == 0)
			throw new Exception("Lineage ID 0 is reserved and cannot switch dormancy state");
	
		return new NeutralLineage(switchNum, -id);
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
		long hourBorn = Runner.settings.ctrl.saveBirthHour == true
						? extIm[2]
						: 0;
		
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
		
		if(Runner.settings.sci.tempFile == null)
			return new NeutralLineage(numNew, id); //, hourBorn);
		else {
			if(newTemp == null)
				newTemp = tempLins.get(id);
			if(newTemp == null)
				throw new Exception("Temperature expected but not found for selective lineage " + id);
			return new SelLineage(numNew, id, newTemp); //hourBorn, newTemp);
		}
	}

	public long getBirthHour() {
		//return birthHour;
		return 0;
	}


	
}
