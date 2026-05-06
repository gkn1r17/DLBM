/** Stores and processes simulation and output
 * 
 */

package inputOutput;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import control.Runner;
import transportMatrix.GridBox;
import transportMatrix.Phylogeny;

public class Output {
	
	/**Object handling debugging*/
	public final Debug debug = new Debug();
	/**When to next save full population (index within Config.SAVE_TIMESTEPS_ARR)*/
	private int saveI;
	/**When to next "report": print abridged info to screen / log (index within Config.REPORT_TIMESTEPS_ARR)*/
	private int reportI;
	/**When to next print info on new mutants to screen / log (index within Config.MUTANT_TIMESTEPS_ARR)*/
	private int mutantI;
	/**Saves output of "report" for printing to timeseries CSV*/
	private StringBuilder reportForCSV = new StringBuilder();
	


	public long[] startOutput(List<GridBox> activeboxs, int globalDiversity, boolean isController) throws Exception {
		long hour = Runner.startHour;

		
		
		//************CSV FILE WITH GLOBAL/LOCAL DIVERSITIES
		//at top of report for CSV print grid box IDs for collating later 
										//(makes collating process agnostic to how boxs are split up)
		if(Runner.startHour == 0) {
			reportForCSV.append("Location IDs,node," + Runner.runParallel.getRank() + ",");
	
			for(GridBox box : activeboxs)
				reportForCSV.append(box.id + ",");
			reportForCSV.append("\n");
			
			//print the minimum lineage ID for each grid box
			reportForCSV.append("Starting IDs,node," + Runner.runParallel.getRank() + ",");
			for(GridBox box : activeboxs)
				reportForCSV.append(box.getMinID() + ",");
			reportForCSV.append("\n");
		}
		//

		

		
		
		
		//***********SAVE
		//find first time stamp (in days) on which to save
		saveI = 0;
		long saveNext = Runner.settings.saveTimestepsArr[saveI];
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(hour > 0 && hour >= saveNext) {
			saveI++;
			if(saveI >= Runner.settings.saveTimestepsArr.length) {
				System.out.println("WARNING: no save times < loaded start time");
				saveNext = Long.MAX_VALUE;
				break;
			}
			saveNext = Runner.settings.saveTimestepsArr[saveI];
		}
		//save timestamp 0 if requested
		if(saveNext == 0)
				saveNext = save(hour, activeboxs);
		//
		
		
		//***********REPORT
		//find first time stamp (in days) on which to report
		reportI = 0;
		long reportNext = Runner.settings.reportTimestepsArr[reportI];
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(hour > 0 && hour >= reportNext) {
			reportI++;
			if(reportI >= Runner.settings.reportTimestepsArr.length) {
				System.out.println("WARNING: no report times < loaded start time");
				reportNext = Long.MAX_VALUE;
				break;
			}
			reportNext = Runner.settings.reportTimestepsArr[reportI];
		}
		//report timestamp 0 if requested
		if(reportNext == 0)
			reportNext = report(hour, activeboxs, globalDiversity);
		//
		
		
		
		
		//***********SAVE MUTANTS
		//find first time stamp (in days) on which to report
		mutantI = 0;
		long mutantNext = 0;
		if(Runner.settings.sci.mutation == 0 || Runner.settings.mutantTimestepsArr == null) { //not saving phylogeny
			mutantNext = Long.MAX_VALUE;
		}
		else {
				mutantNext = Runner.settings.mutantTimestepsArr[mutantI];
				//in case loaded saved run - find next time stamp (in days) on which to save
				while(hour > 0 && hour >= mutantNext) {
					mutantI++;
					if(mutantI >= Runner.settings.mutantTimestepsArr.length) {
						System.out.println("WARNING: no mutant times < loaded start time");
						mutantNext = Long.MAX_VALUE;
						break;
					}
					mutantNext = Runner.settings.mutantTimestepsArr[mutantI];
				}
				//report timestamp 0 if requested
				if(mutantNext == 0)
					mutantNext = saveMutants(hour, activeboxs);
		}
		//
		
		if(isController)
			FileIO.makeSettingsFile(Runner.runState.simName, Runner.settings.getSettingsIniOut());
		
		debug.oldGlobalDiversity = globalDiversity;

		return new long[] {saveNext, reportNext, mutantNext};
	}
	
