package lbm;

import java.util.Arrays;
import java.util.Scanner;
import java.io.File;

public class Settings{
	
	
	/** GROWTH */
	static double MORTALITY_DAY = 0.1;
	public static double MORTALITY;
	static double GROWTH_RATE_DAY = 0.8;
	public static double GROWTH_RATE;
	static double GROWTH_HOURS = 24.0;
	static double GROWTH_PER_DISP;
	
	static boolean TRACER_MODE =false;
	
	/** DISPERSAL */
	public static double DISP_HOURS = 24.0;


	public static int CC = 100;
	public static int INITIAL_P = 7;

	public static String VOLS_FILE = null; //"vols6386.csv"; 
	
	static double addAmount = 1/CC;


	
	/**** OUTPUT ****/
	/** COALESCENCE **/
	
	static int CHECKPOINT_DAY = 365 * 24;
	static int DURATION = 10000;
	
	public static String FILE_OUT = "Maps/D";
	static int EQUI_POINT = 0;
	
	static int COALESCENCE_TIME_THRESH = 1000;
	public static int CLEAR_PARENTS_DAY = 0;
	public static int PRINT_ALL = 100;
	public static boolean NEUTRAL = true;
	public static int NUM_BOXES = 600;
	public static int NEXT_COL = 18;
	
	public static double LOW_DISP = 1;
	public static String SMPL_FILE = "res100.txt"; //"TMD6386.txt";
	public static double ABS_DISP;
	public static double MISSING_PROB = 0;
	public static int LIN_INTV = 90;
	public static int BIN_THRESH = 10;
	public static double UNIFORM_PROB = 0;
	
	public static String LOAD_DIR = "Maps";
	public static double LOAD_YEAR = 0;
	public static boolean USE_ARRAY = false;
	public static String POP_FILE = null; //"volsGlobal.csv";

	public static int START_EXTINCT = -1;
	public static int END_EXTINCT = -1;
	
	public static int NORM_MEAN = 2;
	public static int NORM_SD = 1;
	public static int INIT_LIN_SIZE = 1;

	public static int[] SEED = new int[] {-1}; //{1794952784,1132657726};//new int[] {-1};
	public static int SAVE_INTV = 100;
	
	
	/** SELECTION */
	public static float W = 12.0f;
	public static String TEMP_FILE = null; //"temps6386-test.csv";
	public static float TEMP_INTV = 0.01f;
	public static float TEMP_MIN = -7.0f;
	static float TEMP_MAX = 35.0f;
	
