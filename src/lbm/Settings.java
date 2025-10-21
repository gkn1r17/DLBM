/**
 * Take settings to configure the simulation from the command line or settings file
 */

package lbm;

import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.IntStream;

import util.FileIO;
//import util.MakeArtificialTM;
import util.MakeArtificialTM;

import java.io.File;

public class Settings{
	
	
	/************** SCIENTIFIC SETTINGS *********************************/
			
			//Total simulation duration in years
			public static int DURATION = 10000;
			/**number of individuals per lineage at start */
			public static int INIT_LIN_SIZE = 1;
			/**random seed for simulation ( -1 = randomly select random seed) */
			public static int SEED = -1;
			/**total simulation duration in years */
			public static int K = 100;
			/**starting population of each location (grid box) divided by K*/
			/**this should be the same as the equilibrium population, i.e. the population at which growth == mortality*/
			/**with growth = 0.8 and mortality = 0.1 this equals 0.875*/
			public static double P_0 = 0.875;
					/**initial population size of each location, i.e. K * P_0 - DO NOT CHANGE */
					public static double INITIAL_P;

			
			//TRANSPORT MATRIX
			/**number of locations in TM*/
			public static int NUM_BOXES = 6386;
			/**relative path to txt file where TM is held */
			public static String TM_FILE = "TMD6386bsFilt.txt";
			public static boolean BUILD_TM = false;

			
			// GROWTH
			/**mortality per day*/
			public static double MORTALITY_DAY = 0.1;
			/**growth rate per day*/
			public static double GROWTH_RATE_DAY = 0.8;
			/**number of hours between growth/mortality timesteps*/
			public static double GROWTH_HOURS = 24.0; //
		
			
			// DISPERSAL
			/**number of hours between dispersal timesteps*/
			public static double DISP_HOURS = 24.0;
			/**path to file containing volumes (volume of each location relative to mean)*/
			public static String VOL_FILE = "vols6386.csv"; 
			/**multiply each dispersal pathway by this*/
			public static double DISP_SCALER = 1;
					// Growth/Dispersal - derived parameters DO NOT CHANGE
					static double GROWTH_PER_DISP;
					public static double GROWTH_RATE;
					public static double MORTALITY;
					//by default with 1 day ts for growth/mortality and dispersal:
								//GROWTH_PER_DISP=1, GROWTH_RATE=GROWTH_RATE_DAY, MORTALITY=MORTALITY_DAY 
			
			//SELECTION
			/**niche width*/
			public static float W = 12.0f;
			/**path to temperature file (tenv of each location)*/
			public static String TEMP_FILE = "temps6386.csv";
			/**topts of lineages will be uniformly distributed from tenv - (TEMP_START_RANGE / 2) to tenv + (TEMP_START_RANGE / 2) */
			public static float TEMP_START_RANGE = 10.0f;
			
			
			//DORMANCY	
			/**proportion of each lineage randomly selected to "sink" (be removed from growth/mortality but not dispersal processes) each growth timestep*/
				/**same proportion of individuals from sunken lineages are unsunk*/
			public static double SIZE_REFUGE = 0;

			
			/** NOT FOR FULL SIMULATIONS - switch on for running special simulation to calculate tmins*/
			public static boolean TRACER_MODE =false;

			public static boolean TOP_DOWN = true;

			
	/************** OUTPUT/LOADING SETTINGS *********************************/

