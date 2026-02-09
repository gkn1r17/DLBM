
/**Wrapper around GridBox objects for testing 
 * whether the same locations contains the same set of lineages (same IDs, quantities, topts) in two experiments.
 * 
 */
package test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import lineages.Lineage;
import transportMatrix.GridBox;

public class GridBoxForComparison implements Comparable<GridBoxForComparison>{

	private final GridBox box;
	
	public GridBoxForComparison(GridBox b) {
		box = b;
	}
	
	
	@Override
	public boolean equals(Object oth) {
		GridBoxForComparison othGBC = (GridBoxForComparison)oth;
		GridBox othBox = othGBC.box;

		if(box.id != othBox.id)
			return false;
		
		TreeSet<Lineage> pop = box.getPopulation();
		TreeSet<Lineage> othPop = othBox.getPopulation();
		
		if(pop.size() != othPop.size())
			return false;
		
		Iterator<Lineage> iter = pop.iterator();
		Iterator<Lineage> othIter = othPop.iterator();
		while(iter.hasNext()) {
			if(!Arrays.equals(iter.next().getDetailsArr(),
								othIter.next().getDetailsArr()))
				return false;
			
		}
		
		return true;
		
	}


	@Override
	public int compareTo(GridBoxForComparison o) {
		return box.compareTo(o.box);
	}



}
