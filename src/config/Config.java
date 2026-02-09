/**Manages config variables from ini files/command line:
 * 
 * 1) Creates IniFileReader for reading Ini files
 * 2) Stores references to three Config objects holding config variables from the Ini files:
 * 	- SciConfig for Scientific Configuration settings
 *  - ControlConfig for non scientific (e.g. duration, seed, parallelization settings)
 *  - (ArtificialTMConfig in most simulations can be ignored 
 *  			but used if using a simple idealised TM which is generated dynamically)
 * 3) Calculates and stores various config variables that are calculated from the original config variables
 * 4) Checks for unused config fields and throws an exception
 * 5) Outputs to new Config file
 * 
 * 
 *  Note that error checking of config variables responsibility of relevant config class
 *  TODO some error checking in other classes may need copying across
 *  

 * 
 */

package config;

import java.io.File;
import java.util.Arrays;
import java.util.Map.Entry;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import inputOutput.FileIO;

public class Config {
	
	
	
	//------- CONTAINERS FOR DIRECT FROM INI (NOT CALCULATED) SETTINGS
	/**Contains scientific settings loaded from ini/command line*/
	public final SciConfig SCI;
	/**Contains non-scientific ("control" e.g. IO/timestepping/parallelization) 
	 * settings loaded from ini/command line */
	public final ControlConfig CTRL;
	/**Contains setting loaded from ini/command line for artificial TM
	 * (not used in most simulations)*/
	public final ArtificialTMConfig TM;
	//---
	
	
	
	
	//------------- CALCULATED SCIENTIFIC SETTINGS
	/**growth rate per timestep calculated from GROWTH_RATE_DAY*/
	public final double GROWTH_RATE;
	/**mortality per timestep calculated from GROWTH_RATE_DAY*/
	public final double MORTALITY;
	/**growth steps per dispersal timestep*/
	public final double GROWTH_PER_DISP;
	/**starting population of each location (grid box) divided by K*/
	/**should be equilibrium population, i.e. the population at which growth == mortality*/
	public final double INITIAL_P;
	/**number of locations in TM
	 * Normally equal to value in ini but calculated if artificial TM used*/
	public final int NUM_BOXES;
	public final boolean IS_SELECTIVE;
	//-------------- CALCULATED CONTROL SETTINGS
	/**Internal value (don't change) controlling lineage ID's
	 * to ensure even with new mutants there are no overlapping ID's*/
	public final long MUTANT_OFFSET;
	/**hour timesteps extracted from SAVE_TIMESTEPS_DAY*/
	public final long[] SAVE_TIMESTEPS_ARR;
	/**hour timesteps extracted from REPORT_TIMESTEPS_DAY*/
	public final long[] REPORT_TIMESTEPS_ARR;
	/**hour timesteps extracted from MUTANT_TIMESTEPS_DAY*/
	public final long[] MUTANT_TIMESTEPS_ARR;
	/**directory from LOAD_FILE*/
	public final String LOAD_DIR;
	/**directory from SAVE_FILE*/
	public final String SAVE_DIR;
	//---
	
	
	/**Ini to be later saved in run folder as record of settings used*/
	private final String settingsIniOut;


