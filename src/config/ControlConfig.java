/**Contains scientific configuation parameters from ini/command line
*/

package config;

import control.Runner;

public class ControlConfig {
	
	//------------- OUTPUT/LOADING SETTINGS

	//TODO - should really be hours now for consistency
	/**Total simulation duration in days*/
	public final int durationDay;
	/** NOT FOR FULL SIMULATIONS - switch on for running special simulation to calculate tmins*/
	public final boolean tracerMode;

	
	//OUTPUT
	/**path for where to save output*/
	public final String saveFile;
	/**time in seconds before outputs message just to show hasn't crashed - if no proper update yet happened*/ 
	public final long timeThresh;
	/**Carries out + prints certain debugging checks, currently mostly connected to mutation*/
	public final boolean debug;
	/**Debug mutation*/
	public final boolean debugMutants;
	/**Saves/Loads whole phylogeny to check mutants are working (NOT YET WORKING PROPERLY)*/
	public final boolean deepDebugMutants;
	/**If saving time of creating for every new (mutant) lineage.*/
	public final boolean saveBirthHour;
	/**if true simulation will finish when global diversity = 1*/
	public final boolean stopAt1; 
	//timesteps for output TODO should ultimately be hour for consistency	
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	final String reportTimestepsDay;
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	final String mutantTimestepsDay;
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	final String saveTimestepsDay;
	/**Checkpoint files A and then B produced at this interval*/
	public final int checkpointIntervalDay;



	// LOADING PREVIOUS RUNS
	/**if loading previous run, gives hour to load*/
	final long loadHour;
	/**path for where to load file*/
	public final String loadFile;
	//----

			
	//-------------- PARALLELIZATION
	/**csv file specifying how locations are divided into clusters and clusters into nodes
	 * clusters = parallelized within one computer
	 * nodes = parallelized across computers*/
	public final String clustFile;
	//--
	/**random seed for simulation ( -1 = randomly select random seed) */
	public final int seed;


	
	//------------ MANAGING LINEAGE ID'S: THESE SHOULDN'T NEED TOUCHING
	/**Used internally to distinguish sunken lineages
	 * TODO currently not compatible with mutation
	 */
	public static final long SINK_OFFSET = Long.MAX_VALUE;

	
	
	
	/**
	 * 
	 * @param iniFR Controls reading parameters from ini file/command line
	 * @throws Exception
	 */
	public ControlConfig(IniFileReader iniFR) throws Exception{
		
		
		
		seed = Integer.parseInt(iniFR.getParamValue( "SEED", "Control", false));

	
		durationDay = Integer.parseInt(iniFR.getParamValue( "DURATION_DAY", "TimeStepping", false));
		stopAt1 = iniFR.getParamValue( "STOP_AT_1", "TimeStepping", false).trim().toLowerCase().equals("true");

		tracerMode = iniFR.getParamValue( "TRACER_MODE", "Tracer", false).trim().toLowerCase().equals("true");


		
		////////////////////////////////////////////////InputOutput //////////////////////////////////////////////			

		saveFile = Config.parseFilename(iniFR.getParamValue( "FILE_OUT", "InputOutput", false));
		timeThresh = Long.parseLong(iniFR.getParamValue( "TIME_THRESH", "InputOutput", false));
		saveTimestepsDay = iniFR.getParamValue( "SAVE_TIMESTEPS_DAY", "InputOutput", false);
		reportTimestepsDay = iniFR.getParamValue( "REPORT_TIMESTEPS_DAY", "InputOutput", false);
		mutantTimestepsDay = iniFR.getParamValue( "MUTANT_TIMESTEPS_DAY", "InputOutput", false);
		checkpointIntervalDay = Integer.parseInt(iniFR.getParamValue( "CHECKPOINT_INTERVAL_DAY", "InputOutput", false));
		
		//FIXME
		 /** 	(for back compatibility with some older config files 
		 * 			and because output files haven't yet been adapted to new format
		 * 			i.e. they are still timestamped in days and hours
		 * 		LOAD_DAY=[Day]h[HourOfDay] works in addition to LOAD_HOUR.
		 * 			h[HourOfDay] is optional
		 */
		String loadHourString = iniFR.getParamValue( "LOAD_HOUR", "InputOutput", false);			
		String loadDayString = iniFR.getParamValue( "LOAD_DAY", "InputOutput", true);
		
		//error handling
		if(loadHourString != null && !loadHourString.equals("0") 
				&& loadDayString != null && !loadDayString.equals("0")) {
			throw new IllegalArgumentException("FLOAD_DAY and LOAD_HOUR can't both be specified.");
		}
		
		
		//however is in configure file, file loading time is stored internally as LOAD_HOUR
		//	convert in this section:
																		//POSSIBILITIES = 		
		loadHour = (loadHourString != null && !loadHourString.equals("0")) 
					? 	Integer.parseInt(loadHourString) 				//1) specified in hours
					:   (												//2) specified in days
								(loadDayString != null && !loadDayString.equals("0"))
								?	Config.extractDayAndHour(loadDayString)
								:	0 									//3) NOT LOADING A FILE AT ALL
						);
		Runner.startHour = loadHour;
		//
		
		
		loadFile = Config.parseFilename(iniFR.getParamValue( "FILE_LOAD", "InputOutput", false));
		//if FILE_LOAD set but LOAD_HOUR == 0, load from checkpoint

			
		
		debug = iniFR.getParamValue( "DEBUG", "InputOutput", false).toLowerCase().equals("true");
		
		debugMutants = iniFR.getParamValue( "DEBUG_MUTANTS", "InputOutput", false).toLowerCase().equals("true");
		
		deepDebugMutants = iniFR.getParamValue( "DEEP_DEBUG_MUTANTS", "InputOutput", false).toLowerCase().equals("true");

		
		saveBirthHour = iniFR.getParamValue( "SAVE_BIRTHHOUR", "InputOutput", false).toLowerCase().equals("true");
		
				
		////////////////////////////////////////////////parallelization //////////////////////////////////////////////			
		
		
		clustFile = Config.parseFilename(iniFR.getParamValue( "CLUST_FILE", "Parallelization", false));

	}

}
