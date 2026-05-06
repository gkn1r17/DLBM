package config;


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


import java.io.File;
import java.util.Arrays;
import java.util.Map.Entry;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import inputOutput.FileIO;

public class Config {
	
	
	
	//------- CONTAINERS FOR DIRECT FROM INI (NOT CALCULATED) SETTINGS
	/**Contains scientific settings loaded from ini/command line*/
	public final SciConfig sci;
	/**Contains non-scientific ("control" e.g. IO/timestepping/parallelization) 
	 * settings loaded from ini/command line */
	public final ControlConfig ctrl;
	/**Contains setting loaded from ini/command line for artificial TM
	 * (not used in most simulations)*/
	public final ArtificialTMConfig tm;
	//---
	
	
	
	
	//------------- CALCULATED SCIENTIFIC SETTINGS
	/**growth rate per timestep calculated from GROWTH_RATE_DAY*/
	public final double growthRate;
	/**mortality per timestep calculated from GROWTH_RATE_DAY*/
	public final double mortality;
	/**growth steps per dispersal timestep*/
	public final double growthPerDisp;
	/**starting population of each location (grid box) divided by K*/
	/**should be equilibrium population, i.e. the population at which growth == mortality*/
	public final double initialP;
	/**number of locations in TM
	 * Normally equal to value in ini but calculated if artificial TM used*/
	public final int numBoxes;
	public final boolean isSelective;
	//-------------- CALCULATED CONTROL SETTINGS
	public final long maxMutantOffset;
	/**Internal value (don't change) controlling lineage ID's
	 * to ensure even with new mutants there are no overlapping ID's*/
	public final long mutantOffset;
	/**hour timesteps extracted from SAVE_TIMESTEPS_DAY*/
	public final long[] saveTimestepsArr;
	/**hour timesteps extracted from REPORT_TIMESTEPS_DAY*/
	public final long[] reportTimestepsArr;
	/**hour timesteps extracted from MUTANT_TIMESTEPS_DAY*/
	public final long[] mutantTimestepsArr;
	/**directory from LOAD_FILE*/
	public final String loadDir;
	/**directory from SAVE_FILE*/
	public final String saveDir;
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
		
		
		
		sci = new SciConfig(iniFileReader);
		ctrl = new ControlConfig(iniFileReader);
		tm = new ArtificialTMConfig(iniFileReader);
		
		//---------- SCIENTIFIC CALCULATED
		mortality = ctrl.tracerMode
					?	0
					:	sci.mortalityDay * (sci.growthHours / 24.0);
		
    	growthRate = ctrl.tracerMode 
    				  ?   0 	  
    			      :   sci.growthRateDay * (sci.growthHours / 24.0);
    	growthPerDisp = sci.dispHours / sci.growthHours;
    	
    	initialP = sci.K * 
    				( sci.topDown 
    				  ?    1.0
    			      :    1 - (sci.growthRateDay / sci.mortalityDay) );

		numBoxes = tm.buildTM 
					?	tm.tmNumCols * tm.tmNumRows
					:   sci.numBoxes;

    	

		
		//------------- CONTROL CALCULATED
		mutantOffset = (int) Math.floor(Integer.MAX_VALUE / numBoxes);
		maxMutantOffset = mutantOffset * numBoxes;

    	//TODO currently input only allowed in days
		//for back compatibility with old setup,
		//Now using hours internally. Ultimately should make consistent.
		saveTimestepsArr = ctrl.saveTimestepsDay.endsWith(".csv")
							//option 1 = input as csv filename containing column with timeseries			
							?  FileIO.loadLongSet(ctrl.saveTimestepsDay).stream().mapToLong(e -> e * 24)
														.sorted().toArray() 
                             //option 2 = input as list directly written in .ini file e.g. SAVE_TIMESTEPS_DAY=0,35,365 														
                            :  Arrays.asList(ctrl.saveTimestepsDay.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
														.sorted().toArray();
		
		reportTimestepsArr = ctrl.reportTimestepsDay.endsWith(".csv")
				               ?   FileIO.loadLongSet(ctrl.reportTimestepsDay).stream().mapToLong(e -> e * 24) 
														.sorted().toArray()
				               :   Arrays.asList(ctrl.reportTimestepsDay.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
														.sorted().toArray();
	
						
		
		mutantTimestepsArr = ctrl.mutantTimestepsDay.toLowerCase().equals("none")
				              //"option 0" = when not saving mutants at all or not applying mutation
							  ?    null 
				              :		(    //... and when saving mutants:
				            		    ctrl.mutantTimestepsDay.endsWith(".csv")
				            		    ?    FileIO.loadLongSet(ctrl.mutantTimestepsDay).stream().mapToLong(e -> e * 24) 
																	.sorted().toArray()
							            :    Arrays.asList(ctrl.mutantTimestepsDay.split(",")).stream().mapToLong(e -> Long.parseLong(e.trim()) * 24)
																	.sorted().toArray()
										);
				            		  
				            		  
		isSelective = sci.tempFile != null;
		

		loadDir = ctrl.loadFile == null 
					?	null
					:	new File(ctrl.loadFile).getParent();
		saveDir = new File(ctrl.saveFile).getParent();
		new File(saveDir).mkdir();
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
		inFile = inFile.replace("*", " ");
		
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
