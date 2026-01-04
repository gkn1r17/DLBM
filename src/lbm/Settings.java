/**
 * Take settings to configure the simulation from the command line or settings file
 * Order of priority = 
 * 1) command line arguments (optional) (format SETTINGS_NAME SETTING)
 * 2) file specified as first argument in command line (optional) (SETTINGS FILE_NAME , within file = SETTING_NAME:value)
 * 3) defaultSettings.ini (compulsory but automatic)
 * Throws error if any setting is missing (but all should be in daultSettings.ini - will only happen if you change it)
 * 
 */

package lbm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.stream.IntStream;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Profile;
import org.ini4j.Profile.Section;

import util.FileIO;
//import util.MakeArtificialTM;
import util.MakeArtificialTM;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class Settings{
	
	/************** SCIENTIFIC SETTINGS *********************************/
			
			//TODO - should really be hours now for consistency
			/**Total simulation duration in days*/
			public final int DURATION_DAY;
			/**number of individuals per lineage at start */
			public final int INIT_LIN_SIZE;
			/**random seed for simulation ( -1 = randomly select random seed) */
			public final int SEED;
			/**total simulation duration in years */
			public final int K;
			/**starting population of each location (grid box) divided by K*/
			/**this should be the same as the equilibrium population, i.e. the population at which growth == mortality*/
			/**with growth = 0.8 and mortality = 0.1 this equals 0.875*/
			public final double P_0;
					/**initial population size of each location, i.e. K * P_0 - DO NOT CHANGE */
					public final double INITIAL_P;

			
			//TRANSPORT MATRIX
			/**number of locations in TM*/
			public final int NUM_BOXES;
			/**relative path to txt file where TM is held */
			public final String TM_FILE;
			public final boolean BUILD_TM;
			
			
							//only used if creating artificial TM
							public final int TM_NUM_CORES; // = 40;
							public final int TM_NUM_ROWS; // = 1;
							public final int TM_NUM_COLS; // = 100;
							public final double TM_DISP; // = 0.0001;
							public final boolean TM_GYRE; // = false;
							public final double TM_DISP_OUT; // = 0.0001;
							public final double TM_DISP_IN; // = 0;
							public final double TM_SPLIT_COL; // = 5;


			
			// GROWTH
			/**mortality per day*/
			public final double MORTALITY_DAY;
			/**growth rate per day*/
			public final double GROWTH_RATE_DAY;
			/**number of hours between growth/mortality timesteps*/
			public final double GROWTH_HOURS;
		
			
			// DISPERSAL
			/**number of hours between dispersal timesteps*/
			public final double DISP_HOURS;
			/**path to file containing volumes (volume of each location relative to mean)*/
			public final String VOL_FILE; 
			/**multiply each dispersal pathway by this*/
			public final double DISP_SCALER;
					// Growth/Dispersal - derived parameters DO NOT CHANGE
					final double GROWTH_PER_DISP;
					public final double GROWTH_RATE;
					public final double MORTALITY;
					//by default with 1 day ts for growth/mortality and dispersal:
								//GROWTH_PER_DISP=1, GROWTH_RATE=GROWTH_RATE_DAY, MORTALITY=MORTALITY_DAY 
			
			//SELECTION
			/**niche width*/
			public final float W;
			/**path to temperature file (tenv of each location)*/
			public final String TEMP_FILE;
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public final float TEMP_START_RANGE;
			
			
			//DORMANCY	
			/**proportion of each lineage randomly selected to "sink" (be removed from growth/mortality but not dispersal processes) each growth timestep*/
				/**same proportion of individuals from sunken lineages are unsunk*/
			public final double SIZE_REFUGE;

			
			/** NOT FOR FULL SIMULATIONS - switch on for running special simulation to calculate tmins*/
			public final boolean TRACER_MODE;

			public final boolean TOP_DOWN;

			
	/************** OUTPUT/LOADING SETTINGS *********************************/

			//OUTPUT
			/**path for where to save output*/
			public final String FILE_OUT;

			/**time in seconds before outputs message just to show hasn't crashed - if no proper update yet happened*/ 
			public final long TIME_THRESH;
			
			
			/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
			public final String SAVE_TIMESTEPS_DAY;
				/**days extract from REPORT_TIMESTEPS_FILE (or default) - don't change*/
				public final long[] SAVE_TIMESTEPS_ARR;
				

			//TODO should ultimately be hour for consistency	
			/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
			public final String REPORT_TIMESTEPS_DAY;
				/**days extract from REPORT_TIMESTEPS_FILE (or default) - don't change*/
				public final long[] REPORT_TIMESTEPS_ARR;

		
				
			/**if true simulation will finish when global diversity = 1*/
			public final boolean STOP_AT_1; 


		
			// LOADING PREVIOUS RUNS
			/**if loading previous run, gives hour to load*/
			public final long LOAD_HOUR;

			
			/**path for where to load file*/
			public final String FILE_LOAD;

					
	/************** PARALLELIZATION / DISTRIBUTION AND OTHER PURELY TECHNICAL SETTINGS *********************************/
			
			/**csv file specifying how locations are divided into clusters and clusters into nodes
			 * clusters = parallelized within one computer
			 * nodes = parallelized across computers*/
			public final String CLUST_FILE;
			
			
			
			/**should be set to length of time available on HPC, to ensure prints loadable output immediately before job evicted*/
			public final double CHECKPOINT_HOURS;
			
			public final int LOAD_SEED = Integer.MIN_VALUE;
			public final double MUTATION_DAY;

			/**Used internally to distinguish sunken lineages*/
			public final static int SINK_OFFSET = (int) 1e9;

			
			
			
	/**Loads settings from settings file or command line. 
	 * Command line may contain "SETTINGS [name of settings file]" 
	 * and may instead of/ additionally contain settings listed as "[name of setting] [value of setting]"
	 * (in all cases replace the square brackets and text inside with your own text)	
	 * 
	 * @param args command line arguments as array
	 * @param nodeNum current node so only prints info about settings once (on node 0)
	 * @param verbose if true prints info about settings to screen
	 * @throws Exception 
	 * @throws NumberFormatException 
	 */
	public Settings(String[] args, boolean isController, boolean verbose) throws Exception {
		Ini settingsIni = null;
		try {
			settingsIni = readSettingsFile(args, isController, verbose);
		} catch (NumberFormatException e) {
			throw e;
		}
		catch (InvalidFileFormatException e) {
			throw e;
		}
		
		String settingsStr = makeSettingsString(settingsIni);
		
			//*********************************************************************************************************************
			//***************** LOAD SETTINGS**************************************************************************************
			//*********************************************************************************************************************
		
			SEED = Integer.parseInt(getParamValue(settingsIni, "SEED", "Control", false));

			
			////////////////////////////////////////////////TimeStepping //////////////////////////////////////////////			
			
			DURATION_DAY = Integer.parseInt(getParamValue(settingsIni, "DURATION_DAY", "TimeStepping", false));
			GROWTH_HOURS = Double.parseDouble(getParamValue(settingsIni, "GROWTH_HOURS", "TimeStepping", false));
			DISP_HOURS = Double.parseDouble(getParamValue(settingsIni, "DISP_HOURS", "TimeStepping", false));
			STOP_AT_1 = getParamValue(settingsIni, "STOP_AT_1", "TimeStepping", false).trim().toLowerCase().equals("true");
			
			
			////////////////////////////////////////////////Ecological //////////////////////////////////////////////			
			
			
			GROWTH_RATE_DAY = Double.parseDouble(getParamValue(settingsIni, "GROWTH_RATE_DAY", "Ecological", false));
			MORTALITY_DAY = Double.parseDouble(getParamValue(settingsIni, "MORTALITY_DAY", "Ecological", false));
			MUTATION_DAY = Double.parseDouble(getParamValue(settingsIni, "MUTATION_DAY", "Ecological", false));
			TOP_DOWN = getParamValue(settingsIni, "TOP_DOWN", "Ecological", false).trim().toLowerCase().equals("true");

			
			////////////////////////////////////////////////population //////////////////////////////////////////////			

			K = Integer.parseInt(getParamValue(settingsIni, "K", "Population", false));
			INIT_LIN_SIZE = Integer.parseInt(getParamValue(settingsIni, "INIT_LIN_SIZE", "Population", false));
			
			////////////////////////////////////////////////TransportMatrix //////////////////////////////////////////////			

				//artificial TM
				BUILD_TM = getParamValue(settingsIni, "BUILD_TM", "TransportMatrix", false).trim().toLowerCase().equals("true");
				TM_NUM_CORES = Integer.parseInt(getParamValue(settingsIni, "TM_NUM_CORES", "TransportMatrix", false));
				TM_NUM_ROWS = Integer.parseInt(getParamValue(settingsIni, "TM_NUM_ROWS", "TransportMatrix", false));
				TM_NUM_COLS = Integer.parseInt(getParamValue(settingsIni, "TM_NUM_COLS", "TransportMatrix", false));
				TM_DISP = Double.parseDouble(getParamValue(settingsIni, "TM_DISP", "TransportMatrix", false));
				TM_GYRE = getParamValue(settingsIni, "TM_GYRE", "TransportMatrix", false).trim().toLowerCase().equals("true");
				TM_DISP_OUT = Double.parseDouble(getParamValue(settingsIni, "TM_DISP_OUT", "TransportMatrix", false));
				TM_DISP_IN  = Double.parseDouble(getParamValue(settingsIni, "TM_DISP_IN", "TransportMatrix", false));
				TM_SPLIT_COL  = Double.parseDouble(getParamValue(settingsIni, "TM_SPLIT_COL", "TransportMatrix", false));

			
			
			NUM_BOXES = BUILD_TM ? TM_NUM_COLS * TM_NUM_ROWS  :
									Integer.parseInt(getParamValue(settingsIni, "NUM_BOXES", "TransportMatrix", false));
			TM_FILE = parseFilename(getParamValue(settingsIni, "TM_FILE", "TransportMatrix", false));
			VOL_FILE = parseFilename(getParamValue(settingsIni, "VOL_FILE", "TransportMatrix", false));

			////////////////////////////////////////////////Dispersal //////////////////////////////////////////////			
			
			DISP_SCALER = Double.parseDouble(getParamValue(settingsIni, "DISP_SCALER", "Dispersal", false));
			
			////////////////////////////////////////////////Selection //////////////////////////////////////////////			

			W = Float.parseFloat(getParamValue(settingsIni, "W", "Selection", false));
			TEMP_FILE = parseFilename(getParamValue(settingsIni, "TEMP_FILE", "Selection", false));
			TEMP_START_RANGE = Float.parseFloat(getParamValue(settingsIni, "TEMP_START_RANGE", "Selection", false));

			SIZE_REFUGE = Double.parseDouble(getParamValue(settingsIni, "SIZE_REFUGE", "Dormancy", false));
			
			TRACER_MODE = getParamValue(settingsIni, "TRACER_MODE", "Tracer", false).trim().toLowerCase().equals("true");


			
			////////////////////////////////////////////////InputOutput //////////////////////////////////////////////			

			FILE_OUT = parseFilename(getParamValue(settingsIni, "FILE_OUT", "InputOutput", false));
			TIME_THRESH = Long.parseLong(getParamValue(settingsIni, "TIME_THRESH", "InputOutput", false));
			SAVE_TIMESTEPS_DAY = getParamValue(settingsIni, "SAVE_TIMESTEPS_DAY", "InputOutput", false);
			REPORT_TIMESTEPS_DAY = getParamValue(settingsIni, "REPORT_TIMESTEPS_DAY", "InputOutput", false);
			CHECKPOINT_HOURS = Double.parseDouble(getParamValue(settingsIni, "CHECKPOINT_HOURS", "InputOutput", false));
			
			String loadHourString = getParamValue(settingsIni, "LOAD_HOUR", "InputOutput", false);			
						 /** 	(for back compatibility with some older config files 
						 * 			and because output files haven't yet been adapted to new format
						 * 			i.e. they are still timestamped in days and hours
						 * 		LOAD_DAY=[Day]h[HourOfDay] works in addition to LOAD_HOUR.
						 * 			h[HourOfDay] is optional
						 */
						String loadDayString = getParamValue(settingsIni, "LOAD_DAY", "InputOutput", true);
						if(loadHourString != null && !loadHourString.equals("0") 
								&& loadDayString != null && !loadDayString.equals("0")) {
							throw new IllegalArgumentException("Fatal Exception: LOAD_DAY and LOAD_HOUR can't both be specified.");
						}
			LOAD_HOUR = loadHourString == null ? 
										(loadDayString == null ?
												0 
											: extractDayAndHour(loadDayString) )
									: Integer.parseInt(loadHourString);


			
			FILE_LOAD = parseFilename(getParamValue(settingsIni, "FILE_LOAD", "InputOutput", false));

			
					
			////////////////////////////////////////////////parallelization //////////////////////////////////////////////			
			
			
			CLUST_FILE = parseFilename(getParamValue(settingsIni, "CLUST_FILE", "Parallelization", false));

			
			
			
			//*********************************************************************************************************************
			//***************** CALCULATE DERIVED SETTINGS ************************************************************************
			//*********************************************************************************************************************

			
			//calculate growth/mortality per timestep
			MORTALITY = TRACER_MODE ? 
					0 :
					MORTALITY_DAY * (GROWTH_HOURS / 24.0);
			
	    	GROWTH_RATE = TRACER_MODE ? 
	    			0 :
	    			GROWTH_RATE_DAY * (GROWTH_HOURS / 24.0);
	    	GROWTH_PER_DISP = DISP_HOURS / GROWTH_HOURS;
	    	
	    	
	    	//calculate equilibrium and therefore starting population size
			P_0 = TOP_DOWN ? 
					1.0 :
					1 - (GROWTH_RATE_DAY / MORTALITY_DAY);
	    	INITIAL_P = K * P_0;

	    	
	    	
	    	//TODO currently input only allowed in days, 
	    		//because formerly everything was in days
	    		//should ultimately be in hours
	    	SAVE_TIMESTEPS_ARR = SAVE_TIMESTEPS_DAY.endsWith(".csv") ?
	    //option 1 = input as csv filename containing column with timeseries			
	    			FileIO.loadLongSet(SAVE_TIMESTEPS_DAY).stream().mapToLong(e -> e * 24)
	    													.sorted().toArray() :
	    //option 2 = input as list directly written in .ini file e.g. SAVE_TIMESTEPS_DAY=0,35,365 														
	    			Arrays.asList(SAVE_TIMESTEPS_DAY.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
	    													.sorted().toArray();
	    	
	    	REPORT_TIMESTEPS_ARR = REPORT_TIMESTEPS_DAY.endsWith(".csv") ?
	    //option 1 = input as csv filename containing column with timeseries			
					FileIO.loadLongSet(REPORT_TIMESTEPS_DAY).stream().mapToLong(e -> e * 24) 
															.sorted().toArray() :
		//option 2 = input as list directly written in .ini file e.g. REPORT_TIMESTEPS_DAY=0,35,365 														
					Arrays.asList(REPORT_TIMESTEPS_DAY.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
															.sorted().toArray();

	    					
	    	
	    	


	    	

	    	
	    	FileIO.makeSettingsFile(FILE_OUT, settingsStr);
	    	
	    	checkForUnknownSettings(settingsIni);
		
	}
	
	private long extractDayAndHour(String loadDayString) {
		
		
    	long loadHour = 0;
    	long loadDay = 0;
    	String[] loadDayBits = loadDayString.split("h");
    	loadDay = Long.parseLong(loadDayBits[0]);
    	loadHour = (loadDay * 24);
    	if(loadDayBits.length > 1)
    		loadHour = loadHour + Integer.parseInt(loadDayBits[1]);
    	return (loadHour);
	}

	/**record settings in file 
	 * @throws IOException */
	public static String makeSettingsString(Ini settingsIni) throws IOException {
		
		StringBuilder settingStr = new StringBuilder();
			for( Entry<String, Section> sect : settingsIni.entrySet()) {
				settingStr.append("[" + sect.getKey() + "]\n");
				for( Entry<String, String> val : sect.getValue().entrySet()) {
					settingStr.append(val.getKey() + "=" + val.getValue() + "\n");
				}
			}			
		return settingStr.toString();	
	}
	













	private String parseFilename(String inFile) {

		
		if(inFile.toLowerCase().equals("none"))
			return null;
		
		return inFile;
		
		//File f = new File(inFile);
		
		//return f.getAbsolutePath();
	}













	/** Check for setting included when all settings have been allocated to variables
	 * (and in doing so removed from settingsIni)
	 * 
	 * @param settingsIni
	 * @throws IllegalArgumentException
	 */
	private void checkForUnknownSettings(Ini settingsIni) throws IllegalArgumentException{
		boolean foundUnknown = false;
		for(Entry<String, Section> sect : settingsIni.entrySet()) {
			String sectName = sect.getKey();
			Section sectVals = sect.getValue();
			for(String sectVal : sectVals.keySet()  ) {
				System.err.println("Setting: " + sectVal + 
									" in section [" + sectName + "] unknown");
				foundUnknown = true;
			}
		}
		if(foundUnknown)
			throw new IllegalArgumentException("Fatal Exception: Unknown settings were found."
												+ "See above for details.");
	}













	private String getParamValue(Ini settingsIni, String paramName, String sectName, boolean canBeMissing) throws Exception {
		
		//look in ini file first as command line takes precedence
		Section namedSect = settingsIni.get(sectName);
		if(namedSect == null)
			throw new Exception("Section " + sectName + " missing. "
					+ "Is parameter " + paramName + " in the wrong section?");
		String resStr = settingsIni.get(sectName).remove(paramName);
		
		//look in command line
		Section defSect = settingsIni.get("commandLine");
		if(defSect != null && defSect.containsKey(paramName))
			resStr = defSect.remove(paramName);
		
		
		if(!canBeMissing && resStr == null)
			throw new Exception("Parameter: " + paramName + 
					" not found in ini file (expected section: [" + sectName + "]) or command line" );
		
		
		return resStr;
	}





	private Ini readSettingsFile(String[] args, boolean isController, boolean verbose) throws NumberFormatException, IOException {
		
		Ini defIni = null;
		Ini otherIni = null;
		
		try {
			
			System.out.println("Reading default settings file \"default.ini\"");
			defIni = new Ini( new File("default.ini"));
		
		} catch (NumberFormatException | IOException e) {
			System.err.println("Exception reading default Settings file (default.ini)");
			throw e;
		}
			

		//read any additional settings files
		//filename follows "SETTINGS" in command line args
		Section cmdSect = null;
		for(int i =0; i < args.length; i+= 2) {
			
			
			if(args[i].toLowerCase().trim().equals("settings")) { //read settings file
				String settingsFileName = args[i + 1];
				
				if(!settingsFileName.endsWith(".ini"))
					settingsFileName = settingsFileName + ".ini";
				
				if(isController && verbose)
					System.out.println("*** using settings file ***: \"" + settingsFileName + "\"");
				
				
				try {
					
					//read additional settings file
					try {
						otherIni = new Ini(new File(settingsFileName));
					} catch (NumberFormatException | IOException e) {
						System.err.println("Exception reading default Settings file (default.ini)");
						throw e;
					}	
						
					for (String sectionName : otherIni.keySet()) {
					    Profile.Section othSect = otherIni.get(sectionName);
					    Profile.Section defSect = defIni.get(sectionName);
			
					    if (defSect == null) {
					        defIni.add(sectionName).putAll(othSect);
					    } else {
					        for (String key : othSect.keySet())
					        	defSect.put(key, othSect.get(key));
					    }
					}

					
				} catch (Exception e) {
					System.err.println("Exception reading Settings file \"" + settingsFileName + "\"");
					throw e;
				}
				
				
			}
			else {
			    
				//TODO properly integrate command line into sections
				//for now will check section "commandLine" first (as has precedence over settings files)
			    if (cmdSect == null) {
			        cmdSect = defIni.add("commandLine");
					if(isController && verbose)
						System.out.println("*** Reading command line settings *** ");

			    }
		        cmdSect.put(args[i].toUpperCase().trim(), args[i + 1]);

			}
				
			

		
		}
		return defIni;


	}

}

