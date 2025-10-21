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

import lbm.GridBox;
import lbm.Runner;
import lbm.Settings;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;


public class TestJUnit {
	
	
	void loadRun() {
		//assert( day is same )
		//assert(hour is same)
		//assert(temps are same)
		//assert(populations are same)
	}
	
	//checks to add:
	//missing savetimesteps file
	//cluster max not matching numnodes
	
	@Test
	void testAllSimulations() {
		testOneSimulation("testSettings/testNeutral.ini", "testResults/testNeutral", new String[] {});
		testOneSimulation("testSettings/testSelective.ini", "testResults/testSelective", new String[] {});

	}
	
	private void testOneSimulation(String settingsFile, String outFile, String[] otherArgs) {
		
		
		List<GridBoxForComparison> neutralNew = runSimulation(settingsFile, 7, 365,
				"100", outFile,
				false,
				otherArgs);


		
		List<GridBoxForComparison> neutralLoad = runSimulation(settingsFile, 7, 365,
				"100", outFile,
				true,
				otherArgs);

		assertTrue(areEqual(neutralNew, neutralLoad));

		//NOTE: Not guaranteed "wrong" won't equal - just added this test as crude method to quickly see if
		//if I've messed the tests up so badly they aren't testing anything. Remove if needed.
		//or, alternatively, aren't accidentally saving and reloading just loaded run
		
		List<GridBoxForComparison> neutralWrong = runSimulation(settingsFile, 7, 365,
				"200", outFile,
				false,
				otherArgs);

		
		assertFalse(areEqual(neutralWrong, neutralLoad));

		

		
		


	}
	
	
	private boolean areEqual(List<GridBoxForComparison> results1, 
			List<GridBoxForComparison> results2) {
		
		results1.sort(null);
		results2.sort(null);
		
		Iterator<GridBoxForComparison> res1Iter = results1.iterator();
		Iterator<GridBoxForComparison> res2Iter = results2.iterator();
		
		while(res1Iter.hasNext()) {
			if(!res1Iter.next().equals( res2Iter.next())  )
				return false;
		}
		return true;
		
		// TODO Auto-generated method stub
		
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
	private List<GridBoxForComparison> runSimulation(String settingsFile, int numNodes, int duration, 
			String seedString, String fileName, boolean loading, String[] otherArgs) {
		
		
		
		//*************** PUT ARGS TOGETHER ********************
		String[] argsStr = new String[otherArgs.length + 
		                              (loading ? 14 : 10)];
		argsStr[0] = "NUMNODES";
		argsStr[1] = "" + numNodes;
		argsStr[2] = "SETTINGS";
		argsStr[3] = settingsFile;
		argsStr[4] = "DURATION";
		argsStr[5] = "" + duration;
		argsStr[6] = "SEED";
		argsStr[7] = seedString;

		int nextIdx = 10;
		
		if(loading) {
			argsStr[8] = "LOAD_FILE";
			argsStr[9] = fileName;
			argsStr[10] = "LOAD_DAY";
			argsStr[11] = "" + duration;
			argsStr[12] = "SAVE_TIMESTEPS_FILE";
			argsStr[13] = "none"; //default i.e. don't save
			
			nextIdx = 14;
		}
		else {
			argsStr[8] = "FILE_OUT";
			argsStr[9] = fileName;
		}
		for(int i =0; i < otherArgs.length; i++) {
			argsStr[nextIdx + i] = otherArgs[i];
		}
		//*******************************
		
		Runner.main(argsStr);
		
		assert(Runner.getDay() == duration);
		
		
		List<GridBoxForComparison> results = Runner.getAllLocs().stream().map
				(box -> new GridBoxForComparison(box)).distinct().collect(Collectors.toList());
		
		assert(results.size() == Settings.NUM_BOXES);
		
		return results;

	}

}
