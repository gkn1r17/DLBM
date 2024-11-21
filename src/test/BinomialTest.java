package test;

import java.util.LinkedList;
import java.util.Random;

import cern.colt.Arrays;
import cern.jet.random.Binomial;
import cern.jet.random.Poisson;
import cern.jet.random.engine.DRand;

public class BinomialTest {
	


	public static void main(String[] args) {
		double[] probs = new double[] {1e-5, 1e-1, 0.4, 0.4};
		double totProb = probs[0] + probs[1] + probs[2] + probs[3];
		double[] accumProbs = new double[4];
		
		double[] accumProbs2 = new double[4];
		
		
		Random rd = new Random();
		Binomial bn = new Binomial(1, 0.5, new DRand(rd.nextInt()));
		
		double accum = 0;
		for(int i =0; i < 4; i++) {
			accum+= probs[i];
			accumProbs[i] = accum;
			accumProbs2[i] = accum / totProb;
		}
		
		//random
		
		double[] totals1 = new double[11];
		double[] totals2 = new double[11];
		double[] totals3 = new double[11];
		double[] totals4 = new double[11];
		
		
		double[] totals1b = new double[11];
		double[] totals2b = new double[11];
		double[] totals3b = new double[11];
		double[] totals4b = new double[11];

		
		for(int rep = 0; rep < 1e8; rep ++) {
			if(rep % 1e7 == 0)
				System.out.println(rep);
			int[] chosen = testNormal(rd, totProb, accumProbs);
			
			int[] chosen2 = testBinomial2(rd, bn, totProb, accumProbs2);
			
			for(int i =0 ; i < 10; i ++) {
				totals1[chosen[0]]++;
				totals2[chosen[1]]++;
				totals3[chosen[2]]++;
				totals4[chosen[3]]++;
				
				totals1b[chosen2[0]]++;
				totals2b[chosen2[1]]++;
				totals3b[chosen2[2]]++;
				totals4b[chosen2[3]]++;

			}
			
		}
		
		System.out.println("to box 1 (1e-5)");
		System.out.println(Arrays.toString(totals1).replace("[", "").replace("]", ""));
		System.out.println("to box 2 (1e-4)");
		System.out.println(Arrays.toString(totals2).replace("[", "").replace("]", ""));
		System.out.println("to box 3 (1e-2)");
		System.out.println(Arrays.toString(totals3).replace("[", "").replace("]", ""));
		System.out.println("to box 4 (1e-1)");
		System.out.println(Arrays.toString(totals4).replace("[", "").replace("]", ""));
		
		System.out.println("binomial YO");
		System.out.println("to box 1 (1e-5)");
		System.out.println(Arrays.toString(totals1b).replace("[", "").replace("]", ""));
		System.out.println("to box 2 (1e-4)");
		System.out.println(Arrays.toString(totals2b).replace("[", "").replace("]", ""));
		System.out.println("to box 3 (1e-2)");
		System.out.println(Arrays.toString(totals3b).replace("[", "").replace("]", ""));
		System.out.println("to box 4 (1e-1)");
		System.out.println(Arrays.toString(totals4b).replace("[", "").replace("]", ""));


	}
	
	public static int[] testNormal(Random rd, double totProb, double[] accumProbs) {
		int[] chosen = new int[] {0,0,0,0};
		for(int i =0; i < 10; i++) {
			double val = rd.nextDouble();
			if(val < totProb) {
				for(int i2 = 0; i2 < 4; i2++) {
					if(val < accumProbs[i2]) {
						chosen[i2]++;
						break;
					}
						
				}
			}
		}
		return chosen;
	}
	
	public static int[] testBinomial2(Random rd,Binomial bn, double totProb, double[] accumProbs2){
		int[] chosen = new int[] {0,0,0,0};
		
		int numMov = getBinomial(10, totProb, bn, rd);
		
		for(int i =0 ; i < numMov; i++) {
			double val = rd.nextDouble();
				for(int i2 = 0; i2 < 4; i2++) {
					if(val < accumProbs2[i2]) {
						chosen[i2]++;
						break;
					}
						
				}
		}

		
		return chosen;
	}
	
	public static int[] testBinomial(Random rd,Binomial bn, double totProb, double[] probs, double[] accumProbs){
		int[] chosen = new int[] {0,0,0,0};
		
		//LinkedList<Integer> nms = new LinkedList<Integer>();
		//boolean rewind = false;
		
		//int val = 10;
		for(int i =0; i < 4; i++) {
			//chosen[i] += bn.nextInt(10, probs[i]);
			
			chosen[i]= getBinomial(10, probs[i], bn, rd);
//			nms.addLast(numMov);
//			if(numMov > val) {
//				rewind = true;
//				break;
//			}
//			else 
//				val -= numMov;


		}
		
//		if(!rewind) {
//			for(int i = 0; i < 4; i++) {
//				int numMov = nms.pop();
//				if(numMov > 0)
//					chosen[i] += numMov;
//
//
//			}
//		}
//		else
//			return testNormal(rd, totProb, accumProbs);
		
		return chosen;
	}

	public static int getBinomial(int num, double prob, Binomial bn, Random rd) {
		if(num == 0)
			return 0;
		
		
		//to correct rounding error in cern
		double roundErr = Math.ceil(0.0099 / (num*prob));
		
		if(roundErr > 1) {
			num *= roundErr;
		}
		int res = bn.nextInt(num, prob);
		if(roundErr > 1)
			return rd.nextFloat() <= res / roundErr ? 1 : 0; //NOTE - can only provide a max of so not 100% accurate but chances of max > 1 would be very very small in these circumstances anyway
		return (int) res;

	}
	
}