	public long saveMutants(long hour, List<GridBox> activeboxs) {
		//mutants
		//all output is currently not in hours but [day]h[hourOfDay] format
		//for back compatibility as formally only counted days
		long day = (long) Math.floor(hour / 24);
		int hourOfDay = (int) (hour - (day * 24));
		String hourDayString = "" + day + "hr" + hourOfDay; 
		
		Phylogeny.saveMutants(Runner.runState.simName,  hourDayString, activeboxs);
		debug.mutantLastSavedHour = hour;
		
		
		//next timestamp
		mutantI ++;
		
		//saveNext = when next to save
		return ((mutantI < Runner.settings.mutantTimestepsArr.length)   ?
							Runner.settings.mutantTimestepsArr[mutantI] :
							Integer.MAX_VALUE
				);
	}
	
	public long save(long hour, List<GridBox> activeboxs) throws Exception {
			//all output is currently not in hours but [day]h[hourOfDay] format
			//for back compatibility as formally only counted days
			long day = (long) Math.floor(hour / 24);
			int hourOfDay = (int) (hour - (day * 24));
			String hourDayString = "" + day + "hr" + hourOfDay; 

			
			for(GridBox box : activeboxs)
				box.combineImmigrants();
			
			//include length of longest row for easier loading into R
			int chunkLength = 2;
			if(Runner.settings.isSelective)
				chunkLength++;
			if(Runner.settings.ctrl.saveBirthHour)
				chunkLength++;
			int lineLength = (activeboxs.stream().mapToInt(GridBox::getNumLins).max().getAsInt() * chunkLength) + 1;
			
			String filename = Runner.runState.simName + "s" + lineLength + "_D" + hourDayString + "_N" + Runner.runParallel.getRank() + ".csv";
			FileIO.savePop(activeboxs, filename, "");
			

			
			//save local/global diversity over time
			logToCSV(Runner.runState.simName);
			
			//next timestamp
			saveI ++;
			
			//saveNext = when next to save
			return ((saveI < Runner.settings.saveTimestepsArr.length)   ?
								Runner.settings.saveTimestepsArr[saveI] :
								Integer.MAX_VALUE
					);
	}	
		
	public long report(long hour, List<GridBox> activeboxs, int globalDiversity) throws Exception {	
		
			//all output is currently not in hours but [day]h[hourOfDay] format
			//for back compatibility as formally only counted days
			long day = (long) Math.floor(hour / 24);

			for(GridBox box : activeboxs)
				box.combineImmigrants();
			
			if(Runner.settings.ctrl.tracerMode) {
				for(GridBox box : activeboxs)
					box.tracerPrint(day, activeboxs);
				
			}
			else {
				reportLocalGlobalDiversity("day," + day + ",year," + Math.floorDiv(day, 365), day, activeboxs, globalDiversity);
			
				
				//report mutant idx for working out age of new individuals
				System.out.print("day(mutantIDs)," + day + ",year," + Math.floorDiv(day, 365));
				reportForCSV.append("day(mutantIDs)," + day + ",year," + Math.floorDiv(day, 365));
				for(GridBox box : activeboxs) { 
					System.out.print("," + box.getCurrentMutantID());
					reportForCSV.append("," + box.getCurrentMutantID());
				}
				reportForCSV.append("\n");
				System.out.println();
			}
			
			
			//next timestamp
			reportI ++;
			
			//reportNext = when next to report
			return ((reportI < Runner.settings.reportTimestepsArr.length)   ?
						Runner.settings.reportTimestepsArr[reportI] :
								Integer.MAX_VALUE
					);	
		}


	/**
	 * 	because only get limited time on cluster so output (twice to be safe in case interrupted) if about to end
	 * @param day
	 * @param hourOfDay
	 * @param activeboxs 
	 * @param checkLetter 
	 * @param neutral 
	 * @param DIR_OUT 
	 * @param FILE_OUT 
	 * @throws Exception
	 */
	public void checkPoint(long hour, List<GridBox> activeboxs, char checkLetter) throws Exception {
		//all output is currently not in hours but [day]h[hourOfDay] format
		//for back compatibility as formally only counted days
		long day = (long) Math.floor(hour / 24);
		int hourOfDay = (int) (hour - (day * 24));
		String hourDayString = "" + day + "hr" + hourOfDay; 
					
		System.out.println("Checkpointing started");
			
		for(GridBox box : activeboxs)
			box.combineImmigrants();
		
		//include length of longest row for easier loading into R
		
		String filename = Runner.runState.simName + "_N" + Runner.runParallel.getRank() + "_CHK" + checkLetter + ".csv";
		FileIO.savePop(activeboxs, filename, "Day," + hourDayString + "\n");

		
		//save local/global diversity over time
		logToCSV(Runner.runState.simName);
		
		System.out.println("Checkpointing completed");

			
	}


