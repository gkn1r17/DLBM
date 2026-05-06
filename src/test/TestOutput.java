package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

public class TestOutput {

	@Test
	void testNeutralSimulations() {
		
		//neutral
		compareCSV("testResults/testNeutral", "testResults/testNeutralB");

	}

	@Test
	void testSelectiveSimulations() {
		
		//selective
		compareCSV("testSettings/testSelective", "testResults/testSelectiveB");

	}
	
	
	
	
	private void compareCSV(String baselineTest, String newTest) {
		Scanner baseData;
		try {
			baseData = new Scanner(new File(baselineTest));
			ArrayList<int[]> locals = new ArrayList<int[]>();
			ArrayList<Integer> global = new ArrayList<Integer>();

			while(baseData.hasNextLine()) {
				String intStr = baseData.nextLine();
				
				//regex
				//
				//if(intStr.startsWith(locPrefix)) {
				//	
				//}
				
				Arrays.asList(intStr.split(",")).stream().mapToInt(e -> Integer.parseInt(e)).toArray();
			}
			baseData.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	

}
