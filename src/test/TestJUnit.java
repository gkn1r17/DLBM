//TODO
//error/boundary checking
//check complete saved output matches
//check output to CSV matches

package test;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import config.SciConfig;
import control.Runner;
import transportMatrix.GridBox;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;


public class TestJUnit {
	
	
	

	
	/**Runs a few sanity checks for mutation i.e. switches off mortality and checks
	 * 1) Number of new individuals = number of mutations counted
	 * 2) Number of new individuals roughly inline with expected mutation rate*/
	@Test
	void testMutationSanity() {
		
		//DEBUG=TRUE with MUTATION > 0 automatically carries out checks above
		runSimulation(new String[]{"NUMNODES", "1", 
				"SETTINGS", "testSettings/testNeutral.ini", 
				"DURATION_DAY", "100", 
				"FILE_LOAD", "none",
				"FILE_OUT", "testMutation/testMutation",
				"SEED","-1",
				"SAVE_TIMESTEPS_DAY", "100",
				"REPORT_TIMESTEPS_DAY", "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,30,40,50,60,70,80,90,100",
				"MUTANT_TIMESTEPS_DAY","100",
				"DISP_HOURS", "8",
				"GROWTH_HOURS","1",
				//"DISP_SCALER","0",
				//mutation test specific settings
				"DEBUG", "true",
				"GROWTH_RATE_DAY", "0.01",
				"MUTATION", "0.01",
				"MORTALITY_DAY", "0",
				"INIT_LIN_SIZE", "100",
				"CLUST_FILE", "clusters6386x1.csv"
				});
		
		//load 1
		runSimulation(new String[]{"NUMNODES", "7", 
				"SETTINGS", "testSettings/testNeutral.ini", 
				"DURATION_DAY", "200", 
				"FILE_LOAD", "testMutation/testMutation",
				"LOAD_DAY", "100",
				"FILE_OUT", "testMutation/testMutation",
				"SEED","-1",
				"SAVE_TIMESTEPS_DAY", "200",
				"MUTANT_TIMESTEPS_DAY","200",
				"REPORT_TIMESTEPS_DAY", "timestepsMonthly.csv",
				"DISP_HOURS", "8",
				"GROWTH_HOURS","1",
				//"DISP_SCALER","0",
				//mutation test specific settings
				"DEBUG", "true",
				"GROWTH_RATE_DAY", "0.001",
				"MUTATION", "0.001",
				"MORTALITY_DAY", "0",
				"INIT_LIN_SIZE", "100"
				});
		
		//load 2
		runSimulation(new String[]{"NUMNODES", "7", 
				"SETTINGS", "testSettings/testNeutral.ini", 
				"DURATION_DAY", "300", 
				"FILE_LOAD", "testMutation/testMutation",
				"LOAD_DAY", "200",
				"FILE_OUT", "testMutation/testMutation",
				"SEED","-1",
				"SAVE_TIMESTEPS_DAY", "300",
				"REPORT_TIMESTEPS_DAY", "timestepsMonthly.csv",
				"DISP_HOURS", "8",
				"GROWTH_HOURS","1",
				//"DISP_SCALER","0",
				//mutation test specific settings
				"DEBUG", "true",
				"GROWTH_RATE_DAY", "0.001",
				"MUTATION", "0.001",
				"MORTALITY_DAY", "0",
				"INIT_LIN_SIZE", "100"
				});
	}
	
	/**Runs neutral simulation and compares to previous run with same seed and same settings*/
	@Test
	void testNeutralAgainstBaseline() {
		
		//neutral
		testSimulation("testSettings/testNeutral.ini", "testResults/testNeutralB", 
				"testResults/testNeutral",  new String[] {});

	}
	
	/**Runs selective simulation and compares to previous run with same seed and same settings*/
	@Test
	void testSelectiveAgainstBaseline() {
		
		//selective
		testSimulation("testSettings/testSelective.ini", "testResults/testSelectiveB", 
				"testResults/testSelective", new String[] {});

	}
	
//	TODO
//	/**Runs neutral simulation and compares to previous run with same seed and same settings*/
//	@Test
//	void testMutationAgainstBaseline() {
//		
//		//neutral
//		testSimulation("testSettings/testMutation.ini", "testResults/testNeutralB", 
//				"testResults/testNeutral",  new String[] {});
//
//	}
//	
//	/**Runs neutral simulation and compares to previous run with same seed and same settings*/
//	@Test
//	void testMutationSelectiveBaseline() {
//		
//		//neutral
//		testSimulation("testSettings/testMutationSelective.ini", "testResults/testNeutralB", 
//				"testResults/testNeutral",  new String[] {});
//
//	}
	
