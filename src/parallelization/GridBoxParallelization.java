package parallelization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import dispersal.DispersalHandler;
import dispersal.parallel.DispersalHandlerDistributed;
import dispersal.parallel.DispersalHandlerParallel;
import lbm.GridBox;
import lbm.Settings;
import lineages.Lineage;

public class GridBoxParallelization<T_lin extends Lineage> {
	 
	 private final TreeMap<T_lin, Integer> localImmigrants = new TreeMap<T_lin, Integer>(); //immigrants from same cluster
	 private final LinkedList<int[]> extImmmigrants = new LinkedList<int[]>(); //immigrants from different cluster
	

	/**Record when individual dispersed into this box ready for adding to population */
	public void disperseToMe(T_lin s, int num) {
		localImmigrants.compute(s, (k, v) -> v == null ? num : v + num);
	}
	

	/**Integrate immigrants into population 
	 * @throws Exception */
	public int combineImmigrants(int oldSize, TreeSet<T_lin> population, HashSet<Integer> arrivedFrom, int id) throws Exception {
		if(localImmigrants.isEmpty() && extImmmigrants.isEmpty())
			return oldSize;
		
		int size = oldSize;
		
		extImmmigrants.sort((a,b) -> 
		{
			if(a[0] == b[0])
				return(a[1] - b[1]);
			return (a[0] - b[0]);}
		
		);
		

		///////////////////////////// LINEAGES ALREADY IN LOCAL POPULATION ///////////////////////
		Iterator<int[]> extIter = extImmmigrants.iterator(); 
		int[] nextExt = extImmmigrants.isEmpty() ? null : extIter.next();
		
		int imTotal = localImmigrants.values().stream().mapToInt(i -> i).sum();
		
		
		//// LOOP THROUGH POPULATION
		for(T_lin lin : population) {
			//if(localImmigrants.isEmpty() && extImmmigrants.isEmpty()) //if already added all immigrants
				//return;
			
			
			
			/////// Internal/non distributed immigrants
			//if this member of population has corresponding immigrant then combine them
			Integer imNum = localImmigrants.remove(lin);
			if(imNum != null) {
				lin.size += imNum; //update lineage size
				if(!lin.isSunk()) //update total population size
					size += imNum;
				
				if(size < 0)
					throw new Exception("adding internal immigrants (in pop): size cannot be < 0. size = " + size + " id = " + id);

				
			}
			
			
			
			/////// external/distributed immigrants
			int nextID = lin.getId(); //for distributed, as they arrived by MPI message has to be done by id number not actual lineage "object"

			while(nextExt != null && nextExt[0] <= nextID) {
				if(nextExt[0] == nextID) {
					//add found external lin
					lin.size += nextExt[1];
					if(!lin.isSunk()) {
						size += nextExt[1];
						if(size < 0)
							throw new Exception("adding external immigrants (in pop): size cannot be < 0. size = " + size + " id = " + id);

					}
					extIter.remove(); //so don't add twice
				}				
				nextExt = extIter.hasNext() ? extIter.next() : null;
			}
		}//END LOOP THROUGH POPULATION
		
		
		///////////////////////////// LINEAGES NOT IN LOCAL POPULATION ///////////////////////
		extIter = extImmmigrants.iterator(); 
		nextExt = extImmmigrants.isEmpty() ? null : extIter.next();		
		//// INTERNAL
		while(!localImmigrants.isEmpty()) {
			
			//internal
			Entry<T_lin, Integer> imEntry = localImmigrants.pollFirstEntry();
			Integer imNum = imEntry.getValue();
			T_lin lin = imEntry.getKey();
			if(!lin.isSunk()) {
				size += imNum;
				if(size < 0)
					throw new Exception("size cannot be < 0. size = " + size + " id = " + id);

			}
			Lineage newLin = lin.copy(imNum);
			
			int nextID = newLin.getId();
			if(Settings.TRACER_MODE)
				arrivedFrom.add(nextID);
			
			population.add((T_lin) newLin);
			
			//external (for cases where internal immigrant is also in external list)
					
			while(nextExt != null && nextExt[0] <= nextID) {
				if(nextExt[0] == nextID) {
					if(nextExt[1] > Settings.SINK_OFFSET)
						nextExt[1] = nextExt[1] - Settings.SINK_OFFSET;
	
					
					//add found external lin
					newLin.size += nextExt[1];
					if(!newLin.isSunk()) {
						size += nextExt[1];
						if(size < 0)
							throw new Exception("adding internal immigrants (new): size cannot be < 0. size = " + size + " id = " + id);
	
						
					}
					extIter.remove(); //so don't add twice
				}
				nextExt = extIter.hasNext() ? extIter.next() : null;

			}

		}
		
		
		//// EXTERNAL
		while(!extImmmigrants.isEmpty()) {
			
			
			//if(id == 1107)
				//System.out.println("");

			
			int[] extIm = extImmmigrants.pollFirst();
			
			
			if(Settings.TRACER_MODE)
				arrivedFrom.add(extIm[1]);
			
			T_lin newLin = (T_lin) Lineage.makeNew(extIm);
			population.add(newLin);
			
			if(!newLin.isSunk()) {
				size += extIm[1];
				if(size < 0)
					throw new Exception("adding external immigrants (new): size cannot be < 0. size = " + size + " id = " + id);

			}
		}

		
		
		
		return size;
	}
	

	public void addExt(int[] ext) {
		
		extImmmigrants.add(ext);
		
	}



	
}
