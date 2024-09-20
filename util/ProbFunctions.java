package util;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;

public class ProbFunctions {
	 
		public static int getBinomial(int num, double prob, Binomial bn, DRand rd) {
			if(num == 0)
				return 0;
			
			
			if(Math.ceil(0.0099 / (num*prob)) > 1) {
				if(rd == null)
					return -1;
				else {
						int outVal = 0;
						for(int i =0; i < num; i++) {
							if(rd.nextDouble() < prob)
								outVal++;
						}
						return outVal;
				}

			}
			else
				return bn.nextInt(num, prob);


		}
}