	/**
	 * 
	 * @param args Command line args (may contains more settings and these have precedence over ini file)
	 * @param isController When running distributed if we're on node responsible for printing log stuff
	 * @param verbose
	 * @throws Exception
	 */
	public Config(String[] args, boolean isController, boolean verbose) throws Exception{
		
		IniFileReader iniFileReader = new IniFileReader(args, isController, verbose);
		
		
		
		SCI = new SciConfig(iniFileReader);
		CTRL = new ControlConfig(iniFileReader);
		TM = new ArtificialTMConfig(iniFileReader);
		
		//---------- SCIENTIFIC CALCULATED
		MORTALITY = CTRL.TRACER_MODE
					?	0
					:	SCI.MORTALITY_DAY * (SCI.GROWTH_HOURS / 24.0);
		
    	GROWTH_RATE = CTRL.TRACER_MODE 
    				  ?   0 	  
    			      :   SCI.GROWTH_RATE_DAY * (SCI.GROWTH_HOURS / 24.0);
    	GROWTH_PER_DISP = SCI.DISP_HOURS / SCI.GROWTH_HOURS;
    	
    	INITIAL_P = SCI.K * 
    				( SCI.TOP_DOWN 
    				  ?    1.0
    			      :    1 - (SCI.GROWTH_RATE_DAY / SCI.MORTALITY_DAY) );

		NUM_BOXES = TM.BUILD_TM 
					?	TM.TM_NUM_COLS * TM.TM_NUM_ROWS
					:   SCI.NUM_BOXES;

    	

		
		//------------- CONTROL CALCULATED
		MUTANT_OFFSET = (int) Math.floor(CTRL.MUTANT_MAX_OFFSET / NUM_BOXES);

    	//TODO currently input only allowed in days
		//for back compatibility with old setup,
		//Now using hours internally. Ultimately should make consistent.
		SAVE_TIMESTEPS_ARR = CTRL.SAVE_TIMESTEPS_DAY.endsWith(".csv")
							//option 1 = input as csv filename containing column with timeseries			
							?  FileIO.loadLongSet(CTRL.SAVE_TIMESTEPS_DAY).stream().mapToLong(e -> e * 24)
														.sorted().toArray() 
                             //option 2 = input as list directly written in .ini file e.g. SAVE_TIMESTEPS_DAY=0,35,365 														
                            :  Arrays.asList(CTRL.SAVE_TIMESTEPS_DAY.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
														.sorted().toArray();
		
		REPORT_TIMESTEPS_ARR = CTRL.REPORT_TIMESTEPS_DAY.endsWith(".csv")
				               ?   FileIO.loadLongSet(CTRL.REPORT_TIMESTEPS_DAY).stream().mapToLong(e -> e * 24) 
														.sorted().toArray()
				               :   Arrays.asList(CTRL.REPORT_TIMESTEPS_DAY.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
														.sorted().toArray();
	
						
		
		MUTANT_TIMESTEPS_ARR = CTRL.MUTANT_TIMESTEPS_DAY.toLowerCase().equals("none")
				              //"option 0" = when not saving mutants at all or not applying mutation
							  ?    null 
				              :		(    //... and when saving mutants:
				            		    CTRL.MUTANT_TIMESTEPS_DAY.endsWith(".csv")
				            		    ?    FileIO.loadLongSet(CTRL.MUTANT_TIMESTEPS_DAY).stream().mapToLong(e -> e * 24) 
																	.sorted().toArray()
							            :    Arrays.asList(CTRL.MUTANT_TIMESTEPS_DAY.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
																	.sorted().toArray()
										);
				            		  
				            		  
		IS_SELECTIVE = SCI.TEMP_FILE != null;
		

		LOAD_DIR = CTRL.LOAD_FILE == null 
					?	null
					:	new File(CTRL.LOAD_FILE).getParent();
		SAVE_DIR = new File(CTRL.SAVE_FILE).getParent();
		new File(SAVE_DIR).mkdir();
		//----
		
		
		
    	//all runs with Java simulation are with stochastic, not deterministic transport, 
		//add this setting useful when output picked up by Matlab downstream functions
		iniFileReader.appendToOutput("\n\n\nDET_TRANSPORT=false");
		settingsIniOut = iniFileReader.settingsIniOut.toString();
    	checkForUnknownSettings(iniFileReader.settingsIni);
    	

	}//end constructor

	
	/**Get total hour from format in ini = [day]h[hour of day].
	 * 
	 * @param dayHourString
	 * @return
	 */
	static long extractDayAndHour(String dayHourString) {
    	long loadHour = 0;
    	long loadDay = 0;
    	String[] loadDayBits = dayHourString.split("h");
    	loadDay = Long.parseLong(loadDayBits[0]);
    	loadHour = (loadDay * 24);
    	if(loadDayBits.length > 1)
    		loadHour = loadHour + Integer.parseInt(loadDayBits[1]);
    	return (loadHour);
	}

	
	/**Get filename or null if filename =none.
	 * 
	 * @param inFile
	 * @return
	 */
	static String parseFilename(String inFile) {
		if(inFile.toLowerCase().equals("none"))
			return null;
		return inFile;
	}


	/** Check for setting included when all settings have been allocated to variables
	 * (and in doing so removed from settingsIni)
	 * 
	 * @param settingsIni
	 * @throws IllegalArgumentException
	 */
	static void checkForUnknownSettings(Ini settingsIni) throws IllegalArgumentException{
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


	public String getSettingsIniOut() {
		return settingsIniOut;
	}



	
}
