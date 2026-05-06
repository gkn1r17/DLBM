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
import java.util.ListIterator;
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
		
		GridBox[] boxes = new GridBox[Runner.settings.numBoxes];
        try{

        	Scanner myFileReader = new Scanner(new File(filename));

        	
            while (myFileReader.hasNextLine()){
                String[] tokens = myFileReader.nextLine().trim().split("\\s+");
                
                int from = (int) Double.parseDouble(tokens[0]) - 1;
                int dest = (int) Double.parseDouble(tokens[1]) - 1;
                
                
                if(from < Runner.settings.numBoxes && dest < Runner.settings.numBoxes) {
                    
                	
                	
	                double prob = Double.parseDouble(tokens[2]) * Runner.settings.sci.dispScaler; // * (settings.DISP_HOURS / 24.0);
	                
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
            
            for(int i =0 ; i < Runner.settings.numBoxes; i++) {
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
		long day = (long) Math.floor(Runner.startHour / 24);
		long hourOfDay = Runner.startHour - (day * 24);
		
		
		//produce regular expression for finding all files
		inFile = inFile + "s[0-9]+_D" + day + "(hr" + hourOfDay + ")?"+  "_N[0-9]+\\.csv";
		String filename = inFile.replace(Runner.settings.loadDir + "/", "");
		
		
	    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
	    ArrayList<String> files = Stream.of(new File(Runner.settings.loadDir   ).listFiles())
				      .filter(file -> !file.isDirectory())
				      .map(File::getName)
				      .filter(f -> pattern.matcher(f).find())
				      .collect(Collectors.toCollection(ArrayList::new));
		return loadFilesForEachNode(files, filename, boxes, tempLins);
        
	}
	
	
	/**Loads previous run population at checkpoint from csv file format:
	 * box id,lineageID1,num1,lineageID2,num2,...
	 * box id,.... 
	 * 
	 * @param inFile path to csv file to load from
	 * @param boxes all GridBoxes, to add population to
	 * @param tempLins <GridBox,Temperature> for all GridBoxes, to fill
	 * @return
	 * @throws Exception if can't find file or any other problem loading file
	 */
	public static ArrayList<GridBox> loadCheckpoint(String inFile, GridBox[] boxes, ConcurrentHashMap<Long, Float> tempLins) throws Exception {
		
		char chkChar = 'B';
		
		while(true) {
			try {
			
				//produce regular expression for finding all files
				String filename = inFile + "_N[0-9]+_CHK" + chkChar + "\\.csv";
				filename = filename.replace(Runner.settings.loadDir + "/", "");
				
				
			    Pattern pattern = Pattern.compile(filename, Pattern.CASE_INSENSITIVE);
			    ArrayList<String> files = Stream.of(new File(Runner.settings.loadDir   ).listFiles())
						      .filter(file -> !file.isDirectory())
						      .map(File::getName)
						      .filter(f -> pattern.matcher(f).find())
						      .collect(Collectors.toCollection(ArrayList::new));
				return loadFilesForEachNode(files, filename, boxes, tempLins);
			}catch(Exception e) {
				//if checkpoint B fails to load (probable interruption while saving) try A
				if(chkChar == 'B')
					chkChar = 'A';
				else //else something else is wrong
					throw e;
			}
		}
	}
	
	/**
	 * 
	 * @param files list of filenames
	 * @param inFile archetype filename "files" derived from 
	 * @param boxes [] of empty GridBox objects to fill with population 
	 * @param tempLins empty HashMap to fill with loaded lineage->temperature pairs
	 * @return Arraylist of filled GridBoxes
	 * @throws Exception file fails to load
	 */
	private static ArrayList<GridBox> loadFilesForEachNode(ArrayList<String> files, String inFile, GridBox[] boxes, ConcurrentHashMap<Long, Float> tempLins) throws Exception {
	    if(files.size() == 0)
	    	throw new FileNotFoundException("Load file not found \"" + inFile + "\"");
	    
		//fill with boxes present on this machine
		//(on this node if running distributed or all nodes if running locally)
	    ArrayList<GridBox> activeBoxes = new ArrayList<GridBox>();

	    
	    //either just this node if running distributed or all nodes if running locally
	    HashSet<Integer> nodesHandled = new HashSet<Integer>();
	    
	    
	    for(String f: files) {
	    	
	    	ArrayList<Integer> boxIDsInFile = new ArrayList<Integer>(Runner.settings.numBoxes);
	    	
	    	int nodeNum = Integer.parseInt(f.split("_N")[1].split("(_|\\.csv)")[0]);
	    	if(nodesHandled.contains(nodeNum))
	    		throw new IOException("More than one matching file found filename = \"" + inFile + "\"");
	    	
	    	nodesHandled.add(nodeNum);
	    
			String filePath = Runner.settings.loadDir + "/" + f;
			
			System.out.println("loading file: " + filePath);

        	Scanner myFileReader = new Scanner(new File(filePath));
        	
        	
        	
        	int boxI = 0;

        	//get first line
        	String[] tokens = myFileReader.nextLine().trim().split(",");

        	
        	//header line 1 indicates time if loading from checkpoint
        	if(tokens.length > 0 && tokens[0].trim().equals("Day")) {
        		Runner.startHour = getTimeFromString(tokens[1]);
            	tokens = myFileReader.nextLine().trim().split(","); //first line of data or extra header in next line

        	}
        	//header line 2 indicates whether contains temperatures and birth hours
        	boolean tempsIncluded = false;
        	boolean birthHoursIncluded = false;
        	if(tokens.length > 0 && tokens[0].trim().equals("temps"))
        		tempsIncluded = true;
        	else if(Runner.settings.isSelective) {
        		myFileReader.close();
				throw new IllegalArgumentException("To run simulation with selection"
						+ " loading old results, these results must include t_opt."
						+ "Can't run simulation with selection using non selective loaded results.");
        	}
        		
        	if(tokens.length > 1 && tokens[1].trim().equals("birthHours"))
        		birthHoursIncluded = true;
        	else if(Runner.settings.ctrl.saveBirthHour)
    			System.err.println("WARNING: saving birth hours but loading from file without birth hours."
    					+ "Loaded population birth hours will be set to zero (unless loaded from phylogeny)");

        	if(tempsIncluded || birthHoursIncluded || tokens[0].trim().equals("") ) {
        		tokens = myFileReader.nextLine().trim().split(",");
        	}
        		
        	//read main data lines
            while (tokens != null){
            	
            	//lines containing population
            			//format = boxID1,linId1,pop1,linId2,pop2,...
            					 //boxID2,...
            	
            	//final line contains maximum up to now lineage IDs 
        		//so know which ID to start from when creating new mutants
            	if(  tokens[0].trim().equals("mutIDs")  ) {
	            		String nextLine = myFileReader.nextLine().trim();
	            		tokens = nextLine.split(",");
	            		ListIterator<Integer> boxIter = boxIDsInFile.listIterator();
	            		for(String token : tokens) {
	            			if(token.length() > 0) {
	            				Integer tokenID = boxIter.next();
	            				GridBox box = boxes[tokenID];
	            				if(box != null)
	            					box.setCurrentMutantID(getLongFromString(token) );
	            			}
	            		}
            	}
            	else {
	            	GridBox box = boxes[Integer.parseInt(tokens[0])];
	            	
	            	//System.out.println(Arrays.toString(tokens));
	            	
	            	//add temperatures associated with lineages (has be here so temperatures for lineages not currently on this node
	            												//but potentially arriving later through immigration are stored)
	    			if(tempsIncluded) {
	    				for(int i = 1; i < tokens.length; i += (birthHoursIncluded ? 4 : 3))
	    					tempLins.put(Long.parseLong(tokens[i]), Float.parseFloat(tokens[i + 2]));
	    			}

	            	boxIDsInFile.add(Integer.parseInt(tokens[0]));
	            	
	            	if(box != null) { //box handled on this machine
		            	//System.out.println(boxIDsInNode.size());
		            	
		            	box.addLoadedPop(tokens, tempLins, tempsIncluded, birthHoursIncluded);
		                boxI++;
		                activeBoxes.add(box);
	            	}
            	}
            	

            	if(myFileReader.hasNextLine())
            		tokens = myFileReader.nextLine().trim().split(",");
            	else
            		tokens = null;
            	
            	
            }
            myFileReader.close();
	    }
		activeBoxes.sort(null);
		return activeBoxes;
	}






	/**Get time in hours from string of format [day]hr[hour-of-day]
	 * 
	 * @param str in String
	 * @return time in hours
	 */
	private static long getTimeFromString(String str) {
		String[] bits = str.split("hr");
		long hour = Long.parseLong(bits[0]) * 24;
		if(bits.length > 0) {
			hour = hour + Integer.parseInt(bits[1]);
		}
		return hour;
	}






	/**Save population at a day to a file
	 * 
	 * @param activeBoxes boxes handled on this machine (all nodes if running locally, just this node if running distributed)
	 * @param filename output filename
	 */
	public static void savePop(List<GridBox> activeBoxes, String filename, String checkpointTimeStr) {
        FileWriter outputfile;
		try {
			outputfile = new FileWriter(filename,false);
			
			outputfile.write(checkpointTimeStr);
			
			String header = "";
			if(Runner.settings.isSelective)
				header = header + "temps";
			if(Runner.settings.ctrl.saveBirthHour)
				header = header + "," + "birthHours";
			
			if(!header.equals(""))
				outputfile.write(header + "\n");
			for(GridBox box : activeBoxes) {
				//add total
				outputfile.write(
						box.id + "," + box.writeDetails() + "\n"
					);
			}
			
			//add bottom line with maximum lineage IDs so when reload know what min ID to make new mutants
			StringBuilder mutIDStr = new StringBuilder();
			for(GridBox box : activeBoxes) { 
				mutIDStr.append(box.getCurrentMutantID() + ",");
			}
			outputfile.write("mutIDs\n" + mutIDStr + "\n");

			
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
		double[][] vols = new double[Runner.settings.numBoxes][];
        Scanner myFileReader = new Scanner(new File(filename));
        int boxI = 0;
        while(myFileReader.hasNext()) {
        	//String[] nextLineBits = .split(",");
        	vols[boxI] = getDoubleArrayFromString(myFileReader.nextLine());
		    boxI++;
        }
        myFileReader.close();
		if(boxI < Runner.settings.numBoxes)
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
			outFileWriter.write("[head]\n" + settingsStr);
			outFileWriter.close();
		} catch (IOException e) {
			throw e;
		}				
	}





}

