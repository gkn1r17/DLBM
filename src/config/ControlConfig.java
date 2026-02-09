/**Contains scientific configuation parameters from ini/command line
*/

package config;


public class ControlConfig {
	
	//------------- OUTPUT/LOADING SETTINGS

	//TODO - should really be hours now for consistency
	/**Total simulation duration in days*/
	public final int DURATION_DAY;
	/** NOT FOR FULL SIMULATIONS - switch on for running special simulation to calculate tmins*/
	public final boolean TRACER_MODE;

	
	//OUTPUT
	/**path for where to save output*/
	public final String SAVE_FILE;
	/**time in seconds before outputs message just to show hasn't crashed - if no proper update yet happened*/ 
	public final long TIME_THRESH;
	/**Carries out + prints certain debugging checks, currently mostly connected to mutation*/
	public final boolean DEBUG;
	/**Saves/Loads whole phylogeny to check mutants are working (NOT YET WORKING PROPERLY)*/
	public final boolean DEEP_DEBUG_MUTANTS;
	/**should be set to length of time available on HPC, to ensure prints loadable output immediately before job evicted*/
	public final double CHECKPOINT_HOURS;
	/**If saving time of creating for every new (mutant) lineage.*/
	public final boolean SAVE_BIRTHHOUR;
	/**if true simulation will finish when global diversity = 1*/
	public final boolean STOP_AT_1; 
	//timesteps for output TODO should ultimately be hour for consistency	
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	public final String REPORT_TIMESTEPS_DAY;
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	public final String MUTANT_TIMESTEPS_DAY;
	/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
	public final String SAVE_TIMESTEPS_DAY;

	// LOADING PREVIOUS RUNS
	/**if loading previous run, gives hour to load*/
	public final long LOAD_HOUR;
	/**path for where to load file*/
	public final String LOAD_FILE;
	//----

			
	//-------------- PARALLELIZATION
	/**csv file specifying how locations are divided into clusters and clusters into nodes
	 * clusters = parallelized within one computer
	 * nodes = parallelized across computers*/
	public final String CLUST_FILE;
	//--
	

	
	//------------ MANAGING LINEAGE ID'S: THESE SHOULDN'T NEED TOUCHING
	/**Used internally to distinguish sunken lineages
	 * TODO currently not compatible with mutation
	 */
	public static final long SINK_OFFSET = Long.MAX_VALUE;
	/**Used internally to guarantee unique lineage ID's in runs with mutation*/
	public final int MUTANT_MAX_OFFSET = Integer.MAX_VALUE;
	//---

	/**random seed for simulation ( -1 = randomly select random seed) */
	public final int SEED;

	
	
	
	/**
	 * 
	 * @param iniFR Controls reading parameters from ini file/command line
	 * @throws Exception
	 */
	public ControlConfig(IniFileReader iniFR) throws Exception{
		
		
		
		SEED = Integer.parseInt(iniFR.getParamValue( "SEED", "Control", false));

	
		DURATION_DAY = Integer.parseInt(iniFR.getParamValue( "DURATION_DAY", "TimeStepping", false));
		STOP_AT_1 = iniFR.getParamValue( "STOP_AT_1", "TimeStepping", false).trim().toLowerCase().equals("true");

		TRACER_MODE = iniFR.getParamValue( "TRACER_MODE", "Tracer", false).trim().toLowerCase().equals("true");


		
		////////////////////////////////////////////////InputOutput //////////////////////////////////////////////			

		SAVE_FILE = Config.parseFilename(iniFR.getParamValue( "FILE_OUT", "InputOutput", false));
		TIME_THRESH = Long.parseLong(iniFR.getParamValue( "TIME_THRESH", "InputOutput", false));
		SAVE_TIMESTEPS_DAY = iniFR.getParamValue( "SAVE_TIMESTEPS_DAY", "InputOutput", false);
		REPORT_TIMESTEPS_DAY = iniFR.getParamValue( "REPORT_TIMESTEPS_DAY", "InputOutput", false);
		MUTANT_TIMESTEPS_DAY = iniFR.getParamValue( "MUTANT_TIMESTEPS_DAY", "InputOutput", false);
		CHECKPOINT_HOURS = Double.parseDouble(iniFR.getParamValue( "CHECKPOINT_HOURS", "InputOutput", false));
		
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
		LOAD_HOUR = (loadHourString != null && !loadHourString.equals("0")) 
					? 	Integer.parseInt(loadHourString) 				//1) specified in hours
					:   (												//2) specified in days
								(loadDayString != null && !loadDayString.equals("0"))
								?	Config.extractDayAndHour(loadDayString)
								:	0 									//3) NOT LOADING A FILE AT ALL
						);
		//
		
		
		LOAD_FILE = Config.parseFilename(iniFR.getParamValue( "FILE_LOAD", "InputOutput", false));
		//error handling
		if( (LOAD_HOUR == 0) != (LOAD_FILE == null))
			throw new IllegalArgumentException("If FILE_LOAD is set LOAD_HOUR or LOAD_DAY must also be set");

			
		
		DEBUG = iniFR.getParamValue( "DEBUG", "InputOutput", false).toLowerCase().equals("true");
		
		DEEP_DEBUG_MUTANTS = iniFR.getParamValue( "DEEP_DEBUG_MUTANTS", "InputOutput", false).toLowerCase().equals("true");

		
		SAVE_BIRTHHOUR = iniFR.getParamValue( "SAVE_BIRTHHOUR", "InputOutput", false).toLowerCase().equals("true");
		
				
		////////////////////////////////////////////////parallelization //////////////////////////////////////////////			
		
		
		CLUST_FILE = Config.parseFilename(iniFR.getParamValue( "CLUST_FILE", "Parallelization", false));

	}

}