	public void logToCSV(String fileOut) throws IOException {
		FileWriter csvWriter = new FileWriter(fileOut + "_N" + Runner.runParallel.getRank() + ".csv", true);
		csvWriter.write(reportForCSV.toString() + "\n");
		csvWriter.close();
		
		//reset
		reportForCSV = new StringBuilder();
	}
	
	
	
	private void reportLocalGlobalDiversity(String timeStr, long day, List<GridBox> activeboxs, int globalDiversity) throws Exception {

		
		
			//print local diversities
			String outStr = timeStr + ",node," + Runner.runParallel.getRank() + "," +
					Arrays.toString(activeboxs.stream().mapToInt(c -> c.getNumLins()).toArray()).replaceAll("(\\[|\\]| )+", "");
			
			reportForCSV.append(outStr + "\n");
			System.out.println(outStr);
			
			if(Runner.runParallel.amIController()) { //don't report global diversity if not controller
				System.out.println("global," + globalDiversity);
				reportForCSV.append("global," + globalDiversity + "\n");
			}
			
			
			
	}



	public void finalOutput(String fileOut, ArrayList<GridBox> activeBoxes) throws Exception {
		
		for(GridBox box : activeBoxes)
			box.combineImmigrants();

		
		logToCSV(fileOut);
		

				
			
			
	}
	
	public class Debug{
		
		private int oldGlobalDiversity;
		private long mutantLastSavedHour = 0;

	
		/** Extra checks (set when carrying out JUnit tests):
		 * 1) Check mutation
		 * @param globalDiversity 
		 * @param runState 
		 * @param hour 
		 * @throws Exception if number of mutants generated is not as expected
		 * 
		 */
		public void debugMutation(int globalDiversity, ArrayList<GridBox> boxs, long hour) throws Exception {
			
			if(Runner.settings.mortality != 0)
				throw new Exception("Debug: Debugging mutation requires Runner.settings.MORTALITY = 0");
			
			//get number of new unique lineages: should equal number of mutations
			int newIndvs = globalDiversity - oldGlobalDiversity;
			
			//get actual recorded number of mutations
			long numMutations = boxs.stream().mapToLong(box -> box.getNumMutants()).sum();
			
			//should match
			if(newIndvs != numMutations)
				throw new Exception("Debug: Mismatch in mutation, expected number of mutants = " + numMutations 
						+ ". Actual number of mutants = " + newIndvs);
			
			long numBirths = boxs.stream().mapToLong(box -> box.getNumBirths()).sum();


			System.out.println("Debug: Proportion of mutations = " + (((double)numMutations) / ((double)numBirths)) + ". "
					+ "(should be around " + Runner.settings.sci.mutation + ")");
					
			
			
			//check output phylogeny
			if(hour == mutantLastSavedHour) {
				long[] maxIDs = Phylogeny.loadMutants(Runner.settings.saveDir, Runner.runState.simName,  boxs, Runner.settings.ctrl.deepDebugMutants, hour);
				
				
				long[] linArray = boxs.stream().flatMapToLong(box -> box.streamLinNums())
						.distinct().sorted().toArray();
				
				if(Runner.settings.ctrl.deepDebugMutants) {
					long[] phylArray = boxs.stream().flatMapToLong(box -> box.streamPhylo())
							.distinct().sorted().toArray();
					
					if(!Arrays.equals(linArray, phylArray))
						throw new Exception("Debug: mismatch between population and individuals loaded from phylogeny files");
				
				}else {
					for(long i : linArray) {
						for(long j : maxIDs) {
							if(i == j)
								throw new Exception("Debug: mismatch between population and individuals loaded from phylogeny files");

						}
					}
				}
				
				System.out.println("Successfully checked saved mutants");
				
				for(GridBox box : boxs) {
					box.clearPhylogeny();
				}
			}
			
			//clear
			boxs.forEach(box -> box.clearMutantDebug());
			oldGlobalDiversity = globalDiversity;
			
		}
	
	}
}
