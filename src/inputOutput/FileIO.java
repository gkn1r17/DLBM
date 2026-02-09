/**Collection of static methods handling
 * everything to do with loading from and saving to files
 * 
 */

package inputOutput;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import control.Runner;
import transportMatrix.GridBox;


public class FileIO {


	

	
	/**Loads Transport Matrix from TSV column format:
	 * Column1 = from
	 * Column2 = to
	 * Column3 = probability
	 * 
	 * @param filename
	 * @param vols 2d array where [normalised volume of each grid box][0]
	 * 					or null if all equal volume
	 * @param temps 2d array where [temperature of each grid box][month/day etc. or 0 if not seasonal]
	 * 					or null if non selective
	 * @param verbose
	 * @return all GridBoxes
	 * @throws FileNotFoundException
	 */
	public static GridBox[] loadTM(String filename, double[][] vols, double[][] temps, boolean verbose) throws FileNotFoundException {
		
		//double maxStay = 0;
		
		GridBox[] boxes = new GridBox[Runner.settings.NUM_BOXES];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[0]) - 1;
                int dest = (int) Double.parseDouble(tokens[1]) - 1;
                
                
                if(from < Runner.settings.NUM_BOXES && dest < Runner.settings.NUM_BOXES) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * Runner.settings.SCI.DISP_SCALER; // * (settings.DISP_HOURS / 24.0);
	                
	                if(boxes[from] == null) {
	                	boxes[from] = new GridBox(from, 
	                			vols == null ? 1.0 : vols[from][0],
	                					// temperature = eiter non selective run (-999)
	                						//or from temps array
	                			temps == null ? new double[] {-999.0} : temps[from]);
	                }
	
