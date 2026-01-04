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



	public static long[] startOutput(List<GridBox> activeCells, int globalDiversity, Settings settings) throws Exception {
	
		
		//find first time stamp (in days) on which to save
		saveI = 0;
		long saveNext = settings.SAVE_TIMESTEPS_ARR[saveI];
		
		long hour = settings.LOAD_HOUR;
		
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(hour > 0 && hour >= saveNext) {
			saveI++;
			saveNext = settings.SAVE_TIMESTEPS_ARR[saveI];
		}
		
		//save timestamp 0 if requested
		if(saveNext == 0)
				saveNext = save(hour, activeCells, settings);
		
		//find first time stamp (in days) on which to report
		reportI = 0;
		long reportNext = settings.REPORT_TIMESTEPS_ARR[reportI];
		//in case loaded saved run - find next time stamp (in days) on which to save
		while(hour > 0 && hour >= reportNext) {
			reportI++;
			reportNext = settings.REPORT_TIMESTEPS_ARR[reportI];
		}
		
		//report timestamp 0 if requested
		if(reportNext == 0)
			reportNext = report(hour, activeCells, globalDiversity, settings);

		return new long[] {saveNext, reportNext};
	}
	
	
	public static long save(long hour, List<GridBox> activeCells, Settings settings) throws Exception {
			//all output is currently not in hours but [day]h[hourOfDay] format
			//for back compatibility as formally only counted days
			long day = (long) Math.floor(settings.LOAD_HOUR / 24);
			int hourOfDay = (int) (settings.LOAD_HOUR - (day * 24));
			String hourDayString = "" + day + "hr" + hourOfDay; 

			
			for(GridBox cell : activeCells)
				cell.combineImmigrants();
			
			//include length of longest row for easier loading into R
			int lineLength = (activeCells.stream().mapToInt(GridBox::getNumLins).max().getAsInt() *    
						(settings.TEMP_FILE == null ? 2 : 3) //line will be longer if topt printed (i.e. in selective simulations)
					) + 1;
			
			String filename = settings.FILE_OUT + "s" + lineLength + "_D" + hourDayString + "_N" + RunnerParallelization.getRank() + ".csv";
			FileIO.printAll(activeCells, filename);
			
			
			//next timestamp
			saveI ++;
			
			//saveNext = when next to save
			return ((saveI < settings.SAVE_TIMESTEPS_ARR.length)   ?
								settings.SAVE_TIMESTEPS_ARR[saveI] :
								Integer.MAX_VALUE
					);
	}	
		
	public static long report(long hour, List<GridBox> activeCells, int globalDiversity, Settings settings) throws Exception {	
		
			//all output is currently not in hours but [day]h[hourOfDay] format
			//for back compatibility as formally only counted days
			long day = (long) Math.floor(settings.LOAD_HOUR / 24);

		
		
			for(GridBox cell : activeCells)
				cell.combineImmigrants();
			
			if(settings.TRACER_MODE) {
				for(GridBox cell : activeCells)
					cell.tracerPrint(day, activeCells);
				
			}
			else {
				reportLocalGlobalDiversity(day + ",(year " + Math.floorDiv(day, 365) + ")", day, activeCells, globalDiversity);
			}
			
			
			//next timestamp
			reportI ++;
			
			//reportNext = when next to report
			return ((reportI < settings.REPORT_TIMESTEPS_ARR.length)   ?
						settings.REPORT_TIMESTEPS_ARR[reportI] :
								Integer.MAX_VALUE
					);	
		}


	/**
	 * 	because only get limited time on cluster so output (twice to be safe in case interrupted) if about to end
	 * @param day
	 * @param hourOfDay
	 * @param activeCells 
	 * @param neutral 
	 * @param DIR_OUT 
	 * @param FILE_OUT 
	 * @throws Exception
	 */
	public static void checkPoint(long hour, List<GridBox> activeCells, Settings settings) throws Exception {
		//all output is currently not in hours but [day]h[hourOfDay] format
		//for back compatibility as formally only counted days
		long day = (long) Math.floor(settings.LOAD_HOUR / 24);
		int hourOfDay = (int) (settings.LOAD_HOUR - (day * 24));
		String hourDayString = "" + day + "hr" + hourOfDay; 

		
		
		//print two checkpoints just in case
		//there is no way to ensure cluster won't shut down while checkpointing
		if(checkpointCounter == 2)
			return;
		
					
		System.out.println("Checkpointing started");
			
		for(GridBox cell : activeCells)
			cell.combineImmigrants();
		
		//include length of longest row for easier loading into R
		int lineLength = (activeCells.stream().mapToInt(GridBox::getNumLins).max().getAsInt() *    
					(settings.TEMP_FILE == null ? 2 : 3) //line will be longer if topt printed (i.e. in selective simulations)
				) + 1;
		
		String filename = settings.FILE_OUT + "s" + lineLength + "_D" + hourDayString + "_N" + RunnerParallelization.getRank() + ".csv";
		FileIO.printAll(activeCells, filename);

		
		
		
		checkpointCounter++; 			
		System.out.println("Checkpointing completed");

			
	}


	public static void logToCSV(String FILE_OUT) throws IOException {
		FileWriter csvWriter = new FileWriter(FILE_OUT + ".csv", true);
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
