package lineages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lbm.Settings;

public class SelLineage extends Lineage{
	
	
	/**mapes every lineage id to it's t_opt*/
	private static TreeMap<Integer,Float> tempLins = new TreeMap<Integer,Float>();
	
	
	/**thermal gamma 
	 * -1 if needs recalculating (due to new lineage, dispersed or temp changed with annual cycle) */
	private double t_growthRate = -1;
	/**thermal optima*/
	private final float t_opt;
	

	/**
	 * 
	 * @param sz size
	 * @param id
	 */
	protected SelLineage(int sz, int id) {
		
		super(sz, id);
		this.t_opt = tempLins.get(isSunk() ? (id - Settings.SINK_OFFSET) : id);
		
		//System.out.println(id + "," + t_tOpt);
	}

	protected SelLineage(int sz, int id, float t_tOpt) {
		super(sz, id);
		this.t_opt = t_tOpt;
	}
	
	@Override
	public double getSelectiveGrowth(double tenv, boolean tempChanged) {
		if(tempChanged || t_growthRate == -1)
			t_growthRate = tempFunc(t_opt, tenv, Settings.W);
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
		return new SelLineage(num, id,  t_opt);
	}

	public static void addTemp(int id, float temp) {
		tempLins.put(id, temp);
		
	}

	public static void trimTempArray(IntStream globLins) {
//		TreeMap<Integer, Float> newTempLins = new TreeMap<Integer,Float>();
//		globLins.forEach(e ->
//					newTempLins.put(e, tempLins.get(e))
//				);
//		
//		tempLins = newTempLins;
	}



}