			//OUTPUT
			/**path for where to save output*/
			public static String FILE_OUT = "Maps/100neut";
					/**extracted from FILE_OUT - don't change */
					public static String DIR_OUT;
			/**time in seconds before outputs message just to show hasn't crashed - if no proper update yet happened*/ 
			public static long TIME_THRESH = 1;
			
			
			/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
			public static String SAVE_TIMESTEPS_FILE = null;
			/**default days for saving (assuming max 10,000 year: log 10 space years at intervals of 0.1, converted to days by multiplying by 365 and rounding)*/
			public final static int[] DEFAULT_SAVE_TIMESTEPS = new int[]{Integer.MAX_VALUE};
				/**days extract from REPORT_TIMESTEPS_FILE (or default) - don't change*/
				public static int[] SAVE_TIMESTEPS = DEFAULT_SAVE_TIMESTEPS;
				

				
			/**CSV file containing single column listing days to save data. "none" (null in code) if want to use DEFAULT_SAVE_TIMESTEPS*/
			public static String REPORT_TIMESTEPS_FILE = null;
			/**default days for saving (assuming max 10,000 year: log 10 space years at intervals of 0.1, converted to days by multiplying by 365 and rounding)*/
			public final static int[] DEFAULT_REPORT_TIMESTEPS = new int[]{0,36,46,58,73,92,115,145,183,230,290,365,460,578,728,917,1154,1453,1829,2303,2899,3650,4595,5785,7283,9168,11542,14531,18293,23030,28993,36500,45951,57849,72827,91684,115423,145309,182933,230299,289930,365000,459508,578486,728271,916839,1154231,1453091,1829333,2302994,2899298,3650000};
				/**days extract from REPORT_TIMESTEPS_FILE (or default) - don't change*/
				public static int[] REPORT_TIMESTEPS = DEFAULT_SAVE_TIMESTEPS;

		
				
			/**if true simulation will finish when global diversity = 1*/
			public static boolean STOP_AT_1; 


		
			// LOADING PREVIOUS RUNS
			/**if loading previous run, gives day and hour to load 
			 * format = day:hour (eg: 365:8 for hour 8 on day 365)
			 * :0 can be omitted*/		
			public static String LOAD_DAY_STRING = null;
				/**day extracted from LOAD_DAY_STRING - don't change*/
				public static int LOAD_DAY = 0;
				/**hour extracted from LOAD_DAY_STRING - don't change*/
				public static int LOAD_HOUR = 0;

			
			/**path for where to load file*/
			public static String LOAD_FILE = null;
					/**extracted from LOAD_FILE - don't change */
					public static String LOAD_DIR;
					
	/************** PARALLELIZATION / DISTRIBUTION AND OTHER PURELY TECHNICAL SETTINGS *********************************/
			
			/**csv file specifying how locations are divided into clusters and clusters into nodes
			 * clusters = parallelized within one computer
			 * nodes = parallelized across computers*/
			public static String CLUST_FILE = null;
			/**should be set to length of time available on HPC, to ensure prints loadable output immediately before job evicted*/
			public static double EXPERIMENT_HOURS = 60;
			
			
			/**Used internally to distinguish sunken lineages*/
			public final static int SINK_OFFSET = (int) 1e9;
			
