package lbm;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import lineages.SelLineage;
import mpi.MPI;
import mpi.Status;
import parallelization.Node;
import parallelization.RunnerParallelization;
import util.FileIO;

public class Output {

	private static int saveI;
	private static int reportI;
	private static int checkpointCounter = 0;

	
	private final static StringBuilder reportForCSV = new StringBuilder();



	public static int[] startOutput(long day, List<GridBox> activeCells, int globalDiversity) throws Exception {
	
		
		//find first time stamp (in days) on which to save
		saveI = 0;
		int saveNext = Settings.SAVE_TIMESTEPS[saveI];
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(day > 0 && day >= saveNext) {
			saveI++;
			saveNext = Settings.SAVE_TIMESTEPS[saveI];
		}
		
		//save timestamp 0 if requested
		save(day, 0, activeCells);
		
		//find first time stamp (in days) on which to report
		reportI = 0;
		int reportNext = Settings.REPORT_TIMESTEPS[reportI];
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(day > 0 && day >= reportNext) {
			reportI++;
			reportNext = Settings.REPORT_TIMESTEPS[reportI];
		}
		
		//report timestamp 0 if requested
		report(day, activeCells, globalDiversity);

		return new int[] {saveNext, reportNext};
	}
	
	
	public static int save(long day, int hourOfDay, List<GridBox> activeCells) throws Exception {
		String hourDayString = "" + day + "hr" + hourOfDay; 
			
			for(GridBox cell : activeCells)
				cell.combineImmigrants();
			
			//include length of longest row for easier loading into R
			int lineLength = (activeCells.stream().mapToInt(GridBox::getNumLins).max().getAsInt() *    
						(Settings.TEMP_FILE == null ? 2 : 3) //line will be longer if topt printed (i.e. in selective simulations)
					) + 1;
			
			String filename = Settings.DIR_OUT + "/" + Settings.FILE_OUT + "s" + lineLength + "_D" + hourDayString + "_N" + RunnerParallelization.getRank() + ".csv";
			FileIO.printAll(activeCells, filename);
			
			
			//next timestamp
			saveI ++;
			
			//saveNext = when next to save
			return ((saveI < Settings.SAVE_TIMESTEPS.length)   ?
								Settings.SAVE_TIMESTEPS[saveI] :
								Integer.MAX_VALUE
					);
	}	
		
	public static int report(long day, List<GridBox> activeCells, int globalDiversity) throws Exception {	
		
			for(GridBox cell : activeCells)
				cell.combineImmigrants();
			
			if(Settings.TRACER_MODE) {
				for(GridBox cell : activeCells)
					cell.tracerPrint(day, activeCells);
				
			}
			else {
				reportLocalGlobalDiversity(day + ",(year " + Math.floorDiv(day, 365) + ")", day, activeCells, globalDiversity);
			}
			
			
			//next timestamp
			reportI ++;
			
			//reportNext = when next to report
			return ((reportI < Settings.REPORT_TIMESTEPS.length)   ?
								Settings.REPORT_TIMESTEPS[reportI] :
								Integer.MAX_VALUE
					);	
		}


	/**
	 * 	because only get limited time on cluster so output (twice to be safe in case interrupted) if about to end
	 * @param day
	 * @param hourOfDay
	 * @param activeCells 
	 * @throws Exception
	 */
	public static void checkPoint(long day, int hourOfDay, List<GridBox> activeCells) throws Exception {
		//print two checkpoints just in case
		//there is no way to ensure cluster won't shut down while checkpointing
		if(checkpointCounter == 2)
			return;
		
					
		System.out.println("Checkpointing started");

		String hourDayString = "" + day + "hr" + hourOfDay; 
			
		for(GridBox cell : activeCells)
			cell.combineImmigrants();
		
		//include length of longest row for easier loading into R
		int lineLength = (activeCells.stream().mapToInt(GridBox::getNumLins).max().getAsInt() *    
					(Settings.TEMP_FILE == null ? 2 : 3) //line will be longer if topt printed (i.e. in selective simulations)
				) + 1;
		
		String filename = Settings.DIR_OUT + "/" + Settings.FILE_OUT + "s" + lineLength + "_D" + hourDayString + "_N" + RunnerParallelization.getRank() + ".csv";
		FileIO.printAll(activeCells, filename);

		
		
		
		checkpointCounter++; 			
		System.out.println("Checkpointing completed");

			
	}


	public static void logToCSV() throws IOException {
		FileWriter csvWriter = new FileWriter(Settings.DIR_OUT + "/" + Settings.FILE_OUT + ".csv", true);
		csvWriter.write(reportForCSV.toString() + "\n");
		csvWriter.close();
	}
	
	
	
	private static void reportLocalGlobalDiversity(String timeStr, long day, List<GridBox> activeCells, int globalDiversity) throws Exception {

		
		
			//print local diversities
			String outStr = timeStr + ",node," + RunnerParallelization.getRank() + "," +
					Arrays.toString(activeCells.stream().mapToInt(c -> c.getNumLins()).toArray()).replaceAll("(\\[|\\]| )+", "");
			
			reportForCSV.append(outStr + "\n");
			System.out.println(outStr);
				
			System.out.println("global," + globalDiversity);
			reportForCSV.append("global," + globalDiversity + "\n");

			
			
			
	}
	
}
