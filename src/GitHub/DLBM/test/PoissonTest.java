package test;

import java.util.Random;

import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;
import lbm.GridCell;
import lbm.Settings;

public class PoissonTest {
	
	static Random rd = new Random();
	static Binomial bn = new Binomial(1, 0.5, new DRand(rd.nextInt()));
	static Poisson pn = new Poisson(1, new DRand(rd.nextInt()));


	public static void main(String[] args) {
		int pop = 10;
		double prob = 1e-7;
		
		for(int step = 0; step < 1e7; step ++) {
			if(step % 10000 == 0)
				System.out.println(step);
			
			pop += pn.nextInt(prob * pop);
			
			int remove = 0;
			for(int i =0; i < pop; i++) {
				if(rd.nextDouble() < prob)
					remove++;
			}
			pop -= remove;
		}
		
		System.out.println(pop);
		
		//wholePopTest(args);
	}


	private static void wholePopTest(String[] args) {
		//Settings.loadSettings(args);
		int pop = Settings.INITIAL_P;
		
		for(int step = 0; step < 1e6; step ++) {
			
			if(step % 10000 == 0)
				System.out.println(step);
		
			if(Settings.CC > pop) {
				double prob = ((1.0 - ((double)pop / Settings.CC)) * Settings.GROWTH_RATE);
				pop += pn.nextInt(prob * pop);
			}
			int remove = 0; //GridCell.getBinomial(pop, Settings.MORTALITY, bn, rd);
			
			if(remove == -1) { //can't do binomial
				remove = 0;
				for(int i =0; i < pop; i++) {
					if(rd.nextDouble() < Settings.MORTALITY)
						remove++;
				}
			}
				
			pop -= remove;
			
//			//dispersal
//			int change = GridCell.getBinomial(pop, 0.01, bn, rd);
//			
//			if(change != -1) {
//				pop += (rd.nextBoolean() ?
//						change :
//					-change);
//			}
			
		}
		System.out.println(pop);
		
	}

}