	private void testSimulation(String settingsFile, String outFile, String benchmarkFile, String[] otherArgs) {
		
		
		//NOTE: Not guaranteed "wrong" won't equal - just added this test as crude method to quickly see if
		//if I've messed the tests up so badly they aren't testing anything. Remove if needed.
		//or, alternatively, aren't accidentally saving and reloading just loaded run
		
		int duration = 365;
		
		
		//make baseline
//		runSimulation(new String[]{"NUMNODES", "1", 
//				"SETTINGS", settingsFile, 
//				"DURATION_DAY", "" + duration, 
//				"FILE_LOAD", "none",
//				"FILE_OUT", benchmarkFile,
//				"SEED","100",
//				//TODO
//				//test run saves output but for now will
//				//have to be checked manually
//				"SAVE_TIMESTEPS_DAY", "timesteps.csv",
//				"DEBUG", "true",
//				"CLUST_FILE", "clusters63861.csv"
//				});
//		
		
		
		
		//////////////////////////////////////////////////////////////////
		//************************** SIMULATIONS ************************
		/////////////////////////////////////////////////////////////////
		
		List<GridBoxForComparison> resultsNew = runSimulation(new String[]{"NUMNODES", "7", 
				"SETTINGS", settingsFile, 
				"DURATION_DAY", "" + duration, 
				"FILE_LOAD", "none",
				"FILE_OUT", outFile,
				"SEED","100",
				//TODO
				//test run saves output but for now will
				//have to be checked manually
				"SAVE_TIMESTEPS_DAY", "timesteps.csv",
				"DEBUG", "true",
				"CLUST_FILE", "clusters6386.csv"
				});
		
		List<GridBoxForComparison> resultsLoad = runSimulation(new String[]{"NUMNODES", "7", 
				"SETTINGS", settingsFile, 
				"DURATION_DAY", "" + duration, 
				//loading benchmark run
				"FILE_LOAD", benchmarkFile,
				"LOAD_HOUR", "" + (duration * 24),
				//so doesn't save
				"SAVE_TIMESTEPS_DAY", "" + Integer.MAX_VALUE,
				"DEBUG", "true",
				"CLUST_FILE", "clusters6386.csv"
				});
		

		
		assertTrue(areEqual(resultsNew, resultsLoad));
		
		//check output from new run correctly saved
		
		List<GridBoxForComparison> resultsNewLoad = runSimulation(new String[]{"NUMNODES", "7", 
				"SETTINGS", settingsFile, 
				"DURATION_DAY", "" + duration, 
				//loading benchmark run
				"FILE_LOAD", outFile,
				"LOAD_HOUR", "" + (duration * 24),
				//so doesn't save
				"SAVE_TIMESTEPS_DAY", "" + Integer.MAX_VALUE,
				"DEBUG", "true"

				});
		
		assertTrue(areEqual(resultsNew, resultsNewLoad));

		
		List<GridBoxForComparison> resultsWrong = runSimulation(new String[]{"NUMNODES", "7", 
																			"SETTINGS", settingsFile, 
																			"DURATION_DAY", "" + duration, 
																			"SEED", "200",
																			//so doesn't save
																			"SAVE_TIMESTEPS_DAY", "" + Integer.MAX_VALUE, 
																			"DEBUG", "true"
																	});
		

		
		assertFalse(areEqual(resultsWrong, resultsLoad));		

	}
	
	
	
	/**
	 * 
	 * @param settingsFile
	 * @param numNodes
	 * @param duration
	 * @param seedString
	 * @param fileName
	 * @param loading
	 * @param otherArgs
	 * @return
	 */
	private List<GridBoxForComparison> runSimulation(String[] args) {
		
		
		Runner.main(args);
		
		//did test run to completion?
		assert(Runner.runState.day == Runner.settings.CTRL.DURATION_DAY);
		
		
		List<GridBoxForComparison> results;
		try {
			results = Runner.getFinalResults();
			//are all locations accounted for?
			assert(results.size() == Runner.settings.NUM_BOXES);
			return results;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
				
		return null;

	}
	
	
	private boolean areEqual(List<GridBoxForComparison> results1, 
			List<GridBoxForComparison> results2) {
		
		results1.sort(null);
		results2.sort(null);
		
		Iterator<GridBoxForComparison> res1Iter = results1.iterator();
		Iterator<GridBoxForComparison> res2Iter = results2.iterator();
		
		int i =0;
		while(res1Iter.hasNext()) {
			System.out.println("Comparing box " + i);
			if(!res1Iter.next().equals( res2Iter.next())  )
				return false;
			i++;
		}
		return true;
	}



}