	public static int BIGLIN = 10;
	public static float TEMP_START_RANGE = 5.0f;
	public static String CLUST_FILE = "clusts600.csv";//"clusters6386b.csv";
	public static String LOAD_FILE = null; //"01ks[0-9]+";
	public static boolean SEND_GLOBAL = true;
	public static boolean LOAD_DIST = true;
	public static int LOAD_STEP;
	public static double REPORT_INTV = 1;
	public static long TIME_THRESH = 100;
	public static double SIZE_REFUGE = 0; //0.05;
	public static double EXPERIMENT_HOURS = 0;
	public static int LOAD_HOUR = 0;
	public static String SUBSET_FILE = "pacAtlSmpl.csv";
	public static String MODULE_FILE = "100k10clusts.csv";
	public static boolean OUTPUT_TOPT = false;
	public static boolean DO_SUBSETS = false;
	public static int SUBSET_INTV = 10;
	public static boolean REPORT_HALF = false;
	public static int START_LOAD_YEAR = 100;
	public static int SMPL_SIZE = 10;
	public static int SINK_OFFSET = (int) 1e9;
	public static boolean SAVE_DAILY = false;
	public static boolean REPORT_DAILY = false;
	
	
		
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
            	if(!nextLine.startsWith("//")) { //not a comment
	            	
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

		            	case "TEMP_MIN":
		            		TEMP_MIN = Float.parseFloat(varVal);
		            	case "TEMP_MAX":
			            	TEMP_MAX = Float.parseFloat(varVal);
		            	case "COALESCENCE_TIME_THRESH":
		            		COALESCENCE_TIME_THRESH = Integer.parseInt(varVal);
		            		break;
		            	case "CLEAR_PARENTS_DAY":
		            		CLEAR_PARENTS_DAY = Integer.parseInt(varVal);
		            		break;
		            		
		            	case "CHECKPOINT_DAY":
		            		CHECKPOINT_DAY = Integer.parseInt(varVal);
		            		break;
		            		
		            	case "DURATION":
		            		DURATION = Integer.parseInt(varVal);
		            		break;

		            		
		            	case "FILE_OUT":
		            		FILE_OUT = varVal; 
		            		break;
		            		
		            	case "EQUI_POINT":
		            		EQUI_POINT = Integer.parseInt(varVal);
		            		break;
		            		
		            	case "PRINT_ALL":
		            		PRINT_ALL = Integer.parseInt(varVal);
		            		break;
		            	case "NEUTRAL":
		            		NEUTRAL = varVal.toLowerCase().equals("true");
		            		break;
//		            	case "DISP_PER_DAY":
//		            		DISP_PER_DAY = Double.parseDouble(varVal);
//		            		break;
		            	case "NUM_BOXES":
		            		NUM_BOXES = Integer.parseInt(varVal);
		            		break;
		            	case "NEXT_COL":
		            		NEXT_COL = Integer.parseInt(varVal);
		            		break;
		            	case "DISP_HOURS":
		            		DISP_HOURS = Float.parseFloat(varVal);
		            		break;
		            	case "LOW_DISP":
		            		LOW_DISP = Double.parseDouble(varVal);
		            		break;
		            	case "SMPL_FILE":
		            		SMPL_FILE = varVal;
		            		break;
		            	case "BIN_THRESH":
		            		BIN_THRESH = Integer.parseInt(varVal);
		            		break;
		            	case "MISSING_PROB":
		            		MISSING_PROB = Double.parseDouble(varVal);
		            		break;
		            	case "UNIFORM_PROB":
		            		UNIFORM_PROB = Double.parseDouble(varVal);
		            		break;
		            	case "LOAD_FILE":
		            		if(varVal.toLowerCase().equals("none"))
		            			LOAD_FILE = null;
		            		else
		            			LOAD_FILE = varVal;
		            		break;
		            	case "LOAD_YEAR":
		            		LOAD_YEAR = Double.parseDouble(varVal);
		            		break;
		            	case "USE_ARRAY":
		            		USE_ARRAY = varVal.toLowerCase().equals("true");
		            		break;
		            	case "POP_FILE":
		            		POP_FILE = varVal;
		            		break;
		            	case "START_EXTINCT":
		            		START_EXTINCT = Integer.parseInt(varVal);
		            		break;
		            	case "END_EXTINCT":
		            		END_EXTINCT = Integer.parseInt(varVal);
		            		break;
		            	case "TRACER_MODE":
		            		TRACER_MODE = varVal.toLowerCase().equals("true");
		            		break;
		            	case "SEND_GLOBAL":
		            		SEND_GLOBAL = varVal.toLowerCase().equals("true");
		            		break;
		            	case "LOAD_DIST":
		            		LOAD_DIST = varVal.toLowerCase().equals("true");
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
		            	case "LOAD_DIR":
		            		LOAD_DIR = varVal.trim();
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
		            		if(varVal.equals("0"))
		            				SIZE_REFUGE = 1;
		            		else
		            			SIZE_REFUGE = 1 + (Math.pow( 10,-Integer.parseInt(varVal)));
		            		if(nodeNum == 0 && verbose)
		            			System.out.println("setting size refuge to " + SIZE_REFUGE);
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
		            	case "START_LOAD_YEAR":
		            		START_LOAD_YEAR = Integer.parseInt(varVal);
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
    	
    	ABS_DISP = 1.0; //- (1.0/NUM_BOXES);
    	
    	if(TRACER_MODE) {
    		//INITIAL_P = CC;
    		MORTALITY = 0;
    		GROWTH_RATE = 0;
    	}
    	
    	TEMP_INTV = (float) ( (1.0 / INITIAL_P) * (Settings.TEMP_START_RANGE * 2.0));
    	
    	LOAD_STEP = OUTPUT_TOPT ? 3 :2;
    	
    	if(LOAD_FILE == null)
    		LOAD_YEAR = 0; 
    	
	}



}

