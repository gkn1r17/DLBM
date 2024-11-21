package util;

import cern.jet.random.Binomial;
import cern.jet.random.engine.DRand;

public class ProbFunctions {
	 
		public static int getBinomial(int num, double prob, Binomial bn, DRand rd) {
			if(num == 0)
				return 0;
			
			
			if(Math.ceil(0.0099 / (num*prob)) > 1) { //if probability too low to calculate binomial (just limitation of library)
				if(rd == null) //either:
					return -1; //return error flag
				else { //make num separate uniform probability decisions with probability prob (functionally same as binomial)
						int outVal = 0;
						for(int i =0; i < num; i++) {
							if(rd.nextDouble() < prob)
								outVal++;
						}
						return outVal;
				}

			}
			else //if not return binomial
				return bn.nextInt(num, prob);


		}
}
