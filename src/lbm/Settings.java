package lbm;

import java.util.Arrays;
import java.util.Scanner;

import util.FileIO;

import java.io.File;

public class Settings{
	
	
	/************** SCIENTIFIC SETTINGS *********************************/
	/********************************************************************/
	/********************************************************************/
	/********************************************************************/
	
			public static int DURATION = 1; //total duration in years
			public static int INIT_LIN_SIZE = 1;
			public static int[] SEED = new int[] {-1}; //{1794952784,1132657726};//new int[] {-1};
			public static int CC = 100000;
					/** Initial population, derived as CC * 0.875 - DO NOT CHANGE */
					public static int INITIAL_P;

			
			public static boolean TRACER_MODE =false;
			
			/** TRANSPORT MATRIX */
			public static int NUM_BOXES = 6386;
			public static String TM_FILE = "TMD6386.txt";
			public static int NUM_ROWS = 1;
			public static int NUM_COLS = 1;
			public static double UNIFORM_DISP = 0.0001;
			public static int NUM_CORES = 40;


			
			/** GROWTH */
			public static double MORTALITY_DAY = 0.1; //mortality per day
			public static double GROWTH_RATE_DAY = 0.8; //growth rate per day
			public static double GROWTH_HOURS = 24.0 * 5;
		
			
			/** DISPERSAL */
			public static double DISP_HOURS = 24.0 * 5;
			public static String VOLS_FILE = "vols6386.csv"; 
			public static double DISP_COEF = 1;
		
		
					/** Growth/Dispersal - derived parameters DO NOT CHANGE */
					static double GROWTH_PER_DISP;
					public static double GROWTH_RATE;
					public static double MORTALITY;
					//by default with 1 day ts for growth/mortality and dispersal:
								//GROWTH_PER_DISP=1, GROWTH_RATE=GROWTH_RATE_DAY, MORTALITY=MORTALITY_DAY 
			
			/** SELECTION */
			public static float W = 12.0f;
			public static String TEMP_FILE = null; //"temps6386-daily.csv";
			public static float TEMP_START_RANGE = 10.0f;
			
			
			/** DORMANCY */	
			public static double SIZE_REFUGE = 0.5;

			
			
			
	/************** REPORTING/OPTIMIZATION SETTINGS *********************************/
	/********************************************************************/
	/********************************************************************/
	/********************************************************************/


			/** OUTPUT */
			public static String FILE_OUT = "Maps/blah";
			public static int SAVE_INTV = 10000;
			public static double REPORT_INTV = 1;
			public static boolean OUTPUT_TOPT = false;
			public static boolean REPORT_HALF = false;
					/** DERIVED - DON'T CHANGE */
					public static String DIR_OUT;

		
			/** LOADING PREVIOUS RUNS */
			public static int LOAD_YEAR = 0; //1;
			public static String LOAD_FILE = null; //"Maps/D";
			public static int LOAD_STEP;
			public static int LOAD_YEAR_SCAN_START = 0;
			public static int LOAD_YEAR_SCAN_INTV = 10;
			public static int LOAD_HOUR = 0; //33120; //only use when loading from job saved just before HPC eviction with EXPERIMENT_HOURS
					/** DERIVED - DON'T CHANGE */
					public static String LOAD_DIR;

			/** PARALLELIZATION / DISTRIBUTION and miscellaneous purely technical options */	
			public static String CLUST_FILE = "clusters6386c.csv";
			public static long TIME_THRESH = 100; //time in seconds before outputs message just to show hasn't crashed - if no proper update yet happened 
			public static double EXPERIMENT_HOURS = 60; //should be set to length of time available on HPC, to ensure prints loadable output immediately before job evicted
		
			public static String SUBSET_FILE = null; //"pacAtlSmpl.csv";
			public static String MODULE_FILE = null; //"100k10clusts.csv";
			public static boolean DO_SUBSETS = false;
			public static int SUBSET_INTV = 10;
			public static int SMPL_SIZE = 10;
			public static int SINK_OFFSET = (int) 1e9;
			public static boolean SAVE_DAILY = false;
			public static boolean REPORT_DAILY = false;
			public static double REPORT_GEN = 0.01;
	
	
		