	                if(prob != 0 && from != dest) {
	            		GridBox destBox = boxes[dest];
	            		if(destBox == null) { 
	            			boxes[dest] = new GridBox(dest, 
	            					vols == null ? 1 : vols[dest][0], 
	            					temps == null ? new double[] {-999.0} : temps[dest]);
	            			destBox = boxes[dest];
	            		}
		                boxes[from].addDest(prob, boxes[dest], null);
	                }
                }



            }
            
            myFileReader.close();
            
            for(int i =0 ; i < Runner.settings.NUM_BOXES; i++) {
            	try {
					boxes[i].sortMovers(boxes);
				} catch (Exception e) {
					e.printStackTrace();
				}
            }
        
        
        
        }catch (FileNotFoundException e){
            throw e;
        }
        return boxes;
		
	}
	
	
	



	/**Loads previous run population at specified day from csv file format:
	 * box id,lineageID1,num1,lineageID2,num2,...
	 * box id,.... 
	 * 
	 * @param inFile path to csv file to load from
	 * @param boxes all GridBoxes, to add population to
	 * @param tempLins <GridBox,Temperature> for all GridBoxes, to fill
	 * @return
	 * @throws Exception if can't find file or any other problem loading file
	 */
	public static ArrayList<GridBox> loadDay(String inFile, GridBox[] boxes, ConcurrentHashMap<Long, Float> tempLins) throws Exception {
		
		//get day to load
		long day = (long) Math.floor(Runner.settings.CTRL.LOAD_HOUR / 24);
		long hourOfDay = Runner.settings.CTRL.LOAD_HOUR - (day * 24);
		
		//fill with boxes present on this machine
				//(on this node if running distributed or all nodes if running locally)
		ArrayList<GridBox> activeBoxes = new ArrayList<GridBox>();
		
		//produce regular expression for finding all files
		inFile = inFile + "s[0-9]+_D" + day + "(hr" + hourOfDay + ")?"+  "_N[0-9]+\\.csv";
		String filename = inFile.replace(Runner.settings.LOAD_DIR + "/", "");
		
		
        try{
		    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
		    ArrayList<String> files = Stream.of(new File(Runner.settings.LOAD_DIR   ).listFiles())
					      .filter(file -> !file.isDirectory())
					      .map(File::getName)
					      .filter(f -> pattern.matcher(f).find())
					      .collect(Collectors.toCollection(ArrayList::new));
			
		    if(files.size() == 0)
		    	throw new Exception("Load file not found \"" + inFile + "\"");
		    
		    //either just this node if running distributed or all nodes if running locally
		    HashSet<Integer> nodesHandled = new HashSet<Integer>();
		    
		    
		    for(String f: files) {
		    	int nodeNum = Integer.parseInt(f.split("_N")[1].split("\\.csv")[0]);
		    	if(nodesHandled.contains(nodeNum))
		    		throw new IOException("More than one matching file found filename = \"" + filename + "\"");
		    	System.out.println("Loading File: \"" + filename + "\"" );
		    	
		    	nodesHandled.add(nodeNum);
		    
				filename = Runner.settings.LOAD_DIR + "/" + f;	

	        	Scanner myFileReader = new Scanner(new File(filename));
	        	
	        	int boxI = 0;
	        	
	        	//header line indicates whether contains temperatures and birth hours
	        	String[] tokens = myFileReader.nextLine().trim().split(",");
	        	boolean tempsIncluded = false;
	        	boolean birthHoursIncluded = false;
	        	if(tokens.length > 0 && tokens[0].trim().equals("temps"))
	        		tempsIncluded = true;
	        	else if(Runner.settings.IS_SELECTIVE) {
	        		myFileReader.close();
    				throw new Exception("To run simulation with selection"
							+ " loading old results, these results must include t_opt."
							+ "Can run simulation with selection using non selective results.");
	        	}
	        		
	        	if(tokens.length > 1 && tokens[1].trim().equals("birthHours"))
	        		birthHoursIncluded = true;
	        	else if(Runner.settings.CTRL.SAVE_BIRTHHOUR != true)
	    			System.err.println("WARNING: saving birth hours but loading from file without birth hours."
	    					+ "Loaded population birth hours will be set to zero (unless loaded from phylogeny)");

	        	
	        	
	        	if(tempsIncluded == false && birthHoursIncluded == false && !tokens[0].trim().equals("") ) {
	        		System.err.println("WARNING: old format csv files without header line. "
	        				+ "Assuming doesn't contain t_opt or birth hours but please check");
	        		myFileReader.close();
	        		myFileReader = new Scanner(new File(filename)); //restart
	        	}
	        		
	        	//read main data lines
	            while (myFileReader.hasNextLine()){
	            	tokens = myFileReader.nextLine().trim().split(",");
	            	
	            	GridBox box = boxes[Integer.parseInt(tokens[0])];
	            	if(box == null) //box not handled on this machine
	            		continue;
	            		
	            	box.addLoadedPop(tokens, tempLins, tempsIncluded, birthHoursIncluded);
	                boxI++;
	                activeBoxes.add(box);
	                
	                //if only using part of TM or extra lines in input file for some reason
	                if(boxI == Runner.settings.NUM_BOXES)
	                	break;
	            }
	            myFileReader.close();
		    }
        }
        catch (FileNotFoundException e){
        	System.err.println("Fatal Exception: LOAD_FILE \"" + filename + "\" not found.");
        	throw e;
        }
		return activeBoxes;
        
	}

	/**Save population at a day to a file
	 * 
	 * @param activeBoxes boxes handled on this machine (all nodes if running locally, just this node if running distributed)
	 * @param filename output filename
	 */
	public static void savePop(List<GridBox> activeBoxes, String filename) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,true);
			
			String header = "";
			if(Runner.settings.IS_SELECTIVE)
				header = header + "temps";
			if(Runner.settings.CTRL.SAVE_BIRTHHOUR)
				header = header + "," + "birthHours";
			
			
			outputfile.write(header + "\n");
			for(GridBox box : activeBoxes) {
				//add total
				outputfile.write(
						box.id + "," + box.writeDetails() + "\n"
					);
			}
			outputfile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	
	

	/**Loads 2d array of doubles from csv file
	 * 
	 * @param filename
	 * @return
	 * @throws Exception if mismatch in array length, number of boxes
	 */
	public static double[][] loadDoubleFile(String filename) throws Exception {
		if(filename == null) {
			return null;
		}
		double[][] vols = new double[Runner.settings.NUM_BOXES][];
        Scanner myFileReader = new Scanner(new File(filename));
        int boxI = 0;
        while(myFileReader.hasNext()) {
        	//String[] nextLineBits = .split(",");
        	vols[boxI] = getDoubleArrayFromString(myFileReader.nextLine());
		    boxI++;
        }
        myFileReader.close();
		if(boxI < Runner.settings.NUM_BOXES)
		    throw new Exception("Number of lines in file " + filename + " < number of boxes");
        return vols;
	}
	
	/**Loads of set of doubles from csv file
	 * 
	 * @param filename
	 * @return
	 */
	public static HashSet<Long> loadLongSet(String filename) {
		if(filename == null) {
			return null;
		}
		
		HashSet<Long> longSet = new HashSet<Long>();

		
		Scanner myFileReader;
		try {
			myFileReader = new Scanner(new File(filename));
	        while(myFileReader.hasNext())
	        	longSet.add (getLongFromString(myFileReader.nextLine()));
	        myFileReader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        return longSet;	
     }

	/**Parse csv string into array of doubles
	 * 
	 * @param str
	 * @return
	 */
	private static double[] getDoubleArrayFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9|\\.)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.err.println("WARNING: possible character error in input file. Replacing " + str + " with " + str2);

		return  Arrays.asList(str2.split(",")).stream().mapToDouble(e -> Double.parseDouble(e)).toArray();
	}

	
	
	/**Parse string into Long, handling erroneous characters
	 * 
	 * @param str
	 * @return
	 */
	public static long getLongFromString(String str) {
		str = str.trim();
		String str2 = str.replaceAll("[^(E|,|\\-|0-9)]", ""); //get rid of special characters
		
		//warn in output if special characters replaced
		if(!str.equals(str2))
			System.err.println("WARNING: possible character encoding error in input file. "
					+ "Check that " + str + " = " + str2);

		return Long.parseLong(str2);
	}








	/**record settings in file 
	 * @throws IOException */
	public static void makeSettingsFile(String filename, String settingsStr) throws IOException {
		
		//create directory
		File outFile = new File(filename +  "_Settings.ini");
		
		//save
		try {
			FileWriter outFileWriter = new FileWriter(outFile,false);
			outFileWriter.write(settingsStr);
			outFileWriter.close();
		} catch (IOException e) {
			throw e;
		}				
	}





}

