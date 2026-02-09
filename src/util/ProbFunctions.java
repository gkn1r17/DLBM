package util;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;

public class ProbFunctions {
	 

		public static int getBinomial(int n, double probability, Binomial bn, DRand rd) throws Exception {
			if(n == 0)
				return 0;
			else if(n < 0) {
				throw new Exception("Error in getBinomial, num = " + n);
			}
			if(Math.ceil(0.0099 / (n*probability)) > 1) { //if probability too low to calculate binomial (just limitation of library)
				if(rd == null) //either:
					return -1; //return error flag
				else { //make num separate uniform probability decisions with probability prob (functionally same as binomial)
						int outVal = 0;
						for(int i =0; i < n; i++) {
							if(rd.nextDouble() < probability)
								outVal++;
						}
						return outVal;
				}

			}
			else { //if not return binomial
				try {
					return bn.nextInt(n, probability);
				}catch(java.lang.IllegalArgumentException e) {
					throw(new Exception("Error in getBinomial: num = " + n + " prob = " + probability));
				}
			}

		}
}