			public static final int LOAD_SEED = Integer.MIN_VALUE;


			
			
			
			
			
			
			
	/**Loads settings from settings file or command line. 
	 * Command line may contain "SETTINGS [name of settings file]" 
	 * and may instead of/ additionally contain settings listed as "[name of setting] [value of setting]"
	 * (in all cases replace the square brackets and text inside with your own text)	
	 * 
	 * @param args command line arguments as array
	 * @param nodeNum current node so only prints info about settings once (on node 0)
	 * @param verbose if true prints info about settings to screen
	 */
	public static void loadSettings(String[] args, boolean isController, boolean verbose) {
		int argI = 0;
		Scanner fileReader = null;

        
		try{//if settings file specified
			if(args.length > 1 && args[0].trim().toUpperCase().equals("SETTINGS")) { 
				fileReader = new Scanner(new File(args[1]));
				argI += 2;
				if(isController && verbose)
					System.out.println("*** using settings file ***: " + args[0]);
			}

			while ((fileReader != null && fileReader.hasNextLine()) || argI < args.length) {
            	
            	String nextLine = "";
            	if(fileReader != null && fileReader.hasNextLine()) { //do arguments from settings file first
            		nextLine = fileReader.nextLine();
    				if(isController && verbose)
    					System.out.println("using settings file setting: " + nextLine);
            	}
            	else { //if they're all done use command arguments
            		nextLine = args[argI] + ":" + args[argI + 1];
            		argI += 2;
    				if(isController && verbose)
    					System.out.println("using cmd line setting: " + nextLine);
            	}
            	if(nextLine.trim().length() > 0 && !nextLine.startsWith("//")) { //not a comment or blank
	            	
	            	String[] lineSplit = nextLine.split(":");
	            	String varName = lineSplit[0].trim().toUpperCase();
	            	String varVal = lineSplit[1].trim();
	            	
	            	switch(varName){

	            	
		            	case "GROWTH_RATE":
		            		GROWTH_RATE_DAY = Double.parseDouble(varVal);
		            		break;
		            	case "MORTALITY":
		            		MORTALITY_DAY = Double.parseDouble(varVal);
		            		break;
		            	case "GROWTH_HOURS":
		            		GROWTH_HOURS = Double.parseDouble(varVal);
		            		break;		            	
		            	case "K":
			            	K = (int) Double.parseDouble(varVal);
			            	break;
			            					//(legacy compatibility)
							            	case "CC":
								            	K = (int) Double.parseDouble(varVal);
								            	break;
								            //
		            	case "W":
		            		W = Float.parseFloat(varVal);
		            		break;	

		            	case "DURATION":
		            		DURATION = Integer.parseInt(varVal);
		            		break;
		            	case "STOP_AT_1":
		            		STOP_AT_1 = varVal.toLowerCase().equals("TRUE");
		            		break;

		            		
		            	case "FILE_OUT":
		            		FILE_OUT = varVal; 
		            		break;
		            		

		            	case "NUM_BOXES":
		            		NUM_BOXES = Integer.parseInt(varVal);
		            		break;

		            	case "DISP_HOURS":
		            		DISP_HOURS = Float.parseFloat(varVal);
		            		break;
		            	case "DISP_SCALER":
		            		DISP_SCALER = Double.parseDouble(varVal);
		            		break;
		            	case "TM_FILE":
		            		TM_FILE = varVal;
		            		break;
		            	case "LOAD_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			LOAD_FILE = null;
		            		else
		            			LOAD_FILE = varVal;
		            		break;
		            	case "LOAD_DAY_STRING":
		            		LOAD_DAY_STRING = varVal.toLowerCase();
		            		break;
		            	case "LOAD_DAY": //legacy
		            		LOAD_DAY_STRING = varVal.toLowerCase();
		            		break;
		            	case "TRACER_MODE":
		            		TRACER_MODE = varVal.toLowerCase().equals("true");
		            		break;
		            	case "INIT_LIN_SIZE":
		            		INIT_LIN_SIZE = Integer.parseInt(varVal);
		            		break;
		            	case "SEED":
		            		if(varVal.toLowerCase().equals("load"))
		            			SEED = LOAD_SEED;
		            		else
		            			SEED = Integer.parseInt(varVal);
		            		break;
		            	case "TEMP_START_RANGE":
		            		TEMP_START_RANGE = Float.parseFloat(varVal);
		            		break;
		            	case "CLUST_FILE":
		            		CLUST_FILE = varVal.trim();
		            		break;
		            	case "VOL_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			VOL_FILE = null;
		            		else
		            			VOL_FILE = varVal;
		            		break;
		            	case "VOLS_FILE": //back compatibility
		            		if(varVal.toLowerCase().equals("none"))
		            			VOL_FILE = null;
		            		else
		            			VOL_FILE = varVal;
		            		break;
		            	case "TEMP_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			TEMP_FILE = null;
		            		else
		            			TEMP_FILE = varVal;
		            		break;
		            	case "SIZE_REFUGE":
		            		SIZE_REFUGE = Double.parseDouble(varVal);
		            		break;
		            	case "EXPERIMENT_HOURS":
		            		EXPERIMENT_HOURS = Double.parseDouble(varVal);
		            		if(EXPERIMENT_HOURS == -1)
		            			EXPERIMENT_HOURS = Integer.MAX_VALUE;
		            		break;
		            	case "BUILD_TM":
		            		BUILD_TM = varVal.toLowerCase().equals("true");
		            		break;
		            	case "NUM_ROWS":
		        			MakeArtificialTM.NUM_ROWS = Integer.parseInt(varVal);
		            		break;
		            	case "NUM_COLS":
		            		MakeArtificialTM.NUM_COLS = Integer.parseInt(varVal);
		            		break;
		            	case "DISP":
		            		MakeArtificialTM.DISP = Double.parseDouble(varVal);
		            		break;
		            	case "DISP_OUT":
		            		MakeArtificialTM.DISP_OUT = Double.parseDouble(varVal);
		            		break;
		            	case "DISP_IN":
		            		MakeArtificialTM.DISP_IN = Double.parseDouble(varVal);
		            		break;	

		            	case "GYRE":
		            		MakeArtificialTM.GYRE = varVal.toLowerCase().equals("true");
		            		break;
		            	case "SPLIT_COL":
		            		MakeArtificialTM.SPLIT_COL = Double.parseDouble(varVal);
		            		break;
		            	case "NUM_CORES":
		            		MakeArtificialTM.NUM_CORES = Integer.parseInt(varVal);
		            		break;
		            		

		            	case "REPORT_TIMESTEPS_FILE":			            		
		            		if(varVal.toLowerCase().equals("none"))
		            			REPORT_TIMESTEPS_FILE = null;
		            		else
		            			REPORT_TIMESTEPS_FILE = varVal;
		            		break;
		            		
		            	case "SAVE_TIMESTEPS_FILE":			            		
		            		if(varVal.toLowerCase().equals("none"))
		            			SAVE_TIMESTEPS_FILE = null;
		            		else
		            			SAVE_TIMESTEPS_FILE = varVal;
		            		break;	
		            	case "TIME_THRESH":
		            		TIME_THRESH = Integer.parseInt(varVal);
		            		break;
		            	case "TOP_DOWN":
		            		TOP_DOWN = varVal.toLowerCase().equals("true");
		            		break;

		            	default :
		            		throw new Exception("Setting " + varName + " not recognised.");
	            	}
	            	
	            	
            	}
            }

        }
        catch (Exception e){
            e.printStackTrace();
            System.exit(-1);
        }

		
		calculateDerivedSettings(isController);
	}









