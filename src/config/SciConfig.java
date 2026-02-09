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
			public final int INIT_LIN_SIZE;
			/**total simulation duration in years */
			public final int K;

			
			//TRANSPORT MATRIX
			/**relative path to txt file where TM is held */
			public final String TM_FILE;


			
			// GROWTH
			/**mortality per day*/
			public final double MORTALITY_DAY;
			/**growth rate per day*/
			public final double GROWTH_RATE_DAY;
			/**number of hours between growth/mortality timesteps*/
			public final double GROWTH_HOURS;
			/**mutation per births*/
			public final double MUTATION;

			
			// DISPERSAL
			/**number of hours between dispersal timesteps*/
			public final double DISP_HOURS;
			/**path to file containing volumes (volume of each location relative to mean)*/
			public final String VOL_FILE; 
			/**multiply each dispersal pathway by this*/
			public final double DISP_SCALER;
			
			//SELECTION
			/**niche width*/
			public final float W;
			/**path to temperature file (tenv of each location)*/
			public final String TEMP_FILE;
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public final float TEMP_START_RANGE;
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public final float TEMP_MUTINTV;

			
			//DORMANCY	
			/**proportion of each lineage randomly selected to "sink" (be removed from growth/mortality but not dispersal processes) each growth timestep*/
				/**same proportion of individuals from sunken lineages are unsunk*/
			public final double SIZE_REFUGE;

			
			/**If growth/mortality should be modelled "top down" i.e. 
			 * mortality controlled by crowding and growth and a fixed rate*/
			public final boolean TOP_DOWN;

			/**number of locations in TM*/
			final int NUM_BOXES;


			
			
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
			
			GROWTH_HOURS = Double.parseDouble(iniFR.getParamValue("GROWTH_HOURS", "TimeStepping", false));
			DISP_HOURS = Double.parseDouble(iniFR.getParamValue("DISP_HOURS", "TimeStepping", false));
			
			
			
			////////////////////////////////////////////////Ecological //////////////////////////////////////////////			
			
			
			GROWTH_RATE_DAY = Double.parseDouble(iniFR.getParamValue("GROWTH_RATE_DAY", "Ecological", false));
			MORTALITY_DAY = Double.parseDouble(iniFR.getParamValue("MORTALITY_DAY", "Ecological", false));
			MUTATION = Double.parseDouble(iniFR.getParamValue("MUTATION", "Ecological", false));
			TOP_DOWN = iniFR.getParamValue("TOP_DOWN", "Ecological", false).trim().toLowerCase().equals("true");

			
			////////////////////////////////////////////////population //////////////////////////////////////////////			

			K = Integer.parseInt(iniFR.getParamValue("K", "Population", false));
			INIT_LIN_SIZE = Integer.parseInt(iniFR.getParamValue("INIT_LIN_SIZE", "Population", false));
			
			////////////////////////////////////////////////TransportMatrix //////////////////////////////////////////////			

			NUM_BOXES = Integer.parseInt(iniFR.getParamValue("NUM_BOXES", "TransportMatrix", false));
			
			
			
			TM_FILE = Config.parseFilename(iniFR.getParamValue("TM_FILE", "TransportMatrix", false));
			VOL_FILE = Config.parseFilename(iniFR.getParamValue("VOL_FILE", "TransportMatrix", false));

			////////////////////////////////////////////////Dispersal //////////////////////////////////////////////			
			
			DISP_SCALER = Double.parseDouble(iniFR.getParamValue("DISP_SCALER", "Dispersal", false));
			
			////////////////////////////////////////////////Selection //////////////////////////////////////////////			

			W = Float.parseFloat(iniFR.getParamValue("W", "Selection", false));
			TEMP_FILE = Config.parseFilename(iniFR.getParamValue("TEMP_FILE", "Selection", false));
			TEMP_START_RANGE = Float.parseFloat(iniFR.getParamValue("TEMP_START_RANGE", "Selection", false));
			TEMP_MUTINTV = Float.parseFloat(iniFR.getParamValue("TEMP_MUTINTV", "Selection", false));
			SIZE_REFUGE = Double.parseDouble(iniFR.getParamValue("SIZE_REFUGE", "Dormancy", false));
			
	}

}

