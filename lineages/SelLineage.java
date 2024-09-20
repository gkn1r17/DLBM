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
	//private float t_growthRate = -1;
	private final float t_tOpt;
	
	private static TreeMap<Integer,Float> tempLins = new TreeMap<Integer,Float>();

	
	protected SelLineage(int sz, int id) {
		
		super(sz, id);
		this.t_tOpt = tempLins.get(id);
	}
	
	protected SelLineage(int sz, int id, float t_tOpt) {
		super(sz, id);
		this.t_tOpt = t_tOpt;
	}
	
	public double getSelectiveGrowth(double tenv) {
		//if(t_growthRate == -1)
			//t_growthRate = tempFunc(t_tOpt, tenv, Settings.W);
		//return t_growthRate;
		
		return tempFunc(t_tOpt, tenv, Settings.W);
	}
	
	public static double tempFunc(float topt, double tenv, float W) {
		return Math.exp( -Math.pow(((tenv - topt)/W),2) );
	}
	
	public Float getTopt() {
		return t_tOpt;
	}
	
	public String getDetails() {
		return super.getDetails() + 
				(Settings.OUTPUT_TOPT ? ("," + t_tOpt)
									: "");
		
		
	}
	
	//public void prepareForMove() {
	//	t_growthRate = -1;
	//}
	
	@Override
	public SelLineage copy(int num) {
		return new SelLineage(num, id,  t_tOpt);
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