	private static void calculateDerivedSettings(boolean isController) {
		if(Settings.TOP_DOWN)
			P_0 = 1.0;
		MORTALITY = MORTALITY_DAY * (GROWTH_HOURS / 24.0);
    	GROWTH_RATE = GROWTH_RATE_DAY * (GROWTH_HOURS / 24.0);
    	GROWTH_PER_DISP = DISP_HOURS / GROWTH_HOURS;
    	
    	INITIAL_P = K * Settings.P_0;
    	
    	if(TRACER_MODE) {
    		MORTALITY = 0;
    		GROWTH_RATE = 0;
    	}
    	
    	//separate out file names and directories
    	String[] filepath = Settings.FILE_OUT.split("/");
    	String filename = filepath[filepath.length - 1];
    	DIR_OUT = FILE_OUT.replace("/" + filename, "");
    	FILE_OUT = filename;
    	if(LOAD_FILE != null) {
	    	filepath = Settings.LOAD_FILE.split("/");
	    	filename = filepath[filepath.length - 1];
	    	LOAD_DIR = LOAD_FILE.replace("/" + filename, "");
	    	LOAD_FILE = filename;
    	}

        //print settings to file
    	if(isController)
	    	FileIO.makeSettingsFile(DIR_OUT + "/Settings for " + FILE_OUT + ".txt");
    	
    	if(SAVE_TIMESTEPS_FILE != null) {
    		IntStream tsStream = FileIO.loadIntSet(SAVE_TIMESTEPS_FILE).stream().mapToInt(e -> e).sorted();
    		SAVE_TIMESTEPS = tsStream.toArray();
    	}
    	else
    		SAVE_TIMESTEPS = DEFAULT_SAVE_TIMESTEPS;
    	
    	
    	
    	if(REPORT_TIMESTEPS_FILE != null) {
    		IntStream tsStream = FileIO.loadIntSet(REPORT_TIMESTEPS_FILE).stream().mapToInt(e -> e).sorted();
    		REPORT_TIMESTEPS = tsStream.toArray();
    	}
    	else
    		REPORT_TIMESTEPS = DEFAULT_REPORT_TIMESTEPS;

    	
    	
    	if(LOAD_DAY_STRING != null) {
	    	String[] loadDayBits = LOAD_DAY_STRING.split("h");
	    	LOAD_DAY = Integer.parseInt(loadDayBits[0]);
	    	LOAD_HOUR = (LOAD_DAY * 24);
	    	if(loadDayBits.length > 1)
	    		LOAD_HOUR = LOAD_HOUR + Integer.parseInt(loadDayBits[1]);
    	}
		
	}



}

