/**
 * Scientific Configuration
 * 
 * Take settings to configure the simulation from the command line or settings file
 * Order of priority = 
 * 1) command line arguments (optional) (format SETTINGS_NAME SETTING)
 * 2) file specified as first argument in command line (optional) (SETTINGS FILE_NAME , within file = SETTING_NAME:value)
 * 3) defaultSettings.ini (compulsory but automatic)
 * Throws error if any setting is missing (but all should be in daultSettings.ini - will only happen if you change it)
 * 
 */

package config;

public class SciConfig {
			

			
			
			/**number of individuals per lineage at start */
			public final int initLinSize;
			/**Carrying Capacity (K) */
			public final int K;

			
			//TRANSPORT MATRIX
			/**relative path to txt file where TM is held */
			public final String tmFile;


			
			// GROWTH
			/**mortality per day*/
			public final double mortalityDay;
			/**growth rate per day*/
			public final double growthRateDay;
			/**number of hours between growth/mortality timesteps*/
			public final double growthHours;
			/**mutation per births*/
			public final double mutation;

			
			// DISPERSAL
			/**number of hours between dispersal timesteps*/
			public final double dispHours;
			/**path to file containing volumes (volume of each location relative to mean)*/
			public final String volFile; 
			/**multiply each dispersal pathway by this*/
			public final double dispScaler;
			
			//SELECTION
			/**niche width*/
			public final float W;
			/**path to temperature file (tenv of each location)*/
			public final String tempFile;
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public final float tempStartRange;
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public final float tempMutIntv;
			/**1e6 = topts can only be set at intervals of 1e-6*/
			public final int tempGranularity;

			
			//DORMANCY	
			/**proportion of each lineage randomly selected to "sink" (be removed from growth/mortality but not dispersal processes) each growth timestep*/
				/**same proportion of individuals from sunken lineages are unsunk*/
			public final double sizeRefuge;

			
			/**If growth/mortality should be modelled "top down" i.e. 
			 * mortality controlled by crowding and growth and a fixed rate*/
			public final boolean topDown;

			/**number of locations in TM*/
			final int numBoxes;


			
			
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
	public SciConfig(IniFileReader iniFR) throws NumberFormatException, Exception {

		
			//*********************************************************************************************************************
			//***************** LOAD SETTINGS**************************************************************************************
			//*********************************************************************************************************************

			
			////////////////////////////////////////////////TimeStepping //////////////////////////////////////////////			
			
			growthHours = Double.parseDouble(iniFR.getParamValue("GROWTH_HOURS", "TimeStepping", false));
			dispHours = Double.parseDouble(iniFR.getParamValue("DISP_HOURS", "TimeStepping", false));
			
			
			
			////////////////////////////////////////////////Ecological //////////////////////////////////////////////			
			
			
			growthRateDay = Double.parseDouble(iniFR.getParamValue("GROWTH_RATE_DAY", "Ecological", false));
			mortalityDay = Double.parseDouble(iniFR.getParamValue("MORTALITY_DAY", "Ecological", false));
			mutation = Double.parseDouble(iniFR.getParamValue("MUTATION", "Ecological", false));
			topDown = iniFR.getParamValue("TOP_DOWN", "Ecological", false).trim().toLowerCase().equals("true");

			
			////////////////////////////////////////////////population //////////////////////////////////////////////			

			K = Integer.parseInt(iniFR.getParamValue("K", "Population", false));
			initLinSize = Integer.parseInt(iniFR.getParamValue("INIT_LIN_SIZE", "Population", false));
			
			////////////////////////////////////////////////TransportMatrix //////////////////////////////////////////////			

			numBoxes = Integer.parseInt(iniFR.getParamValue("NUM_BOXES", "TransportMatrix", false));
			
			
			
			tmFile = Config.parseFilename(iniFR.getParamValue("TM_FILE", "TransportMatrix", false));
			volFile = Config.parseFilename(iniFR.getParamValue("VOL_FILE", "TransportMatrix", false));

			////////////////////////////////////////////////Dispersal //////////////////////////////////////////////			
			
			dispScaler = Double.parseDouble(iniFR.getParamValue("DISP_SCALER", "Dispersal", false));
			
			////////////////////////////////////////////////Selection //////////////////////////////////////////////			

			W = Float.parseFloat(iniFR.getParamValue("W", "Selection", false));
			tempFile = Config.parseFilename(iniFR.getParamValue("TEMP_FILE", "Selection", false));
			tempStartRange = Float.parseFloat(iniFR.getParamValue("TEMP_START_RANGE", "Selection", false));
			tempMutIntv = Float.parseFloat(iniFR.getParamValue("TEMP_MUTINTV", "Selection", false));
			tempGranularity = Integer.parseInt(iniFR.getParamValue("TEMP_GRANULARITY", "Selection", false));
			
			
			sizeRefuge = Double.parseDouble(iniFR.getParamValue("SIZE_REFUGE", "Dormancy", false));
			
	}

}

