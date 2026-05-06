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
	
	
	

	
	private static final boolean MAKING_NEW_BASELINE = true;


	
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
	

	/**Runs neutral simulation and compares to previous run with same seed and same settings*/
	@Test
	void testMutationAgainstBaseline() {
		
		//neutral
		testSimulation("testSettings/testMutation.ini", "testResults/testNeutralB", 
				"testResults/testNeutral",  new String[] {});

	}

	/**Runs mutation+selective simulation and compares to previous run with same seed and same settings*/
	@Test
	void testMutationSelectiveBaseline() {
		
		//neutral
		testSimulation("testSettings/testMutationSelective.ini", "testResults/testMutationSelectiveB", 
				"testResults/testMutationSelective",  new String[] {});

	}
	
	/**Carry out set of simulations for one "scenario" (e.g. neutral without mutation, selective with mutation etc.).
	 * Compares new results with pre saved ("baseline") results
	 * Process is:
	 * 
	 *  (optional make new baseline)
	 *  Run simulation with same seed as baseline
	 *  Load baseline
	 *  check matches
	 *  Reload new simulation results
	 *  check matches (i.e. checking loading works correctly)
	 *  Run simulation with different seed
	 *  check doesn't match
	 *  
	 * 
	 * @param settingsFile path to settings file, i.e. follows SETTINGS in command line
	 * @param outFile filename new results are saved to
	 * @param benchmarkFile filename for baseline file
	 * @param otherArgs command args array
	 */
	private void testSimulation(String settingsFile, String outFile, String benchmarkFile, String[] otherArgs) {
		
		
		//NOTE: Not guaranteed "wrong" won't equal - just added this test as crude method to quickly see if
		//if I've messed the tests up so badly they aren't testing anything. Remove if needed.
		//or, alternatively, aren't accidentally saving and reloading just loaded run
		
		int duration = 365;
		
		
		
		if(MAKING_NEW_BASELINE) {
			//make baseline
			runSimulation(new String[]{"NUMNODES", "1", 
					"SETTINGS", settingsFile, 
					"DURATION_DAY", "" + duration, 
					"FILE_LOAD", "none",
					"FILE_OUT", benchmarkFile,
					"SEED","100",
					//TODO
					//test run saves output but for now will
					//have to be checked manually
					"SAVE_TIMESTEPS_DAY", "timesteps.csv",
					"DEBUG", "true",
					"CLUST_FILE", "clusters63861.csv"
					});
		}
		
		
		
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
	
	
	
	/**Run one simulation
	 * 
	 * @param args command line args
	 * @return list of results for each gridbox
	 */
	protected List<GridBoxForComparison> runSimulation(String[] args) {
		
		
		Runner.main(args);
		
		//did test run to completion?
		assert(Runner.runState.day == Runner.settings.ctrl.durationDay);
		
		
		List<GridBoxForComparison> results;
		try {
			results = Runner.getFinalResults();
			//are all locations accounted for?
			assert(results.size() == Runner.settings.numBoxes);
			return results;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
				
		return null;

	}
	
	
	/**Compare two sets of results are equal
	 * 
	 * @param results1 list of results for simulation 1 (each gridbox)
	 * @param results2 list of results for simulation 2 (each gridbox)
	 * @return
	 */
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