	public static void loadSettings(String[] args, int nodeNum, boolean verbose) {
		int argI = 0;
		Scanner fileReader = null;

        
		try{
			if(args.length > 1 && args[0].trim().toUpperCase().equals("SETTINGS")) { //if settings file specified
				fileReader = new Scanner(new File(args[1]));
				argI += 2;
				if(nodeNum == 0 && verbose)
					System.out.println("*** using settings file ***: " + args[0]);
			}

			while ((fileReader != null && fileReader.hasNextLine()) || argI < args.length) {
            	
            	String nextLine = "";
            	if(fileReader != null && fileReader.hasNextLine()) { //do arguments from settings file first
            		nextLine = fileReader.nextLine();
    				if(nodeNum == 0 && verbose)
    					System.out.println("using settings file setting: " + nextLine);
            	}
            	else { //if they're all done use command arguments
            		nextLine = args[argI] + ":" + args[argI + 1];
            		argI += 2;
    				if(nodeNum == 0 && verbose)
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
		            	case "CC":
			            	CC = (int) Double.parseDouble(varVal);
			            	break;
		            	case "INITIAL_P":	
			            	INITIAL_P = (int) Double.parseDouble(varVal);
			            	break;
		            	case "W":
		            		W = Float.parseFloat(varVal);
		            		break;	

		            	case "DURATION":
		            		DURATION = Integer.parseInt(varVal);
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
		            	case "DISP_COEF":
		            		DISP_COEF = Double.parseDouble(varVal);
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
		            	case "LOAD_YEAR":
		            		LOAD_YEAR = Integer.parseInt(varVal);
		            		break;
		            	case "TRACER_MODE":
		            		TRACER_MODE = varVal.toLowerCase().equals("true");
		            		break;
		            	case "INIT_LIN_SIZE":
		            		INIT_LIN_SIZE = Integer.parseInt(varVal);
		            		break;
		            	case "SEED":
		            		SEED = Arrays.asList(varVal.split(",")).stream().mapToInt(e -> Integer.parseInt(e)).toArray();
		            		break;
		            	case "SAVE_INTV":
		            		SAVE_INTV = Integer.parseInt(varVal);
		            		break;
		            	case "TEMP_START_RANGE":
		            		TEMP_START_RANGE = Float.parseFloat(varVal);
		            		break;
		            	case "CLUST_FILE":
		            		CLUST_FILE = varVal.trim();
		            		break;
		            	case "VOLS_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			VOLS_FILE = null;
		            		else
		            			VOLS_FILE = varVal;
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
		            		break;
		            	case "LOAD_HOUR":	
		            		LOAD_HOUR =  Integer.parseInt(varVal);
		            		break;
		            	case "SUBSET_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			SUBSET_FILE = null;
		            		else
		            			SUBSET_FILE = varVal;
		            		break;	
		            	case "MODULE_FILE":
			            		if(varVal.toLowerCase().equals("none"))
			            			MODULE_FILE = null;
			            		else
			            			MODULE_FILE = varVal;
			            		break;	
		            	case "OUTPUT_TOPT":
		            		OUTPUT_TOPT = varVal.toLowerCase().equals("true");
		            		break;
		            	case "DO_SUBSETS":
		            		DO_SUBSETS = varVal.toLowerCase().equals("true");
		            		break;	
		            	case "SUBSET_INTV":
		            		SUBSET_INTV =  Integer.parseInt(varVal);
		            		break;
		            	case "REPORT_HALF":
		            		REPORT_HALF = varVal.toLowerCase().equals("true");
		            		break;
		            	case "LOAD_YEAR_SCAN_START":
		            		LOAD_YEAR_SCAN_START = Integer.parseInt(varVal);
		            		break;
		            	case "LOAD_YEAR_SCAN_INTV":
		            		LOAD_YEAR_SCAN_INTV = Integer.parseInt(varVal);
		            		break;
		            	case "SMPL_SIZE":
		            		SMPL_SIZE = Integer.parseInt(varVal); 
		            		break;
		            	case "SAVE_DAILY":
		            		SAVE_DAILY = varVal.toLowerCase().equals("true");
		            		break;
		            	case "REPORT_DAILY":
		            		REPORT_DAILY = varVal.toLowerCase().equals("true");
		            		break;
		            	case "REPORT_INTV":
		            		REPORT_INTV = Integer.parseInt(varVal);
		            		break;
		            	case "REPORT_GEN":
		            		REPORT_GEN = Double.parseDouble(varVal);
		            		break;
		            	case "NUM_ROWS":
		        			NUM_ROWS = Integer.parseInt(varVal);
		            		break;
		            	case "NUM_COLS":
		        			NUM_COLS = Integer.parseInt(varVal);
		            		break;
		            	case "UNIFORM_DISP":
		            		UNIFORM_DISP = Double.parseDouble(varVal);
		            		break;
		            	case "NUM_CORES":
		            		NUM_CORES = Integer.parseInt(varVal);
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
        //////// derived parameters //////
    	MORTALITY = MORTALITY_DAY * (GROWTH_HOURS / 24.0);
    	GROWTH_RATE = GROWTH_RATE_DAY * (GROWTH_HOURS / 24.0);
    	GROWTH_PER_DISP = DISP_HOURS / GROWTH_HOURS;
    	
    	INITIAL_P = (int) (CC * 0.875);
    	
    	if(TRACER_MODE) {
    		MORTALITY = 0;
    		GROWTH_RATE = 0;
    	}
    	
    	LOAD_STEP = OUTPUT_TOPT ? 3 :2;
    	
    	if(LOAD_FILE == null)
    		LOAD_YEAR = 0; 
    	
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
    	if(nodeNum == 0)
	    	FileIO.makeSettingsFile(DIR_OUT + "/Settings for " + FILE_OUT + ".txt");
    	
    	//set up scanning for when you don't know what the latest available year to load will be
    	if(LOAD_YEAR_SCAN_START == 0)
    		LOAD_YEAR_SCAN_START = LOAD_YEAR;
    	
    	if(REPORT_GEN > 0)
    		REPORT_DAILY = false;
    	
    	if(TM_FILE.equals("uniform")) {
    		
    		//adjust for time intervals per generation
    		UNIFORM_DISP = UNIFORM_DISP / (GROWTH_HOURS / (24 * 5)) ;
    		VOLS_FILE = null;
    		TEMP_FILE = null;
    		Settings.NUM_BOXES = Settings.NUM_ROWS * Settings.NUM_COLS;

    	}
    	
    	
	}



}

